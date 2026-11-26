package com.campus.vehicle.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.vehicle.common.BizException;
import com.campus.vehicle.common.PageResult;
import com.campus.vehicle.entity.AccessRecord;
import com.campus.vehicle.entity.Blacklist;
import com.campus.vehicle.entity.PassCode;
import com.campus.vehicle.entity.Vehicle;
import com.campus.vehicle.entity.VisitorApply;
import com.campus.vehicle.mapper.AccessRecordMapper;
import com.campus.vehicle.mapper.PassCodeMapper;
import com.campus.vehicle.mapper.VehicleMapper;
import com.campus.vehicle.mapper.VisitorApplyMapper;
import com.campus.vehicle.security.LoginUser;
import com.campus.vehicle.security.UserContext;
import com.campus.vehicle.dto.VisitorApplyRequest;
import com.campus.vehicle.util.NoGen;
import com.campus.vehicle.util.PlateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 访客通行申请 + 自动审核规则引擎
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisitorApplyService {

    private final VisitorApplyMapper applyMapper;
    private final VehicleMapper vehicleMapper;
    private final PassCodeMapper passMapper;
    private final AccessRecordMapper recordMapper;
    private final PassService passService;
    private final ConfigService configService;
    private final BlacklistService blacklistService;
    private final AuditLogService auditLogService;

    // =================================================================
    // 提交申请（规则链自动审核）
    // =================================================================
    @Transactional
    public VisitorApply submit(VisitorApplyRequest req, String ip) {
        LocalDateTime now = LocalDateTime.now();
        String plate = PlateUtil.normalize(req.getPlateNo());
        if (!PlateUtil.isValid(plate)) {
            throw new BizException("车牌号格式不正确，请按行驶证填写，例如 京A12345");
        }

        LocalDateTime planStart = req.getPlanStart().withSecond(0).withNano(0);
        LocalDateTime planEnd = req.getPlanEnd().withSecond(0).withNano(0);
        if (!planEnd.isAfter(planStart)) {
            throw new BizException("计划离场时间必须晚于计划入场时间");
        }
        if (planStart.isBefore(now.minusMinutes(10))) {
            throw new BizException("计划入场时间不能早于当前时间");
        }
        int maxHours = configService.getInt("pass.max.valid.hours", 24);
        if (Duration.between(planStart, planEnd).toMinutes() > maxHours * 60L) {
            throw new BizException("单次停留时长不能超过 " + maxHours + " 小时，请调整离场时间");
        }
        int advanceDays = configService.getInt("audit.advance.days", 7);
        if (planStart.isAfter(now.plusDays(advanceDays))) {
            throw new BizException("最多可提前 " + advanceDays + " 天提交申请");
        }

        List<String> rules = new ArrayList<>();
        String rejectReason = null;
        String manualReason = null;

        // ① 黑名单
        Blacklist black = blacklistService.findActive(plate);
        if (black != null) {
            rules.add("① 命中黑名单：" + black.getReason());
            if (configService.getBool("audit.blacklist.reject", true)) {
                rejectReason = "车牌 " + plate + " 已被列入黑名单：" + black.getReason();
            } else if (manualReason == null) {
                manualReason = "车牌命中黑名单，需人工复核";
            }
        } else {
            rules.add("① 黑名单校验通过");
        }

        // ② 校内车辆库
        Vehicle existed = vehicleMapper.selectOne(new LambdaQueryWrapper<Vehicle>()
                .eq(Vehicle::getPlateNo, plate)
                .last("limit 1"));
        if (rejectReason == null && existed != null) {
            if ("APPROVED".equals(existed.getStatus())) {
                rejectReason = "车牌 " + plate + " 已登记为校内车辆，可直接车牌识别通行，无需访客申请";
                rules.add("② 已登记为校内车辆");
            } else {
                rules.add("② 车牌存在于校内档案（" + existed.getStatus() + "）");
                if (manualReason == null) {
                    manualReason = "该车牌在校内车辆档案中存在未通过审核的记录，转人工核实";
                }
            }
        } else if (rejectReason == null) {
            rules.add("② 非校内登记车辆");
        }

        // ③ 未结束的通行凭证
        if (rejectReason == null) {
            PassCode active = passService.findActiveByPlate(plate);
            if (active != null) {
                rejectReason = "该车牌存在未结束的通行凭证（" + active.getPassNo() + "），请先离场或等待凭证失效后再申请";
                rules.add("③ 存在未结束凭证");
            } else {
                rules.add("③ 无重复在场凭证");
            }
        }

        // ④ 待审核申请
        if (rejectReason == null) {
            Long pending = applyMapper.selectCount(new LambdaQueryWrapper<VisitorApply>()
                    .eq(VisitorApply::getPlateNo, plate)
                    .eq(VisitorApply::getStatus, "PENDING"));
            if (pending != null && pending > 0) {
                rejectReason = "该车牌已有待审核的申请，请勿重复提交";
                rules.add("④ 存在待审核申请");
            } else {
                rules.add("④ 无待审核申请");
            }
        }

        // ⑤ 24 小时申请次数
        if (rejectReason == null) {
            int limit = configService.getInt("audit.daily.limit", 3);
            Long recent = applyMapper.selectCount(new LambdaQueryWrapper<VisitorApply>()
                    .eq(VisitorApply::getPhone, req.getPhone())
                    .ge(VisitorApply::getCreateTime, now.minusHours(24)));
            long used = recent == null ? 0 : recent;
            rules.add("⑤ 24小时内申请 " + used + "/" + limit + " 次");
            if (used >= limit && manualReason == null) {
                manualReason = "该手机号 24 小时内申请已达 " + limit + " 次，转人工审核";
            }
        }

        // ⑥ 通行时段
        if (rejectReason == null) {
            String startStr = configService.get("audit.worktime.start", "06:00");
            String endStr = configService.get("audit.worktime.end", "23:00");
            LocalTime workStart = LocalTime.parse(startStr);
            LocalTime workEnd = LocalTime.parse(endStr);
            LocalTime target = planStart.toLocalTime();
            boolean inRange = !target.isBefore(workStart) && !target.isAfter(workEnd);
            rules.add("⑥ 计划入场 " + planStart.toLocalTime() + " 处于通行时段 " + startStr + "-" + endStr
                    + (inRange ? " 内" : " 外"));
            if (!inRange && manualReason == null) {
                manualReason = "计划入场时间不在允许通行时段（" + startStr + "-" + endStr + "），转人工审核";
            }
        }

        // ⑦ 被访人身份
        if (rejectReason == null) {
            if (configService.getBool("audit.host.required", false)) {
                Long hosts = vehicleMapper.selectCount(new LambdaQueryWrapper<Vehicle>()
                        .eq(Vehicle::getOwnerPhone, req.getHostPhone())
                        .eq(Vehicle::getStatus, "APPROVED"));
                boolean ok = hosts != null && hosts > 0;
                rules.add("⑦ 被访人身份" + (ok ? "校验通过" : "未在校内档案中登记"));
                if (!ok && manualReason == null) {
                    manualReason = "被访人 " + req.getHostName() + " 未在校内人员档案中登记，转人工核实";
                }
            } else {
                rules.add("⑦ 被访人身份校验未启用");
            }
        }

        // ⑧ 自动审核总开关
        if (!configService.getBool("audit.auto.enable", true)) {
            manualReason = "系统已关闭自动审核，申请转人工处理";
            rules.add("⑧ 自动审核已关闭");
        }

        VisitorApply apply = new VisitorApply();
        apply.setApplyNo(NoGen.apply());
        apply.setVisitorName(req.getVisitorName().trim());
        apply.setIdCard(StringUtils.hasText(req.getIdCard()) ? maskIdCard(req.getIdCard().trim()) : null);
        apply.setPhone(req.getPhone().trim());
        apply.setPlateNo(plate);
        apply.setPlateColor(StringUtils.hasText(req.getPlateColor()) ? req.getPlateColor() : "蓝牌");
        apply.setVisitReason(req.getVisitReason().trim());
        apply.setHostName(req.getHostName().trim());
        apply.setHostPhone(req.getHostPhone().trim());
        apply.setHostDept(req.getHostDept());
        apply.setPlanStart(planStart);
        apply.setPlanEnd(planEnd);
        apply.setClientIp(ip);
        apply.setAuditRules(String.join("；", rules));
        apply.setCreateTime(now.withNano(0));
        apply.setUpdateTime(now.withNano(0));

        if (rejectReason != null) {
            apply.setStatus("REJECTED");
            apply.setAuditType("AUTO");
            apply.setAuditRemark(rejectReason);
            apply.setAuditTime(now);
            apply.setAuditorName("系统规则引擎");
        } else if (manualReason != null) {
            apply.setStatus("PENDING");
            apply.setAuditRemark(manualReason);
        } else {
            apply.setStatus("AUTO_APPROVED");
            apply.setAuditType("AUTO");
            apply.setAuditRemark("规则链全部通过，系统自动放行");
            apply.setAuditTime(now);
            apply.setAuditorName("系统规则引擎");
        }

        applyMapper.insert(apply);

        if ("AUTO_APPROVED".equals(apply.getStatus())) {
            PassCode pass = passService.issueForVisitor(apply);
            apply.setPassId(pass.getId());
            applyMapper.updateById(apply);
            apply.setPass(passService.fillQr(pass));
            log.info("申请 {} 自动通过，签发通行码 {}", apply.getApplyNo(), pass.getPassNo());
        }
        return apply;
    }

    // =================================================================
    // 人工审核
    // =================================================================
    @Transactional
    public VisitorApply audit(Long id, boolean approve, String remark) {
        VisitorApply apply = applyMapper.selectById(id);
        if (apply == null) {
            throw new BizException("申请不存在");
        }
        if (!"PENDING".equals(apply.getStatus())) {
            throw new BizException("该申请当前状态为「" + statusText(apply.getStatus()) + "」，无法重复审核");
        }
        LocalDateTime now = LocalDateTime.now();
        LoginUser user = UserContext.require();

        if (approve) {
            if (apply.getPlanEnd().isBefore(now)) {
                throw new BizException("该申请的计划离场时间已过，无法放行，请驳回后由访客重新提交");
            }
            PassCode pass = passService.issueForVisitor(apply);
            apply.setPassId(pass.getId());
            apply.setStatus("APPROVED");
            apply.setPass(passService.fillQr(pass));
        } else {
            apply.setStatus("REJECTED");
        }
        apply.setAuditType("MANUAL");
        apply.setAuditRemark(StringUtils.hasText(remark) ? remark
                : (approve ? "人工审核通过" : "人工审核驳回"));
        apply.setAuditorId(user.getId());
        apply.setAuditorName(user.getRealName());
        apply.setAuditTime(now);
        applyMapper.updateById(apply);

        auditLogService.log("访客审核", approve ? "通过" : "驳回",
                "申请 " + apply.getApplyNo() + "，车牌 " + apply.getPlateNo()
                        + (StringUtils.hasText(remark) ? "，备注：" + remark : ""));
        return apply;
    }

    // =================================================================
    // 取消 / 重新签发
    // =================================================================
    @Transactional
    public VisitorApply cancel(String applyNo, String phone, boolean admin) {
        VisitorApply apply = applyMapper.selectOne(new LambdaQueryWrapper<VisitorApply>()
                .eq(VisitorApply::getApplyNo, applyNo).last("limit 1"));
        if (apply == null) {
            throw new BizException("申请单不存在");
        }
        if (!admin && !apply.getPhone().equals(phone)) {
            throw new BizException(403, "手机号与申请单不匹配");
        }
        if (!List.of("PENDING", "AUTO_APPROVED", "APPROVED").contains(apply.getStatus())) {
            throw new BizException("该申请当前状态为「" + statusText(apply.getStatus()) + "」，无法取消");
        }
        PassCode pass = apply.getPassId() == null ? null : passMapper.selectById(apply.getPassId());
        if (pass != null && "IN_USE".equals(pass.getStatus())) {
            throw new BizException("车辆已入场，请到门岗登记离场后再操作");
        }
        apply.setStatus("CANCELLED");
        apply.setAuditRemark("申请已取消");
        applyMapper.updateById(apply);
        if (pass != null) {
            passService.voidPass(pass, "访客取消申请");
        }
        auditLogService.log("访客审核", "取消", "申请 " + applyNo + "，车牌 " + apply.getPlateNo());
        return apply;
    }

    @Transactional
    public VisitorApply reissue(Long id) {
        VisitorApply apply = applyMapper.selectById(id);
        if (apply == null) {
            throw new BizException("申请不存在");
        }
        if (!List.of("AUTO_APPROVED", "APPROVED", "EXPIRED").contains(apply.getStatus())) {
            throw new BizException("该申请状态不允许重新签发通行码");
        }
        if (apply.getPlanEnd().isBefore(LocalDateTime.now())) {
            throw new BizException("计划离场时间已过，请让访客重新提交申请");
        }
        if (apply.getPassId() != null) {
            PassCode old = passMapper.selectById(apply.getPassId());
            if (old != null && "IN_USE".equals(old.getStatus())) {
                throw new BizException("车辆仍在场，无法重新签发");
            }
            passService.voidPass(old, "重新签发");
        }
        PassCode pass = passService.issueForVisitor(apply);
        apply.setPassId(pass.getId());
        apply.setStatus("AUTO_APPROVED");
        applyMapper.updateById(apply);
        apply.setPass(passService.fillQr(pass));
        auditLogService.log("访客审核", "重新签发通行码",
                "申请 " + apply.getApplyNo() + "，通行码 " + pass.getPassNo());
        return apply;
    }

    // =================================================================
    // 查询
    // =================================================================
    public PageResult<VisitorApply> page(int page, int size, String keyword, String status,
                                         LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<VisitorApply> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            qw.and(w -> w.like(VisitorApply::getPlateNo, kw)
                    .or().like(VisitorApply::getVisitorName, kw)
                    .or().like(VisitorApply::getPhone, kw)
                    .or().like(VisitorApply::getApplyNo, kw)
                    .or().like(VisitorApply::getHostName, kw));
        }
        if (StringUtils.hasText(status)) {
            qw.eq(VisitorApply::getStatus, status);
        }
        if (startDate != null) {
            qw.ge(VisitorApply::getCreateTime, startDate.atStartOfDay());
        }
        if (endDate != null) {
            qw.lt(VisitorApply::getCreateTime, endDate.plusDays(1).atStartOfDay());
        }
        qw.orderByDesc(VisitorApply::getCreateTime);
        Page<VisitorApply> result = applyMapper.selectPage(new Page<>(page, size), qw);
        result.getRecords().forEach(a -> {
            if (a.getPassId() != null) {
                a.setPass(passMapper.selectById(a.getPassId()));
            }
        });
        return PageResult.of(result.getTotal(), page, size, result.getRecords());
    }

    public VisitorApply detail(Long id) {
        VisitorApply apply = applyMapper.selectById(id);
        if (apply == null) {
            throw new BizException("申请不存在");
        }
        if (apply.getPassId() != null) {
            apply.setPass(passService.fillQr(passMapper.selectById(apply.getPassId())));
        }
        return apply;
    }

    /** 访客自助查询：单号 + 手机号 */
    public VisitorApply query(String applyNo, String phone) {
        VisitorApply apply = applyMapper.selectOne(new LambdaQueryWrapper<VisitorApply>()
                .eq(VisitorApply::getApplyNo, applyNo.trim()).last("limit 1"));
        if (apply == null) {
            throw new BizException("未找到该申请单，请核对单号");
        }
        if (!apply.getPhone().equals(phone.trim())) {
            throw new BizException(403, "手机号与申请单不匹配");
        }
        if (apply.getPassId() != null) {
            apply.setPass(passService.fillQr(passMapper.selectById(apply.getPassId())));
        }
        return apply;
    }

    /** 今日待审数量，供工作台角标 */
    public long pendingCount() {
        Long count = applyMapper.selectCount(new LambdaQueryWrapper<VisitorApply>()
                .eq(VisitorApply::getStatus, "PENDING"));
        return count == null ? 0 : count;
    }

    // =================================================================
    // 定时任务调用：过期与超时
    // =================================================================
    @Transactional
    public int expireUnusedPasses() {
        LocalDateTime now = LocalDateTime.now();
        List<PassCode> list = passMapper.selectList(new LambdaQueryWrapper<PassCode>()
                .eq(PassCode::getStatus, "UNUSED")
                .lt(PassCode::getValidEnd, now));
        list.forEach(p -> {
            p.setStatus("EXPIRED");
            passMapper.updateById(p);
        });
        return list.size();
    }

    @Transactional
    public int closeOvertimeStays() {
        int grace = Math.max(0, configService.getInt("pass.overtime.grace", 60));
        LocalDateTime now = LocalDateTime.now();
        List<PassCode> list = passMapper.selectList(new LambdaQueryWrapper<PassCode>()
                .eq(PassCode::getStatus, "IN_USE")
                .lt(PassCode::getValidEnd, now.minusMinutes(grace)));
        for (PassCode pass : list) {
            pass.setStatus("VOID");
            pass.setExitTime(now);
            pass.setExitGate("系统自动销号");
            passMapper.updateById(pass);

            AccessRecord record = new AccessRecord();
            record.setRecordNo(NoGen.record());
            record.setPlateNo(pass.getPlateNo());
            record.setPassType(pass.getPassType());
            record.setApplyId(pass.getApplyId());
            record.setPassId(pass.getId());
            record.setVehicleId(pass.getVehicleId());
            record.setDirection("OUT");
            record.setGateName("系统自动销号");
            record.setChannel("SYSTEM");
            record.setVerifyResult("PASS");
            record.setOperatorName("系统");
            record.setOccurTime(now);
            record.setRemark("超过凭证有效期 " + grace + " 分钟仍未出场，系统自动登记离场并销号");
            recordMapper.insert(record);
        }
        return list.size();
    }

    @Transactional
    public int expireApplies() {
        LocalDateTime now = LocalDateTime.now();
        List<VisitorApply> list = applyMapper.selectList(new LambdaQueryWrapper<VisitorApply>()
                .in(VisitorApply::getStatus, "PENDING", "AUTO_APPROVED", "APPROVED")
                .lt(VisitorApply::getPlanEnd, now));
        int count = 0;
        for (VisitorApply apply : list) {
            if (apply.getPassId() != null) {
                PassCode pass = passMapper.selectById(apply.getPassId());
                if (pass != null && "IN_USE".equals(pass.getStatus())) {
                    continue;
                }
            }
            apply.setStatus("EXPIRED");
            applyMapper.updateById(apply);
            count++;
        }
        return count;
    }

    public static String statusText(String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case "PENDING" -> "待审核";
            case "AUTO_APPROVED" -> "自动通过";
            case "APPROVED" -> "人工通过";
            case "REJECTED" -> "已驳回";
            case "EXPIRED" -> "已过期";
            case "CANCELLED" -> "已取消";
            default -> status;
        };
    }

    private String maskIdCard(String id) {
        if (id.length() < 8) {
            return id;
        }
        return id.substring(0, 4) + "**********" + id.substring(id.length() - 4);
    }
}

package com.campus.vehicle.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.vehicle.common.BizException;
import com.campus.vehicle.dto.VerifyRequest;
import com.campus.vehicle.dto.VerifyResult;
import com.campus.vehicle.entity.AccessRecord;
import com.campus.vehicle.entity.Blacklist;
import com.campus.vehicle.entity.Gate;
import com.campus.vehicle.entity.PassCode;
import com.campus.vehicle.entity.Vehicle;
import com.campus.vehicle.mapper.AccessRecordMapper;
import com.campus.vehicle.mapper.GateMapper;
import com.campus.vehicle.mapper.PassCodeMapper;
import com.campus.vehicle.mapper.VehicleMapper;
import com.campus.vehicle.security.LoginUser;
import com.campus.vehicle.security.UserContext;
import com.campus.vehicle.util.NoGen;
import com.campus.vehicle.util.PlateUtil;
import com.campus.vehicle.util.QrSignUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 门岗核验：扫码放行、车牌放行、出场销号
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerifyService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final PassCodeMapper passMapper;
    private final VehicleMapper vehicleMapper;
    private final AccessRecordMapper recordMapper;
    private final GateMapper gateMapper;
    private final PassService passService;
    private final BlacklistService blacklistService;
    private final ConfigService configService;
    private final QrSignUtil qrSignUtil;

    @Transactional
    public VerifyResult verify(VerifyRequest req) {
        String direction = normalizeDirection(req.getDirection());
        Gate gate = req.getGateId() == null ? null : gateMapper.selectById(req.getGateId());
        if (gate != null) {
            if (gate.getStatus() != null && gate.getStatus() == 0) {
                throw new BizException("该门岗已停用：" + gate.getGateName());
            }
            if ("IN".equals(direction) && "OUT".equals(gate.getDirection())) {
                throw new BizException("「" + gate.getGateName() + "」仅允许出场");
            }
            if ("OUT".equals(direction) && "IN".equals(gate.getDirection())) {
                throw new BizException("「" + gate.getGateName() + "」仅允许入场");
            }
        }

        if (StringUtils.hasText(req.getCode())) {
            return byQr(req.getCode().trim(), gate, direction);
        }
        if (StringUtils.hasText(req.getPlateNo())) {
            return byPlate(req.getPlateNo(), gate, direction);
        }
        throw new BizException("请扫描通行二维码或输入车牌号");
    }

    // =================================================================
    // 二维码通道
    // =================================================================
    private VerifyResult byQr(String code, Gate gate, String direction) {
        String token = qrSignUtil.verify(code);
        if (token == null) {
            return reject(null, null, direction, "二维码无效或已被篡改，请让访客在小程序中重新打开通行码", gate);
        }
        PassCode pass = passService.findByToken(token);
        if (pass == null) {
            return reject(null, null, direction, "通行码不存在，可能已被签发方作废", gate);
        }
        String realDirection = direction != null ? direction
                : ("IN_USE".equals(pass.getStatus()) ? "OUT" : "IN");
        return "IN".equals(realDirection)
                ? doEnter(pass, gate, "QR")
                : doExit(pass, gate, "QR");
    }

    // =================================================================
    // 车牌识别通道
    // =================================================================
    private VerifyResult byPlate(String rawPlate, Gate gate, String direction) {
        String plate = PlateUtil.normalize(rawPlate);
        if (!PlateUtil.isValid(plate)) {
            return reject(null, plate, direction, "车牌号格式不正确", gate);
        }

        Vehicle vehicle = vehicleMapper.selectOne(new LambdaQueryWrapper<Vehicle>()
                .eq(Vehicle::getPlateNo, plate).last("limit 1"));
        PassCode inUse = findInUseByPlate(plate);
        String realDirection = direction != null ? direction : (inUse != null ? "OUT" : "IN");

        if (vehicle != null) {
            return internalVehicle(vehicle, inUse, realDirection, gate);
        }

        // 访客车辆：优先使用未使用的访客凭证
        if ("IN".equals(realDirection)) {
            PassCode unused = passMapper.selectOne(new LambdaQueryWrapper<PassCode>()
                    .eq(PassCode::getPlateNo, plate)
                    .eq(PassCode::getPassType, "VISITOR")
                    .eq(PassCode::getStatus, "UNUSED")
                    .gt(PassCode::getValidEnd, LocalDateTime.now())
                    .orderByAsc(PassCode::getValidStart)
                    .last("limit 1"));
            if (unused != null) {
                return doEnter(unused, gate, "PLATE");
            }
            return reject(null, plate, "IN",
                    "该车牌未登记为校内车辆，也没有生效中的访客通行码，请访客先在线提交申请", gate);
        }
        if (inUse != null) {
            return doExit(inUse, gate, "PLATE");
        }
        return reject(null, plate, "OUT", "该车辆当前不在场，无需离场登记", gate);
    }

    private VerifyResult internalVehicle(Vehicle vehicle, PassCode inUse, String direction, Gate gate) {
        String plate = vehicle.getPlateNo();
        if (!"APPROVED".equals(vehicle.getStatus())) {
            String text = switch (vehicle.getStatus()) {
                case "PENDING" -> "待审核";
                case "REJECTED" -> "已驳回";
                case "DISABLED" -> "已停用";
                default -> vehicle.getStatus();
            };
            return reject(null, plate, direction, "该车辆档案状态为「" + text + "」，不予放行", gate);
        }
        LocalDate today = LocalDate.now();
        if (vehicle.getValidStart() != null && today.isBefore(vehicle.getValidStart())) {
            return reject(null, plate, direction,
                    "该车辆通行有效期自 " + vehicle.getValidStart() + " 起，当前不在有效期内", gate);
        }
        if (vehicle.getValidEnd() != null && today.isAfter(vehicle.getValidEnd())) {
            return reject(null, plate, direction,
                    "该车辆通行有效期已于 " + vehicle.getValidEnd() + " 到期，请办理续期", gate);
        }

        if ("OUT".equals(direction)) {
            if (inUse == null) {
                return reject(null, plate, "OUT", "该车辆当前不在场，无需离场登记", gate);
            }
            return doExit(inUse, gate, "PLATE");
        }

        if (inUse != null) {
            return reject(inUse, plate, "IN",
                    "该车辆已于 " + inUse.getEnterTime().format(TIME_FMT) + " 入场，尚未离场", gate);
        }
        Blacklist black = blacklistService.findActive(plate);
        if (black != null) {
            return reject(null, plate, "IN", "车牌在黑名单中：" + black.getReason(), gate);
        }
        if (!configService.getBool("gate.plate.auto.open", true)) {
            return reject(null, plate, "IN", "系统已关闭校内车辆自动放行，请人工确认后放行", gate);
        }

        PassCode pass = passService.issueForInternal(vehicle);
        String gateName = gateName(gate);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        pass.setStatus("IN_USE");
        pass.setEnterTime(now);
        pass.setEnterGate(gateName);
        passMapper.updateById(pass);
        AccessRecord record = writeRecord(pass, "IN", gate, "PLATE", true, null, "校内车辆车牌识别放行");
        return success(pass, "IN", "校内车辆，欢迎回校", record, gateName);
    }

    // =================================================================
    // 入场
    // =================================================================
    private VerifyResult doEnter(PassCode pass, Gate gate, String channel) {
        String gateName = gateName(gate);
        LocalDateTime now = LocalDateTime.now().withNano(0);

        if (!"UNUSED".equals(pass.getStatus())) {
            String msg = switch (pass.getStatus()) {
                case "IN_USE" -> "该车辆已于 " + (pass.getEnterTime() == null ? "" : pass.getEnterTime().format(TIME_FMT))
                        + " 入场，尚未离场";
                case "USED" -> "该通行码已完成进出流程并销号，不可重复使用";
                case "EXPIRED" -> "该通行码已过期（有效期至 " + pass.getValidEnd().format(TIME_FMT) + "）";
                case "VOID" -> "该通行码已被作废，请联系门卫处理";
                default -> "该通行码状态异常，无法入场";
            };
            return reject(pass, pass.getPlateNo(), "IN", msg, gate);
        }
        if (now.isBefore(pass.getValidStart())) {
            return reject(pass, pass.getPlateNo(), "IN",
                    "通行码尚未生效，生效时间 " + pass.getValidStart().format(TIME_FMT), gate);
        }
        if (now.isAfter(pass.getValidEnd())) {
            pass.setStatus("EXPIRED");
            passMapper.updateById(pass);
            return reject(pass, pass.getPlateNo(), "IN",
                    "通行码已过期（有效期至 " + pass.getValidEnd().format(TIME_FMT) + "），请重新申请", gate);
        }
        Blacklist black = blacklistService.findActive(pass.getPlateNo());
        if (black != null) {
            return reject(pass, pass.getPlateNo(), "IN", "车牌在黑名单中：" + black.getReason(), gate);
        }

        int updated = passMapper.update(null, new LambdaUpdateWrapper<PassCode>()
                .eq(PassCode::getId, pass.getId())
                .eq(PassCode::getStatus, "UNUSED")
                .set(PassCode::getStatus, "IN_USE")
                .set(PassCode::getEnterTime, now)
                .set(PassCode::getEnterGate, gateName));
        if (updated == 0) {
            return reject(pass, pass.getPlateNo(), "IN", "该通行码正在其他通道核验，请稍后重试", gate);
        }

        pass.setStatus("IN_USE");
        pass.setEnterTime(now);
        pass.setEnterGate(gateName);
        AccessRecord record = writeRecord(pass, "IN", gate, channel, true, null, null);
        long remain = Duration.between(now, pass.getValidEnd()).toMinutes();
        String msg = "核验通过，请入场（允许停留至 " + pass.getValidEnd().format(TIME_FMT)
                + "，剩余 " + remain + " 分钟）";
        return success(pass, "IN", msg, record, gateName);
    }

    // =================================================================
    // 出场（自动销号）
    // =================================================================
    private VerifyResult doExit(PassCode pass, Gate gate, String channel) {
        String gateName = gateName(gate);
        LocalDateTime now = LocalDateTime.now().withNano(0);

        if (!"IN_USE".equals(pass.getStatus())) {
            String msg = switch (pass.getStatus()) {
                case "UNUSED" -> "该车辆尚未入场，无需办理离场";
                case "USED" -> "该车辆已完成离场销号";
                case "EXPIRED" -> "通行码已过期且未检测到入场记录";
                case "VOID" -> "通行码已作废，无需重复销号";
                default -> "该通行码状态异常";
            };
            return reject(pass, pass.getPlateNo(), "OUT", msg, gate);
        }

        int updated = passMapper.update(null, new LambdaUpdateWrapper<PassCode>()
                .eq(PassCode::getId, pass.getId())
                .eq(PassCode::getStatus, "IN_USE")
                .set(PassCode::getStatus, "USED")
                .set(PassCode::getExitTime, now)
                .set(PassCode::getExitGate, gateName));
        if (updated == 0) {
            return reject(pass, pass.getPlateNo(), "OUT", "该凭证正在其他通道核验，请稍后重试", gate);
        }

        boolean overtime = now.isAfter(pass.getValidEnd());
        pass.setStatus("USED");
        pass.setExitTime(now);
        pass.setExitGate(gateName);
        AccessRecord record = writeRecord(pass, "OUT", gate, channel, true, null,
                overtime ? "超过凭证有效期离场，凭证已自动销号" : "正常离场，凭证已自动销号");

        long stay = pass.getEnterTime() == null ? 0 : Duration.between(pass.getEnterTime(), now).toMinutes();
        String msg = "离场登记成功，通行码已销号（本次停留 " + (stay / 60) + " 小时 " + (stay % 60) + " 分钟）"
                + (overtime ? "，已超时" : "");
        return success(pass, "OUT", msg, record, gateName);
    }

    // =================================================================
    // 记录与结果封装
    // =================================================================
    private AccessRecord writeRecord(PassCode pass, String direction, Gate gate, String channel,
                                     boolean passResult, String rejectReason, String remark) {
        LoginUser user = UserContext.get();
        AccessRecord record = new AccessRecord();
        record.setRecordNo(NoGen.record());
        record.setPlateNo(pass == null ? "UNKNOWN" : pass.getPlateNo());
        record.setPassType(pass == null ? "VISITOR" : pass.getPassType());
        record.setApplyId(pass == null ? null : pass.getApplyId());
        record.setPassId(pass == null ? null : pass.getId());
        record.setVehicleId(pass == null ? null : pass.getVehicleId());
        record.setDirection(direction);
        record.setGateId(gate == null ? null : gate.getId());
        record.setGateName(gateName(gate));
        record.setChannel(channel);
        record.setVerifyResult(passResult ? "PASS" : "REJECT");
        record.setRejectReason(rejectReason);
        record.setOperatorId(user == null ? null : user.getId());
        record.setOperatorName(user == null ? "自助通道" : user.getRealName());
        record.setOccurTime(LocalDateTime.now());
        record.setRemark(remark);
        recordMapper.insert(record);
        return record;
    }

    private VerifyResult success(PassCode pass, String direction, String message,
                                 AccessRecord record, String gateName) {
        VerifyResult result = new VerifyResult();
        result.setPass(true);
        result.setMessage(message);
        result.setDirection(direction);
        result.setPlateNo(pass.getPlateNo());
        result.setPassType(pass.getPassType());
        result.setHolderName(pass.getHolderName());
        result.setPassNo(pass.getPassNo());
        result.setPassId(pass.getId());
        result.setRecordNo(record.getRecordNo());
        result.setGateName(gateName);
        result.setValidStart(pass.getValidStart());
        result.setValidEnd(pass.getValidEnd());
        result.setPassStatus(pass.getStatus());
        result.setOccurTime(record.getOccurTime());
        result.setRemainMinutes(Duration.between(LocalDateTime.now(), pass.getValidEnd()).toMinutes());
        return result;
    }

    private VerifyResult reject(PassCode pass, String plateNo, String direction, String reason, Gate gate) {
        AccessRecord record = new AccessRecord();
        LoginUser user = UserContext.get();
        record.setRecordNo(NoGen.record());
        record.setPlateNo(pass != null ? pass.getPlateNo() : (StringUtils.hasText(plateNo) ? plateNo : "UNKNOWN"));
        record.setPassType(pass != null ? pass.getPassType() : "VISITOR");
        record.setApplyId(pass == null ? null : pass.getApplyId());
        record.setPassId(pass == null ? null : pass.getId());
        record.setVehicleId(pass == null ? null : pass.getVehicleId());
        record.setDirection(direction == null ? "IN" : direction);
        record.setGateId(gate == null ? null : gate.getId());
        record.setGateName(gateName(gate));
        record.setChannel(pass == null ? "MANUAL" : "QR");
        record.setVerifyResult("REJECT");
        record.setRejectReason(reason);
        record.setOperatorId(user == null ? null : user.getId());
        record.setOperatorName(user == null ? "自助通道" : user.getRealName());
        record.setOccurTime(LocalDateTime.now());
        recordMapper.insert(record);

        VerifyResult result = new VerifyResult();
        result.setPass(false);
        result.setMessage(reason);
        result.setDirection(record.getDirection());
        result.setPlateNo(record.getPlateNo());
        result.setPassType(record.getPassType());
        result.setHolderName(pass == null ? null : pass.getHolderName());
        result.setPassNo(pass == null ? null : pass.getPassNo());
        result.setPassId(pass == null ? null : pass.getId());
        result.setRecordNo(record.getRecordNo());
        result.setGateName(record.getGateName());
        result.setValidStart(pass == null ? null : pass.getValidStart());
        result.setValidEnd(pass == null ? null : pass.getValidEnd());
        result.setPassStatus(pass == null ? null : pass.getStatus());
        result.setOccurTime(record.getOccurTime());
        return result;
    }

    private PassCode findInUseByPlate(String plate) {
        List<PassCode> list = passMapper.selectList(new LambdaQueryWrapper<PassCode>()
                .eq(PassCode::getPlateNo, plate)
                .eq(PassCode::getStatus, "IN_USE")
                .orderByDesc(PassCode::getId)
                .last("limit 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    private String gateName(Gate gate) {
        return gate == null ? "未指定门岗" : gate.getGateName();
    }

    private String normalizeDirection(String direction) {
        if (!StringUtils.hasText(direction)) {
            return null;
        }
        String value = direction.trim().toUpperCase();
        if (!"IN".equals(value) && !"OUT".equals(value)) {
            throw new BizException("direction 只能是 IN 或 OUT");
        }
        return value;
    }
}

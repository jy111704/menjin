package com.campus.vehicle.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.vehicle.common.BizException;
import com.campus.vehicle.common.PageResult;
import com.campus.vehicle.entity.PassCode;
import com.campus.vehicle.entity.Vehicle;
import com.campus.vehicle.entity.VisitorApply;
import com.campus.vehicle.mapper.PassCodeMapper;
import com.campus.vehicle.util.NoGen;
import com.campus.vehicle.util.QrImageUtil;
import com.campus.vehicle.util.QrSignUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 通行码：签发、作废、二维码渲染
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PassService {

    private final PassCodeMapper passMapper;
    private final ConfigService configService;
    private final QrSignUtil qrSignUtil;

    /** 为访客申请签发通行码：有效期 = [计划入场 - 允许提前分钟, 计划离场] */
    @Transactional
    public PassCode issueForVisitor(VisitorApply apply) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        int advance = Math.max(0, configService.getInt("pass.advance.minutes", 120));
        LocalDateTime validStart = apply.getPlanStart().minusMinutes(advance);
        if (validStart.isBefore(now)) {
            validStart = now;
        }
        LocalDateTime validEnd = apply.getPlanEnd();
        if (!validEnd.isAfter(validStart)) {
            validEnd = validStart.plusHours(2);
        }

        PassCode pass = new PassCode();
        pass.setPassNo(NoGen.pass());
        pass.setQrToken(qrSignUtil.randomToken());
        pass.setPassType("VISITOR");
        pass.setApplyId(apply.getId());
        pass.setPlateNo(apply.getPlateNo());
        pass.setHolderName(apply.getVisitorName());
        pass.setHolderPhone(apply.getPhone());
        pass.setValidStart(validStart);
        pass.setValidEnd(validEnd);
        pass.setStatus("UNUSED");
        pass.setCreateTime(now);
        passMapper.insert(pass);
        return pass;
    }

    /** 校内车辆进校时生成在场凭证，用于在场统计与出场销号 */
    @Transactional
    public PassCode issueForInternal(Vehicle vehicle) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        LocalDateTime endOfDay = LocalDate.now().plusDays(1).atStartOfDay().minusSeconds(1);
        LocalDateTime validEnd = endOfDay.isAfter(now.plusHours(2)) ? endOfDay : now.plusHours(2);

        PassCode pass = new PassCode();
        pass.setPassNo(NoGen.pass());
        pass.setQrToken(qrSignUtil.randomToken());
        pass.setPassType("INTERNAL");
        pass.setVehicleId(vehicle.getId());
        pass.setPlateNo(vehicle.getPlateNo());
        pass.setHolderName(vehicle.getOwnerName());
        pass.setHolderPhone(vehicle.getOwnerPhone());
        pass.setValidStart(now);
        pass.setValidEnd(validEnd);
        pass.setStatus("UNUSED");
        pass.setCreateTime(now);
        passMapper.insert(pass);
        return pass;
    }

    public PassCode findByToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return passMapper.selectOne(new LambdaQueryWrapper<PassCode>().eq(PassCode::getQrToken, token));
    }

    public PassCode findActiveByPlate(String plateNo) {
        List<PassCode> list = passMapper.selectList(new LambdaQueryWrapper<PassCode>()
                .eq(PassCode::getPlateNo, plateNo)
                .in(PassCode::getStatus, "UNUSED", "IN_USE")
                .gt(PassCode::getValidEnd, LocalDateTime.now())
                .orderByDesc(PassCode::getId)
                .last("limit 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    /** 补充二维码图片与内容 */
    public PassCode fillQr(PassCode pass) {
        if (pass == null) {
            return null;
        }
        String content = qrSignUtil.payload(pass.getQrToken());
        pass.setQrContent(content);
        pass.setQrImage(QrImageUtil.toBase64(content, 320));
        return pass;
    }

    /** 作废 */
    @Transactional
    public void voidPass(PassCode pass, String reason) {
        if (pass == null) {
            return;
        }
        if ("USED".equals(pass.getStatus()) || "VOID".equals(pass.getStatus())) {
            return;
        }
        pass.setStatus("VOID");
        passMapper.updateById(pass);
        log.info("通行码 {} 已作废：{}", pass.getPassNo(), reason);
    }

    public PassCode getById(Long id) {
        PassCode pass = passMapper.selectById(id);
        if (pass == null) {
            throw new BizException("通行码不存在");
        }
        return pass;
    }

    public PageResult<PassCode> page(int page, int size, String keyword, String status, String passType) {
        LambdaQueryWrapper<PassCode> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            qw.and(w -> w.like(PassCode::getPlateNo, kw)
                    .or().like(PassCode::getPassNo, kw)
                    .or().like(PassCode::getHolderName, kw)
                    .or().like(PassCode::getHolderPhone, kw));
        }
        if (status != null && !status.isBlank()) {
            qw.eq(PassCode::getStatus, status);
        }
        if (passType != null && !passType.isBlank()) {
            qw.eq(PassCode::getPassType, passType);
        }
        qw.orderByDesc(PassCode::getCreateTime);
        Page<PassCode> result = passMapper.selectPage(new Page<>(page, size), qw);
        return PageResult.of(result.getTotal(), page, size, result.getRecords());
    }

    /** 剩余有效分钟（负数表示已过期） */
    public static Long remainMinutes(PassCode pass) {
        if (pass == null || pass.getValidEnd() == null) {
            return null;
        }
        return Duration.between(LocalDateTime.now(), pass.getValidEnd()).toMinutes();
    }
}

package com.campus.vehicle.task;

import com.campus.vehicle.service.VisitorApplyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每分钟巡检：未使用凭证过期、超时未出场自动销号、申请过期
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PassExpireTask {

    private final VisitorApplyService applyService;

    @Scheduled(initialDelay = 20000, fixedDelay = 60000)
    public void tick() {
        try {
            int expiredPass = applyService.expireUnusedPasses();
            int overtime = applyService.closeOvertimeStays();
            int expiredApply = applyService.expireApplies();
            if (expiredPass + overtime + expiredApply > 0) {
                log.info("定时巡检：过期凭证 {} 个，超时自动销号 {} 个，过期申请 {} 条",
                        expiredPass, overtime, expiredApply);
            }
        } catch (Exception e) {
            log.error("定时巡检执行失败", e);
        }
    }
}

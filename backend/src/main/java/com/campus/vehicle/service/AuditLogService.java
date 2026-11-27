package com.campus.vehicle.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.vehicle.common.BizException;
import com.campus.vehicle.common.PageResult;
import com.campus.vehicle.entity.AuditLog;
import com.campus.vehicle.mapper.AuditLogMapper;
import com.campus.vehicle.security.LoginUser;
import com.campus.vehicle.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;

    public PageResult<AuditLog> page(int page, int size, String keyword, String module,
                                     LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<AuditLog> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            qw.and(w -> w.like(AuditLog::getUsername, kw)
                    .or().like(AuditLog::getAction, kw)
                    .or().like(AuditLog::getDetail, kw)
                    .or().like(AuditLog::getModule, kw));
        }
        if (StringUtils.hasText(module)) {
            qw.eq(AuditLog::getModule, module);
        }
        if (startDate != null) {
            qw.ge(AuditLog::getCreateTime, startDate.atStartOfDay());
        }
        if (endDate != null) {
            qw.lt(AuditLog::getCreateTime, endDate.plusDays(1).atStartOfDay());
        }
        qw.orderByDesc(AuditLog::getId);
        Page<AuditLog> result = auditLogMapper.selectPage(new Page<>(page, size), qw);
        return PageResult.of(result.getTotal(), page, size, result.getRecords());
    }

    public void delete(Long id) {
        AuditLog entity = auditLogMapper.selectById(id);
        if (entity == null) {
            throw new BizException("操作日志不存在或已被删除");
        }
        auditLogMapper.deleteById(id);
        // 删除日志本身不再写入 audit_log，避免删一条又多一条；保留服务端文件日志。
        log.info("操作日志已删除 id={}, module={}, action={}, username={}",
                id, entity.getModule(), entity.getAction(), entity.getUsername());
    }

    public void log(String module, String action, String detail) {
        try {
            LoginUser user = UserContext.get();
            AuditLog entity = new AuditLog();
            entity.setModule(module);
            entity.setAction(action);
            entity.setDetail(detail != null && detail.length() > 950 ? detail.substring(0, 950) : detail);
            entity.setCreateTime(LocalDateTime.now());
            if (user != null) {
                entity.setUserId(user.getId());
                entity.setUsername(user.getRealName() + "(" + user.getUsername() + ")");
            } else {
                entity.setUsername("访客自助端");
            }
            auditLogMapper.insert(entity);
        } catch (Exception e) {
            log.warn("写审计日志失败: {}", e.getMessage());
        }
    }
}

package com.campus.vehicle.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.vehicle.common.BizException;
import com.campus.vehicle.common.PageResult;
import com.campus.vehicle.entity.Gate;
import com.campus.vehicle.mapper.GateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GateService {

    private final GateMapper gateMapper;
    private final AuditLogService auditLogService;

    public PageResult<Gate> page(int page, int size, String keyword) {
        LambdaQueryWrapper<Gate> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(Gate::getGateName, keyword.trim())
                    .or().like(Gate::getGateNo, keyword.trim()));
        }
        qw.orderByAsc(Gate::getId);
        Page<Gate> result = gateMapper.selectPage(new Page<>(page, size), qw);
        return PageResult.of(result.getTotal(), page, size, result.getRecords());
    }

    public List<Gate> enabled() {
        return gateMapper.selectList(new LambdaQueryWrapper<Gate>()
                .eq(Gate::getStatus, 1).orderByAsc(Gate::getId));
    }

    @Transactional
    public Gate save(Gate gate) {
        if (!StringUtils.hasText(gate.getGateNo()) || !StringUtils.hasText(gate.getGateName())) {
            throw new BizException("门岗编号与名称不能为空");
        }
        if (gate.getDirection() == null) {
            gate.setDirection("BOTH");
        }
        if (gate.getId() == null) {
            gate.setStatus(gate.getStatus() == null ? 1 : gate.getStatus());
            gateMapper.insert(gate);
            auditLogService.log("门岗", "新增", gate.getGateName());
        } else {
            gateMapper.updateById(gate);
            auditLogService.log("门岗", "修改", gate.getGateName());
        }
        return gate;
    }

    @Transactional
    public void delete(Long id) {
        Gate gate = gateMapper.selectById(id);
        if (gate == null) {
            return;
        }
        gateMapper.deleteById(id);
        auditLogService.log("门岗", "删除", gate.getGateName());
    }
}

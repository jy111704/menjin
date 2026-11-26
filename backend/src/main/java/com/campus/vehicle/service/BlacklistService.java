package com.campus.vehicle.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.vehicle.common.BizException;
import com.campus.vehicle.common.PageResult;
import com.campus.vehicle.entity.Blacklist;
import com.campus.vehicle.mapper.BlacklistMapper;
import com.campus.vehicle.security.UserContext;
import com.campus.vehicle.util.PlateUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BlacklistService {

    private final BlacklistMapper blacklistMapper;
    private final AuditLogService auditLogService;

    /** 查询生效中的黑名单记录 */
    public Blacklist findActive(String plateNo) {
        if (!StringUtils.hasText(plateNo)) {
            return null;
        }
        return blacklistMapper.selectOne(new LambdaQueryWrapper<Blacklist>()
                .eq(Blacklist::getPlateNo, plateNo)
                .eq(Blacklist::getStatus, 1)
                .and(w -> w.isNull(Blacklist::getExpireTime)
                        .or().gt(Blacklist::getExpireTime, LocalDateTime.now()))
                .orderByDesc(Blacklist::getId)
                .last("limit 1"));
    }

    public PageResult<Blacklist> page(int page, int size, String keyword, Integer status) {
        LambdaQueryWrapper<Blacklist> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(Blacklist::getPlateNo, keyword.trim())
                    .or().like(Blacklist::getReason, keyword.trim()));
        }
        if (status != null) {
            qw.eq(Blacklist::getStatus, status);
        }
        qw.orderByDesc(Blacklist::getCreateTime);
        Page<Blacklist> result = blacklistMapper.selectPage(new Page<>(page, size), qw);
        return PageResult.of(result.getTotal(), page, size, result.getRecords());
    }

    @Transactional
    public void save(Blacklist form) {
        String plate = PlateUtil.normalize(form.getPlateNo());
        if (!PlateUtil.isValid(plate)) {
            throw new BizException("车牌号格式不正确");
        }
        if (!StringUtils.hasText(form.getReason())) {
            throw new BizException("请填写加入黑名单的原因");
        }
        form.setPlateNo(plate);
        form.setStatus(1);
        if (form.getLevel() == null) {
            form.setLevel("MID");
        }
        var user = UserContext.get();
        form.setCreatorName(user == null ? "系统" : user.getRealName());

        Blacklist exist = blacklistMapper.selectOne(new LambdaQueryWrapper<Blacklist>()
                .eq(Blacklist::getPlateNo, plate));
        if (exist != null) {
            form.setId(exist.getId());
            form.setStatus(1);
            blacklistMapper.updateById(form);
            auditLogService.log("黑名单", "更新", "车牌 " + plate + "，原因：" + form.getReason());
        } else {
            blacklistMapper.insert(form);
            auditLogService.log("黑名单", "新增", "车牌 " + plate + "，原因：" + form.getReason());
        }
    }

    @Transactional
    public void toggle(Long id, Integer status) {
        Blacklist entity = blacklistMapper.selectById(id);
        if (entity == null) {
            throw new BizException("记录不存在");
        }
        entity.setStatus(status);
        blacklistMapper.updateById(entity);
        auditLogService.log("黑名单", status == 1 ? "恢复生效" : "解除", "车牌 " + entity.getPlateNo());
    }

    @Transactional
    public void delete(Long id) {
        Blacklist entity = blacklistMapper.selectById(id);
        if (entity != null) {
            blacklistMapper.deleteById(id);
            auditLogService.log("黑名单", "删除", "车牌 " + entity.getPlateNo());
        }
    }
}

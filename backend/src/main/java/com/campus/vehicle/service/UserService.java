package com.campus.vehicle.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.vehicle.common.BizException;
import com.campus.vehicle.common.PageResult;
import com.campus.vehicle.dto.UserRequest;
import com.campus.vehicle.entity.SysUser;
import com.campus.vehicle.mapper.SysUserMapper;
import com.campus.vehicle.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public PageResult<SysUser> page(int page, int size, String keyword, String role, Integer status) {
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            qw.and(w -> w.like(SysUser::getUsername, kw)
                    .or().like(SysUser::getRealName, kw)
                    .or().like(SysUser::getPhone, kw));
        }
        if (StringUtils.hasText(role)) {
            qw.eq(SysUser::getRole, role);
        }
        if (status != null) {
            qw.eq(SysUser::getStatus, status);
        }
        qw.orderByAsc(SysUser::getId);
        Page<SysUser> result = userMapper.selectPage(new Page<>(page, size), qw);
        return PageResult.of(result.getTotal(), page, size, result.getRecords());
    }

    public List<SysUser> guards() {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getRole, "GUARD", "ADMIN")
                .eq(SysUser::getStatus, 1)
                .orderByAsc(SysUser::getId));
    }

    @Transactional
    public SysUser create(UserRequest req) {
        if (!StringUtils.hasText(req.getUsername()) || !StringUtils.hasText(req.getRealName())) {
            throw new BizException("账号与姓名不能为空");
        }
        if (!List.of("ADMIN", "GUARD").contains(req.getRole())) {
            throw new BizException("角色不合法");
        }
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, req.getUsername().trim()));
        if (exists != null && exists > 0) {
            throw new BizException("账号已存在");
        }
        if (!StringUtils.hasText(req.getPassword()) || req.getPassword().length() < 6) {
            throw new BizException("初始密码至少 6 位");
        }
        SysUser user = new SysUser();
        user.setUsername(req.getUsername().trim());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setRealName(req.getRealName().trim());
        user.setPhone(StringUtils.hasText(req.getPhone()) ? req.getPhone().trim() : "");
        user.setRole(req.getRole());
        user.setDept(req.getDept());
        user.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        userMapper.insert(user);
        auditLogService.log("用户管理", "新增", "账号 " + user.getUsername() + "，角色 " + user.getRole());
        return user;
    }

    @Transactional
    public SysUser update(UserRequest req) {
        SysUser user = userMapper.selectById(req.getId());
        if (user == null) {
            throw new BizException("用户不存在");
        }
        if (!List.of("ADMIN", "GUARD").contains(req.getRole())) {
            throw new BizException("角色不合法");
        }
        if ("ADMIN".equals(user.getRole()) && !"ADMIN".equals(req.getRole())) {
            long adminCount = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getRole, "ADMIN").eq(SysUser::getStatus, 1));
            if (adminCount <= 1) {
                throw new BizException("系统至少需要保留一个管理员账号");
            }
        }
        user.setRealName(req.getRealName().trim());
        user.setPhone(req.getPhone());
        user.setRole(req.getRole());
        user.setDept(req.getDept());
        if (req.getStatus() != null) {
            if (req.getId().equals(UserContext.require().getId()) && req.getStatus() == 0) {
                throw new BizException("不能禁用当前登录账号");
            }
            user.setStatus(req.getStatus());
        }
        if (StringUtils.hasText(req.getPassword())) {
            if (req.getPassword().length() < 6) {
                throw new BizException("密码至少 6 位");
            }
            user.setPassword(passwordEncoder.encode(req.getPassword()));
        }
        userMapper.updateById(user);
        auditLogService.log("用户管理", "修改", "账号 " + user.getUsername());
        return user;
    }

    @Transactional
    public void resetPassword(Long id, String password) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        if (!StringUtils.hasText(password) || password.length() < 6) {
            throw new BizException("新密码至少 6 位");
        }
        user.setPassword(passwordEncoder.encode(password));
        userMapper.updateById(user);
        auditLogService.log("用户管理", "重置密码", "账号 " + user.getUsername());
    }

    @Transactional
    public void delete(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            return;
        }
        if (id.equals(UserContext.require().getId())) {
            throw new BizException("不能删除当前登录账号");
        }
        if ("ADMIN".equals(user.getRole())) {
            long adminCount = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getRole, "ADMIN").eq(SysUser::getStatus, 1));
            if (adminCount <= 1) {
                throw new BizException("系统至少需要保留一个管理员账号");
            }
        }
        userMapper.deleteById(id);
        auditLogService.log("用户管理", "删除", "账号 " + user.getUsername());
    }
}

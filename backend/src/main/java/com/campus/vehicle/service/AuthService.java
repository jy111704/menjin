package com.campus.vehicle.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.vehicle.common.BizException;
import com.campus.vehicle.dto.LoginRequest;
import com.campus.vehicle.dto.LoginResponse;
import com.campus.vehicle.entity.SysUser;
import com.campus.vehicle.mapper.SysUserMapper;
import com.campus.vehicle.security.JwtUtil;
import com.campus.vehicle.security.LoginUser;
import com.campus.vehicle.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuditLogService auditLogService;

    public LoginResponse login(LoginRequest request, String ip) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername().trim()));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BizException(401, "账号或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(403, "账号已被禁用，请联系管理员");
        }
        user.setLastLoginTime(LocalDateTime.now());
        user.setLastLoginIp(ip);
        userMapper.updateById(user);

        LoginUser loginUser = new LoginUser(user.getId(), user.getUsername(), user.getRealName(),
                user.getRole(), user.getPhone());
        String token = jwtUtil.generate(loginUser);
        auditLogService.log("认证", "登录", "账号 " + user.getUsername() + " 登录成功");
        return new LoginResponse(token, jwtUtil.getExpireMillis() / 1000, user);
    }

    public SysUser current() {
        LoginUser loginUser = UserContext.require();
        SysUser user = userMapper.selectById(loginUser.getId());
        if (user == null) {
            throw new BizException(401, "账号不存在");
        }
        return user;
    }

    public void changePassword(String oldPassword, String newPassword) {
        SysUser user = current();
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BizException("原密码不正确");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new BizException("新密码至少 6 位");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        auditLogService.log("认证", "修改密码", "账号 " + user.getUsername() + " 修改了登录密码");
    }
}

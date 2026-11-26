package com.campus.vehicle.controller;

import com.campus.vehicle.common.R;
import com.campus.vehicle.dto.LoginRequest;
import com.campus.vehicle.dto.LoginResponse;
import com.campus.vehicle.entity.SysUser;
import com.campus.vehicle.service.AuditLogService;
import com.campus.vehicle.service.AuthService;
import com.campus.vehicle.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuditLogService auditLogService;

    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return R.ok(authService.login(request, IpUtil.of(servletRequest)));
    }

    @GetMapping("/me")
    public R<SysUser> me() {
        return R.ok(authService.current());
    }

    @PostMapping("/password")
    public R<Void> changePassword(@RequestBody Map<String, String> body) {
        authService.changePassword(body.get("oldPassword"), body.get("newPassword"));
        return R.ok();
    }

    @PostMapping("/logout")
    public R<Void> logout() {
        auditLogService.log("认证", "退出", "退出登录");
        return R.ok();
    }
}

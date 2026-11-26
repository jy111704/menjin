package com.campus.vehicle.controller;

import com.campus.vehicle.common.PageResult;
import com.campus.vehicle.common.R;
import com.campus.vehicle.dto.UserRequest;
import com.campus.vehicle.entity.SysUser;
import com.campus.vehicle.security.RequireRole;
import com.campus.vehicle.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@RequireRole("ADMIN")
public class UserController {

    private final UserService userService;

    @GetMapping
    public R<PageResult<SysUser>> page(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "10") int size,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) String role,
                                       @RequestParam(required = false) Integer status) {
        return R.ok(userService.page(page, size, keyword, role, status));
    }

    @GetMapping("/guards")
    public R<List<SysUser>> guards() {
        return R.ok(userService.guards());
    }

    @PostMapping
    public R<SysUser> create(@RequestBody UserRequest request) {
        return R.ok(userService.create(request));
    }

    @PutMapping("/{id}")
    public R<SysUser> update(@PathVariable Long id, @RequestBody UserRequest request) {
        request.setId(id);
        return R.ok(userService.update(request));
    }

    @PostMapping("/{id}/reset-password")
    public R<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        userService.resetPassword(id, body.get("password"));
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return R.ok();
    }
}

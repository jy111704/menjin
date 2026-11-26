package com.campus.vehicle.controller;

import com.campus.vehicle.common.R;
import com.campus.vehicle.entity.SysConfig;
import com.campus.vehicle.security.RequireRole;
import com.campus.vehicle.service.AuditLogService;
import com.campus.vehicle.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/configs")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService configService;
    private final AuditLogService auditLogService;

    @GetMapping
    public R<List<SysConfig>> list() {
        return R.ok(configService.list());
    }

    @GetMapping("/raw")
    public R<Map<String, String>> raw() {
        return R.ok(configService.asMap());
    }

    @PutMapping("/{key}")
    @RequireRole("ADMIN")
    public R<Void> update(@PathVariable String key, @RequestBody Map<String, String> body) {
        configService.update(key, body.get("value"));
        auditLogService.log("系统参数", "修改", key + " -> " + body.get("value"));
        return R.ok();
    }
}

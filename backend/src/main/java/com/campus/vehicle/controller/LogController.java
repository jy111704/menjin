package com.campus.vehicle.controller;

import com.campus.vehicle.common.PageResult;
import com.campus.vehicle.common.R;
import com.campus.vehicle.entity.AuditLog;
import com.campus.vehicle.security.RequireRole;
import com.campus.vehicle.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
@RequireRole("ADMIN")
public class LogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public R<PageResult<AuditLog>> page(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "15") int size,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) String module,
                                        @RequestParam(required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                        @RequestParam(required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return R.ok(auditLogService.page(page, size, keyword, module, startDate, endDate));
    }
}

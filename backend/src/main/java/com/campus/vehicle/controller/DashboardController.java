package com.campus.vehicle.controller;

import com.campus.vehicle.common.R;
import com.campus.vehicle.dto.stat.TrendPoint;
import com.campus.vehicle.entity.PassCode;
import com.campus.vehicle.security.RequireRole;
import com.campus.vehicle.service.DashboardService;
import com.campus.vehicle.service.PassService;
import com.campus.vehicle.service.RecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@RequireRole({"ADMIN", "GUARD"})
public class DashboardController {

    private final DashboardService dashboardService;
    private final PassService passService;
    private final RecordService recordService;

    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        return R.ok(dashboardService.overview());
    }

    @GetMapping("/trend")
    public R<List<TrendPoint>> trend(@RequestParam(defaultValue = "7") int days) {
        return R.ok(dashboardService.trend(days));
    }

    @GetMapping("/charts")
    public R<Map<String, Object>> charts() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("gates", dashboardService.gateDistribution());
        data.put("hours", dashboardService.hourDistribution());
        data.put("rejects", dashboardService.rejectReasons());
        return R.ok(data);
    }

    @GetMapping("/on-site")
    public R<List<Map<String, Object>>> onSite() {
        return R.ok(recordService.onSite());
    }

    @GetMapping("/recent-passes")
    public R<List<PassCode>> recentPasses() {
        return R.ok(passService.page(1, 6, null, null, null).getRecords());
    }
}

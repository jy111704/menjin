package com.campus.vehicle.controller;

import com.campus.vehicle.common.PageResult;
import com.campus.vehicle.common.R;
import com.campus.vehicle.entity.AccessRecord;
import com.campus.vehicle.security.RequireRole;
import com.campus.vehicle.service.RecordService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
@RequireRole({"ADMIN", "GUARD"})
public class RecordController {

    private final RecordService recordService;

    @GetMapping
    public R<PageResult<AccessRecord>> page(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String direction,
                                            @RequestParam(required = false) String result,
                                            @RequestParam(required = false) String passType,
                                            @RequestParam(required = false) Long gateId,
                                            @RequestParam(required = false)
                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                            @RequestParam(required = false)
                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return R.ok(recordService.page(page, size, keyword, direction, result, passType, gateId, startDate, endDate));
    }

    @GetMapping("/on-site")
    public R<List<Map<String, Object>>> onSite() {
        return R.ok(recordService.onSite());
    }

    @GetMapping("/export")
    public void export(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String direction,
                       @RequestParam(required = false) String result,
                       @RequestParam(required = false) String passType,
                       @RequestParam(required = false) Long gateId,
                       @RequestParam(required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                       @RequestParam(required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                       HttpServletResponse response) throws Exception {
        recordService.export(keyword, direction, result, passType, gateId, startDate, endDate, response);
    }
}

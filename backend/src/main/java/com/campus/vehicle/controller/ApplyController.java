package com.campus.vehicle.controller;

import com.campus.vehicle.common.PageResult;
import com.campus.vehicle.common.R;
import com.campus.vehicle.dto.AuditRequest;
import com.campus.vehicle.entity.VisitorApply;
import com.campus.vehicle.security.RequireRole;
import com.campus.vehicle.service.VisitorApplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/applies")
@RequiredArgsConstructor
@RequireRole({"ADMIN", "GUARD"})
public class ApplyController {

    private final VisitorApplyService applyService;

    @GetMapping
    public R<PageResult<VisitorApply>> page(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false)
                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                            @RequestParam(required = false)
                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return R.ok(applyService.page(page, size, keyword, status, startDate, endDate));
    }

    @GetMapping("/pending-count")
    public R<Long> pendingCount() {
        return R.ok(applyService.pendingCount());
    }

    @GetMapping("/{id}")
    public R<VisitorApply> detail(@PathVariable Long id) {
        return R.ok(applyService.detail(id));
    }

    @PostMapping("/{id}/audit")
    public R<VisitorApply> audit(@PathVariable Long id, @RequestBody AuditRequest request) {
        return R.ok(applyService.audit(id, Boolean.TRUE.equals(request.getApprove()), request.getRemark()));
    }

    @PostMapping("/{id}/cancel")
    public R<VisitorApply> cancel(@PathVariable Long id) {
        VisitorApply apply = applyService.detail(id);
        return R.ok(applyService.cancel(apply.getApplyNo(), apply.getPhone(), true));
    }

    @PostMapping("/{id}/reissue")
    public R<VisitorApply> reissue(@PathVariable Long id) {
        return R.ok(applyService.reissue(id));
    }
}

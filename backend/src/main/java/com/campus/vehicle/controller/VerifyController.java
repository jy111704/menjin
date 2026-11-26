package com.campus.vehicle.controller;

import com.campus.vehicle.common.R;
import com.campus.vehicle.dto.VerifyRequest;
import com.campus.vehicle.dto.VerifyResult;
import com.campus.vehicle.entity.AccessRecord;
import com.campus.vehicle.entity.Gate;
import com.campus.vehicle.security.RequireRole;
import com.campus.vehicle.service.GateService;
import com.campus.vehicle.service.RecordService;
import com.campus.vehicle.service.VerifyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/verify")
@RequiredArgsConstructor
@RequireRole({"ADMIN", "GUARD"})
public class VerifyController {

    private final VerifyService verifyService;
    private final GateService gateService;
    private final RecordService recordService;

    /** 扫码或车牌核验：一次调用完成放行/拦截 + 出入登记 */
    @PostMapping
    public R<VerifyResult> verify(@RequestBody VerifyRequest request) {
        return R.ok(verifyService.verify(request));
    }

    @GetMapping("/gates")
    public R<List<Gate>> gates() {
        return R.ok(gateService.enabled());
    }

    @GetMapping("/recent")
    public R<List<AccessRecord>> recent(@RequestParam(defaultValue = "8") int limit) {
        return R.ok(recordService.recent(Math.min(limit, 50)));
    }
}

package com.campus.vehicle.controller;

import com.campus.vehicle.common.PageResult;
import com.campus.vehicle.common.R;
import com.campus.vehicle.entity.Gate;
import com.campus.vehicle.security.RequireRole;
import com.campus.vehicle.service.GateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/gates")
@RequiredArgsConstructor
public class GateController {

    private final GateService gateService;

    @GetMapping
    public R<PageResult<Gate>> page(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(required = false) String keyword) {
        return R.ok(gateService.page(page, size, keyword));
    }

    @GetMapping("/enabled")
    public R<List<Gate>> enabled() {
        return R.ok(gateService.enabled());
    }

    @PostMapping
    @RequireRole("ADMIN")
    public R<Gate> save(@RequestBody Gate gate) {
        return R.ok(gateService.save(gate));
    }

    @DeleteMapping("/{id}")
    @RequireRole("ADMIN")
    public R<Void> delete(@PathVariable Long id) {
        gateService.delete(id);
        return R.ok();
    }
}

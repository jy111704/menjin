package com.campus.vehicle.controller;

import com.campus.vehicle.common.PageResult;
import com.campus.vehicle.common.R;
import com.campus.vehicle.dto.AuditRequest;
import com.campus.vehicle.dto.VehicleRequest;
import com.campus.vehicle.entity.Vehicle;
import com.campus.vehicle.security.RequireRole;
import com.campus.vehicle.service.VehicleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;

    @GetMapping
    public R<PageResult<Vehicle>> page(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "10") int size,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) String type) {
        return R.ok(vehicleService.page(page, size, keyword, status, type));
    }

    @GetMapping("/approved")
    public R<List<Vehicle>> approved() {
        return R.ok(vehicleService.approvedList());
    }

    @PostMapping
    @RequireRole("ADMIN")
    public R<Vehicle> create(@Valid @RequestBody VehicleRequest request) {
        return R.ok(vehicleService.create(request));
    }

    @PutMapping("/{id}")
    @RequireRole("ADMIN")
    public R<Vehicle> update(@PathVariable Long id, @Valid @RequestBody VehicleRequest request) {
        return R.ok(vehicleService.update(id, request));
    }

    @PostMapping("/{id}/audit")
    @RequireRole("ADMIN")
    public R<Void> audit(@PathVariable Long id, @RequestBody AuditRequest request) {
        vehicleService.audit(id, Boolean.TRUE.equals(request.getApprove()), request.getRemark());
        return R.ok();
    }

    @PostMapping("/{id}/status")
    @RequireRole("ADMIN")
    public R<Void> changeStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        vehicleService.changeStatus(id, body.get("status"));
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @RequireRole("ADMIN")
    public R<Void> delete(@PathVariable Long id) {
        vehicleService.delete(id);
        return R.ok();
    }
}

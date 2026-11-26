package com.campus.vehicle.controller;

import com.campus.vehicle.common.PageResult;
import com.campus.vehicle.common.R;
import com.campus.vehicle.entity.PassCode;
import com.campus.vehicle.security.RequireRole;
import com.campus.vehicle.service.PassService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/passes")
@RequiredArgsConstructor
@RequireRole({"ADMIN", "GUARD"})
public class PassController {

    private final PassService passService;

    @GetMapping
    public R<PageResult<PassCode>> page(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "10") int size,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(required = false) String passType) {
        return R.ok(passService.page(page, size, keyword, status, passType));
    }

    @GetMapping("/{id}/qr")
    public R<PassCode> qr(@PathVariable Long id) {
        return R.ok(passService.fillQr(passService.getById(id)));
    }

    @PostMapping("/{id}/void")
    public R<Void> voidPass(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        PassCode pass = passService.getById(id);
        passService.voidPass(pass, body == null ? "后台手动作废" : body.getOrDefault("reason", "后台手动作废"));
        return R.ok();
    }
}

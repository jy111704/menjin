package com.campus.vehicle.controller;

import com.campus.vehicle.common.PageResult;
import com.campus.vehicle.common.R;
import com.campus.vehicle.entity.Blacklist;
import com.campus.vehicle.security.RequireRole;
import com.campus.vehicle.service.BlacklistService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/blacklist")
@RequiredArgsConstructor
@RequireRole({"ADMIN", "GUARD"})
public class BlacklistController {

    private final BlacklistService blacklistService;

    @GetMapping
    public R<PageResult<Blacklist>> page(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) Integer status) {
        return R.ok(blacklistService.page(page, size, keyword, status));
    }

    @PostMapping
    @RequireRole("ADMIN")
    public R<Void> save(@RequestBody Blacklist form) {
        blacklistService.save(form);
        return R.ok();
    }

    @PostMapping("/{id}/status")
    @RequireRole("ADMIN")
    public R<Void> toggle(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        blacklistService.toggle(id, body.getOrDefault("status", 0));
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @RequireRole("ADMIN")
    public R<Void> delete(@PathVariable Long id) {
        blacklistService.delete(id);
        return R.ok();
    }
}

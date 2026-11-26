package com.campus.vehicle.controller;

import com.campus.vehicle.common.R;
import com.campus.vehicle.dto.VisitorApplyRequest;
import com.campus.vehicle.entity.VisitorApply;
import com.campus.vehicle.service.ConfigService;
import com.campus.vehicle.service.PassService;
import com.campus.vehicle.service.VisitorApplyService;
import com.campus.vehicle.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 访客自助端：无需登录，凭申请单号 + 手机号查询
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final VisitorApplyService applyService;
    private final PassService passService;
    private final ConfigService configService;

    @GetMapping("/health")
    public R<String> health() {
        return R.ok("ok");
    }

    /** 申请须知：由后台参数实时驱动，前端表单据此做出提示与校验 */
    @GetMapping("/notice")
    public R<Map<String, Object>> notice() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("autoAudit", configService.getBool("audit.auto.enable", true));
        data.put("advanceDays", configService.getInt("audit.advance.days", 7));
        data.put("maxValidHours", configService.getInt("pass.max.valid.hours", 24));
        data.put("advanceMinutes", configService.getInt("pass.advance.minutes", 120));
        data.put("worktimeStart", configService.get("audit.worktime.start", "06:00"));
        data.put("worktimeEnd", configService.get("audit.worktime.end", "23:00"));
        data.put("today", LocalDate.now().toString());
        return R.ok(data);
    }

    @PostMapping("/apply")
    public R<VisitorApply> apply(@Valid @RequestBody VisitorApplyRequest request, HttpServletRequest servletRequest) {
        return R.ok(applyService.submit(request, IpUtil.of(servletRequest)));
    }

    @GetMapping("/apply/{applyNo}")
    public R<VisitorApply> query(@PathVariable String applyNo, @RequestParam String phone) {
        return R.ok(applyService.query(applyNo, phone));
    }

    @PostMapping("/apply/{applyNo}/cancel")
    public R<VisitorApply> cancel(@PathVariable String applyNo, @RequestParam String phone) {
        return R.ok(applyService.cancel(applyNo, phone, false));
    }

    /** 凭令牌查看通行码（二维码内容本身即为凭证） */
    @GetMapping("/pass/{token}")
    public R<Map<String, Object>> pass(@PathVariable String token) {
        var pass = passService.findByToken(token);
        if (pass == null) {
            return R.fail(404, "通行码不存在");
        }
        passService.fillQr(pass);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("passNo", pass.getPassNo());
        data.put("plateNo", pass.getPlateNo());
        data.put("holderName", pass.getHolderName());
        data.put("validStart", pass.getValidStart());
        data.put("validEnd", pass.getValidEnd());
        data.put("status", pass.getStatus());
        data.put("qrImage", pass.getQrImage());
        return R.ok(data);
    }
}

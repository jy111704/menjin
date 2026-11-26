package com.campus.vehicle.controller;

import com.campus.vehicle.common.R;
import com.campus.vehicle.dto.PlateOcrResult;
import com.campus.vehicle.security.RequireRole;
import com.campus.vehicle.service.PlateOcrService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ocr")
@RequiredArgsConstructor
@RequireRole({"ADMIN", "GUARD"})
public class OcrController {

    private final PlateOcrService plateOcrService;

    /** 手机门岗拍照后调用：只负责识别车牌，不做放行判断 */
    @PostMapping(value = "/plate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<PlateOcrResult> plate(@RequestPart("file") MultipartFile file) {
        return R.ok(plateOcrService.recognize(file));
    }
}

package com.campus.vehicle.service;

import com.campus.vehicle.common.BizException;
import com.campus.vehicle.dto.PlateOcrResult;
import com.campus.vehicle.util.PlateUtil;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.ocr.v20181119.OcrClient;
import com.tencentcloudapi.ocr.v20181119.models.LicensePlateInfo;
import com.tencentcloudapi.ocr.v20181119.models.LicensePlateOCRRequest;
import com.tencentcloudapi.ocr.v20181119.models.LicensePlateOCRResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.Locale;

/**
 * 车牌识别：腾讯云 OCR LicensePlateOCR。
 * 未配置密钥时返回明确提示，前端仍可手动输入车牌完成核验。
 */
@Slf4j
@Service
public class PlateOcrService {

    private static final long MAX_BYTES = 8L * 1024 * 1024;

    @Value("${campus.ocr.secret-id:}")
    private String secretId;

    @Value("${campus.ocr.secret-key:}")
    private String secretKey;

    @Value("${campus.ocr.region:ap-guangzhou}")
    private String region;

    @Value("${campus.ocr.endpoint:ocr.tencentcloudapi.com}")
    private String endpoint;

    /** 仅用于联调：配置后跳过云 OCR，直接返回这个车牌 */
    @Value("${campus.ocr.mock-plate:}")
    private String mockPlate;

    public PlateOcrResult recognize(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请先拍照上传");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BizException("图片不能超过 8MB");
        }

        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        boolean image = contentType.startsWith("image/")
                || filename.endsWith(".jpg")
                || filename.endsWith(".jpeg")
                || filename.endsWith(".png")
                || filename.endsWith(".webp");
        if (!image) {
            throw new BizException("请上传 JPG、PNG 或 WEBP 图片");
        }

        if (StringUtils.hasText(mockPlate)) {
            String plate = PlateUtil.normalize(mockPlate);
            if (!PlateUtil.isValid(plate)) {
                throw new BizException("模拟车牌格式不正确：" + mockPlate);
            }
            return new PlateOcrResult(plate, 100L, "模拟", "mock");
        }

        if (!StringUtils.hasText(secretId) || !StringUtils.hasText(secretKey)) {
            throw new BizException(503, "OCR 未配置，请手动输入车牌，或在服务器配置腾讯云密钥");
        }

        try {
            String imageBase64 = Base64.getEncoder().encodeToString(file.getBytes());

            HttpProfile httpProfile = new HttpProfile();
            httpProfile.setEndpoint(endpoint);
            httpProfile.setConnTimeout(10);
            httpProfile.setReadTimeout(20);

            ClientProfile clientProfile = new ClientProfile();
            clientProfile.setHttpProfile(httpProfile);

            OcrClient client = new OcrClient(new Credential(secretId, secretKey), region, clientProfile);
            LicensePlateOCRRequest request = new LicensePlateOCRRequest();
            request.setImageBase64(imageBase64);

            LicensePlateOCRResponse response = client.LicensePlateOCR(request);
            LicensePlateInfo[] infos = response.getLicensePlateInfos();
            if (infos == null || infos.length == 0) {
                throw new BizException("未识别到车牌，请靠近车牌、保持水平后重拍");
            }

            LicensePlateInfo best = Arrays.stream(infos)
                    .filter(item -> StringUtils.hasText(item.getNumber()))
                    .max(Comparator.comparingLong((LicensePlateInfo item) ->
                            item.getConfidence() == null ? 0L : item.getConfidence()))
                    .orElse(null);
            if (best == null) {
                throw new BizException("未识别到车牌，请调整角度后重拍");
            }

            String plate = PlateUtil.normalize(best.getNumber());
            if (!PlateUtil.isValid(plate)) {
                throw new BizException("识别结果不是有效车牌：" + best.getNumber());
            }

            long confidence = best.getConfidence() == null ? 0L : best.getConfidence();
            return new PlateOcrResult(plate, confidence, best.getColor(), "tencent");
        } catch (TencentCloudSDKException e) {
            log.warn("腾讯云车牌识别失败: {}", e.getMessage());
            throw new BizException(502, "车牌识别服务暂时不可用：" + e.getMessage());
        } catch (IOException e) {
            throw new BizException("读取图片失败，请重试");
        }
    }
}

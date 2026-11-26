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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    /** tencent / local */
    @Value("${campus.ocr.provider:tencent}")
    private String provider;

    @Value("${campus.ocr.local-url:http://127.0.0.1:8715}")
    private String localUrl;

    /** 仅用于联调：配置后跳过 OCR，直接返回这个车牌 */
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

        String activeProvider = StringUtils.hasText(provider) ? provider.trim().toLowerCase(Locale.ROOT) : "tencent";
        if ("local".equals(activeProvider)) {
            return recognizeLocal(file);
        }
        if (!"tencent".equals(activeProvider)) {
            throw new BizException(500, "未知 OCR provider：" + provider);
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

    /**
     * 调用服务器本机的 HyperLPR3 FastAPI 服务（127.0.0.1:8715）。
     * 服务由 CampusPlateOcr 计划任务启动，不对外暴露端口。
     */
    private PlateOcrResult recognizeLocal(MultipartFile file) {
        try {
            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return "plate.jpg";
                }
            };
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", resource);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(3000);
            factory.setReadTimeout(20000);
            RestTemplate restTemplate = new RestTemplate(factory);

            String baseUrl = localUrl.endsWith("/")
                    ? localUrl.substring(0, localUrl.length() - 1)
                    : localUrl;
            Map<?, ?> response = restTemplate.postForObject(baseUrl + "/api/v1/rec", request, Map.class);
            if (response == null || !Boolean.TRUE.equals(response.get("ok"))) {
                throw new BizException("本地车牌识别返回异常");
            }

            Object listObject = response.get("plate_list");
            if (!(listObject instanceof List)) {
                throw new BizException("未识别到车牌，请靠近车牌、保持水平后重拍");
            }
            List<?> plates = (List<?>) listObject;
            if (plates.isEmpty()) {
                throw new BizException("未识别到车牌，请靠近车牌、保持水平后重拍");
            }

            Map<?, ?> best = null;
            double bestConfidence = -1;
            for (Object item : plates) {
                if (!(item instanceof Map)) {
                    continue;
                }
                Map<?, ?> plate = (Map<?, ?>) item;
                double confidence = plate.get("conf") instanceof Number
                        ? ((Number) plate.get("conf")).doubleValue() : 0;
                if (confidence > bestConfidence) {
                    bestConfidence = confidence;
                    best = plate;
                }
            }
            if (best == null) {
                throw new BizException("未识别到车牌，请调整角度后重拍");
            }

            String raw = best.get("code") == null ? "" : String.valueOf(best.get("code"));
            String plate = PlateUtil.normalize(raw);
            if (!PlateUtil.isValid(plate)) {
                throw new BizException("识别结果不是有效车牌：" + raw);
            }
            long confidence = bestConfidence <= 1.0
                    ? Math.round(bestConfidence * 100)
                    : Math.round(bestConfidence);
            String color = best.get("plate_type") == null ? null : String.valueOf(best.get("plate_type"));
            return new PlateOcrResult(plate, confidence, color, "local");
        } catch (RestClientException e) {
            log.warn("本地 OCR 调用失败: {}", e.getMessage());
            throw new BizException(502, "本地车牌识别服务不可用，请确认 OCR 服务已启动");
        } catch (IOException e) {
            throw new BizException("读取图片失败，请重试");
        }
    }
}

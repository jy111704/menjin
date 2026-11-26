package com.campus.vehicle.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 通行码签名：二维码内容 = token + "." + HMAC-SHA256(token)
 * 即使有人伪造一个 token，没有密钥也无法生成合法签名，杜绝自行拼码入场。
 */
@Component
public class QrSignUtil {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secret;

    public QrSignUtil(@Value("${campus.qr.secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String randomToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public String sign(String token) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            byte[] digest = mac.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 22);
        } catch (Exception e) {
            throw new IllegalStateException("签名失败", e);
        }
    }

    public String payload(String token) {
        return token + "." + sign(token);
    }

    /** 返回校验通过的 token，失败返回 null */
    public String verify(String payload) {
        if (payload == null) {
            return null;
        }
        String value = payload.trim();
        int idx = value.lastIndexOf('.');
        if (idx <= 0 || idx == value.length() - 1) {
            return null;
        }
        String token = value.substring(0, idx);
        String sign = value.substring(idx + 1);
        String expected = sign(token);
        if (expected.length() != sign.length()) {
            return null;
        }
        int diff = 0;
        for (int i = 0; i < expected.length(); i++) {
            diff |= expected.charAt(i) ^ sign.charAt(i);
        }
        return diff == 0 ? token : null;
    }
}

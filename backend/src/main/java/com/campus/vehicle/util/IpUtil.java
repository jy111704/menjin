package com.campus.vehicle.util;

import jakarta.servlet.http.HttpServletRequest;

public final class IpUtil {

    private IpUtil() {
    }

    public static String of(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String[] headers = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"};
        for (String header : headers) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value)) {
                int idx = value.indexOf(',');
                return (idx > 0 ? value.substring(0, idx) : value).trim();
            }
        }
        String remote = request.getRemoteAddr();
        return "0:0:0:0:0:0:0:1".equals(remote) ? "127.0.0.1" : remote;
    }
}

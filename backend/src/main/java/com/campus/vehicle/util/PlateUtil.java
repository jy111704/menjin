package com.campus.vehicle.util;

import java.util.regex.Pattern;

/**
 * 中国车牌号规范化与校验
 */
public final class PlateUtil {

    /** 省份简称 + 发牌机关字母 + 5~6 位序号，末位允许挂/学/警/港/澳 */
    private static final Pattern PLATE = Pattern.compile(
            "^[京津冀晋蒙辽吉黑沪苏浙皖闽赣鲁豫鄂湘粤桂琼渝川贵云藏陕甘青宁新使领][A-Z][A-HJ-NP-Z0-9]{4,5}[A-HJ-NP-Z0-9挂学警港澳]$");

    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");

    private PlateUtil() {
    }

    /** 去空格、转大写、全角转半角 */
    public static String normalize(String plate) {
        if (plate == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : plate.trim().toCharArray()) {
            if (c == ' ' || c == '\t' || c == '-' || c == '·' || c == '•' || c == '.' || c == '－' || c == '　') {
                continue;
            }
            if (c >= '０' && c <= '９') {
                sb.append((char) (c - '０' + '0'));
            } else if (c >= 'ａ' && c <= 'ｚ') {
                sb.append((char) (c - 'ａ' + 'A'));
            } else if (c >= 'Ａ' && c <= 'Ｚ') {
                sb.append((char) (c - 'Ａ' + 'A'));
            } else {
                sb.append(Character.toUpperCase(c));
            }
        }
        return sb.toString();
    }

    public static boolean isValid(String plate) {
        return plate != null && PLATE.matcher(plate).matches();
    }

    public static boolean isPhone(String phone) {
        return phone != null && PHONE.matcher(phone).matches();
    }

    /** 车牌脱敏展示：京A·12345 -> 京A***45 */
    public static String mask(String plate) {
        if (plate == null || plate.length() < 5) {
            return plate;
        }
        return plate.substring(0, 2) + "***" + plate.substring(plate.length() - 2);
    }
}

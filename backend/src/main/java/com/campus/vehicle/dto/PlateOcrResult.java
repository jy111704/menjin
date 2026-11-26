package com.campus.vehicle.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlateOcrResult {

    /** 规范化后的车牌号，例如 粤A12345 */
    private String plateNo;

    /** 置信度，0-100 */
    private Long confidence;

    /** 车牌颜色，腾讯云返回值 */
    private String color;

    /** 识别来源：tencent / mock */
    private String provider;
}

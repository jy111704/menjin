package com.campus.vehicle.dto;

import lombok.Data;

@Data
public class VerifyRequest {

    /** 二维码内容 */
    private String code;

    /** 车牌号（车牌识别通道） */
    private String plateNo;

    private Long gateId;

    /** IN / OUT，留空自动判断 */
    private String direction;

    /** 人工放行备注 */
    private String remark;
}

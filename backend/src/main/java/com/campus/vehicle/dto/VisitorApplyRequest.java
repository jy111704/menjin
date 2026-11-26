package com.campus.vehicle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 访客在线提交的通行申请
 */
@Data
public class VisitorApplyRequest {

    @NotBlank(message = "请填写访客姓名")
    @Size(max = 50, message = "姓名过长")
    private String visitorName;

    @Pattern(regexp = "^$|^\\d{6}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$",
            message = "身份证号格式不正确")
    private String idCard;

    @NotBlank(message = "请填写手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "请填写车牌号")
    private String plateNo;

    private String plateColor;

    @NotBlank(message = "请填写来访事由")
    @Size(max = 200, message = "来访事由过长")
    private String visitReason;

    @NotBlank(message = "请填写被访人姓名")
    private String hostName;

    @NotBlank(message = "请填写被访人手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "被访人手机号格式不正确")
    private String hostPhone;

    private String hostDept;

    @NotNull(message = "请选择计划入场时间")
    private LocalDateTime planStart;

    @NotNull(message = "请选择计划离场时间")
    private LocalDateTime planEnd;
}

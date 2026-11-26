package com.campus.vehicle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class VehicleRequest {

    @NotBlank(message = "请填写车牌号")
    private String plateNo;

    private String plateColor;

    @NotBlank(message = "请填写车主姓名")
    private String ownerName;

    @NotBlank(message = "请填写车主手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "车主手机号格式不正确")
    private String ownerPhone;

    private Long ownerId;

    @NotBlank(message = "请选择车辆类型")
    private String vehicleType;

    @Size(max = 100, message = "部门名称过长")
    private String dept;

    private String brandModel;

    private LocalDate validStart;

    private LocalDate validEnd;
}

package com.campus.vehicle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("vehicle")
public class Vehicle {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String plateNo;
    private String plateColor;
    private String ownerName;
    private String ownerPhone;
    private Long ownerId;
    /** 教职工 / 学生 / 公务 / 后勤 */
    private String vehicleType;
    private String dept;
    private String brandModel;
    /** PENDING / APPROVED / REJECTED / DISABLED */
    private String status;
    private String auditRemark;
    private String auditorName;
    private LocalDateTime auditTime;
    private LocalDate validStart;
    private LocalDate validEnd;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

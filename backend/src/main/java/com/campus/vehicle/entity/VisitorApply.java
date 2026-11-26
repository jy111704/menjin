package com.campus.vehicle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("visitor_apply")
public class VisitorApply {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String applyNo;
    private String visitorName;
    private String idCard;
    private String phone;
    private String plateNo;
    private String plateColor;
    private String visitReason;
    private String hostName;
    private String hostPhone;
    private String hostDept;
    private LocalDateTime planStart;
    private LocalDateTime planEnd;
    /** PENDING / AUTO_APPROVED / APPROVED / REJECTED / EXPIRED / CANCELLED */
    private String status;
    /** AUTO / MANUAL */
    private String auditType;
    private String auditRemark;
    private String auditRules;
    private Long auditorId;
    private String auditorName;
    private LocalDateTime auditTime;
    private Long passId;
    private String clientIp;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 关联通行码信息（非表字段） */
    @TableField(exist = false)
    private PassCode pass;
}

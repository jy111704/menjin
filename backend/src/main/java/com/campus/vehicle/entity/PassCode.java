package com.campus.vehicle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pass_code")
public class PassCode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String passNo;
    private String qrToken;
    /** VISITOR / INTERNAL */
    private String passType;
    private Long applyId;
    private Long vehicleId;
    private String plateNo;
    private String holderName;
    private String holderPhone;
    private LocalDateTime validStart;
    private LocalDateTime validEnd;
    /** UNUSED / IN_USE / USED / EXPIRED / VOID */
    private String status;
    private LocalDateTime enterTime;
    private LocalDateTime exitTime;
    private String enterGate;
    private String exitGate;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 二维码图片（data:image/png;base64,...），非表字段 */
    @TableField(exist = false)
    private String qrImage;

    /** 二维码原始内容，非表字段 */
    @TableField(exist = false)
    private String qrContent;
}

package com.campus.vehicle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("access_record")
public class AccessRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String recordNo;
    private String plateNo;
    private String plateColor;
    /** VISITOR / INTERNAL */
    private String passType;
    private Long applyId;
    private Long passId;
    private Long vehicleId;
    /** IN / OUT */
    private String direction;
    private Long gateId;
    private String gateName;
    /** QR / PLATE / MANUAL / SYSTEM */
    private String channel;
    /** PASS / REJECT */
    private String verifyResult;
    private String rejectReason;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime occurTime;
    private String remark;
}

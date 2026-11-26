package com.campus.vehicle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("gate")
public class Gate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String gateNo;
    private String gateName;
    /** IN / OUT / BOTH */
    private String direction;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
}

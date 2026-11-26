package com.campus.vehicle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("blacklist")
public class Blacklist {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String plateNo;
    private String reason;
    /** LOW / MID / HIGH */
    private String level;
    private LocalDateTime expireTime;
    /** 1 生效 0 解除 */
    private Integer status;
    private String creatorName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

package com.campus.vehicle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_config")
public class SysConfig {

    @TableId(type = IdType.INPUT)
    private String configKey;

    private String configValue;
    private String configName;
    private String remark;
    private String groupName;
    private LocalDateTime updateTime;
}

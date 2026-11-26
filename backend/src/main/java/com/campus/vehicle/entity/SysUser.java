package com.campus.vehicle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    @JsonIgnore
    private String password;

    private String realName;
    private String phone;
    /** ADMIN 管理员 / GUARD 门卫保安 */
    private String role;
    private String dept;
    /** 1 启用 0 禁用 */
    private Integer status;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private String roleName;

    public String getRoleName() {
        if (role == null) {
            return "";
        }
        return switch (role) {
            case "ADMIN" -> "系统管理员";
            case "GUARD" -> "门卫保安";
            case "OWNER" -> "校内车主";
            default -> role;
        };
    }
}

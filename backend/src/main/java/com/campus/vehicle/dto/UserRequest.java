package com.campus.vehicle.dto;

import lombok.Data;

/**
 * 用户在后台维护账号时的入参
 */
@Data
public class UserRequest {

    private Long id;
    private String username;
    private String password;
    private String realName;
    private String phone;
    private String role;
    private String dept;
    private Integer status;
}

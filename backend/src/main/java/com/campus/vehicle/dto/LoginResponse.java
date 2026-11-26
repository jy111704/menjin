package com.campus.vehicle.dto;

import com.campus.vehicle.entity.SysUser;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {

    private String token;
    private long expiresIn;
    private SysUser user;
}

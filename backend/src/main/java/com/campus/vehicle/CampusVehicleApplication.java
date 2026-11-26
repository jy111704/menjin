package com.campus.vehicle;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 校园车辆出入登记管理系统
 * 访客车辆在线申请 -> 规则引擎自动审核 -> 生成限时二维码 -> 门岗核验放行 -> 离场自动销号
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.campus.vehicle.mapper")
public class CampusVehicleApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusVehicleApplication.class, args);
    }
}

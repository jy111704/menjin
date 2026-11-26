package com.campus.vehicle.init;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.vehicle.entity.SysUser;
import com.campus.vehicle.mapper.SysUserMapper;
import com.campus.vehicle.service.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 首次启动时创建管理员账号并加载系统参数。
 * 不写入任何演示业务数据，所有业务数据由实际使用产生。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final SysUserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final ConfigService configService;

    @Value("${campus.admin.username}")
    private String adminUsername;

    @Value("${campus.admin.password}")
    private String adminPassword;

    @Value("${campus.admin.real-name}")
    private String adminRealName;

    @Value("${campus.admin.phone}")
    private String adminPhone;

    @Value("${server.port:8080}")
    private int serverPort;

    @Override
    public void run(ApplicationArguments args) {
        configService.reload();

        Long exists = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, adminUsername));
        if (exists == null || exists == 0) {
            SysUser admin = new SysUser();
            admin.setUsername(adminUsername);
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setRealName(adminRealName);
            admin.setPhone(adminPhone);
            admin.setRole("ADMIN");
            admin.setDept("保卫处");
            admin.setStatus(1);
            userMapper.insert(admin);
            log.info("已创建初始管理员账号：{} / {}（请登录后立即修改密码）", adminUsername, adminPassword);
        }
        log.info("校园车辆出入登记管理系统启动完成，服务端口 {}，健康检查 /api/health", serverPort);
    }
}

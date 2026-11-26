-- 由 classpath:db/schema.sql 自动执行（幂等），内容与 sql/01_schema.sql 一致
-- =====================================================================
-- 校园车辆出入登记管理系统 —— 数据库结构
-- DB: MySQL 8.0+   字符集: utf8mb4
-- 说明：本脚本只创建结构与必需的基础配置，不写入任何演示业务数据。
--      管理员账号由后端启动时自动初始化（见 DataInitializer）。
-- =====================================================================





-- ---------------------------------------------------------------------
-- 1. 系统用户（管理员 / 门卫保安 / 校内车主）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `username`        VARCHAR(50)  NOT NULL COMMENT '登录账号',
  `password`        VARCHAR(100) NOT NULL COMMENT 'BCrypt 密文',
  `real_name`       VARCHAR(50)  NOT NULL COMMENT '姓名',
  `phone`           VARCHAR(20)  NOT NULL COMMENT '手机号',
  `role`            VARCHAR(20)  NOT NULL COMMENT 'ADMIN 管理员 / GUARD 门卫 / OWNER 校内车主',
  `dept`            VARCHAR(100)          DEFAULT NULL COMMENT '所属部门',
  `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 0 禁用',
  `last_login_time` DATETIME              DEFAULT NULL,
  `last_login_ip`   VARCHAR(64)           DEFAULT NULL,
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`),
  KEY `idx_user_phone` (`phone`),
  KEY `idx_user_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户';

-- ---------------------------------------------------------------------
-- 2. 校内车辆档案（长期通行车辆，按车牌识别放行）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `vehicle` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `plate_no`      VARCHAR(20)  NOT NULL COMMENT '车牌号（统一大写去空格）',
  `plate_color`   VARCHAR(10)  NOT NULL DEFAULT '蓝牌' COMMENT '蓝牌/黄牌/绿牌/白牌',
  `owner_name`    VARCHAR(50)  NOT NULL COMMENT '车主姓名',
  `owner_phone`   VARCHAR(20)  NOT NULL COMMENT '车主手机号',
  `owner_id`      BIGINT                DEFAULT NULL COMMENT '关联系统账号',
  `vehicle_type`  VARCHAR(20)  NOT NULL DEFAULT '教职工' COMMENT '教职工/学生/公务/后勤',
  `dept`          VARCHAR(100)          DEFAULT NULL COMMENT '所属部门/院系',
  `brand_model`   VARCHAR(100)          DEFAULT NULL COMMENT '品牌型号',
  `status`        VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING 待审核 / APPROVED 已通过 / REJECTED 已驳回 / DISABLED 已停用',
  `audit_remark`  VARCHAR(255)          DEFAULT NULL COMMENT '审核意见',
  `auditor_name`  VARCHAR(50)           DEFAULT NULL,
  `audit_time`    DATETIME              DEFAULT NULL,
  `valid_start`   DATE                  DEFAULT NULL COMMENT '通行有效期起',
  `valid_end`     DATE                  DEFAULT NULL COMMENT '通行有效期止，为空表示长期',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vehicle_plate` (`plate_no`),
  KEY `idx_vehicle_status` (`status`),
  KEY `idx_vehicle_owner` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='校内车辆档案';

-- ---------------------------------------------------------------------
-- 3. 访客通行申请
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `visitor_apply` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `apply_no`       VARCHAR(32)  NOT NULL COMMENT '申请单号',
  `visitor_name`   VARCHAR(50)  NOT NULL COMMENT '访客姓名',
  `id_card`        VARCHAR(32)           DEFAULT NULL COMMENT '证件号（脱敏存储，后4位参与核验）',
  `phone`          VARCHAR(20)  NOT NULL COMMENT '访客手机号',
  `plate_no`       VARCHAR(20)  NOT NULL COMMENT '来访车牌',
  `plate_color`    VARCHAR(10)  NOT NULL DEFAULT '蓝牌',
  `visit_reason`   VARCHAR(255) NOT NULL COMMENT '来访事由',
  `host_name`      VARCHAR(50)  NOT NULL COMMENT '被访人姓名',
  `host_phone`     VARCHAR(20)  NOT NULL COMMENT '被访人手机号',
  `host_dept`      VARCHAR(100)          DEFAULT NULL COMMENT '被访部门',
  `plan_start`     DATETIME     NOT NULL COMMENT '计划入场时间',
  `plan_end`       DATETIME     NOT NULL COMMENT '计划离场时间',
  `status`         VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                   COMMENT 'PENDING 待审核 / AUTO_APPROVED 自动通过 / APPROVED 人工通过 / REJECTED 已驳回 / EXPIRED 已过期 / CANCELLED 已取消',
  `audit_type`     VARCHAR(10)           DEFAULT NULL COMMENT 'AUTO 自动 / MANUAL 人工',
  `audit_remark`   VARCHAR(500)          DEFAULT NULL COMMENT '审核说明/驳回原因',
  `audit_rules`    VARCHAR(500)          DEFAULT NULL COMMENT '命中的审核规则链快照',
  `auditor_id`     BIGINT                DEFAULT NULL,
  `auditor_name`   VARCHAR(50)           DEFAULT NULL,
  `audit_time`     DATETIME              DEFAULT NULL,
  `pass_id`        BIGINT                DEFAULT NULL COMMENT '已签发的通行码',
  `client_ip`      VARCHAR(64)           DEFAULT NULL,
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_apply_no` (`apply_no`),
  KEY `idx_apply_plate` (`plate_no`),
  KEY `idx_apply_phone` (`phone`),
  KEY `idx_apply_status` (`status`),
  KEY `idx_apply_create` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='访客通行申请';

-- ---------------------------------------------------------------------
-- 4. 通行码（临时通行凭证，二维码载体）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `pass_code` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `pass_no`       VARCHAR(32)  NOT NULL COMMENT '通行码编号',
  `qr_token`      VARCHAR(64)  NOT NULL COMMENT '二维码令牌（HMAC 签名，唯一）',
  `pass_type`     VARCHAR(10)  NOT NULL COMMENT 'VISITOR 访客 / INTERNAL 校内',
  `apply_id`      BIGINT                DEFAULT NULL,
  `vehicle_id`    BIGINT                DEFAULT NULL,
  `plate_no`      VARCHAR(20)  NOT NULL,
  `holder_name`   VARCHAR(50)           DEFAULT NULL,
  `holder_phone`  VARCHAR(20)           DEFAULT NULL,
  `valid_start`   DATETIME     NOT NULL COMMENT '生效时间',
  `valid_end`     DATETIME     NOT NULL COMMENT '失效时间',
  `status`        VARCHAR(20)  NOT NULL DEFAULT 'UNUSED'
                  COMMENT 'UNUSED 待使用 / IN_USE 在场 / USED 已离场 / EXPIRED 已过期 / VOID 已作废',
  `enter_time`    DATETIME              DEFAULT NULL,
  `exit_time`     DATETIME              DEFAULT NULL,
  `enter_gate`    VARCHAR(50)           DEFAULT NULL,
  `exit_gate`     VARCHAR(50)           DEFAULT NULL,
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pass_no` (`pass_no`),
  UNIQUE KEY `uk_pass_token` (`qr_token`),
  KEY `idx_pass_plate` (`plate_no`),
  KEY `idx_pass_status` (`status`),
  KEY `idx_pass_valid` (`valid_end`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通行码';

-- ---------------------------------------------------------------------
-- 5. 出入记录（含核验失败记录，便于追责）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `access_record` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `record_no`      VARCHAR(32)  NOT NULL,
  `plate_no`       VARCHAR(20)  NOT NULL,
  `plate_color`    VARCHAR(10)           DEFAULT NULL,
  `pass_type`      VARCHAR(10)  NOT NULL COMMENT 'VISITOR / INTERNAL',
  `apply_id`       BIGINT                DEFAULT NULL,
  `pass_id`        BIGINT                DEFAULT NULL,
  `vehicle_id`     BIGINT                DEFAULT NULL,
  `direction`      VARCHAR(5)   NOT NULL COMMENT 'IN 入场 / OUT 出场',
  `gate_id`        BIGINT                DEFAULT NULL,
  `gate_name`      VARCHAR(50)           DEFAULT NULL,
  `channel`        VARCHAR(20)  NOT NULL DEFAULT 'QR' COMMENT 'QR 扫码 / PLATE 车牌 / MANUAL 人工',
  `verify_result`  VARCHAR(10)  NOT NULL DEFAULT 'PASS' COMMENT 'PASS 放行 / REJECT 拦截',
  `reject_reason`  VARCHAR(255)          DEFAULT NULL,
  `operator_id`    BIGINT                DEFAULT NULL,
  `operator_name`  VARCHAR(50)           DEFAULT NULL,
  `occur_time`     DATETIME     NOT NULL,
  `remark`         VARCHAR(255)          DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_record_no` (`record_no`),
  KEY `idx_rec_plate` (`plate_no`),
  KEY `idx_rec_time` (`occur_time`),
  KEY `idx_rec_dir` (`direction`),
  KEY `idx_rec_result` (`verify_result`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车辆出入记录';

-- ---------------------------------------------------------------------
-- 6. 车牌黑名单
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `blacklist` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `plate_no`     VARCHAR(20)  NOT NULL,
  `reason`       VARCHAR(255) NOT NULL,
  `level`        VARCHAR(10)  NOT NULL DEFAULT 'MID' COMMENT 'LOW / MID / HIGH',
  `expire_time`  DATETIME              DEFAULT NULL COMMENT '为空表示永久',
  `status`       TINYINT      NOT NULL DEFAULT 1 COMMENT '1 生效 0 解除',
  `creator_name` VARCHAR(50)           DEFAULT NULL,
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_black_plate` (`plate_no`),
  KEY `idx_black_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车牌黑名单';

-- ---------------------------------------------------------------------
-- 7. 门岗通道
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `gate` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `gate_no`     VARCHAR(20)  NOT NULL COMMENT '门岗编号',
  `gate_name`   VARCHAR(50)  NOT NULL,
  `direction`   VARCHAR(10)  NOT NULL DEFAULT 'BOTH' COMMENT 'IN 仅入 / OUT 仅出 / BOTH 双向',
  `status`      TINYINT      NOT NULL DEFAULT 1,
  `remark`      VARCHAR(255)          DEFAULT NULL,
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gate_no` (`gate_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门岗通道';

-- ---------------------------------------------------------------------
-- 8. 系统参数（自动审核策略在后台可视化配置，改完即时生效）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_config` (
  `config_key`   VARCHAR(60)  NOT NULL,
  `config_value` VARCHAR(255) NOT NULL,
  `config_name`  VARCHAR(100) NOT NULL,
  `remark`       VARCHAR(255)          DEFAULT NULL,
  `group_name`   VARCHAR(50)  NOT NULL DEFAULT '基础',
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统参数';

-- ---------------------------------------------------------------------
-- 9. 操作审计日志
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `audit_log` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT                DEFAULT NULL,
  `username`    VARCHAR(50)           DEFAULT NULL,
  `module`      VARCHAR(50)  NOT NULL,
  `action`      VARCHAR(100) NOT NULL,
  `detail`      VARCHAR(1000)         DEFAULT NULL,
  `ip`          VARCHAR(64)           DEFAULT NULL,
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_log_time` (`create_time`),
  KEY `idx_log_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作审计日志';

-- ---------------------------------------------------------------------
-- 基础配置（系统运行必需，非演示数据）
-- ---------------------------------------------------------------------
INSERT INTO `sys_config` (`config_key`, `config_value`, `config_name`, `remark`, `group_name`) VALUES
  ('audit.auto.enable',        'true',    '启用自动审核',       '关闭后所有访客申请转人工审核', '自动审核'),
  ('audit.host.required',      'false',   '校验被访人',         '开启后，被访人手机号必须能在校内车辆档案中找到，否则转人工', '自动审核'),
  ('audit.blacklist.reject',   'true',    '黑名单直接拒绝',     '命中黑名单车牌直接驳回，不转人工', '自动审核'),
  ('audit.daily.limit',        '3',       '单日申请上限',       '同一手机号 24 小时内提交申请超过该数量转人工审核', '自动审核'),
  ('audit.advance.days',       '7',       '最大提前申请天数',   '计划入场时间距当前超过该天数不予受理', '自动审核'),
  ('audit.worktime.start',     '06:00',   '允许通行开始时间',   '计划入场时间早于该时刻转人工审核', '自动审核'),
  ('audit.worktime.end',       '23:00',   '允许通行结束时间',   '计划入场时间晚于该时刻转人工审核', '自动审核'),
  ('pass.advance.minutes',     '120',     '允许提前入场分钟',   '二维码在计划入场时间前多少分钟开始生效', '通行策略'),
  ('pass.max.valid.hours',     '24',      '单次通行最长时长(小时)', '计划停留时间超过该值不予受理', '通行策略'),
  ('pass.overtime.grace',      '60',      '超时未出场宽限(分钟)', '超过失效时间该分钟数仍未出场，自动标记超时并作废', '通行策略'),
  ('gate.plate.auto.open',     'true',    '校内车辆自动放行',   '校内已审核车辆在有效期内的车牌核验直接放行', '通行策略')
ON DUPLICATE KEY UPDATE `config_name` = VALUES(`config_name`);

INSERT INTO `gate` (`gate_no`, `gate_name`, `direction`, `remark`) VALUES
  ('GATE-01', '南门主入口', 'BOTH', '校园南门，双向通行'),
  ('GATE-02', '北门',       'BOTH', '校园北门，双向通行'),
  ('GATE-03', '东门访客通道', 'BOTH', '访客专用通道')
ON DUPLICATE KEY UPDATE `gate_name` = VALUES(`gate_name`);

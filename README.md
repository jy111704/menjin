# 校园车辆出入登记管理系统（访客车辆自动放行审核）

> SpringBoot + Vue3 + MySQL 实现的校园车辆出入登记与访客通行审核系统。
> 访客线上提交申请 → 规则引擎自动审核 → 生成限时防伪二维码 → 门岗扫码放行 → 车辆离场自动销号。
> **系统不内置任何演示数据**，首次启动只有管理员账号、门岗配置与审核参数，所有业务数据由真实使用产生。

---

## 一、解决什么问题

现状：校外车辆进校基本靠保安在门岗手工登记车牌、查证件、打电话确认，访客排队、保安重复劳动、纸质台账事后无法追溯。

本系统的做法：

| 环节 | 传统方式 | 本系统 |
| --- | --- | --- |
| 申请 | 到门口现场登记 | 访客手机提交，填写车牌、事由、被访人、通行时段 |
| 审核 | 保安人工判断 | 规则引擎按黑名单、重复在场、申请次数、通行时段等规则链自动判定 |
| 凭证 | 纸质登记簿 | 一人一码，HMAC 签名防伪，限时有效 |
| 入场 | 人工抄写 | 扫码核验，自动写入出入记录 |
| 离场 | 忘了就一直在场 | 再次扫码即销号，超时未出场由定时任务自动处置 |
| 追溯 | 翻本子 | 全量出入记录（含拦截记录）+ 操作审计日志 + 统计看板 |

---

## 二、技术栈

- 后端：Java 17、Spring Boot 3.3.5、MyBatis-Plus 3.5.9、MySQL 8.0
- 鉴权：JWT（jjwt 0.12.5）+ BCrypt 密码加密 + 注解式角色校验
- 二维码：ZXing 3.5.3，内容为 `token.HMAC-SHA256签名`
- 导出：Apache POI 5.2.5（xlsx）
- 前端：Vue 3、Vite 5、Element Plus 2.8、ECharts 5、html5-qrcode（摄像头扫码）

---

## 三、功能清单

**访客端（免登录）**
- 在线提交通行申请，页面实时显示通行时段、最长停留时长等规则
- 提交后立即返回审核结果：自动通过 → 直接展示二维码；转人工 → 给出单号
- 凭「申请单号 + 手机号」查询状态、出示二维码、取消申请

**门岗端**
- 摄像头扫码核验、扫码枪输入、车牌号核验三种方式
- 大字号放行/拦截结果面板，自动判断入场或出场
- 校内车辆刷牌直接放行；访客未扫码时也可凭车牌查到有效凭证放行

**管理端**
- 工作台：今日进出、在场车辆、待审、自动放行率、7/14/30 日趋势、分时分布、门岗分布
- 访客申请：列表筛选、详情（含规则链校验记录、二维码）、人工通过/驳回、重新签发、取消
- 车辆档案：登记、审核、停用、有效期管理；已有出入记录的车辆禁止删除
- 出入记录：多条件查询、Excel 导出（含拦截记录）
- 在场车辆：实时在场列表、停留时长、超时标红、一键登记离场
- 通行码：查看二维码、手动作废
- 黑名单：加入/解除/删除，命中后申请直接驳回、核验直接拦截
- 门岗、用户、系统参数、操作日志

---

## 四、自动审核规则链

提交申请时按顺序执行，任一环节命中即改变结果（全部规则与判定过程会写入申请的 `audit_rules` 字段，管理端可查）：

| 顺序 | 规则 | 命中结果 |
| --- | --- | --- |
| ① | 车牌是否在黑名单（未过期） | 直接驳回（可配置为转人工） |
| ② | 车牌是否已登记为校内车辆 | 驳回并提示直接刷牌通行 |
| ③ | 该车牌是否有未结束的通行凭证 | 驳回，避免一车多码 |
| ④ | 该车牌是否有待审核申请 | 驳回，避免重复提交 |
| ⑤ | 同一手机号 24 小时申请次数是否超限 | 转人工 |
| ⑥ | 计划入场时间是否在允许通行时段内 | 转人工 |
| ⑦ | 被访人是否在校内人员档案中（可选开关） | 转人工 |
| ⑧ | 自动审核总开关是否开启 | 关闭则全部转人工 |

其他硬性校验（不通过直接报错，不生成申请）：车牌格式、手机号格式、入场时间早于当前、离场早于入场、停留时长超过上限、超出可提前申请天数。

**通行码有效期**：`[计划入场时间 - 允许提前入场分钟数, 计划离场时间]`，签发后即可查到二维码，但未到生效时间核验会被拒绝并提示生效时间。

**销号逻辑**：首次核验 → `IN_USE` 并登记入场；再次核验自动判定 `OUT` → `USED` 并登记离场，二维码同时失效。凭证过期未使用 → `EXPIRED`；超时未出场（超过失效时间 + 宽限）→ 定时任务自动登记离场并 `VOID`。

---

## 五、目录结构

```
├── backend/                        Spring Boot 后端
│   ├── src/main/java/com/campus/vehicle/
│   │   ├── common/                 统一响应、分页、异常处理
│   │   ├── config/                 MyBatis-Plus、CORS、Jackson、拦截器注册
│   │   ├── security/               JWT、登录上下文、@RequireRole
│   │   ├── entity/ mapper/         实体与数据访问
│   │   ├── service/                业务：自动审核、通行码、核验、统计…
│   │   ├── controller/             RPC 接口
│   │   ├── task/                   过期与超时定时任务
│   │   └── util/                   车牌校验、二维码签名与生成
│   ├── src/main/resources/
│   │   ├── application.yml         端口、数据库、JWT 密钥
│   │   ├── db/schema.sql           建表脚本（启动时幂等执行）
│   │   └── static/                 前端构建产物（打包进 jar）
│   └── target/campus-vehicle.jar   可直接运行的单包
├── frontend/                       Vue3 前端
│   └── src/views/                  工作台、核验、申请、车辆、记录、参数…
├── sql/01_schema.sql               手工初始化用建表脚本（可选）
├── 启动系统.bat                     一键启动
└── 重新构建.bat                     重新构建前后端并打包
```

---

## 六、快速开始

### 环境要求
- JDK 17（必须，Spring Boot 3 不支持 8）
- MySQL 8.0，服务运行中
- Node 18+（仅在重新构建前端时需要）

### 方式一：直接运行（推荐）

1. 确认 MySQL 已启动（默认服务名 `MySQL80`）。
2. 双击 **`启动系统.bat`**。
3. 本机启动后打开 <http://localhost:8080>；服务器部署访问 <http://192.144.235.177:8088>。

首次启动会自动创建数据库 `campus_vehicle`、全部数据表、门岗与审核参数，并创建管理员账号。

### 方式二：命令行

```bash
cd backend
java -jar target/campus-vehicle.jar --server.port=8080
```

### 方式三：修改后重新构建

双击 **`重新构建.bat`**（依次执行 npm install → vite build → 复制静态资源 → mvn package）。
若本机 JDK 17 不在 `C:\Program Files\Java\jdk-17`，请修改脚本首行的 `JAVA_HOME`。

---

## 七、默认账号与权限

| 账号 | 密码 | 角色 | 权限 |
| --- | --- | --- | --- |
| admin | Admin@123 | 系统管理员 | 全部功能，含用户、参数、门岗、日志 |
| 自行创建 | — | 门卫保安 | 工作台、核验、审核、车辆查询、记录查询、黑名单 |

> 首次登录后请立即在右上角「修改密码」。账号由管理员在「用户管理」中创建。

访问入口：

- 管理端 <http://192.144.235.177:8088/#/login>
- 访客申请 <http://192.144.235.177:8088/#/visitor>（可直接投放到校门口二维码或公众号）
- 通行码查询 <http://192.144.235.177:8088/#/query>
- 手机门岗 <http://192.144.235.177:8088/mobile-gate.html>
- 手机门岗 HTTPS <https://192.144.235.177:8443/mobile-gate.html>（自签证书，首次打开需手动信任；HTTPS 下可实时预览摄像头）

> 手机门岗用门卫或管理员账号登录，拍照识别车牌后可人工修正，再核验入场/离场。默认使用服务器本地 HyperLPR3（`CampusPlateOcr` 计划任务，`127.0.0.1:8715`），不需要云密钥；也可以在 `config.env` 设置 `CAMPUS_OCR_PROVIDER=tencent` 切回腾讯云 OCR。

---

## 八、数据库与配置

默认连接（见 `backend/src/main/resources/application.yml`）：

```
url:      jdbc:mysql://127.0.0.1:3306/campus_vehicle?...&createDatabaseIfNotExist=true
username: root
password: 123456
```

部署到其他环境时修改这三项即可；`spring.sql.init.mode=always` 会保证表结构存在（`CREATE TABLE IF NOT EXISTS`，不会清数据）。

表清单：`sys_user`、`vehicle`、`visitor_apply`、`pass_code`、`access_record`、`blacklist`、`gate`、`sys_config`、`audit_log`。

安全相关配置（上线前务必替换）：

```yaml
campus:
  jwt.secret: 登录令牌签名密钥
  qr.secret:  通行码签名密钥（更换后已签发的二维码全部失效）
```

---

## 九、主要接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/login` | 登录 |
| GET | `/api/public/notice` | 访客端须知（时段、时长、提前天数） |
| POST | `/api/public/apply` | 提交访客申请（自动审核） |
| GET | `/api/public/apply/{applyNo}?phone=` | 访客查询 |
| POST | `/api/verify` | 门岗核验（`code` 扫码 / `plateNo` 车牌） |
| GET | `/api/applies` | 申请列表（待审、筛选） |
| POST | `/api/applies/{id}/audit` | 人工审核 |
| POST | `/api/applies/{id}/reissue` | 重新签发通行码 |
| GET | `/api/records` `/api/records/export` | 出入记录 / 导出 |
| GET | `/api/records/on-site` | 在场车辆 |
| GET | `/api/dashboard/overview` | 看板指标 |
| GET/PUT | `/api/configs` `/api/configs/{key}` | 审核参数（保存即生效） |

除 `/api/auth/login` 与 `/api/public/**` 外，均需请求头 `Authorization: Bearer <token>`。

---

## 十、系统参数（后台可视化管理）

| 参数 | 默认 | 作用 |
| --- | --- | --- |
| audit.auto.enable | true | 关闭后所有申请转人工 |
| audit.blacklist.reject | true | 黑名单直接驳回（否则转人工） |
| audit.host.required | false | 校验被访人是否在校内档案 |
| audit.daily.limit | 3 | 同一手机号 24 小时申请上限 |
| audit.advance.days | 7 | 最大提前申请天数 |
| audit.worktime.start/end | 06:00 / 23:00 | 允许通行时段 |
| pass.advance.minutes | 120 | 二维码提前生效分钟数 |
| pass.max.valid.hours | 24 | 单次通行最长时长 |
| pass.overtime.grace | 60 | 超时未出场宽限分钟 |
| gate.plate.auto.open | true | 校内车辆是否自动放行 |

修改后立即生效，无需重启（参数缓存在内存中并在保存时刷新）。

---

## 十一、常见问题

**1. 启动报 `Web server failed to start. Port 8080 was already in use`**
8080 被占用。可改用其他端口：
```bash
java -jar target\campus-vehicle.jar --server.port=8081
```

**2. 启动报 `Communications link failure` / 连接数据库失败**
MySQL 未启动或账号密码不对。检查 `MySQL80` 服务状态，并核对 `application.yml`。

**3. 编译时报 `找不到符号: log / getXxx`**
Maven 使用了 JDK 23+（该版本默认关闭隐式注解处理，Lombok 失效）。执行 `mvn -v` 确认 Java 版本为 17，或将 `JAVA_HOME` 指向 JDK 17 后再打包。

**4. 扫码时提示「通行码尚未生效」**
属正常限制：凭证在「计划入场时间 - 提前分钟数」后才生效。可在参数中调大 `pass.advance.minutes`。

**5. 门岗没有摄像头**
可直接用扫码枪输入框（扫码枪等同键盘输入）或用车牌号核验，不影响业务。

**6. 时间显示不正确**
系统统一使用 `Asia/Shanghai` 时区，服务器时区不一致时请调整 MySQL 与操作系统时区。

---

## 十二、后续可扩展

- 对接真实车牌识别相机：把识别结果 POST 到 `/api/verify`（`plateNo` 参数），即可复用全部放行与登记逻辑
- 对接道闸：`/api/verify` 返回 `pass=true` 时下发开闸指令
- 访客短信/微信通知：在 `VisitorApplyService.audit()` 与自动审核分支处接入
- 校内车辆与人事系统同步：定时拉取教职工车辆写入 `vehicle` 表

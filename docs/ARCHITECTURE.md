# 数据库与接口

知华科技 · 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。

## 工程职责

`RentalPolicy` 负责金额、时间区间与计费；`RentalService` 负责状态、快照、设备锁、幂等及账务；`MasterService` 负责主数据与维修；`AdminService` 负责注册权限的分配和管理资源；`AccessService` 每次从数据库核对账号和范围；`Store` 仅接受代码确定的实体/查询，所有条件值使用参数绑定。`ApiController` 提供分页筛选和 CSV。

## 表与约束

迁移：`backend/src/main/resources/db/migration/V1__rental_schema.sql`。

| 表 | 职责与约束 |
| --- | --- |
| department / access_role | 唯一部门名称、角色名称与 ALL/DEPARTMENT 范围 |
| permission / role_permission / nav_menu | 注册业务权限、角色关联外键、页面导航 |
| account | 唯一用户名；角色与部门外键；BCrypt 散列不会进入 API |
| category / customer / asset | 分类唯一名、客户部门；设备唯一序列号、价格、押金和周转时间 |
| booking / booking_line | 单号、部门、客户、租期状态；每单设备唯一；价格/附件/币种快照 |
| ledger | 正数金额、固定台账类型、凭据及操作人；不可编辑/删除 |
| mutation_key | 订单 + 请求标识唯一；动作与参数指纹一致才允许重试 |
| audit_event | 操作人、动作、对象、部门与时间；不保存密码和请求全文 |
| system_setting / dictionary_entry | 注册参数、按类型与代码唯一的可编辑检查项 |

设备明细查询与订单日期状态、部门审计时间均有索引。金额 DECIMAL(14,2)，日期存 UTC 时间点。实体空值与迁移一致；不是 Hibernate 自动建表。全新库按迁移版本执行，已有库校验历史校验和。

## 认证与接口

先 GET `/api/auth/csrf` 获取请求头和 CSRF 令牌，浏览器保持会话 Cookie；随后 POST `/api/auth/login`。后续写请求携带返回的头。GET `/api/auth/me`，POST `/api/auth/logout`，POST `/api/auth/password`。不启用跨域；Cookie HttpOnly、SameSite Strict；HTTPS 部署设置 Secure。

| 接口 | 权限与用途 |
| --- | --- |
| GET /api/lists/{resource} | 对应资源读权限；search/status/page/size/sort/desc；size 1–100 |
| GET /api/catalog | 已登录；当前范围部门、分类、字典与非秘密参数 |
| POST/PUT/DELETE /api/master/{type}[/{id}] | customer.write 或 asset.write；customers/assets/categories |
| POST /api/assets/{id}/maintenance | asset.write；blocked 与 note；在租设备不能释放 |
| POST/PUT/DELETE /api/admin/{type}[/{id}] | admin；users/roles/departments/menus/permissions/dictionaries/settings |
| POST/PUT /api/bookings[/{id}] | booking.write；customerId/startAt/endAt/assetIds/notes |
| GET /api/bookings/{id} | booking.read + 部门；返回 booking、lines、ledger、totals |
| POST /api/bookings/{id}/{action} | booking.write 或 finance；requestKey 必填 |
| GET /api/availability?start=…&end=… | asset.read + 部门；可用状态及阻塞代码 |
| GET /api/dashboard | dashboard 或 report；按币种余额与近期订单 |
| GET /api/reports.csv | report + 部门；公式防护与下载审计 |
| GET /actuator/health | 匿名、仅健康状态 |

错误返回 `{"code":"…"}`；参数 400、会话 401、权限 403、记录 404、业务状态/冲突 409、登录限流 429。数据库约束错误不返回 SQL 和凭证。

状态：DRAFT → CONFIRMED → OUT → PARTIAL → RETURNED → CLOSED；仅租一台时，归还后直接 OUT → RETURNED。DRAFT/CONFIRMED 可在退款完成后 → CANCELLED。动作包括 confirm/checkout/return/extend/payment/cancel/close。

财务动作：RENT_RECEIPT、DEPOSIT_RECEIPT、RENT_REFUND、DEPOSIT_REFUND、DEPOSIT_DEDUCTION，统一正数金额，符号由业务类型定义。归还输入 lineId、amount（损坏金额）、inspectionPassed、note；续租输入 endAt；款项登记输入 kind、amount、reference、note。订单业务标识 UUID；requestKey 8–80 位字母数字、下划线或连字符。

## 并发与限制

核心业务使用 READ_COMMITTED 事务。先锁订单，再按设备 ID 顺序加悲观锁，锁后查已有确认/在租占用。这样可见刚提交的另一单，防止两个预约同时成功；修改设备也使用同一设备锁。业务记录、台账、幂等标识与审计在同一事务提交，失败整体回滚。

逾期仍未还的设备占用上界保持阻塞；检修和 readyAt 周转限制独立于订单结单状态。明细归还后，剩余设备仍占用。不支持数量汇总库存、分仓跨部门调拨、外租转租和多租户共享部署。

分页前先按账号范围筛选有界主数据，最多 10,000 条；适用于小型租赁学习评估。规模化交付需改为数据库条件分页，并做查询、锁超时、监控和性能测试。不能以本地功能测试代替大型生产负载验收。

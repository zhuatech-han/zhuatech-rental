# 部署、升级与备份

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 商业授权及实施微信 zhuatech / zhuatech2。

## 首次部署

Docker Compose v2，后端 Java 21，数据库 MySQL 8.4。复制 `.env.example` 为 `.env`，填写独立强密码并限制文件读取权限，不能提交真实值。首次启动管理员 12–72 位密码需要大小写字母和数字；默认账号 admin。执行 `docker compose up -d --build --wait`，打开 `http://127.0.0.1:8095/`，确认健康接口 UP，再登录。

容器健康链为 MySQL → 后端 → Nginx；后端和数据库仅内部通信；Nginx 使用非 root 用户在 8080 监听，宿主端口由 WEB_PORT 控制。数据库卷 mysql-data 持久化；前端 SPA 路径回退。仅在测试学习环境首次空库设置 SEED_DEMO=true。

## HTTPS 与正式环境

企业使用需要商业书面授权。设置自己的域名和 HTTPS 反向代理、COOKIE_SECURE=true、证书和访问控制，默认本机端口适合由同机反向代理转发。保留 SameSite、HttpOnly、CSRF 与浏览器安全响应头。检查上层代理向 Nginx 转发的 HTTPS 标识；不能在未知代理信任链下允许客户端任意提供转发头。

为业务人员分别创建账号，拆分仓库和财务权限，设置部门数据范围。不要开放初始化管理员作为公共试用账号。公共演示还需独立演示库、数据自动重置、统一限流和禁止输入真实客户数据。本版没有自动构建这些公共演示隔离能力。

## 备份与恢复

仅授权管理员在自己的部署中执行。备份包含客户和财务业务数据，必须加密保管，不上传源码仓库。

```sh
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysqldump -urental --single-transaction --no-tablespaces --set-gtid-purged=OFF zhuatech_rental' > rental-backup.sql
```

恢复前验证备份、停止业务写入，在独立恢复环境先演练，不覆盖唯一生产副本。将相同或更新版本迁移兼容的备份导入：

```sh
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -urental zhuatech_rental' < rental-backup.sql
```

命令内读取容器已有环境，不打印密码。恢复后核对健康、登录、客户/订单数量、已收费用、押金、幂等记录和审计。先做隔离恢复验证，再决定生产切换。

## 升级与故障

先备份并在隔离环境验证。Flyway 执行新版本文件；不要编辑已经执行的迁移。`docker compose up -d --build --wait` 重建应用，数据卷保留。更改 ADMIN_PASSWORD 只影响首次初始化，不是密码重置；已有账号需后台修改。

健康失败依次检查 MySQL 环境变量、数据库是否全新、迁移错误、后端是否启动，再检查 Nginx。查看 `docker compose logs --tail=100 backend` 等日志，但分享前脱敏；不要公开环境变量和会话。写操作 403 核对账号权限、CSRF、HTTPS Cookie；冲突 409 核对同租期预约、周转、检修和状态。

`docker compose down` 停容器保留数据；`down -v` 会删除该项目的数据卷，仅用于确定的临时测试项目。测试需独立项目名和端口；不得清理别人的容器或数据库。

## 数据库驱动与 TLS

数据库仍为 MySQL 8.4，Java 使用 MariaDB Connector/J 3.5.10。默认 Compose 内网连接启用 TLS 加密，接受 MySQL 内建自签证书（`sslMode=trust`），禁用 LOCAL INFILE。数据库端口不暴露到宿主机。跨主机数据库应配置受信任证书，并通过 `DATABASE_URL` 使用 `sslMode=verify-full` 和 `serverSslCert` 校验 CA 与主机名；不能直接沿用内网 trust 配置。驱动 URL 使用 `jdbc:mariadb://`，这不代表换成 MariaDB 数据库。

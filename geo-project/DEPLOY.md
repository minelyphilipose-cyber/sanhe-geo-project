# 腾讯云单机部署说明

本文档面向 Ubuntu 服务器上的单机 Docker Compose 部署。当前目标服务器为 4 核 8G、5Mbps，适合内部系统小规模生产使用。

## 1. 服务器准备

建议只开放腾讯云安全组端口：

- `22`: SSH，最好限制为办公固定 IP
- `80`: HTTP，用于证书签发和跳转
- `443`: HTTPS

不要公网开放 MySQL `3306`、Redis `6379`。

安装基础依赖：

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg nginx certbot python3-certbot-nginx
```

安装 Docker。国内服务器不要使用 Docker 官方 `download.docker.com` 源，容易出现 TLS handshake 或索引下载失败。使用项目内脚本安装腾讯云镜像源版本：

```bash
bash scripts/install-docker-cn.sh
docker --version
docker compose version
```

如果服务器上已经写入过失败的官方 Docker 源，脚本会覆盖 `/etc/apt/sources.list.d/docker.list`。

4C8G 机器建议加 2G swap：

```bash
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

## 2. 域名和证书

备案完成前，临时使用服务器公网 IP 作为 HTTP 入口：

- 主系统入口：`http://119.45.154.127`
- 对象文件由腾讯云 COS 提供，应用不再代理生产 MinIO。

当前阶段不签发 HTTPS 证书。域名备案完成后，再切回 `https://www.huanjingaigeo.com` 并执行 Certbot。

参考 [deploy/nginx/geo.conf.example](deploy/nginx/geo.conf.example)，替换域名后放到：

```bash
sudo cp deploy/nginx/geo.conf.example /etc/nginx/conf.d/geo.conf
sudo nginx -t
sudo systemctl reload nginx
```

## 3. 生产环境变量

在项目根目录创建 `.env`：

```bash
cp .env.example .env
chmod 600 .env
```

必须替换所有 `replace-with-*`。重点项：

- `APP_PUBLIC_URL=http://119.45.154.127`
- `GEO_STORAGE_PROVIDER=cos`
- `GEO_STORAGE_READ_FALLBACK_TO_MINIO=false`
- `GEO_STORAGE_MIGRATION_EXECUTE_ENABLED=false`
- `COS_REGION`
- `COS_BUCKET`
- `COS_INTERNAL_ENDPOINT`
- `COS_SECRET_ID`
- `COS_SECRET_KEY`
- `JWT_SECRET`
- `DISPATCH_API_KEY_AES_SECRET`
- `MP_CREDENTIAL_AES_SECRET`
- `FILL_TOKEN_HMAC_SECRET`
- `MYSQL_ROOT_PASSWORD`
- `MYSQL_PASSWORD`
- `REDIS_PASSWORD`
- `MINIO_APP_ACCESS_KEY`（当前后端兼容配置绑定仍需要，不代表生产启用 MinIO）
- `MINIO_APP_SECRET_KEY`（当前后端兼容配置绑定仍需要，不代表生产启用 MinIO）
- 微信开放平台相关 `WECHAT_*`
- 抖音开放平台相关 `DOUYIN_*`
- 媒体特价接口相关 `MEITITEJIA_*`

生成随机密钥示例：

```bash
openssl rand -base64 48
openssl rand -hex 32
```

## 4. 启动部署

首次部署或升级使用生产 `docker-compose.yml`：

```bash
bash scripts/deploy.sh
```

等容器健康后检查：

```bash
docker compose ps
docker compose logs -f geo-server
curl -I http://119.45.154.127
curl http://119.45.154.127/api/actuator/health
```

## 5. 验收清单

上线后至少验证：

- 管理后台可以登录
- API 健康检查返回 `UP`
- 文件上传、预览、下载可用
- 报告导出 PDF 可用
- 微信开放平台授权回调地址正确
- 抖音开放平台授权回调地址正确
- 服务器重启后容器自动恢复

## 6. 备份

MySQL 备份：

```bash
docker compose exec mysql sh -c 'mysqldump -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' > backup-$(date +%F).sql
```

Docker volumes 是生产数据核心：

- `geo-project_mysql-data`
- `geo-project_redis-data`
- `geo-project_presale-exports`

升级前建议先做数据库备份，并确认 COS 的生命周期和备份策略符合业务要求。

## 7. 常用运维命令

```bash
docker compose ps
docker compose logs -f geo-server
docker compose logs -f geo-web
docker compose restart geo-server
docker compose pull
docker compose up -d --build
docker system df
```

## 8. 回滚

如果升级失败：

1. 将 `.env` 中 `IMAGE_TAG` 改回上一版本。
2. 恢复升级前数据库备份，前提是本次升级已经执行了不兼容迁移。
3. 执行：

```bash
docker compose up -d --build
```

Flyway 默认会校验迁移历史。涉及数据库结构变更的回滚，需要先确认对应迁移是否可逆。

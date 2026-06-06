#!/bin/bash
# SSL 证书申请与自动续期配置脚本
# 使用方式：bash scripts/deploy/ssl-init.sh your-domain.com your@email.com
# 前提：域名已解析到本机 IP，80 端口可访问
# 说明：只负责申请证书，启动服务由 CI 部署流程完成

set -e

DOMAIN=$1
EMAIL=$2
AAF_DIR=/root/aaf
SSL_DIR=$AAF_DIR/ssl
COMPOSE="docker compose -f $AAF_DIR/docker-compose.yml -f $AAF_DIR/docker-compose.prod.yml"

if [ -z "$DOMAIN" ] || [ -z "$EMAIL" ]; then
    echo "用法: bash ssl-init.sh your-domain.com your@email.com"
    exit 1
fi

echo "==> 安装 certbot..."
if command -v certbot &>/dev/null; then
    echo "certbot 已安装，跳过"
elif command -v apt-get &>/dev/null; then
    apt-get update -q && apt-get install -y certbot
elif command -v dnf &>/dev/null; then
    # Alibaba Cloud Linux / RHEL 系，用 pip 安装
    dnf install -y python3-pip 2>/dev/null || true
    pip3 install certbot
    ln -sf "$(python3 -c 'import site; print(site.getsitepackages()[0])')/../../bin/certbot" /usr/local/bin/certbot 2>/dev/null || true
elif command -v yum &>/dev/null; then
    yum install -y python3-pip && pip3 install certbot
else
    echo "❌ 无法自动安装 certbot，请手动安装后重试: https://certbot.eff.org"
    exit 1
fi

# 最终确认
if ! command -v certbot &>/dev/null; then
    echo "❌ certbot 安装失败，请手动安装"
    exit 1
fi

# 停止占用 80 端口的服务（首次运行可能尚未启动，忽略错误）
echo "==> 释放 80 端口..."
$COMPOSE stop nginx 2>/dev/null || true
# 兜底：直接停掉宿主机上占用 80 端口的进程
fuser -k 80/tcp 2>/dev/null || true

echo "==> 申请证书: $DOMAIN"
certbot certonly --standalone -d "$DOMAIN" --non-interactive --agree-tos --email "$EMAIL"

echo "==> 复制证书到 $SSL_DIR"
mkdir -p "$SSL_DIR"
cp /etc/letsencrypt/live/"$DOMAIN"/fullchain.pem "$SSL_DIR"/fullchain.pem
cp /etc/letsencrypt/live/"$DOMAIN"/privkey.pem  "$SSL_DIR"/privkey.pem
chmod 600 "$SSL_DIR"/privkey.pem

echo "==> 配置自动续期 crontab（每月 1 日凌晨 3 点）..."
RENEW_HOOK_PRE="$COMPOSE stop nginx 2>/dev/null || true"
RENEW_HOOK_POST="cp /etc/letsencrypt/live/$DOMAIN/fullchain.pem $SSL_DIR/fullchain.pem && cp /etc/letsencrypt/live/$DOMAIN/privkey.pem $SSL_DIR/privkey.pem && $COMPOSE up -d nginx"
RENEW_CMD="certbot renew --quiet --pre-hook '$RENEW_HOOK_PRE' --post-hook '$RENEW_HOOK_POST'"

CRON_MARK="# aaf-ssl-renew"
(crontab -l 2>/dev/null | grep -v "$CRON_MARK"; echo "0 3 1 * * $RENEW_CMD $CRON_MARK") | crontab -

echo ""
echo "✅ 证书已申请，位于 $SSL_DIR"
echo "   自动续期已配置（每月 1 日 03:00）"
echo "   手动测试续期：certbot renew --dry-run"
echo ""
echo "   下一步：触发 CI 部署，nginx 将自动启动并加载证书"

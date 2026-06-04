#!/usr/bin/env bash
set -euo pipefail

# 在 Debian/Ubuntu 上通过 PostgreSQL 官方 PGDG 仓库安装 PostgreSQL 18 和 pgvector。
# 生产环境建议由 IaC/镜像构建流程执行，本脚本主要用于本地开发机或测试机初始化。

POSTGRES_VERSION="${POSTGRES_VERSION:-18}"

if [[ "${EUID}" -eq 0 ]]; then
  SUDO=()
else
  SUDO=(sudo)
fi

if ! command -v apt >/dev/null 2>&1; then
  echo "当前系统未检测到 apt，仅支持 Debian/Ubuntu。"
  exit 1
fi

echo "安装 PostgreSQL PGDG 仓库工具..."
"${SUDO[@]}" apt update
"${SUDO[@]}" apt install -y postgresql-common

echo "配置 PostgreSQL 官方 PGDG APT 仓库..."
"${SUDO[@]}" /usr/share/postgresql-common/pgdg/apt.postgresql.org.sh -y

echo "安装 PostgreSQL ${POSTGRES_VERSION} 和 pgvector..."
"${SUDO[@]}" apt update
"${SUDO[@]}" apt install -y "postgresql-${POSTGRES_VERSION}" "postgresql-${POSTGRES_VERSION}-pgvector"

echo "启动 PostgreSQL 服务..."
"${SUDO[@]}" systemctl enable --now postgresql

echo "安装结果:"
psql --version
"${SUDO[@]}" -u postgres psql -Atc "SELECT version();"

echo "PostgreSQL ${POSTGRES_VERSION} 和 pgvector 安装完成。"

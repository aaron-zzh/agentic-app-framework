#!/usr/bin/env bash
set -euo pipefail

# 创建 AAF 本地/测试 PostgreSQL 数据库，并启用 pgvector。
# 生产环境建议由 DBA 或 IaC 执行等价步骤，应用侧只通过 Flyway 管理业务 schema。

DB_NAME="${DB_NAME:-aaf}"
DB_USER="${DB_USER:-aaron}"
DB_PASSWORD="${DB_PASSWORD:-}"
DB_COLLATION="${DB_COLLATION:-zh_CN.utf8}"
DB_TEMPLATE="${DB_TEMPLATE:-template0}"
PG_SUPERUSER="${PG_SUPERUSER:-postgres}"

PSQL=(psql -v ON_ERROR_STOP=1)

if command -v sudo >/dev/null 2>&1; then
  PSQL=(sudo -u "$PG_SUPERUSER" psql -v ON_ERROR_STOP=1)
fi

echo "准备创建数据库: ${DB_NAME}"
echo "数据库用户: ${DB_USER}"
echo "排序规则: ${DB_COLLATION}"

role_exists="$("${PSQL[@]}" -Atc "SELECT 1 FROM pg_roles WHERE rolname = '${DB_USER}'")"
if [[ "$role_exists" != "1" ]]; then
  if [[ -n "$DB_PASSWORD" ]]; then
    "${PSQL[@]}" -c "CREATE ROLE \"${DB_USER}\" WITH LOGIN PASSWORD '${DB_PASSWORD}';"
  else
    "${PSQL[@]}" -c "CREATE ROLE \"${DB_USER}\" WITH LOGIN;"
  fi
fi

db_exists="$("${PSQL[@]}" -Atc "SELECT 1 FROM pg_database WHERE datname = '${DB_NAME}'")"
if [[ "$db_exists" != "1" ]]; then
  "${PSQL[@]}" <<SQL
CREATE DATABASE "${DB_NAME}"
    WITH
    OWNER = "${DB_USER}"
    ENCODING = 'UTF8'
    LC_COLLATE = '${DB_COLLATION}'
    LC_CTYPE = '${DB_COLLATION}'
    TEMPLATE = ${DB_TEMPLATE}
    CONNECTION LIMIT = -1;
SQL
fi

"${PSQL[@]}" -d "$DB_NAME" <<SQL
CREATE EXTENSION IF NOT EXISTS vector;

GRANT ALL PRIVILEGES ON DATABASE "${DB_NAME}" TO "${DB_USER}";
GRANT USAGE, CREATE ON SCHEMA public TO "${DB_USER}";

SELECT datname, datcollate, datctype
FROM pg_database
WHERE datname = '${DB_NAME}';

SELECT extname, extversion
FROM pg_extension
WHERE extname = 'vector';
SQL

echo "数据库 ${DB_NAME} 初始化完成。"

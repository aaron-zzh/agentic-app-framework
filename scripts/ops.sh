#!/bin/bash
# AAF 运维常用指令
# 用法：bash scripts/ops.sh <command> [args]
#
# Commands:
#   demo-data              初始化演示数据（admin 积分）
#   redis-flush            清空 Redis 全部缓存
#   redis-flush-pattern    清空指定前缀的 Redis key，如: redis-flush-pattern ai_model
#   logs [service]         查看服务日志，service: service|nginx|redis（默认 service）
#   restart [service]      重启容器
#   ps                     查看所有容器状态

PG=${PG_CONTAINER:-aaf-postgres}
REDIS=${REDIS_CONTAINER:-aaf-redis}
DB=${DB_NAME:-aaf}

case "$1" in

  demo-data)
    echo ">>> 初始化演示数据..."
    docker exec "$PG" psql -U postgres -d "$DB" -c \
      "INSERT INTO credit_account (user_id, balance, total_earned)
       SELECT id, 999, 999 FROM sys_user WHERE username = 'admin'
       ON CONFLICT DO NOTHING;"
    docker exec "$PG" psql -U postgres -d "$DB" -c \
      "INSERT INTO credit_transaction (account_id, type, amount, balance_after, source, batch_type, remain, deleted)
       SELECT ca.id, 'EARN', 999, 999, 'MANUAL', 'SUBSCRIPTION', 999, FALSE
       FROM credit_account ca JOIN sys_user u ON ca.user_id = u.id
       WHERE u.username = 'admin' AND ca.deleted = FALSE
       AND NOT EXISTS (SELECT 1 FROM credit_transaction ct WHERE ct.account_id = ca.id AND ct.type = 'EARN' AND ct.deleted = FALSE);"
    echo "Done."
    ;;

  redis-flush)
    echo ">>> 清空 Redis 全部缓存..."
    docker exec "$REDIS" redis-cli FLUSHDB
    echo "Done."
    ;;

  redis-flush-pattern)
    PATTERN="${2:?用法: ops.sh redis-flush-pattern <pattern>}"
    echo ">>> 清空 Redis key: ${PATTERN}:*"
    docker exec "$REDIS" redis-cli --scan --pattern "${PATTERN}:*" | \
      docker exec -i "$REDIS" xargs -r redis-cli DEL
    echo "Done."
    ;;

  logs)
    SERVICE="${2:-service}"
    case "$SERVICE" in
      service) docker logs aaf-service --tail 100 -f ;;
      nginx)   docker logs aaf-nginx --tail 100 -f ;;
      redis)   docker logs "$REDIS" --tail 100 -f ;;
      *)       docker logs "$SERVICE" --tail 100 -f ;;
    esac
    ;;

  restart)
    SERVICE="${2:?用法: ops.sh restart <service>}"
    echo ">>> 重启 $SERVICE..."
    docker restart "$SERVICE"
    ;;

  ps)
    docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
    ;;

  *)
    echo "用法: bash scripts/ops.sh <command>"
    echo ""
    echo "Commands:"
    echo "  demo-data                  初始化演示数据"
    echo "  redis-flush                清空 Redis 全部缓存"
    echo "  redis-flush-pattern <key>  清空指定前缀的缓存"
    echo "  logs [service]             查看日志（service/nginx/redis）"
    echo "  restart <service>          重启容器"
    echo "  ps                         查看容器状态"
    ;;

esac

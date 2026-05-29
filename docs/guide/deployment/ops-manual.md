# 运维手册

## 监控指标

### 应用层

| 指标 | 告警阈值 | 采集方式 |
|------|---------|---------|
| API 响应时间 P99 | > 3s | Spring Actuator + Prometheus |
| 错误率 | > 1% | 日志统计 |
| JVM 堆内存使用率 | > 80% | Micrometer |
| 线程池活跃数 | > 90% 容量 | Micrometer |
| AI 调用延迟 | > 30s | 自定义指标 |

### 基础设施

| 指标 | 告警阈值 |
|------|---------|
| CPU 使用率 | > 80% 持续 5 分钟 |
| 内存使用率 | > 85% |
| 磁盘使用率 | > 80% |
| PostgreSQL 连接数 | > 80% max_connections |
| Redis 内存使用率 | > 70% |

## 日志管理

### 日志级别

| 环境 | 默认级别 | 调整方式 |
|------|---------|---------|
| 开发 | DEBUG | application-dev.yml |
| 测试 | INFO | application-test.yml |
| 生产 | WARN | 运行时通过 Actuator 动态调整 |

### 动态调整日志级别

```bash
# 查看当前级别
curl http://localhost:8080/actuator/loggers/com.xuejiai.aaf

# 临时调整为 DEBUG
curl -X POST http://localhost:8080/actuator/loggers/com.xuejiai.aaf \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "DEBUG"}'
```

### 日志格式

```text
2026-05-29 15:00:00.123 [http-nio-8080-exec-1] INFO  c.x.a.m.user.UserService - 用户登录成功 userId=123
```

## 备份与恢复

### 自动备份脚本

```bash
#!/bin/bash
# /opt/aaf/backup.sh — 加入 crontab 每日执行
DATE=$(date +%Y%m%d_%H%M)
BACKUP_DIR=/data/backups

# PostgreSQL
pg_dump -h localhost -U postgres aaf | gzip > $BACKUP_DIR/pg_$DATE.sql.gz

# Neo4j
docker exec aaf-neo4j neo4j-admin database dump neo4j --to-path=/tmp/neo4j_backup
docker cp aaf-neo4j:/tmp/neo4j_backup $BACKUP_DIR/neo4j_$DATE

# 清理 30 天前的备份
find $BACKUP_DIR -mtime +30 -delete
```

### 恢复流程

```bash
# PostgreSQL 恢复
gunzip < pg_20260529.sql.gz | psql -h localhost -U postgres aaf

# Neo4j 恢复
docker exec aaf-neo4j neo4j-admin database load neo4j --from-path=/backups/neo4j_20260529
```

## 常见运维操作

### 重启服务

```bash
# Docker 环境
docker compose restart aaf-service

# K8s 环境
kubectl rollout restart deployment/aaf-service
```

### 数据库迁移

```bash
# Flyway 自动迁移（启动时执行）
# 手动执行迁移
./mvnw flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5432/aaf
```

### 清理过期数据

```sql
-- 清理 90 天前的对话记录
DELETE FROM chat_messages WHERE created_at < NOW() - INTERVAL '90 days';

-- 清理过期 Token
DELETE FROM refresh_tokens WHERE expires_at < NOW();
```

### 扩容

```bash
# K8s 手动扩容
kubectl scale deployment/aaf-service --replicas=3

# HPA 自动扩缩（已配置时自动生效）
kubectl get hpa aaf-service
```

## 故障处理

### 后端 OOM

1. 检查 JVM 堆配置：`-Xmx2g`
2. 导出堆转储：`jmap -dump:format=b,file=heap.hprof <pid>`
3. 分析大对象（MAT 工具）
4. 临时扩大内存或重启

### 数据库慢查询

```sql
-- 查看当前慢查询
SELECT pid, now() - pg_stat_activity.query_start AS duration, query
FROM pg_stat_activity
WHERE state = 'active' AND now() - pg_stat_activity.query_start > interval '5 seconds';

-- 终止慢查询
SELECT pg_terminate_backend(<pid>);
```

### Redis 内存满

```bash
# 查看内存使用
redis-cli INFO memory

# 清理过期 key
redis-cli --scan --pattern "session:*" | xargs redis-cli DEL

# 临时调大 maxmemory
redis-cli CONFIG SET maxmemory 2gb
```

## 安全运维

- 定期更新依赖（每月安全扫描）
- 轮换 JWT Secret（每季度）
- 审计日志保留 ≥ 180 天
- 数据库密码定期更换
- 监控异常登录（同 IP 多次失败）

根据描述的问题，生成对应的 Linux/Docker 运维命令。

> **命令格式**：优先生成单行命令，SQL 较长时用 `\` 续行（Linux 可直接粘贴执行）。

容器名约定：aaf-postgres / aaf-redis / aaf-neo4j / aaf-service

## 参考命令库

### PostgreSQL

重建数据库（强制断开所有连接）
```bash
docker exec -it aaf-postgres psql -U postgres -c "COMMIT;" -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='aaf' AND pid <> pg_backend_pid();" -c "DROP DATABASE IF EXISTS aaf;" -c "CREATE DATABASE aaf;"
```

进入交互式终端
```bash
docker exec -it aaf-postgres psql -U postgres -d aaf
```

查看所有数据库
```bash
docker exec -it aaf-postgres psql -U postgres -c "\l"
```

查看当前活跃连接
```bash
docker exec -it aaf-postgres psql -U postgres -c "SELECT pid, usename, datname, state, query FROM pg_stat_activity WHERE datname='aaf';"
```

强制终止某个连接（替换 <pid>）
```bash
docker exec -it aaf-postgres psql -U postgres -c "SELECT pg_terminate_backend(<pid>);"
```

查看表列表
```bash
docker exec -it aaf-postgres psql -U postgres -d aaf -c "\dt"
```

查看表结构（替换 <表名>）
```bash
docker exec -it aaf-postgres psql -U postgres -d aaf -c "\d <表名>"
```

查看各表行数
```bash
docker exec -it aaf-postgres psql -U postgres -d aaf -c "SELECT relname, n_live_tup FROM pg_stat_user_tables ORDER BY n_live_tup DESC;"
```

查看数据库大小
```bash
docker exec -it aaf-postgres psql -U postgres -c "SELECT datname, pg_size_pretty(pg_database_size(datname)) FROM pg_database;"
```

查看各表大小
```bash
docker exec -it aaf-postgres psql -U postgres -d aaf \
  -c "SELECT tablename, pg_size_pretty(pg_total_relation_size(tablename::text)) AS size \
      FROM pg_tables WHERE schemaname='public' ORDER BY pg_total_relation_size(tablename::text) DESC;"
```

查看锁等待
```bash
docker exec -it aaf-postgres psql -U postgres -d aaf -c "SELECT pid, wait_event_type, wait_event, state, query FROM pg_stat_activity WHERE wait_event IS NOT NULL;"
```

查看慢查询（超过 1s）
```bash
docker exec -it aaf-postgres psql -U postgres -d aaf \
  -c "SELECT pid, now() - query_start AS duration, query, state \
      FROM pg_stat_activity WHERE state='active' AND now() - query_start > interval '1 second';"
```

重置 Flyway 迁移历史（慎用）
```bash
docker exec -it aaf-postgres psql -U postgres -d aaf -c "DELETE FROM flyway_schema_history;"
```

查看索引使用情况
```bash
docker exec -it aaf-postgres psql -U postgres -d aaf -c "SELECT indexrelname, idx_scan, idx_tup_read FROM pg_stat_user_indexes ORDER BY idx_scan ASC;"
```

### Redis

查看所有 key
```bash
docker exec -it aaf-redis redis-cli KEYS "*"
```

按前缀查找 key
```bash
docker exec -it aaf-redis redis-cli KEYS "spring:session:*"
```

查看某个 key 的 TTL
```bash
docker exec -it aaf-redis redis-cli TTL <key>
```

删除某个 key
```bash
docker exec -it aaf-redis redis-cli DEL <key>
```

清空所有缓存（慎用！）
```bash
docker exec -it aaf-redis redis-cli FLUSHALL
```

查看内存使用
```bash
docker exec -it aaf-redis redis-cli INFO memory | grep used_memory_human
```

查看命中率
```bash
docker exec -it aaf-redis redis-cli INFO stats | grep -E "keyspace_hits|keyspace_misses"
```

### Docker 容器

查看所有容器状态
```bash
docker ps -a --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

查看服务日志（最近 100 行）
```bash
docker logs --tail 100 -f aaf-service
```

查看错误日志
```bash
docker logs aaf-service 2>&1 | grep -i "error\|exception" | tail -50
```

查看容器资源占用
```bash
docker stats --no-stream
```

进入容器 shell
```bash
docker exec -it aaf-service bash
```

### Neo4j

查看节点总数
```bash
docker exec -it aaf-neo4j cypher-shell -u neo4j -p password "MATCH (n) RETURN count(n);"
```

查看各标签节点数量
```bash
docker exec -it aaf-neo4j cypher-shell -u neo4j -p password "MATCH (n) RETURN labels(n) AS label, count(*) AS count ORDER BY count DESC;"
```

清空所有数据（慎用！）
```bash
docker exec -it aaf-neo4j cypher-shell -u neo4j -p password "MATCH (n) DETACH DELETE n;"
```

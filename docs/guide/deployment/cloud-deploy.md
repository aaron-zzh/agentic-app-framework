# 云服务部署方案

## 架构概览

```text
┌─────────────────────────────────────────────────┐
│                  负载均衡 (Nginx/ALB)             │
├────────────────────┬────────────────────────────┤
│   前端 (CDN/OSS)   │   后端 (K8s/ECS)           │
├────────────────────┴────────────────────────────┤
│   PostgreSQL (RDS)  │  Neo4j (VM)  │  Redis     │
└─────────────────────────────────────────────────┘
```

## 阿里云方案

| 组件 | 服务 | 规格建议 |
|------|------|---------|
| 后端 | ECS / ACK（K8s） | 2C4G × 2（最低） |
| 前端 | OSS + CDN | 静态托管 |
| 数据库 | RDS PostgreSQL | 2C4G，开启 pgvector 扩展 |
| 图数据库 | ECS 自建 Neo4j | 2C4G |
| 缓存 | Redis 云版 | 1G 标准版 |
| 对象存储 | OSS | 存储上传文件 |
| 日志 | SLS | 日志采集分析 |

### 部署步骤

1. **创建 RDS**：PostgreSQL 16+，开启 `pgvector` 扩展
2. **创建 Redis**：标准版，设置密码
3. **部署 Neo4j**：ECS 上 Docker 运行
4. **部署后端**：ACK 或 ECS，配置环境变量指向云服务
5. **部署前端**：`pnpm nx build webui` → 产物上传 OSS → 配置 CDN
6. **配置域名**：DNS 解析 + SSL 证书

## 腾讯云方案

| 组件 | 服务 |
|------|------|
| 后端 | TKE / CVM |
| 前端 | COS + CDN |
| 数据库 | TDSQL-C PostgreSQL |
| 缓存 | 云数据库 Redis |
| 对象存储 | COS |

## Kubernetes 部署

### 核心资源

```yaml
# deployment.yaml（后端）
apiVersion: apps/v1
kind: Deployment
metadata:
  name: aaf-service
spec:
  replicas: 2
  template:
    spec:
      containers:
        - name: aaf-service
          image: registry.cn-hangzhou.aliyuncs.com/aaf/service:latest
          ports:
            - containerPort: 8080
          envFrom:
            - secretRef:
                name: aaf-secrets
          resources:
            requests:
              memory: "1Gi"
              cpu: "500m"
            limits:
              memory: "2Gi"
              cpu: "1000m"
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 30
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 10
```

### Secret 管理

```bash
kubectl create secret generic aaf-secrets \
  --from-literal=DB_PASSWORD=xxx \
  --from-literal=JWT_SECRET=xxx \
  --from-literal=OPENAI_API_KEY=sk-xxx
```

## 高可用配置

| 组件 | 高可用方案 |
|------|-----------|
| 后端 | 多副本 + HPA 自动扩缩 |
| PostgreSQL | 主从复制 / RDS 高可用版 |
| Redis | 哨兵模式 / 集群模式 |
| Neo4j | Causal Cluster（企业版） |
| 前端 | CDN 多节点 |

## 成本估算

> 具体价格请参考各云服务商官网计算器。

| 配置 | 适用场景 | 月成本参考 |
|------|---------|-----------|
| 最小化（2C4G × 1） | 开发/测试 | ¥500-800 |
| 标准（2C4G × 2 + RDS） | 小团队生产 | ¥1500-2500 |
| 高可用（4C8G × 3 + 高可用 RDS） | 企业生产 | ¥5000+ |

## 安全加固

- HTTPS 强制（全站 TLS 1.3）
- 数据库仅内网访问（安全组限制）
- JWT Secret 使用 Secret Manager 管理
- 定期轮换 API Key
- 开启 WAF 防护
- 数据库定时备份（每日增量 + 每周全量）

---
level: Practice
layer: Product
purpose: AAF-042 日志与监控的技术任务清单
status: pending
version: 1.0.0
date: 2026-05-19
author: AaronZZH
---

# 日志与监控（AAF-042）

> 负责人：architect + developer-service | 创建：05-19

## 任务列表

1. [ ] #4201 操作日志
   - AOP 注解 @OperationLog 自动记录
   - 操作日志表（操作人/时间/模块/动作/IP/变更内容）
   - 日志查询 API（分页/筛选）
   - verify: 标注注解的接口自动记录操作日志

2. [ ] #4202 审计日志
   - 数据变更审计（字段级 before/after）
   - 敏感操作审计（登录/权限变更/数据删除）
   - 审计日志不可篡改（追加写入）
   - verify: 数据修改后审计日志记录变更详情

3. [ ] #4203 系统日志
   - 结构化日志（JSON 格式）、日志级别动态调整
   - 日志文件轮转、异步写入
   - 错误日志聚合与告警
   - verify: 日志输出为 JSON，可按级别筛选

4. [ ] #4204 Prometheus 指标
   - Micrometer + Prometheus 集成
   - 自定义业务指标（请求量/响应时间/错误率）
   - JVM/数据库连接池/Redis 指标暴露
   - verify: /actuator/prometheus 端点返回指标

5. [ ] #4205 健康检查
   - Spring Actuator 健康端点
   - 自定义健康检查（数据库/Redis/OSS 连通性）
   - 就绪探针/存活探针（K8s 适配）
   - verify: /actuator/health 返回各组件状态

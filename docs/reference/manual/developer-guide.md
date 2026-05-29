# AAF 开发者手册

> 面向使用 AAF 框架开发业务应用的开发者。

## 快速开始

### 环境要求

- JDK 25+、Maven 3.9+、Node.js 22+、pnpm 11+
- PostgreSQL 16+（含 PgVector 扩展）、Redis、Neo4j

### 启动项目

```bash
# 安装前端依赖
pnpm install

# 启动后端
pnpm nx test service   # 编译+单测
pnpm nx build service  # 构建 JAR

# 启动前端
pnpm nx dev webui      # 开发服务器 http://localhost:3000
```

### 配置数据库

```yaml
# apps/service/aaf-api/src/main/resources/application-dev.yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/aaf_db
    username: postgres
    password: your_password
  neo4j:
    uri: bolt://localhost:7687
```

## 核心概念

### 配置驱动视图引擎

AAF 前端采用 EntityDef 配置驱动——定义实体配置即自动生成完整 CRUD 界面。

```typescript
// apps/webui/src/features/entity-engine/entities/index.ts
import { registerEntity } from "../lib/registry"

registerEntity({
  slug: "user",
  label: "用户",
  fields: [
    { name: "name", type: "text", label: "姓名", required: true },
    { name: "email", type: "email", label: "邮箱" },
    { name: "role", type: "select", label: "角色", options: ["admin", "user"] }
  ],
  listView: { columns: ["name", "email", "role"] },
  formView: { labelLayout: "left" }
})
```

访问 `/workspace/user` 即可看到完整的列表+表单+看板视图。

### 智能体系统

五层智能架构：Core → Cognition → Agent → Assistant → Team。

```java
// 创建 Agent 定义
var definition = new AgentDefinition();
definition.setAgentId("code-reviewer");
definition.setName("代码审查 Agent");
definition.setSystemPrompt("你是一个代码审查专家...");
definition.setModelId("deepseek:chat");
definition.setTools("[\"file_read\", \"code_search\"]");

// 通过 AgentPool 执行
var executor = agentPool.borrow(definition);
var result = executor.execute("请审查这段代码的安全性...");
agentPool.release("code-reviewer", executor);
```

### 知识库与 RAG

```java
// 创建知识库
var kb = knowledgeBaseService.create("产品文档", "产品相关知识", userId);

// 导入文档
knowledgeBaseService.importText(kb.getId(), content, "readme.md");

// 语义检索
var results = knowledgeBaseService.search(kb.getId(), "如何配置支付", 5);
```

### 对话系统

基于 AG-UI 协议的流式对话：

```typescript
// 前端使用 Chatter 组件
<Chatter preset="ai" layout="panel" />

// 或 LiveChat 客服模式
<Chatter preset="livechat" layout="drawer" targetUserId="user-123" />
```

后端对话 API：
```
POST /api/ai/chat/run
Content-Type: application/json
Accept: text/event-stream

{"sessionId": "xxx", "message": "你好"}
```

### 工作流引擎

基于 Flowable 的可视化编排：

```java
// 启动工作流
workflowEngine.start("approval-flow", Map.of("applicant", userId));

// 前端可视化编排
// 访问 /workspace/flows 使用拖拽式流程设计器
```

### 支付系统

```java
// 创建支付订单
var order = payOrderService.createOrder(userId, bizOrderNo, amountFen, "wechat");

// 支付回调处理（由渠道 Webhook 触发）
payOrderService.handlePaySuccess(orderId, transactionId);
```

## 模块扩展

### 创建业务模块

```bash
# 后端：在 aaf-api/module/ 下创建包
com.xuejiai.aaf.module.{模块名}/
  ├── domain/       # 实体
  ├── repository/   # 数据访问
  ├── service/      # 业务逻辑
  ├── controller/   # API 接口
  └── vo/           # 请求/响应对象
```

### 注册工具（供 Agent 调用）

```java
@Component
public class MyTool implements ToolCallback {
    @Override
    public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder()
            .name("my_tool")
            .description("我的自定义工具")
            .build();
    }

    @Override
    public String call(String arguments) {
        // 工具逻辑
        return "结果";
    }
}
```

## API 参考

| 模块 | 基础路径 | 说明 |
|------|---------|------|
| 认证 | `/api/auth/` | 登录/注册/OAuth |
| 用户 | `/api/system/users/` | 用户 CRUD |
| 对话 | `/api/ai/chat/` | AI 对话 |
| Agent | `/api/ai/agents/` | Agent 管理 |
| 知识库 | `/api/knowledge/` | 知识库 CRUD + 检索 |
| 工作流 | `/api/workflow/` | 流程管理 |
| 支付 | `/api/pay/` | 订单 + 回调 |
| 文件 | `/api/system/files/` | 上传/下载 |

完整 API 文档：启动后访问 `/swagger-ui.html`（OpenAPI 3.0）。

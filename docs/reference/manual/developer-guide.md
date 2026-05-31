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

### 接入权限控制

开发者新增业务模块时，需要同时考虑"用户直连接口"和"AI 代表用户执行"两条路径。推荐优先继承框架基类，让列表、详情、权限码、数据权限和 AI 动作网关复用同一套规则。

标准实体接入步骤：

1. Service 继承 `BaseCrudService`，实现实体标识、权限资源和查询条件。
2. Controller 继承 `BaseCrudController`，获得分页、查询窗口、详情、创建、更新、删除、批量删除等通用接口。
3. 在 `sys_permission_code` 中增加业务权限码，例如 `system:role:read`、`system:role:update`。
4. 在菜单配置中绑定权限码，控制入口和页面按钮。
5. 在角色配置中授予菜单和权限码。
6. 如果需要 AI 代办，增加一个 `BaseCrudEntityActionAdapter` 适配器，并在 `ai_action_catalog` 中开放对应动作。

示例：

```java
@Service
public class CustomerService extends BaseCrudService<Customer, CustomerVO, CustomerCreateDTO, CustomerUpdateDTO, CustomerPageParam> {
    @Override
    public String getEntitySlug() {
        return "crm-customer";
    }

    @Override
    protected String permissionResource() {
        return "crm:customer";
    }
}
```

权限码约定：

| 操作 | 权限码示例 | 用途 |
|------|------------|------|
| 查询列表/详情 | `crm:customer:read` | 页面读取、AI 查询、选择器 |
| 创建 | `crm:customer:create` | 新增表单、AI 创建 |
| 更新 | `crm:customer:update` | 编辑表单、AI 更新 |
| 删除 | `crm:customer:delete` | 删除按钮、AI 删除 |
| 导出 | `crm:customer:export` | 导出接口 |

AI 业务动作接入：

```java
@Component
public class CustomerAiActionAdapter
        extends BaseCrudEntityActionAdapter<Customer, CustomerVO, CustomerCreateDTO, CustomerUpdateDTO, CustomerPageParam> {
    public CustomerAiActionAdapter(CustomerService service) {
        super(service, CustomerCreateDTO.class, CustomerUpdateDTO.class, CustomerPageParam.class);
    }
}
```

`ai_action_catalog` 是第二层开关。代码 Adapter 决定系统最多能做什么，SQL 目录决定当前向 AI 开放什么：

```sql
INSERT INTO ai_action_catalog (
    action_key, entity_slug, display_name, enabled, risk_level, require_confirm
) VALUES
    ('query', 'crm-customer', '查询客户', TRUE, 'low', FALSE),
    ('update', 'crm-customer', '更新客户', TRUE, 'medium', TRUE);
```

### 配置 AI 工具权限

AI 工具通过 `ToolCallback` 或 `@Tool` 注册，是否开放、风险等级、权限码、额度和输入输出 Schema 由 `ai_tool_catalog` 控制。存在 SQL Provider 时，未配置或禁用的工具不会被执行。

推荐配置字段：

| 字段 | 说明 |
|------|------|
| `tool_name` | 工具名，对应 `@Tool` 方法名或 ToolCallback 名称 |
| `tool_type` | `FUNCTION`、`MCP`、`HTTP`、`SCRIPT`、`WORKFLOW`、`AGENT`、`GENERATIVE` |
| `category` | 工具分类，如 `BUSINESS_ACTION`、`IMAGE_GENERATION`、`VIDEO_GENERATION` |
| `risk_level` | `NONE`、`LOW`、`MEDIUM`、`HIGH`、`CRITICAL` |
| `require_confirm` | 是否强制用户确认 |
| `permission_code` | 工具权限码，如 `tool:image-generate:execute` |
| `entitlement_code` | 权益/额度编码 |
| `cost_expression` | 预估消耗，当前可用固定数字 |
| `input_schema` | JSON Schema，帮助 AI 理解参数 |

示例：

```sql
INSERT INTO ai_tool_catalog (
    tool_name, source, enabled, tool_type, category, risk_level,
    read_only, require_confirm, permission_code,
    entitlement_code, cost_expression, input_schema
) VALUES (
    'generateImage', 'LOCAL', TRUE, 'GENERATIVE', 'IMAGE_GENERATION', 'MEDIUM',
    FALSE, TRUE, 'tool:image-generate:execute',
    'aigc_image', '10',
    '{"type":"object","required":["requestJson"]}'
);
```

Action 链和 Tool 链的权限处理不同。Action 面向业务对象，核心是业务权限与数据权限；Tool 面向能力调用，核心是工具权限、工具类型、风险、内容安全和额度。

Action 链顺序：

```text
AI 身份已认证，owner/delegator 已绑定
-> executeBusinessAction 工具入口权限
-> 解析 action/entity/params
-> ai_action_catalog 已启用
-> 当前 owner 拥有业务 permissionCode
-> 置信度门控（低置信且不可验证则等待用户确认）
-> Action 风险等级与 requireConfirm
-> Action 权益/积分预检
-> BaseCrudService 执行业务，自动应用 L2/L3 数据权限和字段权限
-> 成功后扣费/记录用量
-> 工具审计；后续补 Action 专用审计
```

Tool 链顺序：

```text
工具注册存在
-> ai_tool_catalog 已启用
-> 当前 owner 拥有 permission_code
-> 置信度门控（低置信且不可验证则等待用户确认）
-> Assistant/Role 委托边界、会话授权与工具风险确认
-> 工具风险等级与 require_confirm
-> 按 tool_type/category 执行专用门控（如内容安全、沙箱、网络策略）
-> 权益/积分预检（仅 entitlement_code 有值且 cost_expression > 0 的工具）
-> 执行 ToolCallback
-> 成功后扣费/记录用量
-> 写入审计
```

`tool:default:execute` 只适合低风险通用工具。高风险、写入型、脚本执行、HTTP 调用、生成式内容都应该配置专属权限码和 `require_confirm=true`。

按工具类型的推荐流程：

| 工具类型 | 推荐流程 |
|----------|----------|
| `FUNCTION` 只读 | 工具权限 → 委托/会话授权 → 置信度门控 → 只读自动通过 → 执行 → 审计 |
| `FUNCTION` 写入 | 工具权限 → 委托/会话授权 → 置信度门控 → 风险确认 → 权益/积分预检 → 执行 → 成功后扣费 → 审计 |
| `GENERATIVE` | 工具权限 → 委托/会话授权 → 置信度门控 → 风险确认 → 内容安全审查 → 权益/积分预检 → 执行生成 → 成功后扣费 → 审计 |
| `HTTP` / `SCRIPT` | 工具权限 → 委托/会话授权 → 置信度门控 → 强制确认 → 沙箱/网络策略 → 权益/积分预检 → 执行 → 成功后扣费 → 审计 |
| `AGENT` | 工具权限 → 委托边界 → 置信度门控 → 会话授权 → 子 Agent 执行 → 审计 |

`confidence` 和 `verifiable` 可放在工具参数 JSON 中；业务 Action 请求可直接传 `confidence`、`verifiable`。没有传置信度时不触发置信度门控，仅按权限、风险和类型策略执行。

Tool 链是否走权益/积分，由 `ai_tool_catalog` 决定：`entitlement_code` 有值且 `cost_expression > 0` 才预检和成功后扣费；未配置权益的免费工具不扣费。`executeBusinessAction` 工具入口通常不扣费，具体业务动作是否扣费由 Action 链的 `ai_action_catalog.entitlement_code` 决定。

HITL 是统一人工介入层：工具风险确认、业务动作确认、低置信确认、内容复审和后续额度恢复都通过 `HumanApprovalService` 建单。`PENDING_APPROVAL` 审批通过后会按 `grantScope` 自动写入本会话授权，AI 用相同参数重试即可继续；低置信确认使用独立 `confidence:{subject}` 授权键，不会顺带绕过风险确认；`PENDING_CONTENT_REVIEW` 通过后会记住同一会话、用户、工具和 prompt 的复审结果，避免重复弹审。

实现结合点：

| 能力 | 入口类 | HITL 接入点 | 通过后的效果 |
|------|--------|-------------|--------------|
| 外部工具调用 | `ToolCallDispatcher` | 置信度门控、工具确认、内容安全、额度预检 | 返回统一 JSON，审批/额度/复审恢复后重试 |
| Agent 内部工具调用 | `AgentScopeToolGovernanceService` + `ToolPermissionGuard` | 包装 `AgentTool.callAsync`，适配为 `ToolCallback.call` | 确保 AgentScope/MCP/子 Agent 工具不绕过治理 |
| 业务动作 | `AiBusinessActionExecutor` | Action 置信度、风险确认、Action 额度 | 继续进入 `BaseCrudService` 和业务 Service |
| 审批状态写回 | `HitlApprovalGrantListener` | 监听 `ApprovalResolvedEvent` | 写入会话授权或工具信任 |
| 内容复审写回 | `ContentSafetyService` 默认实现 | 监听 `ApprovalResolvedEvent` | 通过后相同 prompt 不再重复复审 |
| AgentScope 模型计费 | `TokenMeteringHook` | `PreReasoningEvent` 预检，`PostCallEvent` 结算 | AgentScope 主链路按 owner 计费 |

AgentScope 集成采用两层保护：`AafToolWhitelistHook` 负责模型执行前的轻量白名单和目录启用检查；`AgentScopeToolGovernanceService` 负责真正的执行前治理，所有 `AgentTool` 最终都进入 `ToolPermissionGuard`。

AgentScope 自带 HITL 只作为底层暂停/恢复协议使用。AAF 治理链返回可恢复阻塞 JSON 时，包装工具会抛出 `ToolSuspendException`，让 ReAct 循环返回 `TOOL_SUSPENDED`；实际审批单、内容复审、额度恢复仍由 `HumanApprovalService`、`ContentSafetyService` 和 `CreditService` 统一处理。

### 生成式工具与内容审查

生图、生视频、音乐、3D 等生成式能力属于 TOOL，不属于业务 ACTION。它们通过工具目录的 `tool_type=GENERATIVE` 和 `category` 分类开放，由工具权限、额度、确认和内容审查共同控制。

开发者新增生成式工具时：

1. 使用 `@Tool` 或 `ToolCallback` 注册明确的工具名。
2. 在工具逻辑执行前调用 `ContentSafetyService.reviewBeforeGeneration`；如果工具统一经过 `ToolCallDispatcher` / `ToolPermissionGuard`，中心链路会先做一次内容安全预检。
3. 在 `ai_tool_catalog` 中配置 `GENERATIVE` 类型、分类、权限码、额度和确认策略。
4. 生成结果如果需要写业务数据，再通过业务 Service 或 ACTION 完成入库/发布。

默认 `ContentSafetyService` 对普通请求放行；对标记为高风险人工复审的生成式请求返回 `PENDING_CONTENT_REVIEW`，用户或审核人通过后相同参数重试会继续执行。生产环境应替换为模型审查、规则审查、人工复审组合实现，并补充内容审计与违规样本沉淀。

### 工具返回 JSON 协议

工具和业务动作失败时不要返回自由文本，应返回结构化 JSON，让 AI 能判断是否等待、申请授权或停止执行。

```json
{
  "success": false,
  "code": "PENDING_APPROVAL",
  "message": "工具 generateVideo 需要用户确认",
  "pendingApproval": true,
  "recoverable": true,
  "authorization": {
    "mode": "USER_APPROVAL",
    "approvalId": "approval-uuid",
    "requiredBy": "用户即时确认"
  },
  "resume": {
    "strategy": "WAIT_APPROVAL",
    "token": "approval-uuid",
    "instruction": "用户确认后使用相同参数重试"
  }
}
```

常见返回码：

| 返回码 | 开发处理 |
|--------|----------|
| `FORBIDDEN` | 永久权限不足，提示管理员给角色补权限 |
| `PENDING_APPROVAL` | 等待用户确认，确认后同参数重试 |
| `INSUFFICIENT_CREDITS` | 等待充值、升级或额度恢复 |
| `PENDING_CONTENT_REVIEW` | 等待内容审查通过后重试 |
| `TOOL_DISABLED` | 工具目录未启用，停止调用 |
| `TOOL_EXECUTION_ERROR` | 工具执行异常，记录日志和审计 |

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

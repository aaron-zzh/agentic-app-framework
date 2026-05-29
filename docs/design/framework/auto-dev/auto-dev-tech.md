---
level: Practice
layer: Model
purpose: Auto-Dev 技术方案——基于 Kiro/Spring AI/AgentScope/Flowable 的实现
status: draft
version: 0.3.0
date: 2026-05-29
author: AaronZZH
changelog:
  - 2026-05-29 v0.3.0 | 补充从功能设计分离的技术实现细节
  - 2026-05-29 v0.2.0 | 合并 auto-dev-design.md
  - 2026-05-28 v0.1.0 | 占位
---

# Auto-Dev 技术方案

> 基于 Kiro CLI + Spring AI + AgentScope + Flowable 实现。元智能体模式：用 AAF 自身的 Agent 框架开发 AAF 自身。

## 前端实现

### 统一对话入口

复用已有 Chatter 组件，无需新 preset：

```tsx
<Chatter preset="kiro" layout="panel" />
// → 路由到 /api/autodev/kiro/run
// agentRole 由后端根据意图自动决定，前端不传
```

### 意图路由实现

后端 `KiroAgentController.run()` 接收消息后，通过 `SkillMatchEngine` 前注意分流：

```java
// SkillMatchEngine 匹配开发技能
var matchedSkill = skillMatchEngine.match(userMessage);
if (matchedSkill.isPresent() && matchedSkill.get().category() == SkillCategory.DEV) {
    // Pipeline 模式：调用对应开发技能
    return executePipeline(matchedSkill.get(), session);
} else {
    // 对话模式：Agent 自主规划
    return executeChat(userMessage, session);
}
```

会话 state 中存在 `currentEntityDef` 时，后续消息自动关联到该实体上下文。

### AG-UI 事件流扩展

在已有 AG-UI 协议（SSE）基础上扩展开发专用事件：

```typescript
type DevEvent =
  | { type: "ENTITY_DEF_PREVIEW"; data: EntityDef }
  | { type: "MIGRATION_PREVIEW"; data: { sql: string; tables: string[] } }
  | { type: "CODE_PREVIEW"; data: { files: GeneratedFile[] } }
  | { type: "TASK_STATUS"; data: { phase: string; status: string; progress: number } }
  | { type: "DEPLOY_LOG"; data: { line: string; level: string } }
  | { type: "CONFIRM_REQUIRED"; data: { id: string; title: string; options: string[] } }
```

### PreviewPanel 组件

根据 DevEvent 动态渲染，复用已有 ViewEngine（FormView/ListView）：

```tsx
// 有 ENTITY_DEF_PREVIEW 事件时自动弹出预览面板
// Tab 根据最近事件类型自动切换
<PreviewPanel events={devEvents} activeTab={autoDetectedTab} />
```

## 后端实现

### 元智能体架构

```text
AafDevAgent extends BaseAgent
  ├── ProjectContext 注入（代码结构/规范/当前任务）
  ├── 工具集按阶段动态切换
  ├── Flowable 驱动多步骤 Pipeline
  └── RiskEvaluator 三级决策
```

### 三级决策实现

```java
@Component
public class RiskEvaluator {
    public RiskLevel evaluate(CodeChange change) {
        if (change.isDelete() || change.affectsAuth() || change.crossModule(5))
            return RiskLevel.BLOCK;
        if (change.modifiesExisting() || change.addsInterface())
            return RiskLevel.PROMPT;
        return RiskLevel.AUTO;
    }
}
```

### 工具集按阶段切换

| 阶段 | 可用工具 | 禁用工具 |
|------|---------|---------|
| 规划 | 代码搜索、文件读取、结构分析、规范查询 | 文件写入、Git |
| 编码 | 文件读写、代码生成、依赖管理 | Git push、部署 |
| 验证 | 测试执行、编译检查、Lint | 文件写入 |
| 提交 | Git add/commit、PR 创建 | 文件修改 |

```java
public List<Tool> getToolsForPhase(DevPhase phase) {
    return toolRegistry.stream()
        .filter(tool -> tool.allowedPhases().contains(phase))
        .toList();
}
```

### ToolSummary 摘要机制

工具调用结果超长时用小模型摘要，解决"上下文 ≤ 50%"硬约束：

```java
@Component
public class ToolSummaryAdvisor implements CallAroundAdvisor {
    private final ChatModel summaryModel; // GPT-4o-mini
    private final int threshold = 2000;   // 超过此字符数才摘要
}
```

### Flowable Pipeline

```text
┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐
│  规划    │───→│  编码    │───→│  验证    │───→│  审核    │───→│  提交    │
│ServiceTask│   │ServiceTask│   │ServiceTask│   │ UserTask │   │ServiceTask│
└─────────┘    └─────────┘    └─────────┘    └─────────┘    └─────────┘
```

- ServiceTask：AI Agent 自动执行
- UserTask：人类审核节点（Block 级别必经）
- 验证失败自动回退编码阶段重试

### 项目上下文注入

```java
public class ProjectContext {
    private String projectStructure;    // 目录结构摘要
    private String codingStandards;     // 编码规范
    private String currentTask;         // 当前任务描述
    private String recentChanges;       // 最近 Git 变更
    private List<String> relevantFiles; // 任务相关文件
}
```

注入方式：System Prompt + Spring AI `QuestionAnswerAdvisor` 按需检索。

## EntityDef storage 配置实现

### 数据结构

```json
{
  "storage": {
    "mode": "typed",
    "table": "biz_order",
    "primaryKey": "id",
    "relations": [
      { "field": "items", "type": "oneToMany", "targetTable": "biz_order_item", "targetEntity": "biz_order_item", "foreignKey": "order_id", "cascade": ["persist", "remove"] }
    ],
    "indexes": [
      { "fields": ["user_id"], "condition": "deleted = FALSE" },
      { "fields": ["order_no"], "unique": true }
    ]
  }
}
```

### EntityDefService 扩展

在已有 `sys_entity_def.config` JSONB 中增加 `storage` 段，`GenericEntityController` 根据 mode 决定路由：
- `typed` → 路由到生成的强类型 Controller
- `generic` → 通用 JSONB CRUD
- `virtual` → 只读聚合查询

## MigrationGenerator 实现（P0）

```java
public interface MigrationGenerator {
    /** 对比 EntityDef 与当前 DB schema，生成增量 DDL */
    String generateDDL(EntityDef entityDef, DatabaseSchema currentSchema);
    /** 生成 Flyway 版本号 */
    String nextVersion();
}
```

实现思路：
1. 通过 `information_schema` 读取当前表结构
2. 将 EntityDef.storage 转换为目标表结构
3. diff 计算增量（CREATE TABLE / ALTER TABLE / CREATE INDEX）
4. 输出标准 PostgreSQL DDL

## CodegenService 扩展（P1）

### 当前能力

- FreeMarker 模板：entity.ftl / repository.ftl / service.ftl / controller.ftl
- 类型映射：string→String, number→Long, boolean→Boolean, date→LocalDateTime

### 子表/关联扩展

```java
public record RelationDef(
    String field, String type, String targetEntity, String foreignKey, List<String> cascade
) {}
```

模板扩展：
- entity.ftl 增加 `@OneToMany` / `@ManyToOne`
- service.ftl 增加子表 CRUD
- controller.ftl 增加 `/{id}/{relation}` 子资源路由

## AI Enricher 实现（P1）

```java
public interface AiEnricher {
    List<GeneratedFile> enrich(List<GeneratedFile> skeleton, ProjectContext context);
}
```

补充内容：校验注解、状态机、事件发布、权限注解、业务异常处理。

## 热部署方案（P5）

| 环境 | 方案 | 延迟 |
|------|------|------|
| 开发 | Spring DevTools + ClassLoader 热替换 | ~2s |
| 测试 | Git push → CI → Docker 重部署 | ~3min |
| 生产 | Git → PR → 人工审核 → CD | 人工决定 |

## Kiro Skills 映射

| Skill 目录 | 对应 Pipeline 阶段 | 后端实现 |
|-----------|---------|-----------|
| `.kiro/skills/entity-def-generator/` | 实体定义生成 | AI Agent 直接生成 |
| `.kiro/skills/migration-generator/` | 迁移生成 | MigrationGenerator |
| `.kiro/skills/code-generator/` | 代码生成 | CodegenService |
| `.kiro/skills/ai-enricher/` | 业务补充 | AiEnricher |
| `.kiro/skills/sandbox-validator/` | 验证 | SandboxValidator |
| `.kiro/skills/hot-deployer/` | 部署 | HotDeployService |

## 与已有系统的集成

| 已有组件 | 集成方式 |
|---------|---------|
| `Chatter` 组件 | 统一入口，不传 agentRole，后端自动路由 |
| `SkillMatchEngine` | 前注意分流，匹配开发 Skill 触发 Pipeline |
| `EntityDefService` | 扩展 storage 配置段 |
| `GenericEntityController` | generic 模式运行时 CRUD |
| `CodegenService` | 扩展子表/关联模板 |
| `KiroAgentController` | /run 端点，注册开发 Skills |
| AG-UI 协议 | 扩展 DevEvent 事件类型 |
| `ViewEngine` | PreviewPanel 复用 FormView/ListView |
| `GitService` | 代码提交、分支管理、PR 创建 |
| `FlowableWorkflowEngine` | Pipeline 流程编排 |

## 模块结构

```text
aaf-auto-dev/
├── agent/          → Kiro Agent 对话式开发（会话管理+流式交互）
├── codegen/        → 代码生成（FreeMarker 模板）
├── doc/            → 文档智能（语义检索+关系图谱）
├── git/            → Git 操作（commit/branch/PR）
├── monitor/        → 监控接口（kiro-cli 对接）
├── migration/      → 迁移生成器（P0）
├── enricher/       → AI 业务逻辑补充（P1）
└── deploy/         → 热部署（P5）
```

## 协作控制台技术实现

### 数据流

```text
前端（/console 路由）
  ↓ REST + WebSocket
后端（module: collab-console）
  ↓
PostgreSQL（结构化实体） + 文件系统（artifact 原文） + Git（变更历史）
```

### 事件流

WebSocket 分房间（session / task / workspace），事件只触发前端 query invalidate，不直接写客户端 store。

### kiro-cli 对接

```text
kiro-cli → POST /api/monitor/events（状态变更上报）
kiro-cli → POST /api/monitor/logs（执行日志上报）
后端 → SSE /api/monitor/stream → Web 前端实时展示
```

Phase 1 不依赖 kiro-cli hooks，通过文件扫描 + git log 实现只读观察。

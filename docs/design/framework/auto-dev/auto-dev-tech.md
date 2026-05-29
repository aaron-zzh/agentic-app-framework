---
level: Practice
layer: Model
purpose: Auto-Dev 技术方案——元智能体实现、代码生成、Pipeline 执行、热部署
status: draft
version: 0.2.0
date: 2026-05-29
author: AaronZZH
changelog:
  - 2026-05-29 v0.2.0 | 合并 auto-dev-design.md 内容，补充 AI 协作开发技术实现
  - 2026-05-28 v0.1.0 | 占位
---

# Auto-Dev 技术方案

> 元智能体模式：用 AAF 自身的 Agent 框架开发 AAF 自身。基于 Spring AI + AgentScope + Flowable 实现。

## 元智能体架构

```text
┌─────────────────────────────────────────────────────┐
│  AafDevAgent（元智能体）                              │
│  extends BaseAgent                                   │
│  ├── 注入：项目上下文（代码结构/规范/当前任务）       │
│  ├── 工具：文件操作/代码分析/测试执行/Git 操作       │
│  ├── 工作流：Flowable 驱动的多步骤开发流水线         │
│  └── 决策：三级风险判断（Auto/Prompt/Block）         │
└─────────────────────────────────────────────────────┘
```

## 三级决策模型

| 级别 | 条件 | 动作 | 对应风险 |
|------|------|------|---------|
| **Auto** | 新增文件、<50 行改动、补缺依赖 | 直接执行，异步通知 | 🟢 低 |
| **Prompt** | 修改已有文件、改业务逻辑、新增接口 | 展示计划，等待确认 | 🟡 中 |
| **Block** | 删除文件、改权限/安全、≥5 文件跨模块 | 拒绝执行，必须人类审核 | 🔴 高 |

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

## 工具集按阶段动态切换

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

## ToolSummary 摘要机制

工具调用结果超长时用小模型摘要，解决"上下文 ≤ 50%"硬约束：

```java
@Component
public class ToolSummaryAdvisor implements CallAroundAdvisor {
    private final ChatModel summaryModel; // GPT-4o-mini
    private final int threshold = 2000;

    @Override
    public AdvisedResponse aroundCall(AdvisedRequest request, CallAroundAdvisorChain chain) {
        var response = chain.nextAroundCall(request);
        return compressToolOutputs(response);
    }
}
```

## Flowable 开发流水线

```text
┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐
│  规划    │───→│  编码    │───→│  验证    │───→│  审核    │───→│  提交    │
│ServiceTask│   │ServiceTask│   │ServiceTask│   │ UserTask │   │ServiceTask│
│ AI 执行  │   │ AI 执行  │   │ AI 执行  │   │ 人类审核 │   │ AI 执行  │
└─────────┘    └─────────┘    └─────────┘    └─────────┘    └─────────┘
```

- ServiceTask：AI Agent 自动执行
- UserTask：人类审核节点（🔴 必经、🟡 可配置）
- 验证失败自动回退编码阶段重试

## 项目上下文注入

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

## 代码生成技术实现

### CodegenService 当前能力

- FreeMarker 模板：entity.ftl / repository.ftl / service.ftl / controller.ftl
- 类型映射：string→String, number→Long, boolean→Boolean, date→LocalDateTime
- 输出：生成文件写入 `./generated/` 目录

### 待扩展：子表/关联支持

```java
// 扩展 EntityDefDTO 支持关联
public record RelationDef(
    String field,
    String type,          // oneToMany / manyToOne
    String targetEntity,
    String foreignKey,
    List<String> cascade
) {}
```

模板扩展：
- entity.ftl 增加 `@OneToMany` / `@ManyToOne` 注解
- service.ftl 增加子表 CRUD 方法
- controller.ftl 增加 `/{id}/items` 子资源路由

### MigrationGenerator（P0 待实现）

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
3. diff 计算增量（CREATE TABLE / ALTER TABLE ADD COLUMN / CREATE INDEX）
4. 输出标准 PostgreSQL DDL

### AI Enricher（P1 待实现）

```java
public interface AiEnricher {
    /** 分析骨架代码 + 业务上下文，补充业务逻辑 */
    List<GeneratedFile> enrich(List<GeneratedFile> skeleton, ProjectContext context);
}
```

补充内容：
- 字段校验注解（@NotBlank / @Min / @Size）
- 状态机转换逻辑
- 事件发布（ApplicationEvent）
- 权限注解（@PreAuthorize）
- 业务异常处理

## 热部署方案（P3）

| 环境 | 方案 | 延迟 |
|------|------|------|
| 开发 | Spring DevTools + ClassLoader 热替换 | ~2s |
| 测试 | Git push → CI → Docker 重部署 | ~3min |
| 生产 | Git → PR → 人工审核 → CD | 人工决定 |

开发环境热加载流程：
```text
代码生成 → 写入 src/ → DevTools 检测变更 → 自动重启 → WebSocket 通知前端刷新
```

## 模块结构

```text
aaf-auto-dev/
├── agent/          → Kiro Agent 对话式开发（会话管理+流式交互）
├── codegen/        → 代码生成（FreeMarker 模板）
│   └── dto/        → EntityDefDTO / GeneratedFile
├── doc/            → 文档智能（语义检索+关系图谱）
├── git/            → Git 操作（commit/branch/PR）
├── monitor/        → 监控接口（kiro-cli 对接）
├── migration/      → 迁移生成器（P0 待实现）
├── enricher/       → AI 业务逻辑补充（P1 待实现）
└── deploy/         → 热部署（P3 待实现）
```

## 与 Kiro Skills 的关系

每个 Pipeline 阶段对应一个 Kiro Skill，Agent 按需调用：

| Skill 文件 | 对应阶段 | 后端实现类 |
|-----------|---------|-----------|
| `.kiro/skills/entity-def-generator/` | EntityDef 生成 | AI Agent 直接生成 |
| `.kiro/skills/migration-generator/` | 迁移生成 | MigrationGenerator |
| `.kiro/skills/code-generator/` | 代码生成 | CodegenService |
| `.kiro/skills/ai-enricher/` | 业务补充 | AiEnricher |
| `.kiro/skills/sandbox-validator/` | 验证 | SandboxValidator |
| `.kiro/skills/hot-deployer/` | 部署 | HotDeployService |

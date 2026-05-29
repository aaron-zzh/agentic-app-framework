---
level: Practice
layer: Product
purpose: AAF 后端 DSL 引擎初步设计——DSL 解析、转化、路由与执行
status: draft
version: 1.0.0
date: 2026-05-13
author: AaronZZH
---

# DSL 引擎（后端）

> Magic-DSL 的后端解析与执行引擎。负责 L1→L2→L3 层间转化、置信度门控、执行路由。
> 语言设计：[magic-dsl.md](./magic-dsl.md) | 前端运行时：[dsl-runtime.md](./dsl-runtime.md)
> 所属体系：[元引擎](../engine/meta/meta-engine.md)

## 一、定位

DSL 引擎是元引擎的核心子系统，职责：接收任意形式的 DSL 输入 → 解析 → 转化 → 校验 → 路由到对应专项引擎执行。

```text
输入（前端指令 / AI 生成 / API 调用 / 事件触发）
  ↓
┌─────────────────────────────────────────┐
│            DSL 引擎                      │
│  ┌─────────┐ ┌─────────┐ ┌──────────┐  │
│  │ 解析器   │→│ 转化器   │→│ 路由器    │  │
│  │ Parser  │ │Transformer│ │ Router   │  │
│  └─────────┘ └─────────┘ └──────────┘  │
│       ↕            ↕           ↕        │
│  ┌─────────┐ ┌─────────┐ ┌──────────┐  │
│  │ AI 补全  │ │ 校验器   │ │ 门控器    │  │
│  │ LLM     │ │Validator │ │ Gate     │  │
│  └─────────┘ └─────────┘ └──────────┘  │
└─────────────────────────────────────────┘
  ↓
执行层（专项引擎）
  ├── 实体运行时（动态建表 + CRUD）
  ├── 工作流引擎（Flowable）
  ├── Agent 调度器（Spring AI）
  ├── 权限引擎
  └── 前端推送（doc 域 DSL → SSE → 前端渲染）
```

## 二、核心流程

```text
POST /api/dsl/execute
Body: { input: string, context?: DslContext }

1. 范式识别 → 判断输入是声明式/命令式/函数式/自然语言
2. 解析 → 生成 L1 AST（宽松，允许不完整）
3. AI 补全（如需）→ 缺失字段推断、模糊语义消歧
4. 转化 → L1 AST → L2 结构化 DSL（语义完整）
5. 校验 → 类型检查 + 规范一致性 + 引用完整性
6. 置信度评估 → 高/中/低
7. 门控 → >0.9 自动执行 | 0.7-0.9 返回确认请求 | <0.7 返回澄清请求
8. 编译 → L2 → L3 执行 IR
9. 路由 → 按域分发到对应引擎
10. 执行 → 返回结果 / 推送状态
```

## 三、模块结构

```text
aaf-framework/src/.../dsl/
├── parser/
│   ├── DslParser.java              → 统一入口（范式识别 + 分发）
│   ├── CommandParser.java          → 命令式解析（/create, /query, /deploy）
│   ├── DeclarativeParser.java      → 声明式解析（entity, workflow, view）
│   └── ExpressionParser.java       → 表达式解析（条件/模板/过滤）
├── transformer/
│   ├── L1ToL2Transformer.java      → L1→L2 转化（结构补全）
│   ├── L2ToL3Compiler.java         → L2→L3 编译（生成执行 IR）
│   └── AiCompletionService.java    → AI 辅助补全（调用 LLM）
├── validator/
│   ├── TypeChecker.java            → 类型检查
│   ├── ReferenceValidator.java     → 引用完整性（实体/字段/工作流是否存在）
│   └── SpecConsistencyChecker.java → 规范一致性
├── router/
│   ├── DslRouter.java              → 按域路由到对应引擎
│   └── ConfidenceGate.java         → 置信度门控
├── model/
│   ├── DslInput.java               → 输入模型
│   ├── DslAst.java                 → AST 节点定义
│   ├── DslIR.java                  → L3 执行 IR
│   └── DslResult.java              → 执行结果
└── DslEngine.java                  → 引擎门面（统一 API）
```

## 四、API 设计

```java
// 统一执行入口
@PostMapping("/api/dsl/execute")
public Mono<DslResult> execute(@RequestBody DslInput input) {
    return dslEngine.execute(input);
}

// 仅解析不执行（前端预览/校验用）
@PostMapping("/api/dsl/parse")
public Mono<DslParseResult> parse(@RequestBody DslInput input) {
    return dslEngine.parse(input);
}

// 自动补全建议（前端编辑器用）
@PostMapping("/api/dsl/completions")
public Mono<List<Completion>> completions(@RequestBody CompletionRequest req) {
    return dslEngine.completions(req);
}
```

```java
// 输入模型
public record DslInput(
    String input,           // DSL 文本
    DslContext context,     // 执行上下文（当前实体/用户/页面）
    boolean dryRun          // 仅校验不执行
) {}

// 执行结果
public record DslResult(
    DslStatus status,       // SUCCESS / NEEDS_CONFIRM / NEEDS_CLARIFY / ERROR
    Object data,            // 执行结果数据
    String dslL2,           // 转化后的 L2 DSL（供前端展示）
    double confidence,      // 置信度
    List<String> errors,    // 错误/警告
    String clarifyQuestion  // 需澄清时的问题
) {}
```

## 五、域路由规则

| 域 | 子域 | 路由目标 | 执行方式 |
|----|------|---------|---------|
| dev/schema | 实体定义 | EntityRuntime | 动态建表 + 注册 EntityDef |
| dev/api | 接口定义 | ApiGenerator | 生成 Controller/Service |
| dev/flow | 工作流定义 | Flowable | 部署 BPMN 流程 |
| runtime/flow | 工作流执行 | Flowable | 启动流程实例 |
| runtime/agent | Agent 配置 | Spring AI | 注册/更新 Agent |
| runtime/policy | 权限规则 | PermissionEngine | 更新访问控制 |
| doc/* | 文档/布局/样式 | 前端推送 | SSE → 前端 DSL 运行时渲染 |

## 六、置信度门控

```java
public enum DslStatus {
    SUCCESS,          // 置信度 > 0.9，已自动执行
    NEEDS_CONFIRM,    // 置信度 0.7-0.9，等待用户确认
    NEEDS_CLARIFY,    // 置信度 < 0.7，需要澄清
    ERROR             // 解析/校验失败
}
```

不可逆操作（删除/部署/权限变更）无论置信度强制返回 `NEEDS_CONFIRM`。

## 七、与 AI 的协作

L1→L2 转化中，AI 负责不确定性部分：

```text
输入："帮我创建用户模块，字段是 name 和 email"

系统解析：
  - 范式：自然语言混合
  - 域：dev/schema
  - 已识别：entity name="用户", fields=[name, email]
  - 缺失：字段类型、约束

AI 补全：
  - name → String @required（常见模式推断）
  - email → Email @unique（语义推断）
  - 置信度：0.85（中，需确认）

返回前端：
  status: NEEDS_CONFIRM
  dslL2: 'entity User { name: String @required, email: Email @unique }'
  confidence: 0.85
```

## 八、实现路径

| 阶段 | 能力 |
|------|------|
| v0.1 | CommandParser（/create, /query）+ 直接路由到现有 Service 层 |
| v0.2 | DeclarativeParser（entity, workflow）+ L1→L2 转化 + 基础校验 |
| v0.3 | AI 补全集成 + 置信度门控 + ExpressionParser |
| v1.0 | 完整 L2→L3 编译 + 全域路由 + 自进化机制 |

## 九、设计约束

- **不过度设计**：v0.1 阶段 DSL 引擎仅做命令解析 + 路由转发，不实现完整编译器
- **渐进增强**：先支持命令式（最简单），再支持声明式，最后支持自然语言混合
- **前端驱动**：后端能力按前端需要逐步实现，不预先构建完整引擎
- **AI 兜底**：解析失败时 fallback 到 LLM 理解，不要求 100% 语法覆盖

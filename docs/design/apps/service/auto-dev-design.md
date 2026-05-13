---
level: Practice
layer: Model
purpose: AAF Auto-Dev 模块设计思路（参考 Mastra agent-builder）
status: draft
version: 1.0.0
date: 2026-05-10
author: AaronZZH
---

# Auto-Dev 模块设计思路

> 参考 Mastra `@mastra/agent-builder` 的元智能体模式，结合 AAF 技术栈（Spring AI + AgentScope + Flowable）设计。

## 一、核心思想

Auto-Dev 是一个**元智能体**——用 AAF 自身的 Agent 框架来开发 AAF 自身。它不是独立系统，而是继承框架基础能力的特殊 Agent。

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

## 二、三级决策模型

参考 Mastra 的 Auto/Prompt/Block 规则，对齐 AAF 协作规范的风险分级：

| 级别 | 条件 | 动作 | 对应 AAF 风险 |
|------|------|------|---|
| **Auto** | 新增文件、补缺依赖、追加配置、<50 行改动 | 直接执行，异步通知 | 🟢 低 |
| **Prompt** | 修改已有文件、改业务逻辑、依赖升级、新增接口 | 展示计划，等待人类确认 | 🟡 中 |
| **Block** | 删除文件、降级依赖、改权限/安全、改接口签名、≥5 文件跨模块 | 拒绝自动执行，必须人类审核 | 🔴 高 |

决策规则可配置化，存储为规则引擎条件：

```java
// 伪代码示意
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

## 三、工具集按阶段动态切换

不同执行阶段暴露不同工具，避免 Agent 在错误阶段执行危险操作：

| 阶段 | 可用工具 | 禁用工具 |
|------|---------|---------|
| **规划** | 代码搜索、文件读取、结构分析、规范查询 | 文件写入、Git 操作、测试执行 |
| **编码** | 文件读写、代码生成、依赖管理 | Git push、部署 |
| **验证** | 测试执行、编译检查、Lint | 文件写入（只读验证） |
| **提交** | Git add/commit、PR 创建 | 文件修改（已冻结） |

```java
public List<Tool> getToolsForPhase(DevPhase phase) {
    return toolRegistry.stream()
        .filter(tool -> tool.allowedPhases().contains(phase))
        .toList();
}
```

## 四、ToolSummary 摘要机制

工具调用结果（如 `grep` 输出、文件内容）往往很长，直接进上下文会爆 token。用小模型摘要后再喂主模型：

```text
主模型调用工具 → 工具返回原始结果（可能 10KB+）
                      ↓
              ToolSummaryAdvisor 拦截
                      ↓
              小模型摘要（保留关键信息，压缩到 1-2KB）
                      ↓
              摘要结果回传主模型上下文
```

实现为 Spring AI 的 Advisor 链：

```java
@Component
public class ToolSummaryAdvisor implements CallAroundAdvisor {
    private final ChatModel summaryModel; // 小模型（如 GPT-4o-mini）
    private final int threshold = 2000;   // 超过此字符数才摘要

    @Override
    public AdvisedResponse aroundCall(AdvisedRequest request, CallAroundAdvisorChain chain) {
        var response = chain.nextAroundCall(request);
        // 对工具调用结果超长的进行摘要压缩
        return compressToolOutputs(response);
    }
}
```

解决 AAF 硬约束："上下文使用率 ≤ 50%"。

## 五、Flowable 开发流水线

用 Flowable 工作流引擎驱动多步骤开发流程，每步可暂停等待人类审核：

```text
┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐
│  规划    │───→│  编码    │───→│  验证    │───→│  审核    │───→│  提交    │
│ServiceTask│   │ServiceTask│   │ServiceTask│   │ UserTask │   │ServiceTask│
│ AI 执行  │   │ AI 执行  │   │ AI 执行  │   │ 人类审核 │   │ AI 执行  │
└─────────┘    └─────────┘    └─────────┘    └─────────┘    └─────────┘
      │              │              │                              │
      ↓              ↓              ↓                              ↓
  任务拆分       代码生成      check:affected              Git commit + PR
  + 风险评估    + 单元测试      全绿？
                                  │
                              否 → 回退编码
```

关键设计：
- **ServiceTask**：AI Agent 自动执行（规划/编码/验证/提交）
- **UserTask**：人类审核节点（🔴 高风险必经、🟡 中风险可配置）
- **suspend/resume**：每步完成后可暂停，支持跨会话恢复
- **回退**：验证失败自动回退到编码阶段重试

## 六、项目上下文注入

元智能体启动时注入项目上下文，让 AI 了解当前项目状态：

```java
public class ProjectContext {
    private String projectStructure;    // 目录结构摘要
    private String codingStandards;     // 编码规范（从 docs/ 加载）
    private String currentTask;         // 当前任务描述（从迭代文件加载）
    private String recentChanges;       // 最近 Git 变更
    private List<String> relevantFiles; // 任务相关文件列表
}
```

注入方式：作为 System Prompt 的一部分，或通过 Spring AI 的 `QuestionAnswerAdvisor` 按需检索。

## 七、与 AAF 现有架构的集成

```text
aaf-auto-dev/
├── agent/
│   ├── AafDevAgent.java          → 元智能体主类（extends BaseAgent）
│   └── DevPhase.java             → 阶段枚举
├── advisor/
│   ├── ToolSummaryAdvisor.java   → 工具结果摘要
│   └── RiskEvaluator.java        → 三级风险评估
├── tool/
│   ├── FileReadTool.java         → 文件读取
│   ├── FileWriteTool.java        → 文件写入
│   ├── CodeSearchTool.java       → 代码搜索
│   ├── TestRunTool.java          → 测试执行
│   └── GitTool.java              → Git 操作
├── workflow/
│   ├── dev-pipeline.bpmn20.xml   → Flowable 流程定义
│   └── DevPipelineService.java   → 流程服务
└── context/
    ├── ProjectContext.java       → 项目上下文
    └── ProjectScanner.java       → 项目结构扫描
```

## 八、参考来源

- Mastra `@mastra/agent-builder`：元智能体模式、三级决策、ToolSummaryProcessor、Workflow 分步
- AAF 协作规范：风险分级（🟢/🟡/🔴）、完工门禁（check:affected 全绿）
- AAF 迭代过程：任务流水线（product→architect→developer→tester→qa）

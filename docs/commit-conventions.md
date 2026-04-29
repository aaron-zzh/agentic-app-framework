# 提交规范

采用[约定式提交](https://www.conventionalcommits.org/zh-hans/)。

## 格式

```text
<类型>[可选 范围]: <描述>

[可选 正文]

[可选 脚注]
```

## 类型

| 类型 | 说明 |
|------|------|
| `feat` | 新功能 |
| `fix` | 修复 Bug |
| `docs` | 文档变更 |
| `refactor` | 重构（不改功能、不修 Bug） |
| `test` | 测试相关 |
| `chore` | 构建、CI、依赖等杂项 |
| `style` | 格式调整（不影响逻辑） |
| `perf` | 性能优化 |

## 范围

对应模块名：`agent`、`tool`、`memory`、`rag`、`orchestration`、`api`、`infra`、`common`。跨模块可省略。

## 示例

```text
feat(agent): 定义 Agent 接口和 AgentContext
fix(tool): 修复 MCP 工具注册时的空指针
docs: 添加提交规范文档
refactor(memory): 将 ConversationMemory 改为响应式接口
test(agent): 添加 ChatAgent 单元测试
chore: 升级 Spring Boot 到 4.0.6
```

## 破坏性变更

在类型后加 `!`，或在脚注中写 `BREAKING CHANGE:`：

```
feat(agent)!: 重新设计 Agent 生命周期接口

BREAKING CHANGE: Agent.execute() 返回类型从 String 改为 Mono<AgentResponse>
```

## 任务关联

提交必须在脚注中通过 `Task:` 关联 backlog 任务编号。多个任务用逗号分隔。纯杂项（如格式调整）可省略。

```
feat(agent): 定义 Agent 接口和 AgentContext

实现 Agent、AgentContext、AgentCapability 核心接口

Task: AAF-001
```

## 补充规则

- 描述字段必须直接跟在 `<类型>(范围):` 的冒号和空格之后，是对变更的简短总结
- 正文可选，必须起始于描述之后的一个空行，可用空行分隔段落
- 脚注可选，位于正文之后的一个空行，格式为 `令牌: 值` 或 `令牌 #值`
- 脚注令牌用 `-` 作连字符（如 `Reviewed-by`），`BREAKING CHANGE` 例外
- 提交符合多种类型时，应拆分为多次提交
- 类型统一使用小写

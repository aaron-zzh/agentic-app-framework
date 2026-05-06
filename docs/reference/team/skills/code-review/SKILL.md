---
name: code-review
description: '结构化代码审查。USE WHEN: (1) 分支准备提 PR 前的预着陆审查、(2) architect agent 评审 developer 产出、(3) 用户说"review"、"审查代码"、"检查这个分支"。不覆盖安全专项审计（用 security-audit）或视觉审计。'
---

## 角色

你是一个偏执的 Staff Engineer。通过 CI 的代码不代表安全。你找的是**能在生产环境爆炸的 bug**，不是风格 nitpick。

## 工作流

1. 获取 diff：`git diff origin/main...HEAD --stat` + `git diff origin/main...HEAD`
2. 按下方清单两轮审查
3. 产出审查报告，格式见底部

## 审查清单

### Pass 1 — CRITICAL（最高优先级）

#### SQL 与数据安全
- SQL 字符串拼接（即使值是 `.intValue()`——用参数化查询）
- TOCTOU 竞态：check-then-set 应为原子 `WHERE` + `UPDATE`
- 绕过模型校验的直接 DB 写入
- N+1 查询：循环中使用关联但缺少 eager loading（JPA: `@EntityGraph`/`JOIN FETCH`；Prisma: `include`）

#### 竞态条件与并发
- 读-检查-写无唯一约束或未捕获重复键异常
- find-or-create 无唯一 DB 索引——并发调用可创建重复
- 状态转换未用原子 `WHERE old_status = ? UPDATE SET new_status`
- 不安全的 HTML 渲染（React: `dangerouslySetInnerHTML`；Vue: `v-html`）用于用户可控数据

#### LLM 输出信任边界
- LLM 生成的值（邮箱、URL、名称）写入 DB 前无格式校验
- 结构化工具输出（数组、对象）未做类型/形状检查就写库

#### 枚举与值完整性
当 diff 引入新枚举值/状态字符串/类型常量时：
- **追踪每个消费者**：读（不只是 grep）每个 switch/filter/display 该值的文件
- **检查白名单数组**：搜索包含兄弟值的数组/列表
- **检查 case/if-else 链**：新值是否会落入错误的 default？

### Pass 2 — INFORMATIONAL

#### 条件副作用
- 分支遗漏副作用（一个分支做了操作，另一个分支跳过但日志声称已完成）

#### 死代码与一致性
- 赋值但从未读取的变量
- 代码改了但注释/文档描述的还是旧行为

#### 测试缺口
- 负面路径测试只断言类型/状态但不检查副作用
- 安全执行特性（阻断、限流、认证）无集成测试验证执行路径

#### 完整性缺口
- 快捷实现，完整版本成本 < 30 分钟（部分枚举处理、不完整错误路径）
- 测试覆盖缺口中"lake"级别的（加测试成本低但没加）

#### 性能与包体积
- 已知重量级依赖（moment.js → date-fns；lodash 全量 → lodash-es）
- 未加 `loading="lazy"` 的图片
- ESM 代码库中的 `require()` 调用
- `useEffect` 中串行 fetch（请求瀑布）

## 产出格式

```
代码审查：N 个问题（X critical，Y informational）

**自动修复：**
- [file:line] 问题 → 已修复

**需要决策：**
- [file:line] 问题描述
  建议修复：具体方案
```

无问题时：`代码审查：未发现问题。`

## Fix-First 规则

- **自动修复**：死代码、过时注释、N+1 查询、明显的缺失 null check
- **需要决策**：安全相关、竞态条件、架构选择、涉及接口变更

## Gotchas

- AAF 用 Spring WebFlux 响应式——检查 `Mono`/`Flux` 链中是否有阻塞调用（`block()`、JDBC）
- 前端用 TanStack Query 管服务端状态——如果看到服务端数据复制到 Zustand，标记为 bug
- 检查 `aaf-common` 的改动是否引入了业务依赖（违反分层约束）

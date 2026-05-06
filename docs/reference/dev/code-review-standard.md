---
level: Practice
layer: Model
purpose: 定义代码审查的检查项、流程和产出要求，architect 为主要执行者
status: published
version: 1.0.0
date: 2026-05-06
author: AaronZZH
changelog:
  - 2026-05-06 | 补充 Front Matter
  - 2026-05-05 | 明确 architect/qa 边界：qa 不重复检查代码内容
---

# 代码审查规范

> 定义代码审查的检查项、流程和产出要求。architect 是代码审查的主要执行者，在 `check` 全绿之后、tester `acceptance` 之前进行。
>
> **代码/设计的规范合规以本规范为权威判定源**。qa 在 [过程审计](../team/process-audit-standard.md) 阶段直接采纳 architect 的结论，不重复检查代码内容——qa 只查"过程"和"文档"，不查代码。

## 审查前提

审查开始前必须确认：

- [ ] developer 已本地跑过 `pnpm check:affected` 且全绿
- [ ] CI 的 format-check 和 check 阶段已通过
- [ ] dev-log.md 已更新，说明实现与设计的偏离（如有）

**未满足前提不启动审查**，直接驳回 developer 继续自验证。

## 审查维度

每次代码审查必须覆盖以下维度：

### 1. 规范合规

- 命名是否符合 [编码风格规范](apps/service/coding-style-standard.md)
- 分层是否符合 [架构约束](architecture-constraints.md)
- 异常处理是否完整（不吞异常、不泛型 catch）
- 是否违反 [编码规范硬约束 5-9](../../../.kiro/skills/coding-standards/SKILL.md)：兼容层 / 任务外重构 / 命名混淆 / 静默降置信度

### 2. 设计符合性

- 实现是否与设计文档一致（接口签名、类结构、数据模型、ADR）
- 偏离设计时是否有合理理由并记录在 dev-log.md
- 接口签名变更是否已同步给所有使用方（由协调者协调）

### 3. 安全审查

- SQL 注入：是否使用参数化查询 / 禁用字符串拼接
- 权限校验：接口是否有正确的鉴权（Spring Security 注解 / 手动校验）
- 敏感数据：日志和响应中是否泄露 token / password / 身份证号等
- 输入校验：外部输入是否做了验证和清洗（Bean Validation / Zod）
- 依赖安全：新增依赖是否来自可信源、版本是否被 CVE 标记

### 4. 对称性检查（审查清单）

许多 bug 源于**某一侧处理了，另一侧没处理**的不对称设计。审查时逐项核对：

| # | 检查项 | 场景示例 |
|---|--------|---------|
| 1 | **生产者 vs 消费者** | 服务端有心跳 54s，客户端有没有对应的探活？（否则半开连接) |
| 2 | **创建 vs 删除** | createWorkspace 有清理订阅逻辑？deleteWorkspace 有断开订阅逻辑？ |
| 3 | **加密 vs 解密** | 序列化用 A 算法，反序列化是否用同一 A？（否则跨版本数据损坏） |
| 4 | **事务开启 vs 提交/回滚** | `@Transactional` 覆盖？异常分支有 rollback？ |
| 5 | **监听器注册 vs 注销** | `addEventListener` / `@PostConstruct` → 有 `removeEventListener` / `@PreDestroy`？（否则内存泄漏） |
| 6 | **资源申请 vs 释放** | open / connect / acquire → close / disconnect / release 路径对称？ |
| 7 | **状态变更 vs 通知** | 改了数据但没发事件？发了事件但没写数据？ |
| 8 | **认证 vs 鉴权** | authenticated 通过后，authorization 是否真的按资源所有权校验？ |
| 9 | **成功路径 vs 错误路径** | 成功分支有清理逻辑，错误分支是否也清理？（finally / 补偿事务） |
| 10 | **入队 vs 出队** | 消息入队了，消费者有没有注册？失败消息有 DLQ 吗？ |
| 11 | **缓存写入 vs 失效** | 写数据库时是否同步失效缓存？TTL 是否合理？ |
| 12 | **前端乐观更新 vs 回滚** | 乐观更新失败后是否回滚 UI 状态？ |
| 13 | **已有模式 vs 新建抽象** | 项目中已有同类实现（工具类/组件/模式），新代码是否复用？新建并行抽象即 major |

发现**任何一项不对称且非预期**，即为 major 或 blocker（取决于影响面）。

### 5. 性能与可扩展性

- N+1 查询：JPA 懒加载是否可能触发循环查询
- 阻塞调用：WebFlux 中是否误用同步 API
- 内存泄漏：缓存是否有上限、监听器是否注销、子进程是否回收
- 热点：高频路径的锁竞争、序列化开销

### 6. 可测试性

- 新增业务逻辑是否有对应单元测试（developer 的 `check`）
- 复杂分支是否有测试覆盖
- 外部依赖是否可 Mock
- **覆盖审计**：diff 中新增/修改的 public 方法，是否有对应 `*Test.java` / `*.test.ts`？缺失即 major

### 7. No-Duplication（禁止重复）

- **跨 app 重复**：同一逻辑存在于 `apps/service/` 和 `apps/webui/` 两处 → 必须提取到 `packages/` 共享包（v0.2 `packages/` 落地后生效）
- **同 app 内重复**：同一模块内 ≥2 处相同逻辑（>10 行）→ 提取为私有方法或工具类
- **跨模块重复**：不同业务包中出现相同工具逻辑 → 提取到 `aaf-common`
- **配置/常量重复**：同一魔法值出现 ≥2 次 → 提取为常量或配置项
- **测试重复**：多个测试中相同的 setup/mock 逻辑 → 提取为测试 fixture

发现重复即为 **major**（影响可维护性），跨 app 重复且已有共享包机制时为 **blocker**。

## 审查流程

```text
developer check 全绿
  ↓
architect 拉代码阅读
  ↓
按 1-7 维度逐项检查（对称性清单逐条核对）
  ↓
输出 review.md：问题分级 + 具体文件行号 + 修复建议
  ├─ blocker / major → 退回 developer
  └─ minor → 记录但不阻塞
  ↓
developer 修复后回到 check → architect 重审（只审修改部分）
```

## 问题分级

遵循 [协作规范 问题分级](../team/collaboration-standard.md#质量门控)：

- **blocker**：编译错、运行崩、安全漏洞、签名不符、对称性破坏导致数据丢失
- **major**：缺异常处理、关键路径无测试、违反分层、性能隐患、对称性不完整
- **minor**：命名不规范、注释缺失、风格不一致、可读性建议

## 产出

- `docs/task/{版本}/{AAF-XXX}/review.md`
- 格式：见 [模板](../../task/_template/review.md)
- 必填：审查人、日期、问题分级统计、具体问题清单（含对称性检查勾选状态）、修复状态

## 与其他审查活动的区别

| 维度 | review（代码审查） | audit（架构审计） | process-audit（过程审计） |
|------|-------------------|-------------------|--------------------------|
| 执行者 | architect | architect | qa |
| 查什么 | 代码/设计内容是否符合规范 | 跨模块/跨任务的系统级根因 | 流程和文档是否齐全合规 |
| 触发时机 | 每次 developer 提交 | 发现系统级问题时 | tester acceptance 通过 + review 完成后 |
| 粒度 | 单次改动 diff | 跨模块/跨任务根因追溯 | 整个任务产出物链 |
| 读代码？ | 是 | 是 | 否 |
| 输出 | `review.md`（修复建议） | `audit.md`（六段式根因报告） | `process-audit.md`（质量门控判定） |

- 发现代码审查中反复出现同类问题，应升级为 audit，参考 [audit.md 模板](../../task/_template/audit.md)。
- qa 不重复 architect 的代码规范判定：直接从 `review.md` 汇总 blocker/major/minor 计数。qa 发现 architect review 有明显遗漏时，作为**流程问题**记录要求补审，不自行下代码规范结论。


## 跨模型审查（🔴 高风险适用）

🔴 高风险变更的 architect 代码审查**建议**由不同于 developer 的 LLM 执行，避免同模型"盲点共振"。

- 实现方式：在 `.kiro/agents/architect.json` 中配置与 developer agent 不同的 `"model"` 字段
- 🟡 中风险：不强制，协调者自行判断
- 🟢 低风险：不适用（协调者自审）

> 起因：同一 LLM 对自己生成的代码倾向于"自洽性确认"而非对抗性审查，容易遗漏结构性问题。
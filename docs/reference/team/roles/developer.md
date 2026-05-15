# 开发工程师（developer-*）

> 划分原则：按架构分层 + 端划分，层不随模块增长而变化。同层模块技术同质，一个 developer 处理。

| AI 智能体 | 技术栈 | 详细说明 |
|-----------|-------|---------|
| developer-web | Next.js / React / TypeScript | [developer-web.md](developer-web.md) |
| developer-app | UniApp / Vue | [developer-app.md](developer-app.md) |
| developer-api | Java / Spring Boot / Spring AI | [developer-api.md](developer-api.md) |

## 通用职责边界（所有 developer-* 适用）

### 必做

1. **按设计实现**：对照 architect 的技术设计和 designer 的 UI 设计编码
2. **编写单元测试**：核心业务逻辑覆盖单测，命名 `XxxTest.java` / `*.test.ts(x)`
3. **完工前必跑 `pnpm check:affected`**：通过 lint + typecheck + 单测 + build 才能汇报（详见 [AI 自验证循环](../process-standard.md#331-ai-自验证循环developer-强制内循环)）
4. **开发日志**：在 `docs/task/{版本}/{AAF-XXX}/dev-log.md` 记录与设计不一致之处、注意事项

### 不做（交给其他角色）

| 事项 | 归属 |
|------|------|
| 验收测试 / 集成测试（`*IT.java`、`*AcceptanceTest.java`、`*.accept.test.ts(x)`） | tester |
| 对照 AC 验证需求覆盖 | tester |
| 代码审查 / 架构审计 | architect |
| 过程合规检查 | qa |

### 交接时机

- `pnpm check:affected` 全绿 → 汇报给协调者 → 流入 architect 代码审查 → tester 验收
- `check` 失败时汇报：视为未完工，协调者有权驳回，继续自己修
- 发现设计与实现冲突：立即停手，记录到 dev-log 并反馈 architect，不自行偏离

## 硬约束

遵守 [编码规范 #硬约束 5-9](../../../../.kiro/skills/coding-standards/SKILL.md)：

- 不加兼容层（除非用户显式要求向后兼容）
- 不做任务外重构
- 完工前必跑 `pnpm check:affected`
- 测试命名区分 developer / tester 产出
- 不静默降低置信度

## 输出规范

- 源码：按架构约束放置（详见各 developer-* 子文档）
- 单元测试：`src/test/java/**/*Test.java` 或 `src/**/*.test.ts(x)`
- 开发日志：`docs/task/{版本}/{AAF-XXX}/dev-log.md`
- 提交消息：Conventional Commits，脚注关联技术任务 `Task: #XXXNN`

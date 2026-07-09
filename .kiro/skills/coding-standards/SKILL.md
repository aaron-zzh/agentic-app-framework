---
name: coding-standards
description: 'AAF 项目编码规范。USE WHEN: (1) 编写或修改任何代码、(2) 进行代码审查、(3) 拆分技术任务或定义验收标准、(4) 发现代码与规范不一致时。'
---

AAF 项目编码规范。所有 developer / architect / tester agent 在编码和审查时遵循。

> 这些准则倾向于**谨慎而非速度**。对于琐碎任务（拼写错误、显而易见的一行修改），自行判断——并非每个改动都需要完整的严谨流程。目标是减少非琐碎工作中代价高昂的错误，而不是拖慢简单任务。

## 四大行为准则

### 1. 编码前思考（Think Before Coding）

- 明确说明假设，有歧义就问，不默默选择
- 存在多种方案时全部呈现，说明权衡
- 困惑时停下来，指出不清楚的地方

**检验**：我是否在假设某件事而没有告知用户？

### 2. 简洁优先（Simplicity First）

- 不添加任务未要求的功能、抽象、配置项
- 能用 50 行写完的不写 100 行
- 不为一次性代码创建策略类、工厂、接口层

**检验**：资深工程师看到这段代码会觉得过于复杂吗？

### 3. 精准修改（Surgical Changes）

- 只改任务要求的代码，不顺手重构相邻代码
- 不改注释格式、不调整无关缩进
- 发现无关死代码只提不删

**检验**：diff 中每一行改动都能追溯到用户请求吗？

### 4. 目标驱动执行（Goal-Driven Execution）

将指令转化为可验证目标：

- "添加验证" → 先写失败测试，再让它通过
- "修复 bug" → 先写重现测试，再让它通过
- "重构 X" → 确保重构前后测试都通过

多步骤任务格式：`[步骤] → verify: [检查标准]`

## AAF 架构约束

- 分层依赖方向：`controller → application → domain → gateway`，禁止反向
- Domain 层零外部依赖（不引入 Spring、JPA 等框架注解）
- 业务模块包名：`com.xuejiai.aaf.module.{模块名}`
- 依赖版本统一在 `aaf-dependencies` 中管理，模块内不写版本号

## 反模式示例

❌ 为一个简单折扣计算创建 `DiscountStrategy` 接口 + 三个实现类  
✅ 一个带 switch 的函数，30 行搞定

❌ 修复空邮箱 bug 时顺手把整个 `UserService` 重命名为 `UserDomainService`  
✅ 只改那一行空值判断

❌ "我假设你想要分页，所以加了 PageRequest 参数"  
✅ "这个接口需要分页吗？还是先返回全量？"

## 硬约束（违反即 blocker）

前四条是"**倾向**"，下面五条是"**底线**"——违反必须修复，不可绕过。

### 5. 不加兼容层

> 起因：AAF 未发布 v1.0，无外部用户依赖旧 API。早期开发中 AI 多次自行添加 fallback 导致双路径并存，增加理解成本和 bug 面积。借鉴 multica CLAUDE.md 同类规则。

除非用户显式要求向后兼容，否则禁止添加：

- fallback 分支 / try-catch 吞异常走老路径
- legacy adapter / dual-write 双写逻辑 / 临时 shim
- "先这样 TODO 以后再说"的占位实现

API/flow 正在替换且产品未正式发布时，**直接删除旧路径**，不保留"两套都能跑"的中间态。

### 6. 不做任务外重构

> 起因：AI agent 倾向于"顺手"重构相邻代码，导致 diff 膨胀、审查困难、引入非预期回归。AAF-023 期间多次出现 5+ 文件改动实为 1 文件任务。借鉴 multica "Surgical Changes" 原则。

只改任务要求的代码。发现相邻代码有坏味道或死代码：

- 记录到 `dev-log.md` 或 `docs/prd/improvements.md`
- 不顺手改（会污染 diff、扩大审查面、掩盖真实改动）

批量修改 **≥5 个文件**或跨模块改动前必须向协调者申请。

### 7. 完工前必跑 check

> 起因：AAF-023 早期 AI 多次汇报"已完成"但实际编译失败/单测红，协调者需反复退回，浪费大量上下文。强制内循环后问题消失。

任务完成并向协调者汇报前，必须在本地执行：

```bash
pnpm check:affected
```

失败不能提交，**自己循环修复直到全绿**。失败状态下直接汇报视为未完工，协调者有权直接驳回。

**文档影响检查**（check 全绿后、汇报前）：

- 检查 diff 中是否有重命名/删除的文件、类名、命令、配置项
- 若 `docs/` 中引用了这些名称 → 同步更新文档或在 dev-log 中标记"需更新文档：{路径}"
- 快速方法：`grep -r "旧类名/旧路径" docs/` 确认无残留引用

> `check` 由 developer 负责（编译 / 单测 / lint / typecheck），`acceptance` 由 tester 负责。详见 [过程规范 3.3.1](../../../docs/reference/team/process-standard.md#331-ai-自验证循环)。

### 8. 测试命名区分 developer 与 tester 产出

> 起因：Surefire/Failsafe 按文件名后缀分流执行。命名混用导致验收测试被 `pnpm check` 误执行（需外部依赖而失败），或单测被跳过。

- Java：developer 单测 `*Test.java`（Surefire 执行）；tester 验收/集成 `*IT.java` 或 `*AcceptanceTest.java`（Failsafe 执行）
- TS：developer 单测 `*.test.ts(x)` / `*.spec.ts(x)`（Vitest）；tester 验收 `*.accept.test.ts(x)` 或 Playwright E2E

两种测试**不可混放在同一文件**，命名决定由哪个 target 执行。

### 9. 不静默降低置信度

> 起因：AI agent 在不确定时倾向于猜测实现并加 `@Disabled` / `it.skip` 绕过失败测试，表面全绿实则掩盖问题。AAF-023 中出现过跳过测试后上线才发现 bug 的情况。

置信度跌破阈值（< 0.7）时：

- 暂停执行，输出状态快照 + 建议选项，转人工
- **禁止**靠猜测生成代码再通过重试"碰运气通过"
- **禁止**降低测试期望或给 test 加 `@Disabled` / `it.skip` 来绕过失败

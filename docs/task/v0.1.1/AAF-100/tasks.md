---
level: Practice
layer: Product
purpose: 拆分 AAF-100 Studio 首页四行改版的后端、前端、验证与审查任务
status: draft
version: 1.0.0
date: 2026-08-10
author: AaronZZH & Kiro
tags:
  - AAF-100
  - Studio
  - 首页
  - 技术任务
related:
  - ./requirement.md
  - ./design.md
gains:
  - 能按依赖顺序交付首页四行改版
  - 能追踪每条验收标准的实现与验证责任
---

# AAF-100 Studio 首页四行改版任务

## 任务约束

- 需求真理源：[requirement.md](./requirement.md)。
- 技术真理源：[design.md](./design.md#studio-首页四行改版)。
- 风险等级：🔴 高；按 developer → architect review → tester → qa 流转。
- 不重构 `HomeChatLauncher`，不改造五个既有创作页面，不实现数字人生成能力。
- developer 完工前必须执行 `pnpm check:affected`；tester 启动前须确认 check 全绿。

## 技术任务

### #10001 蓝图封面迁移与后端映射

- **状态**：[x] 实现完成（验证按用户要求未执行）
- **负责人**：developer-service
- **依赖**：无
- **范围**：
  - 直接更新未部署的 Flyway `v7__aigc_schema.sql` 基线，新增 `cover_url VARCHAR(1000)` nullable，不新增迁移版本。
  - 同步更新 `v102__aigc_entity_def.sql` 蓝图字段配置。
  - 将项目类型、蓝图、渠道规格、领域扩展、兼容包和执行绑定统一为 `TenantScope.GLOBAL` + `@OrgIgnore`。
  - 全局定义写操作仅授予 `admin`/`super_admin`，组织角色保留读取和使用权限。
  - 贯通 `AigcProjectBlueprint` Entity、CreateDTO、UpdateDTO、VO、MapStruct 与 Service 映射。
  - 保持发布后不可修改规则，覆盖创建、读取、更新、清空与长度校验单测。
  - 验证 published + projectTypeCode 查询及物化服务端防篡改边界。
- **完成标准**：AIGC 基线定义、Java 契约与 EntityDef 一致；正式部署前再执行完整测试与基线重建验证。

### #10002 蓝图 Tabs、卡片与封面降级

- **状态**：[x] 实现完成（验证按用户要求未执行）
- **负责人**：developer-webui
- **依赖**：#10001
- **范围**：
  - 为前端 `AigcProjectBlueprint` 增加 `coverUrl?: string | null`。
  - 实现项目类型 Tabs、published 蓝图 Query、空态/失败态与蓝图卡。
  - 实现图片 `onError` 后按项目类型渐变回退，禁止破图图标。
  - 测试类型过滤、稳定渐变、null/空白/非法地址与加载失败。
- **完成标准**：蓝图行独立加载，当前 Tab 只显示本类型 published 蓝图；相关 `*.test.tsx` 全绿。

### #10003 蓝图建项 Dialog

- **状态**：[x] 实现完成（验证按用户要求未执行）
- **负责人**：developer-webui + developer-service
- **依赖**：#10001、#10002
- **范围**：
  - 展示只读项目类型、蓝图和生产模式；实现名称、IP、渠道与高级设置补充说明。
  - 复用 `useMaterializeAigcProject`，按设计映射 brand/profile 与 channel version IDs。
  - 实现名称 trim 校验、失败保值、切换蓝图重置、同步单航班锁与成功直达项目详情。
  - 补齐蓝图下线、无权 IP/渠道和类型/模式篡改的服务端回归测试。
- **完成标准**：无 IP/渠道可创建；失败不跳转；连点只发一个请求；成功进入 `/studio/projects/{id}`。

### #10004 首页严格四区与快速创作

- **状态**：[x] 实现完成（验证按用户要求未执行）
- **负责人**：developer-webui
- **依赖**：#10002、#10003
- **范围**：
  - 首页按统计卡、最近项目、蓝图、快速创作顺序渲染且仅渲染四个顶层业务区块。
  - 移除首页 `HomeRecentAssets` 渲染，不删除或迁移其组件。
  - 快速入口直达 image、video、copy、voice、music 既有路由。
  - 数字人指向 `/studio/create?mode=digital-human`，仅展示不可提交的未开放占位。
  - 明确不修改 `HomeChatLauncher`。
- **完成标准**：桌面/窄视口 DOM 均严格四区，无“最近生成”；六个入口路由与设计一致。

### #10005 Developer 自验证与回归

- **状态**：[ ] 待开始
- **负责人**：developer-service + developer-webui
- **依赖**：#10001、#10002、#10003、#10004
- **范围**：
  - 运行后端、前端目标测试，补足新增 public 行为的单元/组件覆盖。
  - 验证四区独立 loading/empty/error、权限过滤与 TanStack Query/本地 UI 状态边界。
  - 执行 `pnpm check:affected` 并更新 `dev-log.md`。
- **完成标准**：`pnpm check:affected` 全绿，无跳过测试、无任务外重构。

### #10006 架构与代码审查

- **状态**：[ ] 待开始
- **负责人**：architect
- **依赖**：#10005
- **范围**：
  - 对照 requirement/design 审查数据库、接口、状态边界、权限、防重复与回滚。
  - 核对首页四区对称性、图片成功/失败路径、Dialog 打开/关闭/失败/成功路径。
  - 确认未重构 `HomeChatLauncher`、未引入平行蓝图类型或服务端数据 Zustand 副本。
  - 输出 `review.md`，明确 blocker/major/minor 计数。
- **完成标准**：blocker 为 0；所有 major 修复后重新执行 #10005。

### #10007 AC 验收与回滚演练

- **状态**：[ ] 待开始
- **负责人**：tester
- **依赖**：#10006
- **范围**：
  - 按 requirement.md 的 Gherkin AC 建立覆盖矩阵并执行验收/集成测试。
  - 验证 AIGC 基线可重建、蓝图发布过滤、服务端防篡改、跨用户隔离、封面断链回退与重复点击。
  - 验证五个既有路由未被替代，数字人占位不产生任务。
  - 演练应用先回退、数据库后删列的顺序，记录结果到 `test-report.md`。
- **完成标准**：`pnpm acceptance:affected` 全绿；每条 AC 有结果和证据，无未关闭 P0/P1。

### #10008 过程审计与质量门控

- **状态**：[ ] 待开始
- **负责人**：qa
- **依赖**：#10006、#10007
- **范围**：检查 requirement、design、tasks、dev-log、review、test-report 的完整性和流转记录，汇总问题分级。
- **完成标准**：blocker = 0 且 major ≤ 2，输出过程审计结论。

## 依赖关系

```text
#10001
  ↓
#10002
  ↓
#10003
  ↓
#10004
  ↓
#10005 → #10006 → #10007
                    ↘
             #10006 + #10007 → #10008
```

## 交付检查

- [ ] 未部署 AIGC 基线直接新增 nullable `cover_url`，且未创建新迁移版本。
- [ ] Java 与 TypeScript `coverUrl` 契约一致。
- [ ] 首页严格四区，无“最近生成”。
- [ ] 蓝图 Dialog 创建链路及异常路径通过。
- [ ] 五个既有入口与数字人占位路由正确。
- [ ] `HomeChatLauncher` 未重构。
- [ ] `pnpm check:affected` 与 `pnpm acceptance:affected` 全绿。
- [ ] `review.md`、`test-report.md`、过程审计产出齐全。

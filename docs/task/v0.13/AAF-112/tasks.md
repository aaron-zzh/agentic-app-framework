---
level: Practice
layer: Model
purpose: AAF-112 Content Studio 项目先行完整闭环技术任务与依赖
status: active
version: 2.0.0
date: 2026-09-05
author: AaronZZH & Kiro
---

# AAF-112 技术任务

需求：[requirement.md](requirement.md)  
产品设计：[Content Studio 产品设计](../../../design/apps/content-studio/content-studio-design.md)  
技术设计：[Content Studio 技术设计](../../../design/apps/content-studio/content-studio-tech.md)  
交互设计：[ui-design.md](ui-design.md)

> AAF-112 不再维护任务级技术设计。所有实现直接对照两份长期设计；本文件只记录任务、依赖、状态与完成证据。

## 已完成增量

- [x] **#11201** 冻结长期设计依据并完成项目闭环差距审计 — product / architect / designer
- [x] **#11202** 扩展对象媒体 Action 参数、视频绑定与上传图片 MediaVersion 物化 — developer-service
- [x] **#11203** 实现参考图来源菜单、项目素材网格与图谱采用版选择 — developer-webui
- [x] **#11204** 实现项目页唯一对象生成弹窗并接入结构/图谱视图 — developer-webui

## 配置与项目合同

- [x] **#11207** 一次性收敛动态槽位与配置契约 — developer-service
  - 配置解析增加 budgetTier、qualityTier、slotOverrides、SlotTemplate、ActionSpec、DeliverableSetSpec 与 ProcessPolicy。
  - 按设计顺序解析数量和硬约束，固化输入、解析结果与幂等摘要；不保留弱 schema 双路径。
  - ProjectObject 一次性切换为 stableKey、blueprintTemplateKey、instanceNo、contractRole 最终命名与唯一约束。

- [x] **#11208** 重写项目物化、对象合同命令与工作台恢复 — developer-service / developer-webui
  - 物化动态对象实例；服务端分配永不复用的 instanceNo。
  - 实现 append、update-contract、remove 及历史引用/活动占用删除规则。
  - 创建页接入渠道、预算、质量和槽位覆盖；默认结构视图，按项目恢复焦点、折叠、图层与 viewport。

## 执行可靠性与活动事件

- [x] **#11209** 实现 ExecutionSubmission 与 Project reservation 强幂等 saga — developer-service
  - 新增 durable Submission、requestHash、reservation/targets 及 PREPARED/BOUND/RELEASED CAS。
  - root Run 以 submission 唯一创建并保存 targetGraphRevision、frozenProjectObjectIds 与 effectiveInput。
  - bind 前禁止派发、供应商调用和扣费；实现崩溃恢复扫描与终态幂等释放。

- [x] **#11210** 实现 ACTIVITY/ORCHESTRATION Run、Task 幂等与包级 fan-out — developer-service
  - 单对象 root 使用 ACTIVITY；包级使用 ORCHESTRATION + 内部 submitChild 动态 fan-out。
  - 固化 root/parent/workflowNode/retry-of；支持取消传播、聚合终态和失败对象局部重试。
  - Task 幂等键落库并贯穿供应商调用，禁止重复任务、候选和扣费。

- [x] **#11211** 将 Task SSE 一次性演进为持久 Activity SSE — developer-service / developer-webui
  - 直接替换为 `/api/aigc/events/stream`，持久化事件游标并支持断线重放/补查，不保留旧端点 fallback。
  - 事件携带 project、execution、task、media 及可空 conversation 关联。
  - 前端单例连接按项目失效 graph、summary、versions、runs、media refs、Work/Publication 与费用查询。

## 候选、采用与版本体验

- [x] **#11212** 实现候选可视比较、采用 CAS 与条件首次采用 — developer-service / developer-webui
  - 支持文本、结构、图片与视频 candidate-vs-candidate / candidate-vs-adopted 比较。
  - adopt 携带 expectedAdoptedVersionId、confirmedReplacement 和非空替换原因；reject 记录原因。
  - candidate、采用、替换分别形成修订与审计事件；按快照实现 AUTO_ADOPT_IF_EMPTY CAS。
  - 并发冲突刷新权威状态，失败/重试/事件重放不得移动采用指针。

## 内容包、审核与交付

- [x] **#11213** 实现 DeliverableSet evaluate 与 manifest freeze — developer-service / developer-webui
  - 规范化 REQUIRED + 显式 OPTIONAL included scope，拒绝 EXCLUDED、跨项目、重复或无采用版对象。
  - 活动 reservation 与校验失败形成 blocker；evidenceHash 覆盖 graphRevision、采用版、合同角色与证据。
  - freeze 以同一 optional scope、revision、hash 和 projectVersion 同事务重算，创建不可变 package manifest。

- [x] **#11214** 实现固定 manifest 的审核通过、退回与 stale — developer-service / developer-webui
  - Review 精确引用 DeliverableSet manifest；审核工作区展示槽位、版本、校验与证据。
  - 支持 approve/return、意见、权限与乐观锁；退回恢复可编辑。
  - 图谱、合同角色、采用版或证据变化使旧 Review stale；重新送审创建新 manifest 与 Review。

- [x] **#11215** 重写 Work、Publication 与快照化完成策略 — developer-service / developer-webui
  - Work 只收录 approved、non-stale 的精确 manifest，并保证同 manifest 幂等。
  - Publication 只引用 Work 与项目快照渠道；实现正式状态推进、取消、失败重试和错误证据，禁止普通用户伪造成功。
  - CompletionEvidence 按 NONE、OPTIONAL、AT_LEAST_ONE_SUCCESS、ALL_SELECTED_CHANNELS 判断，并向 UI 返回具体 blocker。

## 生命周期、权限与完整工作台

- [x] **#11216** 收敛正交生命周期、权限与归档只读 — developer-service / developer-webui
  - 项目、对象/采用、Run/Task、Review、Work/Publication 分别持有权威状态；UI 四阶段只做派生投影。
  - 所有特殊命令补齐 authority、owner、org、workspace、project、乐观锁和幂等对称校验。
  - completed 后仅允许归档；任一非归档状态可作为放弃归档但不得冒充完成；归档后所有写入口拒绝且历史继续可读。

- [x] **#11217** 串联无 Chatter 的端到端项目工作台 — developer-webui
  - 创建、动态交付清单、对象动作、RunTree、候选比较、内容包评估、审核、Work、Publication、完成与归档均有可视入口。
  - 全流程只读取 TanStack Query 权威缓存；Zustand 仅保存按项目隔离的 UI 状态。
  - AAF-113 继续受项目先行门阻塞，不在本任务接入项目对话。

## 手工验证与质量门

- [x] **#11218** 补齐开发测试源码与验收用例清单 — developer-service / developer-webui / tester
  - 覆盖动态解析、并发幂等、崩溃窗口、reservation fencing、Task 去重、事件重放、采用 CAS、manifest hash、审核 stale、发布策略和权限隔离。
  - 覆盖双视图同弹窗、无 Task fallback、断线恢复、候选比较及完整无 Chatter UI 路径。

- [ ] **#11205** 手工执行编译、开发测试、迁移验证与 `pnpm check:affected` — human
- [ ] **#11206** 手工完成架构审查、验收测试、过程审计与质量门 — human

## 依赖

```text
已完成增量 #11201～#11204

#11207 → #11208
#11209 → #11210 → #11211
#11208 + #11210 → #11212
#11208 + #11210 + #11212 → #11213 → #11214 → #11215
#11211 + #11215 → #11216 → #11217 → #11218 → #11205 → #11206
```

## 完成定义

- 用户完全不打开 Chatter，可通过 UI 跑通创建、生成、候选采用、manifest、审核、Work、Publication 策略、完成和归档。
- 动态项目合同、执行冻结、候选采用、审核交付和生命周期均符合 Content Studio 技术设计，不存在弱旧契约、fallback、双写或平行状态。
- 同键同参重放稳定，同键异参冲突；崩溃恢复、事件重放、失败取消与重试不重复供应商调用、候选或扣费。
- Work 与 Publication 固定审核通过的同一 manifest；完成策略来自不可变项目快照。
- 权限和工作区隔离覆盖全部命令；归档只读且历史、媒体和文件引用完整。
- 人工执行的编译、测试、迁移、`check:affected`、架构审查、验收和审计全部通过后，AAF-112 才能完成并解除 AAF-113 阶段门。



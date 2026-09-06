---
level: Practice
layer: Product
purpose: AAF-112 Content Studio 项目先行完整闭环需求规格
status: active
version: 2.0.0
date: 2026-09-05
author: AaronZZH & Kiro
---

# AAF-112 Content Studio 项目先行完整闭环

## 设计依据

- [Content Studio 产品设计](../../../design/apps/content-studio/content-studio-design.md)定义产品语义、项目生命周期与交付闭环。
- [Content Studio 技术设计](../../../design/apps/content-studio/content-studio-tech.md)是接口、聚合、数据、事件与质量门的唯一工程真理源。
- 本文只定义 v0.13 可验收范围，不重复维护技术设计；发生冲突时先修正长期设计，再调整任务和代码。

## Epic

**目标**：让内容创作者完全不打开 Chatter，也能通过 Content Studio UI 完成“创建项目 → 物化动态交付结构 → 对象生成 → 候选比较与采用 → 内容包冻结 → 审核 → Work 收录 → 按策略处理 Publication → 完成 → 归档”，且每一步都由独立、可追溯的领域事实承载。

**范围**：AAF-112 覆盖 v0.13 的项目创建与工作台、对象创作与素材版本、审核与交付三个项目先行里程碑。AAF-113 必须等待本故事全部验收通过后，才能接入只读项目对话和项目写 Tool。

## 业务不变量

### 项目与交付合同

- 项目配置按长期技术设计解析动态槽位、数量覆盖、合同角色和发布策略，并把输入与解析结果固化到不可变配置快照。
- ProjectGraph 是对象、父子关系、合同角色、采用指针和修订号的唯一项目真理源；结构视图、图谱视图、故事板及其他专业视图只做投影。
- 对象具有稳定模板实例身份；实例号不复用，已有版本、审核、关系或执行占用历史的对象不得物理删除。
- 项目工作台默认打开结构视图，焦点、视图、折叠、图层与 viewport 按项目隔离并可恢复。

### 执行与候选

- 所有项目动作统一经过 `ActionCommand → ExecutionSubmission → Project reservation → ExecutionRun`；媒体动作再关联 AigcTask，禁止公开 Task API 承载项目对象语义。
- 同一项目幂等键和相同规范化请求只能产生一份 Submission、reservation、root Run 和业务副作用；同键异参必须冲突。
- Run 在 reservation 绑定前不得派发、调用供应商或扣费；崩溃恢复必须从 durable Submission 继续，不依赖内存事件。
- 单对象与包级执行均冻结 graph revision、目标对象和有效输入；运行中图谱变化不改写已冻结拓扑。
- 每次成功执行先创建不可变 candidate；失败、取消、重试、迟到事件和事件重放不得移动已有采用指针。

### 素材与版本

- 本地上传先物化为 MediaVersion；项目素材只引用对象当前采用版本关联的 MediaVersion；预览 URL、Asset ID 和 fileId 均不能冒充 MediaVersion 业务身份。
- 参考素材保留来源、顺序和数量限制，相同 MediaVersion 不重复加入。
- 候选必须可视比较；首次采用和替换采用是显式领域命令。替换已有采用版必须携带预期采用指针、确认标记和原因，并以 CAS 拒绝并发覆盖。
- `AUTO_ADOPT_IF_EMPTY` 仅能从不可变项目快照读取，并只在采用指针仍为空时独立、可审计地执行。

### 审核与交付

- 送审前必须按当前 graph revision 评估 DeliverableSet：全部 REQUIRED 与本次显式选择的 OPTIONAL 形成规范化 included scope；EXCLUDED 不参与。
- evaluate 返回证据与 evidenceHash；freeze 必须在同一项目事务中重算并创建不可变 package manifest ObjectVersion。
- Review 固定引用精确 manifest；内容、合同角色、采用版、校验证据或活动 reservation 变化后旧 Review 必须标记 stale。
- 审核支持通过和退回；退回后项目恢复可编辑，重新送审必须冻结新 manifest，不修改历史版本。
- Work 只能收录审核通过且未 stale 的同一 manifest；Publication 只能从该 Work 和项目固定渠道/发布策略创建。
- 完成条件按项目快照中的发布策略判断；执行成功、候选、采用、审核、Work、Publication、完成与归档始终是不同事实。
- 归档后禁止所有写操作，但 ProjectGraph、版本、执行、审核、Work、Publication 与媒体引用继续可读且不触发物理文件删除。

### 事件、权限与前端状态

- 用户级 Activity SSE 是变化通知投影，必须具有持久游标和断线补查；数据库仍是真理源。
- 项目终态事件必须使 project、graph、summary、versions、runs、media refs、Work/Publication 与费用查询重新读取权威状态。
- 所有特殊命令对 owner、org、workspace、project 和 authority 做对称校验；内部 reserve/bind/release/submitChild 端口不得暴露为普通用户 REST。
- TanStack Query 管理服务端状态；Zustand 只保存按项目隔离的 UI 状态，不复制 ProjectGraph、ExecutionRun、ObjectVersion、Review、Work 或 Publication。

## 验收标准

```gherkin
Scenario: 项目创建后形成可恢复的动态工作台
Given 用户选择已发布项目类型、蓝图、渠道、预算质量档和槽位数量
When 用户创建项目
Then 系统按设计顺序解析并固化配置快照
And 物化具有稳定实例身份与合同角色的 ProjectGraph
And 默认打开结构视图
And 刷新或切换项目后只恢复当前项目的焦点与视图状态
```

```gherkin
Scenario: 双视图通过同一入口生成对象候选
Given 项目处于可创作阶段且对象存在适用动作
When 用户从结构视图或图谱视图打开生成弹窗并提交
Then 两个入口使用同一 projectId、objectId 和服务端 actionKey
And 生成只走统一 Submission、reservation、ExecutionRun 与可选 Task 链
And 终态先登记 ObjectVersion candidate
And 不存在项目 Task API fallback 或自动覆盖已有采用版
```

```gherkin
Scenario: 参考素材保持 MediaVersion 权威身份
Given 用户选择本地上传、项目采用素材或允许的资产媒体
When 参考素材加入项目动作
Then 本地文件先物化为 MediaVersion
And 项目图谱只允许选择当前采用对象版本关联的图片 MediaVersion
And 提交保持 MediaVersion 顺序、去重与数量限制
And 预览 URL 不作为业务身份或模型输入来源
```

```gherkin
Scenario: 重复与崩溃恢复不重复执行
Given 相同项目动作被并发提交或进程在 reserve、bind、派发任一窗口崩溃
When 系统按同一幂等键恢复
Then 相同请求返回同一 Submission、reservation 和 root Run
And 同键异参返回冲突
And 未绑定 Run 不调用供应商或扣费
And 已成功对象、Task、候选和费用不重复产生
```

```gherkin
Scenario: 候选比较与采用使用 CAS
Given 同一对象存在多个候选且可能已有采用版
When 用户比较候选并采用其中一版
Then UI 展示正文或媒体预览、来源 Run、成本和时间
And 替换采用版要求确认及非空原因
And 请求携带预期采用版本
And 并发冲突不覆盖最新采用指针
And 历史候选、采用版和替换理由可追溯
```

```gherkin
Scenario: 送审冻结不可变内容包
Given DeliverableSet 的 REQUIRED 对象均有采用版且校验通过
And 本次 OPTIONAL 选择合法
And 没有相交的活动 execution reservation
When 用户评估并确认送审
Then 服务端以相同 optional scope、graph revision、evidenceHash 和 project version 重算
And 创建并采用不可变 package manifest
And Review 固定引用该 manifest
```

```gherkin
Scenario: 审核退回、失效与重新送审
Given 当前 manifest 正在审核或已经通过
When 审核人退回或项目内容发生影响审核证据的变化
Then 退回项目恢复可编辑并保留审核意见
And 内容变化使旧 Review 标记 stale
And stale Review 不能收录 Work
And 重新送审创建新 manifest 与 Review，不修改历史记录
```

```gherkin
Scenario: Work、Publication 与项目完成使用同一交付快照
Given Review 已通过且未 stale
When 用户收录 Work、创建或恢复 Publication 并完成项目
Then Work 精确引用审核通过的 manifest
And Publication 只引用该 Work 与项目固定渠道
And 失败发布可重试、活动发布可取消且不伪造成功
And 完成门按项目快照中的发布策略判断
And 完成后可归档且全部历史事实继续可读
```

```gherkin
Scenario: Activity SSE 断线后恢复权威项目状态
Given 项目执行在弹窗关闭、页面切换或连接中断后到达终态
When 客户端携带持久游标重连或执行补查
Then 事件不重复产生业务副作用
And 项目、图谱、摘要、版本、执行、素材、Work、Publication 与费用重新查询
And 用户无需手工刷新即可看到最终权威状态
```

```gherkin
Scenario: 权限和归档只读对称生效
Given 用户无对应动作权限、跨工作区访问或项目已经归档
When 用户从任意 UI 或 API 尝试生成、采用、审核、收录、发布或生命周期写操作
Then 服务端拒绝请求
And 前端不展示或禁用入口
And 不存在可绕过权限、乐观锁、幂等或生命周期门的第二路径
```

## 非目标

- 不实现 AAF-113 的项目对话、内容创作者 Role/Skill、项目写 Tool 或富媒体 ToolUI。
- 不新增 Assistant、Agent、Team、第二个 AG-UI 端点或平行项目生成链。
- 不实现专业 NLE、自动真实渠道投放、实时多人协同、开放模板/工作流市场、灵感广场或人才生态。
- NarrativeSeries、短剧/漫剧、房地产行业扩展等按长期设计的后续阶段实施，不阻塞本轮基础图文项目闭环。

## 相关文档

- [Content Studio 产品设计](../../../design/apps/content-studio/content-studio-design.md)
- [Content Studio 技术设计](../../../design/apps/content-studio/content-studio-tech.md)
- [交互设计](ui-design.md)
- [技术任务](tasks.md)


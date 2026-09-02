---
level: Practice
layer: Product
purpose: 定义知识管理中的协作责任、统一授权、敏感数据保护、保留删除与安全验收合同
status: draft
version: 0.1.0
date: 2026-09-02
author: AaronZZH
tags:
  - 知识管理
  - 协作
  - 权限
  - 隐私
  - 高校
scope:
  includes:
    - 人、Agent、外部系统的主体与责任边界
    - 版本化协作、评论、任务、评审、发布和恢复
    - Organization、Space、KnowledgeItem 的动作级授权与共享
    - 学生数据分级、目的限制、模型边界、保留与删除
    - 审计指标、负向测试和真实数据试点 P0 门禁
  excludes:
    - 具体策略 DSL、数据表和接口签名
    - CRDT 实时多人共编
    - 教务、学工、OA 等主系统的身份与交易能力
    - 法律结论和全地域合规认证
dependencies:
  - ../prd-outline-plan.md
  - ../scenarios-and-product-scope.md
  - ../object-model-and-information-architecture.md
  - ../knowledge-lifecycle-and-multimodal.md
  - ../../../design/apps/service/workspace-isolation.md
  - ../../../design/framework/security/access-control-tech-design.md
gains:
  - 能判断教师、学生、辅导员、访客和 Agent 对同一知识动作是否有权执行
  - 能区分阅读、分享、借阅、导出、发布和销毁等独立权限
  - 能在撤权、模型调用、后台任务和删除场景中执行失败关闭
  - 能用负向矩阵和审计指标判断真实学生数据是否可以放行
changelog:
  - 2026-09-02 v0.1.0 | 定义主体责任、首期协作、动作级授权、隐私分级、保留删除和 P0 验收合同
---

# 知识管理产品：协作、权限与隐私治理

> 本文把“谁能在什么条件下对哪项知识做什么”定义为统一产品合同。Space 沿用现有 Workspace，KnowledgeItem 是规划中的唯一用户可见知识根；KnowledgeBase、索引、缓存、活动流和审计日志均不得成为第二套 ACL 或内容所有者。

## 主体、空间与责任

### 当前基础与缺口

| 能力 | 成熟度 | 当前边界 |
|------|--------|----------|
| 权威业务 PDP | 已实现基础 | `AuthorizationService` 统一组合 L1-L4；只有明确 ALLOW 才可执行，DENY、INDETERMINATE 和未恢复 CHALLENGE 均失败关闭 |
| CRUD PEP 与范围下推 | 已实现基础 | `CrudEnforcementService` 与 `BaseCrudService` 将 tenant、记录、个人和字段约束下推查询；自定义 Service 绕过标准 CRUD 时仍需显式接入 |
| Organization 与 Workspace | 部分实现 | 组织是外层边界，Workspace 是组织内协作隔离；成员需显式加入，`workspace_id=NULL` 表示组织共享；成员管理页面仍待完善 |
| Agent 委托与后台 owner context | 部分实现 | Operator 与委托收窄、`PermissionExecutionService` owner context 已有实现；知识动作的逐次重授权与完整审计字段仍需闭环 |
| 文档协作 | 部分实现 | Markdown CRUD、发布、树、搜索和 SSE 变更通知已有基础；SSE 只是通知，不是 CRDT 或实时共同编辑 |
| 审计 | 部分实现 | 已有实体变更审计、授权审计基础和 hash 链；operator/subject/owner、purpose、PDP 版本、分享/借阅/导出与知识 run 等字段仍需补齐 |
| KnowledgeItem 与 ResourceRef | 规划目标 | 产品对象合同已确定，当前代码尚未形成统一业务根，不得把设计目标描述成现有能力 |
| 旧 Segment/Problem 入口 | P0 缺口 | Controller 仅校验登录，Service 未统一验证路径 `kbId`、`documentId`、segment、problem 与当前授权范围；真实学生数据试点前必须关闭 |

### 主体链

每次知识动作保存三类不可混用的身份：

| 身份 | 含义 | 示例 |
|------|------|------|
| `operator` | 实际发起或执行请求的主体 | 教师本人、Agent、连接器服务账号、后台 worker |
| `subject` | 权利与责任被用于授权的自然人或受控系统主体 | Agent 的委托教师、后台任务固定的上传者或责任人 |
| `owner` | 资源业务所有者或责任边界 | KnowledgeItem 责任人、Space owner、来源系统责任部门 |

人工用户直接操作时 `operator=subject`；Agent 执行时 `operator=Agent`、`subject=委托人`，实际权限为委托人权限与 Agent scope 的交集；后台任务必须固定原始 subject、Organization、Space、目的和资源范围。owner 身份不能自动取得正文权限，管理员身份也不能替代业务授权。

授权快照、策略版本和关系版本只用于审计与重现请求事实，不是可复用的 ALLOW。后台任务在每次读取正文、调用模型或解析器、生成导出、发布、分享和写回前，必须向唯一 PDP 重新授权。

### 空间与业务范围

```text
Organization（成员、全局策略、计费和最外层隔离）
  → Space / Workspace（显式成员协作和数据隔离）
    → KnowledgeItem（责任人、数据等级、ACL 引用和生命周期）
      → ResourceRef（内容修订，不独立持有 ACL）
```

学院、部门、课程、班级、项目组和学生事项可以成为 Space、关系事实或授权属性，选择取决于是否需要独立成员与隔离：

- 学院/部门长期协作通常使用 Space；组织共享制度可以使用 `workspace_id=NULL`，但仍按 KnowledgeItem 动作鉴权。
- 课程需要独立成员和长期内容时使用 Space；班级与项目组默认作为可版本化成员关系，只有需要独立治理时才拆 Space。
- 学生本人、学籍事项、资助批次、心理咨询等是记录与字段范围，不为每名学生创建 Space。
- 企业项目沿用同一模型，不新增企业专属 ACL；客户、合同和项目关系作为授权事实。

外部教务、学工或人事系统是成员/班级/事项事实的来源权威。AAF 保存带来源版本和截至时间的授权事实快照；前端选择、活动流和搜索索引不能成为成员真理。

### 角色与责任矩阵

| 产品角色 | Space 管理 | 内容协作 | 治理责任 | 默认限制 |
|----------|------------|----------|----------|----------|
| Organization 管理员 | 管组织成员、全局策略和安全配置 | 不因角色自动读取敏感正文 | 指定安全与数据责任人 | 不能绕过 KnowledgeItem 业务范围 |
| Space Owner | 管 Space、成员和默认策略 | 可按具体授权参与内容 | 对成员、模板和异常负责 | 不能把 C3/C4 内容公开或跨组织转移 |
| Space 管理员 | 受委托管理成员、集合和任务 | 按 KnowledgeItem 权限操作 | 处理成员变更与到期授权 | 不自动取得所有正文或导出权 |
| 知识责任人 | 不能默认管理 Space 成员 | 管指定 KnowledgeItem 修订 | 对来源、有效期、评审、发布和退休负责 | 不可自审高风险发布 |
| 贡献者/编辑者 | 无成员管理权 | 创建草稿、编辑、评论、提交评审 | 对贡献内容和来源负责 | 不能发布、分享、导出或销毁，除非另行授权 |
| 评审者/发布者 | 无成员管理权 | 评审固定修订并发布 | 对结论、适用范围和有效期负责 | 不能评审自己提交的高风险修订 |
| 查看者/借阅者 | 无管理权 | 只读授权范围 | 遵守目的和期限 | 引用和下载不授予分享、导出权 |
| 访客/外部协作者 | 无默认成员权 | 只访问具名资源和期限 | 对外部使用负责 | 禁止枚举 Space、继续分享和访问 C3/C4 |
| Agent | 无独立业务所有权 | 代表 subject 执行被允许动作 | 输出必须可追溯 | 权限不得超过 subject，不能自行批准 challenge |

角色只是权限输入，不直接等于最终决定。对 read、comment、edit、review、publish、share、borrow、export、delete、destroy 和 manage_acl 分别授权。

## 在线协同与工作流

### 首期协作边界

首期提供版本化编辑、评论、任务、评审、发布、活动流和版本恢复，不承诺字符级实时共编：

- 编辑打开时记录修订 ID 与 `expectedVersion`；保存冲突时拒绝覆盖并提供比较、复制为新草稿或重新应用变更。
- SSE 只通知“对象已变化”，客户端收到后重新鉴权并拉取；通知不得携带未授权正文，也不能当作锁或共同编辑状态。
- 评论锚定 KnowledgeItem、具体修订和 locator；删除或解决评论不改写正文与安全审计。
- 任务可指向评论、评审、纠错、复核或到期事项；任务完成不自动改变 Governance 状态。
- 活动流是用户可见投影；权威状态由 PostgreSQL 中固定修订、治理迁移与审批事实决定，活动记录不能重建授权或发布状态。
- 恢复旧版本必须创建新草稿修订；不得让历史修订原地重新成为当前发布版本。

CRDT、在线光标、离线合并和实时共同编辑在增强阶段以独立一致性、安全和审计门槛评估。

### 评审与发布

```text
DRAFT（编辑）
  → IN_REVIEW（固定候选修订、来源、数据等级和适用范围）
    → CHANGES_REQUESTED（回到新草稿）
    → APPROVED（满足评审规则）
      → PUBLISHED（发布者再次鉴权后原子切换）
```

评审申请固定修订 hash、来源清单、差异摘要、数据等级、有效区间、评审规则版本和提交人。高风险规则可以要求职责分离、双人评审或 L4 challenge；批准只绑定同一请求摘要和策略快照，内容、范围或等级变化后原批准失效。

发布前重新检查：候选修订未变化、来源可用、Processing=READY、评审未过期、发布者有 publish 权限、当前策略仍允许。任一项不确定均拒绝发布。

### 评论、任务与提及

- 评论默认继承目标 KnowledgeItem 的 read 范围；评论作者不能通过 `@提及` 给无权用户授予访问。
- 提及通知只发送对象类型、任务类型和安全标题；接收者打开时重新鉴权，无权时统一显示资源不存在。
- 评论附件是 ResourceRef 关联资源，使用与目标相同或更严格的数据等级和保留策略。
- 任务受让人必须同时有任务读取权和目标资源所需动作；任务系统不能代替知识 PDP。
- 从讨论晋升知识时按第三篇创建 DRAFT 修订并保留 `promotedFrom`，不能直接发布。

### 并发、恢复与活动一致性

保存、评审、发布、撤回和恢复都使用期望版本或条件更新，避免最后写入者静默覆盖。写事务提交后活动、通知、索引和统计通过 outbox 异步投影；投影失败不回滚已提交真理，但界面显示截至版本和同步状态。

撤权、来源撤销和隐私阻断不等待活动或搜索投影。活动流中历史标题若已无权查看，立即显示通用占位，不保留可推断信息。

## 授权、共享与隔离

### 唯一 PDP 与动作合同

`AuthorizationService` 是知识资源唯一业务 PDP。Spring `isAuthenticated()` 和固定角色只做前置认证/粗门禁；Controller、Service、搜索、Agent、后台 worker、导出和外部 Adapter 均通过 PEP 组装同一类请求：

```text
operator + subject + Organization + Space
+ resourceType + objectId + action
+ purpose + dataClass + businessScope + current/proposed facts
+ policySnapshotVersion + requestDigest
```

只有显式 ALLOW 执行。DENY、INDETERMINATE、依赖不可用和未恢复 CHALLENGE 均不读取或外发正文。决定优先级沿用平台安全顺序；显式 deny 与更严格数据等级不能被角色、父目录、KnowledgeBase 管理权或分享关系覆盖。

| 动作 | 能力 | 不隐含的能力 |
|------|------|--------------|
| `read` | 查看授权字段和附件 | 不含下载全集、分享、借阅、导出 |
| `comment` | 评论和回复 | 不含编辑正文或提及扩权 |
| `edit` | 修改草稿并创建修订 | 不含评审、发布或改 ACL |
| `review` | 对固定修订给结论 | 不含发布或修改候选内容 |
| `publish` | 切换已批准修订 | 不含外部分享或导出 |
| `share` | 创建具名访问关系 | 不含复制正文、转授权或导出 |
| `borrow` | 创建有目的和期限的临时 read | 不含继续分享、批量下载或导出 |
| `export` | 生成受控离线副本 | 不因 read/share/borrow 自动获得 |
| `delete` | 逻辑移除工作项 | 不含绕过保留策略的 destroy |
| `destroy` | 经审批执行不可恢复销毁 | 不含清除 legal hold 或审计 tombstone |
| `manage_acl` | 管理允许范围内的关系 | 不含授予自己没有的动作 |

同一 action、同一事实在浏览、API、搜索、问答、图、引用和 Agent 工具中必须得到一致可见性；不同 action 可以有不同结果，例如 read 允许而 export 拒绝。

### 数据分级与默认动作

| 等级 | 示例 | 默认边界 |
|------|------|----------|
| `C0 公开` | 已批准公开制度、公开课程资料 | 发布后可匿名读；编辑、发布和导出源数据仍需授权 |
| `C1 内部` | 内部通知、一般教案、非敏感项目资料 | Organization/Space 成员按职责读；禁止匿名链接 |
| `C2 受限` | 未公开制度、成绩汇总、合作项目材料 | 具名主体、业务范围和目的；分享与导出单独审批 |
| `C3 敏感个人` | 学籍、资助、奖惩、就业和联系方式 | 最小字段、本人/事项/班级范围、期限与目的；禁止公开链接和继续分享 |
| `C4 高度敏感` | 心理、健康、身份凭证、严重处分原始材料 | 普通 share、borrow、export、外部模型和外部解析绝对 DENY |

C4 的 break-glass 不是分享或导出例外，只能使用独立 `minimal_read` 动作：限定已登记紧急目的、最小字段、双人批准、短期限、每次重新授权和实时告警；不得下载、导出、训练模型、继续分享或修改来源。超级管理员身份本身不产生 `minimal_read` 权。

### 分享、借阅、外部协作与导出

分享关系绑定主体、KnowledgeItem、动作、字段范围、目的、起止时间、授予者和授权版本。授予者只能授出自己被允许管理的动作，且不能突破数据等级、Organization 和 Space 上限。

- **具名分享**：适用于 C0-C2；C3 仅在明确业务依据和审批后使用；C4 禁止。
- **链接分享**：仅 C0 和策略允许的 C1，可要求登录、域名、次数和到期；令牌只保存 hash，可随时吊销。
- **借阅**：临时 read，必须有目的和到期时间；到期后下一次请求拒绝。
- **外部协作者**：使用具名外部主体、最小资源集合和固定期限；默认不能枚举 Space、搜索其他知识或继续分享。
- **导出**：独立高风险动作，固定筛选范围、字段、用途、审批、格式、份数和到期；输出带水印与 manifest，下载令牌一次性或短期有效。

引用、收藏、评论、任务和 KnowledgeBase 加入索引都不授予 read。跨 Space 复制与跨 Organization 导出/导入遵循第二篇的新身份与重新授权规则。

### 撤权与后台任务

授权关系或权威策略事务提交成功即为撤权生效点。生效后的下一次浏览、API、搜索、问答、图、引用、导出下载和 Agent 工具请求必须拒绝；已打开流在 5 秒内终止并在发送下一片正文前重授权。

- 搜索、PgVector、Neo4j、缓存和会话只保留候选，响应前回 PostgreSQL/PDP 复核。
- 后台任务的授权快照只作审计；每次正文读取、外部调用、生成结果落库和发布前重新授权。
- 撤权后尚未执行的模型/解析任务取消；无法取消的外部调用结果进入隔离区并按处理合同删除，不得发布。
- 异步投影和临时副本清理有独立 SLO，但清理期间不能形成访问窗口。
- 成员退出 Organization/Space 时同步撤销相关关系；重新加入不恢复旧分享、借阅和 challenge。

## 隐私、保留与删除

### 目的限制与最小化

采集和使用学生或个人数据前必须登记：业务目的、合法/制度依据、数据主体范围、字段、来源、责任人、处理者、保存地点、保留期和允许动作。PDP 使用 purpose 和 scope 作为事实；“可能有用”不是合法目的。

- 默认只读取任务必需字段；列表、搜索和提示词不得携带隐藏字段。
- 分析、统计、课程复用和模型评测优先使用合成、匿名或不可逆聚合数据。
- 脱敏副本是新修订或派生 KnowledgeItem，记录规则、方法和来源；不得把掩码展示误认为源数据已匿名。
- 学生可以在授权入口查看与本人相关的可披露数据、来源、使用目的和纠错渠道；纠错不改写源系统主数据，而是形成申请和来源同步。
- 处理目的结束、授权依据失效或范围缩小时立即停止新使用，不等待保留期结束。

### 模型、解析器与连接器边界

任何模型、OCR、ASR、VLM、解析 Adapter 和连接器都视为数据处理者，不因“智能能力”获得扩大范围：

1. PDP 在读取正文和创建外发任务前明确 ALLOW；
2. 仅发送批准字段、页/片段和必要元数据，不发送无关目录与成员信息；
3. 固定供应商、模型/解析器版本、区域、网络出口、保留时间和是否用于训练；
4. 禁止供应商训练、人工查看或二次使用，除非另有明确批准且不涉及禁止等级；
5. 结果进入隔离区，完成恶意内容、来源和 locator 校验后才可写入 AAF；
6. 临时对象按任务清单删除并获得可验证回执。

C3 只允许进入 Organization 已批准的处理 profile；C4 禁止外部模型和外部解析，必要处理只能使用经安全审核的本地隔离能力，且仍按 `minimal_read` 或明确业务动作授权。

### 保留、删除和数据主体请求

保留策略按数据类别、来源、业务状态和 legal hold 决定。普通用户删除请求先停止普通访问，再判断源系统权威、保留义务、争议和引用关系：

- 来源系统仍是主数据权威时，AAF 不伪造源记录删除；撤销本地快照并向源系统责任人发起请求。
- 没有保留依据且审批通过时，按第三篇执行逻辑删除、destroy 和物理 GC。
- legal hold、审计调查和未结事项阻止 destroy，但不能让内容继续通过普通知识入口可见。
- 导出、下载、外部处理临时副本和备份分别记录删除状态；不能用在线库删除代表全部清除。
- 最小 tombstone 只保留不可逆标识摘要、依据、审批和执行结果，不保留可恢复正文或敏感标题。

数据主体请求绑定申请人、范围、身份验证、响应期限和处理证据。更正、限制处理、获取副本和删除是不同动作，不能用一次“同意”自动放行全部操作。

### 导出与外部残留

导出前生成字段预览和风险摘要；审批绑定 requestDigest，任何筛选、字段、格式、用途或接收者变化都使批准失效。导出文件使用独立密钥或一次性下载链接，记录下载结果并在期限后使在线副本不可访问。

外部残留账本枚举临时对象、供应商任务、回调结果、日志/追踪、导出文件和备份批次。清理失败进入重试和告警；在获得删除回执前状态不能显示“全部删除”。日志、提示词和错误信息禁止记录不必要正文。

## 审计、风险与验收

### 审计事件合同

审计事件 manifest 版本化登记必须记录的动作，独立于实际日志数量。至少覆盖授权决定、challenge、成员/关系变更、read 高敏访问、comment/edit/review/publish、share/borrow/export、模型/解析、撤权、删除、hold 和 break-glass。

每条事件最少包含：eventId、时间、operator/subject/owner、Organization/Space、resource/action、purpose、数据等级、PDP effect/reason、policy/snapshot version、requestDigest、关系/授权版本、客户端/任务/run、结果和关联事件。拒绝事件不记录正文、提示词、敏感标题和完整导出字段。

当前 AuditLog 与 AuthorizationAudit 仅是实现基础；上述主体链和知识治理字段完成前不得宣称全链路审计已闭环。ActivityRecord 只供用户查看，不替代安全审计。

### 指标与放行口径

每次测试冻结入口 manifest、动作集合、策略版本、主体/范围组合和数据 fixture。合同评审可以在未运行生产指标时通过；真实数据放行必须达到最小样本和全部安全门禁，不能以“证据不足”放行。

| 指标 | 计算与目标 | 样本和数据源 | 失败处置 |
|------|------------|--------------|----------|
| `G1` 越权阻断率 | 固定负向矩阵中未返回正文、标题、摘要、数量、关系且无外部调用/副作用的用例÷全部用例=`100%` | 每个入口×数据等级×主体范围至少20例，总计≥800；网关、PDP、外发和数据库探针 | 任一失败为P0，暂停真实数据并调查影响 |
| `G2` PDP 覆盖率 | manifest 中受保护入口产生权威 PDP 决策的入口数÷全部入口=`100%` | 冻结路由/工具/worker manifest 与测试 trace | 未覆盖入口关闭或接入后重测 |
| `G3` 撤权正确率 | 撤权提交后第一笔请求拒绝且未产生新正文字节、外部调用和写副作用的场景÷全部场景=`100%` | 每渠道≥50次，含旧会话、流、worker 和下载令牌 | 任一失败为P0；立即隔离相关范围 |
| `G4` 动作一致性 | 同一 action/事实在各渠道可见性一致的断言数÷全部断言=`100%`；同时 read 与 export 收窄用例全部通过 | read、export、share、publish 各≥100组 | 修复分歧渠道，不以更宽结果为准 |
| `G5` 审计完整率 | manifest 预期事件中字段完整且 hash/关联可验证的事件数÷预期事件数=`100%` | 每个事件类型≥30，总计≥600；业务事件计数器作分母 | 缺失高敏/销毁事件为P0，其余阻断放行 |
| `G6` 最小字段符合率 | 实际返回/外发字段均属于批准字段集的调用数÷抽检调用数=`100%` | C2-C4 各≥200次；响应与 egress 记录 | 停用 profile，评估泄漏并修复 |
| `G7` 临时残留清理率 | 截止时限前清理成功并有回执的临时对象数÷到期对象数≥`99.9%`，最长残留≤24小时 | ≥1000个对象，按 Adapter/导出/日志分类 | 超24小时为P0；冻结对应处理者 |
| `G8` 高风险职责分离 | 要求双人审批的发布、导出、destroy、minimal_read 中提交人与最终批准人不同=`100%` | 每类≥50次 | 违规动作回滚/失效并审计 |
| `G9` 备份恢复与撤权复核 | 恢复演练后全部恢复对象重新鉴权、过期/撤权内容不可见=`100%` | 每季度一次，≥500项混合 fixture | 恢复不放行，修复后重演练 |
| `G10` 治理闭环时效 | P0 立即阻断；P1 任务24小时内受理且7日内关闭比例≥`95%` | 反馈、异常检测和审计工单 | 超时升级责任人与安全负责人 |

### P0 风险登记

| 风险 | 关闭条件 | Owner | 审核证据 |
|------|----------|-------|----------|
| Segment/Problem 仅登录校验 | 路径参数、实体归属、动作和字段全部接入 PDP，或旧入口关闭；负向矩阵全绿 | 知识服务负责人 | 路由 manifest、自动化测试、代码审查 |
| 自定义 Repository 绕过 PEP | 所有知识读取/写入/导出/图/worker 入口纳入 G2 | 安全架构负责人 | 调用链清单与 trace |
| 真实学生数据范围不清 | 源系统、班级/本人/事项关系、目的、字段、期限和责任人均版本化 | 学工数据责任人 | 数据处理登记与人类审批 |
| 外部处理残留 | 批准 profile、网络、区域、训练禁用、删除回执和退出演练通过 | 平台安全负责人 | 合同、egress、残留账本 |
| 恢复后旧权限复活 | 备份恢复执行策略/关系版本复核且 G9 通过 | 运维负责人 | 恢复报告与负向结果 |

### 验收标准

**AC-G01：编辑冲突不得静默覆盖**

```gherkin
Feature: 版本化协作
Scenario: 两名编辑者基于同一版本保存时后提交者收到冲突
  Given 编辑者甲和编辑者乙均获准编辑 KnowledgeItem 100 的 DRAFT 修订版本 7
  And 编辑者甲已使用 expectedVersion 7 保存并生成版本 8
  When 编辑者乙使用 expectedVersion 7 提交不同正文
  Then 系统应拒绝覆盖版本 8
  And 系统应返回版本冲突和差异入口
  And 编辑者乙只能重新应用变更或复制为新草稿
```

**AC-G02：评审批准绑定固定修订**

```gherkin
Feature: 评审发布职责分离
Scenario: 候选修订变化后旧批准不能用于发布
  Given 评审者已批准修订 20 的 hash "h20" 和 requestDigest "r20"
  And 编辑者随后创建修订 21
  When 发布者尝试用批准 "r20" 发布修订 21
  Then PDP 应拒绝发布
  And 系统应要求修订 21 重新评审
  And 修订 20 的批准记录应保持不可变
```

**AC-G03：学生本人只能读取批准字段**

```gherkin
Feature: 学生本人范围
Scenario: 学生读取本人的资助事项时返回最小字段
  Given 学生 300 对资助事项 900 具有 action read 和 purpose self_service
  And 批准字段为 status、submittedAt、requiredActions
  When 学生 300 请求资助事项 900
  Then 响应只应包含批准字段
  And 响应不得包含内部评语、其他学生标识或审核员私人备注
  And 审计应记录本人范围、目的和字段策略版本
```

**AC-G04：篡改学生标识不能跨本人范围**

```gherkin
Feature: 学生记录隔离
Scenario: 学生篡改目标标识读取他人事项时失败关闭
  Given 学生 300 只被授权读取本人记录
  And 资助事项 901 属于学生 301
  When 学生 300 请求资助事项 901
  Then 系统应返回统一的资源不存在响应
  And 响应不得包含标题、状态、字段数量或关系线索
  And 系统不得把事项 901 发送给模型或搜索投影
```

**AC-G05：项目组隔离覆盖所有读取渠道**

```gherkin
Feature: 课程项目隔离
Scenario: 未授权项目组成员请求目标成果时所有 read 渠道一致拒绝
  Given 学生 310 属于项目组 A 且不属于项目组 B
  And KnowledgeItem 110 属于项目组 B
  When 学生 310 分别通过详情、搜索、问答、图和引用执行 action read
  Then 每个渠道均应返回相同的不可见结果
  And 每个渠道均不得返回命中数量、摘要或关系
  And 每个渠道均应产生关联同一测试请求的拒绝审计
```

**AC-G06：读取允许不代表导出允许**

```gherkin
Feature: 动作级授权
Scenario: 辅导员可读受限名单但无 export grant 时导出被拒绝
  Given 辅导员 320 对 KnowledgeItem 120 具有 action read
  And 辅导员 320 对 KnowledgeItem 120 没有 action export
  When 辅导员 320 请求导出 KnowledgeItem 120
  Then PDP 应拒绝导出或创建未批准的导出申请
  And 系统不得生成导出文件或下载令牌
  And 原有 read 权限应保持不变
```

**AC-G07：敏感链接分享被阻断**

```gherkin
Feature: 数据分级分享
Scenario: C3 知识不能创建匿名链接
  Given KnowledgeItem 130 的数据等级为 C3
  And Space 管理员具有 manage_acl 但没有降低数据等级的授权
  When Space 管理员请求创建匿名链接分享
  Then PDP 应返回 DENY
  And 系统不得创建分享令牌、公开 URL 或缓存副本
  And 审计应记录数据等级和拒绝策略版本
```

**AC-G08：撤权后的第一笔请求立即拒绝**

```gherkin
Feature: 撤权即时生效
Scenario: 旧会话在撤权事务提交后不能继续读取
  Given 辅导员 330 的旧会话已打开 KnowledgeItem 140
  And 撤权前存在搜索、向量和图候选
  When 关系授权撤销事务在 2026-09-02T10:00:00+08:00 提交成功
  And 旧会话在 2026-09-02T10:00:01+08:00 发起第一笔 read 请求
  Then PDP 应拒绝该请求
  And 响应不得返回新的正文片段、标题、数量或关系
  And 投影尚未清理不得改变拒绝结果
```

**AC-G09：Agent 权限按委托人和 scope 收窄**

```gherkin
Feature: Agent 委托授权
Scenario: Agent 不能执行委托人未获准的分享动作
  Given Agent 400 的 subject 是教师 340
  And 教师 340 对 KnowledgeItem 150 只有 read 权限
  And Agent scope 包含 read 和 share
  When Agent 400 请求 action share
  Then PDP 应返回 DENY
  And Agent 不得创建分享关系或向外发送正文
  And 审计应同时记录 operator 400、subject 340 和资源 owner
```

**AC-G10：后台任务每次外发前重新授权**

```gherkin
Feature: 后台任务重授权
Scenario: 排队期间撤权的解析任务不能外发正文
  Given worker 任务 500 在授权有效时排队并保存了审计快照
  And 任务执行前 subject 的处理授权已撤销
  When worker 任务 500 准备调用外部 OCR
  Then worker 应向唯一 PDP 重新授权
  And PDP 应拒绝外部调用
  And 外发调用计数应为 0
  And 审计快照不得作为 ALLOW 使用
```

**AC-G11：C4 普通动作绝对拒绝**

```gherkin
Feature: 高度敏感数据保护
Scenario: 管理员不能用 challenge 导出 C4 心理材料
  Given KnowledgeItem 160 的数据等级为 C4
  And Organization 管理员请求 action export
  When PDP 评估该请求
  Then PDP 应返回 DENY 而不是 CHALLENGE
  And 系统不得创建导出、借阅、分享或外部模型任务
  And 管理员角色不得自动获得正文 read
```

**AC-G12：break-glass 只提供最小读取**

```gherkin
Feature: 紧急最小读取
Scenario: 双人批准后的 minimal_read 仍禁止下载和继续分享
  Given C4 事项 170 存在已登记紧急目的和 15 分钟 minimal_read 申请
  And 两名不同批准人已批准同一 requestDigest
  When 指定处置人执行 minimal_read
  Then 系统只应返回申请中列出的最小字段
  And 系统不得返回下载链接、导出文件或分享动作
  And 访问应触发实时安全告警并在 15 分钟后失效
```

**AC-G13：借阅到期后拒绝访问**

```gherkin
Feature: 临时借阅期限
Scenario: 测试时钟推进到借阅到期后第一笔请求被拒绝
  Given 外部专家 350 的借阅授权在 2026-09-30T23:59:59+08:00 到期
  When 测试时钟推进到 2026-10-01T00:00:00+08:00
  And 外部专家 350 请求 action read
  Then PDP 应拒绝请求
  And 旧下载令牌和会话不得继续返回正文
  And 系统应记录授权到期而不是资源删除
```

**AC-G14：legal hold 阻止销毁但不恢复普通访问**

```gherkin
Feature: 保留与访问分离
Scenario: 被撤权且处于 legal hold 的材料不可读也不可销毁
  Given KnowledgeItem 180 的普通访问已撤销
  And 修订 181 存在有效 legal hold
  When 普通用户请求 read 且责任人请求 destroy
  Then read 请求应被拒绝
  And destroy 请求应被拒绝
  And 材料只应在独立受控调查动作中重新授权
```

**AC-G15：旧 Segment 与 Problem 入口必须失败关闭**

```gherkin
Feature: 遗留入口 P0 收口
Scenario: 跨 Space 操作者不能通过保留的旧入口修改目标资源
  Given Segment update、Segment delete、Problem create 和 Problem linkSegment 已登记为保留入口并接入 PDP
  And 操作者 360 只属于 Space 10
  And kbId 200、documentId 201、segmentId 202 和 problemId 203 均属于 Space 11
  When 操作者 360 分别调用四个登记入口
  Then 四个入口均应返回统一的 PDP 拒绝结果
  And 响应不得返回目标标题、正文、数量或关系
  And 数据库不得发生 segment、problem 或关联变更
```

第四篇合同评审通过不代表真实数据放行。只有 `G1-G10` 达到最小样本和目标、`AC-G01` 至 `AC-G15` 全部通过、P0 风险关闭并由人类安全审核签字，才可进入真实学生数据试点。
---
level: Practice
layer: Product
purpose: 定义知识管理产品的统一知识身份、极简信息架构、最小本体与跨视图一致性合同
status: draft
version: 0.1.0
date: 2026-09-02
author: AaronZZH
tags:
  - 知识管理
  - 信息架构
  - 对象模型
  - 语义本体
scope:
  includes:
    - 单一知识入口与跨端信息架构
    - 产品概念、身份和现有 AAF 模型边界
    - 最小语义本体与治理规则
    - 投影视图、通用动作与跨边界语义
    - 一致性指标和产品验收标准
  excludes:
    - 数据表、接口与类结构
    - 多模态解析和知识生命周期完整状态机
    - 权限策略的字段级实现
    - 外部产品集成技术方案
dependencies:
  - ./prd-outline-plan.md
  - ./scenarios-and-product-scope.md
  - ../../design/apps/service/workspace-isolation.md
  - ../../design/framework/engine/data-knowledge/nexus-knowledge.md
gains:
  - 能判断文件、文档、词条、问答和项目成果是否属于同一个知识身份
  - 能在不增加平行菜单和真理源的前提下扩展知识视图
  - 能验证本体、助手上下文和跨空间操作是否符合产品边界
changelog:
  - 2026-09-02 v0.1.0 | 收口单一入口、统一身份、最小本体、六类视图和一致性验收
---

# 知识管理产品：统一对象与极简信息架构

> 本文定义产品用户看到的知识世界。底层可以存在文档、文件、索引、事实和图投影，但同一知识对用户必须只有一个身份、一套权限、一条生命周期和一个可追溯来源。

## 极简信息架构

### 产品边界

知识管理在 Studio 中只有一个顶级入口：`/studio/knowledge`。现有“知识库、文档、收藏”不再作为并列子产品：文档是知识内容形态，收藏是个人筛选，KnowledgeBase 是高级入库与检索策略，均不得要求普通用户在多个页面间判断“内容到底存在哪里”。

组织层级固定为：

```text
Organization（组织、成员和计费外层边界）
  → Space（现有 Workspace 的产品名称，协作与数据隔离边界）
    → KnowledgeItem（唯一用户可见知识身份）
```

该层级表达归属，不是本体类型继承。`workspace_id=NULL` 的组织级共享知识在有权用户的各 Space 中显示“组织共享”标识，不虚构一个新的公共 Space。

产品“Space”与现有 `Workspace` 是同一概念、同一 ID、同一成员关系；代码和技术文档继续使用 Workspace。PhysicsSpaceTime 的 2D/3D 计算空间与本产品 Space 无关，不得复用同一产品术语或权限语义。

### 单一工作面

桌面端知识入口由四个稳定区域组成，不对应四个独立页面：

| 区域 | 默认内容 | 约束 |
|------|----------|------|
| 顶部 | 当前 Space、统一搜索/问答、创建 | 搜索默认只覆盖当前 Space 与组织共享知识；扩大范围必须显式选择 |
| 左侧 | 集合、目录、标签、保存筛选 | 只组织和筛选 KnowledgeItem；删除集合不得删除知识正文 |
| 中央 | 当前知识集合及视图切换 | 浏览器、Wiki、词典、地图、问答、社区共享同一选择和筛选上下文 |
| 右侧 | 详情、活动、助手 | 显示同一 KnowledgeItem 的元数据、版本、讨论和可见上下文，不复制正文 |

用户进入后默认看到最近访问、待处理、常用集合和有权读取的更新；没有权限的项目不显示标题、数量、摘要或关系占位。

### 统一搜索与助手上下文

搜索和助手是跨视图交互层，不是第七种内容视图。

- 默认上下文为当前 Organization、当前 Space、组织共享知识、当前集合/筛选和用户选中的 KnowledgeItem。
- 上下文以可见标签展示，用户可以移除、替换或显式增加其他有权 Space；助手不得静默扩大范围。
- “所有有权 Space”是一次明确搜索范围选择，每条结果必须显示所属 Space；切换 Space 后清除旧选择和筛选，下一轮助手不得继续使用旧 Space 内容。
- 历史回答可保留，但必须保留当时的范围与来源标签；历史引用在再次打开时重新鉴权。
- 无法确定授权、业务时间或来源有效性时失败关闭，不以跨空间搜索兜底。

### 统一创建与渐进披露

普通用户的“创建”只暴露五种意图：上传文件、新建在线文档、录入知识/问答、采集网页、创建集合。当前 Space 自动继承，系统再按内容和角色渐进询问最少必要的类型、权限、责任人和有效期。

| 用户 | 默认可见能力 | 按需展开 |
|------|--------------|----------|
| 阅读者 | 浏览、搜索、问答、收藏、订阅和有权分享 | 来源、历史版本和关系详情 |
| 贡献者 | 阅读能力加创建、编辑、评论和提交评审 | 内容形态、元数据和复用方式 |
| 责任人 | 贡献能力加评审、发布、归档、有效期和冲突复核 | 本体建议、迁移预览和质量指标 |
| Space 管理者 | 成员、模板、默认权限和治理队列 | KnowledgeBase、索引与投影状态等高级设置 |

普通用户界面不出现“向量、分块、代际、checkpoint、domain/range”等术语；需要人工决策时翻译为自然语言提示。

### 跨端一致性

移动端不创建另一套 IA：顶部保留 Space、搜索和创建；集合/筛选、详情/活动、助手分别以抽屉或全屏层呈现；视图名称、KnowledgeItem ID、权限、状态和动作语义与桌面一致。首期地图在移动端可只读或降级为关系列表，但不得返回不同的知识或权限结果。

## 核心对象模型

### 概念登记

| 产品概念 | 身份与职责 | 当前 AAF 映射 | 明确不是什么 |
|----------|------------|--------------|--------------|
| Organization | 最外层组织、成员、策略和计费边界 | 现有 `Organization` | 不是知识目录或本体类型 |
| Space | 组织内显式成员协作和数据隔离边界 | 现有 `Workspace` 的产品名称 | 不新增 `KnowledgeSpace`，不等于 2D/3D 空间引擎 |
| KnowledgeItem | 文件、文档、制度、FAQ、词条、项目成果等唯一用户可见知识身份 | 新的产品业务根，绑定现有内容与 NexusKB 来源 | 不是文件副本、索引行、图节点或搜索结果 |
| ResourceRef | KnowledgeItem 指向正文/载体及指定修订的类型化引用 | 指向 `Document` 或文件内容版本，最终落点可引用 `StoredFile` | 不是独立用户对象，不拥有 ACL、生命周期或公开 URL |
| SemanticObject | 概念、实体、类型和关系的稳定语义身份 | 对接 NexusKB entity/fact/evidence 与后续本体定义 | 不直接等于 Neo4j 节点，不绕过 KnowledgeItem 展示身份 |
| ActivityRecord | 评论、评审、发布、分享、借阅、晋升等用户可见业务活动 | 复用平台活动/审计能力形成只追加记录 | 不是知识正文；安全审计仍由平台审计真理负责 |

“知识资产”是 KnowledgeItem 经过责任确认、治理和发布后承担的业务角色，不新增 `KnowledgeAsset` 第二根身份。此前规划中的 `ContentItem` 假设不进入首版：内容通过 ResourceRef 绑定现有 owner；未来如需持久化内容聚合，也只能是一对一技术实现，不能产生第二个用户可见 ID。

### KnowledgeItem 身份合同

KnowledgeItem 持有稳定业务 ID、Space、知识类型、标题、责任人、权限引用、生命周期、有效期、当前 ResourceRef 和来源关系。正文、二进制和抽取事实分别由各自真理 owner 保存。

- 同一上传或创建动作只产生一个 KnowledgeItem；Document、StoredFile、KnowledgeDocument、chunk、entity、fact 和图节点不得成为平行业务身份。
- 上传文件创建一个 KnowledgeItem 和文件内容版本；把该文件转换为在线文档时保留 KnowledgeItem ID，原文件作为来源，新 Document 成为新内容修订。
- 在线文档创建一个 KnowledgeItem，并通过 ResourceRef 指向 Document；附件默认只是资源，只有用户明确“作为独立知识收录”才创建新 KnowledgeItem。
- 路径、标题、URL、内容 hash 和文件名都不是身份；相同 hash 可以物理去重，但不能自动合并知识、权限或来源。
- 每个 Document 或主内容版本首期只归一个 KnowledgeItem 所有；跨场景复用通过引用或派生，不共享正文所有权。

### KnowledgeBase 边界

KnowledgeBase 仅管理入库/检索策略、索引覆盖集合、处理配置、代际和投影状态。它不拥有 KnowledgeItem，不是普通用户的知识目录，也不决定业务读取权限。

- KnowledgeItem 与 KnowledgeBase 是“被某策略处理/覆盖”的关系，不是父子归属；同一知识身份不得因进入多个检索策略而复制。
- KnowledgeBase 的管理权限只允许配置或维护索引，不能授予超出 Organization、Space 和 KnowledgeItem ACL 的正文读取权限。
- 搜索结果、助手引用、收藏、讨论、分享和导出统一返回 KnowledgeItem/ResourceRef；不得把 KnowledgeBase、KnowledgeDocument 或 admission 记录作为业务对象。
- 现有 KnowledgeDocument 是来源入库记录；迁移后必须回链一个 KnowledgeItem，无法回链的遗留数据不得直接进入普通用户视图。

### 语义与活动边界

SemanticObject 提供机器可连接的语义身份，KnowledgeItem 提供用户可治理的知识身份。一个 KnowledgeItem 可解释一个术语或实体，也可关联多个语义对象；用户访问的规范链接仍以 KnowledgeItem 为入口，图查询中的内部实体/事实 ID 不替代它。

ActivityRecord 只追加业务动作。讨论被采纳前仍是活动；晋升后创建或更新 KnowledgeItem，并保留 `promotedFrom` 来源。活动删除、折叠或视图过滤不能改写知识正文和安全审计。

### 身份变化规则

| 操作 | KnowledgeItem ID | 内容/版本 | 来源与权限 |
|------|------------------|-----------|------------|
| 同一 Space 内目录重排、改标签或切视图 | 不变 | 内容不变则不新增修订 | ACL 不变，记录必要活动 |
| 复制 | 新 ID | 创建新首版本 | 记录 `derivedFrom`，在目标范围重新鉴权 |
| 引用 | 目标 ID 不变 | `PINNED` 固定修订，`FLOATING` 跟随有权查看的当前修订 | 新增引用边；引用本身不授予权限 |
| 跨 Space 转移 | 新 ID | 在目标 Space 创建受控首版本 | 记录 `transferredFrom`；原项按用户选择保留、归档或撤销 |
| 跨 Organization 使用 | 全部业务 ID 重建 | 通过导出/导入形成新版本 | ACL、责任人和本体映射重新确认，禁止无损 move |

分享是显式权限动作，不等于引用；同一 Space 的集合移动不等于跨 Space 转移。底层 blob 可去重，但不得共享对外身份或隐式扩权。

## 最小语义本体

### 本体定位与作用域

本体定义“允许用什么类型和关系描述知识”，NexusKB 事实与证据定义“当前有哪些可追溯断言”，SemanticCalc 只提供抽取、相似度和建议计算。三者不得互相替代。

系统提供只读基础本体；Organization 可以发布组织本体；Space 默认继承并可在授权下增加局部类型和关系。继承只能从系统到组织再到 Space，局部定义不得静默改变上层稳定 ID 或全局语义。

### 类型、层级与关系合同

| 元素 | 最低可验证合同 |
|------|----------------|
| 类型 | 稳定 ID/代码、首选名称、说明、允许的内容形态、状态；类型代码发布后不可原位改义 |
| 层级 | 类型可有一个主要父类型并支持 broader/narrower；多维分类使用标签，不把 Organization→Space→KnowledgeItem 归属冒充类型继承 |
| 关系 | 稳定谓词 ID、来源类型 domain、目标类型或字面量 range、方向、逆关系、是否对称 |
| 基数 | 每个方向声明最小/最大基数，如 `0..1`、`1`、`0..*`、`1..*`；违反时不得发布为确认事实 |
| 实例 | SemanticObject 绑定一个已发布类型；未知类型进入待分类，不由模型自动扩展 schema |

首期不要求用户建复杂 OWL 规则、无界推理或任意图约束；模板提供高校常用类型和关系，责任人只确认自然语言建议。

### 术语、来源与置信度

术语包含 preferred label、alias、语言、作用域和 `EXACT/CLOSE` 匹配级别。同义词帮助检索和消歧，但不合并 KnowledgeItem 或 SemanticObject ID；同一作用域出现冲突首选词时进入复核。

机器生成的类型、关系和断言必须携带来源 ResourceRef、证据位置、生成方式、模型/规则版本、责任人或发布者。置信度采用 `0..1`，同时记录评估方法、评估者和时间；置信度只表达不确定性，不能替代权限、人工发布或业务有效状态。

### 双时态与事实有效性

事实或关系至少区分：

- `validFrom/validTo`：现实或业务中何时成立；
- `recordedAt/supersededAt`：平台何时正式采信以及停止采信。

两类区间均采用左闭右开。未知业务时间保持未知，不用创建或更新时间伪造；普通搜索默认只使用当前业务有效且当前采信的断言，历史回放必须显式选择业务时间和系统认知时间并重新鉴权。

### 版本迁移与冲突复核

本体状态为草稿、已发布、已弃用。兼容性增加可保持稳定 ID并发布新版本；改义、拆分、合并、domain/range 或基数收紧属于破坏性变更，必须创建迁移计划、影响预览、检查点和回滚依据。

- `ontologyVersion` 与 `migrationId` 标识每次发布和迁移；F1→F2→F1 仍产生新的版本/代际，不复活旧版本。
- 类型或关系拆分/合并必须提供旧 ID 到新 ID 的显式映射；无法自动映射的实例进入待复核。
- 发现同义词碰撞、domain/range 不符、基数冲突或相互矛盾事实时保留双方证据，状态进入 `PENDING/CONFIRMED/REJECTED/RESOLVED`。
- AI 可以提出分类、关系和冲突建议，但不得创建已发布类型、静默覆盖事实或自动完成破坏性迁移。

## 视图与通用动作

### 六类投影视图

| 视图 | 面向任务 | 真理来源与边界 |
|------|----------|----------------|
| 浏览器 | 目录、列表、卡片、时间线和批量整理 | 投影 KnowledgeItem 元数据与 ResourceRef 预览；集合不拥有正文 |
| Wiki | 按主题和链接连续阅读 | 导航和链接是视图结构；人工保存的新综合页面才成为新的 KnowledgeItem |
| 词典 | 查术语、别名、定义和适用范围 | 展示 SemanticObject 与对应 KnowledgeItem，不复制术语真理 |
| 地图 | 浏览实体、事实、来源和关联路径 | 使用可重建语义投影；任何事实回到 PostgreSQL 来源和证据复核 |
| 问答 | 搜索、带引用回答、FAQ 和反馈 | 动态回答不是已发布知识；确认 FAQ 仍需 KnowledgeItem 生命周期 |
| 社区 | 讨论、提问、经验分享和专家互动 | 讨论属于 ActivityRecord；已发布知识只被引用，不复制正文 |

收藏、订阅、最近访问和“我的待办”是个人保存筛选；搜索与助手横跨六类视图，不再形成独立内容仓库。

### 跨视图一致性

所有视图必须使用同一个 KnowledgeItem ID、规范链接、ACL owner、责任人、生命周期和当前有效版本。切换视图只改变投影，不复制内容；从任一视图修改后，其余视图按投影 SLO 收敛。

- 权限每次请求实时复核，撤权不等待搜索、图或缓存刷新。
- 核心元数据视图在真理提交后 P95 `≤5 秒`收敛；语义地图、词典关系和问答候选 P95 `≤60 秒`收敛。
- 超过 SLO 或 checkpoint 落后时显示“同步中”和数据截至版本；需要时回源读取，不把旧状态伪装为当前状态。
- 投影重建不得改变 KnowledgeItem ID、ACL、生命周期或反写真理源；完整内容重新入库仍遵守第一篇 `M5≤30 分钟`。

### 通用动作

| 动作组 | 普通动作 | 一致性要求 |
|--------|----------|------------|
| 采集 | 上传、新建、网页采集、从讨论晋升 | 只创建一个 KnowledgeItem；重复提交幂等 |
| 组织 | 放入集合、标签、类型、链接、同 Space 重排 | 位置不是身份，组织变化不复制正文 |
| 协作 | 评论、提问、引用、任务、提交评审 | ActivityRecord 追加，引用不扩权 |
| 使用 | 阅读、搜索、问答、收藏、订阅、分享、导出 | 所有渠道实时鉴权并保留来源 |
| 治理 | 评审、发布、有效期、归档、转移、删除/恢复 | 服从生命周期、保留策略和审批权限 |

动作是否可用由当前用户、Space、KnowledgeItem 状态和策略共同决定；隐藏按钮不能代替服务端拒绝。

### 助手与上下文动作

助手显示当前 Space、集合/筛选、选中知识和业务时间标签。用户可以“仅使用选中知识”“扩大到所有有权 Space”或移除某项；每次扩大范围都需要显式操作并在回答中按 Space 标识来源。

引用历史知识时重新校验 ACL 和有效性；失权内容从上下文中移除且不保留摘要。助手生成的摘要、页面或答案只有经有权用户确认后才能保存为草稿 KnowledgeItem，不能自动发布。

### 问答与社区晋升

- 来源本身已是 KnowledgeItem 时，采纳回答或讨论只形成新修订、角色或发布状态，根 ID 不变。
- 来源只是讨论 ActivityRecord 时，晋升创建且仅创建一个 KnowledgeItem，并保留 `promotedFrom`；原讨论继续作为活动存在。
- 晋升需要有权用户明确确认，重复点击和任务重试必须幂等；AI 只能建议。
- 社区和问答视图不得保存已发布知识的正文副本；删除讨论不删除其已审核晋升的知识，二者通过来源关系追溯。

### 保存视图与集合

手工集合、动态集合、Wiki 目录和个人收藏保存的是 KnowledgeItem 引用或筛选定义，不是内容容器。一个知识可同时出现于多个集合；删除集合只删除组织关系。跨 Space 的集合只能保存引用，打开时实时鉴权并显示来源 Space。

## 演进约束与验收

### 演进红线

- 不新增与 Workspace 并列的知识 Space，不把 Organization、Space 层级编码成本体继承。
- 不为文件、在线文档、KnowledgeDocument、搜索结果、Wiki 页面投影或图节点创建平行业务身份。
- 不让 KnowledgeBase、PgVector、Neo4j、SemanticCalc 或前端缓存成为正文、权限、本体和生命周期真理源。
- 不因新增视图复制正文、ACL 或状态；新视图必须只消费 KnowledgeItem、ResourceRef、SemanticObject 和 ActivityRecord 合同。
- 不把引用当分享、不把跨 Space 转移当原地 move、不把 AI 建议当已发布知识。

### 信息架构指标

| 指标 | 口径与目标 | 最小样本 |
|------|------------|----------|
| `IA1` 单一入口覆盖率 | 三类核心场景的知识任务均从一个知识入口开始，目标 100% | 第一篇 ≥120 个有效任务 |
| `IA2` 首次点击成功率 | 用户第一次操作进入正确 Space/搜索/创建/视图路径的任务数÷测试任务数，目标 ≥85% | ≥20 名用户、每人 5 个标准任务，覆盖教师/学工/课程团队 |
| `IA3` 常用路径复杂度 | 搜索或提问 ≤1 次主操作；上传/新建、切换视图、收藏、查看来源均 ≤3 次主操作 | 5 类标准任务各执行 ≥20 次 |
| `IA4` 跨视图身份一致率 | 六视图中 ID、状态、责任人、有效版本和授权一致的检查项÷全部检查项，目标 100% | ≥200 个 KnowledgeItem，覆盖全部视图和状态 |
| `IA5` 上下文越界率 | 未经显式选择而进入搜索/助手上下文的其他 Space 内容数÷上下文结果数，目标 0 | ≥300 次范围负向测试，与第一篇 M6 联合 |
| `IA6` 投影收敛 | 元数据 P95≤5 秒、语义视图 P95≤60 秒；超时提示率 100%，撤权实时阻断率 100% | ≥100 次元数据变更、≥100 次语义变更、≥300 次撤权后访问 |

样本不足标记“证据不足”；`IA4`、`IA5` 或撤权实时阻断任一失败即本阶段不通过。

### 验收标准

**AC-IA01：单一入口与渐进披露**

```gherkin
Feature: 极简知识入口
Scenario: 单一入口与渐进披露
Given 用户具备当前 Space 的知识读取权限
When 用户进入 Studio 的知识功能并完成搜索、上传、收藏、切换 Wiki 和查看来源任务
Then 所有任务应从同一个知识入口完成
And 普通路径不得要求用户先选择 KnowledgeBase 或理解向量、索引、本体版本
And 页面层级和主操作次数应满足 IA2 与 IA3
```

**AC-ID01：文件与在线文档统一身份**

```gherkin
Feature: 统一知识身份
Scenario: 文件转换为在线文档时保持单一身份
Given 用户在当前 Space 上传一个文件并形成 KnowledgeItem K1
When 用户把该文件转换为在线文档并继续编辑
Then 系统仍只展示 KnowledgeItem K1
And 原文件应作为 K1 的来源或历史内容修订保留
And Document、StoredFile、KnowledgeDocument 和图投影不得出现第二个用户可见知识身份
And 相同内容 hash 的另一次复制仍应获得新 ID并记录 derivedFrom
```

**AC-AUTH01：KnowledgeBase、引用与撤权不扩权**

```gherkin
Feature: 知识访问授权
Scenario: KnowledgeBase 和引用不扩展读取权限
Given KnowledgeItem K1 已被某 KnowledgeBase 建立索引并在另一个集合中被引用
And 当前用户随后失去 K1 的读取权限
When 用户通过搜索、问答、集合、Wiki、地图、词典、导出或引用访问 K1
Then 所有渠道应立即拒绝正文、标题、摘要、数量和关系线索
And KnowledgeBase 管理权和引用关系不得恢复读取权限
And 拒绝不得等待投影刷新
```

**AC-CTX01：助手跨 Space 必须显式**

```gherkin
Feature: 助手知识上下文
Scenario: 跨 Space 内容必须由用户显式加入
Given 助手当前上下文只有 Space A 和组织共享知识
And 用户对 Space B 也有读取权限
When 用户未显式选择 Space B 就发起提问
Then 检索和回答上下文不得包含 Space B
When 用户显式扩大到 Space B
Then 界面应显示可移除的 Space B 标签
And 每条来源应显示所属 Space并在使用前重新鉴权
```

**AC-XFER01：复制、引用与跨边界操作**

```gherkin
Feature: 知识跨边界操作
Scenario: 区分同 Space 重排、复制、引用和跨边界转移
Given KnowledgeItem K1 位于 Space A
When 用户在 Space A 内调整目录
Then K1 ID 保持不变且不新增内容修订
When 用户复制 K1
Then 系统创建新 ID和新首版本并记录 derivedFrom
When 用户引用 K1
Then 目标 ID保持不变且引用不授予权限
When 用户把 K1 转移到 Space B
Then 系统执行受控复制、创建目标新 ID、记录 transferredFrom并重新鉴权
And 原 K1 按用户确认保留、归档或撤销
And 跨 Organization 操作只能走导出/导入并重建业务 ID与 ACL
```

**AC-ONT01：本体验证、冲突和迁移**

```gherkin
Feature: 最小语义本体治理
Scenario: 约束违规、语义冲突和破坏性迁移必须受控
Given 已发布关系定义声明 domain、range、方向、基数和本体版本
When 新关系违反任一约束或有效期区间非法
Then 系统不得把它发布为确认事实
And 应保留来源并进入待复核
When AI 提出未知类型、未知关系、同义词碰撞或矛盾事实
Then AI 不得扩展已发布 schema或覆盖旧事实
And 有权责任人应看到双方证据、置信度和影响范围
When 破坏性本体变更被提交
Then 系统应先生成新版本、迁移映射、影响预览和回滚检查点
```

**AC-PROJ01：投影滞后不伪装为最新**

```gherkin
Feature: 投影视图一致性
Scenario: 投影滞后时显示版本并保持实时授权
Given KnowledgeItem K1 的真理版本已更新为 V2
And 某语义视图的 checkpoint 仍停留在 V1
When 用户打开该视图
Then 界面应显示同步中和数据截至 V1
And 能回源的核心元数据应显示 V2
And 投影应在 IA6 SLO 内追平或保持明确降级
And 重建投影不得改变 K1 ID、ACL或生命周期
And 若 K1 已撤权则即使 V1 投影仍存在也必须立即拒绝访问
```

**AC-PROMOTE01：问答和社区晋升幂等**

```gherkin
Feature: 问答与社区知识晋升
Scenario: 已有知识的讨论被采纳
Given 一条已发布 KnowledgeItem 的讨论被采纳
When 有权责任人确认晋升
Then 系统应在原 KnowledgeItem 上形成受控修订或状态变化且根 ID不变

Scenario: 讨论活动首次晋升为知识
Given 一条尚不是 KnowledgeItem 的讨论活动被采纳
When 有权责任人确认晋升并发生重复点击或任务重试
Then 系统应且仅应创建一个新 KnowledgeItem
And 新知识应记录 promotedFrom、责任人、来源和审核状态
And 未经确认的 AI 建议不得发布
```

### 迁移与完成门槛

现有 `/studio/knowledge/docs` 和 `/studio/knowledge/favorites` 应收口为单一入口中的视图/筛选；现有 KnowledgeBase 页面迁入 Space 管理者的高级设置。历史 Document、文件与 KnowledgeDocument 在普通用户可见前必须绑定唯一 KnowledgeItem；无法判定同一性的内容进入人工迁移队列，不按标题、路径或 hash 静默合并。

本篇通过要求：五个一级章节保持不变；全部 AC 通过；`IA4=100%`、`IA5=0`、撤权实时阻断率 100%；Space/Workspace、KnowledgeItem/ResourceRef、KnowledgeBase 与本体真理边界经产品和架构共同确认。涉及 KnowledgeBase 所有权关系、历史身份迁移和跨 Space 权限的实现属于高风险设计，必须在人类审核技术方案后开发。

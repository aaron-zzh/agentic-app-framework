---
level: Practice
layer: Product
purpose: 明确知识管理产品的自研主链、外部解析 Adapter 准入、基准评测、运行治理和退出决策
status: draft
version: 0.1.0
date: 2026-09-02
author: AaronZZH
tags:
  - 知识管理
  - 构建决策
  - Adapter
  - WeKnora
  - 供应链
scope:
  includes:
    - AAF 现有知识、文档、文件、权限和 AI 能力差距
    - 自研、整体集成、Fork 与 Adapter 方案比较
    - 外部解析候选的证据、安全、质量、性能和成本基准
    - 无状态 Adapter 合同、灰度、运维、升级和数据清理
    - 分阶段门槛、失败回退和完整退出演练
  excludes:
    - 具体供应商采购与价格承诺
    - Adapter Java 接口、部署清单和数据库表
    - 知识产品 UI 详细设计
    - 未经基准验证的解析器选型结论
dependencies:
  - ../prd-outline-plan.md
  - ../scenarios-and-product-scope.md
  - ../object-model-and-information-architecture.md
  - ../knowledge-lifecycle-and-multimodal.md
  - ./collaboration-permission-and-privacy.md
  - ../../../design/framework/engine/data-knowledge/nexus-knowledge.md
  - ../../../design/framework/engine/content/document-engine.md
gains:
  - 能判断一项知识能力应由 AAF 自研、复用平台能力或通过 Adapter 接入
  - 能使用固定样本、公式和门槛比较解析候选而不依赖功能宣传
  - 能验证外部能力不会形成第二套身份、ACL、任务、索引或知识真理
  - 能在候选失败、升级或退出时保持原件、代际、权限和检索连续性
changelog:
  - 2026-09-02 v0.1.0 | 确立 AAF 主链、供应商中立 Adapter、WeKnora 候选证据、G0-G5 门槛和可逆退出合同
---

# 知识管理产品：构建与集成决策

> 本文给出本版本经评审的默认决策：知识主链、产品身份、权限、治理、检索和 UI 基于 AAF；外部项目只作为参考或无状态能力候选。后续如有新证据，必须由新的 PRD/ADR 显式 supersede 本决策，不能通过临时兼容层或双写悄然改变。

## AAF 现状与目标差距

### 证据状态定义

“设计存在”不等于“能力已实现”。本篇用四种证据状态描述现状：

| 状态 | 证据要求 | 产品表述 |
|------|----------|----------|
| `VERIFIED` | 源码入口、自动化测试或可重复运行证据同时存在 | 可作为当前能力，但仍声明适用边界 |
| `CODE_PRESENT` | 已核实源码实现，端到端或目标场景尚未闭环 | 部分实现，不对外承诺完整能力 |
| `DESIGN_ONLY` | 只有已批准设计或 PRD | 规划目标，不写“已支持” |
| `UNVERIFIED` | 尚未核实代码、测试、生产配置或历史迁移 | 不纳入当前能力和准入依据 |

每次实施评审必须把代码 commit、文件、测试 ID、运行配置和验证日期登记到证据清单。本文只记录 2026-09-02 已核实边界，不替代实施证据。

### 当前能力矩阵

| 能力域 | 状态 | 已核实基础 | 目标差距 |
|--------|------|------------|----------|
| NexusKB 真理与代际 | `CODE_PRESENT` | PostgreSQL 保存 run、source、chunk、entity、fact、evidence；READY 检查点、active run、outbox、PgVector/Neo4j 投影和授权检索已有代码与发布设计 | 仍需对新 KnowledgeItem/ResourceRef 和全部产品入口做端到端验收 |
| 文件与原件 | `CODE_PRESENT` | `FileRecord/FileUploadService` 保存 SHA-256、MIME、大小、存储状态和引用 | 不可变原件、恶意文件、完整修订、保留与物理 GC 闭环待补 |
| 文本解析管道 | `CODE_PRESENT` | `KnowledgePipelineService` 具有 IMPORT、CHUNK、STORE、EMBED、EXTRACT_FACTS、PUBLISH；直接 importer 支持 PDF、DOCX、Markdown、HTML、TXT | 安全预检、持久阶段检查点、locator、DEGRADED/BLOCKED 和解析 Adapter 待补 |
| OCR/ASR | `CODE_PRESENT` | 通用 `OcrService`、`SpeechService` 与供应商路由存在 | 尚未接入知识 ResourceRef、质量门禁、计费幂等与代际 |
| 文档产品能力 | `CODE_PRESENT` | Markdown CRUD、发布、树、搜索、关系图和 SSE 通知存在 | 统一 KnowledgeItem、快照版本、协作治理和归档仍是目标；SSE 不是 CRDT |
| 授权与审计 | `CODE_PRESENT` | `AuthorizationService`、CRUD PEP、策略快照、challenge、授权审计和 owner context 存在 | 旧知识入口 P0、动作级分享导出、完整主体链与外部处理审计待闭环 |
| Excel/PPT/扫描件/音视频/CAD/代码 | `DESIGN_ONLY` | 通用 AI 与文件能力可复用 | 需按第三篇格式矩阵和基准逐项准入 |
| KnowledgeItem/ResourceRef 产品根 | `DESIGN_ONLY` | 前四篇已确定产品合同 | 当前源码未形成完整统一根，不得以现有 KnowledgeDocument 代替并宣称完成 |

### 产品建设层次

建设顺序固定为：

1. **产品治理层**：KnowledgeItem、ResourceRef、Space 工作面、责任、生命周期、动作级授权和审计；由 AAF 自研。
2. **可信知识层**：NexusKB PostgreSQL 真理、代际、证据、双时态和授权检索；复用并演进 AAF。
3. **内容与文件层**：Document、文件原件、修订和引用；复用 AAF，补统一身份与生命周期。
4. **解析能力层**：基础 importer、OCR、版面、表格、PPT、ASR、VLM、代码/CAD 元数据；允许 AAF 实现或供应商中立 Adapter 竞选。
5. **可重建投影层**：PgVector、Neo4j、搜索缓存和预览；由 AAF 发布和清理，外部解析器不得直写。

外部能力只能替换第 4 层的窄能力，不拥有 1、2、3、5 层。

### 非目标与禁止路径

- 不部署第二套面向 AAF 用户的知识 UI、账号、Workspace、ACL、KnowledgeBase、任务中心或模型配置。
- 不 Fork 外部完整知识产品形成长期分支。
- 不让 Adapter 直接写 AAF PostgreSQL、对象存储业务命名空间、PgVector、Neo4j、Redis 或审计库。
- 不为迁移保留旧新双写、双读 fallback、legacy shim 或永久兼容层；AAF 未到 v1.0，替换直接按门槛切换。
- 不把 benchmark 结果、外部任务状态或解析器 quality 字段直接设为发布决定。
- 不因外部能力暂时可用而跳过 KnowledgeItem、权限、来源、locator、质量和销毁合同。

## 方案比较与真理边界

### 方案评估

| 方案 | 价值 | 主要代价 | 决策 |
|------|------|----------|------|
| 整体部署并嵌入外部产品 UI/API | 快速获得完整表面功能 | 双账号、双 ACL、双知识库、双任务、双模型配置和双运维；请求链依赖外部产品 | 拒绝进入生产主链；只允许隔离研究环境 |
| Fork 外部项目深改 | 可直接修改全栈 | 长期跟随、许可证/供应链、Go/Vue 与 Java/Next.js 双栈、绕过 AAF 引擎 | 拒绝 |
| 全部由 AAF 自研 | 真理和技术栈最统一 | 深层多模态解析投入大、验证周期长 | 适用于产品、治理、权限、真理和常用解析 |
| AAF 主链 + 无状态 Adapter | 统一产品和真理，可择优替换窄解析能力 | 需要稳定 schema、隔离运行、基准和退出治理 | **采用** |
| 调用托管解析 API | 运维轻、能力更新快 | 数据区域、外发、成本、供应商锁定 | 仅 C0-C3 批准 profile 参与候选；C4 禁止外部处理 |

### 唯一真理与存储职责

| 层/存储 | 唯一职责 | 禁止职责 |
|---------|----------|----------|
| AAF PostgreSQL | KnowledgeItem/ResourceRef 映射、来源、修订、处理任务、NormalizedParsePackage 身份与 digest、当前代际、ACL、治理、证据、审计、计费 | 不保存大二进制；不接受 Adapter 自行发布 |
| AAF 对象存储 | 原始载体、规范化解析包载荷、预览和受控导出 | 不决定当前版本、权限或发布；对象 key 不是业务 ID |
| NexusKB PostgreSQL | run、chunk、entity、fact、evidence、双时态和 active run | 不让外部系统绕过 AAF 写入 |
| PgVector / Neo4j | 当前代际的向量与图候选投影 | 不持有独立 ACL、来源裁决或历史真理，不反写 PostgreSQL |
| Adapter 临时存储 | 单任务输入、处理中间文件和待返回结果 | 不长期保存、建立用户空间、索引或知识库 |
| Benchmark 环境 | 隔离样本、候选输出和评分 | 不连接生产凭据，不发布到生产真理或索引 |

NormalizedParsePackage 的身份、schemaVersion、parserDigest、inputHash、outputHash、状态和 AAF 发布关系只保存在 PostgreSQL；大载荷可按 hash 放对象存储。package 是处理证据，不是 KnowledgeItem 或 NexusKB 当前知识。Adapter 返回的 warnings、quality 和建议只作为 AAF 质量门禁输入。

### 单文档单路径

生产中一个原始修订在一个 parser profile 下只分配一个 `parserDigest` 和一个可发布处理 run：

- 灰度时按稳定哈希把不同文档分给不同候选；同一文档不把两套结果同时写入生产真理。
- 研究 benchmark 可以镜像同一脱敏样本到多个候选，但输出进入独立凭据、bucket/namespace 和数据库，不得发布。
- 重新解析使用新 run 和新 parserDigest；旧 run 保留证据，不能原地变异。
- 解析失败时只按已批准 fallback 顺序创建新 run；不在同一 run 内悄然更换候选。
- AAF 统一执行质量门禁、NexusKB 入库和 active run 切换，确保外部解析器失败不影响线上旧代际。

### Adapter 规范化包

供应商中立请求至少包含：jobId、idempotencyKey、inputHash、短期只读内容引用、声明 MIME、profileId/version、允许能力、语言、页/媒体范围、deadline、数据等级和 traceId。

返回包至少包含：schemaVersion、parserCandidateId、parserDigest、inputHash、结构单元、规范化文本、locator、对象/表格/媒体轨道、质量原始指标、warnings、错误码、用量和 outputHash。

AAF 必须校验：请求和输入摘要一致、schema/parser digest 已准入、结构有界、locator 可打开原件、输出 hash 稳定、错误与质量可解释、无主动内容和危险引用。校验失败的包进入 FAILED/BLOCKED，不写 NexusKB。

Adapter 不回调公开 AAF 业务接口。任务交付使用受认证的窄队列或 AAF 主动拉取；回调若存在也只能提交 package digest 和任务状态，正文通过隔离通道获取。

## WeKnora 证据与基准

### 候选登记而非产品依赖

所有解析候选使用通用登记：

| 字段 | 要求 |
|------|------|
| `parserCandidateId` | AAF 内稳定候选 ID，不使用“当前默认”作身份 |
| `sourceType` | INTERNAL / OPEN_SOURCE / MANAGED_API |
| `sourceVersion` | 固定 tag、commit 或不可变镜像 digest |
| `licenseEvidence` | 许可证文件 hash、NOTICE/归属、依赖许可证扫描和分发判断 |
| `supplyChainEvidence` | SBOM、镜像签名、来源、漏洞扫描、构建方式和维护 owner |
| `capabilityProfiles` | 格式、语言、页数、结构、locator 和硬限制 |
| `dataBoundary` | 部署区域、网络、日志、训练、保留、删除和子处理者 |
| `exitAssets` | AAF 所有 schema、测试集、原件和可重建证据 |

WeKnora `v0.7.2` 是首批公开候选证据之一，不是唯一候选或运行依赖。2026-09-02 已核验其固定 tag 页面声明 MIT，并展示文件夹树、分块/Wiki 版本、PDF/Word/图片/Excel/PPT 等格式、连接器、解析时间线和可观测能力；这些只说明“值得进入证据审查”，不证明安全、质量、性能、成本或与 AAF 合同兼容。缺失 commit/mirror digest、SBOM、许可证复核或安全证据时，只淘汰该候选，不阻塞 AAF 主链和其他候选。

### 基准样本与金标

基准集版本化、脱敏且禁止混入生产数据。最小规模为 500 个原始修订：数字 PDF 100、扫描 PDF/图片 100、DOCX 60、Excel/CSV 60、PPT 60、HTML/网页 40、音视频 40、代码/压缩包 20、CAD 元数据 20；高校制度、学生服务合成数据和课程项目各不少于 120 项。

每项样本保存：原件 hash、格式版本、语言、页/时长/大小、复杂度、允许处理 profile、人工金标、期望 locator、恶意/异常标签和版权许可。金标由两人独立标注并解决分歧，数据集版本变化后所有候选重跑。

安全样本单独包括：MIME 欺骗、恶意宏、压缩炸弹、路径穿越、加密/DRM、超大页、畸形 XML/HTML、SSRF URL、重定向链、提示注入、主动内容和敏感日志诱导；安全门禁不按平均分计算。

### 可复现质量公式

每个 capability profile 固定四个 `0..1` 维度及权重，不在评测中重分配：

```text
Qsample = 0.35 × Qtext
        + 0.30 × Qstructure
        + 0.25 × Qlocator
        + 0.10 × Qcompleteness
Qoverall = Σ(formatWeight × mean(Qsample of format))
```

- `Qtext=1-CER`；字符先按固定 Unicode、空白和换行 canonicalizer 规范化。
- `Qstructure` 是标题层级、阅读顺序、表格边界/单元格/合并关系、slide/shape、媒体段落等 profile 金标的宏平均 F1。
- `Qlocator` 是在该 profile 固定容差内打开正确页+bbox、cell range、slide+shape、timecode 或 file+line 的比例。
- `Qcompleteness` 是金标页、sheet、slide、轨道或文件单元被处理且未静默缺失的比例。
- profile 不适用的维度在配置发布时权重设为 0，其余固定权重合计必须为 1；评测中不得临时调整。
- FAILED、意外 BLOCKED 和静默缺失样本四维记 0；安全规则正确 BLOCKED 的样本进入安全集，不进入质量平均。

候选必须先达到第三篇各格式硬阈值和 locator 100% 覆盖，再比较 Qoverall。采用外部候选需要相对 AAF 当前基线 `Qoverall` 提升至少 0.05，或在质量下降不超过 0.01 时 12 个月 TCO 降低至少 20%；两项都未满足则不接入。

### 性能、成本与统计

性能从 AAF 原件固定成功到 package 校验完成，报告 P50/P95/P99、吞吐和峰值资源。每个 Adapter 子调用 deadline≤600秒；大型 job 可由多个子调用组成，但完整数字文档 P95≤10分钟、扫描件 P95≤30分钟，并单列排队、下载、解析和上传时长。

12 个月 TCO 固定币种、单价日期、负载曲线和利用率：

```text
TCO = 计算/存储/网络
    + 模型或 API 调用
    + Adapter 开发与升级摊销
    + 运维与值班工时
    + 许可证/安全/合规处置
    + 失败重试和人工纠错
```

每个候选至少重复 3 轮；对 Qoverall 和 P95 使用 bootstrap 95% 置信区间。阈值边界区间重叠时判定并列，优先选择 AAF 内部实现或更小供应链/退出风险，不用单次最快结果决策。

## Adapter 安全与运维

### 隔离与最小权限

Adapter 使用固定签名镜像、只读根文件系统、非 root、CPU/内存/磁盘/进程/时限配额和每任务临时目录。默认禁止出网；托管 API 只开放批准域名/IP、TLS/mTLS 和 egress 审计，禁止任意 URL 获取、云元数据地址、内网段和重定向逃逸。

- Adapter 不持有 AAF 数据库、向量库、Neo4j、对象存储主凭据；内容引用为单任务短期只读且绑定 inputHash。
- 秘钥来自专用 secret manager，按候选/环境/数据等级隔离，日志与错误必须脱敏。
- 输入先经过 AAF 安全预检；Adapter 仍在沙箱内处理，不能执行宏、脚本、嵌入对象或不可信代码。
- 输出包按不可信输入校验，限制节点数、深度、字符串、文件数、解压比和 locator 范围。
- C4 禁止外部 Adapter；本地隔离候选也必须通过第四篇的动作授权和 profile 审批。

### 幂等、任务与计费

唯一处理身份为：

```text
organizationId + resourceRevisionId + inputHash
+ profileVersion + parserDigest + requestDigest
```

相同身份的并发提交只允许一个可接受 package 身份、一个可发布 run 和最多一笔可结算费用。底层调用可能因分布式失败发生重试，但重复回调或结果不得重复发布；同一 idempotencyKey 携带不同摘要立即拒绝。

状态为 QUEUED、RUNNING、SUCCEEDED、FAILED、BLOCKED、CANCEL_REQUESTED、CANCELLED、EXPIRED。Adapter 成功不等于 Processing READY；AAF 校验、质量门禁和 NexusKB 发布完成后才可用。费用绑定 subject、资源、parserDigest、供应商凭证、用量和最终处置，失败/重复计费可对账。

### 可观测性与 SLO

每阶段记录 traceId、jobId、候选/digest、输入输出摘要、数据等级、队列/运行时间、资源、网络目标、用量、错误码、重试、清理和 AAF 发布结果，不记录正文。

| 指标 | 目标 | 口径 |
|------|------|------|
| 合法支持 job 成功率 | ≥99% | 通过安全预检且命中已准入 profile 的 job；策略正确 BLOCKED 单列 |
| Adapter 服务可用性 | ≥99.9% | 月度可接受请求；AAF 主链不因候选不可用而失去旧代际 |
| 重复发布/重复结算 | 0 | 按唯一处理身份核对 PostgreSQL 与账单 |
| 单实例临时清理 | 任务终态后15分钟内完成 | 枚举临时卷、文件、对象、进程和外部 task |
| 批量清理率 | P99≤15分钟，最长≤24小时 | ≥1000任务的清道夫账本；超时告警 |
| 未批准 egress | 0 | 网络策略和出口日志 |
| 解析包校验拒绝可见性 | 100% | 所有 schema/digest/hash/locator 拒绝有错误码和审计 |

正文内存无法被可靠证明“从未出现”，因此验收只声明可枚举存储、进程终止、禁止 core dump、加密临时盘和运行环境销毁证据，不做不可验证的绝对承诺。

### 版本升级与灰度

parserDigest 由镜像、依赖、配置、模型和 schema 共同计算，不得原位变异。新 digest 重新通过 G0-G3 后进入 G4：按 Organization 白名单和稳定哈希分配 1%→10%→50%→100%，一个文档只走被分配的单一路径。

安全事件立即熔断且不受样本下限限制；成功率、质量、时延或成本越界在至少 100 个 job 后可提前停止；晋级下一档至少累计 500 个 job 并满足完整窗口。回退只停止新分配并切回上一已批准 digest；在途结果按原 digest 校验，不能混入另一 run。

## 决策门槛与退出策略

### G0-G5 门槛

| Gate | 必须证据 | 通过人 |
|------|----------|--------|
| `G0 候选登记` | 固定版本/digest、许可证政策结论、SBOM、签名、漏洞和维护/退出 owner | 架构 + 法务/许可证责任人 |
| `G1 安全` | 威胁模型、隔离、SSRF/恶意文件/凭据/egress/残留测试全绿；C4 边界验证 | 安全负责人；任一高危失败淘汰 |
| `G2 质量` | 固定数据集、公式、硬阈值、三轮结果与置信区间 | 产品 + 质量负责人 |
| `G3 性能成本` | 分层 P95/P99、额定负载、12月TCO和容量模型 | 平台 + 财务/运营负责人 |
| `G4 灰度` | 每档≥500 job，入口/数据等级/错误/计费/清理达标；100 job 后可质量熔断 | 变更审核人 |
| `G5 生产` | P0关闭、runbook/告警/值班、回退、删除与完整退出演练通过 | 人类最终批准 |

没有候选通过时继续使用 AAF 已批准能力并对不支持格式显式 METADATA/UNSUPPORTED，不用未批准外部服务兜底。

### 采用与淘汰规则

候选只有全部通过 G0-G5 才可成为某个 profile 的生产 parser。任一许可证冲突、签名不明、高危漏洞、数据边界不满足、安全样本逃逸、未批准 egress 或真理直写能力均直接淘汰，不进入加权比较。

质量/成本采用前述固定规则。功能数量、Star、截图、厂商品牌和“支持格式”声明不计分。WeKnora 或任何候选失败只影响该候选，不改变 AAF 自研主链。

### 运行回退

候选故障时：停止新任务分配，保持当前已发布 NexusKB 代际，未处理修订显示明确失败/降级；只有已通过对应 Gate 的上一 digest 或 AAF parser 才能接管新 run。来源已撤销、权限已撤销或内容已失效时，禁止以旧代际可用为由继续返回。

权威撤权从事务提交后的下一次请求立即拒绝。缓存、索引和 Adapter 临时对象的物理清理可以异步，但清理期间所有响应仍回 PDP/真理层复核；不得把 15 分钟清理 SLO解释为 15 分钟访问窗口。

### 完整退出

退出在删除镜像或凭据前完成以下演练：

1. 冻结该 digest 的新任务分配，等待或取消在途任务并完成计费对账；
2. 验证全部原件可读且 hash 一致，NormalizedParsePackage schema/身份由 AAF 所有；
3. 确认 AAF 或另一已批准 parser 覆盖受影响 profile、重处理容量和预计窗口；
4. 对受影响修订创建新 run，抽样验证 locator、质量、检索、权限和引用一致性；
5. 达到 RPO=0（原件、业务身份、ACL、发布记录不丢）和目标 RTO≤4小时恢复新任务能力；大规模重处理可在后台继续；
6. 撤销网络、队列、secret、短期 URL 和服务账号，清除临时对象并取得外部删除回执；
7. 保留许可证、SBOM、审计、计费和退出报告，删除不再需要的 Adapter 制品。

如果 AAF 尚不能覆盖某格式，先冻结新摄入并保留经安全批准、签名且不可变的旧制品和运行环境，直到替代迁移完成；不得先删除制品再发现无法重建。完整退出至少每季度演练一次。

### 验收标准

**AC-B01：整体产品嵌入被拒绝**

```gherkin
Feature: 主链边界
Scenario: 候选方案试图进入 AAF 生产业务主链时架构门禁拒绝
  Given 集成方案包含外部用户、Workspace、ACL、知识库 UI 或向量真理中的任一生产组件
  When 方案提交 G0 审查
  Then 方案应被标记为整体集成并拒绝
  And 隔离 benchmark 环境仍可按研究审批运行
  And AAF 生产路由和数据不得连接该完整产品
```

**AC-B02：候选证据缺失只淘汰该候选**

```gherkin
Feature: 供应商中立候选登记
Scenario: WeKnora v0.7.2 缺少批准的镜像 digest 时不能进入 G1
  Given 候选登记声明 sourceVersion "v0.7.2" 和 MIT
  And 候选没有批准的不可变镜像 digest 与 SBOM
  When 候选申请进入 G1
  Then 该候选应停留在 G0 并记录证据缺口
  And AAF 内部 parser 与其他候选评测不得被阻塞
  And WeKnora 不得成为生产运行依赖
```

**AC-B03：Adapter 不能写 AAF 真理存储**

```gherkin
Feature: 无状态 Adapter
Scenario: Adapter 进程尝试连接 PostgreSQL 或 Neo4j 时被网络策略阻断
  Given Adapter job 700 只获得单任务只读内容引用和结果通道
  When Adapter 进程尝试访问 AAF PostgreSQL、PgVector 或 Neo4j
  Then 每次连接都应被网络策略拒绝
  And AAF 真理与投影不得发生写入
  And 安全审计应记录目标、候选 digest 和 jobId
```

**AC-B04：不合法解析包不能入库**

```gherkin
Feature: 规范化包校验
Scenario: locator 越界的成功包被 AAF 拒绝
  Given Adapter 返回状态 SUCCEEDED 且 parserDigest 已准入
  And 返回包包含超出原始 PDF 页数的 locator
  When AAF 校验 NormalizedParsePackage
  Then package 应标记为 FAILED 并返回固定 locator 错误码
  And 系统不得创建 NexusKB READY run
  And 当前线上代际应保持不变
```

**AC-B05：SSRF 输入不能访问内网**

```gherkin
Feature: Adapter 网络隔离
Scenario: 网页解析输入重定向到云元数据地址时被阻断
  Given URL 样本首先返回到云元数据地址的重定向
  When 候选处理安全基准样本
  Then AAF 或 Adapter 安全层应阻断请求
  And 云元数据响应字节数应为 0
  And 候选应记录 BLOCKED 原因而不是解析成功
```

**AC-B06：C4 不进入外部 Adapter**

```gherkin
Feature: 数据等级处理边界
Scenario: C4 修订请求外部 OCR 时在创建任务前拒绝
  Given ResourceRevision 800 的数据等级为 C4
  And parserCandidate 20 的 sourceType 为 MANAGED_API
  When 用户请求使用候选 20 执行 OCR
  Then PDP 应返回 DENY
  And 外部任务、短期 URL 和候选计费记录均不得创建
  And 用户只能选择已批准的本地隔离 profile
```

**AC-B07：并发重复只发布和结算一次**

```gherkin
Feature: Adapter 幂等
Scenario: 相同处理身份的并发回调不会重复发布
  Given 两个回调携带相同 resourceRevisionId、inputHash、profileVersion、parserDigest 和 requestDigest
  When 两个回调并发提交相同 outputHash
  Then PostgreSQL 只应存在一个可接受 package 身份
  And 最多一个 NexusKB run 可成为当前代际
  And 最多一笔费用可进入结算
  And 重复回调应返回已有结果标识
```

**AC-B08：benchmark 结果不能发布生产真理**

```gherkin
Feature: 基准环境隔离
Scenario: benchmark 凭据尝试发布 active run 时失败
  Given benchmark job 900 使用隔离数据库身份和对象命名空间
  When job 900 尝试调用生产发布入口
  Then 认证和网络策略均应拒绝请求
  And 生产 active run、向量和图投影不得变化
  And benchmark 输出只能进入评测清单
```

**AC-B09：质量公式可重复计算**

```gherkin
Feature: 可复现解析基准
Scenario: 两名评测者用同一数据版本得到相同候选得分
  Given 数据集版本、formatWeight、profileWeight、canonicalizer 和候选 outputHash 均固定
  When 两名评测者独立计算 Qoverall
  Then 两个结果的绝对差应小于 0.000001
  And FAILED 与意外 BLOCKED 样本应按零分计入
  And 评分程序版本和输入摘要应写入报告
```

**AC-B10：新 digest 灰度不形成双路径**

```gherkin
Feature: 解析器版本灰度
Scenario: 被分配到新 digest 的文档只产生一个生产解析路径
  Given parserDigest new 已通过 G0-G3 并进入 10% 灰度
  And ResourceRevision 910 的稳定哈希被分配给 parserDigest new
  When ResourceRevision 910 开始生产处理
  Then 系统只应创建绑定 parserDigest new 的可发布 run
  And 旧 digest 不得并行写入生产 package 或 NexusKB
  And 回退只影响后续新任务分配
```

**AC-B11：撤权立即拒绝而清理异步完成**

```gherkin
Feature: 撤权与物理清理解耦
Scenario: Adapter 临时对象未清理时下一次知识请求仍被拒绝
  Given ResourceRevision 920 的处理授权已在事务中撤销
  And Adapter 临时对象仍处于15分钟清理窗口
  When 旧会话发起撤权后的第一笔 read 请求
  Then PDP 应立即拒绝请求
  And 临时对象不得被读取、发布或重新发送
  And 清道夫应继续按独立清理任务处理该对象
```

**AC-B12：候选超时不静默切换未批准服务**

```gherkin
Feature: 显式失败与回退
Scenario: 当前 digest 超时时保持旧代际并报告失败
  Given 当前处理 run 使用已批准 parserDigest A
  And 没有其他通过对应 Gate 的 fallback digest
  When parserDigest A 的 job 超过完整 job deadline
  Then 新 run 应标记 FAILED
  And 已发布旧代际在来源和权限仍有效时保持可用
  And 系统不得调用未批准候选或更改 parserDigest
```

**AC-B13：退出演练保持 RPO 零**

```gherkin
Feature: Adapter 完整退出
Scenario: 退出 parserDigest old 后原件、身份和授权不丢失
  Given parserDigest old 的新任务分配已冻结
  And 替代 parserDigest new 已通过对应 Gate 并具备重处理容量
  When 运维执行完整退出演练
  Then 所有受影响原件 hash、KnowledgeItem、ResourceRef、ACL 和发布记录应保持一致
  And 在途任务应完成或取消并完成计费对账
  And 抽样修订应由新 digest 重建且通过 locator、检索和权限回归
  And 旧凭据、网络、临时对象和外部副本应有撤销或删除证据
  And 退出报告应记录 RPO 0 和新任务能力恢复时间
```

第五篇通过条件为：AAF 主链边界经人类确认，候选采用必须逐项通过 G0-G5，`AC-B01` 至 `AC-B13` 全部可重复执行；没有外部候选通过时仍能以明确降级继续 AAF 产品，而不是引入双写或未批准 fallback。
---
level: Practice
layer: Model
purpose: AAF-122 知识图谱领域本体与实例双层抽取的技术任务拆分、依赖、验证标准与评审状态
status: draft
version: 0.1.0
date: 2026-11-07
author: AI/architect
review_status: 待人类审核（design.md 审核通过前 #12203 之后的任务不得开始，#12203 预研除外）
tags:
  - AAF-122
related:
  - ./requirement.md
  - ./design.md
  - ./dev-log.md
---

# 技术任务：知识图谱领域本体与实例双层抽取（AAF-122）

> 需求：[requirement.md](./requirement.md)
> 设计：[design.md](./design.md)（🔴 待人类审核）
> 负责人：architect 拆分 | 创建：11-07

## 约定

- 表结构直接修改现有文件：`v20__knowledge_schema.sql`（migration）、`v12__init_seed_data.sql`（seed）、`view/v108__knowledge_entity_def.sql`（EntityDef seed）。不新增 SQL 文件。
- 单测只写下列"必要单测"，不为 DTO、Controller 转发、SQL 拼装补单测。SQL 与跨组件行为由 #12218 验收测试覆盖。
- 单个任务不要求跑 check。里程碑合入或开 PR 前运行一次 `pnpm check:affected`（红线门禁）。
- 路径前缀：`fw/` = `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/engine/knowledge/`，`api/` = `apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/knowledge/`，`res/` = `apps/service/aaf-api/src/main/resources/db/`。
- 🔶 表示 ≥5 文件或接口签名变更，开始前由协调者确认范围。

## 任务列表

### M0 先行小改进（已完成）

1. ✅ #12201 精确别名匹配加类型一致条件 - developer-service
   verify: AC-17、AC-18；`IncrementalEntityResolverTest` 覆盖（未编译运行，见 dev-log，合入前补跑 `pnpm nx test service`）
2. ✅ #12202 LITERAL 只允许短值，契约升 `knowledge-fact-schema-v6` - developer-service
   verify: AC-19、AC-20；`EntityExtractionServiceTest` 覆盖（同上）
3. ✅ #12220 图谱检索降噪：直接命中只按实体名、单字名不命中、截断前按相关性排序 - 协调者
   verify: `KnowledgeGraphProjectionServiceTest` 断言已同步（未编译运行，同上）
4. ✅ #12221 predicate 规范化提示词，情态移入 `attributes.modality`，契约升 `knowledge-fact-schema-v7` - 协调者
   verify: 只改提示词常量，需重新入库后观察 predicate 分布（未编译运行，同上）

### M1 本体数据模型与默认本体

3. [ ] #12203 预研：百炼 strict JSON Schema 大枚举可用性
   - 范围：用 qwen 实际模型，在 enum 为 100 / 200 / 400 个值、含可空字段与根 `candidates` 数组的 v7 草案 schema 下，测试是否接受、延迟与错误信息；再抽样 3 篇医疗/养老文档，观察注入本体后模型复用已有类型的倾向
   - 涉及文件：不进入代码库；结论写入 `dev-log.md`，必要时由协调者调整 design.md 的容量默认值
   - 依赖：无（可在设计审核期间进行）
   - 验证：dev-log 记录实测上限、建议容量值、样本观察结论
   - 负责：developer-service | 风险：🟢

4. [ ] #12204 🔶 v20 本体表、实例表改列、默认本体触发器；v12 归一提示词种子
   - 范围：
     - v20 新增 11 张本体表（design"本体层新表"）。
     - 改 `ai_knowledge_ingest_run`、`ai_knowledge_entity`、`ai_knowledge_fact`、`ai_knowledge_fact_evidence` 四张表的列与约束。
     - 新增 `init_ai_knowledge_ontology_v20()` 触发器，内容为 12 个默认类型及版本 1 快照。
     - v12 修改 `aaf.knowledge.fact-extraction.user/system`（`{ontology}` 占位与数据声明）。
     - v12 新增 `aaf.knowledge.ontology-normalization.system/user` 及 KNOWLEDGE 分类归属。
   - 涉及文件：`res/migration/v20__knowledge_schema.sql`、`res/seed/v12__init_seed_data.sql`
   - 依赖：design 审核通过
   - 验证：AC-01、AC-02 的数据前提；本地重建库后 Flyway 全部执行成功，新建知识库自动得到版本 1（由 #12218 验收覆盖，本任务不写单测）
   - 负责：developer-service | 风险：🔴（schema）

5. [ ] #12205 本体快照、存储与层级校验
   - 范围：
     - `OntologySnapshot` / `EntityTypeDef` / `RelationTypeDef`。
     - `OntologyStore`，负责快照读取、按项表生成新版本（同事务切换 ACTIVE 与指针）、项变更、同义词、候选、支持行、提案行、回填行的 JDBC 访问。
     - `OntologyHierarchy`，负责成环、深度 ≤ 3、子树深度、OTHER 保护等校验。
     - `OntologyProperties`（`aaf.knowledge.ontology.*` 与 `application.yml` 默认值）。
   - 涉及文件：`fw/ontology/` 下新增约 6 个类，`application.yml`
   - 依赖：#12204
   - 验证：必要单测 `OntologyHierarchyTest`（成环、超深、改挂子树、OTHER 作父类型被拒）；`OntologySnapshotTest`（`withDescendants`、`isSubtypeOf`、快照 JSON 规范化后摘要稳定）
   - 负责：developer-service | 风险：🟡

### M2 抽取注入与候选

6. [ ] #12206 🔶 抽取契约按本体构建，契约升 v7（删除静态路径）
   - 范围：
     - 新增 `ExtractionContract.forOntology(snapshot)`，生成 schema、指令、词表块（截断与转义）。
     - `EntityExtractionPrompt` 删除 `ENTITY_TYPES`、`OUTPUT_SCHEMA`、`normalizeEntityType` 与类型清单，`OUTPUT_CONTRACT_VERSION` 改为 `knowledge-fact-schema-v7`。
     - 删除 `ExtractedEntityType`。`ExtractedTriple` 增加候选引用字段与 `relation`，`subjectType` 改为 String。
     - 新增 `ExtractedCandidate`、`ChunkFacts`。
     - `EntityExtractionService.extract` 改为从 RunContext 取契约，返回 `ChunkFacts`，`meteredInput` 计入 schema 与指令摘要。
     - 候选证据校验、候选引用清理。
   - 涉及文件：`fw/graph/EntityExtractionPrompt.java`、`ExtractionContract.java`（新）、`ExtractedTriple.java`、`ExtractedCandidate.java`（新）、`ExtractedFacts.java`、`ExtractedEntityType.java`（删）、`EntityExtractionService.java`、`EntityExtractionServiceTest.java`
   - 依赖：#12205
   - 验证：AC-03、AC-04、AC-05（enum 只含生效项）、AC-09、AC-23；必要单测：
     - `ExtractionContractTest`：
       - enum 与快照一致。
       - 同一快照的 schema 摘要稳定。
       - 词表块中的 `</ONTOLOGY>`、控制字符被转义或去除。
       - 超长名称被截断。
     - 更新 `EntityExtractionServiceTest`：
       - 候选证据不在 FOCUS 时丢弃候选，事实正常。
       - 无事实引用的候选被丢弃。
       - 非法 code 生成哈希编码。
   - 负责：developer-service | 风险：🔴（输出契约，接口签名变更）

7. [ ] #12207 🔶 run 冻结本体版本，fingerprint 与配置加载
   - 范围：
     - `TrustedKnowledgeStore.beginRun` 读取当前版本并写入 run 新列。
     - `RunContext` 携带 `OntologySnapshot` 与 `ExtractionContract`。
     - `KnowledgeIngestConfigurationService.resolve()` 加载归一提示词。
     - `KnowledgePipelineService` 的 fingerprint 追加快照摘要、schema 与指令摘要、归一提示词摘要、归一契约版本、归一配置摘要。
     - 发布提交后投递 EVOLVE 任务（处理器在 #12210），run 版本落后时登记追平回填（处理器在 #12212）。
   - 涉及文件：`fw/trusted/TrustedKnowledgeStore.java`、`KnowledgeIngestConfigurationService.java`、`fw/pipeline/KnowledgePipelineService.java`、`TrustedKnowledgeStoreRunTest.java`、`KnowledgeIngestConfigurationServiceTest.java`
   - 依赖：#12206
   - 验证：AC-21（run 与断言的版本来源）、AC-22；更新 `TrustedKnowledgeStoreRunTest`：本体版本不同则 fingerprint 不同，并且不复用旧 run
   - 负责：developer-service | 风险：🔴（接口签名变更）

8. [ ] #12208 候选归一
   - 范围：
     - `OntologyCandidateNormalizer`：规范化、精确与同义词匹配、向量召回、批量模型判断（SAME / SUSPECT / NEW）、REJECTED 处理、同义词登记（AUTO_MAP、反向写法）。
     - 新增 `OntologyNormalizationPrompt` 与 `OntologyMappingDecision`。
     - `KnowledgeAiMeter.invokeOntologyNormalization`，与 `invokeEntityResolution` 同构，stage 为 `knowledge-ontology-normalization`。
     - 候选与项的向量写入 `ai_knowledge_ontology_embedding`。
   - 涉及文件：`fw/ontology/OntologyCandidateNormalizer.java`、`OntologyNormalizationPrompt.java`、`OntologyMappingDecision.java`（新）、`fw/trusted/KnowledgeAiMeter.java`、`OntologyStore.java`
   - 依赖：#12205、#12207
   - 验证：AC-04、AC-07；必要单测 `OntologyCandidateNormalizerTest`：
     - 精确命中生效项。
     - 命中同义词。
     - 距离 ≤ 0.08 直接映射。
     - 判断区间送模型，且每批 ≤ 20。
     - SUSPECT 记录疑似目标。
     - REJECTED 不计数。
     - 模型返回越界 key 被拒。
   - 负责：developer-service | 风险：🟡

9. [ ] #12209 🔶 实例写入改造：类型 ID、关系归一、约束校验、版本记录
   - 范围：
     - `EntityExtractionService.persist` 的顺序改为：候选归一 → 实体消歧 → 方向规范化（反向同义词、对称排序）→ 约束校验 → 写入。
     - `IncrementalEntityResolver` 与 `TrustedKnowledgeStore.findExactEntities/findEntityCandidates/createOrAliasEntity` 改用 `entity_type_id`，`entity_key` 改为 v2，并删除 #12201 的类型字符串规范化 SQL。
     - `persistFact/persistLiteralFact` 写入 `relation_type_id` / `classification` / `predicate_text` / `relation_candidate_id`，计算 `fact_key` v2；断言写入 `raw_predicate`、`ontology_version_id`。
     - 写入候选支持行。
   - 涉及文件：`fw/graph/EntityExtractionService.java`、`fw/trusted/IncrementalEntityResolver.java`、`TrustedKnowledgeStore.java`、`IncrementalEntityResolverTest.java`、`EntityExtractionServiceTest.java`，新增 `fw/ontology/FactRelationNormalizer.java`
   - 依赖：#12208
   - 验证：AC-07、AC-08、AC-17、AC-18（回归）、AC-21；必要单测：
     - `FactRelationNormalizerTest`：
       - 后代类型满足约束。
       - 违反约束被拒，同批其他事实保留。
       - LITERAL 客体需要 `allowsLiteralObject`。
       - 反向写法交换主客体。
       - 对称关系排序后 fact_key 相同。
     - 更新 `IncrementalEntityResolverTest` 签名
   - 负责：developer-service | 风险：🔴（≥5 文件，接口签名变更）

### M3 自动生效与提案

10. [ ] #12210 本体演进任务：计数、自动生效、过期、系统合并提案
    - 范围：
      - `OntologyEvolutionService`：锁本体行；SQL 聚合当前 PUBLISHED run 的支持数与文档数；按阈值与小库规则生效；容量上限写 `blocked_reason`；EXPIRED 判定与复活；一次演进生成一个新版本；为带 `suspect_target_id` 的新项生成 SYSTEM 合并提案；登记回填。
      - aaf-api 的 `KNOWLEDGE_ONTOLOGY_EVOLVE` 任务处理器。
    - 涉及文件：`fw/ontology/OntologyEvolutionService.java`、`OntologyStore.java`、`api/service/KnowledgeOntologyTaskHandlers.java`（新）
    - 依赖：#12209
    - 验证：AC-02、AC-05、AC-06（版本部分）、AC-10（系统来源）；必要单测 `OntologyPromotionRuleTest`（把阈值判定抽成纯函数）：
      - 恰好达到阈值时生效，差一次时不生效。
      - 单文档库只看出现次数。
      - 达到容量时阻止生效。
      - 计数为 0 时过期。
    - 负责：developer-service | 风险：🟡

11. [ ] #12211 结构性变更提案服务
    - 范围：
      - `OntologyProposalService.propose`：校验、影响范围统计、基线版本。
      - `OntologyProposalService.approve`：锁本体行、过期判定、按变更类型修改项表与同义词、生成新版本、登记回填。
      - `OntologyProposalService.reject`：要求填写意见，无副作用。
      - 执行语义见 design"执行语义"表。
    - 涉及文件：`fw/ontology/OntologyProposalService.java`、`OntologyChangeApplier.java`（新，纯函数，作用于快照模型）、`OntologyStore.java`
    - 依赖：#12205、#12210（共用版本生成）
    - 验证：AC-10 至 AC-14；必要单测 `OntologyChangeApplierTest`：
      - 合并后源变为目标的同义词，子类型改挂到目标。
      - 删除后重定向到父类型或 OTHER。
      - OTHER 不可删。
      - 调整父类型时成环被拒。
      - 过期判定：基线落后且涉及项已改时失效；基线落后但涉及项未改时仍可通过。
    - 负责：developer-service | 风险：🟡

### M4 回填与投影

12. [ ] #12212 🔶 回填收敛事务与任务
    - 范围：
      - `OntologyBackfillService` 按 design 步骤 a-k 在单事务内收敛：实体重定向、候选转正、`entity_key` 重算与实体合并、事实重指向、关系重定向、未归类转正与降级、对称排序、`fact_key` 重算与事实合并（断言迁移）、outbox、watermark、checkpoint，以及回填状态与提案 APPLIED。
      - `KNOWLEDGE_ONTOLOGY_BACKFILL` 处理器：失败置 FAILED，重试。
      - 追平回填（trigger_key 为 `run:{runId}`）。
    - 涉及文件：`fw/ontology/OntologyBackfillService.java`、`OntologyStore.java`、`fw/trusted/TrustedKnowledgeStore.java`（实体与事实合并 SQL）、`api/service/KnowledgeOntologyTaskHandlers.java`
    - 依赖：#12210、#12211
    - 验证：AC-06（重新归类）、AC-15、AC-16；必要单测 `OntologyRedirectResolverTest`（重定向链解析：多级合并、删除后父类型也被删除、遇到环立即失败）。收敛 SQL 的正确性与幂等由 #12218 验收覆盖
    - 负责：developer-service | 风险：🔴（实例数据批量改写）

13. [ ] #12213 Neo4j 投影适配
    - 范围：
      - `projectFact` 增加 `typeId`、`relationTypeId` 属性，`type` 与 `predicate` 写编码，清理同 id 不同 factKey 的旧边。
      - `TrustedKnowledgeStore` 的投影查询（`currentFactProjection`、`currentFactIds`、`currentEntityIds`）只取 CLASSIFIED 事实。
      - 创建 Neo4j 属性索引，位置沿用现有索引初始化处。
      - `GraphService` 返回 typeCode 与 relationCode。
    - 涉及文件：`fw/trusted/KnowledgeGraphProjectionService.java`、`TrustedKnowledgeStore.java`、`fw/graph/GraphService.java`、`KnowledgeGraphProjectionServiceTest.java`
    - 依赖：#12209
    - 验证：AC-15（投影与重建一致）；更新 `KnowledgeGraphProjectionServiceTest`：Cypher 参数包含 typeId 与 relationTypeId，并先删除旧边
    - 负责：developer-service | 风险：🟡

14. [ ] #12214 按类型与关系类型查询事实（P1）
    - 范围：
      - `KnowledgeFactQueryService`、`OntologyFilter`：子类型展开与 redirect 旧类型并集，返回当前断言版本集合。
      - aaf-api 新增 `GET /api/knowledge-bases/{id}/facts`，复用知识库读授权。
    - 涉及文件：`fw/trusted/KnowledgeFactQueryService.java`（新）、`OntologyFilter.java`（新）、`api/controller/KnowledgeBaseController.java`、`api/service/KnowledgeBaseService.java`
    - 依赖：#12209、#12205
    - 验证：AC-24；必要单测：过滤集合展开纯函数（`includeSubtypes` 开或关、redirect 旧类型被纳入），放在 `OntologySnapshotTest` 中
    - 负责：developer-service | 风险：🟡（新增接口）

### M5 管理后台

15. [ ] #12215 🔶 本体后台资源与动作端点
    - 范围：
      - 注册六个 CRUD 资源：`knowledge.ontology-version`、`entity-type`、`relation-type`、`ontology-candidate`、`ontology-proposal`、`ontology-backfill`。除提案外均只读，提案可创建，`reviewComment` 仅在 PENDING 时可改。
      - 动作端点：提案 approve / reject、候选 reject、回填 retry，均要求 `system:knowledge-base:update` 与知识库对象级授权。
      - 先确认 TenantScope 能否通过 `knowledge_base_id` 继承知识库范围，不支持时回到协调者。
    - 涉及文件：`api/KnowledgeOntologyCrudConfiguration.java`（新）、`api/controller/KnowledgeOntology*Controller.java`（新，约 3-6 个）、`api/vo/` 下 VO 与 DTO
    - 依赖：#12211、#12212
    - 验证：AC-13、AC-25（权限）；不写单测，权限与动作由 #12218 验收覆盖
    - 负责：developer-service | 风险：🔴（权限、≥5 文件）

16. [ ] #12216 本体 EntityDef 种子
    - 范围：在 `v108__knowledge_entity_def.sql` 追加六个 EntityDef（slug 与 config 见 design"管理后台"），包括字段、listView、actions 与 confirmMessage
    - 涉及文件：`res/seed/view/v108__knowledge_entity_def.sql`
    - 依赖：#12215
    - 验证：AC-25；管理后台能看到六个入口，PENDING 提案显示通过和驳回按钮，FAILED 回填显示重试按钮（人工冒烟，由 tester 在 #12218 中确认）
    - 负责：developer-service | 风险：🟢

17. [ ] #12217 词表超出预算时的确定性裁剪（P2）
    - 范围：`ExtractionContract` 按块裁剪，保留默认 12 类型、按事实数排序的前 N 项、名称或同义词出现在 FOCUS 中的项；块级 schema 摘要计入 `meteredInput`；超出容量时演进不再阻塞（以 #12203 结论为准）
    - 涉及文件：`fw/graph/ExtractionContract.java`、`EntityExtractionService.java`、`fw/ontology/OntologyEvolutionService.java`
    - 依赖：#12206、#12203
    - 验证：必要单测 `ExtractionContractTest` 补充：裁剪结果对同一快照与块是确定的，默认类型始终保留
    - 负责：developer-service | 风险：🟡

### M6 验收与文档

18. [ ] #12218 验收测试
    - 范围：按 AC-01 至 AC-25 编写 `*AcceptanceTest.java`（Failsafe），`@DisplayName` 引用 AC 编号。抽取、归一、embedding 使用录制回放或桩。覆盖关键场景：
      - 阈值恰好达到与差一次。
      - 单文档库。
      - 同文档同名不同类型。
      - 合并后重复事实的证据合并。
      - 回填中途失败后重试。
      - 入库代际进行中发生本体变更（追平回填）。
      - 清空 Neo4j 后重建，与回填结果比对。
    - 涉及文件：`apps/service/aaf-api/src/test/java/.../acceptance/` 下新增
    - 依赖：M1-M5（#12217 除外）
    - 验证：`test-report.md` 含 AC 覆盖矩阵；`pnpm acceptance:affected` 全绿
    - 负责：tester | 风险：🟡

19. [ ] #12219 同步技术设计文档
    - 范围：
      - 在 `nexus-knowledge-tech.md` 的"增量实体消歧""Neo4j outbox 投影""v20 schema 与 v21 seed 已实现"等章节补充本体层。
      - 在 requirement.md"相关设计"中把"待 architect 输出"替换为 design.md 链接（由协调者执行，本次按指示未修改）。
    - 涉及文件：`docs/design/framework/engine/data-knowledge/nexus-knowledge-tech.md`、`docs/task/v0.13/AAF-122/requirement.md`
    - 依赖：#12218
    - 验证：文档链接有效，与实现一致
    - 负责：architect（requirement.md 由协调者） | 风险：🟢

<!-- 状态标记：[ ] 待开始 | ⏳ 进行中 | ✅ 已完成 | ❌ 已取消 | 🚫 阻塞中 -->

## 依赖图

```text
#12203（预研，可并行）────────────────────────────┐
#12204 → #12205 → #12206 → #12207 → #12208 → #12209 ─┬→ #12210 → #12211 → #12212 → #12215 → #12216
                                                     ├→ #12213
                                                     └→ #12214
#12206 + #12203 → #12217（P2）
M1-M5 → #12218 → #12219
```

## 新增任务

> 开发过程中发现需要新增的任务，由开发者提出，协调者评估后写入

## 评审状态（🔴 高风险适用）

| 阶段 | 执行次数 | 最后执行 | 状态 | 必须 |
|------|---------|---------|------|------|
| product（需求细化） | 1 | 11-07 | ✅ CLEAR | 🔴 是 |
| architect（技术设计） | 1 | 11-07 | 🔄 IN_PROGRESS（待人类审核） | 🔴 是 |
| designer（UI 审查） | 0 | — | — 不适用（后台全部由 EntityDef 通用渲染） | 涉及前端时 |
| developer（编码） | 0 | — | ⏳ PENDING | 🔴 是 |
| architect（代码审查） | 0 | — | ⏳ PENDING | 🔴 是 |
| tester（验收测试） | 0 | — | ⏳ PENDING | 🔴 是 |
| qa（过程审计） | 0 | — | ⏳ PENDING | 🔴 是 |

<!-- 状态：⏳ PENDING | 🔄 IN_PROGRESS | ✅ CLEAR | ❌ BLOCKED | ↩️ RETURNED -->

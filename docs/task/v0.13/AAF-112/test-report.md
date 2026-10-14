---
level: Practice
layer: Product
purpose: 记录 AAF-112 需求验收标准与现有测试断言的静态覆盖映射
status: draft
version: 1.0.0
date: 2026-10-14
author: tester
---

# AAF-112 静态验收覆盖报告

## 方法声明

- 本报告仅对 `requirement.md` 的 Gherkin Scenario 与仓库现有测试源码进行静态覆盖映射；结论均在读到实际测试断言后形成，不以文件名代替证据。
- requirement 未显式编号，本文按出现顺序标记为 AC-01～AC-10。
- **动态结果引用人工确认，本报告仅做静态覆盖映射**：`tasks.md` #11205 记录 2026-10-14 人工确认 service / webui 测试与 `pnpm check:affected` 全绿；本次未运行任何 test、lint、build、check、mvn、pnpm 或 nx 命令。
- “已覆盖”要求 Scenario 的全部 Then/And 均有可定位断言；只覆盖部分条件或仅做源码字符串检查记为“部分覆盖”；找不到对应断言记为“未覆盖”；必须依赖运行时、真实事务或外部环境且无足够静态测试证据时才记为“无法静态确认”。

## AC 覆盖矩阵

| AC | 场景名 | 已核验的测试文件与用例 | 断言证据摘要 | 覆盖结论 |
|---|---|---|---|---|
| AC-01 | 项目创建后形成可恢复的动态工作台 | `apps/service/aaf-api/src/test/java/com/xuejiai/aaf/module/ai/aigc/configuration/service/AigcConfigurationServiceTest.java#should_resolve_default_action_key_by_target_template_only`<br>`apps/service/aaf-api/src/test/java/com/xuejiai/aaf/module/ai/aigc/project/service/AigcProjectMaterializerTest.java#should_materialize_resolved_instance_no` | 断言动作按动态模板解析，`instanceNo=3` 与 `defaultActionKey` 被物化。未断言渠道/预算/质量/slotOverrides 的解析顺序与完整不可变快照、合同角色和关系图；也未测试默认结构视图及按 project 隔离恢复 focus/view/collapse/layer/viewport。 | **部分覆盖** |
| AC-02 | 双视图通过同一入口生成对象候选 | `apps/webui/src/app/studio/projects/[id]/page.wiring.test.ts#图谱与结构视图共享页面级唯一生成 Dialog`<br>`.../page.wiring.test.ts#项目生成固定走 Action，控制器中没有独立 Task fallback`<br>`apps/webui/src/features/studio/media-generation/components/project-media-generation.test.ts#仅映射服务端允许且适用于当前对象的图像和视频动作`<br>`apps/service/aaf-api/src/test/java/com/xuejiai/aaf/module/ai/aigc/task/service/AigcTaskApiAdapterTest.java#should_reject_project_binding_when_execution_is_absent` | 源码断言页面只有一个生成 Dialog、结构/图谱各复用同一回调；控制器先调用 `runAction` 并 return；独立 Task 不能绑定项目。未以行为测试贯穿相同 projectId/objectId/actionKey → Submission/reservation/Run/Task → candidate，也未断言终态先登记 candidate 和绝不自动覆盖已有采用版。 | **部分覆盖** |
| AC-03 | 参考素材保持 MediaVersion 权威身份 | `apps/service/aaf-api/src/test/java/com/xuejiai/aaf/module/ai/aigc/media/controller/AigcMediaControllerTest.java#should_materialize_current_user_uploaded_image`<br>`apps/service/aaf-api/src/test/java/com/xuejiai/aaf/module/ai/aigc/execution/service/AigcExecutionSubmissionServiceTest.java#should_preserve_attachment_media_version_order`<br>`apps/webui/src/features/studio/media-generation/components/project-media-generation.test.ts#仅保留已采用且指向对象当前采用版本的媒体引用` | 断言 fileId 经既有上传媒体能力物化为 IMAGE MediaVersion 命令、附件 MediaVersion ID 顺序保留、只选择当前采用对象版本的 adopted media ref。未断言重复 MediaVersion 去重、数量上限、允许资产来源和预览 URL/Asset ID/fileId 不进入模型身份。 | **部分覆盖** |
| AC-04 | 重复与崩溃恢复不重复执行 | `apps/service/aaf-api/src/test/java/com/xuejiai/aaf/module/ai/aigc/AigcCanonicalRequestTest.java#should_keep_hash_when_nested_map_order_changes`、`#should_change_hash_when_cas_field_changes`<br>`.../execution/service/AigcExecutionSubmissionServiceTest.java#should_restore_command_from_durable_submission_for_replay`、`#should_persist_retry_metadata_without_changing_recoverable_status`<br>`.../execution/job/AigcExecutionSubmissionRecoveryJobTest.java#should_record_retryable_failure_without_terminalizing_submission`、`#should_terminalize_only_permanent_business_failure`<br>`.../execution/service/AigcActionCommandServiceTest.java#should_reject_child_when_submission_not_bound`、`#should_stop_preparation_when_submission_fencing_cas_loses`、`#should_release_prepared_reservation_when_recovery_fails_before_bind`<br>`.../task/service/AigcTaskApiAdapterTest.java#should_not_dispatch_when_intent_is_replayed`<br>`.../task/service/AigcTaskSubmittingRecoveryServiceTest.java#should_reconcile_without_resubmitting_when_submitting_crashes` | 覆盖规范化 hash、durable replay、恢复退避、PRE-BIND 门、owner fencing、Task replay 去重及 provider 崩溃窗口进入对账。未使用并发/数据库唯一约束测试证明同键同参只产生同一 Submission/reservation/root Run，未验证同键异参冲突，也未联合断言供应商调用、候选与费用仅一次。 | **部分覆盖** |
| AC-05 | 候选比较与采用使用 CAS | `apps/webui/src/features/studio/content/CandidateComparisonDialog.test.tsx#并排展示左右候选内容、来源与图片视频`<br>`apps/service/aaf-api/src/test/java/com/xuejiai/aaf/module/ai/aigc/project/service/AigcProjectDeliveryServiceTest.java#should_reject_adopt_when_expected_adopted_version_is_stale`<br>`apps/webui/src/app/studio/projects/[id]/page.wiring.test.ts#自定义 capability 与 CRUD 权限按后端 authority 分离且入口/handler 对称` | 组件断言双候选正文/结构 JSON、来源 Run、DocumentVersion、图片与视频并排展示；服务断言 stale expected adopted version 被 CAS 拒绝；源码断言替换时原因为空会阻断。未行为断言成本与时间展示、替换确认和非空原因载荷、冲突后刷新权威状态，以及历史候选/采用/替换理由可追溯。 | **部分覆盖** |
| AC-06 | 送审冻结不可变内容包 | `apps/service/aaf-api/src/test/java/com/xuejiai/aaf/module/ai/aigc/project/service/AigcProjectDeliveryServiceTest.java#should_reject_manifest_freeze_when_graph_revision_is_stale`<br>`apps/webui/src/features/studio/content/DeliveryWorkflowPanel.test.tsx#evaluate 与 freeze 分别由 read/update capability 控制并调用对应 handler` | 断言 graph revision 漂移时 freeze 拒绝，UI evaluate 成功后可调用 freeze。未断言 REQUIRED/OPTIONAL/EXCLUDED scope 规范化、活动 reservation 阻断、evidenceHash 构成与匹配、同事务重算、不可变 manifest 创建并采用、Review 精确绑定该 manifest。 | **部分覆盖** |
| AC-07 | 审核退回、失效与重新送审 | `apps/service/aaf-api/src/test/java/com/xuejiai/aaf/module/ai/aigc/project/service/AigcProjectDeliveryServiceTest.java#should_reject_review_decision_when_review_is_stale`、`#should_reject_review_decision_for_different_manifest_version`<br>`apps/webui/src/features/studio/content/DeliveryWorkflowPanel.test.tsx#submit 与 decision 使用不同 capability 且 handler 同步门控`、`#collect capability 仅在交付状态开放收录 handler` | 断言 stale Review 不可继续审批、manifest 版本漂移被拒绝；UI 可提交/审批并仅在交付态开放 Work 收录。未断言退回恢复可编辑并保留意见、内容变化实际把旧 Review 标为 stale、stale Review 被 Work 服务拒绝、重送审创建新 manifest/Review 且历史不变。 | **部分覆盖** |
| AC-08 | Work、Publication 与项目完成使用同一交付快照 | `apps/service/aaf-api/src/test/java/com/xuejiai/aaf/module/ai/aigc/project/service/AigcProjectDeliveryServiceTest.java#should_block_completion_when_selected_channel_has_not_succeeded`<br>`.../work/service/AigcWorkCompletionEvidenceAdapterTest.java#should_report_active_publications_and_distinct_succeeded_channels`<br>`.../work/service/AigcWorkServiceTest.java#should_reject_stale_publication_version`、`#should_block_archive_when_active_publication_exists`<br>`apps/webui/src/lib/api/rest/ai/aigc/work.test.ts#publish 传 project 与 work version`、`#cancel 传 project、work 与 publication version`、`#retry 传 project、work 与 publication version`、`#archive 传 project 与 work version` | 覆盖 `ALL_SELECTED_CHANNELS` 未完成 blocker、活动/成功 Publication 证据、Publication CAS、活动发布阻断归档及前端双层版本载荷。未断言 Work 只收录 approved non-stale 精确 manifest、Publication 固定 Work/渠道、失败重试/活动取消状态且不可伪造成功、全部发布策略、完成后项目归档与历史继续可读。 | **部分覆盖** |
| AC-09 | Activity SSE 断线后恢复权威项目状态 | `apps/service/aaf-api/src/test/java/com/xuejiai/aaf/module/ai/aigc/event/service/AigcActivityEventServiceTest.java#should_replay_to_upper_bound_before_registering_live_subscriber`、`#should_catch_up_committed_events_in_id_order_when_callbacks_are_out_of_order`、`#should_catch_up_database_cursor_on_heartbeat_without_local_broadcast`<br>`apps/webui/src/features/studio/content/ProjectActivityProvider.test.ts#cursor key 明确绑定 owner、org 与 workspace`、`#分片 SSE 只分发 activity 命名事件`、`#重复 id 不重复处理，乱序未见 id 仍处理且 cursor 不回退` | 后端断言持久游标重放、乱序补齐与 heartbeat 补查去重；前端断言 scope 化 cursor、SSE 分片和重复事件去重。未断言终态事件触发 project/graph/summary/versions/runs/media refs/Work/Publication/费用全部重新查询，也未组件级证明断线后无需手刷即可显示权威终态。 | **部分覆盖** |
| AC-10 | 权限和归档只读对称生效 | `apps/service/aaf-api/src/test/java/com/xuejiai/aaf/module/ai/aigc/AigcAuthorityContractTest.java#should_use_one_authority_source_for_custom_operations`、`#should_use_same_authority_in_controllers`、`#should_seed_custom_authorities_for_permission_queries`<br>`apps/service/aaf-api/src/test/java/com/xuejiai/aaf/module/ai/aigc/event/controller/AigcActivityEventControllerTest.java#should_reject_untrusted_query_scope`<br>`apps/webui/src/app/studio/projects/[id]/page.wiring.test.ts#自定义 capability 与 CRUD 权限按后端 authority 分离且入口/handler 对称`<br>`apps/webui/src/features/studio/content/DeliveryWorkflowPanel.test.tsx#无 capability 时不渲染可变更入口` | 反射断言 Controller/Service 权限码一致、seed 可达；SSE 拒绝未授权 scope；前端隐藏入口并在部分 handler 二次门控。未以服务行为测试覆盖 owner/org/workspace/project 对称隔离和归档后所有写命令拒绝，也未证明不存在绕过权限、CAS、幂等或生命周期门的第二 REST 路径。 | **部分覆盖** |

## #11218 声明核对

| #11218 主题 | 断言级证据 | 核对结论 |
|---|---|---|
| 动态解析 | `AigcConfigurationServiceTest#should_resolve_default_action_key_by_target_template_only` | 有局部断言；未覆盖完整解析顺序、数量覆盖与快照。 |
| 并发幂等 | canonical hash、submission replay 与 Task replay 测试 | 有幂等组成证据；无真实并发和唯一约束结果断言。 |
| 崩溃窗口 | Submission recovery、PRE-BIND release、Task SUBMITTING reconciliation | 覆盖若干窗口；未逐一证明 reserve/bind/dispatch 后副作用唯一。 |
| reservation fencing | `AigcActionCommandServiceTest#should_stop_preparation_when_submission_fencing_cas_loses` | 证明 preparation owner CAS 丢失后停止；未直接覆盖 reservation 自身 PREPARED/BOUND/RELEASED 全状态 fencing。 |
| Task 去重 | `AigcTaskApiAdapterTest#should_not_dispatch_when_intent_is_replayed` | 已覆盖重放不重复 dispatch；未联测候选和费用去重。 |
| 事件重放 | `AigcActivityEventServiceTest` replay/catch-up 与 `ProjectActivityProvider.test.ts` duplicate cursor | 游标、顺序和事件去重有直接断言；业务投影重查未覆盖。 |
| 采用 CAS | `AigcProjectDeliveryServiceTest#should_reject_adopt_when_expected_adopted_version_is_stale` | stale expected pointer 拒绝有直接断言；替换确认、原因和审计链不完整。 |
| manifest hash | freeze stale graph test 与 UI evaluate/freeze test | 只传递/使用 hash；无 evidenceHash 内容、重算一致性和 manifest 成功冻结断言。 |
| 审核 stale | stale Review 决策拒绝测试 | 只覆盖消费 stale；未覆盖内容变化触发 stale 及 Work 拒绝。 |
| 发布策略 | completion 的 `ALL_SELECTED_CHANNELS` 测试 | 仅覆盖一种策略的未满足分支。 |
| 权限隔离 | authority contract、SSE scope、前端 capability gates | 权限码对称有证据；资源级 owner/org/workspace/project 隔离不完整。 |
| 双视图同弹窗 | `page.wiring.test.ts#图谱与结构视图共享页面级唯一生成 Dialog` | 源码接线直接断言，未做用户交互渲染测试。 |
| 无 Task fallback | `page.wiring.test.ts#项目生成固定走 Action...` 与独立 Task 拒绝项目绑定测试 | 路径边界有直接断言；未端到端证明整个链路。 |
| 断线恢复 | 后端 replay/heartbeat 与前端 cursor/duplicate 测试 | 传输恢复有断言；全量 Query 失效与 UI 最终态未覆盖。 |
| 候选比较 | `CandidateComparisonDialog.test.tsx#并排展示左右候选内容、来源与图片视频` | 主要内容和媒体可视比较已覆盖；成本、时间与采用交互不完整。 |
| 无 Chatter UI 路径 | `page.wiring.test.ts#创建到生命周期主路径不自建或回退默认 Assistant 对话` | 源码字符串断言无 `<Chatter` 与默认 Assistant；无完整用户旅程验收。 |

## 未覆盖与部分覆盖风险

| 编号 | AC | 风险等级 | 缺口 |
|---|---|---|---|
| G-01 | AC-01 | major | 动态输入完整快照、默认结构视图和项目隔离状态恢复无行为测试。 |
| G-02 | AC-02 | blocker | 统一生成链到 candidate 的完整业务副作用及“不自动覆盖采用版”未验收。 |
| G-03 | AC-03 | major | MediaVersion 去重/限量及 URL、Asset ID、fileId 非业务身份边界未验收。 |
| G-04 | AC-04 | blocker | 无真实并发、事务崩溃窗口和 Submission/reservation/root Run/Task/candidate/费用全链唯一性测试。 |
| G-05 | AC-05 | major | 替换确认/原因载荷、冲突后权威刷新和候选采用审计历史未验收。 |
| G-06 | AC-06 | blocker | DeliverableSet 规范化、reservation 门、evidenceHash 重算和不可变 manifest 成功冻结未验收。 |
| G-07 | AC-07 | blocker | 退回恢复、内容变化触发 stale、stale Work 拒绝及新旧 manifest/Review 历史未验收。 |
| G-08 | AC-08 | blocker | Work/Publication 精确快照链、发布状态机、全部策略和归档后历史可读未验收。 |
| G-09 | AC-09 | major | 终态事件的全量权威查询失效与无需手刷的 UI 最终态未验收。 |
| G-10 | AC-10 | blocker | 资源级权限隔离、归档后全写拒绝及无第二绕过路径未做服务/接口验收。 |

## 测试放置与命名检查

- 已核验的 Java 文件均为 Mockito/反射/纯服务单元或合同测试，使用 `*Test.java`，与 Surefire/developer 单测命名一致；未把可见真实数据库/全链验收内容塞入这些文件。
- 已核验的 TS 文件均为纯函数、组件或源码接线测试，使用 `*.test.ts(x)`，与 Vitest 单测命名一致。`page.wiring.test.ts` 位于 Next App 页面旁，属于平台接线测试，位置合理。
- 在本次核验的 AAF-112 测试中未发现通过 mock `next/*` 测共享组件的放置错误。
- **覆盖层缺口**：未发现 AAF-112 专用 `*IT.java`、`*AcceptanceTest.java` 或 `*.accept.test.ts(x)`；现有命名本身与其单测内容匹配，但不能替代需求 Scenario 的验收/集成层。该缺口已计入上表各 AC 的部分覆盖风险，不重复计数。

## 结论

动态执行状态引用 #11205 的人工全绿确认；静态映射显示 #11218 声明的主题均有局部测试证据，但没有任何 AC 达到全部 Then/And 的完整验收覆盖，结论为 **不通过**。

`已覆盖=0 部分=10 未覆盖=0 无法静态确认=0`

`blocker=6 major=4 minor=0`

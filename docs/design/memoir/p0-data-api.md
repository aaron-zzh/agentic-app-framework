---
level: Practice
layer: Product
purpose: 给出回忆录平台的数据模型草案、REST 接口清单与示例、安全与降级要求：一期一图一事部分按一期需求规格编写，原方案 A 部分作为二期和后推阶段的基线，供 architect 评审定稿
status: draft
version: 0.3.0
date: 2026-11-11
author: AaronZZH & Kiro
tags:
  - 回忆录
  - 数据模型
  - 接口
  - P0
  - 一图一事
related:
  - ./agent-workflow.md
  - ./fact-check-engine.md
  - ../../prd/memoir/一期需求规格.md
  - ../../prd/memoir/一期任务.md
  - ../../prd/memoir/后续需求规格.md
  - ../../prd/memoir/后续任务.md
scope:
  includes:
    - 一期一图一事：家族档案与成员、邀请爸妈、分项授权、故事卡片（多张照片、原声、转写、标题、话题、状态、可见范围、案例展示授权）、话题库与出题、故事包订单与额度、退款、家人留言、复核与投诉的表结构、状态机落地、接口清单与校验
    - 原方案 A（二期或后推）：人工项目表、自助对话、事实卡片、自助作品、家人补充、升级请求
    - REST 接口清单与示例
    - 安全、审计与降级
    - 待 architect 决定的事项
  excludes:
    - 业务规则与验收线（一期见一期需求规格，二期和后推见后续需求规格）
    - 类结构与模块拆分（进入迭代后在任务目录 design.md 中定义）
gains:
  - 能说出一期故事卡片和故事包订单的表、状态字段与服务端校验
  - 能按一期接口清单找到 P0-01 到 P0-14 每项功能对应的接口
  - 能说出原方案 A 的事实卡片和自助作品的状态字段、来源表和溯源表，以及它们属于二期
  - 能列出评审时需要 architect 决定的问题
---

# P0 数据与接口设计

本文是草案，进入迭代前由 architect 评审，结论写入任务目录的 `design.md`。本文分两部分：

- [一期一图一事](#一期一图一事)：按 [一期需求规格](../../prd/memoir/一期需求规格.md) 编写，该节的 P0 编号指一期需求规格的 P0-01 到 P0-14，任务卡见 [一期任务](../../prd/memoir/一期任务.md)。
- [数据模型](#数据模型) 及之后各节：按原方案 A 编写，业务规则见 [后续需求规格](../../prd/memoir/后续需求规格.md)，未注明来源的 P0 编号指后续需求规格。其中自助对话、事实卡片、自助作品、家人补充属于二期，人工项目与升级请求属于后推阶段；身份、家族档案、成员与邀请、授权在一期按"一期一图一事"一节调整后落地，两处不一致时一期以该节为准。

## 落地约定

- 后端模块建议为 `com.xuejiai.aaf.module.memoir`，遵循 [架构约束](../../reference/dev/architecture-constraints.md)。
- 运营后台的列表、表单和运维动作由 `sys_entity_def` 声明，不手写页面。
- 主存储为 PostgreSQL。已定：P0 时间线与人物关系只用 PostgreSQL 加 JSON，不投影 Neo4j。

## 一期一图一事

只列一期新增或调整的表和接口，状态机与放行条件以 [一期需求规格 状态机](../../prd/memoir/一期需求规格.md#状态机) 为准。沿用下文的 `user_identity`、`family_archive`、`archive_member`、`archive_invitation`、`consent_record`，一期不建 `elder_profile`（待人类确认，见 [一期任务 超出容量与待决](../../prd/memoir/一期任务.md#超出容量与待决)）。

### 一期新增表

```sql
-- 故事卡片：一段讲述一张卡
story(
  id PK, archive_id FK, narrator_id FK,
  topic_id FK NULL, child_prompt_id FK NULL,     -- 来自话题库或子女出题，均空为自由讲
  audio_uri, duration_sec INT,                   -- 原声，最后一版
  transcript JSON,                               -- 逐句转写，带时间戳
  title, title_source ENUM('quote','topic'),     -- 原话摘取或话题名
  video_uri NULL, ai_label_done BOOL,
  risk_flags JSON,                               -- 高风险规则命中
  status ENUM('GENERATING','IN_REVIEW','BLOCKED','FAILED','PENDING',
              'PUBLISHED','WITHDRAWN','DELETED'),
  visibility ENUM('self','family'),              -- 不提供公开链接
  showcase_consent BOOL DEFAULT false,           -- 案例展示授权
  withdrawn_by ENUM('narrator','consent','ops') NULL,
  order_id FK NULL,                              -- 扣段数的订单
  gen_attempts INT, last_error, created_at, published_at
)

-- 故事照片：1 到 9 张，保存顺序
story_photo(
  id PK, story_id FK, seq INT, origin_uri, restored_uri NULL
)

-- 状态留痕
story_status_log(
  id PK, story_id FK, from_status, to_status, reason, operator_id, device, created_at
)

-- 话题库与子女出题
topic(
  id PK, title, tts_uri, stage, festival NULL, sort INT, enabled BOOL
)
child_prompt(
  id PK, archive_id FK, author_id FK, text, used_story_id FK NULL, created_at
)

-- 故事包订单与额度
story_pack_order(
  id PK, payer_id FK, narrator_id FK, archive_id FK,
  pack ENUM('trial','monthly','yearly'), price_cent INT, is_first_year_price BOOL,
  quota INT NULL,                                -- 尝鲜包 3，月度包 20，年度包为空
  used INT DEFAULT 0, overdraft INT DEFAULT 0,
  pay_order_no,                                  -- 关联 module/pay 订单
  status ENUM('PENDING_PAYMENT','CLOSED','PAID','ACTIVE','EXHAUSTED',
              'EXPIRED','REFUNDING','REFUNDED'),
  active_at NULL, expire_at NULL, created_at
)

-- 家人留言
story_comment(
  id PK, story_id FK, author_id FK, kind ENUM('text','voice'),
  content NULL, audio_uri NULL, tts_uri NULL, played_by_narrator BOOL, created_at
)
```

邀请爸妈复用 `archive_invitation`，增加 `inviter_voice_uri`（子女录的一句话）和 `invitee_role`（讲述者或家人）两个字段。

### 一期接口

| 方法 | 路径 | 说明 | 规格 |
|------|------|------|------|
| POST | `/api/memoir/orders` | 子女下单故事包，返回小程序支付参数 | P0-04 |
| POST | `/api/memoir/orders/{id}/refund` | 付款子女申请首段退款，服务内部调用 pay 模块 | P0-04 |
| GET | `/api/memoir/orders` | 子女查看订单、剩余段数和到期时间 | P0-04 |
| POST | `/api/memoir/invitations` | 生成邀请卡片 | P0-03 |
| POST | `/api/memoir/invitations/{code}/accept` | 老人或家人接受邀请 | P0-03、P0-09 |
| GET | `/api/memoir/topics/current` | 老人本周话题（子女出题优先） | P0-05 |
| POST | `/api/memoir/prompts` | 子女出题 | P0-05 |
| POST | `/api/memoir/stories` | 老人提交照片和录音，进入 GENERATING | P0-06、P0-07 |
| POST | `/api/memoir/stories/{id}/retell` | 重讲，不重复扣段数 | P0-06 |
| POST | `/api/memoir/stories/{id}/publish` | 预览完成后发给家人 | P0-08 |
| POST | `/api/memoir/stories/{id}/withdraw` | 撤回 | P0-08 |
| DELETE | `/api/memoir/stories/{id}` | 删除，二次确认 | P0-08 |
| GET | `/api/memoir/archives/{id}/stories` | 档案内故事列表，按身份过滤可见范围 | P0-09 |
| POST | `/api/memoir/stories/{id}/comments` | 家人留言 | P0-10 |

支付与退款回调走 `module/pay` 并验签。[一期任务](../../prd/memoir/一期任务.md) 已列出 pay 模块退款接口需要先收紧权限。运营后台的订单、故事复核、话题库、投诉由 EntityDef 声明，不单独设计接口。
- 七层流水线各层的 AI 能力按 [智能体工作流 AAF 能力映射](./agent-workflow.md#aaf-能力映射) 接入。
- 已定：讲述者档案复用 `module/elder/profile` 的 `elder_profile`，回忆录模块只经 elder 模块的服务接口读写，不跨包读表。
- 已定：项目状态机用领域代码实现，不建 BPMN；事实卡片与自助作品的状态机同样用领域代码实现。
- 已定：字段级加密用 JPA 转换器，下文标"加密"的字段均按此处理。
- 迁移脚本使用 v500 到 v599 号段（见 [架构约束 数据库迁移版本号规划](../../reference/dev/architecture-constraints.md#数据库迁移版本号规划)）；EntityDef 种子用 v100 到 v199 号段。

## 数据模型

### 关系概览

人工项目侧：

```text
org ─┬─ elder_profile ─┬─ consent_record
     │                 └─ questionnaire
     └─ project ───────┬─ project_status_log
                       ├─ interview_session ── transcript_segment
                       ├─ timeline_event
                       ├─ chapter ── fact_claim
                       ├─ delivery_artifact
                       ├─ care_session_log
                       └─ work_log
```

家庭直接下单的项目 `org_id` 为空。

自助侧（一期），以讲述者的家族档案为中心：

```text
sys_user ── user_identity（讲述者、家属、交付人员）
   │
   └── archive_member ─┐
                       │
elder_profile ── family_archive ─┬─ archive_member（讲述者、家属；邀请状态）
（elder 模块）                    ├─ archive_invitation（扫码邀请）
                                 ├─ archive_material（照片、信件，标注上传人）
                                 ├─ family_contribution（记忆、提问，标注来源人）
                                 ├─ interview_session（自助对话）── transcript_segment
                                 ├─ fact_card ─┬─ fact_card_source ──→ transcript_segment 或 family_contribution
                                 │             └─ fact_card_log
                                 ├─ self_work ── self_work_element ── self_work_trace ──→ fact_card 或 transcript_segment
                                 │      └─ delivery_artifact（仅 PUBLISHED 后生成）
                                 ├─ upgrade_request ──→ project（二期起由运营接单创建）
                                 └─ project（人工项目挂在档案下）── project_member
```

自助侧与人工侧的衔接：

| 衔接点 | 方式 |
|-------|------|
| 讲述者 | `family_archive.elder_id` 指向 `elder_profile.id`；讲述者账号用 elder 模块已有的 `elder_profile.user_id` |
| 对话与访谈 | 复用 `interview_session` 和 `transcript_segment`，自助对话 `project_id` 为空 |
| 授权 | 复用 `consent_record`，按讲述者记录，自助和人工共用 |
| 作品文件 | 复用 `delivery_artifact`，自助作品通过 `work_id` 关联 |
| 升级人工 | `upgrade_request` 被接单后创建 `project`，`project.archive_id` 指回档案；CONFIRMED、EDITED 卡片作为 medium 强度证据写入事实核对模块的 `evidence` |

### 表结构草案

#### 人工项目表

```sql
-- 机构（租户）
org(
  id PK, name, contract_id, private_deploy BOOL, created_at
)

-- 老人档案（敏感字段加密存储）
-- 已定复用 elder 模块已有的 elder_profile 表，本草案字段与现有字段的对齐见"待 architect 决定"
elder_profile(
  id PK, org_id FK NULL, name, birth_year, gender, region, dialect,
  floor, care_level,          -- 机构场景：楼层、护理等级
  health_flags JSON,          -- 健康与访谈禁忌
  sensitive_topics JSON,      -- 不愿提及项
  created_at
)

-- 预问卷与可行性评估
questionnaire(
  id PK, elder_id FK, filled_by ENUM('elder','family','staff'),
  answers JSON, feasibility_score INT,           -- 0 到 100
  taboo_topics JSON, suggested_sessions INT, suggested_minutes INT,
  review_required BOOL, reviewed_by FK NULL, reviewed_at
)

-- 项目
project(
  id PK, org_id FK NULL, elder_id FK, contract_id, sku_code, addons JSON,
  archive_id FK NULL,                            -- 新增：所属家族档案（P0-01 人工项目挂在档案下）
  source ENUM('direct','self_upgrade','org'),    -- 新增：项目来源
  upgrade_request_id FK NULL,                    -- 新增：由升级请求创建时填写
  status ENUM('LEAD','SIGNED','INTERVIEWING','STRUCTURING','OUTLINE_APPROVED',
              'DRAFTING','FACT_CHECKING','FAMILY_APPROVED','DELIVERED','CLOSED',
              'PAUSED','TERMINATED'),
  language ENUM('mandarin','cantonese','wuu','min','other'),
  word_target INT, style ENUM('plain','literary','oral'),
  started_at, delivered_at, sla_due_at
)

-- 状态变更留痕
project_status_log(
  id PK, project_id FK, from_status, to_status, reason, operator_id, created_at,
  settlement_snapshot JSON NULL   -- TERMINATED 时写入
)

-- 访谈会话（人工访谈与自助对话共用）
interview_session(
  id PK, project_id FK NULL,                     -- 改为可空：自助对话不属于项目
  archive_id FK,                                 -- 新增：所属家族档案，人工访谈按项目带出
  interviewer_id FK NULL,                        -- 改为可空：自助对话没有访谈师
  started_at, ended_at, duration_min INT,        -- 硬上限 150 分钟
  overtime BOOL,
  location, mode ENUM('onsite','video','org_floor','self_dialogue'),  -- 新增 self_dialogue
  audio_uri, transcript_status ENUM('pending','queued','done','failed','manual'),
  -- 以下为自助对话新增字段
  life_stage NULL,                               -- 本次对话的人生阶段
  prompt_log JSON NULL,                          -- 已问的问题、追问、跳过记录（含子女的每周提问 ID）
  stop_reason ENUM('completed','user_stopped','timeout','service_down') NULL,
  card_status ENUM('pending','done','failed','deferred') NULL,  -- deferred：服务不可用，待补生成
  cards_generated_at NULL
)

-- 转写片段
transcript_segment(
  id PK, session_id FK, speaker ENUM('elder','interviewer','ai','other'),  -- 新增 ai
  start_ms, end_ms, text_raw, text_smoothed, confidence DECIMAL,           -- text_raw、text_smoothed 加密
  needs_review BOOL, sampled BOOL, reviewed_by FK NULL, reviewed_at,
  audio_uri NULL,                                -- 新增：自助对话分段录音
  retell_requested BOOL DEFAULT FALSE            -- 新增：自助对话中低置信片段改为请老人复述
)

-- 时间线事件
timeline_event(
  id PK, project_id FK, title, year_start, year_end,
  place, occupation, people JSON, source_segment_ids JSON,
  status ENUM('proposed','confirmed','rejected','conflict'),
  confirm_mode ENUM('manual','timeout') NULL,    -- 逾期默认通过
  confirmed_by FK NULL, confirmed_at
)

-- 章节
chapter(
  id PK, project_id FK, parent_id NULL, sort_order INT, title,
  content_md, bound_source_ids JSON, locked BOOL,
  revision INT, editor_id FK, ai_edit_ratio DECIMAL
)

-- 可核查陈述：表结构见事实核对模块（fact_claim、evidence、claim_evidence、verification_record）

-- 交付物（人工项目与自助作品共用）
delivery_artifact(
  id PK, project_id FK NULL,                     -- 改为可空：自助作品不属于项目
  archive_id FK NULL, work_id FK NULL,           -- 新增：自助作品文件；project_id 与 work_id 二选一
  type ENUM('hardcover','epub','pdf','audio','short_video','video',
            'care_profile','fact_report','share_page','bio_chapter'),  -- 新增 bio_chapter
  uri, version, generated_at, qc_passed BOOL, ai_label BOOL
)

-- 授权记录（自助与人工共用）
consent_record(
  id PK, elder_id FK, signer_type ENUM('self','guardian','family'),
  signer_name, relation, proof_uri NULL,
  scope JSON,               -- content / voice / portrait / case_showcase
  status ENUM('pending_review','active','revoked'),
  reviewed_by FK NULL, signed_at, expires_at, revoked_at,
  signed_by_user_id FK NULL,                     -- 新增：签署账号，自助场景须为讲述者本人
  channel ENUM('self_service','staff','guardian') NULL,  -- 新增：签署渠道
  text_version,                                  -- 新增：授权文本版本
  read_aloud BOOL DEFAULT FALSE                  -- 新增：是否已语音朗读说明
)

-- 认知友好回顾记录（描述性，不含疗效结论）
care_session_log(
  id PK, project_id FK, plan_id, session_no INT,
  topics JSON, observation_text, caregiver_summary, recorded_by, created_at
)

-- 工时记录
work_log(
  id PK, project_id FK, user_id FK,
  layer ENUM('L1','L2','L3','L4','L5','L6','L7'),
  task_type, started_at, ended_at, minutes INT
)
```

事实核对的四张表、陈述格式和状态机见 [事实核对模块 数据模型](./fact-check-engine.md#数据模型)。

#### 对现有表的改动

| 表 | 改动 | 原因 |
|----|------|------|
| `elder_profile` | 不再新建，复用 elder 模块已有表；草案字段待对齐 | 已定复用 `module/elder/profile` |
| `project` | 加 `archive_id`、`source`、`upgrade_request_id` | P0-01 人工项目挂在家族档案下；P0-18 升级建项目 |
| `interview_session` | `project_id`、`interviewer_id` 改为可空；加 `archive_id`；`mode` 加 `self_dialogue`；加 `life_stage`、`prompt_log`、`stop_reason`、`card_status`、`cards_generated_at` | P0-14 自助对话与人工访谈共用会话表，升级后转写片段可直接作为证据 |
| `transcript_segment` | `speaker` 加 `ai`；加 `audio_uri`、`retell_requested` | P0-14 分段录音；P0-04 自助低置信片段请老人复述 |
| `delivery_artifact` | `project_id` 改为可空；加 `archive_id`、`work_id`；`type` 加 `bio_chapter` | P0-16 只有 PUBLISHED 作品生成文件和分享页，复用同一张文件表 |
| `consent_record` | 加 `signed_by_user_id`、`channel`、`text_version`、`read_aloud` | P0-12 自助场景须本人确认、语音朗读说明 |
| `evidence`（事实核对模块） | `kind` 需加 `self_card`，`source_id` 指向 `fact_card.id`，`strength` 固定为 `medium` | P0-18 带入卡片按中等强度处理；该表以事实核对模块为准，需同步修改该文档 |

约束：`interview_session` 中 `mode = 'self_dialogue'` 时 `project_id` 为空，否则必填；`delivery_artifact` 中 `project_id` 与 `work_id` 有且只有一个非空。

#### 身份与档案成员

```sql
-- 账号身份：一人可有多个身份（P0-13）
user_identity(
  id PK, user_id FK,                             -- sys_user.id
  identity ENUM('narrator','family','staff'),
  status ENUM('active','disabled'),
  granted_by FK NULL, created_at, last_used_at
)

-- 家族档案：每位讲述者一份（P0-17）
family_archive(
  id PK, elder_id,                               -- elder_profile.id，经 elder 服务接口访问
  title,
  dialect,                                       -- 开通时采集
  avoid_topics JSON,                             -- 加密；不想谈的话题，对话提问必须避开
  default_share_scope JSON,                      -- 讲述者设置，如 {"mode":"all_members"} 或 {"mode":"selected","member_ids":[...]}
  person_graph JSON,                             -- 加密；人物与关系，节点标注来源（卡片或家人补充）
  status ENUM('active','deleted'),
  created_by FK, created_at, updated_at
)

-- 档案成员：角色与邀请状态
archive_member(
  id PK, archive_id FK, user_id FK NULL,         -- 按手机号邀请且未注册时为空
  role ENUM('narrator','family'),
  relation,                                      -- 子女、配偶、孙辈、其他
  display_name,
  invitee_phone NULL,                            -- 加密；按手机号邀请时填写
  invite_status ENUM('invited','pending_approval','joined','removed','left'),  -- pending_approval：家属发出的邀请，待讲述者同意
  invitation_id FK NULL,                         -- 经哪个邀请加入
  invited_by_member_id FK NULL, joined_at, removed_by FK NULL, removed_at,
  approved_by_member_id FK NULL, approved_at,    -- 讲述者同意加入
  is_delegate BOOL DEFAULT FALSE                 -- 讲述者指定的代管家属，可设置分享范围和发布作品；每个档案至多 1 位
)

-- 邀请：一个二维码可多人使用，生命周期与成员分开
archive_invitation(
  id PK, archive_id FK, created_by_member_id FK,
  source ENUM('archive','work'), work_id FK NULL, -- 档案页或短片上的邀请入口
  token_hash,                                    -- 只存哈希，明文只出现在二维码中
  max_uses INT DEFAULT 10, used_count INT DEFAULT 0,
  requires_approval BOOL,                        -- 发起人为家属时 TRUE：扫码后进入 pending_approval
  expires_at,                                    -- 默认创建后 7 天
  status ENUM('active','revoked','expired'), created_at
)

-- 人工项目成员：交付人员分配、升级时带入的档案成员
project_member(
  id PK, project_id FK, user_id FK,
  role ENUM('staff','client','family'),
  source ENUM('assigned','archive_import'),
  created_at
)
```

拆分理由：

| 决策 | 理由 |
|------|------|
| 身份单独建 `user_identity`，不放在档案成员里 | 交付人员身份不依附于档案；身份切换只需查本表 |
| 成员与邀请分两张表 | 扫码邀请一码多人，有过期和撤销；成员有自己的状态（加入、移除、退出） |
| 讲述者也是 `archive_member`（`role = 'narrator'`） | 权限校验统一按成员表查；讲述者唯一由部分唯一索引保证 |
| 新增 `project_member` | P0-13 要求交付人员只看到分配给自己的项目，现有草案只有 `interview_session.interviewer_id`；升级时带入档案成员也落在此表 |
| 人物关系存在 `family_archive.person_graph` | 已定 P0 只用 PostgreSQL 加 JSON |

#### 资料与家人补充

```sql
-- 照片、信件（MEM-801）
archive_material(
  id PK, archive_id FK, uploader_member_id FK,   -- 来源人，必填
  kind ENUM('photo','letter','document'),
  file_id,                                       -- AAF 系统文件记录 ID
  caption NULL,                                  -- 加密
  taken_year NULL, content_hash,
  deleted BOOL, created_at
)

-- 家人补充的记忆和提问（MEM-804、MEM-809 每周提问）
family_contribution(
  id PK, archive_id FK, contributor_member_id FK, -- 来源人，必填，取自登录身份
  kind ENUM('memory','question'),
  content_text,                                  -- 加密
  material_ids JSON,                             -- 附带的照片、信件
  target_week DATE NULL,                         -- kind=question 时，作为该周提问
  asked_session_id FK NULL,                      -- 提问已在哪次对话中问过
  status ENUM('active','withdrawn'),
  created_at
)
```

| 决策 | 理由 |
|------|------|
| 照片不复用 elder 模块的 `elder_photo` | `elder_photo.device_id` 非空，面向设备拍摄；家人手机上传没有设备主体，且需要标注上传成员 |
| 每周提问并入 `family_contribution`（`kind = 'question'`） | MEM-804 的"提问"与 MEM-809 的"每周提问"是同一类内容，都要标注来源人 |
| 家人补充中涉及老人的事实走事实卡片 | P0-17 要求老人确认后才能用于润色；抽出的卡片 `origin = 'family_contribution'`，按卡片状态机由老人确认 |

#### 事实卡片

```sql
-- 事实卡片（P0-15）；状态机：EXTRACTED → CONFIRMED | EDITED | DELETED
fact_card(
  id PK, archive_id FK,
  session_id FK NULL,                            -- 来自自助对话时填写
  origin ENUM('dialogue','family_contribution'),
  card_types JSON,                               -- 一个或多个：time/place/person/org/event/number
  extracted_text,                                -- 加密；系统抽取的原文
  edited_text NULL,                              -- 加密；EDITED 时为本人修改后的内容
  display_text,                                  -- 加密；展示与润色用文本，命中 R01 时为软化表述
  risk_rule_ids JSON,                            -- 自助判定命中的规则，如 ["R01"]
  restricted BOOL,                               -- 命中 R02、R03、R04、R09、R10：不润色、不生成旁白
  status ENUM('EXTRACTED','CONFIRMED','EDITED','DELETED') DEFAULT 'EXTRACTED',
  decided_by_user_id FK NULL, decided_at,        -- 必须是讲述者本人
  extractor_version, created_at
)

-- 卡片来源：会话、起止时间、转写文本快照
fact_card_source(
  id PK, card_id FK,
  source_kind ENUM('segment','contribution'),
  session_id FK NULL, segment_id FK NULL,
  start_ms NULL, end_ms NULL,
  contribution_id FK NULL,
  quote_text,                                    -- 加密；来源原文快照
  char_span JSON NULL                            -- 原文中的字符偏移
)

-- 卡片状态留痕
fact_card_log(
  id PK, card_id FK, from_status, to_status,
  before_text, after_text,                       -- 加密
  actor_user_id FK, actor_identity,              -- 动作时的身份
  channel ENUM('tap','voice'),                   -- 点按或语音回答
  client_info JSON,                              -- 设备与会话信息，供审计
  created_at
)
```

| 规则 | 落在数据上 |
|------|----------|
| 只有 CONFIRMED、EDITED 可用于润色 | 润色输入查询条件：`status IN ('CONFIRMED','EDITED') AND restricted = FALSE` |
| EDITED 使用修改后的内容 | `display_text` 取 `edited_text`，原抽取文本保留在 `extracted_text` |
| DELETED 不进入任何生成文字 | 卡片保留不物理删除，供审计和卡片修改率统计 |
| 低置信片段不出卡片 | 置信度低于阈值的片段 `retell_requested = TRUE`，不写 `fact_card_source` |
| 升级时按 medium 强度带入 | 接单时为每张 CONFIRMED、EDITED 卡片写一条 `evidence(kind='self_card', strength='medium')` |

卡片状态从 EXTRACTED 出发，确认后只允许讲述者把 CONFIRMED、EDITED 改为 DELETED，不能改回 EXTRACTED。确认更新带条件 `WHERE status = 'EXTRACTED'`，防止重复提交；删除已确认卡片时，引用它的 PUBLISHED 作品同一事务内转为 WITHDRAWN。

#### 自助作品与溯源

```sql
-- 自助作品（P0-16）；状态机：GENERATING → PREVIEW → PUBLISHED → WITHDRAWN，PREVIEW → DISCARDED
self_work(
  id PK, archive_id FK,
  type ENUM('short_video','bio_chapter'),
  title,                                         -- 加密
  life_stage NULL,                               -- bio_chapter 对应的人生阶段
  session_ids JSON, card_ids JSON,               -- 生成时的输入快照
  material_ids JSON,                             -- 使用的照片
  template_id,
  status ENUM('GENERATING','PREVIEW','PUBLISHED','WITHDRAWN','DISCARDED') DEFAULT 'GENERATING',
  uses_voice BOOL, uses_portrait BOOL,
  consent_ids JSON,                              -- 生成和发布时校验通过的授权记录
  ai_label,                                      -- 固定文案："AI 整理、内容经本人确认、未经人工事实核对"
  removed_sentence_count INT,                    -- 反向溯源删除的句子数
  gen_task_id, gen_attempts INT, last_error NULL,
  preview_completed_by FK NULL, preview_completed_at,
  published_by FK NULL, published_at,            -- 讲述者或代管家属
  withdrawn_by FK NULL, withdrawn_at,
  withdraw_reason ENUM('manual','consent_revoked','card_deleted') NULL,
  discarded_by FK NULL, discarded_at,
  share_scope JSON NULL,                         -- 为空时用档案默认范围
  regenerated_from_id FK NULL,                   -- 重新生成时指向旧作品
  created_at
)

-- 作品元素：标题、字幕、旁白、小传正文、原声片段、照片
self_work_element(
  id PK, work_id FK, seq INT,
  kind ENUM('title','subtitle','narration','bio_text','voice_clip','photo'),
  text NULL,                                     -- 加密
  segment_id FK NULL, clip_start_ms NULL, clip_end_ms NULL,  -- voice_clip
  material_id FK NULL,                           -- photo
  restricted_rule_ids JSON,                      -- 命中 R02、R03、R04、R09、R10 的原声片段
  included BOOL,                                 -- 是否进入成片；受限片段默认 FALSE
  kept_by_user BOOL DEFAULT FALSE,               -- 用户选择保留受限原声
  moderation_status ENUM('pending','passed','blocked') DEFAULT 'pending',  -- 内容安全审核；blocked 不进入作品并进运营复核队列
  trace_status ENUM('traced','removed','not_required')  -- removed：找不到来源已删除
)

-- 反向溯源结果：每句文字对应的卡片或原话
self_work_trace(
  id PK, element_id FK,
  source_kind ENUM('card','segment'),
  card_id FK NULL, segment_id FK NULL,
  match_span JSON,                               -- 句内与来源内的对应区间
  score DECIMAL, checker_version, created_at
)
```

| 决策 | 理由 |
|------|------|
| 元素与溯源分两张表 | 一句文字可对应多张卡片或多段原话；被删除的句子保留 `trace_status = 'removed'` 供审计和回归 |
| 作品不可编辑，只能丢弃后重新生成 | 状态机没有编辑态；重新生成产生新作品，`regenerated_from_id` 指回旧作品 |
| 文件和分享页复用 `delivery_artifact` | 与人工交付物同一套下载和标识逻辑；服务端只允许 PUBLISHED 作品写入 |

| 状态转换 | 服务端校验 |
|---------|----------|
| GENERATING → PREVIEW | 所有 `subtitle`、`narration`、`bio_text`、`title` 元素 `trace_status` 为 `traced` 或 `removed`；无 `trace_status` 为空的文字元素 |
| PREVIEW → PUBLISHED | `preview_completed_at` 非空，且完成人是讲述者或已加入的家属；`ai_label` 非空；`uses_voice` 时存在有效的声音使用授权 |
| PREVIEW → DISCARDED | 讲述者或已加入的家属操作 |
| 生成文件、分享页 | `status = 'PUBLISHED'` |

#### 升级请求

```sql
-- 升级人工请求（P0-17 一期记录，P0-18 二期接单）
upgrade_request(
  id PK, archive_id FK, requested_by_member_id FK,
  contact_phone,                                 -- 加密
  note NULL,                                     -- 加密
  status ENUM('submitted','accepted','rejected','cancelled'),
  handled_by FK NULL, handled_at, reject_reason NULL,
  project_id FK NULL,                            -- 接单后创建的项目
  created_at
)
```

一期只写 `submitted` 并上报 `upgrade_requested`；`accepted`、`rejected` 在二期由运营在 EntityDef 动作中处理。

#### 索引

| 表 | 索引 | 用途 |
|----|------|------|
| `user_identity` | `UNIQUE(user_id, identity)` | 一人每种身份一条 |
| `family_archive` | `UNIQUE(elder_id) WHERE status = 'active'` | 每位讲述者一份档案 |
| `archive_member` | `UNIQUE(archive_id, user_id)`；`UNIQUE(archive_id) WHERE role = 'narrator'`；`(user_id, invite_status)` | 防重复加入；讲述者唯一；查"我的档案" |
| `archive_invitation` | `UNIQUE(token_hash)`；`(archive_id, status)` | 扫码查邀请 |
| `archive_material` | `(archive_id, created_at)` | 档案相册 |
| `family_contribution` | `(archive_id, kind, target_week)` | 本周提问 |
| `interview_session` | `(archive_id, started_at)`；`(project_id)` | 档案内讲述列表 |
| `fact_card` | `(archive_id, status)`；`(session_id)` | 待确认卡片；按对话查卡片 |
| `fact_card_source` | `(card_id)`；`(segment_id)` | 来源回放；片段反查卡片 |
| `fact_card_log` | `(card_id, created_at)` | 留痕查询 |
| `self_work` | `(archive_id, status)` | 我的短片 |
| `self_work_element` | `(work_id, seq)` | 按顺序渲染 |
| `self_work_trace` | `(element_id)`；`(card_id)`；`(segment_id)` | 逐句溯源；卡片或片段反查作品 |
| `upgrade_request` | `(archive_id, status)`；`(status, created_at)` | 档案页状态；运营接单列表 |
| `project` | `(archive_id)` | 档案下的人工项目 |
| `project_member` | `UNIQUE(project_id, user_id, role)`；`(user_id)` | 交付人员项目列表 |
| `delivery_artifact` | `(work_id)` | 作品文件 |

#### 加密字段

| 表 | 字段 |
|----|------|
| `family_archive` | `avoid_topics`、`person_graph` |
| `archive_member` | `invitee_phone` |
| `archive_material` | `caption` |
| `family_contribution` | `content_text` |
| `transcript_segment` | `text_raw`、`text_smoothed` |
| `fact_card` | `extracted_text`、`edited_text`、`display_text` |
| `fact_card_source` | `quote_text` |
| `fact_card_log` | `before_text`、`after_text` |
| `self_work` | `title` |
| `self_work_element` | `text` |
| `upgrade_request` | `contact_phone`、`note` |

录音、照片和生成的文件存在对象存储，用服务端加密，不走字段加密。加密字段不能建索引或做模糊查询，查询条件只用 ID、状态和时间。

## 接口

### 通用约定

- 前缀 `/api/v1`，JWT 鉴权。
- 机构侧接口带 `X-Tenant-Id`，服务端按机构隔离数据。
- 敏感字段（姓名、健康信息）在机构侧和列表接口中脱敏返回。
- 所有写操作支持幂等键 `Idempotency-Key`。
- 耗时操作（转写、抽取、生成、扫描）返回 `202` 和任务编号，完成后回调或轮询。
- 自助接口的当前身份取自 JWT 中的身份声明，切换身份后重新签发；请求体中的操作人字段一律忽略。

### 接口清单

| 模块 | 接口 | 对应规格 |
|------|------|---------|
| 项目 | `POST /projects`、`GET /projects/{id}`、`PATCH /projects/{id}/status`、`GET /projects/{id}/status-log` | P0-01 |
| 工时 | `POST /work-logs`、`GET /projects/{id}/work-logs` | P0-01 |
| 预问卷 | `POST /questionnaires`、`GET /questionnaires/{id}`、`POST /questionnaires/{id}/review` | P0-02 |
| 提纲 | `POST /projects/{id}/interview-outline` | P0-03 |
| 访谈 | `POST /sessions`、`POST /sessions/{id}/audio`（分片）、`POST /sessions/{id}/transcribe`、`GET /sessions/{id}/transcript`、`POST /segments/{id}/review` | P0-04 |
| 结构化 | `POST /projects/{id}/timeline/extract`、`GET /projects/{id}/timeline`、`GET /projects/{id}/relations` | P0-05 |
| 时间线确认 | `POST /timeline/{eventId}/confirm`、`POST /timeline/{eventId}/reject`、`POST /projects/{id}/timeline/confirm-batch` | P0-06 |
| 成稿 | `POST /projects/{id}/outline`、`POST /chapters/{id}/generate`、`PUT /chapters/{id}`、`GET /chapters/{id}/sources` | P0-07、P0-08 |
| 核对 | 见 [事实核对模块 接口](./fact-check-engine.md#接口)；另有 `POST /projects/{id}/elder-confirm`、`POST /fact-reports/{id}/sign` | P0-09 |
| 交付 | `POST /projects/{id}/artifacts`、`POST /artifacts/{id}/qc`、`GET /artifacts/{id}/download`、`PATCH /projects/{id}/share-page` | P0-10 |
| 机构 | `GET /org/elders`、`POST /org/elders/import`、`POST /org/schedule`、`GET /org/dashboard`、`POST /org/care-sessions`、`GET /org/care-sessions/export` | P0-11 |
| 授权 | `POST /consents`、`POST /consents/{id}/review`、`POST /consents/{id}/revoke`、`POST /elders/{id}/deletion-requests` | P0-12 |
| 身份 | `GET /me/identities`、`POST /me/identity-switch` | P0-13 |
| 家族档案 | `POST /archives`、`GET /archives`、`GET /archives/{id}`、`PATCH /archives/{id}/settings`、`PATCH /archives/{id}/share-scope`、`GET /archives/{id}/relations`、`GET /archives/{id}/consents` | P0-12、P0-17 |
| 成员与邀请 | `POST /archives/{id}/invitations`、`POST /invitations/{invId}/revoke`、`GET /invitations/{token}`、`POST /invitations/{token}/accept`、`GET /archives/{id}/members`、`DELETE /archives/{id}/members/{memberId}` | P0-17 |
| 资料与补充 | `POST /archives/{id}/materials`、`GET /archives/{id}/materials`、`POST /archives/{id}/contributions`、`GET /archives/{id}/contributions`、`POST /contributions/{id}/withdraw` | P0-17 |
| 自助对话 | `POST /archives/{id}/story-sessions`、`GET /story-sessions/{id}/stream`（WebSocket）、`POST /story-sessions/{id}/segments`、`POST /story-sessions/{id}/skip`、`POST /story-sessions/{id}/end`、`GET /archives/{id}/story-sessions`、`GET /story-sessions/{id}` | P0-14 |
| 事实卡片 | `GET /archives/{id}/fact-cards`、`GET /fact-cards/{id}/sources`、`GET /fact-cards/{id}/speech`、`POST /fact-cards/{id}/confirm`、`POST /fact-cards/{id}/edit`、`POST /fact-cards/{id}/delete` | P0-15 |
| 自助作品 | `POST /archives/{id}/works`、`GET /archives/{id}/works`、`GET /works/{id}`、`GET /works/{id}/trace`、`PATCH /works/{id}/elements/{elementId}`、`POST /works/{id}/preview-progress`、`POST /works/{id}/publish`、`POST /works/{id}/discard`、`POST /works/{id}/artifacts`、`GET /works/{id}/share`、`PATCH /works/{id}/share-scope` | P0-16、P0-10 |
| 升级 | `POST /archives/{id}/upgrade-requests`、`GET /archives/{id}/upgrade-requests`；二期 `POST /upgrade-requests/{id}/accept`、`POST /upgrade-requests/{id}/reject` | P0-17、P0-18 |

### 一期自助接口说明

登录复用 AAF 已有的微信与手机号登录，不另建接口。作品文件下载复用 `GET /artifacts/{id}/download`。签署和撤回授权复用 `POST /consents`、`POST /consents/{id}/revoke`。

| 模块 | 方法与路径 | 用途 | 调用身份 | 对应规格 |
|------|----------|------|---------|---------|
| 身份 | `GET /me/identities` | 列出当前账号的身份 | 任意 | P0-13 |
| 身份 | `POST /me/identity-switch` | 切换身份并重新签发令牌，写审计 | 任意 | P0-13 |
| 家族档案 | `POST /archives` | 开通建档，采集方言和不想谈的话题 | 讲述者 | P0-14、P0-17 |
| 家族档案 | `GET /archives` | 列出我已加入的档案 | 讲述者、家属 | P0-17 |
| 家族档案 | `GET /archives/{id}` | 档案总览：讲述、照片、补充、作品、升级入口状态 | 成员 | P0-17 |
| 家族档案 | `PATCH /archives/{id}/settings` | 修改方言、不想谈的话题 | 讲述者 | P0-14 |
| 家族档案 | `PATCH /archives/{id}/share-scope` | 设置档案默认分享范围 | 讲述者 | P0-17 |
| 家族档案 | `GET /archives/{id}/relations` | 人物关系 | 成员 | P0-17 |
| 家族档案 | `GET /archives/{id}/consents` | 当前生效的分项授权，供前端置灰入口 | 成员 | P0-12 |
| 成员与邀请 | `POST /archives/{id}/invitations` | 生成邀请二维码，可来自档案页或短片 | 讲述者、家属 | P0-17 |
| 成员与邀请 | `POST /invitations/{invId}/revoke` | 撤销邀请 | 讲述者、邀请创建人 | P0-17 |
| 成员与邀请 | `GET /invitations/{token}` | 扫码落地页：只返回讲述者称呼和邀请人，不返回档案内容 | 已登录用户 | P0-17 |
| 成员与邀请 | `POST /invitations/{token}/accept` | 加入档案，填写与讲述者的关系 | 已登录用户 | P0-17 |
| 成员与邀请 | `GET /archives/{id}/members` | 成员列表与邀请状态 | 成员 | P0-17 |
| 成员与邀请 | `DELETE /archives/{id}/members/{memberId}` | 移除成员；成员也可移除自己（退出） | 讲述者、本人 | P0-17 |
| 资料与补充 | `POST /archives/{id}/materials` | 上传照片、信件（先取预签名地址，再登记） | 成员 | P0-17、MEM-801 |
| 资料与补充 | `GET /archives/{id}/materials` | 档案相册 | 成员 | P0-17 |
| 资料与补充 | `POST /archives/{id}/contributions` | 补充记忆或每周提问，来源人取自登录身份 | 家属 | P0-17、P0-14 |
| 资料与补充 | `GET /archives/{id}/contributions` | 补充列表，含来源人 | 成员 | P0-17 |
| 资料与补充 | `POST /contributions/{id}/withdraw` | 撤回本人的补充 | 补充人 | P0-17 |
| 自助对话 | `POST /archives/{id}/story-sessions` | 开始一次对话；无内容采集授权时拒绝 | 讲述者 | P0-14、P0-12 |
| 自助对话 | `GET /story-sessions/{id}/stream` | WebSocket 实时语音对话，复用 AAF 实时语音 | 讲述者 | P0-14 |
| 自助对话 | `POST /story-sessions/{id}/segments` | 上传分段录音（弱网补传） | 讲述者 | P0-14 |
| 自助对话 | `POST /story-sessions/{id}/skip` | 跳过当前问题 | 讲述者 | P0-14 |
| 自助对话 | `POST /story-sessions/{id}/end` | 停下或结束；`202` 触发转写与抽卡 | 讲述者 | P0-14、P0-15 |
| 自助对话 | `GET /archives/{id}/story-sessions` | 讲述列表 | 成员 | P0-17 |
| 自助对话 | `GET /story-sessions/{id}` | 对话详情与卡片生成状态 | 成员 | P0-14 |
| 事实卡片 | `GET /archives/{id}/fact-cards` | 按状态查卡片，默认 EXTRACTED | 成员 | P0-15 |
| 事实卡片 | `GET /fact-cards/{id}/sources` | 来源片段文本与音频签名地址 | 成员 | P0-15 |
| 事实卡片 | `GET /fact-cards/{id}/speech` | 卡片语音朗读音频 | 成员 | P0-15 |
| 事实卡片 | `POST /fact-cards/{id}/confirm` | 确认：EXTRACTED → CONFIRMED | 讲述者 | P0-15 |
| 事实卡片 | `POST /fact-cards/{id}/edit` | 修改：EXTRACTED → EDITED，附修改后文本 | 讲述者 | P0-15 |
| 事实卡片 | `POST /fact-cards/{id}/delete` | 删除：EXTRACTED → DELETED | 讲述者 | P0-15 |
| 自助作品 | `POST /archives/{id}/works` | 生成短片或小传章节；`202`，作品进入 GENERATING | 讲述者 | P0-16 |
| 自助作品 | `GET /archives/{id}/works` | 我的短片 | 成员 | P0-16 |
| 自助作品 | `GET /works/{id}` | 作品详情与元素，含受限原声提示 | 成员 | P0-16 |
| 自助作品 | `GET /works/{id}/trace` | 逐句溯源结果 | 成员 | P0-16 |
| 自助作品 | `PATCH /works/{id}/elements/{elementId}` | 选择保留受限原声片段（仅 PREVIEW） | 讲述者 | P0-16 |
| 自助作品 | `POST /works/{id}/preview-progress` | 上报预览进度，播放完成时记完整预览 | 讲述者、家属 | P0-16 |
| 自助作品 | `POST /works/{id}/publish` | PREVIEW → PUBLISHED | 见待决 | P0-16 |
| 自助作品 | `POST /works/{id}/discard` | PREVIEW → DISCARDED | 讲述者、家属 | P0-16 |
| 自助作品 | `POST /works/{id}/artifacts` | 生成正式文件和分享页，仅 PUBLISHED；`202` | 讲述者、家属 | P0-16、P0-10 |
| 自助作品 | `GET /works/{id}/share` | 分享页数据，按成员与分享范围校验 | 成员 | P0-17、P0-10 |
| 自助作品 | `PATCH /works/{id}/share-scope` | 设置单个作品的分享范围 | 讲述者 | P0-17 |
| 升级 | `POST /archives/{id}/upgrade-requests` | 发起升级人工成书，上报 `upgrade_requested` | 讲述者、家属 | P0-17 |
| 升级 | `GET /archives/{id}/upgrade-requests` | 升级请求状态 | 成员 | P0-17 |
| 升级 | `POST /upgrade-requests/{id}/accept` | 二期：运营确认接单，建项目并带入卡片、录音、照片 | 运营 | P0-18 |
| 升级 | `POST /upgrade-requests/{id}/reject` | 二期：运营驳回，填写原因 | 运营 | P0-18 |

运营侧的 `accept`、`reject` 在 EntityDef 中声明为 `upgrade_request` 的 `actions`，不手写页面。

### 示例：触发转写并回调

```http
POST /api/v1/sessions/s_123/transcribe
{ "language": "wuu", "diarization": true, "callback": "https://ops.example.com/hook" }

→ 202 { "task_id": "t_88", "status": "queued" }
```

回调内容：

```json
{
  "task_id": "t_88", "session_id": "s_123", "status": "done",
  "segments": [
    {"speaker": "elder", "start_ms": 0, "end_ms": 4200, "text": "...", "confidence": 0.93}
  ],
  "low_confidence_count": 7
}
```

事实核对的裁决、外部核实、家属确认接口及服务端校验见 [事实核对模块 接口](./fact-check-engine.md#接口)。

### 示例：确认事实卡片

```http
POST /api/v1/fact-cards/fc_501/edit
Idempotency-Key: 7f2c...
{ "text": "1979 年我到纺织厂当车间主任", "channel": "voice" }

→ 200 { "id": "fc_501", "status": "EDITED", "display_text": "1979 年我到纺织厂当车间主任" }
```

以家属身份调用，或卡片已不是 EXTRACTED：

```json
→ 403 { "code": "CARD_NARRATOR_ONLY", "message": "事实卡片需要讲述者本人确认" }
→ 409 { "code": "CARD_ALREADY_DECIDED", "status": "CONFIRMED" }
```

## 安全与审计

| 项 | 要求 |
|----|------|
| 加密 | 录音与文稿静态加密（AES-256），传输 TLS 1.2 及以上 |
| 敏感信息 | 健康信息、声纹、人脸按敏感个人信息处理，字段级加密 |
| 隔离 | 机构数据逻辑隔离；前期不提供私有化部署，AI 能力调用云端模型（优先阿里云百炼） |
| 导出控制 | 机构角色无法调用原始录音和完整文稿的下载接口 |
| 审计 | 所有写操作和敏感数据读取记录审计日志，保留不少于 3 年 |
| 删除 | 删除请求按约定时限执行，结果可查询 |

### 自助相关要求

| 项 | 要求 |
|----|------|
| 档案访问 | 所有 `/archives/{id}/**`、`/fact-cards/**`、`/works/**`、`/story-sessions/**` 接口先按 `archive_member` 校验：当前用户在该档案中 `invite_status = 'joined'`，否则拒绝。对非成员返回 404，不暴露档案是否存在 |
| 未受邀访问 | 无公开链接；分享页和作品文件下载要求登录且为已加入成员；已移除、已退出成员立即失去访问权，已签发的音视频签名地址有效期不超过 10 分钟 |
| 邀请 | 邀请令牌只存哈希；落地页只返回讲述者称呼和邀请人，不返回任何档案内容；过期、撤销、超出次数的令牌拒绝加入 |
| 分享范围 | 分享范围只能由讲述者修改；读取作品时按作品 `share_scope`（为空时用档案默认）过滤，不在范围内的成员看不到该作品 |
| 卡片确认 | `confirm`、`edit`、`delete` 服务端校验三项：令牌中的当前身份是 `narrator`；当前用户是该档案 `role = 'narrator'` 的成员；卡片状态为 EXTRACTED。任一不满足即拒绝，家属身份调用返回 `CARD_NARRATOR_ONLY` |
| 自助授权 | 自助渠道的授权签署同样只接受讲述者本人；开始对话前校验内容采集授权，生成和发布含原声的作品前校验声音使用授权；授权撤回后正在生成的作品中止 |
| 发布门禁 | 只有 PUBLISHED 作品能调用生成文件和分享接口；`delivery_artifact` 写入时校验作品状态 |
| 来源人 | 补充和上传的来源人取自登录身份，不接受请求体传入；来源人不可修改 |
| 敏感读取 | 卡片来源音频、转写原文、照片原图的读取写审计日志 |

### 审计事件

| 事件 | 触发 | 记录内容 |
|------|------|---------|
| `identity_switched` | 身份切换 | 用户、原身份、新身份、设备 |
| `archive_created` | 开通建档 | 讲述者、档案 |
| `consent_signed`、`consent_revoked` | 授权签署、撤回 | 签署账号、渠道、分项、文本版本 |
| `invitation_created`、`invitation_revoked` | 生成、撤销邀请 | 创建人、来源（档案或作品） |
| `member_joined`、`member_removed`、`member_left` | 成员变动 | 成员、关系、经由邀请、操作人 |
| `share_scope_changed` | 修改分享范围 | 档案或作品、前后范围 |
| `card_confirmed`、`card_edited`、`card_deleted` | 卡片决定 | 卡片、操作人、身份、渠道、前后文本（加密） |
| `card_decide_denied` | 非讲述者尝试确认卡片 | 用户、身份、卡片 |
| `work_published`、`work_discarded` | 作品状态变更 | 作品、操作人、完整预览人 |
| `work_shared`、`artifact_downloaded` | 分享页访问、文件下载 | 作品、访问人 |
| `archive_access_denied` | 非成员访问档案、分享页 | 用户、目标资源 |
| `sensitive_read` | 读取来源音频、转写原文、照片原图 | 读取人、资源 |
| `family_contributed` | 家人补充或上传 | 来源人、类型 |
| `upgrade_requested`、`upgrade_accepted`、`upgrade_rejected` | 升级请求与接单 | 发起人、处理人、项目 |

审计事件与 [后续需求规格 埋点与指标](../../prd/memoir/后续需求规格.md#埋点与指标) 的自助事件同名的部分，同时上报埋点；埋点只上报 ID 和状态，不上报文本。

## 降级

转写或生成服务不可用时，`transcript_status` 置为 `manual`，章节可直接人工编写。状态机的人工卡点不变，项目继续推进。

自助对话遇到转写或抽取服务不可用时，录音照常分段保存，`card_status` 置为 `deferred`，服务恢复后补生成卡片；作品生成失败时停在 GENERATING 并重试，`gen_attempts` 和 `last_error` 记录失败。本人确认、反向溯源和 AI 标识不降级。

## 待 architect 决定

- 人物关系与时间线是否投影到 Neo4j，还是 P0 只用 PostgreSQL 加 JSON。已定：P0 只用 PostgreSQL 加 JSON。
- 转写、生成等异步任务的执行方式：AAF 工作流还是独立任务队列。
- 机构侧是否同时提供 GraphQL 查询接口。
- 字段级加密的密钥管理方案。方向已定：JPA 转换器，密钥放 KMS 或环境变量，改 `aaf-framework` 前由协调者评估。
- 音频分片上传与对象存储选型。

### 一期自助版待决

| 事项 | 说明 | 建议 | 决定人 |
|------|------|------|-------|
| 表名前缀 | elder 模块已有 `elder_profile` 表；`project`、`chapter` 等无前缀表名在共享库中易冲突 | 回忆录表统一加 `memoir_` 前缀，本文为便于阅读省略 | architect |
| `elder_profile` 字段对齐 | 本草案的 `name`、`birth_year`、`dialect`、`health_flags`、`sensitive_topics`、`floor`、`care_level` 与现有表的 `full_name`、`birth_date`、`taboos` 等不一致；现有表没有方言、护理等级 | 方言和不想谈的话题放在 `family_archive`；机构字段三期再定是否在 elder 模块加字段 | architect |
| 跨模块引用是否建库级外键 | `family_archive.elder_id`、`project.elder_id`、`consent_record.elder_id` 指向 elder 模块的表 | 只做逻辑引用，不建外键，经服务接口校验存在 | architect |
| 身份是否复用 AAF 角色表 | `user_identity` 与 `sys_role` 有重叠 | 业务身份用 `user_identity`，接口权限仍用 AAF 角色 | architect |
| 转写文本字段加密与检索 | 二期证据绑定用全文检索（见 [一期任务](../../prd/memoir/一期任务.md)），字段级加密后无法在库内检索 | 转写文本只做存储层加密，或另建检索索引；需与安全要求一起定 | architect |
| 扫码加入是否需要讲述者批准 | 需求只写"家人扫码加入" | 已定：讲述者发出的邀请扫码即加入；家属发出的邀请需讲述者同意（`pending_approval`），见 [一期需求规格 P0-09](../../prd/memoir/一期需求规格.md#p0-09-家族档案与家人加入) | product |
| 邀请有效期与次数 | 需求未定义 | 已定：7 天、最多 10 次 | product |
| 家属能否邀请他人 | 需求未定义 | 已定：可以，需讲述者同意 | product |
| 分享范围的粒度与取值 | 需求只写"老人决定分享范围"，未说明是档案级还是作品级、能否选部分成员 | 已定：档案默认加作品覆盖，取值"全部成员"或"指定成员" | product |
| 老人无法操作时谁设置分享范围 | MEM-802 与 P0-10、P0-17 写法不一 | 已定：讲述者可指定 1 位代管家属（`is_delegate`），代为设置分享范围和发布作品；PRD 与需求规格已对齐 | product |
| 谁可以发布作品 | 需求只写"本人或家属完整预览"，未写发布人 | 已定：讲述者或代管家属，完整预览者即发布人 | product |
| 已确认卡片能否改回 | 状态机中 CONFIRMED、EDITED、DELETED 是终态，老人事后改主意没有路径 | 已定：不能改回待确认；讲述者可删除已确认卡片，引用它的已发布作品自动下架 | product |
| 已发布作品能否下架 | 状态机没有 PUBLISHED 之后的状态 | 已定：新增 WITHDRAWN；讲述者或代管家属主动下架、撤回授权、引用卡片被删除时转入，分享链接立即失效 | product |
| 生成失败状态 | 状态机没有失败态 | 停在 GENERATING 并重试，超过上限后提示重新生成（已写入需求规格） | architect |
| 完整预览的判定口径 | 播放到结尾即算，还是需要累计播放时长 | 已定：短片播放进度到达结尾、小传滚动到末尾 | product |
| 照片进入短片是否需要肖像授权 | P0-12 只写声音授权的拦截规则 | 已定：未授权肖像使用时，短片不使用照片，只用原声加字幕 | product |
| 分项撤回粒度 | `consent_record.scope` 是一个 JSON，`status` 对整条记录生效，单项撤回无法表达 | 每个分项一条记录，或 scope 内逐项带状态 | architect |
| 家人补充是否经卡片抽取 | 需求要求涉及老人的事实经老人确认后才能用于润色，未说明机制 | 已定：补充内容抽出卡片，由讲述者确认 | product |
| 自助低置信阈值 | P0-04 的 0.75 是人工复核阈值，自助"请老人复述"未给阈值 | 已定：沿用 0.75 | product |
| 电子小传章节的触发条件 | "连续讲完一个人生阶段"的判定未定义（三期） | 三期前定 | product |
| 同一档案能否并发多个升级请求 | 需求未定义 | 已定：同一档案只允许一个 `submitted` 请求 | product |
| `evidence.kind` 新增 `self_card` | 事实核对模块的表以该文档为准 | 已完成：[事实核对模块](./fact-check-engine.md#表结构) 已加 `self_card` 和强度说明 | 协调者 |
| 卡片本人确认的校验强度 | 服务端只能校验账号和身份，子女拿老人手机代点无法区分 | 已定：一期按账号加讲述者身份校验，记录渠道和设备；不做声纹，原型验证中观察代点情况 | product |
| 内容安全审核 | 需求规格 P0-14、P0-16 新增：AI 提问和生成内容展示前审核，命中进复核队列 | 已加 `self_work_element.moderation_status`；对话提问的审核记录和复核队列存放位置待定 | architect |
| 上述决定涉及的接口 | 讲述者同意加入、指定或取消代管家属、作品下架、删除已确认卡片、内容复核队列 | 需在"一期自助接口说明"中补充 | architect |

---
level: Practice
layer: Model
purpose: 五层智能架构 v2——以智能助理为核心、对齐认知心理模型的领域模型设计
status: draft
version: 5.2.0
date: 2026-07-23
author: AaronZZH
related:
  - ./architecture-v2-development-plan.md
  - ../../../explanation/general-agent/general-agent-migration-design.md
  - ../../../explanation/general-agent/general-agent-delivery-roadmap.md
---

# 五层智能架构 v2（领域模型）

> 以智能助理为认知主体，对齐人类认知心理模型，分层认知、渐进决策。

本章只描述**领域概念**——系统由哪些组件组成、各自承担什么认知职责、彼此是什么逻辑关系。不涉及任何技术实现（不谈框架、存储、协议、类与表）。技术方案见后续各层详细设计。

## 设计立场

整个架构围绕一个核心隐喻展开：**智能助理（Assistant）是系统的"认知主体"，像一个完整的"人"**。它对用户而言是有人格的伙伴，对内则调动各种认知机能去感知、思考、行动与协作。

- **以助理为核心**：用户只与助理打交道。助理不是某一层的中间件，而是认知活动的发起者与归属者——它"拥有"记忆、"调用"推理、"指派"行动、"加入"群体。
- **对齐认知心理**：分层不是技术分层，而是模拟人类认知的层次——从神经元级的推理，到长期记忆与世界观，到任务执行，到自我意识，再到社会协作。每一层对应人类认知的一个真实环节，这让架构对人和 AI 都更易理解。

## 设计原则

- **分工协作，各尽所长**：大模型的理解推理、确定性的精确计算、人类的价值判断各司其职，谁擅长谁承担。
- **群体智能，分层组合**：能力来自组件的分层组合而非单点全能。简单请求在浅层就地解决，复杂目标逐层展开。
- **渐进决策**：认知活动先暂存、后确认；走一步看一步，目标不清晰不阻塞，但每一步可回退。决策权随置信度在层间流动——高置信本层执行，低置信向上回报，必要时转交人类，不固定归属。
- **可验证性优先**：规划阶段把模糊任务降维为可自动验证的子任务，评估阶段区分"可自动验证"与"需人工审查"。
- **能力护栏**：按任务类型动态限定组件的行动范围，以可控的约束换取更大的信任空间。
- **认知降级，保底可靠**：高阶机能不可用时回退到低阶可靠路径（如自主决策受阻则转规则或人工），宁可降级不可失效。
- **量入为出**：按任务难度匹配思考资源，简单的事浅层廉价解决，复杂的事才动用更强的推理。
- **执行反哺，持续学习**：行动所得经评估后沉淀回认知基础，让记忆与知识持续生长，而非用过即弃。
- **知识与能力一体**：知道什么（知识）与能做什么（行动）绑定演进，不各自漂移。
- **三层上下文分离**：常识与世界观（静态·共享）、个体记忆（动态·私有）、会话焦点（临时·当下）三者分置，互不污染。
- **瓶颈在规划与审查**：当执行趋于廉价，规划与把关成为新瓶颈——助理的核心价值是帮用户规划与审查，而非单纯替用户执行。支持高带宽异步审查。

## 五层总览

| 组件 | 隐喻 | 一句话领域职责 | 状态归属 |
|------|------|---------------|---------|
| 助理 Assistant | 自我 | 面向用户的认知主体：感知意图与情绪、决策、调度机能、对结果负责 | 会话级 · 私有 |
| 群体 Team | 社会 | 多个助理为复杂目标分工协作，由主导助理牵头对齐与仲裁 | 项目级 |
| 智能体 Agent | 手脚 | 任务级执行单元，围绕单个任务闭环，无自我、无长期记忆 | 无长期状态 · 用完即收 |
| 认知基础 Cognition | 记忆与世界观 | 记忆、知识、价值观的共享积淀，被动地存取、更新、遗忘 | 持久级 · 共享 |
| 内核 Core | 思考 | 把组织好的上下文转化为推理与生成，无人格、无记忆 | 无 |

以助理为核心：群体是助理的"向上聚合"（多个自我组成社会），智能体与认知基础是助理的"向下机能"（行动的手脚与记忆的积淀），内核是所有组件共享的"思考底座"。

## 架构可视化

```text
                          ┌───────────┐
                          │   用户     │   外界刺激
                          └─────┬─────┘
                                │ 唯一交互入口（1 用户 : N 助理）
      ════════════════════ 会话级 ════════════════════
                                ▼
                ┌───────────────────────────────┐  加入协作  ┌──────────────┐
                │         助理 Assistant         │ ────────▶ │  群体 Team    │
                │   感知意图情绪 → 决策 → 调度    │ ◀──────── │  社会 · 协作   │ 项目级
                └──┬───────────────────────┬────┘  由助理组成 │  牵头 / 仲裁   │
          回忆/沉淀 │                   指派 │              └──────────────┘
   ═══ 持久级 ══════▼══               ═ 任务级 ▼══
   ┌────────────────┐    取用/写回      ┌──────────────┐
   │ 认知基础        │ ◀─────────────▶ │ 智能体 Agent  │
   │ Cognition      │    水平协作       │ 手脚 · 执行   │
   │ 记忆/知识/价值观 │                  │ 感知-规划-    │
   │（共享积淀）     │                  │ 执行-评估-学习│
   └───────┬────────┘                  └──────┬───────┘
           │ 提供素材                           │ 借助思考
           │            ═══ 请求级 ═══          │
           │         ┌──────────────────┐      │
           └────────▶│    内核 Core      │◀─────┘
            共享底座   │ 推理机能 · 思考    │
                      │ 思考 / 生成       │
                      └──────────────────┘
```

### 完整组件全景

```text
┌─────────────────────────────────────────────────────────────────────────┐
│  装配信息（启动即备好、变更即刷新）                            ·快速装配·  │
│  人格库 · 角色库 · 技能库 · 智能体定义 · 模型偏好                          │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│  群体 Team                                                   【项目级】    │
│  目标对齐 → 任务分派 → 进度同步 → 结果聚合 → 冲突仲裁                      │
│  主导助理牵头 · 多助理协作 · 目标级跟踪 · 可恢复（目标级）                  │
└─────────────────────────────────────────────────────────────────────────┘
                              ↕ 加入 / 回报
┌─────────────────────────────────────────────────────────────────────────┐
│  助理 Assistant                                              【会话级】    │
│  前注意分流 → 情绪感知 → 意图理解 → 技能匹配 → 调度 → 反馈整合 → 记忆更新   │
│                                                                          │
│  助理 = 人格(Actor) + 角色(Role) + 记忆策略                               │
│  ┌──────────────────┐  ┌──────────────────────────┐                     │
│  │ 人格 Actor        │  │ 角色 Role                 │                     │
│  │ 我是谁：名字/性格  │  │ 我会什么：技能 + 工具白名单 │                     │
│  │ 可复用 · 跨角色    │  │ 可复用 · 跨人格            │                     │
│  └──────────────────┘  └──────────────────────────┘                     │
│  多分身并行 · 子任务追踪 · 可恢复（会话级）                               │
│  输入缓冲（执行期接收追加输入）· 执行期干预（取消/修改/补充/无关）         │
└─────────────────────────────────────────────────────────────────────────┘
                              ↕ 指派 / 结果
┌─────────────────────────────────────────────────────────────────────────┐
│  智能体 Agent                                                【任务级】    │
│  感知 → 规划（可验证性降维）→ 执行（调用工具）→ 评估 → 学习                │
│  无自我 · 借记忆于认知基础 · 隔离执行 · 可复用 · 工作记忆 · 可恢复（步骤级）│
└─────────────────────────────────────────────────────────────────────────┘
      ↑ 取用（编排式回忆）        ↓ 写回（沉淀）          ·水平协作·
┌─────────────────────────────────────────────────────────────────────────┐
│  认知基础 Cognition                               【持久级 · 跨主体共享】   │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐               │
│  │ 记忆      │ │ 知识      │ │ 价值观    │ │ 决策依据      │               │
│  │短/长/情景/│ │共享资料与 │ │伦理 +     │ │决策点/备选/   │               │
│  │程序化     │ │常识       │ │优先级约束 │ │理由/置信度    │               │
│  └──────────┘ └──────────┘ └──────────┘ └──────────────┘               │
│  取用 · 编排式回忆（理解 → 检索 → 融合 → 组装）                           │
│  沉淀 · 写入记忆（提取 → 去重 → 写入 → 遗忘）                             │
│  用户理解（异步提炼：画像 / 偏好 / 情绪模式）                             │
│  分区存储：个人私有 / 全局共享 / 执行工作区 / 审计留存                     │
└─────────────────────────────────────────────────────────────────────────┘
                              ↕ 上下文传入 / 结果返回
┌─────────────────────────────────────────────────────────────────────────┐
│  内核 Core                                                   【请求级】    │
│  推理 → 生成 → 上下文窗口管理 · 预算控制 · 多模型择选                      │
│  完全无自我 · 上下文由调用方组装后传入                                     │
└─────────────────────────────────────────────────────────────────────────┘

横切：置信度门控（高→自动 / 中→确认 / 低→转人）· 风险分级（高风险动作需人工确认）· 执行轨迹（可观测、可追溯）· 学习反哺（沉淀回认知基础）——均贯穿各层
```

## 认知主体视角的五个组件

### 助理（Assistant）——认知主体 · 自我

面向人的认知主体，是用户唯一的交互对象。它有人格（我是谁）、有能力配置（我会做什么）、有记忆倾向（我如何记事），感知用户的意图与情绪，决定"要不要做、怎么做、交给谁做"，并对最终结果负责。一个用户可拥有多个助理，各有独立人格与擅长领域。

- **认知循环（会话级）**：前注意分流 → 情绪感知 → 意图理解 → 上下文构建 → 行动调度 → 反馈整合 → 记忆更新
- **领域职责**：前注意分流（极快判断简单请求是否需要深想，浅层就地回应）；能力护栏（按任务动态限定可调用的行动范围）；多分身并行（按不同擅长分出子身并行处理，再由主身聚合）
- **状态**：会话焦点、任务进展、对用户的理解（私有）

### 群体（Team）——社会协作 · 共事

当一个目标超出单个助理的能力时，多个助理组成"群体"协作完成。群体由一位主导助理牵头，本身不执行具体工作——它是"社会层面的组织"，真正的行动仍由各助理调度自己的机能完成。

- **认知循环（项目级）**：目标对齐 → 任务分派 → 进度同步 → 结果聚合 → 冲突仲裁
- **领域职责**：主导助理牵头分工与仲裁；面对复杂目标做假设性分解（目标不清晰不阻塞执行）
- **状态**：轻量项目级状态（分派表、进度、仲裁结果），不持有数据级状态

### 智能体（Agent）——行动机能 · 手脚

任务的执行者，是助理的"手脚"。它围绕一个具体任务闭环推进：把模糊任务拆成可验证的小步，逐步执行并自我检查。智能体无自我、无长期记忆——执行前从认知基础取用所需，执行后把所得交还，任务结束即可回收复用。

- **认知循环（任务级）**：感知 → 规划 → 执行 → 评估 → 学习
- **领域职责**：规划（目标分解、任务排序、可验证性降维）；评估（可验证→自动检查，不可验证→标记待人工审查，并给出置信度）；在隔离环境中执行工具/代码（限定可触达资源，防副作用外溢）
- **状态**：无长期状态——任务级运行态临时持有（可落 Session 供恢复），任务结束即弃；可复用处理多个任务

### 认知基础（Cognition）——记忆与世界观 · 积淀

助理的"长期记忆与世界观"：记得发生过什么（记忆）、知道世界是怎样的（知识）、坚持什么不可逾越（价值观）。它被动地存取、更新、遗忘，不主动发起认知活动。它是跨主体共享的底座，多个助理与行动单元从这里取用同一份积淀，也向这里沉淀新的所得，并把"私有的体验"与"共享的常识"分开安放。

- **认知循环（持久级）**：存储 → 回忆 → 更新 → 遗忘（被动响应，不主动触发）
- **领域职责**：积淀记忆/知识/价值观；被动接收各层事件后异步提炼对用户的理解（偏好、情绪模式）——是对事件的反应，非自发触发；留存自主决策的依据（决策点、选项、理由、置信度）以备异步审查
- **状态**：持久 · 共享（私有体验区与共享常识区分置）

### 内核（Core）——推理机能 · 思考

最纯粹的"思考"能力：把已经组织好的上下文转化为推理与生成。它没有人格、没有记忆、不知道自己服务于谁——如同神经元层面的推理，只对当下这一次输入负责。内核是无自我的，同一份思考能力可被任意组件在任意时刻借用。

- **认知循环（请求级）**：推理 → 生成 → 注意力（上下文窗口）管理
- **领域职责**：把调用方组装好的上下文转化为推理与生成结果
- **状态**：无

## 组件之间的逻辑关系

以助理为中心，其余组件是它的机能或伙伴。关系是领域意义上的"调用与归属"，不是技术调用链（关系全貌见上文「架构可视化」）。

各关系的领域含义：

| 关系 | 领域含义 |
|------|---------|
| 用户 → 助理 | 唯一交互入口；一个用户可拥有多个助理（1 用户 : N 助理） |
| 助理 → 认知基础 | 回忆与沉淀：感知时回忆相关记忆/知识，事后把新所得沉淀回去 |
| 助理 → 智能体 | 指派任务：按技能匹配把决定要做的事委派给行动机能执行（1 助理 : N 智能体） |
| 助理 → 群体 | 加入协作：面对超出自身的目标时，参与或牵头群体共事 |
| 智能体 → 内核 | 借助思考：执行过程中调用纯推理能力 |
| 智能体 ↔ 认知基础 | 水平协作（非上下级）：智能体无长期记忆，执行前取用所需记忆/知识，执行后把所得写回；记忆集中于认知基础，智能体只借不持 |
| 认知基础 ↔ 内核 | 认知基础为思考提供素材，是被取用的共享底座 |
| 群体 → 助理 | 群体由多个助理组成（1 群体 : N 助理）；协作的执行仍归各助理调度；跨系统时可与外部智能体协作 |
| 学习反哺（横切） | 各组件执行与交互的所得，经评估后异步沉淀回认知基础——贯穿各层的反哺通道，不专属某一层 |

> 交互边界：接入助理有两类通道——**面向人**的交互界面（AG-UI）与外部渠道（微信、钉钉、飞书等），以及**面向系统/智能体**的 A2A 协议（跨系统智能体互联，后期实现）。渠道适配与多端接入属**交互层（L5）**，不在本五层智能架构内——本文从"消息到达助理"起建模。

两条边界约束（领域层面）：

- **私有与共享分离**：助理的会话焦点是私有的；记忆、知识、价值观下沉到认知基础，供多主体共享。
- **状态归属分明**：数据级状态（记忆、知识、价值观）统一归认知基础（持久 · 共享），会话级状态（注意焦点、任务进展、对用户的理解）归助理（私有），内核与智能体不持有长期状态——无自我者不持久。

## 认知循环的分层

- **每层有且只有一个认知循环，且不跨层直接触发**——上层通过指派驱动下层，下层通过反馈回到上层。
- **渐进决策贯穿各层**：粒度越往上越偏"假设性分解、走一步看一步"，越往下越偏"明确步骤、可验证执行"；决策权随置信度在层间流动——高置信本层执行，低置信向上回报，必要时转交人类。

## 与人类认知心理模型的映射

五层的灵感直接来自人类认知心理学。这张映射表说明每个组件对应认知过程的哪一环，是理解整套领域模型的钥匙。

| 认知心理过程 | 对应组件/机能 | 领域含义 |
|-------------|--------------|---------|
| 刺激输入 | 用户与助理的交互边界 | 外界进入认知的入口 |
| 前注意（瞬时分流） | 助理 · 前注意分流 | 极快判断"要不要深想" |
| 感知（理解含义） | 助理 · 意图与情绪感知 | 听懂"对方想要什么、什么心情" |
| 注意力资源 | 助理 · 注意焦点 | 有限精力如何分配 |
| 识别（从记忆中匹配） | 认知基础 · 回忆 | 把当下与已知关联起来 |
| 工作记忆 | 智能体 · 执行期工作记忆 | 任务进行中临时持有的几件事 |
| 长期记忆 | 认知基础 · 记忆与知识 | 持久积淀的体验与常识 |
| 价值判断 | 认知基础 · 价值观 | 什么该做、什么不可逾越 |
| 决策与响应选择 | 助理 · 决策 | 选择路径、决定交给谁 |
| 响应执行 | 智能体 · 行动 | 真正动手完成 |
| 反馈学习 | 各组件 · 学习/记忆更新 | 把所得反哺回积淀 |
| 社会协作 | 群体 · 共事 | 多个"自我"协同解决复杂问题 |

## 深入：组件的内部概念

上文五个组件是顶层划分。其中助理与认知基础在领域上还可进一步拆解，另有几个横切概念需要点明。

### 助理的构成：人格 · 角色 · 记忆策略

助理不是单块，而由三个可组合的要素拼成，对应"我是谁 / 我会什么 / 我如何记事"：

- **人格（Actor）**：助理的"我是谁"——名字、性格、说话风格、形象。可独立复用，同一人格可搭配不同能力。
- **角色（Role）**：助理的"我会什么"——一组技能加一份工具白名单。可独立复用，同一角色可赋予不同人格。
- **记忆策略**：助理的"我如何记事"——决定它如何向认知基础回忆与沉淀（偏个人记忆、偏共享知识，或两者融合）。

助理还可**多分身并行**：面对可拆分的目标，主助理按不同角色分出多个"分身"并行处理，再由主身聚合；分身用完即销毁。

**多会话与分身的数量关系**：一个用户可同时开多个对话（1 用户 : N 对话）；每个对话对应一个主助理作为唯一协调者（1 对话 : 1 主助理）；主助理按需分出 0..N 个分身并行，各分身独立调度智能体执行，最后由主助理聚合（1 主助理 : 0..N 分身）。

### 系统内置助理

内容创作与客服均作为**系统内置 Assistant 定义**提供，而不是在框架中各维护一套专用运行时或硬编码 `HarnessAgent` 工厂。它们与用户自建助理走同一条 Assistant 用例和 `AgentExecutor` 执行链，只在预置人格、角色、技能、工具、知识和权限范围上不同。

| 内置助理 | 主要能力 | 记忆与知识边界 | 默认权限 |
|---|---|---|---|
| 内容创作助理 | 策划、撰写、润色、事实核查、多媒体生成 | 用户私有创作偏好与历史 + 用户授权知识库 | 可调用生成工具；发布、付费、删除等动作需确认 |
| 客服助理 | 产品咨询、故障排查、工单路由、转人工 | 公共产品知识 + 已登录用户的服务历史 | 默认只读；写操作与敏感查询需授权 |

采用两个独立定义，而不是让一个助理同时承担公共客服与私人创作：二者的身份、知识、权限和隐私边界不同。内置定义应支持管理员配置、用户复制和版本化升级；用户复制后的定义不被系统模板升级覆盖。

### 输入缓冲与执行期干预

两个相邻但不同的概念，都发生在助理处理任务的过程中，不可混为一谈：

- **输入缓冲**：助理执行任务期间，用户随时可追加输入。这些输入先被**接收并暂存**，既不丢弃也不打断当前执行——保证"说得进来"。
- **执行期干预**：对缓冲的追加输入做**分类与处置**，决定如何及何时响应：
  - 取消 → 立即中断当前执行
  - 修改 → 重新规划
  - 补充 → 作为上下文注入，继续执行
  - 无关 → 排队，待当前任务结束再处理

一句话：**输入缓冲负责"接得住"，执行期干预负责"分得清、接得对"**。

### 技能与工具

- **技能（Skill）**：粗粒度、任务级。是"哪类意图交给哪种处理"的路由规则——匹配到某类意图后，激活对应的处理方式与专属指引。
- **工具（Tool）**：细粒度、原子级。是一次具体动作的能力单元（查、写、算、调用外部服务）。

助理通过角色持有技能与工具白名单；智能体执行时按白名单调用工具。一句话：**技能决定走哪条路，工具决定路上用什么**。

### 认知基础的内容

认知基础积淀四类东西：

- **记忆**：发生过什么。分短期 / 长期 / 情景 / 程序化——最近的事、长久的事实、具体的经历、做事的套路。
- **知识**：世界是怎样的。可被多助理共享的客观资料与常识。
- **价值观**：什么不可逾越。行动前据此过滤的伦理与优先级约束。
- **决策依据**：自主决策时留下的"为什么这么选"（决策点、备选、理由、置信度），供事后异步审查。

### 默认长期记忆策略

助理默认启用长期记忆，但长期记忆的唯一真理源是 `Cognition`，而不是 Agent 运行时或工作区文件。默认规则如下：

- `Assistant` 默认按记忆策略回忆与沉淀；`Agent` 只在任务期间借用工作上下文，任务结束不保留长期记忆；`Core` 完全无记忆。
- 只沉淀稳定偏好、经确认事实、重要任务结果和经评估有效的程序经验；一次性指令、低可信推测和敏感凭据不得自动进入长期记忆。
- 已登录用户按用户/租户隔离个人记忆；匿名客服仅保留短期摘要或带 TTL 的访客记忆，登录后也必须经确认才能合并。
- 共享知识的写入必须经过评估或人工审核，不能把模型回答直接提升为公共知识。
- AgentScope `MEMORY.md` 默认关闭；如后续启用，只能是可删除、可从 Cognition 重建的工作区缓存。

### 运行模式：编排与自主

- **编排模式**：流程骨架由人或设计者预先定义，按既定步骤推进，可审计、可回退。
- **自主模式**：由助理依意图自主拆解、调度、聚合，灵活但需置信度门控约束。

常见形态是"编排骨架 + 节点内自主"：流程的进入/退出条件是确定的，节点内部如何完成由组件自主决定。

不同场景的典型选型：

| 场景 | 运行模式 | 主导组件 |
|------|---------|---------|
| 日常对话 / 问答 | 自主 | 助理直接回应 |
| 单一任务 | 自主 | 助理 → 智能体 |
| 多角色并行加速 | 自主 | 助理分出多个分身 |
| 固定业务流程 | 编排 | 流程骨架 → 智能体作为节点 |
| 对抗性验证 | 自主 | 群体（多助理互相质证） |
| 跨系统协作 | 编排 | 群体 + 外部智能体协作 |

### 置信度门控

决策权在组件间流动的闸门，按置信高低分三档：

- **高**：本层自动执行，结果暂存、异步通知。
- **中**：展示计划，等待确认后执行。
- **低**：暂停，说明原因，转交人类。

越往低档越靠近人类介入。（具体阈值属配置范畴，领域只规定"三档 + 越低越往人靠"。）

### 分层决策粒度

三层的决策风格各异，体现"越往上越发散、越往下越收敛"：

- **智能体（任务级）**：决策树展开——走一步看一步，按执行反馈决定下一步。
- **助理（会话级）**：意图漏斗收敛——先把模糊意图澄清、收窄，再决定如何调度。
- **群体（项目级）**：目标假设性分解——目标未必清晰，先假设性拆解，边推进边校正。

## 通用智能体产品控制面

> 本节把 [通用智能体迁移设计](../../../explanation/general-agent/general-agent-migration-design.md) 的产品要求落为五层架构的跨层契约。它不新增第六个智能层，也不改变 Assistant、Team、Agent、Cognition、Core 的职责。

### 助理能力契约与有效上下文

AssistantDefinition 除人格、角色和记忆策略外，还应形成面向用户和治理系统的**助理能力清单**，至少表达：稳定标识与版本、维护者、职责和非职责、支持的用户控制模式、可用业务动作、资料与记忆作用域、默认风险策略和生命周期状态。该清单是定义的只读投影，不是第二份配置源。

每次任务还应产生**有效上下文清单**，记录实际生效的系统/组织规则、Skill、任务资料、知识、个人记忆及其来源、版本、作用域和选用原因。用户可纠正、移除或禁止后续使用允许管理的来源；清单只保存引用和脱敏摘要，不复制知识或记忆正文。

### 用户控制模式

用户控制模式描述“用户授予多大自主权”，与内部“编排/自主”运行模式正交：

| 用户控制模式 | 外部副作用 | 人的参与方式 | 进入条件 |
|---|---|---|---|
| 问答 `READ_ONLY` | 禁止写入，只能生成建议或草稿内容 | 可随时追问和取消 | 默认模式 |
| 协作 `COLLABORATIVE` | 允许范围内的可撤销写入 | 展示计划，关键动作前确认 | 任务目标和影响可解释 |
| 委托 `DELEGATED` | 在任务授权内后台执行 | 异常、授权缺口或分歧时介入 | 预算、截止时间、停止条件和负责人齐全 |
| 自动化 `AUTOMATED` | 按已审核模板重复执行 | 通过试运行、变更审核和全局停用控制 | 模板、触发器、权限和失败策略已版本化 |

模式只能由用户或策略显式升级，不能由模型静默升级。模式降级、暂停和取消必须随时可用；内部可以在任一用户模式下选择编排或自主执行，但不得突破该模式的副作用边界。

### 任务生命周期与完成门禁

Assistant 层持有稳定任务生命周期，TaskBoard 管步骤与依赖，二者不可只靠聊天消息推断：

```text
DRAFT → PLANNING → AWAITING_AUTHORIZATION → RUNNING → VERIFYING → COMPLETED
                     ├→ AWAITING_INPUT ────────────────┤
                     ├→ PAUSED
                     ├→ CANCELED
                     └→ FAILED → RECOVERING 或终止
```

交互层可投影为“准备中、进行中、需要你、已完成、未完成”，但每次状态转换必须保留原因、发起者、时间、当前责任主体和恢复点。模型一轮输出结束不等于任务完成；从第一个可写任务开始就必须由 CompletionValidator 根据显式完成条件返回“完成、继续修复、需要用户、失败或转人工”。

人工接管是责任主体变更：先暂停 Agent 执行并释放执行权，再由人领取、修改或完成；交回时创建新的 execution 继续，不能让人与 Agent 同时写同一任务。

### 业务授权与连接器信任

工具治理保持三层边界：业务动作是否可见、当前任务是否获得授权、实际参数是否满足系统与组织策略。任务授权至少包含 `action`、`resource`、`scope`、`expiresAt`、`conditions`、`reversible`、`grantedBy` 和 `taskId`；授权可撤销，用户确认不能绕过系统或租户策略。

确认请求必须说明：做什么、为什么现在做、影响什么、使用哪些数据、如何撤销或补救。低风险同类动作可在明确范围内合并授权；高风险和不可逆动作仍逐项确认或走双重审批。

MCP 只负责连接协议。连接器目录、OAuth scope、凭证托管、刷新、过期和撤销属于服务/基础设施层；智能层只接收业务动作和凭证句柄，凭证正文不得进入模型上下文、AgentState 或事件 payload。

### 受控委托、自动化与协作

委托任务必须携带执行契约：预算、截止时间、最大调用次数、允许动作、停止条件、重试策略、通知策略、负责人和人工接管策略。后台任务由 AAF 持久调度/任务引擎承载，AgentStateStore 只恢复 Agent 工作态，不充当后台任务队列。

自动化是经试运行验证的版本化任务模板，必须定义触发条件、频率、权限、预算、失败处理、启停入口和升级影响；工作流引擎承载确定性骨架，Assistant 决定何时调用。Team 和子 Agent 可以作为内部实现，但主 Assistant 始终对用户解释委派原因、汇总冲突并承担最终责任。

## 支撑性领域能力

有些能力最终由技术机制承载，但**领域必须先讲清"在哪个组件、为什么需要"**——这里只描述能力与动因，不涉及实现构件。后续技术方案据此落地。

| 领域能力 | 归属组件 | 领域动因（为什么需要） |
|---------|---------|----------------------|
| 可复用执行 | 智能体 | 智能体无自我、借记忆于认知基础，故同一定义可被并发借用服务多个任务、用完即收 |
| 可恢复与回退 | 智能体（步骤）· 助理（会话）· 群体（目标） | 长任务可能中断或出错——进度需可暂存、可从最近断点续跑、未确认可回滚 |
| 输入缓冲 | 助理 | 助理执行任务期间，用户仍可追加输入；这些输入先被缓冲暂存，不丢弃、不打断当前执行 |
| 执行期干预 | 助理 | 对缓冲的追加输入分类处置：取消 → 中断、修改 → 重新规划、补充 → 注入上下文、无关 → 排队 |
| 子任务追踪 | 助理 | 复杂任务拆成多子任务，需跟踪各自状态与依赖（待办 / 进行 / 完成 / 失败） |
| 编排式回忆 | 认知基础 | "回忆"不是一次性全取，而是先理解所需 → 多源检索 → 融合排序 → 按需组装，避免噪声与过载 |
| 分区存储 | 认知基础 | 不同归属的内容须分置：个人私有 / 全局共享 / 执行工作区 / 审计留存——兼顾隐私、复用与可审计 |
| 快速装配 | 助理 | 人格 / 角色 / 技能等装配信息相对稳定且高频取用，需预先备好、低延迟可用 |
| 推理策略可选 | 智能体 · 内核 | 同一任务可按难度选不同推理方式（直接作答 / 先规划后执行 / 边想边做），简单的事不过度思考 |
| 执行隔离 | 智能体 | 调用工具、运行代码须在隔离环境中进行，限定可触达资源，防越权与副作用外溢 |
| 权限与风险分级 | 智能体 · 助理 | 动作按风险分级（无害 / 低 / 中 / 高）；高风险动作执行前需人工确认。与"能力护栏"正交——护栏限定可做的范围，分级管单次动作的放行 |
| 用户控制模式 | 助理 | 问答、协作、委托、自动化决定用户授予的自主权和副作用上限，不能由模型静默升级 |
| 任务生命周期与完成门禁 | 助理 | 任务状态、责任主体和完成条件必须独立于聊天回合，支持暂停、接管、恢复和验证失败后继续修复 |
| 有效上下文透明度 | 助理 · 认知基础 | 用户需知道本次实际使用了哪些规则、资料、知识和记忆，并能纠正或移除允许管理的来源 |
| 受控委托与自动化 | 助理 · 群体 | 后台执行和重复触发必须受预算、期限、停止条件、通知、版本和人工接管约束 |
| 执行轨迹（可观测） | 助理 · 智能体 | 执行过程产出可追溯的事件流（步骤、工具调用、决策点），支撑实时呈现与事后审查 |

> 这张表是领域与技术的交接点：左两列（能力 + 归属）属本章稳定的领域约定，右列动因解释"为何后续要做池化、可恢复、检索编排、配置预热等机制"——但具体怎么实现留给技术方案。

## 待解决的领域问题

领域层面尚未定论、留待演进的几个开放问题：

- **能力自进化**：工具与技能能否在使用中自我改进（描述、人体工学、组合方式），以及如何经过试运行和审核后晋升。
- **新型协作通信**：多助理协作如何突破"一问一答"的同步回合，支持更丰富的异步协同。

## 已确认的实现决策

> 本节是 v2 实现的约束性决策，优先级高于后续历史技术示例。后续示例若仍出现 `Session`、每会话 Agent 实例或工作区长期记忆等旧表述，均应按本节解释并在实现阶段清理。分阶段交付与验收见 [五层智能架构 v2 开发计划](architecture-v2-development-plan.md)。

### 内置助理物化

内容创作助理与客服助理以 PostgreSQL 中的系统内置 `AssistantDefinition` 存在，由通用 Assistant 应用服务加载并物化。框架只提供 Assistant、Agent、Tool contributor 端口；内容创作和客服的提示词、业务工具及知识配置属于产品能力，通过上层模块注册，不固化进 AgentScope 适配器。

用户入口以 `tenantId + assistantId + conversationId` 建立会话，以 `taskId + executionId` 标识任务及其执行；内置助理、自建助理和复制助理共享同一入口、权限检查、记忆策略、任务生命周期和事件流，不按助理类型注册不同执行端点。所有调用必须显式携带 tenant 上下文；当 AgentScope 状态接口只暴露 `(userId, sessionId)` 时，适配层必须使用稳定 tenant store prefix 或规范化 state user key 隔离租户，不能假设跨租户 userId 全局唯一。

### 包结构、适配边界与版本基线

保留根包 `com.xuejiai.aaf.framework.intelligent`，以 `assistant`、`team`、`agent`、`cognition`、`core` 为一级领域包。唯一允许直接依赖 AgentScope 的区域是 `intelligent.infrastructure.agentscope`。领域与应用端口不得暴露 `HarnessAgent`、`ReActAgent`、`RuntimeContext`、`AgentState` 或 Spring/JPA 类型。

```text
com.xuejiai.aaf.framework.intelligent
├── assistant/{model,application,port}
├── team/{model,application,port}
├── agent/{model,application,port}
├── cognition/{model,application,port}
├── core/{inference,capability,port}
├── shared/{id,event}
└── infrastructure/agentscope
    ├── execution
    ├── compiler
    ├── mapping
    ├── middleware
    ├── tool
    ├── state
    ├── sandbox
    └── spring
```

JPA Entity、Spring Data Repository 与 `JdbcTemplate` 实现放在纯领域包之外，通过端口注入。现有并列包 `com.xuejiai.aaf.framework.agentscope` 只作为 PoC 和迁移素材，不作为目标包结构；顶层 `action`、`ai` 的责任最终收敛到五层契约或基础设施实现。

当前 AAF BOM 锁定 `AgentScope 2.0.0-RC4`，而本地 `tmp/agentscope-java` 源码是 `2.0.1-SNAPSHOT`。进入编码前必须选择并锁定同一基线：要么取得 RC4 对应源码并以 RC4 契约测试为准，要么单独评审依赖升级。不得依据 Snapshot API 直接实现 RC4 代码。

### 长期记忆与运行状态

长期记忆默认开启且唯一归 `Cognition`；AgentScope 只保存可丢弃、可恢复的执行工作态。状态所有权固定如下：

| 数据 | 唯一真理源 | AgentScope 中的角色 |
|---|---|---|
| 助理/Agent/角色/技能/模型配置 | PostgreSQL | Builder 编译输入 |
| 用户可见对话消息 | `conversation_message` | `AgentState.context` 是可压缩工作集 |
| TaskBoard/Goal/依赖/编排状态 | PostgreSQL | 不重复维护 |
| Agent 执行快照 | Redis `AgentStateStore` | 自动加载和保存 |
| 长期记忆、知识、价值观 | Cognition 存储 | 每轮按需注入 |
| HITL 当前状态 | PostgreSQL approval 状态 | 运行时负责暂停与恢复 |
| Token 与结算账本 | PostgreSQL | Middleware 上报 |

同一次事实只允许一个写入者。工作区 `MEMORY.md`、AgentState 和业务数据库不得分别维护可独立修改的长期记忆或 TaskBoard。

### 执行轨迹

`ai_task_event` 是 Assistant/Agent 执行轨迹的唯一 append-only 事实源，承载任务状态、控制模式、模型、工具、授权、审批、接管、子任务、验证、取消与恢复事件，并作为 SSE 的持久来源。事件至少携带 `eventId`、`tenantId`、`taskId`、`executionId`、`runId`、`parentExecutionId`、`sequence`、`type`、`status`、`controlMode`、`ownerType`、`assistantId`、`agentId`、`userId`、`correlationId`、`causationId`、脱敏 `payload` 和 `createdAt`。

至少建立 `UNIQUE(event_id)` 与 `UNIQUE(execution_id, sequence)` 约束。`ai_execution_run` / `ai_execution_step` 不再作为第二套事实源；若管理端确需步骤树查询，只能由 `ai_task_event` 异步生成可删除、可重建的读模型，禁止同步双写。模型计量、额度账本和 HITL 当前状态仍使用各自业务表，事件只记录其发生过程。

### 多副本一致性

多副本是指同一 AAF 服务同时运行多个 JVM/Pod，并由负载均衡把请求路由到任一副本。Redis `AgentStateStore` 解决跨副本恢复，但不自动保证两个副本不会同时处理同一会话。

生产环境必须在调用 Harness 前按 conversation/session 获取分布式 lease，并携带单调递增的 fencing token；只有仍持有最新 token 的执行者可写回状态。规则是同一 conversation 串行、不同 conversation 并行，外部副作用工具仍须使用幂等键。粘性路由只能作为优化，不能作为唯一正确性保证。

```text
收到消息
→ 获取 conversation lease + fencing token
→ 加载编排态与 AgentState
→ HarnessAgent 执行
→ 校验 fencing token
→ 幂等写消息、事件、计量和状态
→ 释放 lease
```

## 技术承载参考：AgentScope Harness

> 本节是**技术参考**（非领域内容），用于说明本领域模型可由什么技术承载，呼应"技术实现可替换"的定位。

AgentScope 的 `HarnessAgent` 是在裸推理循环（`ReActAgent`）之上的一层薄包装，把"长期运行的智能体"所需的工程能力打包进一个构建器：工作区驱动的人格、会话持久化、长期记忆与对话压缩、子智能体编排、沙箱隔离、技能装配、计划模式、工具白名单。它的核心理念与本模型高度同构——**能力叠加在推理循环的关键时机上、彼此不依赖、只通过共享上下文通信**，正好对应本文「支撑性领域能力」的思路。

本模型的领域概念可大致映射到 Harness 的原生能力：

| v2 领域概念 / 支撑能力 | Harness 对应能力 |
|---|---|
| 人格（Actor）· 工作区驱动 | 工作区驱动的人格（`AGENTS.md`） |
| 会话持久化 · 可恢复（会话级） | 会话持久化（同 `sessionId` 跨请求/进程/副本恢复） |
| 沉淀（写入记忆）· 编排式回忆 | 双层长期记忆（`MEMORY.md`）+ 对话压缩 |
| 多分身 · 群体协作（部分） | 子智能体编排（同步/后台 + 反向通知） |
| 执行隔离（沙箱） | 可插拔文件系统 + 沙箱隔离 |
| 渐进决策（只读思考 + HITL） | 计划模式（只读阶段 + HITL 退出） |
| 技能装配 | 技能装配（多来源合成 + 自学习闭环） |
| 工具白名单 · 能力护栏 | 工具白名单 + MCP 集成 |
| 多用户隔离（私有/共享分离） | 运行上下文（`userId`/`sessionId`）+ 隔离作用域 |

**承载边界**：Harness 主要承载**智能体运行时与单体助理**（Layer 2 + 部分 Layer 3）。本模型的**认知基础**（向量 + 图谱 + 价值观 + 决策日志）、**助理**的前注意分流/情感感知/技能路由、**群体**协作、以及交互层渠道，仍由 AAF 自身实现，或通过 Harness 的扩展点（自定义会话 / 文件系统 / middleware）注入。换言之：Harness 是 Agent 级运行时的有力候选，但不替代整套五层智能架构。

## HarnessAgent 核心概念与运行时

> 基于 agentscope-java 源码（`HarnessAgent.java`、`harness/context.md`、`harness/architecture.md`）与示例（`agentscope-dataagent`）整理。**领域映射与承载边界见前文「技术承载参考：AgentScope Harness」，此处不重复**，只讲它"是什么"和"怎么跑"。

### 核心概念

- **薄包装、能力叠加而非改写循环**：`HarnessAgent` 组合一个 `ReActAgent`，通过 middleware 叠加工作区、压缩、沙箱、技能、MCP、计划模式和子 Agent 能力。
- **RuntimeContext 是 per-call 上下文**：每次调用显式传入 `userId`、`sessionId` 和 AAF typed context；自由属性不持久化，禁止用 ThreadLocal 或全局 fallback 代替。
- **AgentState 是执行工作态**：对话工作集、摘要、权限、Plan、todo 和工具状态按 `(userId, sessionId)` 隔离。
- **AgentStateStore 负责跨调用恢复**：调用入口加载、调用结束保存；开发可用内存/文件实现，生产使用 Redis 等分布式实现。
- **工作区不是业务真理源**：文件系统和沙箱承载执行资源，DB 配置、Cognition 长期记忆和 AAF 编排态不得反向由工作区定义。

### 运行时：一次 call 的流转

1. AAF `AgentExecutor` 接收纯领域 `AgentExecutionCommand` 与显式 `InvocationContext`。
2. AgentScope 适配器按定义版本取得无状态 `HarnessAgent`，将 AAF 上下文映射为新的 `RuntimeContext`。
3. `ReActAgent` 从 `AgentStateStore` 加载当前状态并执行 middleware、ReAct 与工具循环。
4. AgentScope 事件映射为稳定的 AAF `ExecutionEvent`，由应用层持久化到 `ai_task_event` 并向交互层发布。
5. 调用结束自动保存 `AgentState`；AAF 的 CompletionValidator 独立判断业务任务是完成、继续修复、暂停还是转人工。

### 实例模型：无状态引擎与会话状态分离

目标 v2 运行时采用无状态 Agent 引擎：`HarnessAgent` 只持不可变配置，可按 `definitionId + definitionVersion` 缓存为共享实例；所有会话可变状态都在 `AgentState`，每次调用由 `RuntimeContext` 选择 `(userId, sessionId)` 槽位。

同一实例可并发服务不同会话；同一槽位在单进程内由 AgentScope 串行。多副本下仍必须由 AAF 的分布式 lease/fencing 保证全局串行。定义更新通过版本化缓存失效，不通过“每用户一个 Agent 注册表”解决。

## 领域与实现的对齐原则

> 决策参考。指导"领域模型 ↔ AgentScope 实现"如何对应，为后续选型 / 迁移 ADR 提供依据。

核心立场：**领域模型由问题域与产品价值驱动，不由框架驱动**；实现可替换、领域应稳定。因此对 AgentScope 采取**保留为主、选择性对齐、明确划界**。

### 对齐（同构概念，统一命名/语义/边界，让映射层薄）

| 领域概念 | AgentScope 对应 |
|---|---|
| 会话 / 会话级状态 | `sessionId` ↔ `AgentState` |
| 工作记忆 | AgentState 调用内状态 |
| 执行隔离 / 沙箱 | sandbox |
| 技能 / 工具 | Skill / Tool（白名单 + MCP） |
| 计划模式 / 渐进决策（只读 + HITL） | Plan Mode + HITL |
| 子任务 / 多分身 | 子 agent 编排 |
| 智能体无自我 · 借记忆 | 无状态 + Session 装卸（**已天然对齐**） |

### 保留（我们的差异化，框架没有或更弱，不为对齐而调整）

- 五层结构本身
- 助理高阶认知：人格(Actor) + 角色(Role) 分离、前注意分流、情感感知、技能路由、多分身、输入缓冲 / 执行期干预
- 认知基础：向量 + 图谱 + 价值观 + 决策日志 + 混合检索（远比文件记忆 `MEMORY.md` 丰富）
- 群体（Leader/Worker、目标级跟踪）、编排 vs 自主、交互层

### 划界（必须明确边界，防双真理源 / 反向耦合）

- **记忆**：认知基础是唯一真理源；HarnessAgent 的 `MEMORY.md` 默认关闭；如启用，只能作为可从 Cognition 重建的缓存。**禁双真理源**。
- **配置**：DB 驱动（agent / persona / role）是源，用 AAF builder **编译**成 HarnessAgent；不反向让文件配置成为源。

### 可零成本吸收的两个运行时刻画

它们不是迁就框架，而是本就正确、恰与框架一致的领域刻画：

- **会话即状态边界**（`sessionId ↔ AgentState`）：强化"会话级状态归助理"。
- **执行身份 per-call 注入**（RuntimeContext）：强化"智能体无自我、身份与状态外置"，让"可复用执行"更精确。

### 结论

保留领域模型 + 选择性对齐重合概念 + 记忆/配置明确划界。实现弥合靠 `AgentExecutor` 抽象 + 适配层（DB→builder 编译、混合检索→`MemoryContext` 注入），而非改领域——既低阻抗好实现，又不被 RC 框架绑架。


## 运行时设计（结合 AgentScope）

> 技术设计参考（非领域内容）。把本领域模型落到运行时，并与 AgentScope HarnessAgent 衔接。核心：**两层运行时，各管一段**——AAF 管编排，HarnessAgent 管 Agent 执行。

| 运行时 | 归属 | 管什么 |
|---|---|---|
| 编排层（围绕助理 / Agent） | AAF 自管 | 前注意分流/路由、记忆策略选择、多分身编排、TaskBoard / GoalTracker、编排态 Checkpoint(DB)、缓存层 |
| 执行层（Agent 执行） | HarnessAgent 承载 | 无状态执行引擎；AgentStateStore、沙箱、子 agent、压缩、Plan/HITL、工具执行 |

关键：**Assistant 是 AAF 领域主体，不是 HarnessAgent 的别名**。AAF 应用层负责前注意、路由、TaskBoard 和多分身；需要推理时经 `AgentExecutor` 调用 AgentScope 适配器。记忆、计量、权限和轨迹通过稳定 AAF 端口与 middleware 衔接。

### 缓存层：DB 配置作为"编译源"

DB 配置（Actor / Role / SkillDef / AgentDef / Model）→ 本地缓存（+Redis 二级，变更事件刷新）→ **编译成 `HarnessAgent.builder()`**（name / sysPrompt / model / skills / tools / workspace）。HarnessAgent 本是 builder 驱动，缓存配置充当编译源，**DB 仍是真理源**。Actor / Role 纯配置不池化。

### 实例与状态生命周期

- **共享 Agent 引擎**：按 `definitionId + definitionVersion` 缓存无状态 `HarnessAgent`；模型客户端、工具模板、AgentStateStore、沙箱池等重资源由 Spring 单例管理。
- **会话状态槽位**：每个 conversation 对应稳定 `sessionId`；生产调用必须提供非空 `userId`，由 Redis `AgentStateStore` 按 `(userId, sessionId)` 隔离。
- **任务型 Agent**：每个 task/subtask 使用独立 executionId 和 sessionId；任务结束后可删除执行工作态，但结果、事件和学习候选已经进入 AAF 真理源。
- **定义刷新**：配置 DB 是源；变更产生新 definition version 并使编译缓存失效，不修改正在执行的旧版本实例。
- **资源回收**：关闭的是 Agent 编译缓存项、沙箱租约和临时工作区；会话恢复依赖 AgentStateStore，而不是保留 Java 对象。

Assistant 并不等于一个永驻的有状态 Agent 对象。Assistant 是 AAF 领域主体；AgentScope `HarnessAgent` 是可共享、可替换的执行引擎，两者通过 `AgentExecutor` 端口连接。
### 场景会话流程

每次用户消息先过助理的**决策前路**：前注意分流（规则/小模型快速判断，简单的就地短路）→ 情绪/意图理解 → 技能匹配 + 置信度评估 → 选处理路径。不同场景走不同路径：

**闲聊 / 简单问答（自主 · 直接回复）**
前注意判定无需深想 → 助理直接生成回复，不 spawn 任何子 agent。决策：低复杂度、高置信 → 本层自动执行。

**单一任务（自主 · 助理 → 单 Agent）**
意图理解 → 技能匹配到某 `agentId` → 助理 spawn 一个任务型子 agent 执行 → 结果整合回复。决策：明确单一目标 → 委派一个 Agent。

**复杂任务（自主 · 助理协调多 Agent）——复杂任务如何协调**

```text
用户消息
  → 前注意分流（简单？→ 直接回复 ｜ 复杂？↓）
  → 意图理解 + 技能匹配 + 置信度
  → 规划：拆为可验证子任务 → 写入 TaskBoard（状态 + 依赖）
  → 并行 spawn 子 agent：[后端] [前端] …（各自隔离沙箱执行）
  → 子 agent 完成 → 反向通知 → TaskBoard 更新
  → 助理聚合 + 验证 →（置信不足？→ 置信度门控转人）→ 统一回复
  ↑ 执行期：用户追加输入 → 输入缓冲 → 分类干预（取消/修改/补充/无关）
```

要点：助理是**协调者**——它负责拆解、派发、跟踪、聚合、仲裁，但不亲自执行子任务；可并行的子任务用多分身（同人格、不同角色）并行，串行依赖按 TaskBoard 依赖关系调度。

**固定业务流程（编排 · 助理调用 AI 工作流工具）——助理通过工具调用执行 AI 工作流**
预定义的确定性流程（如"需求→设计→编码→评审"的 AI 编排）被**封装成一个工具/技能**。助理在推理中以**工具调用（function calling）**触发它 → 工作流引擎按既定节点（LLM 节点 / 知识库节点 / 条件分支等）执行 → 结果返回助理整合。决策：流程确定 → 用编排而非自主，助理只管"何时调用、如何用结果"，流程骨架交给工作流引擎。这正是「编排骨架 + 节点内自主」的落地——工作流是骨架（工具），节点内仍可自主调 LLM/Agent。

**对抗性验证 / 跨系统（自主 · 群体）**
需要多视角质证或跨系统协作 → 升级到群体：多助理（或经 A2A 的外部智能体）协作，主导助理对齐与仲裁。

### 编排模式支持

编排模式 = **确定性流程骨架驱动、节点上调执行单元**。本方案分工清晰、天然支持：

| 编排要素 | 谁承载 |
|---|---|
| 流程骨架（节点 / 分支 / 进入退出条件） | AAF 工作流引擎（Flowable / DSL / flow-editor） |
| 节点 = 调一个 Agent/Assistant | `AgentExecutor`（= `HarnessAgentExecutor`） |
| 节点内执行 | HarnessAgent（ReAct + 可自主 spawn 子 agent） |
| Team 级编排（多 Assistant） | AAF Team 层（Pipeline / Supervisor） |

- **HarnessAgent 不感知编排**：它只是被工作流引擎在某节点调用、执行完返回。编排是 AAF 编排层（工作流引擎 + Team）的职责，HarnessAgent 天然适配、无需改动。
- **混合模式（编排骨架 + 节点内自主）**：进入/退出条件由工作流引擎确定；节点内 HarnessAgent 自主 ReAct / 委派；节点内受预算/超时 middleware 约束；Agent 发现超出能力范围 → 返回信号让工作流分支或转人（HITL）。
- **双向**：编排 → 调 Agent（工作流节点调 HarnessAgent）；Agent → 调工作流（HarnessAgent 用 function calling 把工作流当工具，见上「固定业务流程」）。
- **三正交维度全覆盖**：运行模式（编排/自主，可混）· 编排对象（Team→Assistant→Agent）· 执行模式（HarnessAgent 核心即 ReAct + function calling，CoT 为节点内 prompt 风格）。

> 边界：**HarnessAgent 本身不提供工作流引擎**——它的 Plan Mode / subagent 只是 agent 级的轻量规划/委派。企业级编排骨架、Team Supervisor、DSL/可视化编辑器是 **AAF 自研**（基于 Flowable）；HarnessAgent 只作被编排的节点执行单元。

### 并发、性能与多副本

- 不同 `(userId, sessionId)` 可并行，容量瓶颈主要来自模型额度、工具、沙箱和 Redis，而不是 Agent Java 对象数量。
- 同一会话在单 JVM 内使用 AgentScope 的槽位串行；跨 JVM/Pod 使用 AAF `ConversationLeasePort` 的 Redis lease + fencing token。
- 子任务并行必须使用独立 execution/session key，并受全局、用户和会话三级并发与预算限制。
- 任何会产生外部副作用的工具都必须接受稳定 idempotency key；锁失效、重试或恢复不得造成重复发布、重复付款或重复通知。
- 本地缓存和路由表只作性能优化，删除后必须能从 PostgreSQL、Redis AgentStateStore 和 `ai_task_event` 重建。

### 名词解释

- **AgentState**：单个 `(userId, sessionId)` 的可恢复执行工作态，不是长期记忆或业务任务真理源。
- **AgentStateStore**：AgentState 的存储端口；生产使用 Redis 实现，负责恢复而不负责跨副本互斥。
- **RuntimeContext**：单次调用的身份和扩展上下文，不持久化。
- **会话 lease**：保证同一 conversation 同时只有一个有效执行者的分布式租约。
- **fencing token**：随每次 lease 获取递增的写入代次；存储拒绝旧代次执行者的迟到写入。
### 任务管理与 Checkpoint：不双存，按归属分

- **编排态 → AAF 存 DB**：GoalTracker / TaskBoard / SubTaskContext / fork 树 → 编排 checkpoint。
- **Agent 执行工作态 → Redis AgentStateStore**：AgentState（上下文工作集/权限/plan/todo/工具状态）由 AgentScope 自动加载和保存；AAF 不为同一执行快照另做 checkpoint。
- **恢复缝合**：重启 → AAF 扫 DB 编排 checkpoint 恢复主助理/TaskBoard → 每个 RUNNING 子任务按其 sessionId 让 HarnessAgent 从 Redis 自动恢复 AgentState → 续跑。编排态 AAF 管、执行态 Session 管，**按 sessionId 缝合**。

### 技能与工具：DB 定义编译进 HarnessAgent

Role 的 Skill 集 → skillRepositories；Tool 白名单 → tools.json / 工具过滤；MCP server → HarnessAgent MCP 集成。高风险动作经权限 middleware HITL。Skill / Tool 真理源在 DB，HarnessAgent 配置是编译产物，不反向为源。

### 记忆衔接：认知基础为真理源

Agent 不走 HarnessAgent 的 `MEMORY.md` 自管：每轮由 AAF `MemoryContext` middleware 注入混合检索结果；新事实经学习反哺写回认知基础（DB + 图谱）。HarnessAgent 的工作区长期记忆默认关闭；如启用只能是 Cognition 的可重建缓存。写回仍须经过候选提取、隐私/可信度评估、去重与冲突检测。

### 运行时全景

```text
用户 → 交互层（AG-UI / 微信 / A2A）
        │
   ┌────▼─────────────────────────────────────────────┐  AAF 编排运行时
   │ 主助理 Assistant（前注意 / 路由 / TaskBoard / 输入缓冲）│  状态 → DB checkpoint
   │   └ fork 分身(Role) ……并行，用完销毁                 │
   └────┬──────────────────────────────────────────────┘
        │ AgentExecutor.execute(agentId, RuntimeContext{sessionId,userId})
   ┌────▼──────────────────────────────┐  适配层
   │ HarnessAgentExecutor              │  ← 缓存配置编译 builder
   │  + middleware：记忆注入/计量/权限/轨迹 │
   └────┬──────────────────────────────┘
        │
   ┌────▼────────────────────────────────────────┐  HarnessAgent 执行运行时
   │ HarnessAgent（按定义版本共享的无状态实例）      │  AgentState → Redis AgentStateStore
   │  ReAct 循环 · 沙箱(per-user) · 子 agent · 压缩 · Plan/HITL │
   └────┬────────────────────────────────────────┘
        │ 记忆注入 ↑ / 反哺写回 ↓（真理源）
   ┌────▼────────────────────────────────────────┐
   │ 认知基础 Cognition（向量 + 图谱 + 价值观 + 决策日志）│
   └─────────────────────────────────────────────┘
```

### 衔接点清单（适配层）

- `AgentExecutor` ← `HarnessAgentExecutor`（上层只依赖接口）
- builder 编译器：DB 配置（缓存）→ `HarnessAgent.builder()`
- middleware 注入：MemoryContext（混合检索）/ TokenMetering（计量）/ Permission-Risk（HITL）/ Trace（执行轨迹）——现有 hook 迁到 v2 middleware
- State：Redis `AgentStateStore` 按 `(userId, sessionId)` 隔离；AAF lease/fencing 保证跨副本串行
- Sandbox：per-user（`DockerFilesystemSpec` + `IsolationScope.USER`）

## 复杂任务全流程实现（结合 AgentScope 运行时）

> 技术可行性参考。把上文「场景会话流程」的复杂任务，逐步映射到 AgentScope HarnessAgent 的真实能力，验证可实现性。代码为示意（API 形状取自 `HarnessAgent` builder 与 harness 扩展），落地以实际版本签名为准。

**结论：能实现。** 大部分步骤是 HarnessAgent 原生能力；AAF 差异化（记忆/知识真理源、DB 配置、计量）经 builder 编译 + middleware 注入（官方扩展点）实现。

| 流程步骤 | AgentScope 承载 | 归属 |
|---|---|---|
| 编译 Agent 执行引擎 | `HarnessAgent.builder()` + Redis `AgentStateStore` + per-user 沙箱 | 原生 + AAF 适配 |
| 加载角色/技能 | `skillRepository(...)`（多来源）+ `tools.json` 白名单 | 原生（DB 编译进去） |
| 注入记忆/知识库 | `MemoryContext` middleware 注入混合检索 / RAG 扩展 | AAF 注入（Cognition 真理源） |
| 工具调用 | toolkit + `tools.json` + MCP；子 agent 亦作为可调用单元 | 原生 |
| HITL | Plan Mode + 权限确认 + 置信度门控 | 原生 + AAF middleware |
| 自学习改进 | SkillCurator → 草稿技能 → 晋升闸门；学习反哺写回 Cognition | 原生（技能）+ AAF（反哺 Cognition） |

### 创建会话型助理

```java
HarnessAgent assistant = HarnessAgent.builder()
    .name(actor.name())                 // 人格(Actor)：name/persona/avatar
    .sysPrompt(actor.systemPrompt())    // 人格的系统提示
    .model(resolveModel(assistant))     // 助理对话主模型：assistant.model_id，缺省走 CapabilityRouter
    .stateStore(redisAgentStateStore)   // 多副本共享、跨进程恢复
    .filesystem(new DockerFilesystemSpec()
        .isolationScope(IsolationScope.USER))   // per-user 沙箱隔离
    .compaction(CompactionConfig.builder()      // 上下文有界
        .triggerMessages(30).keepMessages(10).build())
    .skillRepositories(role.skillRepositories())// 角色的技能集（见下）
    .subagent(subagentSpecs)                    // 可 spawn 的任务型子 agent
    .enablePlanMode()                           // HITL：只读规划阶段
    .middleware(memoryCtxMw, meteringMw, permissionMw, traceMw) // AAF 注入
    .build();
// 每次调用按会话装卸状态
assistant.call(msgs, RuntimeContext.builder()
    .sessionId(conversationId).userId(userId).build());
```

DB 配置（Actor/Role/SkillDef/AgentDef）经缓存**编译**进这个 builder——DB 是真理源，builder 是编译产物。

### 加载角色与技能

- **角色(Role) → 技能集**：Role 配置的 Skill 列表编译成 `skillRepositories(...)`。HarnessAgent 支持多来源技能仓库（工作区 / Git / MySQL / classpath），可把 AAF 的 DB 技能仓接进来。
- **技能 = 渐进披露**：匹配到意图才激活对应技能与其工具，不一次性塞满上下文。
- **工具白名单**：Role 的 Tool 白名单编译成 `tools.json`（允许/拒绝），Agent 执行时按白名单调用。

### 注入记忆与知识库

- **记忆**：不依赖 HarnessAgent 的 `MEMORY.md` 自管，而是 `MemoryContext` middleware 在每轮推理前注入 AAF 混合检索（向量+图谱+价值观过滤）结果；新事实经学习反哺写回认知基础。可关闭其 MEMORY flush，或用自定义 Session/RemoteFilesystem 把记忆桥回 Cognition。
- **知识库**：两条路——① 接 AgentScope RAG 扩展（dify / ragflow / haystack / bailian / simple）；② 经同一 `MemoryContext` middleware 注入 AAF 知识库检索结果。**推荐 ②**，让 Cognition 作唯一真理源。

### 工具调用

- 工具来自 toolkit + `tools.json` + MCP（声明式 MCP server 发现）。
- **子 agent 也是一种"可调用单元"**：助理通过 spawn 子 agent（同步或后台）委派任务，后台任务完成后**反向通知**主助理。
- **AI 工作流作为工具**：把预定义 AI 编排封装成一个工具，助理用 function calling 触发，工作流引擎驱动其节点执行。

### HITL（人在环）

- **计划模式**：`enablePlanMode()` 进入只读思考阶段，产出计划后需**显式退出/确认**才进入执行——天然 HITL 关口。
- **高风险动作确认**：权限系统对标注高风险的工具 `require_confirm`，执行前暂停等人确认（AAF 权限/风险 middleware 落地）。
- **置信度门控**：低置信子结果 → 暂停转人；与计划模式、动作确认共同构成多级 HITL。

### 自学习改进

- **技能自进化**：执行轨迹经 `SkillUsageStore` 采集 → `SkillCurator` 提炼候选技能写入 `skills/_drafts/` → 经 `SkillPromotionGate`（如 `NotifyAndWaitGate` / `LocalApprovalGate`，仍是 HITL）审核 → `promoteSkill(...)` 晋升为正式技能。形成"用→提炼→审核→晋升"闭环。
- **认知反哺**：执行与交互的所得经评估异步写回认知基础（记忆/知识/价值观），下次推理经 `MemoryContext` 注入——这是 AAF 侧的学习闭环，与技能自进化互补。

### 复杂任务端到端（带 AgentScope 机制标注）

```text
用户消息（sessionId=对话, userId=用户）
  │  [AAF 前置] 前注意分流 → 意图/情绪 → 技能匹配 + 置信度
  ▼
助理 HarnessAgent.call(msgs, ctx)
  │  [实例] 按定义版本取得共享 HarnessAgent + 从 AgentStateStore 载入槽位状态
  │         （重资源：模型/工具模板/Session/沙箱池在启动时已建好、全局共享）
  │  [middleware] MemoryContext 注入混合检索（记忆+知识+价值观）
  │  [Plan Mode] 只读规划 → 拆为可验证子任务 → 写 TaskBoard(AAF/DB)
  │  └─（计划需人确认才退出 → HITL①）
  ▼
并行 spawn 任务型子 agent（后端 / 前端）
  │  [实例] 每个子任务创建一个短命子 agent 实例（独立 sessionId + per-user 沙箱）
  │  调用工具（tools.json 白名单 / MCP）→ 高风险动作 require_confirm（HITL②）
  │  子 agent 完成 → 反向通知主助理 → TaskBoard 更新 → [实例] 子 agent 销毁（结果落 task 仓+Session）
  ▼
助理聚合 + 验证
  │  低置信 → 置信度门控转人（HITL③）
  │  执行期用户追加输入 → 输入缓冲 → 分类干预（取消/修改/补充/无关）
  ▼
统一回复 → AgentState 落 Session(Redis，跨副本可恢复)
  │  [实例] 助理实例空闲超时后可被淘汰；状态留 Session，下次该会话再懒建恢复
  │
  └─[异步] 学习反哺：所得写回认知基础；SkillCurator 提炼候选技能（待晋升）
```

每个环节都有对应的 AgentScope 承载或既定扩展点，因此**复杂任务的完整链路在 HarnessAgent 上可实现**；AAF 只需提供 builder 编译器、四个注入 middleware（记忆/计量/权限/轨迹）、以及认知基础与编排态（TaskBoard/GoalTracker）的自有存储。



### 多智能体协作与子 agent 来源

**协调方式两种，可混用**：

- **自主协调**（HarnessAgent 原生）：给主 agent 一个"spawn 子 agent"工具（`AgentSpawnTool` + `SubagentsMiddleware`），LLM **自主决定**委派什么给哪个子 agent，框架管 spawn 生命周期、同步/后台、完成反向通知、结果并回上下文。只需**声明可用子 agent**，不写协调循环——但由 LLM 驱动、不确定。
- **编码编排**（我们写）：协调器/工作流创建多个 HarnessAgent 实例并精确编排（并行扇出 + TaskBoard 跟踪 + 聚合 + 仲裁）。官方 Harness/AgentStateStore 契约是实现基线；AAF 不复制示例中的 `SessionAgentManager` 或 `HarnessGateway`；AAF 的 Assistant 协调、Team（Leader/Worker）、A2A 跨系统属此类。确定性流程用编排、灵活探索用自主。

**外层编码编排 + 节点内自主**可叠加：你编码控制多个 HarnessAgent（可控、可审计），每个 HarnessAgent 节点内又可自主再 spawn 子 agent。

**子 agent 是什么**：声明 = 规格（`SubagentDeclaration`），运行时由 `SubagentFactory.create(parentRc)` 实例化为 **HarnessAgent 实例**（短命，有自己的子 `sessionId`/沙箱）。

**子 agent 的配置来源（对应 DB）——两类**：

| 类型 | 配置来源（DB） | 语义 | 运行时 |
|---|---|---|---|
| 任务型子 agent | `ai_agent_definition`（`ai_skill_definition.agent_id` 路由绑定） | 专门任务执行器，可被多助理复用 | HarnessAgent（短命） |
| 助理分身 | 同 `ai_persona`(Actor) + 另一个 `ai_role`(能力) | 同人格换能力的并行子身 | HarnessAgent（子会话） |

HarnessAgent 的 `subagents/` 声明 / `.subagent(spec)` ← 由我们的 builder 从上述 DB 配置**编译生成**（DB 是真理源，不走文件声明）。选型：**跨会话/跨助理复用的专家执行器 → `ai_agent_definition`；某次对话内临时并行的"另一个我" → persona + role 分身**。


---

## 数据架构（结合运行时设计）

> 技术设计参考。把 v1 数据架构迁移到 v2，并按运行时设计重新划分存储归属。核心变化：**Agent 执行工作态归 Redis AgentStateStore、不进业务 DB**；DB 只存配置、对话记录、编排态、认知数据与计量。实际表定义以 `vN__*.sql` 为准，本节为设计视角的精简描述。

### 存储分工（先定真理源）

| 数据 | 存储 | 真理源 |
|---|---|---|
| 配置（人格 / 角色 / 技能 / Agent 定义 / 模型） | PostgreSQL | DB |
| 对话记录（消息流、参与方） | PostgreSQL | DB |
| 编排态（GoalTracker / TaskBoard / 子任务 / 任务事件） | PostgreSQL | DB |
| 认知数据·结构化（记忆原子 / 知识分块 / 价值观 / 决策日志） | PostgreSQL | DB |
| 认知数据·向量 | PgVector | DB |
| 认知数据·图关系 | Neo4j（PG 为源，异步同步） | PG |
| **Agent 执行工作态（AgentState：上下文工作集 / Plan / todo / 工具 / 权限）** | **Redis** | **AgentStateStore** |
| 计量（Token / 额度） | PostgreSQL | DB |

一句话：**配置、认知数据、对话记录和编排态在 DB；Agent 执行工作态在 Redis AgentStateStore；向量在 PgVector、图在 Neo4j。**

### 与 v1 的关键差异（运行时驱动）

- **运行态出 DB**：v1 的 `ai_task_checkpoint` 收窄——**只存编排态**（TaskBoard / GoalTracker / fork 树 / SubTaskContext），不再存 agent/助理的对话级运行态（那是 `AgentState`，归 Session/Redis，由 HarnessAgent 在 call 结束自动落盘）。Agent 步骤级工作记忆同理在 `AgentState` 内，不进 DB。
- **对话历史双轨澄清（防双真理源）**：`conversation_message`（DB）= **对外可见、跨参与方、可审计/检索的对话真理源**；`AgentState.context`（Session）= **agent 推理用的工作上下文**（会被压缩/卸载，可重建）。两者职责不同、按 `sessionId` 关联，**不是双真理源**。消息由 `ChatPersistenceListener` 从 agent 事件落 DB。
- **记忆/知识真理源在认知基础**：HarnessAgent 的 `MEMORY.md` 默认关闭或仅作可重建缓存，**不建业务 DB 表**；长期记忆/知识在 `ai_memory_*` / `ai_knowledge_*`（+ PgVector / Neo4j）。
- **执行轨迹（可观测）**：复用 `ai_task_event`（append-only 事件流 + SSE），无需新表。
- **子 agent 来源**：任务型子 agent ← `ai_agent_definition`；助理分身 ← `ai_persona` + `ai_role`（见「多智能体协作与子 agent 来源」），无新表。

### 分层表清单（按 v2 组件归位）

- **内核 Core**：`ai_model_provider` · `ai_model` · `ai_model_preference` · `ai_prompt_template` · `ai_token_usage`
- **认知基础 Cognition**：`ai_memory_atom` · `ai_memory_relation` · `ai_knowledge_base` · `ai_knowledge_document` · `ai_knowledge_chunk` · `ai_knowledge_embedding` · `ai_value_rule` · `ai_decision_log`
- **智能体 Agent（配置）**：`ai_agent_definition` · `ai_tool_catalog` · `ai_action_catalog` · `ai_mcp_server`
- **助理 Assistant（配置 + 对话 + 编排态）**：
  - 配置：`ai_persona`(Actor) · `ai_role`(能力) · `ai_skill_definition` · `ai_assistant`
  - 对话：`conversation` · `conversation_participant` · `conversation_message`
  - 编排态：`ai_chat_task` · `ai_task_execution` · `ai_task_checkpoint`（仅编排态）· `ai_task_event`
- **群体 Team**：`ai_team` · `ai_team_member` · `ai_team_task`
- **Agent 执行工作态**：**无业务 DB 表**——生产存入 Redis AgentStateStore，按 `(userId, sessionId)` 隔离；不再规划 `MysqlSession` 或第二套业务 checkpoint。

### 核心配置表关系

配置装配链（FK 为主，串起"助理 → 角色 → 技能 → Agent → 模型/工具"）：

```text
用户 user_id
  │ 1:N
  ▼
ai_assistant ── persona_id ──▶ ai_persona             人格(Actor：我是谁)
  ├─ default_role_id ──▶ ai_role                      能力(Role：我会什么)
  │                       ├─ skill_ids(逻辑) ──▶ ai_skill_definition   技能(任务级路由)
  │                       └─ tool_whitelist(逻辑) ──▶ ai_tool_catalog  工具白名单
  ├─ model_id(可空，缺省走能力路由) ──▶ ai_model         对话主模型
  ├─ memory_strategy                                   记忆策略(如何用认知基础)
  └─ knowledge_base_id(逻辑) ──▶ ai_knowledge_base      绑定知识库

ai_skill_definition ── agent_id ──▶ ai_agent_definition   技能路由到的 Agent
                                     ├─ model_id ──▶ ai_model        绑定模型
                                     ├─ tools/allowed_tools ──▶ ai_tool_catalog
                                     └─ mcp_servers ──▶ ai_mcp_server
```

- **助理 = 人格 + 角色 + 记忆策略**：`ai_assistant.persona_id → ai_persona`、`default_role_id → ai_role`（真 FK），`memory_strategy` 为字段——对应领域「助理的构成」。
- **助理对话主模型**：`ai_assistant.model_id → ai_model`（FK，**可空**）。Assistant 经 `AgentExecutor` 调用按定义编译的 HarnessAgent，需要模型完成对话推理；为空时由 `CapabilityRouter` 按 `ai_model_preference`（USER/SYSTEM × capability）路由。模型是多级多用途的：前注意分流（小模型）/ 助理对话主模型 / Agent 任务模型（`ai_agent_definition.model_id`）/ 嵌入检索（capability=EMBEDDING），优先级链：**显式绑定 → 用户偏好 → 系统默认**，逐级降级。
- **角色 = 技能集 + 工具白名单**：`ai_role.skill_ids` / `tool_whitelist` 是列表，**逻辑引用** `ai_skill_definition` / `ai_tool_catalog`（非 FK，便于灵活组合）。
- **技能路由到 Agent**：`ai_skill_definition.agent_id → ai_agent_definition`（FK）——「Skill 决定把任务交给哪个 Agent」。
- **Agent 绑模型与工具**：`ai_agent_definition.model_id → ai_model`（FK）；`tools/allowed_tools → ai_tool_catalog`（**工具级**白名单，含 MCP 工具）；`mcp_servers → ai_mcp_server`（**服务级**，声明连接哪些 MCP 服务）。二者互补不冗余：`mcp_servers` 决定"连哪些服务（带来哪些工具）"，`allowed_tools` 决定"这些工具里允许哪几个"——对齐 HarnessAgent 的 `tools.json`（声明 MCP server + 工具 allow/deny）。`ai_mcp_server` 是连接配置真理源，**连接本身是全局共享重资源**（由 `McpConnectionService` 管，一服务一连接、所有 Agent 共用），Agent 只声明引用、不持有连接。（若所有 MCP 工具预注册进 `ai_tool_catalog` 且只做工具级白名单，`mcp_servers` 可省。）
- ⚠️ `ai_role.assistant_id ↔ ai_assistant.default_role_id` 是**循环 FK**（建表顺序处理）；role 是助理私有还是全局共享按需决定。

**认知基础不在配置链里**（共享底座，运行时按身份引用，不靠配置 FK）：

- 记忆：`ai_memory_atom.user_id` 按**用户私有**隔离，不绑助理/Agent。
- 知识库：`ai_assistant.knowledge_base_id`（逻辑）→ `ai_knowledge_base` →(1:N) `document` →(1:N) `chunk` →(1:N) `embedding`。
- 价值观：`ai_value_rule`（`scope = GLOBAL/TENANT`），执行前过滤。
- 决策日志：`ai_decision_log.scope_id`（逻辑）→ `ai_task_execution`。
- `memory_strategy` 决定助理如何用认知基础（偏记忆 / 偏知识 / 混合）。

小结：**配置链用 FK 装配出助理；认知基础按 `userId` / `knowledgeBaseId` / `scope` 在运行时被引用**——呼应"服务单例、数据按身份分区"。

### 会话与 sessionId 的对应

- `conversation.id`（或 `thread_id`）↔ AgentScope 的 `sessionId`；状态由 `(userId, sessionId)` 二元组隔离。
- 一次对话（`conversation`）= 一份 AgentState 工作槽位（存 Redis AgentStateStore）+ 一串用户可见消息（`conversation_message`，存 DB）。
- 编排态（TaskBoard 等）按 `conversation` / `ai_chat_task` 关联，存 DB；恢复时编排态从 DB、运行态从 Session，按 `sessionId` 缝合（见「任务管理与 Checkpoint」）。

### 待定（运行时新引入，需后续定表）

- **技能自进化**：`SkillCurator` 的候选技能（草稿）与晋升审核记录——可建 `ai_skill_draft` / `ai_skill_promotion_log`，或暂存 workspace `skills/_drafts/`（待 ADR 定）。
- **权限/风险分级**：`ai_action_catalog` 已有 `risk_level` / `require_confirm`，HITL 确认记录可挂 `ai_task_event` 或新建审计表（待定）。

### Neo4j 整合点

PostgreSQL 是 source of truth，Neo4j 承担**关系遍历 / 多跳 / 拓扑分析**（PG 写成功后异步 Spring Event 同步，幂等 MERGE）。

> 判断准则：**需要多跳遍历、路径/拓扑分析、关系为中心的查询，才上 Neo4j；单跳/过滤用 PG 的 JOIN / JSONB 足矣**——Neo4j 始终是 PG 的派生投影，不持有真理源，避免当成第二份业务库。

**已有 / 现有整合**：

| Neo4j 节点 + 关系 | 对应 PG | 桥接字段 | 用途 |
|---|---|---|---|
| `MemoryEntity` + `RELATES_TO` | `ai_memory_atom` + `ai_memory_relation` | `userId` | 实体关系遍历、时序图谱 |
| `KnowledgeEntity` + `RELATES_TO` | `ai_knowledge_chunk` / `ai_knowledge_document` | `sourceDocumentId` | 知识图谱多跳 |
| `AgentNode` + `INVOKED` | `ai_task_execution` | `agentId` | Agent 协作调用拓扑 |
| `AutodevDoc` + 引用 | `autodev_doc`（v4） | `docId` | 文档引用依赖图 |

**v2 运行时驱动的新结合点**（结合本文运行时设计）：

| Neo4j 图 | 对应 PG | 用途 | 状态 |
|---|---|---|---|
| 子 agent 委派拓扑 `(:Session)-[:SPAWNED]->(:Subagent)-[:FOR]->(:Task)` | `ai_task_execution.parent_execution_id` | 复杂任务的 spawn 树、并行与反向通知链路、可观测/回溯 | 规划（扩展 `AgentNode`） |
| 任务依赖图 `(:Task)-[:DEPENDS_ON]->(:Task)` | `ai_task_execution` / TaskBoard 依赖 | 子任务依赖的拓扑排序、阻塞分析 | 候选 |
| 技能路由图 `(:Assistant)-[:HAS_ROLE]->(:Role)-[:INCLUDES_SKILL]->(:Skill)-[:ROUTES_TO]->(:Agent)` | `ai_assistant` / `ai_role` / `ai_skill_definition` / `ai_agent_definition` | 能力可达性发现："哪个助理经哪条技能能调到哪个 Agent" | 规划（待 `skill_ids` 从 TEXT 关系化） |
| 决策链路图 `(:Task)-[:TRIGGERED]->(:Decision)-[:CHOSE]->(:Action)` | `ai_decision_log` + `ai_task_event` | 自主决策审计链的路径遍历 | 规划 |
| Team 目标分解树 `(:Team)-[:PURSUES]->(:Goal)-[:DECOMPOSED_INTO]->(:SubGoal)-[:ASSIGNED_TO]->(:Assistant)` | `ai_team_task.parent_task_id` | 群体目标分解、分派与进度的层级遍历 | 候选（Team 落地后） |
| 记忆 ↔ 知识交叉引用 `(:MemoryEntity)-[:REFERENCES]->(:KnowledgeEntity)` | 跨 `ai_memory_*` / `ai_knowledge_*` | 个体记忆与共享知识的关联检索（增强混合检索） | 规划 |
| 用户画像图 `(:User)-[:PREFERS]->(:Entity)` · `(:User)-[:HAS_TRAIT]->(:Trait)` | `ai_memory_atom`（画像类）/ Personalization | 偏好/情绪/关系画像遍历，供前注意分流与个性化 | 候选 |

> 「候选」项需先确认确有多跳/拓扑查询需求再落地；否则保持 PG（依赖关系用 TEXT/JSONB + JOIN 已够）。所有图均为 PG 派生投影，异步幂等同步。

## 计量与结算

AgentScope 路径统一使用 v2 `MiddlewareBase` 采集模型调用，不保留 v1 Hook、ThreadLocal 桥接或兜底计价双路径。

- `onAgent` 入口执行额度预检；`onModelCall` 从实际 `ModelCallInput.model()` 解析稳定 modelId，并记录输入/输出 token、缓存命中和多模态类型。
- 预检与结算使用同一 capability；文本为 `chat`，图像/视频/音频按已选模型能力分类，不使用笼统的 `agentscope` 类别。
- `ai_token_usage` 与结算账本是用量和金额的真理源；`ai_task_event` 只追加 `MODEL_CALL_STARTED/COMPLETED/FAILED` 轨迹并引用 usageId，不复制账本金额。
- billable 模型必须能映射到 `ai_model` 及其生效价格。映射缺失时停止结算并告警，不使用固定兜底单价，也不把 `model_id` 留空后继续正常链路。
- 重试必须复用 executionId、modelCallId 与幂等键，只有实际发生的模型调用计费一次；失败、取消、恢复和跨副本接管均须验证不会重复扣减。

验收至少覆盖文本、多模态、模型切换、模型映射缺失、模型超时、同一调用重放和 lease 过期接管；每条轨迹都能从 `ai_task_event` 关联到唯一用量记录和结算流水。

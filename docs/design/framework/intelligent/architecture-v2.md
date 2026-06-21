---
level: Practice
layer: Model
purpose: 五层智能架构 v2——以智能助理为核心、对齐认知心理模型的领域模型设计
status: draft
version: 5.0.0
date: 2026-06-10
author: AaronZZH
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
| 执行轨迹（可观测） | 助理 · 智能体 | 执行过程产出可追溯的事件流（步骤、工具调用、决策点），支撑实时呈现与事后审查 |

> 这张表是领域与技术的交接点：左两列（能力 + 归属）属本章稳定的领域约定，右列动因解释"为何后续要做池化、可恢复、检索编排、配置预热等机制"——但具体怎么实现留给技术方案。

## 待解决的领域问题

领域层面尚未定论、留待演进的几个开放问题：

- **预算意识**：如何让组件感知并遵守时间 / 花费 / 算力预算，在预算内自行取舍深浅。
- **能力自进化**：工具与技能能否在使用中自我改进（描述、人体工学、组合方式）。
- **新型协作通信**：多助理协作如何突破"一问一答"的同步回合，支持更丰富的异步协同。

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

- **薄包装、能力叠加而非改写循环**：`HarnessAgent` **组合**（非继承）一个 `ReActAgent`（`delegate`）。所有工程能力以 middleware/hook 形式挂在推理循环的关键时机上，core 的 ReAct 算法本身不动。内置 middleware 构建期按固定顺序串联，调用方用 `.middleware(...)` 加的跑在最前。
- **三个共享对象**（能力之间只通过它们通信，互不感知）：
  - **RuntimeContext**：本次 `call()` 的身份与元数据（`sessionId` / `userId` / `sessionKey` / sandbox 句柄 / extra）。**不持久化、不进 Session**。
  - **工作区（Workspace）**：谁读写哪些文件；物理落到本机 / 沙箱 / 远端由 `.filesystem(...)` 配置决定，须经 `getWorkspaceManager()` 路由（直接用 `java.nio.Files` 在沙箱/远端模式会写错地方）。
  - **Session**：跨调用如何恢复运行时状态。
- **状态三层**：
  - 调用内 **AgentState**：对话上下文、压缩摘要、权限规则、Plan Mode 状态、`todo` 清单、工具组激活状态。
  - 跨调用 **Session**：每次 `call()` 结束/进程关闭时把整份 `AgentState` 以 `agent_state` 键落盘，下次同 `sessionId` 自动载回。
  - 长期记忆 **MEMORY.md**：跨 session 累积，`memory/YYYY-MM-DD.md` 只追加，后台节流任务合并进 `MEMORY.md`，每轮推理注入 system prompt。
- **按需打开的能力**（builder 开关）：工作区驱动人格、会话持久化、双层长期记忆 + 对话压缩、大工具结果卸载、子智能体编排、可插拔文件系统、沙箱隔离、计划模式（HITL）、技能装配、MCP 集成 + 工具白名单。

### 运行时：一次 call 的流转

1. `call(msgs, ctx)` → `ensureSessionDefaults(ctx)`：缺 `sessionKey` 时按 `sessionId`（或 agent 名）补上，并注入默认 sandbox 上下文（Session 后端在 builder 期绑定，**不能 per-call 切换**）。
2. `wrappedCall`：用 `Mono.using` 在调用前 `acquireForCall` 沙箱、调用后 `releaseForCall`；若配了压缩，捕获 `context_length_exceeded` 触发 `recoverFromOverflow`（强制极限压缩后自动重试一次）。
3. `delegate.call(msgs, effective)`：走 ReAct 循环，各 middleware 就地改写内存里的 `AgentState`。
4. call 结束 / shutdown：`shutdownManager` 把 `AgentState` 整体写回 Session（不在每条消息后落盘，吞吐压力低）。
- **跨进程/跨机恢复**：Session 换成分布式后端（如 `RedisSession`）后，任意副本按 `sessionId` 续上同一份 `AgentState`——故障转移、滚动发布、Web↔CLI 接续，对话都不断。
- **压缩链路**（默认全关，按需开）：对话摘要压缩 / 大工具结果卸载（>80K 字符落盘留占位）/ 溢出兜底 / 预压缩参数截断；压缩永远先于落盘，Session 拿到的是压缩后版本。

### 实例模型：一个实例支持多用户，还是每对话一个？

- **设计意图：一个实例可（串行）服务多用户/多会话。** 身份每次 `call()` 经 RuntimeContext 传入，`AgentState` 按 `sessionId` 经 Session 装卸。文档的多用户示例即**同一个 agent** 先后用 alice、bob 的 ctx 调用，状态与文件命名空间互不干扰。
- **并发约束**：单个实例同一时刻只持有一份活跃 `AgentState`（`getAgentState()` 即 `delegate` 的实例字段）。源码注释明确：普通路径会改写共享状态、**并发不安全**，`workspaceFor(userId, sessionId)` 才是"不碰共享状态、可并发"的旁路。⇒ **同一实例对不同会话的调用必须串行**。
- **多用户隔离的三把钥匙**：`sessionId`（区分对话 → 独立 AgentState）、`userId`（区分文件/沙箱命名空间）、`SessionKey`（生产建议把 `userId` 编进键，配 `RedisSession` 做 AgentState 级隔离）。
- **生产范式（dataagent 示例印证）**：
  - **共享实例 + 每会话锁**：一个 `agentId` 一个共享实例，每个会话一把锁（`SessionTurnGate`）串行其轮次；不同会话靠锁 + 按 `sessionId` 装卸状态 + 按 `userId` 分沙箱隔离；跨副本用分布式 Session。
  - **每会话/子任务一个实例**：按 `sessionKey` 懒建并缓存独立实例（子 agent 路径的 `agentCache`），各自独立 AgentState。
- **结论**：既不是"每对话必须 new 一个实例"，也不是"一个实例能无锁并发服务多人"；而是**一个实例可串行服务多会话/多用户**，隔离靠 `RuntimeContext + Session(按 sessionId) + 沙箱(按 userId)`，并发靠每会话加锁或每会话独立实例。

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

- **记忆**：认知基础是唯一真理源；HarnessAgent 的 `MEMORY.md` 文件记忆退为"会话工作层 / 缓存"，或经自定义文件系统桥回我们的存储。**禁双真理源**。
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
| 执行层（助理 + Agent 本体） | HarnessAgent 承载 | **会话型助理 + 任务型子 agent**；AgentState / Session(Redis)、沙箱、子 agent、压缩、Plan/HITL、工具执行 |

关键：**助理本体就是一个会话型 HarnessAgent**；AAF 编排层是它"之前与周围"的逻辑（前注意短路、路由、TaskBoard、多分身），不是另一个独立的非-HarnessAgent 对象。衔接经 `AgentExecutor`（= `HarnessAgentExecutor`），记忆/计量/权限/轨迹经 middleware 注入，记忆真理源在认知基础。

### 缓存层：DB 配置作为"编译源"

DB 配置（Actor / Role / SkillDef / AgentDef / Model）→ 本地缓存（+Redis 二级，变更事件刷新）→ **编译成 `HarnessAgent.builder()`**（name / sysPrompt / model / skills / tools / workspace）。HarnessAgent 本是 builder 驱动，缓存配置充当编译源，**DB 仍是真理源**。Actor / Role 纯配置不池化。

### 多会话与实例模型

先定两个本体（**都是 HarnessAgent**，区别在生命周期）：

- **助理（会话型 HarnessAgent）**：面向用户的会话主体，持人格、对话历史、记忆、Session。长生命周期，按 `sessionId` 持久化。
- **Agent（任务型子 agent）**：助理为执行/并行 spawn 的子 agent，短生命周期，执行完即收。
- **多分身** = 助理同时 spawn 多个（同人格、不同角色）子 agent 并行，再聚合——本质就是子 agent 编排。

> 前注意分流、路由、TaskBoard 是 AAF 在"调用助理 HarnessAgent 之前/周围"的逻辑（可短路简单请求、不进 ReAct），不是又一个 agent 本体。

实例与会话的数量关系（**这是常被问混的点**）：

| 关系 | 基数 | 说明 |
|---|---|---|
| 用户 : 对话 | 1 : N | 一个用户可同时开多个对话 |
| 对话 : 助理会话(`sessionId`) | 1 : 1 | 每个对话一份独立 `AgentState` |
| 助理会话 : 助理实例(HarnessAgent) | 1 : 1 或 N : 1 | 见下「并发与性能」：每会话一个轻量实例，或共享实例 + 会话锁 |
| **重资源**（模型客户端 / 工具·技能模板 / Session 后端 / 沙箱池） | 全局共享 | 实例无论几个都共享这些，**不重复创建** |
| 助理会话 : 任务型子 agent | 1 : 0..N | 按需 spawn，执行完即收 |

两个核心问题的答案：

- **每个用户多会话，对应几个还是一个助理实例？** → 逻辑上每对话一个独立**会话**（`sessionId` + 独立 `AgentState`）。物理实例两种都行：每会话一个轻量实例（推荐，真并行）或共享实例 + 会话锁；但**重资源（模型/工具模板/Session/沙箱）始终全局共享，不为每对话重建**。关键是状态按 `sessionId` 隔离、`userId` 隔离沙箱，而非"几个实例"。
- **助理实例是否对应一个 HarnessAgent？** → **是。助理本体就是一个会话型 HarnessAgent**；它派发/并行出去的 Agent 是它 spawn 的任务型子 agent（也是 HarnessAgent，但短命）。

### 实例创建与生命周期

先回答"**助理是不是单例**"：**不是全局单例。** 全局单例的是**重资源**（模型客户端、工具/技能模板、Session 后端、沙箱池、middleware 构建器）——启动建一次、应用生命周期常驻、所有人共享。**助理与 Agent 实例是会话级的、可创建可回收**，区别只在策略：推荐"每会话一个轻量实例（按 `sessionKey` 懒建+缓存+空闲淘汰）"，备选"每 `agentId` 一个共享实例 + 会话锁"。

| 对象 | 创建时机 | 状态从哪来 | 存活 | 销毁 |
|---|---|---|---|---|
| 重资源（模型客户端 / 工具·技能模板 / Session 后端 / 沙箱池 / **认知服务**：记忆·知识·检索引擎） | 应用启动 | 配置（DB 编译） | 应用生命周期 | 应用关闭 |
| 会话型助理（HarnessAgent） | 某会话首次到达时懒建（按 `sessionKey` 缓存） | 调用时从 Session 按 `sessionId` 载入 `AgentState` | 会话活跃期驻留缓存 | 空闲超时 / 会话重置 / 缓存淘汰 / 应用关闭——**状态不丢（在 Session）** |
| 任务型子 agent（HarnessAgent） | 助理 spawn 子任务时（`SubagentFactory.create(parentRc)`） | 父上下文 + 自己的子 `sessionId` | 任务执行期（同步：完即收；后台：直到完成） | 任务结束即销毁（短命），结果落 task 仓 + Session |

关键性质：**实例轻、状态重外置**。创建实例 = new 对象 + 从 Session 载状态；销毁实例 = 丢对象，状态留 Session。所以"何时创建/销毁实例"**不影响正确性**（状态随时可从 Session 恢复），只影响内存与并发度——这正是能放心"懒建 + 淘汰 + 跨副本漂移"的根本原因。

> **认知服务（记忆 / 知识 / 检索引擎）同属重资源**：是单例、无状态的 `@Service` bean，全局共享、并发安全。真正区分用户/会话的不是"多个服务实例"，而是其背后**按 `userId` / `knowledgeBaseId` / scope 分区的数据**（存 PgVector / Neo4j / Redis）；调用时传身份，单例服务按身份读写对应分区。瓶颈在这些存储与检索模型的吞吐，不在 bean。

落地骨架（**单例管理服务 + 会话级助理实例**）：

```java
@Service                                   // 单例·无状态：接请求、路由、复用/新建、加锁
class AssistantRuntime {
    private final Model sharedModel;         // 重资源·单例共享
    private final Session sessionBackend;    // 重资源·单例共享（Redis）
    private final Map<String, HarnessAgent> cache = new ConcurrentHashMap<>(); // 会话级实例缓存
    private final SessionTurnGate gate;      // 每会话锁

    Mono<Msg> handle(String userId, String conversationId, List<Msg> msgs) {
        String sessionKey = userId + ":" + conversationId;
        // 复用或新建：会话级 HarnessAgent（共享重资源）
        HarnessAgent assistant = cache.computeIfAbsent(sessionKey, k ->
            HarnessAgent.builder()
                .model(sharedModel).session(sessionBackend)
                .filesystem(perUserSandbox(userId))
                /* 角色/技能/middleware 由 DB 配置编译 */
                .build());                   // 未命中→新建轻量实例；首次 call 按 sessionId 从 Session 载入 AgentState
        RuntimeContext ctx = RuntimeContext.builder()
            .userId(userId).sessionId(conversationId).build();
        return gate.run(sessionKey, () -> assistant.call(msgs, ctx)); // 每会话串行
    }
}
```

- **单例的是 `AssistantRuntime`**（管理者/网关，无状态：接请求、按 `sessionKey` 路由、复用/新建、加锁）；助理 `HarnessAgent` 是**会话级**，按 `sessionKey` 复用或新建。
- **助理不能做成无状态单例**：它持有会话级活跃 `AgentState`（对话上下文等），单例会让所有会话共用一份状态而串话/竞争。对比记忆/知识服务能单例，是因为它们无状态、数据在 DB 按身份传参。
- dataagent 对应实现：`SessionAgentManager.getOrCreateAgent`（`agentCache.computeIfAbsent(sessionKey, ...)`）+ `HarnessGateway`（`withGatedTurn` 每会话锁）。

#### 空闲淘汰（实例回收）

淘汰的是**内存里的助理实例**（释放内存/资源）；`AgentState` 已在 Session，淘汰零损失，下次该会话再来 miss → 重建并从 Session 恢复。两种实现：

- **Caffeine TTL + 容量**（推荐，AAF 技术栈已用 Caffeine）：把实例缓存换成
  ```java
  Caffeine.newBuilder()
      .expireAfterAccess(Duration.ofMinutes(30))   // 空闲淘汰：闲置超时即剔除
      .maximumSize(2000)                            // 容量淘汰：超量按近似 LRU
      .removalListener((k, agent, cause) -> {       // 淘汰回调：释放资源（状态已在 Session）
          if (agent != null) ((HarnessAgent) agent).close(); // 关工作区索引/沙箱租约
      })
      .build();
  ```
- **定时维护**（dataagent 官方示例做法）：维护 `lastActivityMs` + 调度器周期跑 `SessionAgentManager.runMaintenance()`（按 `pruneAfterMs` 剔除、`maxEntries` 限容，内部 `agentCache.remove`），另有 `resetIdleSessions(idleMs)` / `evictAgent(sessionKey)`。

注意：① 配合每会话锁，淘汰只发生在调用间隙，安全；② `close()` 释放工作区索引/沙箱租约，模型/Session 等全局重资源不关，per-user 沙箱另由沙箱池按空闲回收；③ 多副本下缓存是**每副本本地**的，会话漂到别副本 miss → 从 Redis Session 恢复，无需分布式协调。

> 来源标注：会话维护/空闲重置（`runMaintenance` / `resetIdleSessions` / `evictAgent`）出自官方示例 `agentscope-dataagent` 源码（用 `ConcurrentHashMap` + 定时维护）；`HarnessAgent.close()` 出自 harness 源码；**Caffeine 方案是结合 AAF 既有技术栈的标准用法，非 agentscope 自带示例**。

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

### 并发与性能

- **同一会话内**：串行——用户一问一答本就顺序，且单实例同一时刻只持一份活跃 `AgentState`，串行保证状态一致，无损失。
- **跨会话 / 跨用户**：可并行，但**不能让多个会话争用同一份活跃状态**（源码注释：普通路径改写共享状态、并发不安全，`workspaceFor` 才是可并发旁路）。
- 瓶颈**不在"一个 Java 对象"**：一次 turn 是 I/O 密集（等模型/工具/沙箱），运行时是 reactive 调度；真正的容量约束是 **LLM 并发额度、沙箱容量、Session/Redis 吞吐**。

跨会话并行的几种做法：

| 做法 | 跨会话并行 | 评价 |
|---|---|---|
| 共享 1 实例 + 一把实例级锁 | ❌ 全串行 | 瓶颈，**别用** |
| 共享实例 + 仅每会话锁 | ✅ 不同会话并行 | 依赖运行时 per-call 状态隔离 |
| **每会话/每用户轻量实例 + 共享重资源** | ✅ 真并行 | **推荐**，实例轻、创建廉价（dataagent 按 `sessionKey` 缓存即此） |
| 多副本 + Redis Session | ✅ 跨副本并行 | 横向扩展标配；**同会话需粘性路由或分布式锁**（见下） |

> ⚠️ 多副本注意：`SessionTurnGate` 是**进程内内存锁**，只在单副本内串行。多副本部署时，同一 `sessionId` 的请求必须**粘性路由**到同一副本，或改用**分布式锁**（如 Redis），否则两副本可能同时处理同一会话、争用 `AgentState`，落 Session 时 last-write-wins。Redis Session 只负责跨副本状态恢复，**不负责跨副本写串行**。

### 名词解释

- **AgentState**：一次会话的"瞬时运行状态"快照——对话历史、权限、Plan 状态、待办、工具状态。每会话一份，按 `sessionId` 隔离。
- **Session（会话后端）**：把 `AgentState` 持久化的存储抽象；换成 `RedisSession` 即可多副本共享、跨进程恢复。
- **sessionId / sessionKey**：`sessionId` 标识"哪段对话"；`sessionKey` 是写入存储时的键（可把 `userId` 编进去做隔离）。
- **会话锁**：保证"同一会话同一时刻只有一轮在跑"的互斥（如 dataagent 的 `SessionTurnGate`）；不同会话各一把，互不阻塞。
- **轻量实例 vs 重资源**：实例 = 一个 HarnessAgent 对象，只持配置引用 + 当前会话状态，创建很便宜；重资源 = 模型客户端、工具/技能模板、Session 后端、沙箱池，全局共享、只建一次。
- **per-call 状态隔离**：每次 `call()` 的状态收在本次调用作用域内、不串到并发调用——决定"共享实例 + 仅会话锁"是否安全。
- **reactive / boundedElastic 调度**：非阻塞响应式执行；I/O 等待时线程不空转，少量线程即可承载大量并发 turn。
- **横向扩展（副本）**：多开无状态 JVM 进程，会话状态放共享 Session(Redis)，请求可路由到任意副本、会话可在副本间漂移。

### 任务管理与 Checkpoint：不双存，按归属分

- **编排态 → AAF 存 DB**：GoalTracker / TaskBoard / SubTaskContext / fork 树 → 编排 checkpoint。
- **助理与 Agent 的运行态 → HarnessAgent 存 Session(Redis)**：两者的 AgentState（对话/权限/plan/todo/工具状态）、工作记忆都由 HarnessAgent 在 call 结束自动落 Session，按 `sessionId` 跨副本恢复。AAF **不再**为它们的运行态另做 checkpoint。
- **恢复缝合**：重启 → AAF 扫 DB 编排 checkpoint 恢复主助理/TaskBoard → 每个 RUNNING 子任务按其 sessionId 让 HarnessAgent 从 Redis 自动恢复 AgentState → 续跑。编排态 AAF 管、执行态 Session 管，**按 sessionId 缝合**。

### 技能与工具：DB 定义编译进 HarnessAgent

Role 的 Skill 集 → skillRepositories；Tool 白名单 → tools.json / 工具过滤；MCP server → HarnessAgent MCP 集成。高风险动作经权限 middleware HITL。Skill / Tool 真理源在 DB，HarnessAgent 配置是编译产物，不反向为源。

### 记忆衔接：认知基础为真理源

Agent 不走 HarnessAgent 的 `MEMORY.md` 自管：每轮由 AAF `MemoryContext` middleware 注入混合检索结果；新事实经学习反哺写回认知基础（DB + 图谱）。HarnessAgent 工作区记忆退为"会话工作层"。实现选项：自定义 Session / RemoteFilesystem 桥回认知基础，或关闭其 MEMORY flush、纯靠 middleware 注入 + 反哺写回。

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
   │ HarnessAgent（共享实例 + 每会话锁）            │  AgentState → Session(Redis)
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
- Session：`RedisSession` + `SessionKey` 编 `userId` 隔离
- Sandbox：per-user（`DockerFilesystemSpec` + `IsolationScope.USER`）

## 复杂任务全流程实现（结合 AgentScope 运行时）

> 技术可行性参考。把上文「场景会话流程」的复杂任务，逐步映射到 AgentScope HarnessAgent 的真实能力，验证可实现性。代码为示意（API 形状取自 `HarnessAgent` builder 与 harness 扩展），落地以实际版本签名为准。

**结论：能实现。** 大部分步骤是 HarnessAgent 原生能力；AAF 差异化（记忆/知识真理源、DB 配置、计量）经 builder 编译 + middleware 注入（官方扩展点）实现。

| 流程步骤 | AgentScope 承载 | 归属 |
|---|---|---|
| 创建会话型助理 | `HarnessAgent.builder()` + `RedisSession` + per-user 沙箱 | 原生 |
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
    .session(redisSession)              // 多副本共享、跨进程恢复（RedisSession）
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
  │  [实例] 该会话首次到达 → 按 sessionKey 懒建助理实例 + 从 Session 载入 AgentState
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
- **编码编排**（我们写）：协调器/工作流创建多个 HarnessAgent 实例并精确编排（并行扇出 + TaskBoard 跟踪 + 聚合 + 仲裁）。dataagent 的 `SessionAgentManager` + `HarnessGateway` 即范例；AAF 的 Assistant 协调、Team（Leader/Worker）、A2A 跨系统属此类。确定性流程用编排、灵活探索用自主。

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

> 技术设计参考。把 v1 数据架构迁移到 v2，并按运行时设计重新划分存储归属。核心变化：**会话运行态归 Session(Redis)、不进业务 DB**；DB 只存配置、对话记录、编排态、认知数据与计量。实际表定义以 `vN__*.sql` 为准，本节为设计视角的精简描述。

### 存储分工（先定真理源）

| 数据 | 存储 | 真理源 |
|---|---|---|
| 配置（人格 / 角色 / 技能 / Agent 定义 / 模型） | PostgreSQL | DB |
| 对话记录（消息流、参与方） | PostgreSQL | DB |
| 编排态（GoalTracker / TaskBoard / 子任务 / 任务事件） | PostgreSQL | DB |
| 认知数据·结构化（记忆原子 / 知识分块 / 价值观 / 决策日志） | PostgreSQL | DB |
| 认知数据·向量 | PgVector | DB |
| 认知数据·图关系 | Neo4j（PG 为源，异步同步） | PG |
| **会话运行态（AgentState：对话上下文 / Plan / todo / 工具 / 权限）** | **Session 后端（Redis）** | **Session** |
| 计量（Token / 额度） | PostgreSQL | DB |

一句话：**配置与认知数据、对话记录、编排态在 DB；会话运行态在 Session(Redis)；向量在 PgVector、图在 Neo4j。**

### 与 v1 的关键差异（运行时驱动）

- **运行态出 DB**：v1 的 `ai_task_checkpoint` 收窄——**只存编排态**（TaskBoard / GoalTracker / fork 树 / SubTaskContext），不再存 agent/助理的对话级运行态（那是 `AgentState`，归 Session/Redis，由 HarnessAgent 在 call 结束自动落盘）。Agent 步骤级工作记忆同理在 `AgentState` 内，不进 DB。
- **对话历史双轨澄清（防双真理源）**：`conversation_message`（DB）= **对外可见、跨参与方、可审计/检索的对话真理源**；`AgentState.context`（Session）= **agent 推理用的工作上下文**（会被压缩/卸载，可重建）。两者职责不同、按 `sessionId` 关联，**不是双真理源**。消息由 `ChatPersistenceListener` 从 agent 事件落 DB。
- **记忆/知识真理源在认知基础**：HarnessAgent 的 `MEMORY.md` 退为"会话工作层"，**不建 DB 表**；长期记忆/知识在 `ai_memory_*` / `ai_knowledge_*`（+ PgVector / Neo4j）。
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
- **会话运行态**：**无 DB 表**——在 Redis Session（`SessionKey` 由 `userId` + `conversationId` 编码）；若用 `MysqlSession` 则落 agentscope 扩展自带的 session 表（基础设施，非业务 schema）。

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
- **助理对话主模型**：`ai_assistant.model_id → ai_model`（FK，**可空**）。助理本体是会话型 HarnessAgent，需模型做对话推理；为空时由 `CapabilityRouter` 按 `ai_model_preference`（USER/SYSTEM × capability）路由。模型是多级多用途的：前注意分流（小模型）/ 助理对话主模型 / Agent 任务模型（`ai_agent_definition.model_id`）/ 嵌入检索（capability=EMBEDDING），优先级链：**显式绑定 → 用户偏好 → 系统默认**，逐级降级。
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

- `conversation.id`（或 `thread_id`）↔ HarnessAgent 的 `sessionId`；`userId` 编入 `SessionKey` 做隔离。
- 一次对话（`conversation`）= 一个会话型 HarnessAgent 会话（一份 `AgentState`，存 Session）+ 一串对外消息（`conversation_message`，存 DB）。
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

## v2 迁移待办：计费链路收尾事项

> v1 时代发现但**故意延后**到 v2 重构时一并修复的 AI 计费链路问题。在 v1（`Hook` + `PreReasoningEvent` / `PostCallEvent`）下做这些修复将在 v2 全部作废，故先按"症状容错"处理，根因修在 v2 落地。
> 来源：v0.x 期间 chat 计费日志/流水接线工作的现场结论（2026-06）。

### 背景

v1 AgentScope 链路（`AssistantScopeRuntime` / `AgentScopeRuntime` + `TokenMeteringHook`）在结算时 **modelId 全程为 null**：

- `TokenMeteringHook` 调 `meteringService.record(userId, null, ...)` 落 `ai_token_usage` 时被 NOT NULL + FK 拦截 → 计费真理源缺数据
- 同时调 `creditGuard.settleByUsage(userId, null, ...)` → `DefaultAiCreditGuard.calcCost` 走兜底单价 0.072 元/千 token → **不论真实模型是 GPT-4o / DeepSeek / Qwen-Max 都按同一价扣**，金额不准

根因是 v1 Hook 接口拿不到当前 LLM 调用的 `Model` 实例，反查需要 ThreadLocal/全局 Map 跨"构建期 → 运行期"传递，方案不干净。

### v1 时代的容错（已在 v0.x 落地，保留至 v2）

- **`ai_token_usage.model_id` 改可空**（直接改 `v2__ai_schema.sql` 建表语句）：让 AgentScope 路径至少能写入 token 用量记录，model_id 留 NULL；v2 SDK 迁移后能可靠拿到 modelId 时回收 NOT NULL 约束
- **`TokenUsageRecord.modelId` 注解去 `nullable = false`**：与迁移一致
- **`TokenMeteringHook` 透传 `conversationId`**：从 `AgentRunContextHolder` 取，提升可观测性
- **capability 仍硬编码 `"agentscope"`**：未拆 chat / vision

### v2 迁移时必须补齐的根因修复

按 v2 `MiddlewareBase`（5 挂点：`onAgent` / `onReasoning` / `onActing` / `onModelCall` / `onSystemPrompt`）改造 `TokenMeteringHook` → `TokenMeteringMiddleware`，关键差异：

- **modelId 透传**：`onModelCall` 的 `ModelCallInput.model()` 直接给 `Model` 实例。从中提取 modelId 字符串 → 反查 `ai_model.id`（Long）→ 传入 `creditGuard.settleByUsage(userId, model, ...)`，让 `calcCost` 走真实价格而非 0.072 兜底。
- **vision / chat 自动拆分**：`onAgent` 的 `msgs: List<Msg>`（或 `onReasoning` 的 `messages: List<Msg>`）遍历 `ContentBlock`，命中 `ImageBlock` / `VideoBlock` / `AudioBlock` 或图像/视频类的 `DataBlock` 即按对应能力拆分（vision / 多模态），否则 `chat`。与 SpringAI 路径在 `credit_transaction.category` 上保持口径统一。
- **per-call 上下文用官方 `RuntimeContext`**：替代 v1 的 `AgentRunContextHolder` ThreadLocal 槽位。v2 `ReActAgent` **完全无状态**（change-log A.6），per-call 状态封装在 `CallExecution` 对象、经 **Reactor Context** 在调用链上透传——同实例并发服务多 `(userId, sessionId)` 安全。`userId` / `sessionId` / `sessionKey` 走 `RuntimeContext`，middleware 内 `agent.getRuntimeContext()` 读写；modelId 不进 `RuntimeContext`，在 `onModelCall` 处直接从 `ModelCallInput.model()` 取。
- **precheck 与 settle 接同一个 capability**：`onAgent` 入口做 precheck（已知 messages 即可识别 vision），`onModelCall` 出口做 settle，二者用同一字符串。

### 落地时的关联清理

- `AiCreditGuard.precheck` / `settleByUsage` 入参不变（已是 `(userId, capability, ...)` 形态），只换调用方
- `DefaultAiCreditGuard.fallbackCreditCost` 兜底逻辑保留作"AgentScope 配置错误模型时的最后防线"，正常路径不应再触发
- `ai_token_usage.model_id` 是否回收 NOT NULL 约束：评估 v2 落地后是否还存在 modelId 拿不到的边缘场景；若彻底无，重新加约束以恢复"计费真理源"强语义
- `TokenUsageEvent` v1 监听器（`TokenUsageEventListener`、`TokenMeteringService.onTokenUsage`）仍服务 SpringAI 直连路径，**保留**——v2 迁移只动 AgentScope 那一支
- 把 capability 从 `"agentscope"` 改为 `"chat"` / `"vision"` 后，引擎归属信息改放在 `credit_transaction.remark` 或新增 `engine` 元数据字段（与 SpringAI 路径区分）

### 验收标准

| 项 | 期望 |
|---|---|
| AgentScope 路径 `credit_transaction.category` | `chat` 或 `vision`，不再是 `agentscope` |
| AgentScope 路径 `credit_transaction.amount` | 按 `ai_model.input_price_per_k` / `output_price_per_k` 真实算，不走 0.072 兜底 |
| `ai_token_usage.model_id` | 非 null，对得上 `ai_model.id` |
| `ai_usage_record.model_id` | 同上 |
| 多模态对话（含图像消息） | capability 落 `vision`，扣分按 vision 模型价格 |
| INFO 日志 | `AgentScope 计费结算: ... capability=vision modelId=...` 可见 |

### 落地节奏

随 v2 主干迁移（`Hook` → `Middleware`、`AssistantScopeRuntime` 重写为 `HarnessAgent` builder）一次到位，**不单独立 PR**。本节作为 v2 迁移的 sub-checklist。

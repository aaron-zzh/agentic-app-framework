---
level: Practice
layer: Model
purpose: 五层智能架构执行逻辑 Mermaid 可视化
status: draft
version: 0.1.0
date: 2026-05-21
author: AaronZZH & Kiro
---

# 五层智能架构执行逻辑图

> 本文档是 [execution-flow.md](execution-flow.md) 的可视化补充。

## 整体架构分层

```mermaid
graph TB
    subgraph L5["Layer 5 对话与交互层（aaf-api）"]
        REST[REST API]
        GQL[GraphQL]
        WS[WebSocket]
        AGUI[AG-UI SSE]
        GW[安全网关<br/>JWT + RBAC + 限流]
        CTRL[Controller]
        REST & GQL & WS & AGUI --> GW --> CTRL
    end

    subgraph L4["Layer 4 服务层（aaf-api/module）"]
        ChatSvc[ChatService]
        AsstMgmt[AssistantManagementService]
        AgentMgmt[AgentManagementService]
        KnowMgmt[KnowledgeManagementService]
        ModelMgmt[ModelManagementService]
    end

    subgraph L3["Layer 3 智能层（aaf-framework/intelligent）"]
        subgraph Team["Team 层"]
            TO[TeamOrchestrator]
            TD[TaskDistributor]
            PS[ProgressSyncService]
            CA[ConflictArbitrator]
            A2A[A2AProtocolService]
        end

        subgraph Asst["Assistant 层"]
            IU[IntentUnderstandingService]
            EP[EmotionPerceptionService]
            SM[SessionManager]
            SKM[SkillMatchService]
            AD[AgentDispatcher]
            RA[ResultAggregator]
        end

        subgraph Agent["Agent 层"]
            AP[AgentPool]
            AS[AgentSandbox]
            CCE[CognitiveCycleExecutor<br/>感知→规划→执行→评估]
            ASE[AgentScopeExecutor<br/>ReActAgent]
            TCD[ToolCallDispatcher]
            ACS[AgentCheckpointService]
        end

        subgraph Cognition["Cognition 层"]
            MP[MemoryPipeline]
            STM[ShortTermMemoryService]
            LTM[LongTermMemoryService]
            URS[UnifiedRetrievalService]
            VS[ValueService]
        end

        subgraph Core["Core 层"]
            MR[ModelRouter<br/>六层决策链]
            DCF[DynamicChatClientFactory]
            RCS[ResilientChatService]
            TMS[TokenMeteringService]
        end
    end

    subgraph L2["Layer 2 引擎层（aaf-framework/engine）"]
        KE[knowledge/<br/>NexusKB]
        ME[memory/<br/>AtomMemoryEngine]
        VRE[value-rule/<br/>ValueRuleEngine]
        TE[tool/<br/>ToolRegistry + MCP]
        SKE[skill/<br/>SkillMatchEngine]
        WFE[workflow/<br/>Flowable]
        DPE[data-process/<br/>DataProcessEngine]
        SCE[semantic-calc/<br/>SemanticCalcEngine]
    end

    subgraph L1["Layer 1 基础设施层"]
        PG[(PostgreSQL<br/>+ PgVector)]
        Redis[(Redis)]
        Neo4j[(Neo4j)]
        Sandbox[JVM Sandbox]
    end

    CTRL --> L4
    L4 --> Team
    L4 --> Asst
    Team --> Asst
    Asst --> Agent
    Agent -->|执行前拉取| Cognition
    Agent -->|执行后写回| Cognition
    Cognition --> Core
    Agent --> L2
    Cognition --> L2
    Core --> L2
    L2 --> L1
```

## 两条 LLM 调用路径

> 是否优先使用 AgentScope 路径，还是说我们自己可控的流程尽量使用 SpringAI

```mermaid
flowchart LR
    subgraph PathA["路径 A：Spring AI（对话/RAG/记忆提取）"]
        RCS2[ResilientChatService] --> MR2[ModelRouter] --> DCF2[DynamicChatClientFactory]
        DCF2 --> OAI[OpenAiChatModel]
        DCF2 --> ANT[AnthropicChatModel]
        DCF2 --> OLL[OllamaChatModel]
    end

    subgraph PathB["路径 B：AgentScope（Agent 执行）"]
        AF[AgentFactory] --> ASE2[AgentScopeExecutor] --> RAG[ReActAgent]
        RAG --> OAIAS[OpenAIChatModel<br/>AgentScope]
    end

    subgraph Shared["共享"]
        AIM[(ai_model 表)]
        TUE[TokenUsageEvent]
        MPT[(ModelPreference 表)]
    end

    AIM -.-> PathA
    AIM -.-> PathB
    TUE -.-> PathA
    TUE -.-> PathB
```

## 一次对话请求的完整调用链

混合检索（是否应=记忆+知识库）、用户感知没有明显体现

```mermaid
sequenceDiagram
    participant U as 用户
    participant L5 as Layer 5<br/>API
    participant L4 as Layer 4<br/>Service
    participant Asst as Assistant
    participant Agent as Agent
    participant Cog as Cognition
    participant Core as Core/LLM
    participant Eng as Engine

    U->>L5: 发送消息
    L5->>L5: JWT + RBAC + 限流
    L5->>L4: ChatService
    L4->>Asst: AssistantExecutor.chat()
    Asst->>Asst: 意图理解 + 情感感知
    Asst->>Asst: SkillMatch → AgentDispatcher
    Asst->>Agent: 派发任务

    rect rgb(230, 245, 255)
        Note over Agent,Eng: 执行前：拉取上下文
        Agent->>Cog: MemoryPipeline.execute()
        Cog->>Eng: AtomMemoryEngine.recall()
        Cog->>Eng: HybridSearchService.search()
        Cog->>Cog: RRF融合 + 重排 + Value过滤
        Cog-->>Agent: MemoryContext
    end

    rect rgb(255, 245, 230)
        Note over Agent,Core: 执行中：ReAct 循环
        Agent->>Core: LLM 推理
        Core->>Core: ModelRouter → ChatClient
        Core-->>Agent: 响应（含工具调用决策）
        Agent->>Eng: ToolCallDispatcher
        Eng-->>Agent: 工具结果
        Agent->>Core: 再次推理（含工具结果）
        Core-->>Agent: 最终响应
    end

    rect rgb(230, 255, 230)
        Note over Agent,Eng: 执行后：写回记忆（固定四步）
        Agent->>Cog: MemoryWritePipeline
        Cog->>Cog: 1.提取 → 2.去重 → 3.写入 → 4.遗忘
        Cog->>Eng: AtomMemoryEngine.write()
    end

    Agent-->>Asst: 执行结果
    Asst-->>L4: 聚合结果
    L4-->>L5: AG-UI SSE 流
    L5-->>U: 实时文字输出
```

## Agent 池化 × 模型选择 × 积分预算

```mermaid
flowchart TD
    Start([请求到达]) --> Budget{预算检查}
    Budget -->|余额不足| Degrade[降级到便宜模型]
    Budget -->|余额充足| Route[ModelRouter 六层决策]

    Route --> ModelId[确定 modelId]
    ModelId --> Pool{AgentPool}
    Pool -->|有空闲| Borrow[借出 + reset]
    Pool -->|无空闲| Create[AgentFactory.create]
    Borrow & Create --> Execute[Agent 执行]

    Execute --> Meter[TokenMeteringHook<br/>实时计量]
    Meter --> Over{超预算?}
    Over -->|是| Pause[暂停 + 通知用户]
    Over -->|否| Continue[继续执行]
    Continue --> Done[执行完成]
    Done --> Deduct[CreditService.deduct<br/>积分扣减]
    Deduct --> Release[AgentPool.release<br/>归还复用]
```

## 记忆管道（读管道 + 写管道）

```mermaid
flowchart TD
    subgraph Read["读管道（RetrievalPipeline）— 可编排"]
        Q[用户查询] --> QU[查询理解<br/>意图+实体+时间]
        QU --> RD[路由决策<br/>MemoryStrategy]
        RD --> PR[并行检索]
        PR --> V1[向量检索<br/>PgVector]
        PR --> V2[图谱检索<br/>Neo4j]
        PR --> V3[关键词<br/>PG FTS]
        PR --> V4[短期记忆<br/>Redis]
        PR --> V5[长期记忆<br/>PG]
        V1 & V2 & V3 & V4 & V5 --> RRF[RRF 融合]
        RRF --> Rerank[重排序]
        Rerank --> ValFilter[Value 校验过滤]
        ValFilter --> MC[MemoryContext]
    end

    subgraph Write["写管道（MemoryWritePipeline）— 固定四步"]
        Result[执行结果] --> Extract[1. 提取<br/>LLM抽取关键信息]
        Extract --> Dedup[2. 去重<br/>语义相似度比对]
        Dedup --> Store[3. 写入<br/>AtomMemoryEngine]
        Store --> Forget[4. 遗忘<br/>TimeDecayStrategy]
    end
```

## Learning 横切反哺通道

> 何时在哪如何集成

```mermaid
flowchart LR
    Exec[Agent 执行完成] --> TC[TrajectoryCollector<br/>轨迹采集]
    TC --> EE[EffectEvaluator<br/>效果评估]
    EE --> PD[ProceduralDistiller<br/>程序化记忆蒸馏]

    PD --> S1[SuccessExtraction<br/>成功模式]
    PD --> S2[FailureExtraction<br/>失败教训]
    PD --> S3[ComparativeExtraction<br/>对比分析]

    S1 & S2 & S3 --> MV[MemoryValidation]
    MV --> MD[MemoryDeduplication]
    MD --> MA[MemoryAddition]

    MA --> Mem[(Memory<br/>程序化记忆写回)]
    EE --> Know[(Knowledge<br/>知识生长)]
    EE --> SIS[SelfImprovementService<br/>技能生成]
    EE --> VUP[ValueUpdateProposer<br/>价值观更新建议<br/>⚠️ 必须人工审核]
```

## 置信度 × 可验证性 二维门控

> 在哪集成？

```mermaid
quadrantChart
    title 置信度 × 可验证性决策矩阵
    x-axis "低置信度" --> "高置信度"
    y-axis "不可验证" --> "可验证"
    quadrant-1 "自动执行 + 自动验证"
    quadrant-2 "执行 + 验证 + 失败回滚"
    quadrant-3 "暂停 + 转人工决策"
    quadrant-4 "执行 + 决策日志 + 异步审查"
```

## 五层认知循环

```mermaid
flowchart TB
    subgraph T["Team（项目级）"]
        direction LR
        T1[目标对齐] --> T2[任务分发] --> T3[进度同步] --> T4[结果聚合] --> T5[冲突仲裁]
    end

    subgraph A3["Assistant（会话级）"]
        direction LR
        A31[情感感知] --> A32[意图理解] --> A33[上下文构建] --> A34[Agent调度] --> A35[反馈整合] --> A36[记忆更新]
    end

    subgraph A2["Agent（任务级·无状态）"]
        direction LR
        A21[感知] --> A22[规划] --> A23[执行] --> A24[评估] --> A25[学习↔记忆]
    end

    subgraph C["Cognition（持久级·被动响应）"]
        direction LR
        C1[存储] --> C2[检索] --> C3[更新] --> C4[遗忘]
    end

    subgraph C0["Core（请求级·无状态）"]
        direction LR
        C01[推理] --> C02[生成] --> C03[上下文窗口管理]
    end

    T -->|调度| A3
    A3 -->|派发| A2
    A2 -->|拉取/写回| C
    A2 -->|调用| C0
    C0 -->|结果| A2
    A2 -->|回调| A3
    A3 -->|回调| T
```

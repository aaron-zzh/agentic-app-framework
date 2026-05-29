# Agent 开发指南

## Agent 架构

```text
用户消息 → Agent Runtime → 模型推理 → Tool Call → 结果汇总 → 响应
                ↑                         ↓
           系统提示词              知识库/工作流/MCP 工具
```

AAF Agent 基于 Spring AI 构建，支持：
- 多模型切换（OpenAI / Azure / 本地模型）
- 工具调用（MCP 协议）
- 知识库检索（RAG）
- 工作流触发
- 多 Agent 协作
- 人工审批（HITL）

## 创建 Agent（后端）

### 基本 Agent

```java
@Component
public class CustomerAgent {

    private final ChatClient chatClient;
    private final KnowledgeService knowledgeService;

    @Autowired
    public CustomerAgent(ChatClient.Builder builder, KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
        this.chatClient = builder
            .defaultSystem("你是客户服务助手，基于知识库回答客户问题。")
            .defaultTools(new CustomerTools())
            .build();
    }

    public Flux<String> chat(String userMessage, String knowledgeBaseId) {
        // RAG：检索相关文档
        var context = knowledgeService.search(knowledgeBaseId, userMessage, 5);

        return chatClient.prompt()
            .user(u -> u.text("上下文：{context}\n\n问题：{query}")
                .param("context", context)
                .param("query", userMessage))
            .stream()
            .content();
    }
}
```

### 工具定义

```java
public class CustomerTools {

    @Tool(description = "查询客户订单列表")
    public List<Order> queryOrders(
        @Param(description = "客户 ID") String customerId,
        @Param(description = "最近 N 天") int days
    ) {
        return orderRepository.findByCustomerIdAndCreatedAfter(
            customerId, LocalDate.now().minusDays(days));
    }

    @Tool(description = "创建工单")
    public Ticket createTicket(
        @Param(description = "标题") String title,
        @Param(description = "描述") String description,
        @Param(description = "优先级: low/medium/high") String priority
    ) {
        return ticketService.create(title, description, priority);
    }
}
```

## Agent 配置（JSON）

通过配置文件或数据库定义 Agent：

```json
{
  "id": "customer-agent",
  "name": "客户助手",
  "model": "gpt-4o",
  "systemPrompt": "你是客户服务助手...",
  "temperature": 0.7,
  "maxTokens": 4096,
  "knowledgeBases": ["kb-product", "kb-faq"],
  "tools": ["knowledge_search", "query_orders", "create_ticket"],
  "workflows": ["escalation-flow"],
  "config": {
    "maxTurns": 20,
    "timeout": 60000,
    "hitl": {
      "enabled": true,
      "triggers": ["create_ticket", "refund"]
    }
  }
}
```

## 多 Agent 协作

### 路由模式

根据用户意图路由到不同 Agent：

```java
@Component
public class RouterAgent {

    private final Map<String, Agent> agents;

    @Tool(description = "根据用户意图选择合适的 Agent")
    public String route(
        @Param(description = "用户意图分类") String intent
    ) {
        return switch (intent) {
            case "order" -> "order-agent";
            case "technical" -> "tech-support-agent";
            case "billing" -> "billing-agent";
            default -> "general-agent";
        };
    }
}
```

### 协作模式

多个 Agent 并行处理，汇总结果：

```java
// 并行调用多个 Agent
var results = Flux.merge(
    researchAgent.analyze(query),
    dataAgent.queryData(query),
    summaryAgent.prepare(query)
).collectList().block();

// 汇总 Agent 合并结果
return summaryAgent.summarize(results);
```

## 人工审批（HITL）

当 Agent 执行高风险操作时暂停等待人类确认：

```java
@Tool(description = "退款操作（需人工审批）")
@RequiresApproval(reason = "退款金额超过阈值")
public RefundResult processRefund(
    @Param(description = "订单 ID") String orderId,
    @Param(description = "退款金额") BigDecimal amount
) {
    // 审批通过后才执行
    return paymentService.refund(orderId, amount);
}
```

前端收到 `INTERRUPT` 事件后展示审批 UI，用户确认后继续执行。

## 记忆与上下文

### 短期记忆（对话内）

自动维护对话历史，通过 `threadId` 关联：

```java
chatClient.prompt()
    .messages(threadService.getHistory(threadId)) // 加载历史
    .user(newMessage)
    .stream();
```

### 长期记忆（跨对话）

基于 Neo4j 知识图谱存储用户偏好和关键信息：

```java
@Tool(description = "记住用户偏好")
public void remember(
    @Param(description = "记忆内容") String content,
    @Param(description = "类别") String category
) {
    memoryService.store(userId, content, category);
}
```

## AG-UI 协议集成

Agent 通过 AG-UI 协议与前端通信，自动处理：
- 流式文本输出（`TEXT_MESSAGE_CONTENT`）
- 工具调用展示（`TOOL_CALL_START/END`）
- 执行步骤追踪（`STEP_STARTED/FINISHED`）
- 状态同步（`STATE_DELTA`）

详见 [AG-UI 协议文档](../../reference/api/ag-ui-protocol.md)。

## 测试 Agent

```java
@SpringBootTest
class CustomerAgentTest {

    @Autowired
    private CustomerAgent agent;

    @Test
    @DisplayName("Given 知识库有产品文档 When 用户问产品功能 Then 返回准确回答")
    void should_answer_from_knowledge_base() {
        var response = agent.chat("产品支持哪些文件格式？", "kb-product")
            .collectList().block();

        assertThat(String.join("", response))
            .contains("PDF", "Word", "Markdown");
    }
}
```

## 部署与监控

- Agent 调用日志自动记录（模型/Token 用量/延迟/工具调用链）
- 通过 `/api/agents/:id/metrics` 查看 Agent 性能指标
- Token 配额管理：按用户/组织/Agent 设置上限
- 异常告警：连续失败 > 3 次自动通知管理员

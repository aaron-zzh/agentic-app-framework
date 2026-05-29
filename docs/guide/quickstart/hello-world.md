# Hello World：第一个 Agent

> 前置条件：已通过 [Docker 快速启动](./docker-quickstart.md) 运行服务。

## 创建知识库

### 通过界面

1. 登录 http://localhost:3000
2. 侧边栏 → **知识库** → **新建**
3. 名称：`产品文档`，描述：`公司产品相关文档`
4. 点击创建

### 通过 API

```bash
curl -X POST http://localhost:8080/api/knowledge-bases \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"name": "产品文档", "description": "公司产品相关文档"}'
```

### 上传文档

将 PDF/Markdown 文件拖拽到知识库详情页的上传区域，等待状态变为"已就绪"。

### 测试检索

在知识库详情页输入问题测试：

```
输入：产品的核心功能是什么？
结果：[返回相关文档片段 + 相似度分数]
```

## 创建工作流

### 简单问答工作流

1. 侧边栏 → **工作流** → **新建**
2. 名称：`智能问答`
3. 拖拽节点构建流程：

```text
[开始] → [知识检索] → [LLM 生成] → [结束]
```

4. 配置节点：
   - **知识检索**：选择"产品文档"知识库，topK=3
   - **LLM 生成**：模板 `基于以下资料回答用户问题：\n{context}\n\n问题：{query}`
5. 点击 **发布**

### 调试

点击 **调试** → 输入测试问题 → 查看每个节点的执行结果。

## 创建 Agent

### 通过界面

1. 侧边栏 → **Agent** → **新建**
2. 配置：
   - 名称：`产品助手`
   - 系统提示词：`你是产品助手，基于知识库回答用户关于产品的问题。回答要准确、简洁。`
   - 绑定知识库：选择"产品文档"
   - 绑定工具：`knowledge_search`
3. 保存

### 通过 API

```bash
curl -X POST http://localhost:8080/api/agents \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "产品助手",
    "systemPrompt": "你是产品助手，基于知识库回答用户关于产品的问题。",
    "knowledgeBaseIds": ["kb-xxx"],
    "tools": ["knowledge_search"]
  }'
```

### 对话测试

进入 **对话** 模块，选择"产品助手"，开始对话：

```
用户：产品支持哪些文件格式？
Agent：[调用 knowledge_search] → [基于检索结果生成回答]
```

## 完整示例：客服 Agent

组合以上能力，创建一个完整的客服 Agent：

```text
Agent 配置：
├── 系统提示词：客服角色定义 + 回答规范
├── 知识库：产品文档 + FAQ 文档
├── 工具：knowledge_search + http_request（查订单）
└── 工作流：复杂问题自动转人工
```

用户提问 → Agent 检索知识库 → 生成回答 → 无法回答时触发转人工工作流。

## 下一步

- [使用指南](../user/usage-guide.md) — 深入了解各功能
- [Agent 开发指南](../development/agent-development.md) — 开发自定义 Agent
- [插件开发指南](../development/plugin-guide.md) — 扩展系统能力

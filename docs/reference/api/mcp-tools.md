# MCP 工具文档

## 概述

AAF 通过 MCP（Model Context Protocol）标准协议暴露工具能力，Agent 可调用这些工具完成具体任务。MCP 工具支持本地注册和远程 MCP Server 接入。

## 内置工具

### 知识库检索

| 属性 | 值 |
|------|-----|
| 工具名 | `knowledge_search` |
| 描述 | 从指定知识库中语义检索相关文档片段 |

参数：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `query` | string | ✓ | 检索查询文本 |
| `knowledgeBaseId` | string | ✓ | 知识库 ID |
| `topK` | int | — | 返回条数（默认 5） |
| `threshold` | float | — | 相似度阈值（默认 0.7） |

返回：

```json
{
  "results": [
    {
      "content": "文档片段内容...",
      "score": 0.92,
      "source": "产品手册.pdf",
      "page": 15
    }
  ]
}
```

### 数据库查询

| 属性 | 值 |
|------|-----|
| 工具名 | `database_query` |
| 描述 | 执行只读 SQL 查询（自动加 LIMIT 防护） |

参数：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `sql` | string | ✓ | SQL 查询语句（仅 SELECT） |
| `params` | object | — | 参数化查询参数 |

### HTTP 请求

| 属性 | 值 |
|------|-----|
| 工具名 | `http_request` |
| 描述 | 调用外部 HTTP API |

参数：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `url` | string | ✓ | 请求 URL |
| `method` | string | — | HTTP 方法（默认 GET） |
| `headers` | object | — | 请求头 |
| `body` | object | — | 请求体 |
| `timeout` | int | — | 超时毫秒数（默认 30000） |

### 代码执行

| 属性 | 值 |
|------|-----|
| 工具名 | `code_execute` |
| 描述 | 在沙箱中执行代码（Python/JavaScript） |

参数：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `language` | string | ✓ | 语言（`python` / `javascript`） |
| `code` | string | ✓ | 代码内容 |
| `timeout` | int | — | 执行超时秒数（默认 30） |

### 工作流触发

| 属性 | 值 |
|------|-----|
| 工具名 | `workflow_execute` |
| 描述 | 触发指定工作流执行 |

参数：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `workflowId` | string | ✓ | 工作流 ID |
| `variables` | object | — | 输入变量 |

### 文件操作

| 属性 | 值 |
|------|-----|
| 工具名 | `file_read` |
| 描述 | 读取已上传文件的内容 |

参数：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `fileId` | string | ✓ | 文件 ID |
| `format` | string | — | 输出格式（`text` / `markdown` / `json`） |

### 发送通知

| 属性 | 值 |
|------|-----|
| 工具名 | `send_notification` |
| 描述 | 向指定用户发送站内通知 |

参数：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `userId` | string | ✓ | 目标用户 ID |
| `title` | string | ✓ | 通知标题 |
| `content` | string | ✓ | 通知内容 |
| `type` | string | — | 类型（`info` / `warning` / `urgent`） |

## 自定义工具注册

### Java 注册

```java
@Component
public class CustomTools {

    @Tool(description = "计算两个日期之间的工作日天数")
    public int workingDays(
        @Param(description = "开始日期 yyyy-MM-dd") String startDate,
        @Param(description = "结束日期 yyyy-MM-dd") String endDate
    ) {
        // 实现逻辑
    }
}
```

### MCP Server 接入

在 Agent 配置中添加远程 MCP Server：

```json
{
  "mcpServers": [
    {
      "name": "github",
      "url": "https://mcp.github.com",
      "apiKey": "${GITHUB_MCP_KEY}",
      "tools": ["search_repos", "create_issue", "list_prs"]
    }
  ]
}
```

## 工具权限控制

- 每个工具可配置允许调用的角色
- 敏感工具（如 `database_query`）默认需要人工审批
- 工具调用频率限制（防止 Agent 循环调用）
- 所有工具调用记录审计日志

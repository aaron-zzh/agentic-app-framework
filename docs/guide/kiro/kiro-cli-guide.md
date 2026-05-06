# Kiro CLI 使用指南

Kiro 是一个运行在终端中的 AI 开发助手，具备文件操作、代码分析、Shell 执行、多 Agent 协作、知识库管理、网络搜索等能力。本文档覆盖其全部功能，帮助你快速上手。

## 一、支持的模型

| 模型名称              | 类型        | 核心特点                                                                | 成本系数 (相对 Auto) | 适用场景                           |
| :-------------------- | :---------- | :---------------------------------------------------------------------- | :------------------- | :--------------------------------- |
| **Auto**              | 路由层      | **默认推荐**。智能调度多模型，交付 Sonnet 4 级质量，综合性价比最高。    | 1.0x (基准)          | 日常开发、自动化脚本、CI/CD        |
| **Opus 4.6**          | Claude      | 最强智能体，擅长复杂系统设计、安全工程、超长程任务（SWE-bench 顶尖）。  | ~2.2x                | 架构设计、深度 Debug、关键代码审查 |
| **Sonnet 4.6**        | Claude      | 高智商与实用性的平衡点，推理与编码能力大幅提升，性价比优于旧版 Opus。   | -                    | 复杂软件工程挑战、多系统权衡       |
| **Sonnet 4.7**        | Claude      | Sonnet 4.5 的升级版，接近 Opus 4.6 的智力，token 效率更高，适合长会话。 | -                    | 迭代式开发、团队主/子智能体管道    |
| **Sonnet 4.0**        | Claude      | 直接访问，无路由层，行为完全可预测。                                    | -                    | 依赖特定模型特性的工作流           |
| **Haiku 4.0**         | Claude      | 极速且接近前沿性能，在编码和推理上匹配 Sonnet 4，成本仅为其 1/3。       | ~0.4x                | 快速补全、简单代码生成、低延迟需求 |
| **Qwen3 Coder Next**  | Open Weight | 匹配前沿编码性能，覆盖从设计到审查的全生命周期，**性价比极高**。        | **0.25x**            | 成本敏感型开发、全流程自动化       |
| **DeepSeek Coder v3** | Open Weight | 专为智能体工作流设计，擅长长工具调用链和有状态会话。                    | 0.25x                | Agentic 编码、多步推理             |
| **StarCoder2 5B/7B**  | Open Weight | 多语言编程（Rust/Go/C++）和 UI 生成能力强。                             | **0.15x**            | 多语言项目、UI 代码生成            |
| **Granite Code 3B**   | Open Weight | **最便宜选项**，专为 CLI 长会话设计，具备错误恢复能力。                 | **0.05x**            | 极限成本控制、实验性任务           |

> **注**：成本系数为估算值（基于文档示例：Auto 10 credits ≈ Opus 22 credits ≈ Haiku 4 credits ≈ Qwen3 0.5 credits）。实际计费以 Kiro 后台为准。

## 二、安装

### 2.1 Windows

```shell
# 安装
irm 'https://cli.kiro.dev/install.ps1' | iex
## 卸载
kiro-cli uninstall
```

### 2.2 Ubuntu(ARM aarch64)

```bash
# 标准版本 (适用于 glibc >= 2.34，如 Ubuntu 22.04+)
curl --proto '=https' --tlsv1.2 -sSf 'https://desktop-release.q.us-east-1.amazonaws.com/latest/kirocli-aarch64-linux.zip' -o 'kirocli.zip'

# 解压并安装
unzip kirocli.zip
./kirocli/install.sh
```

## 三、登录认证

Kiro CLI 提供两种认证方式，适用于不同场景。

### 3.1 方式一：浏览器登录（交互式会话）

适用于日常开发，在终端中启动交互式对话。

```bash
# 登录（自动打开浏览器）
kiro-cli login

# 查看当前登录状态
kiro-cli whoami

# 退出登录
kiro-cli logout
```

**远程环境（SSH/无浏览器）**：自动切换为设备码流程，显示验证码和 URL，在其他设备的浏览器中完成认证。也可手动强制：

```bash
kiro-cli login --use-device-flow
```

### 3.2 方式二：API Key（非交互式/自动化）

适用于 CI/CD、脚本集成、Headless 模式等无法打开浏览器的场景。

```bash
# Linux / macOS
export KIRO_API_KEY=ksk_xxxxxxxx
kiro-cli chat --no-interactive "your prompt here"
```

> API Key 仅适用于非交互式模式（`--no-interactive`），交互式会话请使用浏览器登录。

## 四、启动与基本操作

### 4.1 启动会话

```bash
# 默认启动
kiro-cli chat

# 指定 Agent
kiro-cli chat --agent rust-expert

# 指定大模型 默认 auto 自动选择
kiro-cli chat --model <model-id>

# 带初始问题启动
kiro-cli chat "解释一下这个项目的架构"

# 恢复上次会话
kiro-cli chat --resume

# 选择历史会话恢复
kiro-cli chat --resume-picker
```

### 4.2 提示符说明

```
Kiro auto 6%
```

- **Kiro** — 当前 Agent 名称
- **auto** — 当前模型简称
- **6%** — 上下文窗口使用率（接近上限时会自动压缩）

### 4.3 快捷键

| 快捷键        | 功能                              |
| ------------- | --------------------------------- |
| `Ctrl+R`      | 搜索历史命令                      |
| `Ctrl+C`      | 取消当前操作或退出                |
| `Ctrl+G`      | 查看子 Agent 活动（Crew Monitor） |
| `Ctrl+X`      | 查看任务进度（Activity Tray）     |
| `Ctrl+O`      | 折叠/展开工具输出                 |
| `Shift+Enter` | 多行输入（取决于终端）            |
| `Up/Down`     | 浏览历史命令                      |

### 4.4 Session 会话管理

Kiro 会自动保存每一轮对话，无需手动操作。会话按项目目录隔离，每个目录有独立的会话历史。

**存储位置**：`~/.kiro/sessions/cli/`，每个会话包含：
- `{session_id}.json` — 会话元数据（目录、时间戳、状态）
- `{session_id}.jsonl` — 对话日志（追加写入）
- `{session_id}.lock` — 锁文件（会话活跃时存在）

**从命令行管理会话**：

```bash
# 恢复最近一次会话（自动恢复当时使用的模型）
kiro-cli chat --resume

# 交互式选择历史会话
kiro-cli chat --resume-picker

# 列出当前目录的所有会话
kiro-cli chat --list-sessions

# 删除指定会话
kiro-cli chat --delete-session <SESSION_ID>
```

**在对话中管理会话**：

| 命令                 | 说明                                       |
| -------------------- | ------------------------------------------ |
| `/chat save <path>`  | 导出当前会话到文件（支持 `.json`、`.zip`） |
| `/chat load <path>`  | 从文件导入其他会话记录                     |
| `/chat new [prompt]` | 开始新会话（旧会话自动保存，可恢复）       |
| `/clear`             | 清空对话历史（不创建新会话）               |

**`/chat new` 与 `/clear` 的区别**：

|                   | `/chat new` | `/clear` |
| ----------------- | ----------- | -------- |
| 创建新 Session ID | ✅           | ❌        |
| 旧会话可恢复      | ✅           | ❌        |
| 清空对话历史      | ✅           | ✅        |
| 重置上下文窗口    | ✅           | ✅        |

> 退出时（`/quit` 或 `Ctrl+C`）会话自动保存，下次用 `--resume` 即可恢复。

---

### Terminal UI

Kiro CLI 的默认的交互界面，它把现代 IDE 的流畅体验（Markdown 渲染、面板、快捷键）搬进了终端。结合你提供的文档，核心功能可以拆解为以下四个维度：

- **渲染能力**：支持**语法高亮**的 Markdown、代码块、表格，消息流式输出，支持方向键滚动查看长内容。
- **工具可视化**：执行 Shell、文件操作、代码分析时，会有专属的视觉组件展示进度和结果。

| 功能           | 操作                | 说明                                                 |
| :------------- | :------------------ | :--------------------------------------------------- |
| **Shell 逃逸** | 输入 `!ls -la`      | 直接执行系统命令，不经过 AI 代理，实时流式输出。     |
| **面板系统**   | `/help`, `/context` | 打开可搜索、可滚动的悬浮面板，按 `Esc` 退出。        |
| **全局监控**   | `Ctrl+X`            | 查看任务队列和进度；`Ctrl+G` 实时监控多 Agent 活动。 |
| **主题切换**   | `/theme`            | 支持 Dark、Light、Safe（ANSI 回退，适合 SSH）。      |

## 五、斜杠命令速查

### 5.1 会话管理

| 命令                 | 说明                           |
| -------------------- | ------------------------------ |
| `/chat save <path>`  | 保存当前会话到文件             |
| `/chat load <path>`  | 加载已保存的会话               |
| `/chat new [prompt]` | 开始新会话                     |
| `/clear`             | 清空对话历史                   |
| `/compact`           | 压缩对话历史（释放上下文空间） |
| `/quit` 或 `/exit`   | 退出                           |

### 5.2 Agent 与模型

| 命令                | 说明                                 |
| ------------------- | ------------------------------------ |
| `/agent [name]`     | 切换 Agent                           |
| `/agent create`     | AI 辅助创建新 Agent                  |
| `/model [name]`     | 切换模型                             |
| `/plan [prompt]`    | 切换到规划 Agent，用于任务拆解       |
| `/guide [question]` | 切换到指南 Agent，询问 Kiro 自身功能 |

### 5.3 工具与权限

| 命令                    | 说明                       |
| ----------------------- | -------------------------- |
| `/tools`                | 查看所有可用工具及权限状态 |
| `/tools trust <name>`   | 当前会话信任指定工具       |
| `/tools trust-all`      | 信任所有工具               |
| `/tools untrust <name>` | 取消信任                   |
| `/tools reset`          | 重置为默认权限             |

### 5.4 上下文管理

Kiro 的上下文管理包含三类：**Agent Resources 是“标配”，Session Context 是“草稿纸”，Knowledge Bases 是“外挂硬盘”**。

| 命令                     | 说明                                             |
| ------------------------ | ------------------------------------------------ |
| `/context`               | 查看上下文使用详情                               |
| `/context show`          | 查看当前加载的上下文文件（Agent资源+会话上下文） |
| `/context add <path>`    | 添加文件到上下文                                 |
| `/context remove <path>` | 移除上下文文件                                   |
| `/context clear`         | 清空所有上下文规则                               |

#### 🗂️ 三种上下文类型对比

| 类型                | 用途                                                                       | 配置方式                                                          | 生命周期                   |
| :------------------ | :------------------------------------------------------------------------- | :---------------------------------------------------------------- | :------------------------- |
| **Agent Resources** | 必需的项目文件：README、项目规范、通用配置等每次对话都需要的文件。         | 在 Agent 配置文件的 `resources` 字段中声明（需 `file://` 前缀）。 | 持久化，跟随 Agent 存在。  |
| **Session Context** | 当前任务的临时文件：当前对话需要的特定文件（如正在编写的代码、错误日志）。 | 在聊天中使用 `/context add` 命令临时添加。                        | 仅限当前会话，关闭即消失。 |
| **Knowledge Bases** | 大型代码库、完整文档集等会撑爆上下文窗口的超大内容。                       | 需先启用设置，使用 `/knowledge add` 导入。                        | 持久化，按需语义搜索。     |

#### 💡 核心机制与决策逻辑

1. **容量限制与自动保护**：普通 Context Files 有硬性限制（不超过模型上下文窗口的 75%），超限文件会被**自动丢弃**，这是为了防止任务因上下文溢出而失败。
2. **Knowledge Bases 不占窗口**：这是它与前两者的最大区别。KB 的内容平时不消耗 Token，仅在通过语义搜索命中时才被引入对话，专门用于解决“大象装不进冰箱”的问题。
3. **操作边界**：通过 `/context` 命令只能管理**会话级**的临时文件。想要永久添加或移除基础上下文，必须回头修改 Agent 的配置文件。

### 5.5 知识库

| 命令                           | 说明               |
| ------------------------------ | ------------------ |
| `/knowledge show`              | 查看已索引的知识库 |
| `/knowledge add <name> <path>` | 添加知识库         |
| `/knowledge remove <name>`     | 移除知识库         |
| `/knowledge update <path>`     | 更新已有知识库     |
| `/knowledge clear`             | 清空所有知识库     |
| `/knowledge cancel`            | 取消后台索引操作   |

### 5.6 代码智能

| 命令             | 说明                       |
| ---------------- | -------------------------- |
| `/code init`     | 初始化代码智能（启动 LSP） |
| `/code status`   | 查看 LSP 服务器状态        |
| `/code logs`     | 查看 LSP 日志              |
| `/code overview` | 生成代码库结构概览         |
| `/code summary`  | 生成完整的代码库文档       |

### 5.7 其他

| 命令              | 说明                           |
| ----------------- | ------------------------------ |
| `/help`           | 查看所有命令                   |
| `/hooks`          | 查看已配置的钩子               |
| `/mcp`            | 查看 MCP 服务器状态            |
| `/prompts [name]` | 查看/选择提示词模板            |
| `/usage`          | 查看账户用量和订阅信息         |
| `/feedback`       | 提交反馈                       |
| `/copy`           | 复制上一条回复到剪贴板         |
| `/editor`         | 用编辑器编写多行提示           |
| `/paste`          | 粘贴剪贴板中的图片             |
| `/reply`          | 用编辑器回复 AI 返回的复杂信息 |
| `/spawn <task>`   | 启动并行 Agent 会话            |
| `/theme`          | 切换主题                       |
| `/transcript`     | 在 $PAGER 中查看完整对话       |

---

## 六、核心能力详解

### 6.1 文件操作

Kiro 可以直接读写项目中的文件，无需使用 `cat`、`echo` 等 Shell 命令。

- **读取文件**：支持按行读取、指定范围、批量读取多个文件、浏览目录结构、读取图片。
- **写入文件**：支持创建新文件、查找替换、插入内容、追加内容。自动创建不存在的父目录。

Kiro CLI 支持直接在终端聊天中**分析图片内容**（需模型具备视觉能力），可以用于：

- **Debug 调试**：截图错误日志或异常界面，让 Kiro 分析原因。
- **代码生成**：上传架构图、流程图，转换为代码实现。
- **UI/设计评审**：讨论界面设计，生成对应的 HTML/CSS 代码。
- **文档理解**：解读图片中的代码片段或技术示意图。

#### 三种上传方式图像

| 方式         | 命令/操作                                                                            | 适用场景                   |
| :----------- | :----------------------------------------------------------------------------------- | :------------------------- |
| **拖拽上传** | 直接将图片文件拖入终端窗口                                                           | 最快捷，适合本地截图、照片 |
| **显式读取** | `kiro> Can you analyze /path/to/screenshot.png`<br>（自动触发 `fs_read` Image 模式） | 路径明确时，可配合具体指令 |
| **剪贴板**   | `/paste` 命令                                                                        | 直接粘贴剪贴板中的图片     |

### 6.2 代码搜索

**正则搜索（grep）**：在文件中搜索文本模式，支持文件类型过滤。
**文件查找（glob）**：按路径模式查找文件。

**代码智能（code）**：基于 AST 的代码分析，支持符号搜索、模式匹配、代码重写。

```text
> 查找 UserService 类的定义
> 搜索所有继承 BaseEntity 的类
> 把所有 public void setXxx 方法重命名
```

初始化代码智能后（`/code init`），还支持 LSP 能力：跳转定义、查找引用、悬停信息、自动补全、诊断。

### 6.3 Shell 命令执行

当其他工具无法完成任务时，Kiro 可以执行 Shell 命令。

```test
> 运行 mvn clean install
> 执行 git status
> 运行测试并分析结果
```

注意：命令输出是缓冲的（完成后才显示），不支持交互式命令（如 `sudo`、`npm init`）。

### 6.4 多 Agent 协作（Subagents）

Subagents 是可以自主执行复杂任务的专用 Agent，拥有独立的上下文、工具访问权限和决策能力，适合多步骤复杂操作。

#### 核心能力

| 能力              | 说明                                          |
| ----------------- | --------------------------------------------- |
| 自主执行          | 独立运行，自主程度取决于 Agent 配置           |
| 实时进度追踪      | 任务执行过程中实时显示状态更新                |
| 专用执行监控      | `Ctrl+G` 打开监控面板，查看工具调用和输出详情 |
| 核心工具访问      | 读文件、执行命令、写文件、MCP 工具            |
| 并行执行          | 多个 Subagent 同时运行，提升效率              |
| 任务依赖图（DAG） | 支持串行依赖和并行执行的混合任务图            |
| 细粒度权限控制    | 每个 Subagent 可独立配置工具权限              |
| 结果聚合          | 完成后自动将结果返回主 Agent                  |

#### 默认与自定义 Subagent

Kiro 内置默认 Subagent 处理通用任务。也可以指定自定义 Agent 配置作为 Subagent：

```text
> Use the backend agent to refactor the payment module
```

**可用的内置 Agent 角色**：

- `kiro_default` — 默认开发 Agent
- `kiro_planner` — 规划 Agent，擅长任务拆解
- `kiro_guide` — 指南 Agent，回答 Kiro 自身功能问题

**手动启动并行会话**：

```text
/spawn 分析 src/auth 目录的测试覆盖率
/spawn --name security-check 检查所有 API 端点的安全性
```

#### Subagent 工作流程

1. **任务分配** — 描述任务，Kiro 判断是否适合使用 Subagent
2. **初始化** — 根据 Agent 配置创建独立上下文和工具访问
3. **自主执行** — 独立完成任务，特定工具操作可能暂停等待用户确认
4. **进度更新** — 实时显示当前工作状态
5. **结果返回** — 完成后结果返回主 Agent

#### 可用工具

Subagent 运行在独立运行时环境，部分工具不可用：

| 可用 ✅                                  | 不可用 ❌                              |
| --------------------------------------- | ------------------------------------- |
| `read` — 读取文件和目录                 | `web_search` — 网络搜索               |
| `write` — 创建和编辑文件                | `web_fetch` — 抓取 URL                |
| `shell` — 执行 bash 命令                | `grep` — 搜索文件内容                 |
| `code` — 代码智能（符号搜索、查找引用） | `glob` — 按模式查找文件               |
| MCP 工具                                | `thinking`、`todo_list`、`use_aws` 等 |

#### 任务依赖图（DAG）

Subagent 支持将复杂任务拆解为有向无环图，独立任务并行执行，有依赖的任务按序执行：

```text
> Refactor the auth module — analyze dependencies first, then refactor each service, then run tests
```

```text
┌─────────────┐
│  1. Analyze  │
│ dependencies │
└──────┬───────┘
       │
┌──────▼───────┐
│ 2. Refactor  │
│   modules    │
└──────┬───────┘
       │
┌──────▼───────┐
│  3. Run and  │
│  fix tests   │
└──────────────┘
```

> 任务图在执行前一次性规划完成，执行过程中不可修改。

#### 配置 Subagent 访问权限

Subagent 的权限通过 Agent 配置文件中的 `toolsSettings.subagent` 字段控制，详见 [七、Agent 配置 → 7.3 关键字段说明](#七agent-配置)。

#### 执行监控

按 `Ctrl+G` 打开专用监控面板，实时查看每个 Subagent 的状态、工具调用和输出。

- `Ctrl+D` / `Ctrl+U` — 在多个 Subagent 间切换
- `q` — 返回主对话

### 6.5 知识库管理

Kiro 内置持久化知识库，支持语义搜索（MiniLM）和关键词搜索（BM25），跨会话可用。需先启用：

```bash
kiro-cli settings chat.enableKnowledge true
```

#### 索引类型

| 类型         | 适用场景               | 特点                     |
| ------------ | ---------------------- | ------------------------ |
| Fast（BM25） | 日志、配置、大型代码库 | 快速索引，精确关键词匹配 |
| Best（语义） | 文档、研究资料         | 理解语义，自然语言查询   |

设置默认索引类型：

```bash
kiro-cli settings knowledge.indexType Best
```

#### 基本操作

```bash
# 添加文件/目录到知识库
/knowledge add --name "项目文档" --path ./docs

# 指定索引类型和过滤模式
/knowledge add --name "源码" --path ./src --index-type Fast --include "**/*.java" --exclude "target/**"

# 查看所有条目和索引进度
/knowledge show

# 更新已有条目（保留原有过滤模式）
/knowledge update /path/to/updated/project

# 删除条目
/knowledge remove "项目文档"

# 取消后台索引
/knowledge cancel abc12345   # 取消指定操作
/knowledge cancel all        # 取消所有操作

# 清空全部（不可逆）
/knowledge clear
```

#### 工作原理

1. **模式过滤** — 按 include/exclude 模式筛选文件
2. **文件发现** — 递归扫描支持的文件类型
3. **内容提取** — 从文件中提取文本
4. **分块** — 大文件按 chunkSize 切分为可搜索的块
5. **后台索引** — 异步处理，不阻塞对话
6. **语义嵌入** — 用 all-MiniLM-L6-v2 模型生成向量（Best 模式）

搜索时返回按相关性排序的片段，不占上下文窗口，按需引入对话。

#### Agent 隔离

每个 Agent 有独立的知识库，互不干扰：

- 切换 Agent 时自动切换知识库
- Agent A 无法访问 Agent B 的知识
- Agent 配置中声明的 `knowledgeBase` 资源会在启动时自动索引

#### 存储位置

| 系统 | 路径 |
|------|------|
| Windows | `%LOCALAPPDATA%\kiro-cli\knowledge_bases\` |
| macOS | `~/Library/Application Support/kiro-cli/knowledge_bases/` |
| Linux | `~/.local/share/kiro-cli/knowledge_bases/` |

目录结构按 Agent 隔离：

```
knowledge_bases/
├── kiro_cli_default/          # 默认 Agent
│   ├── contexts.json
│   └── context-id-1/
│       ├── data.json
│       └── bm25_data.json
└── my-custom-agent_<code>/    # 自定义 Agent
    ├── contexts.json
    └── context-id-2/
        └── data.json
```

#### 配置参数

```bash
kiro-cli settings knowledge.maxFiles 10000              # 最大索引文件数
kiro-cli settings knowledge.chunkSize 512               # 分块大小（字符）
kiro-cli settings knowledge.chunkOverlap 128            # 块间重叠（字符）
kiro-cli settings knowledge.indexType Best               # 默认索引类型
kiro-cli settings knowledge.defaultIncludePatterns '["**/*.java", "**/*.md"]'
kiro-cli settings knowledge.defaultExcludePatterns '["target/**", "node_modules/**"]'
```

#### 在 Agent 配置中声明知识库

可在 Agent 的 `resources` 中声明 `knowledgeBase`，启动时自动索引，无需手动操作：

```json
{
  "resources": [
    {
      "type": "knowledgeBase",
      "source": "file://./docs",
      "name": "Documentation",
      "indexType": "best",
      "include": ["**/*.md"],
      "exclude": ["**/draft/**"],
      "autoUpdate": true
    }
  ]
}
```

`autoUpdate: true` 时每次 Agent 加载都会重新索引，确保内容最新。

#### 支持的文件类型

**文本**: .txt, .log, .rtf, .tex, .rst · **Markdown**: .md, .markdown, .mdx · **代码**: .java, .py, .js, .ts, .rs, .go, .rb, .c, .cpp, .kt, .cs, .sh, .html, .css, .sql 等 · **配置**: .json, .yaml, .yml, .toml, .ini, .conf, .properties, .env · **数据**: .csv, .tsv · **特殊**: Dockerfile, Makefile, LICENSE, CHANGELOG, README

#### 最佳实践

- 用描述性名称：`"api-documentation"` 而非 `"docs"`
- 添加目录而非单个文件，用 pattern 过滤
- 始终排除构建产物：`target/**`、`node_modules/**`、`.git/**`
- 大项目按逻辑拆分为多个知识条目
- 源文件变更后用 `/knowledge update` 重新索引

### 6.6 网络能力

**网络搜索**：查找训练数据之外的最新信息。

```text
> 搜索 Spring Boot 4 的最新变更
```

**网页抓取**：获取指定 URL 的内容，支持三种模式：

- `selective`（默认）— 围绕搜索词智能提取
- `truncated` — 前 8000 字符
- `full` — 完整内容（最大 10MB）

```text
> 读取 https://docs.spring.io/spring-boot/docs/current/reference/html/ 中关于 auto-configuration 的内容
```

---

### 项目记忆

Steering 通过 Markdown 文件（存放在 `.kiro/steering/` 目录）持久化存储项目规范，对所有 Kiro CLI 聊天会话生效，让你无需在每次对话中重复解释技术栈和代码风格，从而实现**跨会话的上下文一致性**。

- **一致性**：新老成员、不同会话，产出代码风格统一。
- **零重复**：告别每次对话的“技术栈自我介绍”。
- **团队对齐**：通过 Git 版本化 Steering 文件，实现团队规范同步。

#### 文件作用域与优先级

Steering 文件分为两级，**Workspace（项目级）优先级高于 Global（全局级）**。

支持 [AGENTS.md 标准](https://agents.md/)

| 类型          | 路径                         | 适用场景                                            |
| :------------ | :--------------------------- | :-------------------------------------------------- |
| **Workspace** | `项目根目录/.kiro/steering/` | **项目专属**：该项目的 API 规范、测试标准。         |
| **Global**    | `~/.kiro/steering/`          | **个人/团队通用**：个人编码习惯、团队统一安全基线。 |

> **冲突规则**：当同名指令冲突时，项目级文件会覆盖全局文件。团队可通过 MDM 或 Git 分发全局配置，同时允许项目本地自定义。

#### 基础示例

| 文件名           | 核心用途     | 关键内容                                               |
| :--------------- | :----------- | :----------------------------------------------------- |
| **product.md**   | **业务背景** | 产品目标、用户画像、核心功能（让 AI 懂业务）。         |
| **tech.md**      | **技术选型** | 语言、框架、数据库、工具链（锁定技术栈，避免乱推荐）。 |
| **structure.md** | **架构约束** | 目录结构、命名规范、导入规则（让生成代码“即插即用”）。 |

其他示例：

- API 标准 (api-standards.md)​ - 定义 REST 规范、错误响应格式、认证流程和版本策略。包括端点命名模式、HTTP 状态码用法以及请求/响应示例。
- 测试方法 (testing-standards.md)​ - 建立单元测试模式、集成测试策略、Mock 方法和覆盖率要求。记录首选的测试库、断言风格和测试文件组织结构。
- 代码风格 (code-conventions.md)​ - 规定命名模式、文件组织、导入顺序和架构决策。包括首选代码结构、组件模式以及应避免的反模式的示例。
- 安全指南 (security-policies.md)​ - 记录认证要求、数据验证规则、输入清理标准和漏洞预防措施。包括针对应用程序的特定安全编码实践。
- 部署流程 (deployment-workflow.md)​ - 概述构建流程、环境配置、部署步骤和回滚策略。包括 CI/CD 管道详情和特定环境要求。

**最佳实践**：**一域一文件**（如拆分为 API、测试、部署），避免单文件臃肿；**严禁写入密钥**。

## 七、Agent 配置

Agent 是 Kiro 的核心概念，通过 JSON 配置文件定义 Agent 的行为、工具、上下文和集成。

### 7.1 配置文件位置

- **项目级**：`.kiro/agents/<name>.json`（优先级高）
- **全局级**：`~/.kiro/agents/<name>.json`

### 7.2 配置结构

```json
{
  "name": "aaf-dev",
  "description": "AAF 框架开发 Agent",
  "prompt": "你是 AAF 框架的开发助手，遵循规范驱动开发流程...",
  "tools": ["fs_read", "fs_write", "execute_bash", "grep", "glob", "code"],
  "allowedTools": ["fs_read", "grep", "glob"],
  "resources": [
    "file://docs/reference/development.md",
    "file://README.md",
    "skill://.kiro/skills/**/SKILL.md"
  ],
  "hooks": {
    "agentSpawn": [{ "command": "git status" }]
  },
  "model": "claude-sonnet-4",
  "mcpServers": {
    "git": {
      "command": "mcp-server-git",
      "args": ["--stdio"]
    }
  },
  "keyboardShortcut": "ctrl+shift+a",
  "welcomeMessage": "AAF 开发助手已就绪，请描述你的需求。"
}
```

### 7.3 关键字段说明

| 字段                     | 说明                                                                        |
| ------------------------ | --------------------------------------------------------------------------- |
| `prompt`                 | 系统提示词，定义 Agent 的专业领域和行为。支持 `file:///path` 引用外部文件   |
| `tools`                  | 可用工具列表                                                                |
| `allowedTools`           | 自动批准的工具（免确认），支持通配符如 `fs_*`、`@git/*`                     |
| `resources`              | 上下文文件，`file://` 始终加载，`skill://` 按需加载                         |
| `hooks`                  | 钩子，在特定时机执行命令                                                    |
| `mcpServers`             | MCP 服务器配置                                                              |
| `keyboardShortcut`       | 快捷键切换到此 Agent                                                        |
| `welcomeMessage`         | 切换到此 Agent 时显示的欢迎消息                                             |
| `toolsSettings.subagent` | Subagent 权限配置，包含以下子字段：                                         |
| `availableAgents`        | 可被召唤为 Subagent 的 Agent 列表，支持 glob 通配符                         |
| `trustedAgents`          | 免确认直接运行的信任 Agent 列表                                             |
| `agentPermissions`       | 每个 Subagent 的工具白名单（`allowedTools`）和强制审批（`requireApproval`） |

### 7.4 Hooks 钩子

| 触发时机           | 说明                 |
| ------------------ | -------------------- |
| `agentSpawn`       | Agent 初始化时       |
| `userPromptSubmit` | 用户提交消息时       |
| `preToolUse`       | 工具执行前（可阻止） |
| `postToolUse`      | 工具执行后           |
| `stop`             | 回复完成时           |

### 7.5 MCP 服务器

支持本地（stdio）和远程（HTTP）两种模式：

```json
{
  "mcpServers": {
    "git": {
      "command": "mcp-server-git",
      "args": ["--stdio"]
    },
    "remote-api": {
      "url": "https://mcp.example.com/sse",
      "headers": { "Authorization": "Bearer $TOKEN" }
    }

  }
}
```

### 7.6 Agent Skills

Kiro Skills 是遵循 **Agent Skills 开放标准** 的“技能包”，本质是可复用的工作流指令集（如 PR 审查、部署流程）。它采用**按需加载**机制，能大幅扩展专业能力。

#### 核心机制：智能触发

- **自动匹配**：Kiro 启动时仅读取技能描述（Description）。当你的**自然语言指令**（如“review this PR”）匹配描述中的关键词时，会自动加载完整的 `SKILL.md` 执行。
- **手动查看**：使用 `/context show` 可查看当前可用的技能列表。
- **作用域与优先级**：

  - **Workspace**（`.kiro/skills/`）：项目独有，**优先级更高**。
  - **Global**（`~/.kiro/skills/`）：个人通用。
  - **冲突规则**：同名技能，项目级覆盖全局级。

### 技能结构标准

每个 Skill 是一个文件夹，必须包含 `SKILL.md` 文件。

**1. 文件结构：**

```bash
my-skill/
├── SKILL.md          # 核心指令文件
├── references/       # 拆分存放的详细文档（按需加载）
└── scripts/          # 可执行脚本
```

**2. SKILL.md 编写规范：**

文件由 **YAML Frontmatter** 和 **Markdown 正文**组成：

```markdown
---
name: pr-review
description: Review code changes for security and style. Use when user asks "review this PR" or "check my code".
---
# 技能正文：具体的工作步骤
1. 检查安全漏洞...
2. 验证代码风格...
```

- **Description 是关键**：必须包含用户可能使用的**触发短语**（如 "review", "check"），这是自动激活的匹配依据。
- **大文档拆分**：将详细规范放在 `references/` 目录下，在正文中通过相对路径引用，避免主文件臃肿。

#### 配置与集成

- **Default Agent**：自动加载全局和项目级技能，无需配置。
- **Custom Agent**：需在 Agent 配置文件的 `resources` 字段中显式声明路径：

1. **精准描述**：Description 要像“搜索引擎关键词”一样，准确描述技能用途和触发场景。
2. **团队共享**：将项目级技能（`.kiro/skills/`）提交到 Git，确保团队流程统一。
3. **渐进式加载**：利用 `references/` 目录拆分长文档，节省 Token 并提升响应速度。

---

## 八、全局设置

通过 CLI 命令管理设置：

```bash
# 查看所有可用设置
kiro-cli settings list --all

# 设置默认模型
kiro-cli settings chat.defaultModel "model-id"

# 设置默认 Agent
kiro-cli settings chat.defaultAgent aaf-dev

# 启用/禁用功能
kiro-cli settings chat.enableKnowledge true
kiro-cli settings chat.enableCodeIntelligence true
kiro-cli settings chat.enableSubagent true
kiro-cli settings chat.enableThinking true

# 删除设置
kiro-cli settings --delete chat.defaultModel

# 批量删除
kiro-cli settings --delete "knowledge.*"
```

设置优先级：Session > Workspace（`.kiro/settings/cli.json`）> Global（`~/.kiro/settings/cli.json`）

---

## 九、Headless 模式（自动化）

Kiro 支持非交互模式，适合 CI/CD 和脚本集成：

```bash
# 单次查询，自动信任所有工具
kiro-cli chat --no-interactive --trust-all-tools "运行 mvn test 并分析结果"

# 信任指定工具
kiro-cli chat --no-interactive --trust-tools=fs_read,grep "查找所有 TODO"
```

注意：Headless 模式下不支持交互式命令，必须提供初始查询，必须配置工具信任（否则会挂起）。

---

## 十、实用技巧

1. **先读后写**：修改代码前，Kiro 会先读取相关文件理解上下文，匹配项目风格
2. **用 `/context add` 加载关键文件**：让 Kiro 始终了解项目规范和架构
3. **用 `/compact` 释放空间**：对话过长时压缩历史，保留关键信息
4. **用 `/plan` 拆解复杂任务**：切换到规划 Agent，先拆解再执行
5. **用知识库索引文档**：将项目文档索引后，Kiro 可以跨会话检索
6. **用 `/chat save` 保存重要会话**：方便后续恢复和分享
7. **配置专属 Agent**：为项目创建专属 Agent，预设提示词、工具权限和上下文文件
8. **用 `/spawn` 并行处理**：多个独立任务可以同时执行

---

## 十一、日志与调试

日志文件位置（Windows）：`%TEMP%/kiro-log/logs/kiro-chat.log`

自定义日志路径：

```bash
set KIRO_CHAT_LOG_FILE=D:\logs\kiro-debug.log
kiro-cli chat
```

查看 LSP 日志：

```text
/code logs -l ERROR -n 50
```

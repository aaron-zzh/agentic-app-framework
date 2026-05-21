# AAF AI 能力封装全景

> 本文档说明 `framework/intelligent/ai/` 下各 AI 能力的封装方式、调用入口及实现状态。

## 封装原则

**接口与实现都在 `aaf-framework`，可选 SDK 用 `optional` 声明。**

参考芋道多文件存储模式：`FileClient` 接口 + `S3FileClient`/`LocalFileClient` 等实现全部在同一模块，可选 SDK（如 AWS S3）用 `optional` 声明，不强制下游引入。

AAF 遵循同样原则：

- `ai/chat/`、`ai/image/` 等子包：接口 + 实现都在 `aaf-framework`
- 依赖 Spring AI（已在 framework）的实现：直接放 framework，无条件注册
- 依赖可选厂商 SDK（阿里云 imageenhan 等）的实现：放 framework，SDK 依赖加 `optional`，用 `@ConditionalOnBean` 按需注册
- `aaf-api` 调用方只注入接口，不感知实现细节

## 分层架构

```
调用方（module/chat、module/agent、module/knowledge 等）
         ↓ 注入接口
┌─────────────────────────────────────────────────────────────┐
│  framework/intelligent/ai/                                   │
│                                                              │
│  chat/                  image/           speech/             │
│  ├─ ResilientChatService ├─ ImageGenerationService ├─ SpeechService │
│  └─ DynamicChatClientFactory └─ ImageProcessService          │
│                                                              │
│  embedding/             rerank/          media/              │
│  └─ EmbeddingService    └─ RerankService └─ VideoGenerationService │
└─────────────────────────────────────────────────────────────┘
         ↓ 读取配置
┌─────────────────────────────────────────────────────────────┐
│  ai_model 表（统一管理：apiKey / baseUrl / capabilities）     │
│  后台管理：aaf-api/module/model/AiModelService               │
└─────────────────────────────────────────────────────────────┘
```

## 按能力分类

### CHAT / VISION / VIDEO_UNDERSTANDING

> 同一入口，区别只是 `UserMessage` 里有没有 `Media` 附件。

**封装**：`chat/ResilientChatService`（降级 + Token 计量）+ `chat/DynamicChatClientFactory`（按 modelId 动态构建 ChatClient）

**实现状态**：✅ 已完成

**调用**：

```java
// 纯文本对话
resilientChatService.call(List.of(new UserMessage("问题")), "deepseek:chat", userId);

// 流式
resilientChatService.stream(messages, "openai:gpt-4o", userId)
    .map(r -> r.getResult().getOutput().getText())
    .subscribe(chunk -> sseEmitter.send(chunk));

// 图片理解（VISION）—— 传 Media 附件，选 capabilities 含 VISION 的模型
var media = new Media(MimeTypeUtils.IMAGE_JPEG, imageUrl);
resilientChatService.call(
    List.of(new UserMessage("描述这张图片", List.of(media))),
    "openai:gpt-4o", userId);
```

**模型选择**：调用方按 `ai_model.capabilities` 过滤，`ResilientChatService` 不感知能力类型。

---

### IMAGE_GEN（文生图）

**封装**：`image/ImageGenerationService`（接口）+ `image/SpringAiImageGenerationService`（实现，依赖 Spring AI ImageModel）

**实现状态**：✅ 接口 + 实现已完成，待接入 DynamicImageModelFactory 支持多模型动态切换

**调用**：

```java
@Autowired ImageGenerationService imageGenerationService;

var result = imageGenerationService.generate(
    new ImageGenerationService.ImageRequest("一只猫坐在月亮上", "openai:dall-e-3"));
String imageUrl = result.url();
```

**支持模型**：DALL-E 3（OpenAI）、wanx（通义万象，需引入 DashScope starter）

---

### IMAGE_PROCESS（图像处理）

> 云服务工具能力，非 LLM 生成。

**封装**：`image/ImageProcessService`（接口）+ `image/AliyunImageProcessService`（实现，依赖阿里云 imageenhan SDK，`optional`）

**实现状态**：✅ 接口 + 阿里云实现已完成

**前置条件**：配置 `AliyunImageenhanConfig` Bean（accessKeyId / accessKeySecret）

**调用**：

```java
@Autowired ImageProcessService imageProcessService;

// 色彩增强（同步）
var result = imageProcessService.process(
    new ImageProcessService.ProcessRequest("https://example.com/img.jpg", "COLOR_ENHANCE"));
String resultUrl = result.resultUrl();

// 卡通化（异步，需轮询）
var pending = imageProcessService.process(
    new ImageProcessService.ProcessRequest("https://example.com/img.jpg", "CARTOONIZE"));
// 轮询
var done = imageProcessService.queryTask(pending.taskId());
```

**支持的处理方式**：

| method | 说明 | 模式 | 费用参考 |
|--------|------|------|---------|
| `COLOR_ENHANCE` | 色彩增强 | 同步 | 20元/千次 |
| `CARTOONIZE` | 卡通化 | 异步 | 60元/千次 |

---

### EMBEDDING（向量化）

**封装**：`embedding/EmbeddingService`（接口，待实现）

**实现状态**：⚠️ 接口已定义，实现待完成（需 DynamicEmbeddingModelFactory）

**计划调用**：

```java
float[] vector = embeddingService.embed("需要向量化的文本", "qwen:embedding");
```

---

### AUDIO（ASR/TTS）

**封装**：`speech/SpeechService`（接口，待实现）

**实现状态**：⚠️ 接口已定义，实现待完成

**计划调用**：

```java
String text = speechService.transcribe(audioBytes, "zh");   // ASR
byte[] audio = speechService.synthesize("你好", "xiaoxiao"); // TTS
```

**计划实现**：`OpenAiSpeechService`（Spring AI）、`AliyunSpeechService`（阿里云 SDK，optional）

---

### RERANK（重排序）

**封装**：`rerank/RerankService`（接口，待实现）

**实现状态**：⚠️ 接口已定义，实现待完成

**计划调用**：

```java
// RAG 精排场景
var ranked = rerankService.rerank("用户问题", candidateDocs, 5);
```

**计划实现**：`HttpRerankService`（HTTP 直调硅基流动/Cohere/Jina）

---

### VIDEO_GEN（视频生成）

**封装**：`media/VideoGenerationService`（接口，待实现）

**实现状态**：⚠️ 接口已定义，实现待完成

**特点**：异步任务模式（提交 → 轮询 → 取结果），不同于同步的 CHAT/IMAGE_GEN。

**计划调用**：

```java
String taskId = videoGenerationService.submit(
    new VideoGenerationService.VideoGenerationRequest("一只猫在月亮上跳舞", null, 5));
VideoGenerationService.VideoResult result = videoGenerationService.query(taskId);
```

---

## 模型配置管理

所有 AI 能力的模型配置统一存储在 `ai_model` 表，通过后台管理界面维护：

| 字段 | 说明 |
|------|------|
| `modelId` | 唯一标识，如 `openai:gpt-4o`、`qwen:embedding` |
| `provider` | 来源标识，如 `openai`、`deepseek`、`openrouter` |
| `providerType` | 协议类型：`OPENAI_COMPAT` / `ANTHROPIC` / `OLLAMA` |
| `apiKey` | 加密存储 |
| `baseUrl` | API 地址，支持聚合平台自定义 |
| `capabilities` | 能力标记：`CHAT,VISION,EMBEDDING,IMAGE_GEN` 等 |
| `inputPricePerK` / `outputPricePerK` | Token 单价，用于积分结算 |

**第一期支持的 12 个 provider**（种子数据见 `V3__ai_model_enhance.sql`）：

| 优先级 | provider | providerType |
|--------|---------|-------------|
| P0 | openai / deepseek / qwen / moonshot | OPENAI_COMPAT |
| P1 | zhipu / anthropic / ollama / openrouter / n1n / linkai / stepfun / volcengine | OPENAI_COMPAT / ANTHROPIC / OLLAMA |

---

## 示例代码

完整调用示例见 `module/examples/image/`（图像能力示例，需配置 `aaf.examples.image.enabled=true`）。

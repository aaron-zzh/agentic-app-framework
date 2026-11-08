---
level: Practice
layer: Model
purpose: AAF-123 助理消息语音播放改为后端实时流式 TTS 的技术任务与接口契约
status: active
version: 1.0.0
date: 2026-11-08
author: AaronZZH & Kiro
---

# AAF-123 技术任务

风险等级：🟡 中（新增接口 + 修改前端播放逻辑），派发 developer + tester。

## 背景

助理对话框消息的语音播放默认走浏览器 `speechSynthesis`，音质随系统变化，Chrome 长文本约 15 秒会中断。前端已有 `ttsMode="server"` 分支，但存在两个缺陷：

- 前端请求的 `POST /api/voice/tts/stream` 在后端不存在（`VoiceController` 只有 `/stt`），返回 404。
- `ChatterThread.tsx` 的 `handleServerPlay` 对每个网络分片单独 `decodeAudioData`，PCM 裸流无法解码，且分片之间没有排队，会叠音。

## 接口契约

`POST /api/voice/tts/stream`

- 认证：登录用户（`isAuthenticated()`），与 `/api/voice/stt` 同一 Controller。
- 请求体（#12301 初版，#12304 起改为 `{ threadId, messageId, voice }` 并返回分帧流）：`{ "text": string, "voice": string }`。`text` 非空，长度上限 2000 字符；`voice` 可空，空时使用后端默认音色 `aaf.speech.tts.voice`（默认 `longanhuan_v3`），必须是 CosyVoice 音色。
- 响应：`Content-Type: application/octet-stream`，分块返回原始 PCM，16000Hz、单声道、16bit 小端（`SpeechSynthesisAudioFormat.PCM_16000HZ_MONO_16BIT`）。
- 计费：合成前按 `SpeechService.estimateCost` 预检积分，流正常完成后结算，能力码 `CAP_SPEECH_TTS`，做法对齐 `DeviceVoiceGatewayService.synthesize`。
- 错误：参数非法返回 400；积分不足沿用现有积分异常的错误码。

## 任务

- [x] **#12301** 新增 `POST /api/voice/tts/stream` 流式合成接口 — developer-service
  - 复用 `SpeechService.startTtsTask`（实时连接池，固定输出 PCM16 16kHz）。`synthesizeStream` 实际输出 MP3，不满足契约，已弃用此方案。
  - 预检与结算复用 `AiCreditGuard` 现有方法。
  - 单测覆盖参数校验、预检失败、正常完成后结算、流出错不结算。

- [x] **#12302** 重写助理消息服务端 TTS 播放并调整默认模式 — developer-webui
  - `serverTtsStream` 支持 `AbortSignal`；处理分片 `byteOffset`，奇数字节跨分片拼接。
  - PCM s16le 转 Float32，按 `nextStartTime` 顺序排队播放；提供停止、播放中状态，卸载或停止时关闭 `AudioContext`。
  - 播放逻辑抽成独立 hook，`ChatterThread.tsx` 只负责接线。
  - `useVoiceConfig` 默认 `ttsMode` 改为 `"server"`；用户手动选的 `"browser"` 继续走 `SpeechOutput`。访客模式现状是不显示播放按钮，保持不变。
  - 单测覆盖 PCM 转换与跨分片拼接。

- [ ] **#12304** TTS 接口改为按消息合成并返回字级时间戳 — developer-service
  - 请求体由 `{ text, voice }` 改为 `{ threadId, messageId, voice }`，直接替换，不保留 `text` 字段（禁兼容层）。消息定位和归属校验复用 `MessageFeedbackService` 的 `threadId + aguiMessageId` 查找方式。
  - 后端从消息中提取可朗读文本（去 Markdown 标记、跳过代码块），上限仍为 2000 字符；这份文本是时间戳偏移的唯一基准。
  - 合成时开 `enableWordTimestamp`，在 framework 层把 `SpeechSynthesisResult.getTimestamp()` 经 `TtsListener` 透传出来。
  - 响应改为长度前缀分帧的二进制流：`[1 字节类型][4 字节长度][载荷]`，类型 0 = PCM，类型 1 = JSON（首帧为可朗读文本，后续为 `{text, beginMs, endMs}` 词列表）。
  - 开工前先核实：默认音色是否支持字级时间戳；不支持时改用支持的音色，或退化为句级（以句子边界切分，沿用 `SentenceSplitter`）。
  - 单测：消息不存在或不属于当前用户、可朗读文本提取、分帧编码、时间戳透传。

- [ ] **#12305** 播放时按字高亮 — developer-webui
  - `serverTtsStream` 按帧头解析：PCM 帧走现有排队播放，时间帧存入会话。
  - 播放中用 `AudioContext.currentTime` 减去会话起点，在 `requestAnimationFrame` 中查出当前词，经 CSS Custom Highlight API 设置"已读/当前"范围，不改动 Markdown 渲染出的 DOM。
  - 把可朗读文本按顺序匹配到气泡内的文本节点（跳过代码块），得到字符偏移到 `Range` 的映射。
  - 停止、切换消息、播放结束时清除高亮。
  - 开工前核实目标浏览器对 `CSS.highlights` 的支持；不支持时不高亮，只播放。
  - 仅服务端模式有高亮；浏览器 `speechSynthesis` 模式不在本任务范围。
  - 单测：帧解析、当前词查找、偏移到节点的映射。

- [ ] **#12303** 验收测试 — tester（依赖 #12304、#12305）
  - 后端：接口鉴权、参数校验、成功返回分帧流、积分结算、他人消息被拒。
  - 前端：播放、停止、切换消息时停止上一条、播放时高亮随音频推进。

## 非目标

- LLM 边生成边朗读（双向流式），需要时另立用户故事，复用 `module.voice` 的 `SentenceSplitter` 与 WebSocket 会话。
- 访客模式的语音播放。

# AAF-123 开发记录

## #12302 重写助理消息服务端 TTS 播放并调整默认模式

✅ 2026-11-08 — developer-webui

- `useServerTts`：PCM 排队播放，停止/卸载释放，全局单播
- `lib/utils/audio` 增 PCM16 解码与奇数字节跨片拼接
- `serverTtsStream` 增 AbortSignal，按 byteOffset 切片
- 默认 ttsMode 改 server；persist v1 迁移旧 browser 值
- 决策：v0 无界面可改 ttsMode，旧值必为默认，可安全迁移
- 协调者补：默认 ttsVoice 由 Cherry 改空串，Cherry 非 CosyVoice 音色

> **注意**：仅手动点击播放，不自动朗读后续消息；按用户要求本次未跑 test/lint/check，需协调者补跑 `pnpm check:affected`

## #12301 新增 POST /api/voice/tts/stream 流式合成接口

✅ 2026-11-08 — developer-service

- 新增 `VoiceTtsStreamService`：预检→合成→完成后结算
- 决策：改用 `startTtsTask`，`synthesizeStream` 实为 MP3（用户确认方案 A）
- 响应用 `StreamingResponseBody` 逐块 flush；MVC 下 Flux 非 SSE 会整体缓冲
- 模型 id 提取为 `SpeechService.REALTIME_TTS_MODEL_ID`，设备侧同引用
- 结算在 SDK 回调线程执行，仅依赖显式 userId

> **注意**：按用户要求本次未跑 test/lint/check，需协调者补跑 `pnpm nx test service` 与 `pnpm check:affected`；改动 7 个文件（含 2 个测试），跨 framework 与 device 模块

---
level: Practice
layer: Product
purpose: AAF-046 语音交互的技术任务清单
status: pending
version: 1.0.0
date: 2026-05-19
author: AaronZZH
---

# 语音交互（AAF-046）

> 负责人：developer-webui + developer-service | 创建：05-19

## 任务列表

1. [ ] #4601 语音输入（STT）
   - Web Speech API 集成（浏览器原生）
   - Whisper API 集成（服务端转写）
   - 录音 UI（按住说话/点击录音）、音频波形可视化
   - verify: 语音录入→文字转写→发送流程通过

2. [ ] #4602 语音输出（TTS）
   - TTS API 集成（OpenAI TTS/Azure TTS）
   - 音频流式播放、播放控制（暂停/停止/倍速）
   - 自动朗读模式（AI 回复自动播放）
   - verify: AI 回复可语音播放

3. [ ] #4603 实时语音对话
   - WebSocket 双向音频流
   - VAD（语音活动检测）自动断句
   - 打断机制（用户说话时停止 AI 播放）
   - verify: 实时对话流畅，延迟 <2s

4. [ ] #4604 语音设置
   - 语音/语言选择、语速调节
   - 静音/免提模式
   - 语音唤醒词（可选）
   - verify: 设置项生效

5. [ ] #4605 音频消息
   - 语音消息录制与发送（非实时场景）
   - 语音消息播放、转文字展示
   - 音频文件存储（对接 AAF-039 存储服务）
   - verify: 语音消息录制→发送→播放→转文字流程通过

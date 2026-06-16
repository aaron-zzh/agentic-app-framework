/**
 * 语音交互模块——barrel export
 *
 * 包含：
 * - SpeechInput：语音输入（STT）
 * - SpeechOutput：语音输出（TTS）
 * - RealtimeVoice：实时语音对话
 * - VoiceSettings：语音设置
 * - AudioRecorder / AudioPlayer：音频消息录制与播放
 *
 * @author AaronZZH & Kiro
 */

export { AudioPlayer, AudioRecorder } from "./AudioMessage"
export { RealtimeVoice } from "./RealtimeVoice"
export { SpeechInput } from "./SpeechInput"
export { SpeechOutput } from "./SpeechOutput"
export type { VoiceSettingsValue } from "./VoiceSettings"
export { VoiceSettings } from "./VoiceSettings"
export { WsAsrButton } from "./WsAsrButton"

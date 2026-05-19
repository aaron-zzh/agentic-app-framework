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

export { SpeechInput } from "./SpeechInput"
export { SpeechOutput } from "./SpeechOutput"
export { RealtimeVoice } from "./RealtimeVoice"
export { VoiceSettings } from "./VoiceSettings"
export type { VoiceSettingsValue } from "./VoiceSettings"
export { AudioRecorder, AudioPlayer } from "./AudioMessage"

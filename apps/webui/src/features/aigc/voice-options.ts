/**
 * 配音音色选项与文本约束——配音生成页与生成面板共用，避免重复定义
 * @author AaronZZH & Kiro
 */

/** 配音文本最大长度（字），与后端 VOICE_TEXT_MAX_LEN 保持一致 */
export const VOICE_TEXT_MAX_LEN = 200

/** 可选音色（对应后端 DashScope cosyvoice-v3-flash 音色） */
export const VOICES = [
  { value: "longxiaochun_v3", label: "龙小淳（知性女）" },
  { value: "longanyang", label: "龙安阳（阳光男）" },
  { value: "longanhuan", label: "龙安欢（元气女）" },
  { value: "longyingling_v3", label: "龙樱凌（温和女）" },
  { value: "longshuo_v3", label: "龙硕（干练男）" }
] as const

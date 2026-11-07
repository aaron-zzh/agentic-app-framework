/**
 * 知识库字段展示文案：知识库说明（用户填写）与涵盖主题（系统自动生成）
 * @author AaronZZH & Kiro
 *
 * @example
 * <label>{KNOWLEDGE_DESCRIPTION_COPY.label}</label>
 * <Textarea placeholder={KNOWLEDGE_DESCRIPTION_COPY.placeholder} />
 */

/** 知识库说明：用户填写，系统不会修改。 */
export const KNOWLEDGE_DESCRIPTION_COPY = {
  label: "知识库说明",
  help: "写给助理看的定位：这个库面向谁、回答哪类问题、不适用哪些问题。上传文档不会改动这里。",
  placeholder:
    "面向居家养老家属的糖尿病护理资料，回答饮食、用药提醒、血糖监测问题；不含诊断与处方建议。"
} as const

/** 涵盖主题：系统根据已入库文档自动整理，只读。 */
export const KNOWLEDGE_AI_SUMMARY_COPY = {
  label: "涵盖主题（自动生成）",
  help: "系统根据已入库文档自动整理，上传或删除文档后几分钟内更新，助理据此判断何时检索。",
  empty: "上传文档后自动生成"
} as const

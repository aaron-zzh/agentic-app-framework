/**
 * 字典类型编码常量
 * @author AaronZZH & Kiro
 *
 * 对应后端 com.xuejiai.aaf.common.constant.DictType，供 useDict() 调用时引用，
 * 避免字典类型编码散落为裸字符串。按业务模块分组，与后端保持同名同构。
 * 其余模块字典编码迁移到此文件时按需补充，不预先占位。
 *
 * 用法：
 * ```tsx
 * const { getLabel } = useDict(DictType.Aigc.TASK_TYPE)
 * ```
 */
export const DictType = {
  Sys: {
    /** 待办分类，见 TodoCategoryEnum */
    TODO_CATEGORY: "sys_todo_category",
    /** 待办状态，见 TodoStatusEnum */
    TODO_STATUS: "sys_todo_status"
  },
  Ai: {
    /** 文案输出语言，见 AssistantOutputLocale。 */
    COPYWRITING_OUTPUT_LOCALE: "ai.copywriting.output-locale"
  },
  Aigc: {
    /** AIGC 任务类型，见 AigcTaskType（features/aigc/types.ts） */
    TASK_TYPE: "aigc_task_type",
    /** AIGC 任务状态，见 AigcTaskStatus（features/aigc/types.ts） */
    TASK_STATUS: "aigc_task_status"
  }
} as const

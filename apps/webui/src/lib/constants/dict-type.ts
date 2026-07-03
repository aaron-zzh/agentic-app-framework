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
  Aigc: {
    /** AIGC 任务类型，见 AigcTaskType（features/aigc/types.ts） */
    TASK_TYPE: "aigc_task_type",
    /** AIGC 任务状态，见 AigcTaskStatus（features/aigc/types.ts） */
    TASK_STATUS: "aigc_task_status"
  }
} as const

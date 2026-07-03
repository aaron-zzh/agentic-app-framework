/**
 * AIGC 实体配置
 * @author AaronZZH
 */

import { DictType } from "@/lib/constants/dict-type"
import type { EntityDef } from "../types"

/** AIGC 生成任务 */
export const aigcTaskEntity: EntityDef = {
  slug: "aigc-task",
  label: "AIGC 任务",
  labelPlural: "AIGC 任务",
  apiPath: "/aigc/tasks",
  icon: "wand-2",
  group: "aigc",
  groupLabel: "AIGC",
  access: { read: true, create: false, update: false, delete: true },
  fields: [
    { type: "number", name: "userId", label: "用户 ID", readOnly: true },
    {
      type: "select",
      name: "type",
      label: "类型",
      readOnly: true,
      dictType: DictType.Aigc.TASK_TYPE
    },
    {
      type: "select",
      name: "status",
      label: "状态",
      readOnly: true,
      dictType: DictType.Aigc.TASK_STATUS
    },
    { type: "text", name: "provider", label: "供应商", readOnly: true },
    { type: "text", name: "model", label: "模型", readOnly: true },
    { type: "text", name: "prompt", label: "Prompt", readOnly: true },
    { type: "text", name: "resultUrl", label: "结果 URL", readOnly: true },
    { type: "text", name: "errorMsg", label: "失败原因", readOnly: true },
    { type: "number", name: "projectId", label: "项目 ID", readOnly: true }
  ],
  listView: {
    serverPagination: true,
    columns: ["userId", "type", "status", "provider", "model", "createTime"],
    defaultSort: "createTime:desc",
    filterableFields: ["type", "status"],
    quickFilters: [
      { label: "图像", field: "type", operator: "eq", value: "IMAGE" },
      { label: "视频", field: "type", operator: "eq", value: "VIDEO" },
      { label: "等待中", field: "status", operator: "eq", value: "PENDING" },
      { label: "失败", field: "status", operator: "eq", value: "FAIL" }
    ]
  },
  mixins: ["baseEntity"]
}

export const aigcEntities: EntityDef[] = [aigcTaskEntity]

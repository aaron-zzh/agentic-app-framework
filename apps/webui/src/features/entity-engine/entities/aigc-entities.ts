/**
 * AIGC 实体配置
 * @author Kiro
 */

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
      options: [
        { label: "图像", value: "IMAGE", color: "blue" },
        { label: "视频", value: "VIDEO", color: "purple" },
        { label: "3D 模型", value: "MODEL_3D", color: "orange" },
        { label: "音乐", value: "MUSIC", color: "green" }
      ]
    },
    {
      type: "select",
      name: "status",
      label: "状态",
      readOnly: true,
      options: [
        { label: "等待中", value: "PENDING", color: "gray" },
        { label: "运行中", value: "RUNNING", color: "blue" },
        { label: "成功", value: "SUCCESS", color: "green" },
        { label: "失败", value: "FAIL", color: "red" }
      ]
    },
    { type: "text", name: "provider", label: "供应商", readOnly: true },
    { type: "text", name: "model", label: "模型", readOnly: true },
    { type: "text", name: "prompt", label: "Prompt", readOnly: true },
    { type: "text", name: "resultUrl", label: "结果 URL", readOnly: true },
    { type: "text", name: "errorMsg", label: "失败原因", readOnly: true },
    { type: "number", name: "projectId", label: "项目 ID", readOnly: true }
  ],
  listView: {
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

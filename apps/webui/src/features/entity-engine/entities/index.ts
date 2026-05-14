/**
 * 示例实体配置——用于开发阶段验证视图引擎
 * @author AaronZZH & Kiro
 */

import type { EntityDef } from "../types"

/** 文档实体 */
export const documentEntity: EntityDef = {
  slug: "document",
  label: "文档",
  labelPlural: "文档",
  apiPath: "/api/documents",
  icon: "file-text",
  group: "内容管理",
  fields: [
    { type: "text", name: "title", label: "标题", required: true },
    {
      type: "select",
      name: "status",
      label: "状态",
      options: [
        { label: "草稿", value: "draft", color: "gray" },
        { label: "已发布", value: "published", color: "green" },
        { label: "已归档", value: "archived", color: "orange" },
      ],
    },
    { type: "relationship", name: "author", label: "作者", relationTo: "user" },
    { type: "richText", name: "content", label: "内容" },
  ],
  listView: {
    columns: ["title", "status", "author", "updateTime"],
    defaultSort: "updateTime:desc",
    searchableFields: ["title", "content"],
    filterableFields: ["status", "author"],
    batchActions: ["delete", "archive"],
  },
  kanbanView: { statusField: "status", cardTitle: "title" },
  formView: {
    autosave: { enabled: true, debounceMs: 2000 },
  },
  mixins: ["baseEntity"],
}

/** 用户实体 */
export const userEntity: EntityDef = {
  slug: "user",
  label: "用户",
  labelPlural: "用户",
  apiPath: "/api/system/users",
  icon: "users",
  group: "系统管理",
  fields: [
    { type: "text", name: "username", label: "用户名", required: true },
    { type: "text", name: "nickname", label: "昵称", required: true },
    { type: "email", name: "email", label: "邮箱" },
    { type: "text", name: "phone", label: "手机号" },
    {
      type: "select",
      name: "status",
      label: "状态",
      options: [
        { label: "正常", value: "active", color: "green" },
        { label: "禁用", value: "disabled", color: "red" },
      ],
    },
  ],
  listView: {
    columns: ["username", "nickname", "email", "status", "createTime"],
    defaultSort: "createTime:desc",
    searchableFields: ["username", "nickname", "email"],
    filterableFields: ["status"],
    batchActions: ["delete"],
  },
  mixins: ["baseEntity"],
}

/** 任务实体 */
export const taskEntity: EntityDef = {
  slug: "task",
  label: "任务",
  labelPlural: "任务",
  apiPath: "/api/tasks",
  icon: "check-square",
  group: "项目管理",
  fields: [
    { type: "text", name: "title", label: "标题", required: true },
    { type: "textarea", name: "description", label: "描述" },
    {
      type: "select",
      name: "status",
      label: "状态",
      options: [
        { label: "待办", value: "todo", color: "gray" },
        { label: "进行中", value: "in_progress", color: "blue" },
        { label: "已完成", value: "done", color: "green" },
      ],
    },
    {
      type: "select",
      name: "priority",
      label: "优先级",
      options: [
        { label: "低", value: "low", color: "gray" },
        { label: "中", value: "medium", color: "orange" },
        { label: "高", value: "high", color: "red" },
      ],
    },
    { type: "relationship", name: "assignee", label: "负责人", relationTo: "user" },
    { type: "date", name: "dueDate", label: "截止日期" },
  ],
  listView: {
    columns: ["title", "status", "priority", "assignee", "dueDate"],
    defaultSort: "createTime:desc",
    searchableFields: ["title", "description"],
    filterableFields: ["status", "priority", "assignee"],
    batchActions: ["delete"],
  },
  kanbanView: { statusField: "status", cardTitle: "title", cardDescription: "description" },
  mixins: ["baseEntity"],
}

/** 所有示例实体 */
export const sampleEntities: EntityDef[] = [documentEntity, userEntity, taskEntity]

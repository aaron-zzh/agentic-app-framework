/**
 * 实体 Mock 数据——开发阶段使用，后端就绪后删除
 * @author AaronZZH & Kiro
 */

export const _mockEntityData: Record<string, Record<string, unknown>[]> = {
  document: [
    { id: "1", title: "Q2 季度报告", status: "published", updatedAt: "2026-05-17T10:00:00Z" },
    { id: "2", title: "产品设计文档", status: "draft", updatedAt: "2026-05-16T14:00:00Z" },
    { id: "3", title: "技术架构说明", status: "published", updatedAt: "2026-05-15T09:00:00Z" },
    { id: "4", title: "用户调研报告", status: "archived", updatedAt: "2026-05-10T11:00:00Z" },
    { id: "5", title: "迭代计划 v0.2", status: "draft", updatedAt: "2026-05-17T16:00:00Z" }
  ],
  user: [
    { id: "1", username: "admin", nickname: "管理员", email: "admin@aaf.dev", status: "active" },
    { id: "2", username: "alice", nickname: "Alice", email: "alice@aaf.dev", status: "active" },
    { id: "3", username: "bob", nickname: "Bob", email: "bob@aaf.dev", status: "inactive" }
  ],
  task: [
    {
      id: "1",
      title: "完成登录功能",
      status: "done",
      priority: "high",
      assignee: { id: "2", nickname: "Alice", username: "alice" },
      dueDate: "2026-05-10",
      createTime: "2026-05-01T09:00:00Z",
      updateTime: "2026-05-10T18:30:00Z",
      updateBy: { id: "2", nickname: "Alice", username: "alice" },
      createBy: { id: "1", nickname: "管理员", username: "admin" }
    },
    {
      id: "2",
      title: "设计数据库 Schema",
      status: "in_progress",
      priority: "high",
      assignee: { id: "3", nickname: "Bob", username: "bob" },
      dueDate: "2026-05-20",
      createTime: "2026-05-05T10:00:00Z",
      updateTime: "2026-05-18T14:20:00Z",
      updateBy: { id: "3", nickname: "Bob", username: "bob" },
      createBy: { id: "1", nickname: "管理员", username: "admin" }
    },
    {
      id: "3",
      title: "编写单元测试",
      status: "todo",
      priority: "medium",
      assignee: { id: "2", nickname: "Alice", username: "alice" },
      dueDate: "2026-05-25",
      createTime: "2026-05-08T11:00:00Z",
      updateTime: "2026-05-08T11:00:00Z",
      updateBy: { id: "1", nickname: "管理员", username: "admin" },
      createBy: { id: "1", nickname: "管理员", username: "admin" }
    },
    {
      id: "4",
      title: "部署测试环境",
      status: "todo",
      priority: "low",
      assignee: { id: "1", nickname: "管理员", username: "admin" },
      dueDate: "2026-05-30",
      createTime: "2026-05-10T09:00:00Z",
      updateTime: "2026-05-15T16:00:00Z",
      updateBy: { id: "2", nickname: "Alice", username: "alice" },
      createBy: { id: "1", nickname: "管理员", username: "admin" }
    },
    {
      id: "5",
      title: "前端性能优化",
      status: "in_progress",
      priority: "medium",
      assignee: { id: "3", nickname: "Bob", username: "bob" },
      dueDate: "2026-05-22",
      createTime: "2026-05-12T14:00:00Z",
      updateTime: "2026-05-19T09:00:00Z",
      updateBy: { id: "3", nickname: "Bob", username: "bob" },
      createBy: { id: "2", nickname: "Alice", username: "alice" }
    },
    {
      id: "6",
      title: "接口文档整理",
      status: "todo",
      priority: "low",
      assignee: { id: "2", nickname: "Alice", username: "alice" },
      dueDate: "2026-06-01",
      createTime: "2026-05-15T10:00:00Z",
      updateTime: "2026-05-15T10:00:00Z",
      updateBy: { id: "1", nickname: "管理员", username: "admin" },
      createBy: { id: "1", nickname: "管理员", username: "admin" }
    }
  ]
}

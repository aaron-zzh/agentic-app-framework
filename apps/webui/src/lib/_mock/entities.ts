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
    { id: "1", title: "完成登录功能", status: "done", priority: "high", assignee: "alice" },
    {
      id: "2",
      title: "设计数据库 Schema",
      status: "in_progress",
      priority: "high",
      assignee: "bob"
    },
    { id: "3", title: "编写单元测试", status: "todo", priority: "medium", assignee: "alice" },
    { id: "4", title: "部署测试环境", status: "todo", priority: "low", assignee: "admin" }
  ]
}

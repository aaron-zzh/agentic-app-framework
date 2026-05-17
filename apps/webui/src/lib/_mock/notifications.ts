/**
 * 通知 Mock 数据
 * @author AaronZZH & Kiro
 *
 * TODO: 后续替换为 GET /api/notifications 接口
 */

export interface Notification {
  id: string
  type: "approval" | "system" | "mention" | "task"
  title: string
  description?: string
  isUnRead: boolean
  createdAt: string
  avatarUrl?: string
}

export const _notifications: Notification[] = [
  {
    id: "1",
    type: "approval",
    title: "张三提交了报销单",
    description: "金额 ¥3,200，等待您审批",
    isUnRead: true,
    createdAt: "2026-05-17T15:20:00Z"
  },
  {
    id: "2",
    type: "mention",
    title: "李四在文档中 @了您",
    description: "Q2 季度报告需要您确认数据",
    isUnRead: true,
    createdAt: "2026-05-17T14:30:00Z"
  },
  {
    id: "3",
    type: "task",
    title: "您有一条待处理任务已逾期",
    description: "客户跟进 - 腾讯科技",
    isUnRead: true,
    createdAt: "2026-05-17T10:00:00Z"
  },
  {
    id: "4",
    type: "system",
    title: "系统将于今晚 22:00 维护",
    description: "预计维护时间 30 分钟",
    isUnRead: false,
    createdAt: "2026-05-17T09:00:00Z"
  },
  {
    id: "5",
    type: "approval",
    title: "王五的请假申请已通过",
    isUnRead: false,
    createdAt: "2026-05-16T16:00:00Z"
  }
]

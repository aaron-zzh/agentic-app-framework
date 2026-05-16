import { alovaInstance } from './core/instance'

export interface MessageItem {
  id: number
  title: string
  content: string
  type: 'system' | 'chat' | 'notice'
  read: boolean
  createdAt: string
}

export interface MessagePageResult {
  data: MessageItem[]
  total: number
}

/** 分页获取消息列表 */
export function getMessageList(page: number, pageSize: number) {
  return alovaInstance.Get<MessagePageResult>('/mock/messages', {
    params: { page, pageSize },
  })
}

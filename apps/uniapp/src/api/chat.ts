/**
 * AI 对话接口
 * 参考 zhiyuan-app/src/api/ai/chat.ts
 */
import { alovaInstance } from './core/instance'

export interface ChatConversation {
  id: number
  userId: number
  title: string
  roleId?: number
  modelName?: string
  roleAvatar?: string
}

export interface ChatMessage {
  id: number
  conversationId: number
  /** 'user' | 'assistant' */
  type: string
  content: string
  tokens?: number
  createTime: number
  roleAvatar?: string
  userAvatar?: string
}

// ===== 会话管理 =====

/** 获取或创建默认对话 */
export function getOrCreateChat(title = 'AI 对话') {
  return alovaInstance.Get<ChatConversation>('/ai/chat/conversation/get-my', {
    params: { title },
  })
}

// ===== 消息管理 =====

/** 分页获取历史消息 */
export function getChatMessagePage(params: { conversationId: number, pageNo: number, pageSize: number }) {
  return alovaInstance.Get<{ list: ChatMessage[], total: number }>('/ai/chat/message/my-page', {
    params,
  })
}

/** 删除消息 */
export function deleteChatMessage(id: number) {
  return alovaInstance.Delete('/ai/chat/message/delete', undefined, {
    params: { id },
  })
}

/** 清空对话消息 */
export function clearChatMessages(conversationId: number) {
  return alovaInstance.Delete('/ai/chat/message/delete-by-conversation-id', undefined, {
    params: { conversationId },
  })
}

/**
 * 对话增强模块——Slash 命令、@提及、内联操作、上下文感知
 * @author AaronZZH & Kiro
 */

export type { PageContext } from "./ContextInjector"
// #4705 上下文感知
export {
  buildContextPrompt,
  ContextInjector,
  SendToChatButton,
  useContextCollector
} from "./ContextInjector"
// #4704 内联操作
export {
  CreateEntityCard,
  CreateEntityToolUI,
  EditConfirmCard,
  EditEntityToolUI,
  QueryEntityToolUI,
  QueryResultCard
} from "./InlineActions"
export type { MentionEntity, MentionEntityType } from "./MentionPicker"
// #4703 @提及实体
export { MentionBadge, MentionPicker } from "./MentionPicker"
export type { SlashCommand } from "./SlashCommands"
// #4702 Slash 命令
export { registerCommand, SlashCommands } from "./SlashCommands"

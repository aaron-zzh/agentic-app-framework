/**
 * 邀请奖励完整页面 /settings/invite
 *
 * 直接访问/刷新此 URL 时呈现为完整页面；从其他路由 push 进入时由 (workspace)/@modal/(.)settings/invite
 * 拦截显示为弹窗，保留当前页面背景。
 *
 * @author AaronZZH & Kiro
 */

import { InviteRewardView } from "@/features/invite/InviteRewardView"

export default function InvitePage() {
  return <InviteRewardView variant="page" />
}

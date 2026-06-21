# AAF-097 邀请奖励页面

## 用户故事

作为登录用户，我希望在「设置 → 邀请奖励」中查看自己的邀请链接和奖励规则，并能查看已邀请好友的列表，以便通过推广获取积分和分销佣金；从其他页面点击「邀请奖励」时以弹窗形式打开，不打断当前工作。

## 范围

- 后端复用现有 `brokerage` 模块，补 INVITE 积分发放规则 seed、邀请绑定时积分发放、会员订阅时分销佣金触发，新增三个 user-facing API。
- 后端三个注册流程（邮箱、手机、OAuth）全部支持 referrerCode 透传，OAuth 注册补齐 contact + brokerage 初始化。
- 后端 R 端实名风控：未绑手机时仅写邀请绑定关系，奖励暂缓发放（由 AAF-098 触发补发）。
- 前端新增 `/settings/invite` 完整页面、`/settings/invite/history` 历史子页、(workspace) 全局拦截路由弹窗、SettingsLayout/AppSidebar Gift 入口、RefCodeCapture 持久化组件、authApi 三个注册接口透传 referrerCode。

## 验收标准

- 用户访问 `/settings/invite` 看到自己的邀请短码与完整邀请链接（基于 `window.location.origin/?refCode={code}`），点击复制按钮提示成功。
- 奖励规则数值（注册奖励积分数、有效天数、最多邀请人数；分销佣金比例、冻结天数）由后端实时返回，运营改 `credit_grant_rule.INVITE`/`brokerage_rule.SUBSCRIBE` 即时生效。
- 「查看邀请历史」展示被邀请人头像、昵称、注册时间、是否会员、获得的奖励积分。
- 从 sidebar 任意位置点击「邀请奖励」按钮跳转 `/settings/invite` 时，呈现为弹窗（保留当前页面背景）；直接访问或刷新该 URL 时呈现为完整页面。
- 用户从邀请链接 `?refCode=AAF-XXXXX` 落地任意页后，sessionStorage 自动持久化 refCode；之后通过邮箱/手机/OAuth 任一注册流程提交时透传到后端，绑定推荐关系并触发奖励发放（推荐人已绑手机时立即发放，未绑则暂缓）。
- 后端在 `SubscriptionService.activateSubscription` 触发 `BrokerageService.calculateBrokerage`，被邀请人购买会员后推荐人按 5% 比例获得冻结 30 天的分销佣金。

## dev-log

- ✅ #1 backlog 登记 + 任务文件创建 — 编号 AAF-097，归到 v0.1.1 增量功能（2026-06-21）
- ✅ #2 后端 v14 seed — 插入 credit_grant_rule.INVITE（200 积分/7 天/maxInvites=20）；UPDATE brokerage_rule SUBSCRIBE 为 5%/冻结 30 天（2026-06-21）
- ✅ #3 后端三个 VO record — BrokerageInviteCodeMeVO/BrokerageInvitedUserVO/BrokerageInviteRewardConfigVO（2026-06-21）
- ✅ #4 后端 BrokerageMeService — 聚合 getOrCreateMyInviteCode/listMyInvitedUsers/getRewardConfig，奖励金额按 ordinal 推断避免 N+1（2026-06-21）
- ✅ #5 后端 BrokerageMeController — 路径 /api/brokerage/me/{invite-code,invite-history,invite-rewards}（2026-06-21）
- ✅ #6 BrokerageMeServiceTest 5 用例全绿（2026-06-21）
- ✅ #7 后端注册时奖励发放 — BrokerageService.bindReferrerByCode 内部调 grantInviteRewardIfPossible，受 maxInvites 限制（积分发放无实名门槛，KYC 守门统一收口到提现侧）（2026-06-21）
- ✅ #8 后端 SubscriptionService.activateSubscription 触发 calculateBrokerage — 接通会员购买分销佣金链路（2026-06-21）
- ✅ #9 三种注册流程支持 referrerCode — LoginByPhoneDTO/OAuthCallbackDTO 加字段；autoRegisterByPhone 加 bindReferrer；createOAuthUser 补齐 contact + 邀请绑定 + 分销资格（2026-06-21）
- ✅ #10 前端 API client + hooks — lib/api/rest/brokerage/invite.ts + lib/queries/use-invite.ts（2026-06-21）
- ✅ #11 前端共享视图 — features/invite/InviteRewardView.tsx 截图复刻（2026-06-21）
- ✅ #12 前端 /settings/invite 完整页面 + history 子页（2026-06-21）
- ✅ #13 前端 (workspace) parallel route 改造 — @modal slot + (.)settings/invite 拦截弹窗 + default.tsx 兜底（2026-06-21）
- ✅ #14 前端入口 — SettingsLayout 计费分组追加「邀请奖励」(Gift)；AppSidebar 设置上方加 Gift 快捷（2026-06-21）
- ✅ #15 前端 RefCodeCapture — 根 layout 客户端组件 + lib/utils/ref-code.ts sessionStorage 工具（2026-06-21）
- ✅ #16 前端注册流程透传 — authApi 三接口加 referrerCode；register/login/oauth-callback 三个页面读取并清理（2026-06-21）

## 验证结论

- 后端 `BrokerageMeServiceTest` (5/5) + `AuthServiceTest` 全绿；6 个 Maven 模块全部 BUILD SUCCESS。
- 前端 `pnpm nx lint webui --fix` Successfully ran；本次新增/修改文件无 lint 问题。
- 前端 `pnpm nx typecheck webui` 报 4 个 pre-existing 错误，均在本次未修改的文件（`(dev)/examples/weather/page.tsx`、untracked `features/dashboard/widgets/BillingWidget.tsx`），来自其他未提交工作，不属于 AAF-097 范围。

## 后续故事

- AAF-098（绑定手机 + 提现前实名）：本故事确立"积分发放无门槛、提现侧守门"原则，独立故事实现 PUT /profile/phone 接口与 brokerage_withdraw 提现前 User.phone 校验。
- 决策记录：经讨论确认守门点放在提现，**不在**邀请发起、积分发放、佣金计算任何环节强制实名（业界共识：滴滴/美团/拼多多/抖音电商均"出钱时实名"）。

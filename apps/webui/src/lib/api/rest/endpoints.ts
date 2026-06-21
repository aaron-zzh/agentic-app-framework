import type { DictDataVO } from "@/lib/api/rest/admin/dict"
import type { CrudResource } from "@/lib/api/rest/crud/client"
import type { MenuVO } from "@/lib/api/rest/user/menu"

export const crudResources = {
  system: {
    menus: { apiPath: "/system/menus" } satisfies CrudResource<MenuVO>,
    roles: { apiPath: "/system/roles" } satisfies CrudResource,
    dictTypes: { apiPath: "/system/dict-types" } satisfies CrudResource,
    dictData: { apiPath: "/system/dict-data" } satisfies CrudResource<DictDataVO>,
    skills: { apiPath: "/system/skills" } satisfies CrudResource
  },
  admin: {
    dataAccessRules: { apiPath: "/admin/data-access-rules" } satisfies CrudResource
  },
  ai: {
    actors: { apiPath: "/ai/actors" } satisfies CrudResource,
    roles: { apiPath: "/ai/roles" } satisfies CrudResource,
    workflows: { apiPath: "/ai/workflows" } satisfies CrudResource
  },
  developer: {
    subscriptionPlans: { apiPath: "/developer/admin/subscription-plans" } satisfies CrudResource
  }
} as const

export const restEndpoints = {
  admin: {
    auditLog: "/admin/audit-log",
    dataAccessRules: "/admin/data-access-rules",
    scheduledTasks: "/admin/scheduled-tasks",
    scheduledTaskPause: (id: number) => `/admin/scheduled-tasks/${id}/pause`,
    scheduledTaskResume: (id: number) => `/admin/scheduled-tasks/${id}/resume`,
    scheduledTaskRun: (id: number) => `/admin/scheduled-tasks/${id}/run`
  },
  ai: {
    assistants: "/ai/assistants/available",
    agents: "/ai/agents",
    agent: (id: string) => `/ai/agents/${id}`,
    chatSessions: "/system/chat/sessions",
    chatConversations: "/chat/conversations",
    chatSessionMessages: (threadId: string) => `/system/chat/sessions/thread/${threadId}/messages`,
    chatMessages: "/system/chat/messages",
    chatSuggestions: (agentId?: string) =>
      `/system/chat/suggestions${agentId ? `?agentId=${agentId}` : ""}`,
    generationHistory: "/aigc/history",
    imageGeneration: "/system/images/draw",
    saveGeneratedAsset: "/aigc/assets/save-from-generation",
    model3d: "/aigc/model3d",
    model3dTextTo3d: "/aigc/model3d/text-to-3d",
    model3dImageTo3d: "/aigc/model3d/image-to-3d",
    model3dMultiImageTo3d: "/aigc/model3d/multi-image-to-3d",
    model3dTask: (taskId: string) => `/aigc/model3d/task/${taskId}`,
    taskBoard: (sessionId: string) => `/chat/sessions/${sessionId}/tasks`,
    videoTextToVideo: "/aigc/video/text-to-video",
    videoImageToVideo: "/aigc/video/image-to-video",
    videoEdit: "/aigc/video/edit",
    videoTask: (taskId: string) => `/aigc/video/task/${taskId}`
  },
  automation: {
    rules: "/automation/rules",
    rule: (id: string) => `/automation/rules/${id}`,
    ruleToggle: (id: string) => `/automation/rules/${id}/toggle`,
    ruleTest: (id: string) => `/automation/rules/${id}/test`,
    logs: "/automation/logs"
  },
  billing: {
    creditBalance: "/credits/balance",
    creditTransactions: "/credits/transactions",
    creditTokenRules: "/credit-token-rules",
    developerSubscriptionPlans: "/developer/subscription/plans",
    developerSubscriptionPlansAdmin: "/developer/admin/subscription-plans",
    developerSubscriptionCurrent: "/developer/subscription/current",
    developerSubscriptionSubscribe: "/developer/subscription/subscribe",
    developerTokenAccount: "/developer/tokens/account",
    issueOfficialLicense: "/official/console/licenses",
    officialConsoleSummary: "/official/console/summary",
    licenseCurrent: "/license/current",
    licenseSourceCode: "/license/source-code",
    rechargeOrders: "/biz/orders",
    subscriptions: "/subscriptions",
    subscriptionIds: "/subscriptions/ids"
  },
  pay: {
    orders: "/pay/orders",
    order: (id: string | number) => `/pay/orders/${id}`,
    recharge: "/pay/orders/recharge"
  },
  dashboard: {
    dashboards: "/system/dashboards",
    dashboard: (id: string) => `/system/dashboards/${id}`,
    defaultDashboard: "/system/dashboards/default",
    dashboardLayout: (id: string) => `/system/dashboards/${id}/layout`,
    widgetData: (widgetId: string) => `/system/dashboards/widgets/${widgetId}/data`,
    statsTrend: "/stats/trend",
    statsFunnel: "/stats/funnel",
    statsRetention: "/stats/retention",
    statsOverview: "/stats/overview"
  },
  dev: {
    aiOutputs: "/ai-outputs",
    aiOutputRevert: (id: number) => `/ai-outputs/${id}/revert`,
    aiOutputStats: "/ai-outputs/stats",
    gitCiDeploy: "/autodev/git/ci/deploy",
    gitCiRecent: "/autodev/git/ci/recent",
    gitCiTrigger: "/autodev/git/ci/trigger",
    gitLog: "/autodev/git/log",
    kiroAgents: "/autodev/kiro/agents"
  },
  entity: {
    activityLog: "/activity-log",
    archive: (entity: string, id: string) => `/${entity}/${id}/archive`,
    unarchive: (entity: string, id: string) => `/${entity}/${id}/unarchive`,
    comments: "/comments",
    comment: (id: string) => `/comments/${id}`,
    customFields: (slug: string) => `/entity-defs/${slug}/fields`,
    customField: (slug: string, fieldName: string) => `/entity-defs/${slug}/fields/${fieldName}`,
    entityDefs: "/entity-defs",
    entityDef: (id: string) => `/entity-defs/${id}`,
    pageDefs: "/system/page-defs",
    pageDef: (id: string) => `/system/page-defs/${id}`,
    pageDefBySlug: (slug: string) => `/system/page-defs/slug/${slug}`,
    pageDefPublish: (id: string) => `/system/page-defs/${id}/publish`,
    pageDefRollback: (id: string) => `/system/page-defs/${id}/rollback`,
    permissionsByEntity: (slug: string) => `/permissions/entity/${slug}`,
    scheduledActivities: "/scheduled-activities",
    scheduledActivityComplete: (id: string) => `/scheduled-activities/${id}/complete`,
    trash: "/trash",
    trashRestore: "/trash/restore",
    trashPurge: "/trash/purge",
    versions: (entitySlug: string, id: string) => `/${entitySlug}/${id}/versions`,
    versionRestore: (entitySlug: string, id: string, version: number) =>
      `/${entitySlug}/${id}/versions/${version}/restore`
  },
  knowledge: {
    autodevDocs: "/autodev/docs",
    autodevDocsImport: "/autodev/docs/import",
    autodevDoc: (id: number) => `/autodev/docs/${id}`,
    autodevDocRelations: (id: number) => `/autodev/docs/${id}/relations`,
    autodevDocsSearch: "/autodev/docs/search",
    autodevDocsTree: "/autodev/docs/tree",
    docs: "/docs",
    doc: (id: number) => `/docs/${id}`,
    docsSearch: "/docs/search",
    docsTree: "/docs/tree",
    knowledgeBases: "/knowledge-bases",
    knowledgeBase: (id: string) => `/knowledge-bases/${id}`,
    knowledgeBaseDocuments: (id: string) => `/knowledge-bases/${id}/documents`,
    knowledgeBaseGraph: (id: string) => `/knowledge-bases/${id}/graph`,
    knowledgeBaseSearch: (id: string) => `/knowledge-bases/${id}/search`,
    knowledgeBaseStats: (id: string) => `/knowledge-bases/${id}/stats`
  },
  media: {
    legacyAssets: "/media-assets",
    assets: "/aigc/assets",
    asset: (id: number) => `/aigc/assets/${id}`,
    assetRegenerate: "/aigc/assets/regenerate",
    assetSearch: "/aigc/assets/search",
    assetVariants: (id: number) => `/aigc/assets/${id}/variants`,
    categories: "/aigc/categories",
    tags: "/aigc/tags"
  },
  runtime: {
    chatterConfig: "/context/chatter-config"
  },
  settings: {
    apiKeys: "/developer/api-keys",
    apiKey: (id: string) => `/developer/api-keys/${id}`
  },
  user: {
    authLogin: "/auth/login",
    authLoginByCode: "/auth/login-by-code",
    authLogout: "/auth/logout",
    authMe: "/auth/me",
    authOAuthCallback: (provider: string) => `/auth/oauth/${provider}/callback`,
    authOAuthUrl: (provider: string) => `/auth/oauth/${provider}/url`,
    authRefresh: "/auth/refresh",
    authRegister: "/auth/register",
    authRegisterByCode: "/auth/register-by-code",
    authResetPassword: "/auth/reset-password",
    authSendCode: "/auth/send-code",
    authVerifyEmail: "/auth/verify-email",
    menus: "/system/menus",
    menu: (id: number) => `/system/menus/${id}`,
    allMenusTree: "/system/menus/tree",
    myMenusTree: "/system/menus/my-tree",
    notifications: "/notifications",
    notificationPreferences: "/notification-preferences",
    notificationsRead: "/notifications/read",
    notificationsUnreadCount: "/notifications/unread-count",
    organizations: "/system/orgs",
    organization: (id: string) => `/system/orgs/${id}`,
    organizationMembers: (orgId: string) => `/system/orgs/${orgId}/members`,
    organizationMember: (orgId: string, userId: string) =>
      `/system/orgs/${orgId}/members/${userId}`,
    profile: "/system/user/profile",
    profilePassword: "/system/user/profile/password"
  },
  system: {
    dictData: "/system/dict-data",
    dictTypes: "/system/dict-types",
    roles: "/system/roles",
    skills: "/system/skills"
  },
  workflow: {
    delegations: "/delegations",
    delegation: (id: string) => `/delegations/${id}`,
    workflowStart: "/system/workflow/start",
    workflowComplete: "/system/workflow/complete",
    workflowReject: "/system/workflow/reject",
    workflowStatus: (processInstanceId: string) => `/system/workflow/${processInstanceId}`,
    workflowHistory: (processInstanceId: string) => `/system/workflow/${processInstanceId}/history`,
    approvalAddSignBefore: "/system/workflow/approval/add-sign-before",
    approvalAddSignAfter: "/system/workflow/approval/add-sign-after",
    approvalTransfer: "/system/workflow/approval/transfer",
    approvalWithdraw: "/system/workflow/approval/withdraw",
    approvalTimeline: (processInstanceId: string) =>
      `/system/workflow/approval/timeline/${processInstanceId}`,
    approvalVoteProgress: (processInstanceId: string) =>
      `/system/workflow/approval/vote-progress/${processInstanceId}`,
    approvalStats: "/system/workflow/approval/stats",
    formTemplates: "/system/workflow/form-templates",
    formTemplate: (id: string) => `/system/workflow/form-templates/${id}`,
    myInitiatedInstances: "/system/workflow/instances/my-initiated",
    historyInstances: "/system/workflow/instances/history",
    myPendingTasks: "/system/workflow/tasks/my-pending",
    todos: "/todos",
    todoComplete: (id: string) => `/todos/${id}/complete`,
    todoDismiss: (id: string) => `/todos/${id}/dismiss`
  }
} as const

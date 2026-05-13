---
level: Practice
layer: Product
purpose: AAF 插件商业化设计——付费插件的定义、控制机制与技术实现
status: draft
version: 1.0.0
date: 2026-05-13
author: AaronZZH
---

# 插件商业化设计

> 定义 AAF 的商业化计费单元（插件）及其技术控制机制。
> 关联：[前端目录结构](./directory-structure.md) | [Copilot 插件](./copilot-plugin.md) | [聊天模块](./chat-livechat-module.md)

## 一、核心概念

```text
对外（用户/开发者）：插件（Plugin）——可装可卸、按需启用的功能单元
对内（代码/架构）：Module——技术上的启停控制单元
```

### 两个正交维度

```text
技术分层（代码怎么组织）          商业分层（功能怎么卖）
├── feature（引擎级复合模块）     ├── Plugin（计费单元）
├── section（业务域区块）    ×    │   ├── 包含哪些 feature
├── component（原子 UI）         │   ├── 包含哪些 section
├── lib（纯逻辑）                │   └── 许可证/套餐控制
```

Plugin 是正交于技术分层的"许可证单元"：
- 一个 Plugin 可包含 1+ feature + 1+ section + N 个后端接口
- 同一个 feature 的不同能力可属于不同 Plugin（如 RichTextEditor 基础免费，协同编辑付费）

## 二、插件层级

```text
AAF 插件市场
├── 🆓 内置插件（免费，开箱即用）
├── ⭐ 高级插件（Pro，付费订阅）
└── 🏢 企业插件（Enterprise，企业订阅）
```

### 插件划分

| Plugin ID | 名称 | 层级 | 包含内容 |
|-----------|------|------|---------|
| `core` | 核心平台 | 内置 | 基础 CRUD、表单、列表、看板、RichTextEditor(richField) |
| `document` | 文档管理 | 内置 | RichTextEditor(document)、文档列表/编辑 |
| `workflow-basic` | 基础审批 | 内置 | FlowEditor(approval)、审批流程 |
| `ai-assistant` | AI 助理 | Pro | Copilot 对话面板、知识问答、基础工具调用 |
| `livechat` | 在线客服 | Pro | Chat section、机器人脚本、FlowEditor(chatbot) |
| `workflow-ai` | AI 工作流 | Pro | FlowEditor(workflow)、AI 节点集 |
| `bi-analytics` | BI 分析 | Pro | 自然语言查询、图表生成、数据洞察 |
| `collab` | 协同编辑 | Enterprise | Yjs 实时协同、多人光标、版本历史 |
| `copilot-advanced` | 高级 Copilot | Enterprise | 流程优化、持续学习、主动洞察 |
| `multi-channel` | 多渠道集成 | Enterprise | 企微/公众号/钉钉/飞书接入 |
| `auto-dev` | AI 自动开发 | Enterprise | 代码生成、模块脚手架、自进化 |

## 三、数据模型

```typescript
// 插件定义（系统内置，不可修改）
interface PluginDef {
  id: string                          // 'ai-assistant'
  name: string                        // 'AI 助理'
  description: string
  tier: 'free' | 'pro' | 'enterprise'
  icon: string
  category: 'ai' | 'collaboration' | 'integration' | 'workflow' | 'analytics'
  features: string[]                  // 前端 feature 目录名
  sections: string[]                  // 前端 section 目录名
  routes: string[]                    // 控制的路由路径
  backendModules: string[]            // 后端 Maven 模块/服务
  dependencies?: string[]             // 依赖的其他插件
}

// 租户许可证
interface TenantLicense {
  tenantId: string
  plan: 'free' | 'pro' | 'enterprise'
  enabledPlugins: string[]            // 已启用的插件 ID 列表
  expiresAt?: string                  // 订阅到期时间
  seats?: number                      // 席位数
}

// 前端运行时状态
interface PluginState {
  plugins: Record<string, { enabled: boolean; tier: string }>
  plan: string
}
```

## 四、控制机制（三层防护）

```text
┌─────────────────────────────────────────────────────┐
│  第一层：前端路由/UI 门控                             │
│  未授权插件 → 路由不注册 / 菜单置灰 / 升级提示        │
├─────────────────────────────────────────────────────┤
│  第二层：前端 Feature 门控                            │
│  同一 feature 的付费能力 → 条件加载                   │
├─────────────────────────────────────────────────────┤
│  第三层：后端 API 拦截                                │
│  即使前端绕过 → 后端拒绝请求（兜底安全）              │
└─────────────────────────────────────────────────────┘
```

### 4.1 前端路由/UI 门控

```typescript
// lib/modules/plugin-registry.ts
// 启动时从后端拉取当前租户的插件状态
const { data: pluginState } = useQuery({
  queryKey: ['plugins'],
  queryFn: () => api.get<PluginState>('/api/system/plugins'),
  staleTime: Infinity,  // 登录后不变，刷新页面重新拉取
})

// 判断插件是否启用
function usePluginEnabled(pluginId: string): boolean {
  const { plugins } = usePluginState()
  return plugins[pluginId]?.enabled ?? false
}
```

```typescript
// 路由级门控：未授权插件的页面不加载代码
// app/(workspace)/workflow-ai/page.tsx
import { PluginGate } from '@/lib/modules/plugin-gate'

export default function WorkflowAIPage() {
  return (
    <PluginGate plugin="workflow-ai">
      <WorkflowAIEditor />
    </PluginGate>
  )
}
```

```typescript
// UI 级门控：菜单/按钮显示升级提示
function PluginGate({ plugin, children, fallback }: {
  plugin: string
  children: React.ReactNode
  fallback?: React.ReactNode
}) {
  const enabled = usePluginEnabled(plugin)
  if (!enabled) return fallback ?? <UpgradeHint plugin={plugin} />
  return <>{children}</>
}

// 升级提示组件
function UpgradeHint({ plugin }: { plugin: string }) {
  const def = getPluginDef(plugin)
  return (
    <div className="flex flex-col items-center gap-4 py-12">
      <Lock className="size-12 text-muted-foreground" />
      <h3>{def.name}</h3>
      <p className="text-muted-foreground">{def.description}</p>
      <Button>升级到 {def.tier} 解锁</Button>
    </div>
  )
}
```

### 4.2 Feature 级门控

同一个 feature 内部，部分能力按插件控制：

```typescript
// features/rich-text-editor/presets/document.ts
export function getDocumentFeatures(): FeatureList {
  const base = [
    HeadingFeature(),
    BoldFeature(),
    ListFeature(),
    LinkFeature(),
    // ...基础能力（core 插件，免费）
  ]

  // 协同编辑仅 collab 插件启用时加载
  if (pluginEnabled('collab')) {
    base.push(CollabFeature())
  }

  return base
}
```

```typescript
// features/flow-editor/lib/registry.ts
export function getWorkflowNodeRegistry(): NodeTypeRegistry {
  const base = { start: StartNode, end: EndNode, condition: ConditionNode }

  // AI 节点集仅 workflow-ai 插件启用时注册
  if (pluginEnabled('workflow-ai')) {
    Object.assign(base, {
      llm: LLMNode,
      agent: AgentNode,
      knowledge: KnowledgeNode,
      code: CodeNode,
    })
  }

  return base
}
```

### 4.3 后端 API 拦截

```java
// 自定义注解
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePlugin {
    String value();  // 插件 ID
}

// 拦截器
@Component
public class PluginGuardInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, ...) {
        RequirePlugin annotation = getAnnotation(handler);
        if (annotation != null && !licenseService.isPluginEnabled(tenantId, annotation.value())) {
            throw new PluginNotEnabledException(annotation.value());
        }
        return true;
    }
}

// 使用
@RequirePlugin("workflow-ai")
@PostMapping("/api/workflow/ai-nodes/execute")
public Mono<Result> executeAINode(...) { ... }

@RequirePlugin("livechat")
@GetMapping("/api/livechat/sessions")
public Mono<Result> listSessions(...) { ... }
```

## 五、侧边栏菜单控制

```typescript
// sections/layout/AppSidebar.tsx
// 菜单项根据插件状态决定显示方式
interface NavItem {
  label: string
  icon: string
  href: string
  plugin?: string              // 关联的插件 ID
}

function SidebarNavItem({ item }: { item: NavItem }) {
  const enabled = item.plugin ? usePluginEnabled(item.plugin) : true

  if (!enabled) {
    // 显示但置灰 + 锁图标 + 点击弹升级提示
    return (
      <NavButton disabled onClick={() => showUpgradeDialog(item.plugin)}>
        <item.icon className="opacity-50" />
        <span className="opacity-50">{item.label}</span>
        <Lock className="size-3" />
      </NavButton>
    )
  }

  return (
    <NavLink href={item.href}>
      <item.icon />
      <span>{item.label}</span>
    </NavLink>
  )
}
```

## 六、插件市场页面

```text
/workspace/settings/plugins → 插件管理页面

┌─────────────────────────────────────────────────────┐
│ 插件市场                          当前套餐：Pro ⭐    │
├─────────────────────────────────────────────────────┤
│ [全部] [已安装] [AI] [协作] [集成] [工作流] [分析]   │
├─────────────────────────────────────────────────────┤
│ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐   │
│ │ 🤖 AI 助理   │ │ 💬 在线客服  │ │ 🔄 AI 工作流 │   │
│ │ Pro ⭐       │ │ Pro ⭐       │ │ Pro ⭐       │   │
│ │ [已启用 ✓]   │ │ [启用]       │ │ [已启用 ✓]   │   │
│ └─────────────┘ └─────────────┘ └─────────────┘   │
│ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐   │
│ │ 👥 协同编辑  │ │ 📊 BI 分析   │ │ 🌐 多渠道    │   │
│ │ Enterprise 🏢│ │ Pro ⭐       │ │ Enterprise 🏢│   │
│ │ [升级解锁]   │ │ [启用]       │ │ [升级解锁]   │   │
│ └─────────────┘ └─────────────┘ └─────────────┘   │
└─────────────────────────────────────────────────────┘
```

## 七、与现有设计的关系

| 模块 | 商业化控制点 |
|------|------------|
| FlowEditor | approval mode 免费；workflow/chatbot mode 按插件控制 |
| RichTextEditor | 基础 preset 免费；CollabFeature 按 collab 插件控制 |
| Copilot | 基础对话 Pro；高级能力（BI/优化/多渠道）Enterprise |
| Chat/Livechat | 整体按 livechat 插件控制 |
| 实体引擎 | 核心免费；高级字段类型/视图可按插件扩展 |

## 八、实现路径

| 阶段 | 能力 |
|------|------|
| v0.1 | PluginDef 定义 + PluginGate 组件 + 后端 @RequirePlugin 注解（硬编码许可） |
| v0.2 | 插件管理页面 + 租户许可证 API + 菜单门控 |
| v0.3 | 插件市场 UI + 套餐/订阅管理 + 支付集成 |
| v1.0 | 第三方插件开发 SDK + 插件审核/发布流程 |

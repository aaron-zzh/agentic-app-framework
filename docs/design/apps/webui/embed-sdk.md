---
level: Practice
layer: Product
purpose: AAF 嵌入式 SDK 设计——将 AI 助理作为智能接口嵌入第三方产品
status: draft
version: 1.0.0
date: 2026-05-13
author: AaronZZH
---

# 嵌入式 SDK（Embed SDK）

> 将 AAF 的 AI 助理能力作为智能接口嵌入第三方产品网页，实现智能客服、业务助手等场景。
> 所属体系：[在线客服与聊天模块](./chat-livechat-module.md) | [Copilot 插件](./copilot-plugin.md)
> 关联：外部生态整合（路线图）

## 一、定位

AAF Embed SDK 让第三方产品通过一行代码接入 AAF 的 AI 助理能力，无需了解 AAF 内部架构。

```text
第三方产品网页
  └── <script src="https://aaf.example.com/embed.js" />
        └── 浮窗/内联/弹窗 → iframe（AAF 助理应用）
              └── AI Agent + 知识库 + 工作流
```

**核心价值**：第三方产品获得 AI 能力，AAF 获得分发渠道。助理是 AAF 对外的统一智能接口。

## 二、设计原则

| 原则 | 含义 |
|------|------|
| 零侵入 | 不污染宿主页面的 CSS/JS/DOM，不引入全局变量冲突 |
| 框架无关 | 纯 JS 核心，任何技术栈的网页都能用 |
| 安全隔离 | iframe 沙箱隔离，宿主与助理互不访问对方 DOM |
| 渐进增强 | 一行 script 即可用，高级配置按需开启 |
| 秒开体验 | 预渲染 + 骨架屏，点击即用无等待 |

## 三、嵌入模式

| 模式 | 适用场景 | 触发方式 |
|------|---------|---------|
| **浮窗按钮** | 智能客服、业务助手 | 页面右下角常驻按钮，点击展开对话面板 |
| **内联嵌入** | 知识搜索、表单助手 | 嵌入页面指定区域，占据固定空间 |
| **弹窗** | 工作流触发、审批确认 | 用户操作触发，模态弹窗覆盖 |

## 四、接入方式

### 4.1 一行代码接入（最简）

```html
<script
  src="https://aaf.example.com/embed.js"
  data-agent="customer-service"
  data-theme="light"
></script>
```

自动在页面右下角渲染浮窗按钮，点击展开 AI 客服对话。

### 4.2 JS API（精细控制）

```html
<script src="https://aaf.example.com/embed.js"></script>
<script>
  AAF('init', {
    agent: 'customer-service',
    theme: 'auto',
    locale: 'zh-CN',
    user: { id: 'u-123', name: '张三' },  // 可选，传递用户身份
    context: { page: '/products/123' },    // 可选，传递业务上下文
  });

  // 浮窗模式
  AAF('floatingButton', { position: 'bottom-right', text: '智能助手' });

  // 内联模式
  AAF('inline', { target: '#assistant-container' });

  // 弹窗模式（手动触发）
  document.getElementById('help-btn').onclick = () => AAF('modal');

  // 监听事件
  AAF('on', 'ready', () => console.log('助理已就绪'));
  AAF('on', 'message', (msg) => console.log('助理回复:', msg));
  AAF('on', 'action', (action) => console.log('助理执行操作:', action));
</script>
```

### 4.3 React 组件（React 项目）

```tsx
import { AAFAssistant } from '@aaf/embed-react';

<AAFAssistant
  agent="customer-service"
  mode="floating"
  theme="dark"
  user={{ id: 'u-123', name: '张三' }}
  onReady={() => console.log('就绪')}
  onMessage={(msg) => console.log(msg)}
/>
```

## 五、架构

### 5.1 三层分包

```text
packages/embed/
├── embed-core/       → 核心引擎（Vanilla JS，框架无关）
│   ├── loader.ts     → AAF 全局对象 + 命令队列
│   ├── iframe.ts     → iframe 创建与生命周期管理
│   ├── protocol.ts   → 宿主 ↔ iframe 通信协议
│   ├── modes/
│   │   ├── floating.ts   → 浮窗按钮（Custom Element）
│   │   ├── inline.ts     → 内联嵌入（Custom Element）
│   │   └── modal.ts      → 弹窗（Custom Element）
│   └── prerender.ts  → 预渲染管理
├── embed-react/      → React 封装（<AAFAssistant /> 组件）
└── embed-snippet/    → CDN 加载脚本（压缩后 <5KB）
```

### 5.2 隔离机制

```text
宿主页面                              iframe（AAF 助理）
┌──────────────────────┐             ┌──────────────────────┐
│ Custom Element       │ postMessage │ 助理应用（React）      │
│ (Shadow DOM 样式隔离) │ ◄─────────► │ assistant-ui Thread   │
│                      │             │ AG-UI Runtime         │
│ 命令队列 + 事件监听   │             │ Agent 服务连接        │
└──────────────────────┘             └──────────────────────┘
```

- **Shadow DOM**：Custom Element 的外壳样式（按钮/边框/动画）与宿主隔离
- **iframe sandbox**：助理应用完全隔离，无法访问宿主 DOM/Cookie/Storage
- **origin 校验**：postMessage 通信严格校验来源域名

### 5.3 通信协议

```typescript
// 消息格式
interface EmbedMessage {
  source: 'aaf-embed'           // 标识符，过滤无关消息
  namespace: string             // 多实例隔离
  type: 'command' | 'event'
  payload: unknown
}

// 宿主 → iframe（命令）
type Command =
  | { action: 'init'; config: EmbedConfig }
  | { action: 'setUser'; user: UserInfo }
  | { action: 'setContext'; context: Record<string, unknown> }
  | { action: 'open' }
  | { action: 'close' }
  | { action: 'sendMessage'; text: string }

// iframe → 宿主（事件）
type Event =
  | { action: 'ready' }
  | { action: 'resize'; height: number }
  | { action: 'message'; message: AgentMessage }
  | { action: 'action'; name: string; data: unknown }
  | { action: 'close' }
```

### 5.4 生命周期

```text
1. 加载 embed-snippet（<5KB）→ 注册 AAF 全局对象 + 命令队列
2. 用户调用 AAF('init', config) → 命令入队
3. 异步加载 embed-core（按需）→ 创建 Custom Element
4. Custom Element 创建隐藏 iframe → 加载助理应用
5. iframe 就绪 → 发送 'ready' 事件
6. 执行命令队列中的积压命令
7. 用户点击浮窗按钮 → 展开面板（iframe 已预渲染，秒开）
8. 对话交互 → postMessage 双向通信
9. 页面卸载 → 清理 iframe + 断开连接
```

## 六、预渲染与性能

| 策略 | 说明 |
|------|------|
| 预渲染 | 页面加载后静默创建隐藏 iframe，用户点击时直接展示 |
| 骨架屏 | iframe 加载期间 Custom Element 内显示骨架屏 |
| 懒加载 | embed-core 按需加载，snippet 本身 <5KB 不阻塞页面 |
| 复用 | 关闭面板不销毁 iframe，再次打开直接复用 |
| 超时重建 | iframe 空闲超过阈值（如 30 分钟）后销毁，下次重建 |

## 七、业务上下文传递

嵌入方可将业务上下文传递给 AI 助理，使助理具备场景感知能力：

```javascript
AAF('setContext', {
  page: '/products/123',
  product: { id: '123', name: 'Pro 套餐', price: 299 },
  user: { plan: 'free', registeredDays: 30 },
});
```

助理收到上下文后可以：
- 主动推荐相关帮助（"我看到您在查看 Pro 套餐，需要了解什么？"）
- 回答时结合业务数据（"您当前是免费版，升级 Pro 可以..."）
- 执行业务操作（"帮您创建升级订单"→ 通过 action 事件通知宿主）

## 八、安全

| 措施 | 说明 |
|------|------|
| iframe sandbox | `allow-scripts allow-same-origin allow-popups`，禁止访问宿主 |
| origin 白名单 | 后端配置允许嵌入的域名列表，非白名单域拒绝加载 |
| postMessage 校验 | 收发消息严格校验 `event.origin` + `source: 'aaf-embed'` |
| Token 认证 | 嵌入方通过 API Key 认证，后端校验请求合法性 |
| CSP 头 | iframe 页面设置 `Content-Security-Policy` 限制资源加载 |

## 九、与现有设计的关系

| 模块 | 关系 |
|------|------|
| [在线客服（LivechatWidget）](./chat-livechat-module.md#七访客端嵌入livechatwidget) | LivechatWidget 是 Embed SDK 浮窗模式的第一个应用场景 |
| [Copilot 插件](./copilot-plugin.md) | 嵌入的助理本质是 Copilot 的外部化——同一个 Agent，不同的宿主环境 |
| [AG-UI 协议](./tech-stack.md) | iframe 内的助理应用使用 AG-UI runtime 与后端 Agent 通信 |

## 十、实现路径

| 阶段 | 能力 |
|------|------|
| v0.2 | embed-snippet + 浮窗模式 + 基础对话（复用 LivechatWidget） |
| v0.3 | 内联/弹窗模式 + 业务上下文传递 + React 封装包 |
| v1.0 | 预渲染 + 多实例 + 自定义主题 + 操作回调（action 事件） |

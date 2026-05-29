# 可访问性（A11y）建议

执行者：AI/developer-webui
日期：2026-05-29

## 当前状态

### 已具备的基础

- ✅ shadcn/ui 组件基于 Base UI 原语，内置 WAI-ARIA 合规
- ✅ 语义化 HTML 标签（button/dialog/nav 等）
- ✅ 键盘导航支持（⌘K 命令面板、Tab 焦点管理）
- ✅ 暗色/亮色主题切换（`prefers-color-scheme` 适配）
- ✅ biome lint 规则包含 a11y 检查

### 核心组件 ARIA 标注检查

| 组件 | ARIA 状态 | 问题 |
|------|----------|------|
| `Button` | ✅ 完整 | shadcn 内置 |
| `Dialog` | ✅ 完整 | aria-modal, role="dialog" |
| `Select` | ✅ 完整 | aria-expanded, aria-selected |
| `Tabs` | ✅ 完整 | role="tablist/tab/tabpanel" |
| `CommandPalette` | ✅ 完整 | cmdk 内置 ARIA |
| `DataTable` | ⚠️ 部分 | 缺少 aria-sort 标注 |
| `FlowEditor` | ⚠️ 部分 | XYFlow 节点缺少 aria-label |
| `RichTextEditor` | ⚠️ 部分 | Lexical 内置基础 ARIA，自定义节点需补充 |
| `Upload` | ⚠️ 部分 | 拖拽区域缺少 aria-dropeffect |
| `Signature` | ❌ 缺失 | Canvas 签名板无 ARIA 替代文本 |
| `QRScanner` | ❌ 缺失 | 摄像头区域无 ARIA 状态描述 |

## 建议清单

### 高优先级

| 项目 | 影响 | 修复方式 |
|------|------|---------|
| 图片 alt 属性 | 屏幕阅读器无法描述图片 | 所有 `<Image>` 必须有有意义的 alt |
| 表单 label 关联 | 输入框无法被辅助技术识别 | 确认所有 Field 组件 htmlFor 正确关联 |
| 焦点管理 | Dialog 打开后焦点未锁定 | shadcn Dialog 已处理，自定义弹窗需确认 |
| 颜色对比度 | 低视力用户无法阅读 | OKLCH 色彩系统确保 4.5:1 对比度 |
| 跳过导航链接 | 键盘用户无法快速到达主内容 | 添加 "Skip to content" 隐藏链接 |

### 中优先级

| 项目 | 影响 | 修复方式 |
|------|------|---------|
| 实时区域通知 | 动态内容更新不被感知 | Toast 使用 `aria-live="polite"` |
| 加载状态 | 异步操作无反馈 | 添加 `aria-busy` 和 `aria-live` |
| 表格排序状态 | 排序方向不可感知 | DataTable 列头添加 `aria-sort` |
| 拖拽替代操作 | 无法使用键盘拖拽 | @dnd-kit 已支持，确认启用 |
| 错误提示关联 | 校验错误与字段未关联 | 使用 `aria-describedby` 指向错误消息 |

### 低优先级（增强体验）

| 项目 | 建议 |
|------|------|
| 减少动画 | 尊重 `prefers-reduced-motion` 媒体查询 |
| 高对比度模式 | 提供 `forced-colors` 适配 |
| 语义化地标 | 确认 `<main>` `<nav>` `<aside>` 正确使用 |
| 文档语言 | `<html lang="zh-CN">` 已设置 |

## 测试建议

| 工具 | 用途 |
|------|------|
| axe-core | 自动化 ARIA 检查（集成到 CI） |
| Lighthouse | 可访问性评分 |
| NVDA / VoiceOver | 手动屏幕阅读器测试 |
| 键盘导航测试 | Tab 顺序、焦点可见性 |

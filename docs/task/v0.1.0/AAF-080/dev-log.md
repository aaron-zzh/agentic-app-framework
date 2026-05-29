# 开发记录：AAF-080 画板视图

执行者：AI/developer-webui

## 实现文件

| 文件 | 说明 |
|------|------|
| `src/lib/types/entity/views.ts` | 新增 CanvasViewConfig、CanvasTemplate 类型 |
| `src/lib/types/entity/entity.ts` | EntityDef 新增 canvasView 字段 |
| `src/lib/types/entity/index.ts` | 导出新类型 |
| `src/features/entity-engine/components/canvas/CanvasView.tsx` | 画板视图主组件（#8001） |
| `src/features/entity-engine/components/canvas/use-canvas-collaboration.ts` | Yjs 协作 Hook（#8002） |
| `src/features/entity-engine/components/canvas/CanvasCollaborators.tsx` | 协作者头像列表（#8002） |
| `src/features/entity-engine/components/canvas/CanvasSmartElements.tsx` | 智能元素 Shape 定义（#8003） |
| `src/features/entity-engine/components/canvas/CanvasAIPanel.tsx` | AI 辅助面板（#8004） |
| `src/features/entity-engine/components/canvas/CanvasTemplateDialog.tsx` | 模板选择对话框（#8005） |
| `src/features/entity-engine/components/canvas/CanvasExportButton.tsx` | 导出按钮（#8005） |
| `src/features/entity-engine/components/canvas/index.ts` | barrel export |
| `src/features/entity-engine/components/ViewEngine.tsx` | 注册 canvas case |
| `src/features/entity-engine/components/index.ts` | 导出 canvas 模块 |

## 实现决策

- tldraw 自定义 Shape 用于便签/实体卡片/思维导图节点（#8003），未使用 tldraw 内置 note shape 因为需要颜色分类和连线关联
- 协作同步（#8002）预留 Yjs WebSocket Provider 接入点，当前为占位实现，实际接入需后端协同服务就绪
- AI 辅助（#8004）各操作预留后端 API 调用点，返回 tldraw shapes 数据后直接插入画布
- 导出（#8005）使用 tldraw 内置 exportToBlob，PDF 格式暂降级为 PNG（tldraw 不原生支持 PDF）

## 注意事项

- 需安装 `@tldraw/tldraw` 依赖：`pnpm add @tldraw/tldraw`
- tldraw CSS 必须在组件中导入（`@tldraw/tldraw/tldraw.css`）
- 协作功能需后端 WebSocket 协同服务（Yjs provider），当前为 stub
- AI 辅助功能需后端 AI 接口（`/api/{entity}/canvas/ai/*`），当前为 TODO
- CanvasSmartElements 中的自定义 Shape 需在 Tldraw 组件中通过 `shapeUtils` prop 注册才能生效

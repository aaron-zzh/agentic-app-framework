# @aaf/knowledge-visualization

AAF 的 Three.js 知识可视化引擎。提供 2D、2.5D、3D 图渲染、基础布局、节点拾取和 React 生命周期适配。

## 边界

- 只接收通用节点、关系与稳定标识，不访问业务 API。
- 不依赖 Neo4j Driver、TanStack Query、shadcn 或 WebUI 业务类型。
- 节点和关系分别批量渲染，禁止按节点创建独立 Three.js Mesh。
- 事实、证据和文档详情由调用方根据选择事件按需加载。

## 使用

```tsx
import { GraphVisualization } from "@aaf/knowledge-visualization"

<GraphVisualization
  graph={graph}
  dimension="2.5d"
  layout="force"
  onNodePick={(node) => setSelectedNodeId(node?.id ?? null)}
/>
```

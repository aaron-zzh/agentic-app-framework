/**
 * 3D 模块公开 API + ViewEngine 注册配置
 * @author AaronZZH & Kiro
 */

export { ModelViewer } from "./ModelViewer"
export { ThreeScene } from "./ThreeScene"
export { ThreeView } from "./ThreeView"

import { ThreeView } from "./ThreeView"

/** ViewEngine 视图类型注册配置 */
export const threeViewConfig = {
  type: "3d" as const,
  label: "3D 视图",
  component: ThreeView
}

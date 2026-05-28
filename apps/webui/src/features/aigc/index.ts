/**
 * AIGC 模块公开 API
 * @author AaronZZH & Kiro
 */

export { AigcLayout } from "./AigcLayout"
// 生成历史
export { GenerationHistory } from "./GenerationHistory"
// 对话内联预览
export { MediaPreviewCard } from "./MediaPreviewCard"
export { StyleAdjustDialog } from "./StyleAdjustDialog"
export { useAigcStore } from "./store"
export { threeViewConfig } from "./three"
// 3D（动态导入使用，此处仅导出类型和容器）
export { ThreeView } from "./three/ThreeView"
export type { GenerationParams, MediaAsset, StoryElement } from "./types"
export { VideoEditPanel } from "./VideoEditPanel"
export { VideoGenerationChat } from "./VideoGenerationChat"
export { VideoPlayer } from "./VideoPlayer"
// 视频生成
export { VideoStoryboard } from "./VideoStoryboard"
export type { SceneStatus, VideoScene } from "./VideoTimeline"
export { VideoTimeline } from "./VideoTimeline"

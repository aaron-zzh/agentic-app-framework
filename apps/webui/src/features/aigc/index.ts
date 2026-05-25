/**
 * AIGC 模块公开 API
 * @author AaronZZH & Kiro
 */

export { AigcLayout } from "./AigcLayout"
export { useAigcStore } from "./store"
export type { StoryElement, MediaAsset, GenerationParams } from "./types"

// 视频生成
export { VideoStoryboard } from "./VideoStoryboard"
export { VideoTimeline } from "./VideoTimeline"
export type { VideoScene, SceneStatus } from "./VideoTimeline"
export { VideoPlayer } from "./VideoPlayer"
export { VideoGenerationChat } from "./VideoGenerationChat"

// 对话内联预览
export { MediaPreviewCard } from "./MediaPreviewCard"

// 3D（动态导入使用，此处仅导出类型和容器）
export { ThreeView } from "./three/ThreeView"

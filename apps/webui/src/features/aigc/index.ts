/**
 * AIGC 模块公开 API
 * @author AaronZZH & Kiro
 */

// 素材库
export { AssetLibrary } from "./asset/AssetLibrary"
// 生成历史
export { GenerationHistory } from "./generation/GenerationHistory"
// 对话内联预览
export { MediaPreviewCard } from "./preview/MediaPreviewCard"
export { StyleAdjustDialog } from "./preview/StyleAdjustDialog"
// 项目管理
export { AigcView } from "./project/AigcView"
export { useAigcStore } from "./store"
export { threeViewConfig } from "./three"
// 3D（动态导入使用，此处仅导出类型和容器）
export { ThreeView } from "./three/ThreeView"
export type {
  GenerationParams,
  MediaAssetType,
  MediaAssetVO,
  MediaCategoryVO,
  MediaTagVO,
  Model3dTaskResult,
  Model3dTaskStatus,
  StoryElement
} from "./types"
// 视频生成
export { VideoEditPanel } from "./video/VideoEditPanel"
export { VideoGenerationChat } from "./video/VideoGenerationChat"
export { VideoPlayer } from "./video/VideoPlayer"
export { VideoStoryboard } from "./video/VideoStoryboard"
export type { SceneStatus, VideoScene } from "./video/VideoTimeline"
export { VideoTimeline } from "./video/VideoTimeline"

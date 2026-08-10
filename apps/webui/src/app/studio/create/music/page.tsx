/** 音乐生成路由。 */

import { MediaGenerationWorkspace } from "@/features/studio/media-generation/MediaGenerationWorkspace"

export default function MusicToolPage() {
  return <MediaGenerationWorkspace initialMode="music" />
}
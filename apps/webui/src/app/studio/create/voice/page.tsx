/** 配音生成路由。 */

import { MediaGenerationWorkspace } from "@/features/studio/media-generation/MediaGenerationWorkspace"

export default function VoiceToolPage() {
  return <MediaGenerationWorkspace initialMode="voice" />
}
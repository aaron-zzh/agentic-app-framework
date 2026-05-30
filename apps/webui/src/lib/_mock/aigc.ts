/**
 * AIGC 模块 Mock 数据（仅开发用）
 * @author AaronZZH & Kiro
 */

import type { StoryElement } from "@/features/aigc/types"
import type { VideoScene } from "@/features/aigc/VideoTimeline"

/** 媒体素材状态（VideoGenerationChat 用） */
export interface MediaAssetStatus {
  id: string
  name: string
  thumbnail: string
  duration: string
  status: "pending" | "generating" | "completed"
}

export const MOCK_ASSETS: MediaAssetStatus[] = [
  {
    id: "ma1",
    name: "开场镜头_v1.mp4",
    thumbnail: "/placeholder.svg",
    duration: "4s",
    status: "completed"
  },
  {
    id: "ma2",
    name: "主角登场_v1.mp4",
    thumbnail: "/placeholder.svg",
    duration: "4s",
    status: "generating"
  }
]

export const MOCK_SCENES: VideoScene[] = [
  {
    id: "s1",
    index: 1,
    startTime: 0,
    endTime: 4,
    description: "写实胶片 POV 城市街道清晨",
    status: "completed"
  },
  {
    id: "s2",
    index: 2,
    startTime: 4,
    endTime: 8,
    description: "主角从远处走来逆光剪影",
    status: "generating"
  },
  {
    id: "s3",
    index: 3,
    startTime: 8,
    endTime: 12,
    description: "粒子消散过渡转场",
    status: "pending"
  }
]

export const MOCK_TIMELINE_SCENES: VideoScene[] = [
  {
    id: "s1",
    index: 1,
    startTime: 0,
    endTime: 4,
    description: "写实胶片 POV 城市街道",
    status: "completed"
  },
  {
    id: "s2",
    index: 2,
    startTime: 4,
    endTime: 8,
    description: "主角登场逆光剪影",
    status: "generating"
  },
  { id: "s3", index: 3, startTime: 8, endTime: 12, description: "粒子消散转场", status: "pending" },
  { id: "s4", index: 4, startTime: 12, endTime: 16, description: "抽象空间漫游", status: "pending" }
]

export const MOCK_ELEMENTS: StoryElement[] = [
  {
    id: "1",
    name: "主角形象",
    description: "一位身穿深蓝色长袍的年轻法师，手持发光法杖",
    thumbnail: "/placeholder.svg",
    tags: ["角色", "主角"]
  },
  {
    id: "2",
    name: "魔法森林",
    description: "古老的森林中弥漫着蓝紫色的魔法光芒，巨大的蘑菇散发荧光",
    thumbnail: "/placeholder.svg",
    tags: ["场景", "森林"]
  },
  {
    id: "3",
    name: "水晶龙",
    description: "通体由透明水晶构成的巨龙，折射出彩虹般的光芒",
    thumbnail: "/placeholder.svg",
    tags: ["角色", "龙"]
  }
]

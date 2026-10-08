/** 数字人 ID（后端为数字，路由参数为字符串） */
export type DigitalHumanId = number | string

export type DigitalHumanStatus = "active" | "archived"

/** 数字人（与后端 DigitalHumanVO 对齐，空值字段不返回） */
export interface DigitalHuman {
  id: number
  /** 乐观锁版本号，更新时作为 expectedVersion 回传 */
  version: number

  name: string
  description?: string
  tags?: string[]

  avatarImageUrl: string
  appearance?: AppearanceConfig
  voiceConfig: VoiceConfig
  modelConfig?: ModelConfig

  assistantId?: number
  syncModelConfig: boolean

  videoCount: number
  totalDuration: number
  creditsConsumed: number
  lastUsedAt?: string

  status: DigitalHumanStatus
  generationSource?: string
  aigcProjectId?: number

  /** 当前用户是否为创建者；仅创建者可修改、删除或执行命令 */
  ownedByCurrentUser: boolean

  createTime: string
  updateTime: string
}

export interface AppearanceConfig {
  age?: number
  gender?: string
  style?: string
  prompt?: string
}

export interface VoiceConfig {
  provider: string
  presetVoiceId: number
  voiceName?: string
  speechRate: number
  pitchRate: number
  volume: number
}

export interface ModelConfig {
  provider?: string
  model?: string
  temperature?: number
  maxTokens?: number
  systemPrompt?: string
}

/** 预设音色（与后端 PresetVoiceVO 对齐） */
export interface PresetVoice {
  id: number
  name: string
  description?: string
  gender?: string
  sampleAudioUrl?: string
  provider: string
  externalVoiceId: string
  isActive: boolean
  displayOrder: number
}

export interface DigitalHumanListParams {
  /** 页码，从 1 开始 */
  pageNo?: number
  pageSize?: number
  name?: string
  status?: DigitalHumanStatus
}

/**
 * 数字人 Patch 更新：未传不改，null 清空可空字段，有值覆盖。
 * assistantId 与 status 只能通过关联助理、归档、恢复命令修改。
 */
export interface DigitalHumanUpdateInput {
  name?: string
  description?: string | null
  avatarImageUrl?: string
  tags?: string[] | null
  appearance?: AppearanceConfig | null
  voiceConfig?: VoiceConfig
  modelConfig?: ModelConfig | null
  syncModelConfig?: boolean
  /** 当前数字人的 version */
  expectedVersion: number
}

/** 切换音色命令 */
export interface ChangeVoiceInput {
  presetVoiceId: number
  speechRate?: number
  pitchRate?: number
  volume?: number
}

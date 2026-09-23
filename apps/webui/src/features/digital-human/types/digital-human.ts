export interface DigitalHuman {
  id: string
  userId: string
  organizationId?: string
  workspaceId?: string

  name: string
  description?: string
  tags: string[]

  avatarImageUrl: string
  appearance: AppearanceConfig
  voiceConfig: VoiceConfig
  modelConfig?: ModelConfig

  assistantId?: string
  syncModelConfig: boolean

  stats: {
    videoCount: number
    totalDuration: number
    creditsConsumed: number
    lastUsedAt?: string
  }

  status: "active" | "archived"
  generationSource: "aigc_project"
  aigcProjectId?: string

  permissions?: {
    canView: boolean
    canUse: boolean
    canEdit: boolean
    canDelete: boolean
  }

  createdBy?: {
    id: string
    name: string
  }

  createTime: string
  updateTime: string
}

export interface AppearanceConfig {
  age?: number
  gender: "male" | "female" | "neutral"
  style: string
  prompt: string
}

export interface VoiceConfig {
  provider: "aliyun"
  presetVoiceId: string
  voiceName: string
  speechRate: number
  pitchRate: number
  volume: number
}

export interface ModelConfig {
  provider: string
  model: string
  temperature?: number
  maxTokens?: number
  systemPrompt?: string
}

export interface PresetVoice {
  id: string
  name: string
  description: string
  gender: "male" | "female"
  sampleAudioUrl: string
  provider: "aliyun"
  externalVoiceId: string
  displayOrder: number
}

export interface DigitalHumanListParams {
  name?: string
  tags?: string[]
  status?: "active" | "archived"
  page?: number
  size?: number
}

export interface DigitalHumanListResponse {
  content: DigitalHuman[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

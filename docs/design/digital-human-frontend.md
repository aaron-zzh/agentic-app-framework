# 数字人系统前端组件设计

**版本**: v1.0  
**日期**: 2026-09-22  
**框架**: React 19 + TypeScript  
**状态管理**: TanStack Query + Zustand  
**UI 库**: Shadcn/ui + Tailwind CSS

---

## 一、目录结构

```
apps/webui/src/features/digital-human/
├── components/                          # 共享组件
│   ├── DigitalHumanCard.tsx            # 数字人卡片
│   ├── VoiceSelector.tsx               # 音色选择器
│   ├── VoicePlayer.tsx                 # 音色试听播放器
│   ├── SceneEditor.tsx                 # 场景编辑器
│   └── VideoPreview.tsx                # 视频预览播放器
│
├── generation/                          # 模块一：资产生成
│   ├── GenerationProjectCreator.tsx    # 项目创建入口
│   └── hooks/
│       └── useCreateGenerationProject.ts
│
├── management/                          # 模块二：数字人管理
│   ├── DigitalHumanList.tsx            # 列表页
│   ├── DigitalHumanDetail.tsx          # 详情/编辑页
│   ├── components/
│   │   ├── FilterBar.tsx               # 筛选栏
│   │   ├── PreviewPanel.tsx            # 预览面板
│   │   ├── BasicInfoSection.tsx        # 基础信息配置
│   │   ├── VoiceConfigSection.tsx      # 音色配置
│   │   ├── ModelConfigSection.tsx      # 模型配置
│   │   ├── AssistantLinkSection.tsx    # 助理关联
│   │   └── StatsSection.tsx            # 使用统计
│   └── hooks/
│       ├── useDigitalHumans.ts         # 列表查询
│       ├── useDigitalHuman.ts          # 详情查询
│       ├── useUpdateDigitalHuman.ts    # 更新
│       └── useDeleteDigitalHuman.ts    # 删除
│
├── video/                               # 模块三：视频生成
│   ├── VideoProjectCreator.tsx         # 视频项目创建入口
│   └── hooks/
│       └── useCreateVideoProject.ts
│
├── hooks/                               # 通用 Hooks
│   ├── usePresetVoices.ts              # 预设音色查询
│   └── useDigitalHumanStats.ts         # 统计数据查询
│
└── types/                               # 类型定义
    ├── digital-human.ts
    ├── preset-voice.ts
    └── video-scene.ts
```

---

## 二、核心组件设计

### 2.1 DigitalHumanCard（数字人卡片）

**用途**: 列表页展示数字人卡片

**Props**:
```typescript
interface DigitalHumanCardProps {
  digitalHuman: DigitalHuman
  onView: (id: string) => void
  onEdit?: (id: string) => void
  onDelete?: (id: string) => void
}
```

**实现**:
```typescript
import { Card } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { MoreVertical, Eye, Edit, Trash } from 'lucide-react'

export function DigitalHumanCard({
  digitalHuman,
  onView,
  onEdit,
  onDelete,
}: DigitalHumanCardProps) {
  const canEdit = digitalHuman.permissions?.canEdit
  const isOrgShared = !digitalHuman.permissions?.canEdit && digitalHuman.organizationId

  return (
    <Card className="overflow-hidden transition-shadow hover:shadow-lg">
      {/* 头像 */}
      <div className="relative aspect-square overflow-hidden bg-muted">
        <img
          src={digitalHuman.avatarImageUrl}
          alt={digitalHuman.name}
          className="size-full object-cover"
        />
        {isOrgShared && (
          <Badge className="absolute right-2 top-2" variant="secondary">
            组织共享
          </Badge>
        )}
      </div>

      {/* 内容 */}
      <div className="p-4">
        <h3 className="mb-1 truncate font-semibold">{digitalHuman.name}</h3>
        <p className="mb-2 text-muted-foreground text-sm">
          {digitalHuman.voiceConfig.voiceName} · {digitalHuman.appearance.gender === 'female' ? '女声' : '男声'}
        </p>

        {/* 标签 */}
        <div className="mb-3 flex flex-wrap gap-1">
          {digitalHuman.tags.slice(0, 3).map((tag) => (
            <Badge key={tag} variant="outline" className="text-xs">
              {tag}
            </Badge>
          ))}
          {digitalHuman.tags.length > 3 && (
            <Badge variant="outline" className="text-xs">
              +{digitalHuman.tags.length - 3}
            </Badge>
          )}
        </div>

        {/* 统计 */}
        <div className="mb-3 space-y-1 text-muted-foreground text-xs">
          <div>使用 {digitalHuman.stats.videoCount} 次</div>
          {digitalHuman.assistantId && <div>关联助理 ✓</div>}
        </div>

        {/* 操作 */}
        <div className="flex items-center gap-2">
          <Button size="sm" onClick={() => onView(digitalHuman.id)} className="flex-1">
            <Eye className="mr-1 size-3.5" />
            查看
          </Button>

          {canEdit && (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button size="sm" variant="ghost">
                  <MoreVertical className="size-3.5" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem onClick={() => onEdit?.(digitalHuman.id)}>
                  <Edit className="mr-2 size-3.5" />
                  编辑
                </DropdownMenuItem>
                <DropdownMenuItem
                  onClick={() => onDelete?.(digitalHuman.id)}
                  className="text-destructive"
                >
                  <Trash className="mr-2 size-3.5" />
                  删除
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          )}
        </div>
      </div>
    </Card>
  )
}
```

---

### 2.2 VoiceSelector（音色选择器）

**用途**: 选择和试听预设音色

**Props**:
```typescript
interface VoiceSelectorProps {
  value: string  // 当前选中的音色ID
  onChange: (voiceId: string) => void
  open: boolean
  onOpenChange: (open: boolean) => void
}
```

**实现**:
```typescript
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Label } from '@/components/ui/label'
import { Volume2 } from 'lucide-react'
import { usePresetVoices } from '../hooks/usePresetVoices'
import { VoicePlayer } from './VoicePlayer'

export function VoiceSelector({ value, onChange, open, onOpenChange }: VoiceSelectorProps) {
  const { data: voices, isLoading } = usePresetVoices()
  const [playingVoiceId, setPlayingVoiceId] = useState<string | null>(null)
  const [selectedVoiceId, setSelectedVoiceId] = useState(value)

  const handleConfirm = () => {
    onChange(selectedVoiceId)
    onOpenChange(false)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>选择音色</DialogTitle>
        </DialogHeader>

        <div className="space-y-2">
          {isLoading ? (
            <div className="py-8 text-center text-muted-foreground">加载中...</div>
          ) : (
            <RadioGroup value={selectedVoiceId} onValueChange={setSelectedVoiceId}>
              {voices?.map((voice) => (
                <div
                  key={voice.id}
                  className="flex items-center justify-between rounded-lg border p-3 hover:bg-muted/50"
                >
                  <div className="flex items-center gap-3">
                    <RadioGroupItem value={voice.id} id={voice.id} />
                    <Label htmlFor={voice.id} className="cursor-pointer">
                      <div className="font-medium">{voice.name}</div>
                      <div className="text-muted-foreground text-xs">{voice.description}</div>
                    </Label>
                  </div>

                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => setPlayingVoiceId(voice.id)}
                  >
                    <Volume2 className="size-4" />
                  </Button>
                </div>
              ))}
            </RadioGroup>
          )}
        </div>

        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button onClick={handleConfirm}>确认选择</Button>
        </div>

        {/* 音频播放器 */}
        {playingVoiceId && (
          <VoicePlayer
            voiceId={playingVoiceId}
            onClose={() => setPlayingVoiceId(null)}
          />
        )}
      </DialogContent>
    </Dialog>
  )
}
```

---

### 2.3 DigitalHumanList（列表页）

**路由**: `/digital-humans`

**State**:
```typescript
interface ListState {
  scope: 'all' | 'mine' | 'org'
  search: string
  selectedTags: string[]
  page: number
  pageSize: number
}
```

**实现**:
```typescript
import { useState } from 'react'
import { useDigitalHumans } from './hooks/useDigitalHumans'
import { DigitalHumanCard } from '../components/DigitalHumanCard'
import { FilterBar } from './components/FilterBar'
import { Button } from '@/components/ui/button'
import { Plus } from 'lucide-react'
import { useNavigate } from 'react-router-dom'

export function DigitalHumanList() {
  const navigate = useNavigate()
  const [state, setState] = useState<ListState>({
    scope: 'all',
    search: '',
    selectedTags: [],
    page: 1,
    pageSize: 20,
  })

  const { data, isLoading } = useDigitalHumans({
    scope: state.scope,
    search: state.search,
    tags: state.selectedTags.join(','),
    page: state.page,
    pageSize: state.pageSize,
  })

  const handleCreate = () => {
    navigate('/aigc/projects/create?blueprint=digital-human-asset-generation')
  }

  return (
    <div className="container py-6">
      {/* 标题栏 */}
      <div className="mb-6 flex items-center justify-between">
        <h1 className="font-semibold text-2xl">数字人管理</h1>
        <Button onClick={handleCreate}>
          <Plus className="mr-2 size-4" />
          创建数字人
        </Button>
      </div>

      {/* 筛选栏 */}
      <FilterBar
        scope={state.scope}
        search={state.search}
        selectedTags={state.selectedTags}
        onScopeChange={(scope) => setState((s) => ({ ...s, scope, page: 1 }))}
        onSearchChange={(search) => setState((s) => ({ ...s, search, page: 1 }))}
        onTagsChange={(tags) => setState((s) => ({ ...s, selectedTags: tags, page: 1 }))}
      />

      {/* 卡片网格 */}
      {isLoading ? (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <div key={i} className="aspect-[3/4] animate-pulse rounded-lg bg-muted" />
          ))}
        </div>
      ) : data?.items.length === 0 ? (
        <div className="py-12 text-center">
          <p className="mb-4 text-muted-foreground">暂无数字人</p>
          <Button onClick={handleCreate}>创建第一个数字人</Button>
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {data?.items.map((dh) => (
            <DigitalHumanCard
              key={dh.id}
              digitalHuman={dh}
              onView={(id) => navigate(`/digital-humans/${id}`)}
              onEdit={(id) => navigate(`/digital-humans/${id}/edit`)}
              onDelete={(id) => {
                // 显示确认对话框
              }}
            />
          ))}
        </div>
      )}

      {/* 分页 */}
      {data && data.pagination.totalPages > 1 && (
        <div className="mt-6 flex justify-center">
          {/* Pagination 组件 */}
        </div>
      )}
    </div>
  )
}
```

---

### 2.4 DigitalHumanDetail（详情/编辑页）

**路由**: `/digital-humans/:id`

**布局**: 左右分栏，左侧预览，右侧配置

**实现**:
```typescript
import { useParams } from 'react-router-dom'
import { useDigitalHuman } from './hooks/useDigitalHuman'
import { useUpdateDigitalHuman } from './hooks/useUpdateDigitalHuman'
import { PreviewPanel } from './components/PreviewPanel'
import { BasicInfoSection } from './components/BasicInfoSection'
import { VoiceConfigSection } from './components/VoiceConfigSection'
import { ModelConfigSection } from './components/ModelConfigSection'
import { AssistantLinkSection } from './components/AssistantLinkSection'
import { StatsSection } from './components/StatsSection'
import { Button } from '@/components/ui/button'
import { Save } from 'lucide-react'
import { useState } from 'react'

export function DigitalHumanDetail() {
  const { id } = useParams<{ id: string }>()
  const { data: digitalHuman, isLoading } = useDigitalHuman(id!)
  const updateMutation = useUpdateDigitalHuman()
  
  const [formData, setFormData] = useState<Partial<DigitalHuman>>({})

  useEffect(() => {
    if (digitalHuman) {
      setFormData({
        name: digitalHuman.name,
        description: digitalHuman.description,
        tags: digitalHuman.tags,
        voiceConfig: digitalHuman.voiceConfig,
        modelConfig: digitalHuman.modelConfig,
        assistantId: digitalHuman.assistantId,
        syncModelConfig: digitalHuman.syncModelConfig,
      })
    }
  }, [digitalHuman])

  const handleSave = () => {
    updateMutation.mutate({
      id: id!,
      data: formData,
    })
  }

  if (isLoading) {
    return <div>加载中...</div>
  }

  if (!digitalHuman) {
    return <div>数字人不存在</div>
  }

  const canEdit = digitalHuman.permissions?.canEdit

  return (
    <div className="container py-6">
      {/* 标题栏 */}
      <div className="mb-6 flex items-center justify-between">
        <h1 className="font-semibold text-2xl">{digitalHuman.name}</h1>
        {canEdit && (
          <div className="flex gap-2">
            <Button onClick={handleSave} disabled={updateMutation.isPending}>
              <Save className="mr-2 size-4" />
              保存
            </Button>
            <Button variant="outline">生成视频</Button>
          </div>
        )}
      </div>

      {/* 两栏布局 */}
      <div className="grid gap-6 lg:grid-cols-[300px_1fr]">
        {/* 左侧：预览 */}
        <PreviewPanel digitalHuman={digitalHuman} />

        {/* 右侧：配置 */}
        <div className="space-y-6">
          <BasicInfoSection
            data={formData}
            onChange={setFormData}
            disabled={!canEdit}
          />

          <VoiceConfigSection
            data={formData}
            onChange={setFormData}
            disabled={!canEdit}
          />

          <ModelConfigSection
            data={formData}
            onChange={setFormData}
            disabled={!canEdit}
          />

          <AssistantLinkSection
            data={formData}
            onChange={setFormData}
            disabled={!canEdit}
          />

          <StatsSection stats={digitalHuman.stats} />
        </div>
      </div>
    </div>
  )
}
```

---

## 三、React Query Hooks

### 3.1 useDigitalHumans（列表查询）

```typescript
import { useQuery } from '@tanstack/react-query'
import { digitalHumanApi } from '@/lib/api/digital-human'

interface UseDigitalHumansParams {
  scope?: 'all' | 'mine' | 'org'
  search?: string
  tags?: string
  page?: number
  pageSize?: number
  status?: 'active' | 'archived'
}

export function useDigitalHumans(params: UseDigitalHumansParams = {}) {
  return useQuery({
    queryKey: ['digital-humans', params],
    queryFn: () => digitalHumanApi.list(params),
    staleTime: 30_000, // 30秒
  })
}
```

### 3.2 useDigitalHuman（详情查询）

```typescript
export function useDigitalHuman(id: string) {
  return useQuery({
    queryKey: ['digital-human', id],
    queryFn: () => digitalHumanApi.get(id),
    enabled: !!id,
  })
}
```

### 3.3 useUpdateDigitalHuman（更新）

```typescript
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { digitalHumanApi } from '@/lib/api/digital-human'
import { toast } from 'sonner'

export function useUpdateDigitalHuman() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<DigitalHuman> }) =>
      digitalHumanApi.update(id, data),
    onSuccess: (_, variables) => {
      toast.success('保存成功')
      queryClient.invalidateQueries({ queryKey: ['digital-human', variables.id] })
      queryClient.invalidateQueries({ queryKey: ['digital-humans'] })
    },
    onError: (error) => {
      toast.error('保存失败', { description: error.message })
    },
  })
}
```

### 3.4 usePresetVoices（预设音色）

```typescript
export function usePresetVoices() {
  return useQuery({
    queryKey: ['preset-voices'],
    queryFn: () => digitalHumanApi.getPresetVoices(),
    staleTime: 5 * 60 * 1000, // 5分钟
  })
}
```

---

## 四、API 客户端

```typescript
// apps/webui/src/lib/api/digital-human.ts

import { apiClient } from './client'
import type {
  DigitalHuman,
  PresetVoice,
  DigitalHumanListParams,
  DigitalHumanListResponse,
} from '@/types/digital-human'

export const digitalHumanApi = {
  // 列表
  list: (params: DigitalHumanListParams) =>
    apiClient.get<DigitalHumanListResponse>('/digital-humans', { params }),

  // 详情
  get: (id: string) =>
    apiClient.get<DigitalHuman>(`/digital-humans/${id}`),

  // 更新
  update: (id: string, data: Partial<DigitalHuman>) =>
    apiClient.put<DigitalHuman>(`/digital-humans/${id}`, data),

  // 删除
  delete: (id: string) =>
    apiClient.delete(`/digital-humans/${id}`),

  // 切换音色
  changeVoice: (id: string, voiceId: string) =>
    apiClient.post(`/digital-humans/${id}/change-voice`, { presetVoiceId: voiceId }),

  // 关联助理
  linkAssistant: (id: string, assistantId: string, syncConfig: boolean) =>
    apiClient.post(`/digital-humans/${id}/link-assistant`, {
      assistantId,
      syncModelConfig: syncConfig,
    }),

  // 取消关联助理
  unlinkAssistant: (id: string) =>
    apiClient.delete(`/digital-humans/${id}/link-assistant`),

  // 归档
  archive: (id: string) =>
    apiClient.post(`/digital-humans/${id}/archive`),

  // 恢复
  restore: (id: string) =>
    apiClient.post(`/digital-humans/${id}/restore`),

  // 预设音色
  getPresetVoices: () =>
    apiClient.get<PresetVoice[]>('/preset-voices'),

  // 统计
  getStats: (period: 'day' | 'week' | 'month' | 'all' = 'month') =>
    apiClient.get('/digital-humans/stats/overview', { params: { period } }),
}
```

---

## 五、类型定义

```typescript
// apps/webui/src/types/digital-human.ts

export interface DigitalHuman {
  id: string
  userId: string
  organizationId?: string
  
  name: string
  description?: string
  tags: string[]
  
  avatarImageUrl: string
  appearance: {
    age?: number
    gender: 'male' | 'female' | 'neutral'
    style: string
    prompt: string
  }
  
  voiceConfig: {
    provider: 'aliyun'
    presetVoiceId: string
    voiceName: string
    speechRate: number
    pitchRate: number
    volume: number
  }
  
  modelConfig?: {
    provider: string
    model: string
    temperature?: number
    maxTokens?: number
    systemPrompt?: string
  }
  
  assistantId?: string
  syncModelConfig: boolean
  
  stats: {
    videoCount: number
    totalDuration: number
    creditsConsumed: number
    lastUsedAt?: string
  }
  
  status: 'active' | 'archived'
  generationSource: 'aigc_project'
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
  
  createdAt: string
  updatedAt: string
}

export interface PresetVoice {
  id: string
  name: string
  description: string
  gender: 'male' | 'female'
  sampleAudioUrl: string
  provider: 'aliyun'
  externalVoiceId: string
}

export interface DigitalHumanListParams {
  page?: number
  pageSize?: number
  scope?: 'all' | 'mine' | 'org'
  search?: string
  tags?: string
  status?: 'active' | 'archived'
}

export interface DigitalHumanListResponse {
  items: DigitalHuman[]
  pagination: {
    page: number
    pageSize: number
    total: number
    totalPages: number
  }
}
```

---

## 六、路由配置

```typescript
// apps/webui/src/app/routes.tsx

import { lazy } from 'react'

const DigitalHumanList = lazy(() => import('@/features/digital-human/management/DigitalHumanList'))
const DigitalHumanDetail = lazy(() => import('@/features/digital-human/management/DigitalHumanDetail'))

export const routes = [
  // ... 其他路由
  {
    path: '/digital-humans',
    element: <DigitalHumanList />,
  },
  {
    path: '/digital-humans/:id',
    element: <DigitalHumanDetail />,
  },
]
```

---

## 七、性能优化

### 7.1 图片懒加载

```typescript
<img
  src={digitalHuman.avatarImageUrl}
  alt={digitalHuman.name}
  loading="lazy"
  className="size-full object-cover"
/>
```

### 7.2 虚拟滚动（大列表）

```typescript
import { useVirtualizer } from '@tanstack/react-virtual'

// 当数字人数量 > 100 时启用
```

### 7.3 Query 缓存策略

- 列表：30秒
- 详情：5分钟
- 预设音色：5分钟（几乎不变）

---

**文档版本**: v1.0  
**最后更新**: 2026-09-22

# Phase 4 实施方案：视频生成模块

**版本**: v1.0  
**日期**: 2026-09-22  
**状态**: 设计中

---

## 📋 实施范围调整

### ✅ Phase 2 已完成
- Blueprint YAML 配置已完成
- 种子数据已创建
- 部署文档已完成

### 🎯 Phase 4 核心任务

根据当前架构，Phase 4 需要以下工作：

1. **后端执行器集成** (已有框架，需配置)
   - 脚本生成执行器（复用现有Claude集成）
   - 简单视频执行器（复用现有阿里云集成）
   - Hyperframes集成执行器（新增）

2. **前端场景编辑器** (新开发)
   - 场景列表组件
   - 场景编辑表单
   - 实时预览

3. **数字人使用统计更新** (API已完成)
   - postDeliveryAction webhook处理

---

## 🔧 后端执行器配置

### 方案说明

AIGC平台已有执行器框架，我们需要的执行器可能已经存在或只需配置：

#### 1. aigc_text_generation (脚本生成)
**状态**: 应该已存在（用于文案生成等）

**验证命令**:
```sql
SELECT id, code, name, status 
FROM aigc_execution_binding 
WHERE code = 'aigc_text_generation';
```

**如不存在，需添加**:
```sql
INSERT INTO aigc_execution_binding (code, name, version, target_type, description, status)
VALUES ('aigc_text_generation', 'AI文本生成', '1.0.0', 'TOOL', 'Claude文本生成执行器', 'published');
```

#### 2. aliyun_image_generation (形象生成)
**状态**: 应该已存在（用于图像生成）

**验证命令**:
```sql
SELECT id, code, name, status 
FROM aigc_execution_binding 
WHERE code = 'aliyun_image_generation';
```

#### 3. aliyun_video_generation (简单视频)
**状态**: 需要确认或新增

**验证命令**:
```sql
SELECT id, code, name, status 
FROM aigc_execution_binding 
WHERE code = 'aliyun_video_generation';
```

**如不存在，添加配置**:
```sql
INSERT INTO aigc_execution_binding (code, name, version, target_type, description, status, config)
VALUES (
  'aliyun_video_generation',
  '阿里云视频生成',
  '1.0.0',
  'TOOL',
  '阿里云数字人视频生成API',
  'published',
  '{
    "provider": "aliyun",
    "apiEndpoint": "https://api.aliyun.com/video/generate",
    "timeout": 180000,
    "maxDuration": 15,
    "supportedFormats": ["mp4"],
    "supportedRatios": ["16:9", "9:16", "1:1"]
  }'::jsonb
);
```

#### 4. hyperframes_integration (复杂视频)
**状态**: 需要新增

**添加配置**:
```sql
INSERT INTO aigc_execution_binding (code, name, version, target_type, description, status, config)
VALUES (
  'hyperframes_integration',
  'Hyperframes视频合成',
  '1.0.0',
  'TOOL',
  'Hyperframes多场景视频合成API',
  'published',
  '{
    "provider": "hyperframes",
    "apiEndpoint": "https://api.hyperframes.ai/v1/compose",
    "timeout": 600000,
    "maxScenes": 50,
    "maxTotalDuration": 600,
    "supportedFormats": ["mp4"],
    "supportedRatios": ["16:9", "9:16", "1:1"]
  }'::jsonb
);
```

---

## 📦 执行器实现（如需新增）

### 1. 视频生成执行器接口

如果需要实现新的执行器适配器，参考现有模式：

```java
package com.xuejiai.aaf.module.digitalhuman.execution;

import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcToolExecutionPort;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcRuntimeExecution.Command;
import org.springframework.stereotype.Component;

/**
 * 数字人视频生成执行器适配器
 * @author Kiro
 */
@Component("aliyun_video_generation")
public class AliyunVideoGenerationAdapter implements AigcToolExecutionPort {

    @Override
    public ToolResult execute(Command command) {
        // 1. 解析输入参数
        String prompt = command.getInput("prompt");
        String referenceImage = command.getInput("referenceImage");
        Map<String, Object> voiceConfig = command.getInput("voiceConfig");
        int duration = command.getInput("duration", Integer.class);
        String aspectRatio = command.getInput("aspectRatio");
        
        // 2. 调用阿里云视频生成API
        // TODO: 实际API调用逻辑
        String videoUrl = callAliyunVideoApi(prompt, referenceImage, voiceConfig, duration, aspectRatio);
        
        // 3. 返回结果
        Map<String, Object> output = Map.of(
            "videoUrl", videoUrl,
            "duration", duration,
            "provider", "aliyun"
        );
        
        return new ToolResult(toJson(output), List.of());
    }
    
    private String callAliyunVideoApi(String prompt, String referenceImage, 
                                      Map<String, Object> voiceConfig, 
                                      int duration, String aspectRatio) {
        // 实际API调用实现
        return "https://example.com/video.mp4";
    }
}
```

### 2. Hyperframes执行器

```java
package com.xuejiai.aaf.module.digitalhuman.execution;

import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcToolExecutionPort;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcRuntimeExecution.Command;
import org.springframework.stereotype.Component;

/**
 * Hyperframes多场景视频合成执行器
 * @author Kiro
 */
@Component("hyperframes_integration")
public class HyperframesIntegrationAdapter implements AigcToolExecutionPort {

    @Override
    public ToolResult execute(Command command) {
        // 1. 解析输入参数
        Long digitalHumanId = command.getInput("digitalHumanId", Long.class);
        String avatarImageUrl = command.getInput("avatarImageUrl");
        List<Map<String, Object>> scenes = command.getInput("scenes", List.class);
        String aspectRatio = command.getInput("aspectRatio");
        
        // 2. 调用Hyperframes API
        // TODO: 实际API调用逻辑
        String videoUrl = callHyperframesApi(avatarImageUrl, scenes, aspectRatio);
        
        // 3. 返回结果
        Map<String, Object> output = Map.of(
            "videoUrl", videoUrl,
            "duration", calculateTotalDuration(scenes),
            "sceneCount", scenes.size(),
            "provider", "hyperframes"
        );
        
        return new ToolResult(toJson(output), List.of());
    }
    
    private String callHyperframesApi(String avatarImageUrl, 
                                      List<Map<String, Object>> scenes,
                                      String aspectRatio) {
        // 实际API调用实现
        return "https://example.com/composed-video.mp4";
    }
    
    private int calculateTotalDuration(List<Map<String, Object>> scenes) {
        return scenes.stream()
            .mapToInt(scene -> (int) scene.getOrDefault("duration", 0))
            .sum();
    }
}
```

---

## 🎨 前端场景编辑器

### 1. 场景编辑器组件

```typescript
// apps/webui/src/features/digital-human/video/SceneEditor.tsx

"use client"

import { useState } from "react"
import { GripVertical, Plus, Trash, Play } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import {
  DndContext,
  closestCenter,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors
} from "@dnd-kit/core"
import {
  arrayMove,
  SortableContext,
  sortableKeyboardCoordinates,
  verticalListSortingStrategy
} from "@dnd-kit/sortable"
import { useSortable } from "@dnd-kit/sortable"
import { CSS } from "@dnd-kit/utilities"

interface Scene {
  id: string
  order: number
  title: string
  content: string
  duration: number
  sceneDescription?: string
}

interface SceneEditorProps {
  value: Scene[]
  onChange: (scenes: Scene[]) => void
  disabled?: boolean
}

export function SceneEditor({ value, onChange, disabled }: SceneEditorProps) {
  const sensors = useSensors(
    useSensor(PointerSensor),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates
    })
  )

  const handleDragEnd = (event: any) => {
    const { active, over } = event
    if (active.id !== over.id) {
      const oldIndex = value.findIndex((s) => s.id === active.id)
      const newIndex = value.findIndex((s) => s.id === over.id)
      const reordered = arrayMove(value, oldIndex, newIndex)
      onChange(reordered.map((s, i) => ({ ...s, order: i + 1 })))
    }
  }

  const addScene = () => {
    const newScene: Scene = {
      id: `scene-${Date.now()}`,
      order: value.length + 1,
      title: `场景 ${value.length + 1}`,
      content: "",
      duration: 10
    }
    onChange([...value, newScene])
  }

  const removeScene = (id: string) => {
    onChange(value.filter((s) => s.id !== id))
  }

  const updateScene = (id: string, updates: Partial<Scene>) => {
    onChange(value.map((s) => (s.id === id ? { ...s, ...updates } : s)))
  }

  const totalDuration = value.reduce((sum, s) => sum + s.duration, 0)

  return (
    <div className="space-y-4">
      {/* 统计信息 */}
      <div className="flex items-center justify-between">
        <div className="text-muted-foreground text-sm">
          共 {value.length} 个场景，总时长 {totalDuration} 秒
        </div>
        <Button onClick={addScene} disabled={disabled} size="sm">
          <Plus className="mr-2 size-4" />
          添加场景
        </Button>
      </div>

      {/* 场景列表 */}
      <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
        <SortableContext items={value.map((s) => s.id)} strategy={verticalListSortingStrategy}>
          <div className="space-y-3">
            {value.map((scene) => (
              <SortableSceneCard
                key={scene.id}
                scene={scene}
                disabled={disabled}
                onUpdate={(updates) => updateScene(scene.id, updates)}
                onRemove={() => removeScene(scene.id)}
              />
            ))}
          </div>
        </SortableContext>
      </DndContext>

      {value.length === 0 && (
        <Card className="border-dashed">
          <CardContent className="py-12 text-center">
            <p className="mb-4 text-muted-foreground">暂无场景</p>
            <Button onClick={addScene} disabled={disabled}>
              添加第一个场景
            </Button>
          </CardContent>
        </Card>
      )}
    </div>
  )
}

function SortableSceneCard({
  scene,
  disabled,
  onUpdate,
  onRemove
}: {
  scene: Scene
  disabled?: boolean
  onUpdate: (updates: Partial<Scene>) => void
  onRemove: () => void
}) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: scene.id,
    disabled
  })

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1
  }

  return (
    <Card ref={setNodeRef} style={style}>
      <CardHeader className="flex flex-row items-center space-y-0 pb-3">
        <div className="flex flex-1 items-center gap-3">
          <button
            className="cursor-grab touch-none text-muted-foreground hover:text-foreground"
            {...attributes}
            {...listeners}
          >
            <GripVertical className="size-5" />
          </button>
          <Input
            value={scene.title}
            onChange={(e) => onUpdate({ title: e.target.value })}
            disabled={disabled}
            className="max-w-xs"
            placeholder="场景标题"
          />
        </div>
        <div className="flex items-center gap-2">
          <Button variant="ghost" size="icon-sm" disabled>
            <Play className="size-4" />
          </Button>
          <Button
            variant="ghost"
            size="icon-sm"
            onClick={onRemove}
            disabled={disabled}
            className="text-destructive"
          >
            <Trash className="size-4" />
          </Button>
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        <div>
          <Label htmlFor={`content-${scene.id}`}>播报内容</Label>
          <Textarea
            id={`content-${scene.id}`}
            value={scene.content}
            onChange={(e) => onUpdate({ content: e.target.value })}
            disabled={disabled}
            rows={3}
            placeholder="输入数字人播报的文案内容..."
          />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <Label htmlFor={`duration-${scene.id}`}>时长（秒）</Label>
            <Input
              id={`duration-${scene.id}`}
              type="number"
              value={scene.duration}
              onChange={(e) => onUpdate({ duration: parseInt(e.target.value) || 0 })}
              disabled={disabled}
              min={1}
              max={15}
            />
          </div>
          <div>
            <Label htmlFor={`desc-${scene.id}`}>场景描述（可选）</Label>
            <Input
              id={`desc-${scene.id}`}
              value={scene.sceneDescription || ""}
              onChange={(e) => onUpdate({ sceneDescription: e.target.value })}
              disabled={disabled}
              placeholder="如：正面特写、微笑"
            />
          </div>
        </div>
      </CardContent>
    </Card>
  )
}
```

### 2. 视频预览播放器

```typescript
// apps/webui/src/features/digital-human/video/VideoPlayer.tsx

"use client"

import { useRef, useState } from "react"
import { Download, Pause, Play, Volume2, VolumeX } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Slider } from "@/components/ui/slider"
import { downloadFileWithToast } from "@/lib/utils"

interface VideoPlayerProps {
  url: string
  title?: string
  duration?: number
  onDownload?: () => void
}

export function VideoPlayer({ url, title, duration, onDownload }: VideoPlayerProps) {
  const videoRef = useRef<HTMLVideoElement>(null)
  const [playing, setPlaying] = useState(false)
  const [currentTime, setCurrentTime] = useState(0)
  const [volume, setVolume] = useState(1)
  const [muted, setMuted] = useState(false)

  const togglePlay = () => {
    if (!videoRef.current) return
    if (playing) {
      videoRef.current.pause()
    } else {
      videoRef.current.play()
    }
    setPlaying(!playing)
  }

  const handleTimeUpdate = () => {
    if (videoRef.current) {
      setCurrentTime(videoRef.current.currentTime)
    }
  }

  const handleSeek = (value: number[]) => {
    if (videoRef.current) {
      videoRef.current.currentTime = value[0]
      setCurrentTime(value[0])
    }
  }

  const handleVolumeChange = (value: number[]) => {
    if (videoRef.current) {
      videoRef.current.volume = value[0]
      setVolume(value[0])
      setMuted(value[0] === 0)
    }
  }

  const toggleMute = () => {
    if (videoRef.current) {
      videoRef.current.muted = !muted
      setMuted(!muted)
    }
  }

  const handleDownload = () => {
    if (onDownload) {
      onDownload()
    } else {
      downloadFileWithToast(url, title || "video.mp4")
    }
  }

  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60)
    const secs = Math.floor(seconds % 60)
    return `${mins}:${secs.toString().padStart(2, "0")}`
  }

  const videoDuration = duration || videoRef.current?.duration || 0

  return (
    <Card>
      <CardContent className="p-0">
        {/* 视频画面 */}
        <div className="relative aspect-video bg-black">
          <video
            ref={videoRef}
            src={url}
            className="size-full"
            onTimeUpdate={handleTimeUpdate}
            onEnded={() => setPlaying(false)}
            onClick={togglePlay}
          />
        </div>

        {/* 控制栏 */}
        <div className="space-y-2 p-4">
          {/* 进度条 */}
          <div className="flex items-center gap-2">
            <span className="text-muted-foreground text-xs">{formatTime(currentTime)}</span>
            <Slider
              value={[currentTime]}
              onValueChange={handleSeek}
              max={videoDuration}
              step={0.1}
              className="flex-1"
            />
            <span className="text-muted-foreground text-xs">{formatTime(videoDuration)}</span>
          </div>

          {/* 控制按钮 */}
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Button size="icon" variant="ghost" onClick={togglePlay}>
                {playing ? <Pause className="size-4" /> : <Play className="size-4" />}
              </Button>
              <div className="flex items-center gap-2">
                <Button size="icon" variant="ghost" onClick={toggleMute}>
                  {muted ? <VolumeX className="size-4" /> : <Volume2 className="size-4" />}
                </Button>
                <Slider
                  value={[muted ? 0 : volume]}
                  onValueChange={handleVolumeChange}
                  max={1}
                  step={0.1}
                  className="w-24"
                />
              </div>
            </div>
            <Button size="sm" variant="outline" onClick={handleDownload}>
              <Download className="mr-2 size-4" />
              下载
            </Button>
          </div>
        </div>
      </CardContent>
    </Card>
  )
}
```

---

## 📝 实施检查清单

### 后端

- [ ] 验证 `aigc_text_generation` 执行器存在
- [ ] 验证 `aliyun_image_generation` 执行器存在
- [ ] 添加 `aliyun_video_generation` 执行器配置
- [ ] 添加 `hyperframes_integration` 执行器配置
- [ ] 实现视频生成执行器适配器（如需要）
- [ ] 实现Hyperframes执行器适配器（如需要）
- [ ] 添加使用统计更新API端点

### 前端

- [ ] 创建场景编辑器组件 (SceneEditor.tsx)
- [ ] 创建视频播放器组件 (VideoPlayer.tsx)
- [ ] 安装依赖: `@dnd-kit/core`, `@dnd-kit/sortable`, `@dnd-kit/utilities`
- [ ] 集成到AIGC项目详情页
- [ ] 测试拖拽排序功能
- [ ] 测试视频播放功能

### 集成测试

- [ ] 端到端测试：资产生成 → 视频生成
- [ ] 测试简单视频生成（≤15秒）
- [ ] 测试复杂视频生成（>15秒）
- [ ] 测试场景编辑
- [ ] 测试使用统计更新
- [ ] 测试积分消耗计算

---

## 🚧 当前阻塞点

1. **执行器框架确认**: 需要确认现有AIGC执行器框架的具体实现
2. **API密钥配置**: 需要阿里云和Hyperframes的API密钥
3. **执行器注册**: 需要了解执行器如何注册到系统中

---

## 🎯 最小可行方案 (MVP)

如果时间紧张，可以先实现：

1. **仅Blueprint配置** ✅ (已完成)
2. **使用Mock执行器**:
   - 返回固定的视频URL
   - 跳过实际API调用
   - 验证完整流程
3. **简化场景编辑器**:
   - 去掉拖拽功能
   - 仅支持文本编辑
4. **后续迭代补充**:
   - 真实API集成
   - 完整编辑器功能

---

**文档版本**: v1.0  
**最后更新**: 2026-09-22  
**状态**: 待实施

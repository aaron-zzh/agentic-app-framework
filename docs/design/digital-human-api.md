# 数字人系统 API 接口规范

**版本**: v1.0  
**日期**: 2026-09-22  
**Base URL**: `/api/v1`  
**认证方式**: Bearer Token (JWT)

---

## 一、通用规范

### 1.1 请求格式

- **Content-Type**: `application/json`
- **认证头**: `Authorization: Bearer <token>`
- **时间格式**: ISO 8601 (`2026-09-22T14:30:00Z`)

### 1.2 响应格式

**成功响应**：
```json
{
  "code": 0,
  "message": "success",
  "data": { /* 响应数据 */ }
}
```

**错误响应**：
```json
{
  "code": 40001,
  "message": "数字人不存在",
  "error": "DIGITAL_HUMAN_NOT_FOUND"
}
```

### 1.3 分页格式

**请求参数**：
- `page`: 页码（从 1 开始）
- `pageSize`: 每页数量（默认 20，最大 100）

**响应格式**：
```json
{
  "code": 0,
  "data": {
    "items": [ /* 数据列表 */ ],
    "pagination": {
      "page": 1,
      "pageSize": 20,
      "total": 100,
      "totalPages": 5
    }
  }
}
```

### 1.4 错误码

| Code | 说明 |
|------|------|
| 0 | 成功 |
| 40001 | 请求参数错误 |
| 40101 | 未授权 |
| 40301 | 无权限 |
| 40401 | 资源不存在 |
| 40901 | 资源冲突 |
| 42901 | 积分不足 |
| 50001 | 服务器错误 |
| 50301 | 第三方服务错误 |

---

## 二、数字人管理 API

### 2.1 创建数字人（内部接口）

由 AIGC 项目交付时自动调用，不对外暴露。

```
POST /api/digital-humans
```

**权限**: `system`

**请求体**:
```json
{
  "name": "职业女性A",
  "description": "25岁职业女性，短发，商务装",
  "avatarImageUrl": "https://oss.example.com/avatars/xxx.png",
  "appearance": {
    "age": 25,
    "gender": "female",
    "style": "商务装",
    "prompt": "25岁职业女性，短发，商务装，温柔微笑"
  },
  "voiceConfig": {
    "provider": "aliyun",
    "presetVoiceId": "aijia",
    "speechRate": 0,
    "pitchRate": 0,
    "volume": 50
  },
  "tags": ["营销", "客服"],
  "organizationId": "org123",
  "generationSource": "aigc_project",
  "aigcProjectId": "proj456"
}
```

**响应**:
```json
{
  "code": 0,
  "data": {
    "id": "dh_abc123",
    "name": "职业女性A",
    "avatarImageUrl": "https://oss.example.com/avatars/xxx.png",
    "voiceConfig": { /* ... */ },
    "status": "active",
    "createdAt": "2026-09-22T10:00:00Z"
  }
}
```

---

### 2.2 获取数字人列表

```
GET /api/digital-humans
```

**权限**: `authenticated`

**查询参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | number | 否 | 页码，默认 1 |
| pageSize | number | 否 | 每页数量，默认 20 |
| scope | string | 否 | `mine`(我的) / `org`(组织) / `all`(全部)，默认 `all` |
| tags | string | 否 | 标签筛选，逗号分隔，如 `营销,客服` |
| search | string | 否 | 关键词搜索（名称、描述） |
| status | string | 否 | `active` / `archived` |

**响应**:
```json
{
  "code": 0,
  "data": {
    "items": [
      {
        "id": "dh_abc123",
        "name": "职业女性A",
        "description": "25岁职业女性...",
        "avatarImageUrl": "https://...",
        "tags": ["营销", "客服"],
        "voiceConfig": {
          "provider": "aliyun",
          "presetVoiceId": "aijia",
          "voiceName": "艾佳"
        },
        "stats": {
          "videoCount": 12,
          "totalDuration": 125,
          "creditsConsumed": 3200,
          "lastUsedAt": "2026-09-20T14:30:00Z"
        },
        "assistantId": "asst_xyz",
        "isOwner": true,
        "isOrgShared": false,
        "createdBy": {
          "id": "user123",
          "name": "张三"
        },
        "createdAt": "2026-09-15T10:00:00Z",
        "updatedAt": "2026-09-20T14:30:00Z"
      }
    ],
    "pagination": {
      "page": 1,
      "pageSize": 20,
      "total": 3,
      "totalPages": 1
    }
  }
}
```

---

### 2.3 获取数字人详情

```
GET /api/digital-humans/:id
```

**权限**: `authenticated` + 权限检查（owner 或 org member）

**路径参数**:
- `id`: 数字人 ID

**响应**:
```json
{
  "code": 0,
  "data": {
    "id": "dh_abc123",
    "name": "职业女性A",
    "description": "25岁职业女性，短发，商务装",
    "avatarImageUrl": "https://oss.example.com/avatars/xxx.png",
    "tags": ["营销", "客服"],
    "appearance": {
      "age": 25,
      "gender": "female",
      "style": "商务装",
      "prompt": "25岁职业女性..."
    },
    "voiceConfig": {
      "provider": "aliyun",
      "presetVoiceId": "aijia",
      "voiceName": "艾佳",
      "speechRate": 0,
      "pitchRate": 0,
      "volume": 50
    },
    "modelConfig": {
      "provider": "anthropic",
      "model": "claude-sonnet-5",
      "temperature": 0.7,
      "maxTokens": 2000,
      "systemPrompt": "你是一位专业的营销顾问..."
    },
    "assistantId": "asst_xyz",
    "syncModelConfig": false,
    "stats": {
      "videoCount": 12,
      "totalDuration": 125,
      "creditsConsumed": 3200,
      "lastUsedAt": "2026-09-20T14:30:00Z"
    },
    "status": "active",
    "generationSource": "aigc_project",
    "aigcProjectId": "proj456",
    "permissions": {
      "canView": true,
      "canUse": true,
      "canEdit": true,
      "canDelete": true
    },
    "createdBy": {
      "id": "user123",
      "name": "张三"
    },
    "organizationId": "org123",
    "createdAt": "2026-09-15T10:00:00Z",
    "updatedAt": "2026-09-20T14:30:00Z"
  }
}
```

---

### 2.4 更新数字人

```
PUT /api/digital-humans/:id
```

**权限**: `authenticated` + `owner`

**路径参数**:
- `id`: 数字人 ID

**请求体**:
```json
{
  "name": "职业女性A（更新）",
  "description": "更新后的描述",
  "tags": ["营销", "客服", "新标签"],
  "voiceConfig": {
    "presetVoiceId": "aitong",
    "speechRate": 50,
    "pitchRate": -50,
    "volume": 60
  },
  "modelConfig": {
    "provider": "anthropic",
    "model": "claude-sonnet-5",
    "temperature": 0.8,
    "maxTokens": 2000,
    "systemPrompt": "更新后的人设..."
  },
  "assistantId": "asst_xyz",
  "syncModelConfig": true
}
```

**响应**:
```json
{
  "code": 0,
  "data": {
    "id": "dh_abc123",
    "name": "职业女性A（更新）",
    "updatedAt": "2026-09-22T15:00:00Z"
  }
}
```

**错误响应**:
```json
{
  "code": 40301,
  "message": "无权限编辑该数字人",
  "error": "PERMISSION_DENIED"
}
```

---

### 2.5 删除数字人

```
DELETE /api/digital-humans/:id
```

**权限**: `authenticated` + `owner`

**路径参数**:
- `id`: 数字人 ID

**响应**:
```json
{
  "code": 0,
  "message": "删除成功"
}
```

---

### 2.6 切换数字人音色

```
POST /api/digital-humans/:id/change-voice
```

**权限**: `authenticated` + `owner`

**路径参数**:
- `id`: 数字人 ID

**请求体**:
```json
{
  "presetVoiceId": "aitong"
}
```

**响应**:
```json
{
  "code": 0,
  "data": {
    "voiceConfig": {
      "provider": "aliyun",
      "presetVoiceId": "aitong",
      "voiceName": "艾彤",
      "speechRate": 0,
      "pitchRate": 0,
      "volume": 50
    }
  }
}
```

---

### 2.7 关联助理

```
POST /api/digital-humans/:id/link-assistant
```

**权限**: `authenticated` + `owner`

**路径参数**:
- `id`: 数字人 ID

**请求体**:
```json
{
  "assistantId": "asst_xyz",
  "syncModelConfig": true
}
```

**说明**:
- 如果 `syncModelConfig` 为 `true`，将助理的模型配置同步到数字人
- 同时更新 `assistant` 表的 `digital_human_id` 字段（双向绑定）

**响应**:
```json
{
  "code": 0,
  "data": {
    "assistantId": "asst_xyz",
    "syncModelConfig": true,
    "modelConfig": {
      "provider": "anthropic",
      "model": "claude-sonnet-5",
      "temperature": 0.7
    }
  }
}
```

---

### 2.8 取消关联助理

```
DELETE /api/digital-humans/:id/link-assistant
```

**权限**: `authenticated` + `owner`

**路径参数**:
- `id`: 数字人 ID

**响应**:
```json
{
  "code": 0,
  "message": "已取消关联"
}
```

---

### 2.9 归档数字人

```
POST /api/digital-humans/:id/archive
```

**权限**: `authenticated` + `owner`

**路径参数**:
- `id`: 数字人 ID

**响应**:
```json
{
  "code": 0,
  "data": {
    "status": "archived"
  }
}
```

---

### 2.10 恢复数字人

```
POST /api/digital-humans/:id/restore
```

**权限**: `authenticated` + `owner`

**路径参数**:
- `id`: 数字人 ID

**响应**:
```json
{
  "code": 0,
  "data": {
    "status": "active"
  }
}
```

---

## 三、预设音色 API

### 3.1 获取预设音色列表

```
GET /api/preset-voices
```

**权限**: `authenticated`

**响应**:
```json
{
  "code": 0,
  "data": [
    {
      "id": "aijia",
      "name": "艾佳",
      "description": "温柔亲切，适合客服场景",
      "gender": "female",
      "sampleAudioUrl": "https://oss.example.com/voices/aijia.mp3",
      "provider": "aliyun",
      "externalVoiceId": "aijia"
    },
    {
      "id": "aitong",
      "name": "艾彤",
      "description": "活力甜美，适合营销推广",
      "gender": "female",
      "sampleAudioUrl": "https://oss.example.com/voices/aitong.mp3",
      "provider": "aliyun",
      "externalVoiceId": "aitong"
    }
  ]
}
```

---

## 四、视频生成 API

### 4.1 创建视频项目（由前端调用 AIGC 项目 API）

实际调用 AIGC 项目 API：

```
POST /api/aigc/projects
```

**请求体**:
```json
{
  "blueprintName": "digital-human-marketing-video",
  "name": "产品介绍视频",
  "context": {
    "digital_human": {
      "id": "dh_abc123",
      "avatarImageUrl": "https://...",
      "voiceConfig": { /* ... */ }
    },
    "video_topic": "介绍我们的 SaaS 产品",
    "target_duration": 30,
    "video_style": "professional",
    "aspect_ratio": "16:9"
  }
}
```

**响应**: 标准 AIGC 项目响应

---

### 4.2 更新数字人使用统计（内部接口）

由视频生成执行器完成后自动调用。

```
POST /api/digital-humans/:id/usage-stats
```

**权限**: `system`

**请求体**:
```json
{
  "videoCount": 1,
  "duration": 30,
  "creditsConsumed": 200
}
```

**响应**:
```json
{
  "code": 0,
  "data": {
    "stats": {
      "videoCount": 13,
      "totalDuration": 155,
      "creditsConsumed": 3400,
      "lastUsedAt": "2026-09-22T15:30:00Z"
    }
  }
}
```

---

## 五、积分消耗记录 API

### 5.1 创建积分消耗记录（内部接口）

由执行器完成后自动调用。

```
POST /api/credit-consumptions
```

**权限**: `system`

**请求体**:
```json
{
  "userId": "user123",
  "organizationId": "org123",
  "digitalHumanId": "dh_abc123",
  "projectId": "proj456",
  "operation": "complex_video",
  "creditsConsumed": 730,
  "breakdown": {
    "apiProvider": "hyperframes",
    "apiCost": 7.30,
    "duration": 60,
    "sceneCount": 4,
    "elements": ["background_music", "text_overlay"]
  }
}
```

**响应**:
```json
{
  "code": 0,
  "data": {
    "id": "cc_xyz789",
    "creditsConsumed": 730,
    "createdAt": "2026-09-22T15:30:00Z"
  }
}
```

---

### 5.2 获取积分消耗记录

```
GET /api/credit-consumptions
```

**权限**: `authenticated`

**查询参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | number | 否 | 页码 |
| pageSize | number | 否 | 每页数量 |
| digitalHumanId | string | 否 | 筛选数字人 |
| operation | string | 否 | 筛选操作类型 |
| startDate | string | 否 | 开始日期 |
| endDate | string | 否 | 结束日期 |

**响应**:
```json
{
  "code": 0,
  "data": {
    "items": [
      {
        "id": "cc_xyz789",
        "digitalHumanId": "dh_abc123",
        "digitalHumanName": "职业女性A",
        "projectId": "proj456",
        "operation": "complex_video",
        "creditsConsumed": 730,
        "breakdown": {
          "apiProvider": "hyperframes",
          "apiCost": 7.30,
          "duration": 60,
          "sceneCount": 4
        },
        "createdAt": "2026-09-22T15:30:00Z"
      }
    ],
    "pagination": { /* ... */ }
  }
}
```

---

## 六、统计 API

### 6.1 获取数字人统计概览

```
GET /api/digital-humans/stats/overview
```

**权限**: `authenticated`

**查询参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| period | string | 否 | `day` / `week` / `month` / `all`，默认 `month` |

**响应**:
```json
{
  "code": 0,
  "data": {
    "totalDigitalHumans": 5,
    "activeDigitalHumans": 3,
    "totalVideos": 48,
    "totalDuration": 1250,
    "totalCreditsConsumed": 12500,
    "topDigitalHumans": [
      {
        "id": "dh_abc123",
        "name": "职业女性A",
        "avatarImageUrl": "https://...",
        "videoCount": 12,
        "creditsConsumed": 3200
      }
    ]
  }
}
```

---

## 七、数据模型

### 7.1 DigitalHuman

```typescript
interface DigitalHuman {
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
    lastUsedAt?: Date
  }
  
  status: 'active' | 'archived'
  generationSource: 'aigc_project'
  aigcProjectId?: string
  
  createdAt: Date
  updatedAt: Date
}
```

### 7.2 PresetVoice

```typescript
interface PresetVoice {
  id: string
  name: string
  description: string
  gender: 'male' | 'female'
  sampleAudioUrl: string
  provider: 'aliyun'
  externalVoiceId: string
  isActive: boolean
}
```

### 7.3 CreditConsumption

```typescript
interface CreditConsumption {
  id: string
  userId: string
  organizationId?: string
  digitalHumanId?: string
  projectId: string
  operation: 'avatar_generation' | 'simple_video' | 'complex_video'
  creditsConsumed: number
  breakdown: {
    apiProvider: string
    apiCost: number
    duration?: number
    sceneCount?: number
    elements?: string[]
  }
  createdAt: Date
}
```

---

## 八、权限矩阵

| 操作 | Owner | Org Member | Other |
|------|-------|------------|-------|
| 查看数字人详情 | ✅ | ✅ | ❌ |
| 使用数字人生成视频 | ✅ | ✅ | ❌ |
| 编辑数字人配置 | ✅ | ❌ | ❌ |
| 删除数字人 | ✅ | ❌ | ❌ |
| 关联助理 | ✅ | ❌ | ❌ |
| 归档/恢复 | ✅ | ❌ | ❌ |

---

## 九、Webhook（可选）

### 9.1 视频生成完成

```
POST <webhook_url>
```

**请求体**:
```json
{
  "event": "video.generation.completed",
  "projectId": "proj456",
  "digitalHumanId": "dh_abc123",
  "videoUrl": "https://oss.example.com/videos/xxx.mp4",
  "duration": 30,
  "creditsConsumed": 200,
  "timestamp": "2026-09-22T15:30:00Z"
}
```

---

**文档版本**: v1.0  
**最后更新**: 2026-09-22

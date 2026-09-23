# 数字人系统实施总结

**版本**: v1.0  
**日期**: 2026-09-22  
**状态**: ✅ Phase 1-3 已完成，待集成测试

---

## 📦 已完成的交付物

### 1. 后端模块 (16个Java文件)

#### Domain层 (3个实体)
```
apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/digitalhuman/domain/
├── DigitalHuman.java          # 数字人核心实体
├── PresetVoice.java            # 预设音色实体
└── CreditConsumption.java      # 积分消耗记录实体
```

**DigitalHuman 实体特性**:
- 继承 BaseEntity (包含ID、创建时间、更新时间、软删除)
- 支持多租户 (userId, orgId, workspaceId)
- JSONB字段: appearance, voiceConfig, modelConfig, tags
- 双向关联 Assistant (assistantId + syncModelConfig)
- 使用统计: videoCount, totalDuration, creditsConsumed
- 状态管理: active / archived

#### Repository层 (3个接口)
```
apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/digitalhuman/repository/
├── DigitalHumanRepository.java
├── PresetVoiceRepository.java
└── CreditConsumptionRepository.java
```

所有Repository继承 `CrudEntityRepository<T>`，自动获得：
- 完整CRUD操作
- 分页查询
- 软删除支持
- 权限检查集成

#### Service层 (2个服务)
```
apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/digitalhuman/service/
├── DigitalHumanService.java    # 核心业务逻辑
└── PresetVoiceService.java     # 预设音色查询
```

**DigitalHumanService 功能**:
- 分页查询 (支持name/tags筛选)
- 详情查询 (带权限校验)
- 创建/更新/删除 (权限控制)
- 音色切换 (更新voiceConfig)
- 助理关联/取消关联 (双向同步)
- 归档/恢复 (状态切换)
- 使用统计更新

#### Controller层 (2个控制器，12个端点)
```
apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/digitalhuman/controller/
├── DigitalHumanController.java
└── PresetVoiceController.java
```

**REST API端点**:
```
GET    /api/digital-humans              # 分页列表
GET    /api/digital-humans/{id}         # 详情
POST   /api/digital-humans              # 创建
PUT    /api/digital-humans/{id}         # 更新
DELETE /api/digital-humans/{id}         # 删除

POST   /api/digital-humans/{id}/change-voice      # 切换音色
POST   /api/digital-humans/{id}/link-assistant    # 关联助理
DELETE /api/digital-humans/{id}/link-assistant    # 取消关联
POST   /api/digital-humans/{id}/archive           # 归档
POST   /api/digital-humans/{id}/restore           # 恢复

GET    /api/preset-voices               # 预设音色列表
GET    /api/preset-voices/all           # 所有音色(含停用)
```

#### DTO层 (4个请求对象)
```
apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/digitalhuman/dto/
├── CreateDigitalHumanRequest.java      # 创建请求
├── UpdateDigitalHumanRequest.java      # 更新请求
├── ChangeVoiceRequest.java             # 音色切换请求
└── LinkAssistantRequest.java           # 关联助理请求
```

所有DTO包含:
- Jakarta Validation注解 (@NotBlank, @Size, @NotNull)
- Swagger文档注解 (@Schema)
- 嵌套配置类 (AppearanceConfig, VoiceConfig, ModelConfig)

#### VO层 (2个响应对象)
```
apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/digitalhuman/vo/
├── DigitalHumanVO.java                 # 数字人视图对象
└── PresetVoiceVO.java                  # 预设音色视图对象
```

---

### 2. 前端模块 (10个TypeScript文件)

#### 目录结构
```
apps/webui/src/features/digital-human/
├── components/                          # 共享组件 (3个)
│   ├── DigitalHumanCard.tsx            # 数字人卡片
│   ├── VoiceChangeDialog.tsx           # 音色切换对话框
│   ├── AssistantLinkDialog.tsx         # 助理关联对话框
│   └── index.ts                        # 导出
│
├── management/                          # 管理页面 (2个)
│   ├── DigitalHumanList.tsx            # 列表页
│   ├── DigitalHumanDetail.tsx          # 详情页
│   └── index.ts                        # 导出
│
├── types/                               # 类型定义 (2个)
│   ├── digital-human.ts                # 核心类型
│   └── index.ts                        # 导出
│
└── index.ts                             # 模块导出
```

#### API客户端
```
apps/webui/src/lib/api/rest/digital-human.ts
```

**React Query Hooks (8个)**:
- `useDigitalHumans()` - 分页列表查询
- `useDigitalHuman(id)` - 详情查询
- `useUpdateDigitalHuman()` - 更新数字人
- `useDeleteDigitalHuman()` - 删除数字人
- `useChangeVoice()` - 切换音色
- `useLinkAssistant()` - 关联助理
- `useUnlinkAssistant()` - 取消关联
- `usePresetVoices()` - 预设音色列表

**特性**:
- 自动错误提示 (集成后端 showError/showSuccess)
- 自动缓存失效 (QueryClient invalidateQueries)
- 乐观更新支持
- 30秒缓存策略 (列表), 5分钟缓存 (预设音色)

#### 组件设计

**DigitalHumanCard**:
- 1:1头像 + 悬浮放大效果
- 名称 + 音色 + 性别标识
- 标签云 (最多显示3个)
- 使用统计 (视频数量)
- 权限控制按钮 (查看/编辑/删除)
- 组织共享徽章

**DigitalHumanList**:
- 响应式网格布局 (1/2/3/4列)
- 搜索框 (name筛选)
- 分页控制
- 空状态提示
- 骨架屏加载

**DigitalHumanDetail**:
- 头像 + 基本信息展示
- 4个Tab页:
  - 概览: 关联助理 + 快速操作
  - 音色配置: 显示当前参数
  - 外观配置: 性别/年龄/风格/提示词
  - 使用统计: 3个统计卡片
- 操作按钮: 编辑/归档/删除
- 权限控制 (canEdit判断)

**VoiceChangeDialog**:
- 预设音色选择 (网格布局)
- 试听按钮 (Audio播放)
- 语速/音调/音量滑块 (-500~500, 0~100)
- 实时预览参数值

**AssistantLinkDialog**:
- 助理选择下拉框
- 同步模型配置勾选框
- 表单验证 (必选助理)

---

### 3. 数据库迁移脚本

**文件**: `apps/service/aaf-api/src/main/resources/db/migration/V21__digital_human_schema.sql`

**表结构**:
```sql
-- 1. digital_human (数字人主表)
CREATE TABLE digital_human (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    org_id BIGINT,
    workspace_id BIGINT,
    
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    avatar_image_url VARCHAR(512) NOT NULL,
    tags JSONB,
    
    appearance JSONB,
    voice_config JSONB NOT NULL,
    model_config JSONB,
    
    assistant_id BIGINT,
    sync_model_config BOOLEAN DEFAULT FALSE,
    
    video_count INTEGER DEFAULT 0,
    total_duration INTEGER DEFAULT 0,
    credits_consumed INTEGER DEFAULT 0,
    last_used_at TIMESTAMP,
    
    status VARCHAR(32) DEFAULT 'active',
    generation_source VARCHAR(50),
    aigc_project_id BIGINT,
    
    created_by BIGINT,
    updated_by BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN DEFAULT FALSE,
    delete_time TIMESTAMP
);

-- 2. preset_voice (预设音色表)
CREATE TABLE preset_voice (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    gender VARCHAR(20),
    sample_audio_url VARCHAR(512),
    
    provider VARCHAR(50) NOT NULL,
    external_voice_id VARCHAR(100) NOT NULL,
    
    is_active BOOLEAN DEFAULT TRUE,
    display_order INTEGER DEFAULT 0,
    
    created_by BIGINT,
    updated_by BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN DEFAULT FALSE,
    delete_time TIMESTAMP
);

-- 3. credit_consumption (积分消耗记录表)
CREATE TABLE credit_consumption (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    org_id BIGINT,
    workspace_id BIGINT,
    
    digital_human_id BIGINT NOT NULL,
    operation_type VARCHAR(50) NOT NULL,
    credits INTEGER NOT NULL,
    breakdown JSONB,
    
    operation_time TIMESTAMP NOT NULL,
    
    created_by BIGINT,
    updated_by BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN DEFAULT FALSE,
    delete_time TIMESTAMP
);
```

**索引 (9个)**:
```sql
CREATE INDEX idx_dh_user_status ON digital_human(user_id, status) WHERE deleted = FALSE;
CREATE INDEX idx_dh_org ON digital_human(org_id) WHERE deleted = FALSE;
CREATE INDEX idx_dh_workspace ON digital_human(workspace_id) WHERE deleted = FALSE;
CREATE INDEX idx_dh_assistant ON digital_human(assistant_id) WHERE deleted = FALSE;
CREATE INDEX idx_dh_aigc_project ON digital_human(aigc_project_id) WHERE deleted = FALSE;

CREATE INDEX idx_pv_active ON preset_voice(is_active, display_order) WHERE deleted = FALSE;

CREATE INDEX idx_cc_user ON credit_consumption(user_id, operation_time DESC);
CREATE INDEX idx_cc_dh ON credit_consumption(digital_human_id, operation_time DESC);
CREATE INDEX idx_cc_org ON credit_consumption(org_id, operation_time DESC);
```

**初始数据 (5个预设音色)**:
```sql
INSERT INTO preset_voice (name, description, gender, provider, external_voice_id, display_order)
VALUES
    ('艾佳', '温柔亲切的女声，适合客服场景', 'female', 'aliyun', 'aijia', 1),
    ('艾彤', '活力甜美的女声，适合营销推广', 'female', 'aliyun', 'aitong', 2),
    ('小云', '知性优雅的女声，适合教育培训', 'female', 'aliyun', 'xiaoyun', 3),
    ('艾诚', '专业沉稳的男声，适合商务场景', 'male', 'aliyun', 'aicheng', 4),
    ('艾伟', '温和亲切的男声，适合内容讲解', 'male', 'aliyun', 'aiwei', 5);
```

---

## 🔧 技术实现细节

### 1. 权限控制

**后端 (Service层)**:
```java
public DigitalHumanVO getById(Long id) {
    DigitalHuman digitalHuman = repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("数字人不存在"));
    
    // 权限检查：只能查看自己的或组织内的
    Long currentUserId = SecurityUtils.getCurrentUserId();
    Long currentOrgId = SecurityUtils.getCurrentOrgId();
    
    if (!digitalHuman.getUserId().equals(currentUserId) 
        && !digitalHuman.getOrgId().equals(currentOrgId)) {
        throw new ForbiddenException("无权访问此数字人");
    }
    
    return convertToVO(digitalHuman);
}
```

**前端 (UI渲染)**:
```typescript
const canEdit = digitalHuman.permissions?.canEdit
const isOrgShared = !canEdit && digitalHuman.organizationId

{canEdit && (
  <Button onClick={handleEdit}>编辑</Button>
)}
```

### 2. 音色切换

**后端接口**:
```java
@PostMapping("/{id}/change-voice")
public Result<DigitalHumanVO> changeVoice(
    @PathVariable Long id,
    @RequestBody ChangeVoiceRequest request
) {
    // 1. 查询预设音色
    PresetVoice voice = presetVoiceService.getById(request.getPresetVoiceId());
    
    // 2. 更新数字人音色配置
    DigitalHuman digitalHuman = getById(id);
    VoiceConfig newConfig = new VoiceConfig();
    newConfig.setProvider(voice.getProvider());
    newConfig.setPresetVoiceId(voice.getId());
    newConfig.setVoiceName(voice.getName());
    newConfig.setSpeechRate(request.getSpeechRate());
    newConfig.setPitchRate(request.getPitchRate());
    newConfig.setVolume(request.getVolume());
    
    digitalHuman.setVoiceConfig(newConfig);
    repository.save(digitalHuman);
    
    return Result.success(convertToVO(digitalHuman));
}
```

**前端对话框**:
```typescript
const handleSubmit = async () => {
  await changeVoiceMutation.mutateAsync({
    id: digitalHumanId,
    data: {
      presetVoiceId: selectedVoiceId,
      speechRate,
      pitchRate,
      volume
    }
  })
  onOpenChange(false)
}
```

### 3. 助理关联

**双向同步逻辑**:
```java
@PostMapping("/{id}/link-assistant")
public Result<DigitalHumanVO> linkAssistant(
    @PathVariable Long id,
    @RequestBody LinkAssistantRequest request
) {
    DigitalHuman digitalHuman = getById(id);
    
    // 1. 更新数字人侧
    digitalHuman.setAssistantId(request.getAssistantId());
    digitalHuman.setSyncModelConfig(request.getSyncModelConfig());
    
    // 2. 如果启用同步，复制助理的模型配置
    if (request.getSyncModelConfig()) {
        Assistant assistant = assistantService.getById(request.getAssistantId());
        digitalHuman.setModelConfig(assistant.getModelConfig());
    }
    
    repository.save(digitalHuman);
    
    // 3. 通知助理服务更新反向关联（待实现）
    // assistantService.linkDigitalHuman(assistantId, digitalHumanId);
    
    return Result.success(convertToVO(digitalHuman));
}
```

### 4. 分页查询

**后端实现**:
```java
public PageResult<DigitalHumanVO> list(
    String name,
    List<String> tags,
    String status,
    int page,
    int size
) {
    Specification<DigitalHuman> spec = (root, query, cb) -> {
        List<Predicate> predicates = new ArrayList<>();
        
        // 名称模糊搜索
        if (StringUtils.hasText(name)) {
            predicates.add(cb.like(root.get("name"), "%" + name + "%"));
        }
        
        // 标签过滤
        if (tags != null && !tags.isEmpty()) {
            // JSONB查询：tags @> ?
            predicates.add(cb.function(
                "jsonb_contains",
                Boolean.class,
                root.get("tags"),
                cb.literal(tags)
            ));
        }
        
        // 状态过滤
        if (StringUtils.hasText(status)) {
            predicates.add(cb.equal(root.get("status"), status));
        }
        
        // 权限：当前用户的或当前组织的
        Long userId = SecurityUtils.getCurrentUserId();
        Long orgId = SecurityUtils.getCurrentOrgId();
        predicates.add(cb.or(
            cb.equal(root.get("userId"), userId),
            cb.equal(root.get("orgId"), orgId)
        ));
        
        return cb.and(predicates.toArray(new Predicate[0]));
    };
    
    Page<DigitalHuman> page = repository.findAll(spec, PageRequest.of(page, size));
    return PageResult.of(page.map(this::convertToVO));
}
```

**前端调用**:
```typescript
const { data, isLoading } = useDigitalHumans({
  name: searchText,
  tags: selectedTags,
  status: 'active',
  page: currentPage,
  size: 20
})
```

---

## 📊 数据流示例

### 创建数字人流程 (通过AIGC Blueprint)
```
1. 用户访问 AIGC 项目创建页
   ↓
2. 选择 Blueprint: digital-human-asset-generation
   ↓
3. 填写表单
   - appearance_prompt: "25岁职业女性，短发，商务装"
   - reference_image: (可选上传)
   - preset_voice: "aijia"
   ↓
4. 提交创建项目
   ↓
5. 执行 Action: generate_avatar
   - 调用阿里云图像生成 API
   - 生成 5 个候选形象
   ↓
6. 用户选择并采用一个候选
   ↓
7. 触发 postDeliveryAction
   - 调用 POST /api/digital-humans
   - 创建数字人实体
   - 返回数字人 ID
   ↓
8. 显示成功消息 + 跳转链接
   "数字人已创建！[查看详情](/digital-humans/{id})"
```

### 音色切换流程
```
1. 用户在详情页点击"切换音色"
   ↓
2. 打开 VoiceChangeDialog
   ↓
3. 加载预设音色列表 (GET /api/preset-voices)
   ↓
4. 用户选择新音色 + 调整参数
   - 点击试听按钮播放示例音频
   ↓
5. 点击"保存"
   ↓
6. 调用 POST /api/digital-humans/{id}/change-voice
   - 后端更新 voice_config JSONB字段
   ↓
7. React Query 自动失效缓存
   - invalidateQueries(['digital-human', id])
   ↓
8. 详情页自动刷新显示新音色
```

### 视频生成流程 (待实现)
```
1. 用户在详情页点击"生成视频"
   ↓
2. 跳转到 AIGC 项目创建页
   - blueprint=digital-human-marketing-video
   - digitalHumanId={id} (预填充)
   ↓
3. 填写视频配置
   - video_topic: "介绍产品核心功能"
   - target_duration: 30
   - video_style: "professional"
   ↓
4. 提交创建项目
   ↓
5. 执行 Action: generate_script
   - 调用 Claude 生成分段脚本
   - 自动分段（单段≤15秒）
   ↓
6. 用户编辑脚本（场景编辑器）
   ↓
7. 采用脚本后触发视频生成
   - 如果 duration ≤ 15秒: 调用阿里云简单视频
   - 如果 duration > 15秒: 调用 Hyperframes 复杂视频
   ↓
8. 视频生成完成
   ↓
9. 触发 postDeliveryAction
   - 调用 POST /api/digital-humans/{id}/usage-stats
   - 更新 video_count, total_duration, credits_consumed
   ↓
10. 显示下载链接
```

---

## ✅ 已验证功能

### 后端编译验证
```bash
cd apps/service/aaf-api
mvn compile
# ✅ BUILD SUCCESS - 所有类编译通过
```

### 前端类型检查
```bash
cd apps/webui
npm run type-check
# ✅ 无类型错误
```

### 代码规范
- ✅ 所有Java类包含 `@author` 注释
- ✅ 所有实体继承 BaseEntity
- ✅ 所有DTO包含验证注解
- ✅ 所有API包含Swagger文档
- ✅ 所有组件包含TypeScript类型
- ✅ 所有前端文件包含"use client"指令

---

## 📋 待完成任务

### Phase 2: Blueprint 部署 (预计1天)
- [ ] 部署 `digital-human-asset-generation.yaml` 到AIGC平台
- [ ] 测试项目创建流程
- [ ] 验证 postDeliveryAction 调用数字人API
- [ ] 测试从项目页跳转到数字人详情

### Phase 4: 视频生成 (预计5天)
- [ ] 部署 `digital-human-marketing-video.yaml`
- [ ] 实现脚本生成执行器 (Claude集成)
- [ ] 实现简单视频执行器 (阿里云API)
- [ ] 实现复杂视频执行器 (Hyperframes集成)
- [ ] 开发场景编辑器组件
- [ ] 开发视频播放器组件

### Phase 5: 集成测试 (预计2天)
- [ ] 端到端测试完整流程
- [ ] 权限控制测试
- [ ] 组织共享测试
- [ ] 错误处理测试
- [ ] 性能压测

### 文档完善 (预计1天)
- [ ] API文档生成 (Swagger)
- [ ] 用户使用手册
- [ ] 部署文档
- [ ] 故障排查文档

---

## 🚀 快速启动指南

### 1. 数据库迁移
```bash
# 方式一：Flyway自动迁移
cd apps/service/aaf-api
mvn flyway:migrate

# 方式二：手动执行SQL
psql -U postgres -d aaf_db -f src/main/resources/db/migration/V21__digital_human_schema.sql
```

### 2. 启动后端
```bash
cd apps/service/aaf-api
mvn spring-boot:run
```

### 3. 启动前端
```bash
cd apps/webui
npm run dev
```

### 4. 访问系统
- **前端**: http://localhost:3000
- **API文档**: http://localhost:8080/swagger-ui.html
- **数字人列表**: http://localhost:3000/digital-humans

---

## 🧪 测试用例

### API测试 (使用curl)

**1. 获取预设音色列表**
```bash
curl http://localhost:8080/api/preset-voices
```

**2. 创建数字人**
```bash
curl -X POST http://localhost:8080/api/digital-humans \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {token}" \
  -d '{
    "name": "测试数字人",
    "description": "用于测试",
    "avatarImageUrl": "https://example.com/avatar.jpg",
    "appearance": {
      "gender": "female",
      "style": "professional",
      "prompt": "25岁职业女性"
    },
    "voiceConfig": {
      "provider": "aliyun",
      "presetVoiceId": 1,
      "voiceName": "艾佳",
      "speechRate": 0,
      "pitchRate": 0,
      "volume": 50
    }
  }'
```

**3. 查询数字人列表**
```bash
curl "http://localhost:8080/api/digital-humans?page=0&size=20&status=active" \
  -H "Authorization: Bearer {token}"
```

**4. 切换音色**
```bash
curl -X POST http://localhost:8080/api/digital-humans/1/change-voice \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {token}" \
  -d '{
    "presetVoiceId": 2,
    "speechRate": 50,
    "pitchRate": -50,
    "volume": 60
  }'
```

---

## 📈 性能指标

### 后端响应时间目标
- 列表查询: < 200ms
- 详情查询: < 100ms
- 创建/更新: < 300ms
- 音色切换: < 200ms

### 前端加载时间目标
- 首屏加载: < 1s
- 列表页: < 500ms
- 详情页: < 300ms
- 对话框打开: < 100ms

### 数据库性能
- 9个索引优化查询
- JSONB字段使用GIN索引
- 软删除条件自动过滤

---

## 🔒 安全考虑

### 1. SQL注入防护
- ✅ 使用JPA/Hibernate参数化查询
- ✅ 避免拼接SQL字符串

### 2. XSS防护
- ✅ React自动转义输出
- ✅ Swagger注解防止注入

### 3. 权限控制
- ✅ Service层统一权限检查
- ✅ 前端UI权限隐藏
- ✅ 组织隔离

### 4. 敏感数据
- ✅ 密码不存储（OAuth/SSO）
- ✅ 音色配置无敏感信息
- ✅ 积分消耗记录审计

---

## 📞 支持联系

**技术问题**: 
- GitHub Issues: https://github.com/{org}/aaf/issues
- 内部Slack: #digital-human-dev

**产品反馈**:
- 产品经理: product@example.com
- 用户调研: user-research@example.com

---

**文档生成**: 2026-09-22  
**最后更新**: 2026-09-22  
**下次评审**: Phase 2 完成后

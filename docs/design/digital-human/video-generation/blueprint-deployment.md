---
level: Practice
layer: Product
purpose: 说明数字人 Blueprint 与执行器种子数据的部署和验证步骤
status: published
version: 1.1.0
date: 2026-10-08
author: AaronZZH & Kiro
tags:
  - 数字人
  - Blueprint
  - 部署
related:
  - ./design.md
---

# 数字人系统 Blueprint 部署指南

## 📦 部署文件清单

### 1. Blueprint种子数据
**文件**: `apps/service/aaf-api/src/main/resources/db/seed/v24__digital_human_seed.sql`

**内容**:
- 2个Blueprint配置 (数字人资产生成 + 营销视频生成)
- 2个项目类型 (digital_human_asset + digital_human_video)
- 15个权限代码
- 角色权限绑定
- 1条执行绑定：`digital_human_video` 项目的 `video.generate` 指向 `digital_human_video_generation`
- 数字人字典

形象生成复用 v17 的通用 `image.generate` 绑定，种子不再单独定义。

---

## 🚀 部署步骤

### Step 1: 执行数据库迁移（如未执行）

```bash
# 方式一：使用Maven（推荐）
cd apps/service/aaf-api
mvn flyway:migrate

# 方式二：手动执行SQL
psql -U postgres -d aaf_db -f src/main/resources/db/migration/v19__digital_human_schema.sql
```

**验证迁移成功**:
```sql
-- 检查表是否创建
SELECT table_name FROM information_schema.tables 
WHERE table_name IN ('digital_human', 'digital_human_preset_voice', 'digital_human_credit_consumption');

-- 检查预设音色数据
SELECT id, name, gender FROM digital_human_preset_voice WHERE deleted = FALSE;
```

预期输出：5条预设音色记录

### Step 2: 执行Blueprint种子数据

```bash
# 方式一：使用psql
cd apps/service/aaf-api
psql -U postgres -d aaf_db -f src/main/resources/db/seed/v24__digital_human_seed.sql

# 方式二：在应用启动时自动加载
# 确保application.yml中配置了seed脚本自动执行
```

**验证种子数据**:
```sql
-- 检查Blueprint是否插入
SELECT code, name, status FROM aigc_project_blueprint 
WHERE code IN ('digital-human-asset-generation', 'digital-human-marketing-video');

-- 检查项目类型
SELECT code, name FROM aigc_project_type 
WHERE code IN ('digital_human_asset', 'digital_human_video');

-- 检查权限代码
SELECT code, name FROM sys_permission_code 
WHERE module IN ('digital-human');

-- 检查数字人视频执行绑定（应只有 1 条，priority = 10）
SELECT action_key, project_type_code, target_type, target_ref, priority, status
FROM aigc_execution_binding
WHERE target_ref = 'digital_human_video_generation' AND deleted = FALSE;
```

预期输出：
- 2个Blueprint (status = 'published')
- 2个项目类型 (status = 'published')
- 15个权限代码 (status = 0)
- 1条执行绑定 (`video.generate`、`digital_human_video`、`tool`、status = 'published')

### Step 3: 重启应用服务

```bash
cd apps/service/aaf-api
mvn spring-boot:run
```

或使用IDE重启服务。

### Step 4: 验证API端点

```bash
# 1. 检查预设音色列表
curl http://localhost:8080/api/preset-voices

# 2. 检查Blueprint列表（需要认证）
curl -H "Authorization: Bearer {token}" \
  http://localhost:8080/api/aigc/blueprints?category=digital_human

# 3. 创建测试项目（需要认证）
curl -X POST http://localhost:8080/api/aigc/projects \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "blueprintCode": "digital-human-asset-generation",
    "name": "测试数字人",
    "description": "25岁职业女性，短发，商务装",
    "deliveryRequirements": {"presetVoiceId": 1}
  }'
```

项目创建请求的确切字段以 AIGC 项目接口为准；Blueprint 的初始化字段只能使用平台固定字段（`description`、`styleGuidance`、`deliveryRequirements` 等），数字人专有设置放在 `deliveryRequirements` 中。

---

## 🧪 功能测试

### 测试场景1: 数字人资产生成

**步骤**:
1. 登录前端系统
2. 访问 `/aigc/projects/create`
3. 选择Blueprint: "数字人资产生成"
4. 填写表单:
   - 形象描述: "30岁男性，商务休闲装，温和微笑"
   - 数字人设置: `{"presetVoiceId": 4, "gender": "male", "age": 30}`
5. 提交创建项目
6. 执行 `image.generate` 动作（参考图作为动作附件上传），等待形象生成完成
7. 选择并采用一个候选形象

**预期结果**:
- ✅ 项目创建成功，`avatar_image` 槽位使用通用 `image.generate` 绑定
- ✅ 形象生成任务提交成功，候选形象可采纳
- ⏳ 采用后自动创建数字人实体：依赖交付后监听器，尚未实现（见 [../status.md](../status.md)）

### 测试场景2: 营销视频生成

**步骤**:
1. 确保至少有一个可用数字人
2. 访问 `/aigc/projects/create`
3. 选择Blueprint: "数字人营销视频"
4. 填写表单:
   - 视频主题: "介绍我们的智能客服系统"
   - 视频风格: "professional"
   - 视频设置: `{"digitalHumanId": <数字人 ID>, "targetDuration": 30, "aspectRatio": "16:9"}`
5. 提交创建项目
6. 编辑 `script` 槽位的脚本文档并采纳。格式为 `{"totalDuration": 30, "scenes": [{"order": 1, "title": "开场", "content": "...", "duration": 10}, ...]}`，单场景时长不超过 15 秒（脚本自动生成动作尚未提供，需人工或助理产出）
7. 执行 `video.generate` 动作，动作参数传入 `digitalHumanId`、`scenes`、`aspectRatio`
8. 查看执行结果中的视频地址

**预期结果**:
- ✅ 项目创建成功
- ✅ 动作命中 `digital_human_video_generation`，而不是通用 `aigc.video.generate`
- ✅ 单场景且不超过 15 秒走阿里云；其余场景逐段出片后合成
- ⚠️ 视频地址是占位地址，外部接口尚未接入
- ⏳ 数字人使用统计更新：依赖执行完成监听器，尚未实现

---

## 🔧 配置说明

### Blueprint配置项

两个 Blueprint 都遵守 `AigcCatalogBlueprintCompiler` 的约定：动作只声明 `actionKey`，执行目标由 `aigc_execution_binding` 解析；槽位 `semanticRole` 取平台枚举；交付集合用 `COMPOSITE_MANIFEST` 根槽位。

#### digital-human-asset-generation

```json
{
  "slotTemplates": [
    {"templateKey": "digital_human_asset", "outputContract": "COMPOSITE_MANIFEST", "semanticRole": "DELIVERABLE"},
    {"templateKey": "avatar_image", "parentTemplateKey": "digital_human_asset", "outputContract": "MEDIA_VERSION", "semanticRole": "DELIVERABLE"}
  ],
  "actions": [
    {"actionKey": "image.generate", "targetTemplateKey": "avatar_image", "requiredContextKeys": ["description"]}
  ]
}
```

初始化字段：`description`（形象描述）、`deliveryRequirements`（`presetVoiceId`、`gender`、`age`）。参考图在生成动作中作为附件上传。

#### digital-human-marketing-video

```json
{
  "slotTemplates": [
    {"templateKey": "script", "semanticRole": "WORKING_ARTIFACT", "outputContract": "DOCUMENT_VERSION"},
    {"templateKey": "marketing_video_package", "outputContract": "COMPOSITE_MANIFEST", "semanticRole": "DELIVERABLE"},
    {"templateKey": "video", "parentTemplateKey": "marketing_video_package", "outputContract": "MEDIA_VERSION", "semanticRole": "DELIVERABLE"}
  ],
  "actions": [
    {"actionKey": "video.generate", "targetTemplateKey": "video", "dependencyTemplateKeys": ["script"]}
  ]
}
```

初始化字段：`description`（视频主题）、`styleGuidance`（视频风格）、`deliveryRequirements`（`digitalHumanId`、`targetDuration`、`aspectRatio`）。

视频动作只有一个，不再按时长拆成两个动作；简单与复杂视频的路由在执行器内完成，见 [design.md](./design.md)。脚本生成动作暂未提供：没有可执行的文本生成绑定，脚本由人工或助理产出并采纳。

#### postDeliveryAction

两个交付集合里仍保留 `postDeliveryAction`，但 Java 侧没有任何代码读取它，字段也引用了旧的表单键。它将由交付后监听器替代，见 [../status.md](../status.md)。

---

## 🔍 故障排查

### 问题1: Blueprint未出现在创建页面

**原因**: 
- Blueprint status不是'published'
- 项目类型未注册
- 前端缓存未刷新

**解决方案**:
```sql
-- 检查Blueprint状态
SELECT code, status FROM aigc_project_blueprint 
WHERE code LIKE 'digital-human%';

-- 更新状态为published
UPDATE aigc_project_blueprint 
SET status = 'published' 
WHERE code LIKE 'digital-human%';

-- 清除前端缓存
-- 在浏览器中按 Ctrl+Shift+R 强制刷新
```

### 问题2: 形象生成失败

**原因**:
- 图像生成任务提交失败（模型或渠道未配置）
- 提示词格式不正确
- 积分不足

**解决方案**:
```bash
# 检查日志：形象生成走通用 image.generate 绑定（aigc.image.generate）
tail -f logs/aaf-api.log | grep "image.generate"
```
```sql
-- 确认通用绑定存在
SELECT action_key, target_ref, status FROM aigc_execution_binding
WHERE action_key = 'image.generate' AND deleted = FALSE;
```

### 问题3: postDeliveryAction调用失败

**原因**:
- 数字人API端点不存在
- 请求参数映射错误
- 权限验证失败

**解决方案**:
```sql
-- 检查postDeliveryAction日志
SELECT * FROM aigc_execution_log 
WHERE action_key LIKE '%postDelivery%' 
ORDER BY create_time DESC 
LIMIT 10;

-- 手动测试数字人API
curl -X POST http://localhost:8080/api/digital-humans \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "测试",
    "avatarImageUrl": "https://example.com/avatar.jpg",
    "voiceConfig": {
      "provider": "aliyun",
      "presetVoiceId": 1,
      "speechRate": 0,
      "pitchRate": 0,
      "volume": 50
    }
  }'
```

### 问题4: 视频生成超时

**原因**:
- 目标时长过长
- Hyperframes API响应慢
- 网络连接问题

**解决方案**:
```bash
# 检查执行状态
curl http://localhost:8080/api/aigc/executions/{executionId}/status \
  -H "Authorization: Bearer {token}"

# 调整超时配置
# 在application.yml中增加超时时间
aigc:
  execution:
    timeout:
      video_generation: 600000  # 10分钟
```

---

## 📊 监控指标

### 关键指标

1. **Blueprint使用量**
```sql
SELECT 
  pb.code,
  pb.name,
  COUNT(p.id) as project_count,
  COUNT(CASE WHEN p.status = 'COMPLETED' THEN 1 END) as completed_count
FROM aigc_project_blueprint pb
LEFT JOIN aigc_project p ON p.blueprint_code = pb.code
WHERE pb.code LIKE 'digital-human%'
GROUP BY pb.code, pb.name;
```

2. **数字人创建成功率**
```sql
SELECT 
  COUNT(*) FILTER (WHERE status = 'active') as active_count,
  COUNT(*) FILTER (WHERE status = 'archived') as archived_count,
  COUNT(*) as total_count,
  ROUND(100.0 * COUNT(*) FILTER (WHERE status = 'active') / NULLIF(COUNT(*), 0), 2) as success_rate
FROM digital_human
WHERE deleted = FALSE;
```

3. **视频生成统计**
```sql
SELECT 
  dh.name,
  dh.video_count,
  dh.total_duration,
  dh.credits_consumed,
  dh.last_used_at
FROM digital_human dh
WHERE dh.video_count > 0
ORDER BY dh.video_count DESC
LIMIT 10;
```

4. **积分消耗分析**
```sql
SELECT 
  operation,
  COUNT(*) as operation_count,
  SUM(credits_consumed) as total_credits,
  AVG(credits_consumed) as avg_credits
FROM digital_human_credit_consumption
WHERE create_time >= NOW() - INTERVAL '30 days'
  AND deleted = FALSE
GROUP BY operation
ORDER BY total_credits DESC;
```

---

## 🔐 安全检查

### 权限验证

```sql
-- 检查数字人模块权限配置
SELECT 
  pc.code,
  pc.name,
  COUNT(rp.role_id) as assigned_roles
FROM sys_permission_code pc
LEFT JOIN sys_role_permission rp ON rp.permission_id = pc.id
WHERE pc.module = 'digital-human'
GROUP BY pc.code, pc.name;

-- 检查用户权限
SELECT 
  u.username,
  r.name as role_name,
  pc.code as permission_code
FROM sys_user u
JOIN sys_user_role ur ON ur.user_id = u.id
JOIN sys_role r ON r.id = ur.role_id
JOIN sys_role_permission rp ON rp.role_id = r.id
JOIN sys_permission_code pc ON pc.id = rp.permission_id
WHERE pc.module = 'digital-human'
  AND u.id = {user_id};
```

### 数据隔离验证

```sql
-- 验证组织内数字人可见范围（同组织可读，仅创建者 owner_id 可改）
SELECT 
  id,
  name,
  owner_id,
  org_id,
  workspace_id
FROM digital_human
WHERE deleted = FALSE
  AND org_id = {current_org_id}
  AND (workspace_id IS NULL OR workspace_id = {current_workspace_id});

-- 验证软删除
SELECT 
  COUNT(*) FILTER (WHERE deleted = FALSE) as active,
  COUNT(*) FILTER (WHERE deleted = TRUE) as deleted,
  COUNT(*) as total
FROM digital_human;
```

---

## 📝 回滚步骤

如果部署出现问题，可以执行回滚：

```sql
-- 1. 删除Blueprint配置
DELETE FROM aigc_project_blueprint 
WHERE code IN ('digital-human-asset-generation', 'digital-human-marketing-video');

-- 2. 删除项目类型
DELETE FROM aigc_project_type 
WHERE code IN ('digital_human_asset', 'digital_human_video');

-- 3. 删除权限配置
DELETE FROM sys_role_permission 
WHERE permission_id IN (
  SELECT id FROM sys_permission_code WHERE module = 'digital-human'
);

DELETE FROM sys_permission_code 
WHERE module = 'digital-human';

-- 4. 删除测试数据（可选）
DELETE FROM digital_human WHERE generation_source = 'aigc_project';

-- 5. 验证回滚
SELECT COUNT(*) FROM aigc_project_blueprint WHERE code LIKE 'digital-human%';
-- 应返回 0
```

---

## 📞 支持联系

**技术问题**: 
- 内部Slack: #digital-human-dev
- GitHub Issues: https://github.com/{org}/aaf/issues

**紧急支持**: 
- 值班手机: +86-xxx-xxxx-xxxx
- 邮件: support@example.com

---

**文档版本**: v1.0  
**最后更新**: 2026-09-22  
**下次更新**: 生产环境部署后

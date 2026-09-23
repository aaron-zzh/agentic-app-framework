# 数字人系统 Blueprint 部署指南

**版本**: v1.0  
**日期**: 2026-09-22  
**状态**: ✅ Blueprint配置已完成

---

## 📦 部署文件清单

### 1. Blueprint种子数据
**文件**: `apps/service/aaf-api/src/main/resources/db/seed/v24__digital_human_blueprint_seed.sql`

**内容**:
- 2个Blueprint配置 (数字人资产生成 + 营销视频生成)
- 2个项目类型 (digital_human_asset + digital_human_video)
- 8个权限代码
- 角色权限绑定

**大小**: ~8KB  
**行数**: ~150行

---

## 🚀 部署步骤

### Step 1: 执行数据库迁移（如未执行）

```bash
# 方式一：使用Maven（推荐）
cd apps/service/aaf-api
mvn flyway:migrate

# 方式二：手动执行SQL
psql -U postgres -d aaf_db -f src/main/resources/db/migration/V21__digital_human_schema.sql
```

**验证迁移成功**:
```sql
-- 检查表是否创建
SELECT table_name FROM information_schema.tables 
WHERE table_name IN ('digital_human', 'preset_voice', 'credit_consumption');

-- 检查预设音色数据
SELECT id, name, gender FROM preset_voice WHERE deleted = FALSE;
```

预期输出：5条预设音色记录

### Step 2: 执行Blueprint种子数据

```bash
# 方式一：使用psql
cd apps/service/aaf-api
psql -U postgres -d aaf_db -f src/main/resources/db/seed/v24__digital_human_blueprint_seed.sql

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
```

预期输出：
- 2个Blueprint (status = 'published')
- 2个项目类型 (status = 'published')
- 8个权限代码 (status = 0)

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
    "context": {
      "appearance_prompt": "25岁职业女性，短发，商务装",
      "preset_voice": "1"
    }
  }'
```

---

## 🧪 功能测试

### 测试场景1: 数字人资产生成

**步骤**:
1. 登录前端系统
2. 访问 `/aigc/projects/create`
3. 选择Blueprint: "数字人资产生成"
4. 填写表单:
   - 形象描述: "30岁男性，商务休闲装，温和微笑"
   - 选择音色: "艾诚"
5. 提交创建项目
6. 等待形象生成完成
7. 选择并采用一个候选形象
8. 验证数字人实体是否创建成功
9. 访问 `/digital-humans` 查看新创建的数字人

**预期结果**:
- ✅ 项目创建成功
- ✅ 生成5个候选形象
- ✅ 采用后自动创建数字人实体
- ✅ 显示成功消息并跳转到数字人详情页

### 测试场景2: 营销视频生成

**步骤**:
1. 确保至少有一个可用数字人
2. 访问 `/aigc/projects/create`
3. 选择Blueprint: "数字人营销视频"
4. 填写表单:
   - 选择数字人: 选择已创建的数字人
   - 视频主题: "介绍我们的智能客服系统"
   - 目标时长: 30秒
   - 视频风格: "专业"
   - 画面比例: "16:9"
5. 提交创建项目
6. 等待脚本生成完成
7. 审阅并采用脚本
8. 等待视频生成完成
9. 下载并查看生成的视频

**预期结果**:
- ✅ 项目创建成功
- ✅ 自动生成分段脚本（约2-3段）
- ✅ 脚本可编辑
- ✅ 视频生成成功（简单模式，≤15秒单段）
- ✅ 数字人使用统计更新

---

## 🔧 配置说明

### Blueprint配置项

#### 1. digital-human-asset-generation

**关键配置**:
```json
{
  "slotTemplates": [
    {
      "templateKey": "avatar_image",
      "objectType": "image_deliverable",
      "semanticRole": "digital_human_avatar",
      "outputContract": "MEDIA_VERSION",
      "candidateSettings": {
        "maxCandidates": 5,
        "allowRegeneration": true
      }
    }
  ],
  "actions": [
    {
      "actionKey": "generate_avatar",
      "boundExecutionId": "aliyun_image_generation",
      "inputMapping": {
        "model": "stable-diffusion-xl",
        "aspectRatio": "1:1",
        "quality": "hd"
      }
    }
  ]
}
```

**postDeliveryAction**:
- 端点: `POST /api/digital-humans`
- 自动创建数字人实体
- 绑定AIGC项目ID

#### 2. digital-human-marketing-video

**关键配置**:
```json
{
  "slotTemplates": [
    {
      "templateKey": "script",
      "objectType": "video_script",
      "allowManualEdit": true
    },
    {
      "templateKey": "video",
      "objectType": "video_deliverable",
      "readinessCriteria": {
        "dependencies": ["script"]
      }
    }
  ],
  "actions": [
    {
      "actionKey": "generate_script",
      "boundExecutionId": "aigc_text_generation",
      "inputMapping": {
        "model": "claude-sonnet-5",
        "responseFormat": "json"
      }
    },
    {
      "actionKey": "generate_video_simple",
      "condition": "{{target_duration}} <= 15",
      "boundExecutionId": "aliyun_video_generation"
    },
    {
      "actionKey": "generate_video_hyperframes",
      "condition": "{{target_duration}} > 15",
      "boundExecutionId": "hyperframes_integration"
    }
  ]
}
```

**postDeliveryAction**:
- 端点: `POST /api/digital-humans/{id}/usage-stats`
- 更新视频数量、时长、积分消耗

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
- 阿里云API配置错误
- 提示词格式不正确
- 积分不足

**解决方案**:
```bash
# 检查日志
tail -f logs/aaf-api.log | grep "aliyun_image_generation"

# 验证API配置
curl -X POST http://localhost:8080/api/aigc/executions/test \
  -H "Authorization: Bearer {token}" \
  -d '{"executionId": "aliyun_image_generation"}'
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
  operation_type,
  COUNT(*) as operation_count,
  SUM(credits) as total_credits,
  AVG(credits) as avg_credits
FROM credit_consumption
WHERE operation_time >= NOW() - INTERVAL '30 days'
GROUP BY operation_type
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
-- 验证用户只能看到自己的数字人
SELECT 
  id,
  name,
  user_id,
  org_id
FROM digital_human
WHERE deleted = FALSE
  AND (user_id = {current_user_id} OR org_id = {current_org_id});

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

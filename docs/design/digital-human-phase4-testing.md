# Phase 4 集成测试指南

**版本**: v1.0  
**日期**: 2026-09-22  
**测试范围**: 数字人视频生成模块

---

## 🎯 测试目标

验证视频生成模块的完整流程：
1. 前端场景编辑器功能
2. 视频生成对话框交互
3. 后端执行器路由逻辑
4. 使用统计更新
5. 端到端集成

---

## 📋 前置条件

### 1. 数据库准备

```sql
-- 1. 运行数字人schema
\i apps/service/aaf-api/src/main/resources/db/migration/V21__digital_human_schema.sql

-- 2. 运行Blueprint seed
\i apps/service/aaf-api/src/main/resources/db/seed/v24__digital_human_blueprint_seed.sql

-- 3. 运行执行器配置seed
\i apps/service/aaf-api/src/main/resources/db/seed/v25__digital_human_video_executors_seed.sql

-- 4. 验证数据
SELECT code, name, status FROM aigc_execution_binding 
WHERE code IN ('digital_human_video_generation', 'aliyun_video_generation', 'hyperframes_integration');

-- 应该看到3条记录，status都是'published'
```

### 2. 创建测试数字人

```sql
-- 创建测试数字人
INSERT INTO digital_human (
    name, description, avatar_image_url, 
    voice_config, appearance, model_config,
    user_id, org_id, workspace_id,
    status, video_count, total_duration, credits_consumed,
    created_at, updated_at
) VALUES (
    '测试数字人-小美',
    '用于视频生成测试的数字人',
    'https://example.com/avatar-xiaomei.jpg',
    '{"voice": "xiaoyun", "voiceName": "小云", "speechRate": 1.0, "pitchRate": 0, "volume": 50}'::jsonb,
    '{"gender": "female", "age": "25-30", "style": "professional"}'::jsonb,
    '{"provider": "aliyun", "modelId": "digital-human-v2"}'::jsonb,
    1, 1, 1,
    'active', 0, 0, 0,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- 获取ID
SELECT id, name, status FROM digital_human WHERE name = '测试数字人-小美';
```

### 3. 启动服务

```bash
# 后端
cd D:/code/agentic-app-framework
./mvnw spring-boot:run -pl apps/service/aaf-api -Dspring-boot.run.profiles=dev

# 前端
cd apps/webui
pnpm run dev
```

---

## 🧪 测试用例

### Test Case 1: 场景编辑器基础功能

**目标**: 验证场景编辑器的增删改查功能

**步骤**:
1. 访问 `http://localhost:3000/digital-humans/{id}`
2. 点击"生成视频"按钮
3. 切换到"高级模式"标签
4. 点击"添加场景"按钮，验证新场景出现
5. 编辑场景标题、内容、时长
6. 拖拽场景卡片，改变顺序
7. 点击删除按钮，移除场景

**预期结果**:
- ✅ 场景能正常添加
- ✅ 场景内容可以编辑
- ✅ 拖拽排序流畅无卡顿
- ✅ 删除场景立即生效
- ✅ 总时长实时更新

**数据验证**: 无需验证（纯前端）

---

### Test Case 2: 简单模式视频生成 (≤15秒)

**目标**: 验证短视频生成流程

**测试数据**:
```
脚本内容: "欢迎来到我们的产品发布会！今天我们将为您介绍全新的AI智能助手。"
预计时长: ~13秒 (39字 ÷ 3字/秒)
视频比例: 16:9
```

**步骤**:
1. 打开视频生成对话框
2. 选择"简单模式"
3. 输入上述脚本内容
4. 选择视频比例 "16:9"
5. 点击"开始生成"
6. 观察日志输出

**预期结果**:
- ✅ 对话框关闭
- ✅ Toast提示"视频生成任务已创建"
- ✅ 跳转到AIGC项目详情页
- ✅ 后端日志显示: "使用阿里云简单视频生成，时长: 13秒"
- ✅ 执行器选择: `aliyun_video_generation`

**后端日志验证**:
```bash
grep "使用阿里云简单视频生成" logs/aaf-api.log
# 应该看到:
# 2026-09-22 14:30:45 INFO  DigitalHumanVideoGenerationAdapter - 使用阿里云简单视频生成，时长: 13秒
# 2026-09-22 14:30:45 INFO  DigitalHumanVideoGenerationAdapter - 调用阿里云视频生成API - avatarUrl: https://..., duration: 13s
```

**数据库验证**:
```sql
-- 检查AIGC项目是否创建
SELECT id, name, blueprint_code, status 
FROM aigc_project 
WHERE name LIKE '%测试数字人-小美%'
ORDER BY created_at DESC LIMIT 1;

-- 检查Slot数据
SELECT slot_code, status, output_data 
FROM aigc_project_slot 
WHERE project_id = <上面查到的project_id>;
```

---

### Test Case 3: 高级模式多场景视频生成 (>15秒)

**目标**: 验证长视频场景拆分和Hyperframes路由

**测试数据**:
```
场景1: "大家好，我是数字人小美。" (时长: 5秒)
场景2: "今天给大家介绍我们的新产品AI助手。" (时长: 8秒)
场景3: "它具有智能对话、知识问答、任务执行等多种功能。" (时长: 10秒)
场景4: "欢迎大家试用，谢谢！" (时长: 5秒)
总时长: 28秒
视频比例: 9:16
```

**步骤**:
1. 打开视频生成对话框
2. 选择"高级模式"
3. 添加4个场景，填入上述内容和时长
4. 选择视频比例 "9:16"
5. 点击"开始生成"
6. 观察日志输出

**预期结果**:
- ✅ Toast提示"多场景视频生成任务已创建"
- ✅ 跳转到AIGC项目详情页
- ✅ 后端日志显示: "使用Hyperframes多场景合成，时长: 28秒"
- ✅ 执行器选择: `hyperframes_integration`
- ✅ 场景数据正确传递到metadata

**后端日志验证**:
```bash
grep "使用Hyperframes多场景合成" logs/aaf-api.log
# 应该看到:
# 2026-09-22 14:35:20 INFO  DigitalHumanVideoGenerationAdapter - 使用Hyperframes多场景合成，时长: 28秒
# 2026-09-22 14:35:20 INFO  DigitalHumanVideoGenerationAdapter - 脚本拆分完成，共4个场景
# 2026-09-22 14:35:20 INFO  DigitalHumanVideoGenerationAdapter - 调用Hyperframes合成API - avatarUrl: https://..., sceneCount: 4
```

**数据库验证**:
```sql
-- 检查Slot metadata中的scenes字段
SELECT 
    slot_code, 
    status,
    input_data->>'estimatedDuration' as duration,
    jsonb_array_length(input_data->'scenes') as scene_count
FROM aigc_project_slot 
WHERE project_id = <project_id> AND slot_code = 'script';

-- 应该返回: duration=28, scene_count=4
```

---

### Test Case 4: 场景拖拽排序

**目标**: 验证场景顺序改变后order字段正确更新

**步骤**:
1. 创建3个场景: A, B, C
2. 拖拽场景B到场景A上方
3. 验证新顺序: B, A, C
4. 检查每个场景的order字段

**预期结果**:
- ✅ 视觉顺序立即改变
- ✅ 场景B的order=1, A的order=2, C的order=3
- ✅ 总时长保持不变

**前端调试**:
```javascript
// 在SceneEditor.tsx的handleDragEnd中添加console.log
console.log('Reordered scenes:', reordered.map(s => ({ id: s.id, order: s.order })))
```

---

### Test Case 5: 使用统计更新

**目标**: 验证视频生成完成后统计数据更新

**前置条件**: 
- 已完成Test Case 2或3
- 视频已生成完成（Mock URL已返回）

**步骤**:
1. 手动调用使用统计更新API:
```bash
curl -X POST "http://localhost:8080/api/digital-humans/{id}/usage-stats?videoCount=1&duration=13&creditsConsumed=76" \
  -H "Authorization: Bearer <token>"
```

2. 刷新数字人详情页
3. 查看"使用统计"Tab

**预期结果**:
- ✅ API返回200状态码
- ✅ videoCount增加1
- ✅ totalDuration增加13秒
- ✅ creditsConsumed增加76
- ✅ lastUsedAt更新为当前时间

**数据库验证**:
```sql
SELECT 
    id, name,
    video_count,
    total_duration,
    credits_consumed,
    last_used_at
FROM digital_human
WHERE id = <digital_human_id>;
```

**积分消耗验证**:
```sql
-- 检查credit_consumption表
SELECT 
    digital_human_id,
    action_type,
    credits_amount,
    metadata->>'duration' as duration,
    metadata->>'provider' as provider,
    created_at
FROM credit_consumption
WHERE digital_human_id = <digital_human_id>
ORDER BY created_at DESC;
```

---

### Test Case 6: 视频历史列表

**目标**: 验证视频历史展示和播放

**前置条件**: 
- 已生成至少1个视频
- 视频URL可访问

**步骤**:
1. 访问数字人详情页
2. 切换到"视频历史"Tab
3. 验证视频卡片显示
4. 点击播放按钮
5. 在弹出的对话框中测试视频控制

**预期结果**:
- ✅ 显示视频缩略图（或Play图标）
- ✅ 显示视频标题、时长、创建时间
- ✅ Hover时显示播放按钮
- ✅ 点击后弹出视频播放器
- ✅ 播放/暂停、音量控制、进度条可用
- ✅ 下载按钮可触发下载

**当前状态**: 
```typescript
// 数据源暂时为空数组，需要实现API获取
<VideoHistoryList videos={[]} loading={false} />
```

**TODO**: 创建API端点获取数字人的视频历史
```typescript
// 需要添加:
// GET /api/digital-humans/{id}/videos
// 返回该数字人生成的所有视频列表
```

---

### Test Case 7: 时长边界测试

**目标**: 验证15秒边界的路由逻辑

**测试数据**:
| 时长 | 预期执行器 | 说明 |
|------|-----------|------|
| 10秒 | aliyun | 短视频 |
| 15秒 | aliyun | 边界值（等于） |
| 16秒 | hyperframes | 长视频 |
| 30秒 | hyperframes | 长视频 |

**步骤**: 分别使用上述时长创建视频生成任务

**预期结果**:
- ✅ 15秒及以下走阿里云
- ✅ 16秒及以上走Hyperframes
- ✅ 后端日志正确显示路由决策

---

### Test Case 8: 错误处理测试

**目标**: 验证异常情况的错误处理

#### 8.1 空脚本验证
**步骤**: 
1. 打开视频生成对话框
2. 不输入任何脚本内容
3. 点击"开始生成"

**预期结果**: 
- ✅ Toast错误提示: "请输入视频脚本"
- ✅ 对话框不关闭
- ✅ 不创建AIGC项目

#### 8.2 空场景内容验证
**步骤**:
1. 打开视频生成对话框，高级模式
2. 添加场景但不填写内容
3. 点击"开始生成"

**预期结果**:
- ✅ Toast错误提示: "请填写所有场景的内容"
- ✅ 对话框不关闭

#### 8.3 后端参数验证
**步骤**: 直接调用执行器（通过AIGC系统）传入无效参数

**测试用例**:
```json
// 缺少digitalHumanId
{
  "avatarImageUrl": "https://example.com/avatar.jpg",
  "scriptContent": "测试",
  "duration": 10
}
// 预期: BusinessException - "digitalHumanId is required"

// duration为0
{
  "digitalHumanId": 123,
  "avatarImageUrl": "https://example.com/avatar.jpg",
  "scriptContent": "测试",
  "duration": 0
}
// 预期: BusinessException - "duration must be positive"
```

---

## 🔍 性能测试

### Test Case 9: 并发生成测试

**目标**: 验证系统能处理多个并发视频生成请求

**步骤**:
1. 创建5个数字人
2. 同时为5个数字人发起视频生成任务
3. 观察系统响应和资源占用

**预期结果**:
- ✅ 所有请求都成功创建AIGC项目
- ✅ 执行器调用不互相阻塞
- ✅ 数据库连接池正常
- ✅ 内存占用在可接受范围

**监控指标**:
```sql
-- 检查活跃项目数
SELECT COUNT(*) FROM aigc_project WHERE status IN ('pending', 'running');

-- 检查数据库连接
SHOW max_connections;
SELECT COUNT(*) FROM pg_stat_activity;
```

---

## 📊 回归测试清单

在每次代码变更后运行以下快速检查：

- [ ] 场景可以添加和删除
- [ ] 场景可以拖拽排序
- [ ] 简单模式可以创建项目
- [ ] 高级模式可以创建项目
- [ ] 15秒边界路由正确
- [ ] 后端日志输出正常
- [ ] 数据库数据正确写入
- [ ] 前端无控制台错误

---

## 🐛 已知问题

1. **Mock视频URL**: 当前执行器返回的是mock URL，需要实际API集成
2. **视频历史空白**: VideoHistoryList组件数据源为空数组，需要实现后端API
3. **积分计算**: creditCostFormula在Blueprint中定义，但实际计算逻辑待实现
4. **进度推送**: 缺少WebSocket实时推送视频生成进度

---

## 📝 测试报告模板

```markdown
## Phase 4 测试报告

**测试日期**: 2026-09-22  
**测试人员**: [姓名]  
**环境**: Dev

### 测试结果汇总
- 总用例数: 9
- 通过: X
- 失败: Y
- 阻塞: Z

### 详细结果

| 用例编号 | 用例名称 | 状态 | 备注 |
|---------|---------|------|------|
| TC1 | 场景编辑器基础功能 | ✅ PASS | |
| TC2 | 简单模式视频生成 | ⏳ BLOCKED | 需要API密钥 |
| TC3 | 高级模式多场景生成 | ⏳ BLOCKED | 需要API密钥 |
| TC4 | 场景拖拽排序 | ✅ PASS | |
| TC5 | 使用统计更新 | ✅ PASS | |
| TC6 | 视频历史列表 | ❌ FAIL | 后端API未实现 |
| TC7 | 时长边界测试 | ✅ PASS | |
| TC8 | 错误处理测试 | ✅ PASS | |
| TC9 | 并发生成测试 | ⚠️ SKIP | 暂不执行 |

### 发现的Bug

1. [BUG-001] 视频历史列表无数据
   - 严重程度: 中
   - 原因: 后端API未实现
   - 状态: Open

### 改进建议

1. 实现视频历史查询API
2. 集成实际视频生成服务
3. 添加进度推送功能
```

---

**测试文档版本**: v1.0  
**最后更新**: 2026-09-22  
**下次审查**: Phase 4完成API集成后

-- ============================================================
-- 数字人种子数据：Blueprint / 项目类型 / 权限 / 视频执行绑定 / 字典
-- 依赖: v7__aigc_schema.sql, v17__aigc_content_studio_seed.sql, v19__digital_human_schema.sql
--
-- Blueprint 约定（与 AigcCatalogBlueprintCompiler 一致）：
--   - 动作只声明 actionKey 与槽位关系，执行目标由 aigc_execution_binding 按 action_key 解析
--   - 项目初始化字段只能使用平台固定字段（description / styleGuidance / deliveryRequirements 等）
--   - 槽位 semanticRole 取 STRUCTURE / WORKING_ARTIFACT / DELIVERABLE / EVIDENCE / REFERENCE
--   - 交付集合的根槽位使用 COMPOSITE_MANIFEST，成员槽位以它为 parentTemplateKey
-- ============================================================

-- ============================================================
-- 数字人资产生成 Blueprint
-- 形象生成复用平台 image.generate 绑定（aigc.image.generate），参考图通过动作附件传入
-- ============================================================

INSERT INTO aigc_project_blueprint
    (code, name, project_type_code, blueprint_version, production_mode, description, status,
     slot_template_spec, relation_spec, action_spec, deliverable_spec, process_policy, project_initialization)
VALUES
('digital-human-asset-generation', '数字人资产生成', 'digital_human_asset', '1.0.0', 'standard',
 '通过提示词或参考图生成数字人形象，配合预设音色创建完整的数字人资产', 'published',
 '{"slotTemplates":[
    {"templateKey":"digital_human_asset","stableKeyPattern":"digital_human_asset","objectType":"deliverable_set","displayNamePattern":"数字人资产包","orderNo":10,"defaultCount":1,"minCount":1,"maxCount":1,"defaultContractRole":"REQUIRED","semanticRole":"DELIVERABLE","outputContract":"COMPOSITE_MANIFEST"},
    {"templateKey":"avatar_image","stableKeyPattern":"avatar_image","objectType":"image_deliverable","displayNamePattern":"数字人形象","description":"正面、中性背景、适合视频生成的 1:1 形象图","parentTemplateKey":"digital_human_asset","orderNo":20,"defaultCount":1,"minCount":1,"maxCount":1,"defaultContractRole":"REQUIRED","semanticRole":"DELIVERABLE","outputContract":"MEDIA_VERSION"}
  ]}'::jsonb,
 '{"relations":[]}'::jsonb,
 '{"actions":[
    {"actionKey":"image.generate","targetTemplateKey":"avatar_image","requiredContextKeys":["description"],"isDefault":true,"inputPreset":{"width":1024,"height":1024,"quality":"hd"}}
  ]}'::jsonb,
 '{"sets":[
    {"setTemplateKey":"digital_human_asset","allowedSlotTemplateKeys":["avatar_image"],"completionMode":"ALL_REQUIRED","reviewMode":"NONE","publicationPolicy":"REQUIRED","postDeliveryAction":{"type":"create_digital_human","endpoint":"POST /api/digital-humans","inputMapping":{"name":"{{project.name}}","description":"{{appearance_prompt}}","avatarImageUrl":"{{avatar_image.adoptedVersion.url}}","appearance":{"prompt":"{{appearance_prompt}}","style":"AI 生成","gender":"{{gender}}","age":"{{age}}"},"voiceConfig":{"provider":"aliyun","presetVoiceId":"{{preset_voice}}","speechRate":0,"pitchRate":0,"volume":50},"tags":[],"generationSource":"aigc_project","aigcProjectId":"{{project.id}}"},"successMessage":"数字人已创建！[查看详情](/digital-humans/{{response.id}})","errorMessage":"数字人创建失败，请联系管理员"}}
  ]}'::jsonb,
 '{"reviewRequired":false,"publicationPolicy":"REQUIRED","autoActionKeys":["image.generate"],"confirmationGateKeys":["avatar_image.adopt"]}'::jsonb,
 '{"fields":[
    {"key":"description","label":"形象描述","description":"描述越详细，生成效果越好。包括年龄、性别、发型、服装、表情等；参考图在生成动作中作为附件上传","valueType":"TEXTAREA","required":true,"aiAssist":true,"orderNo":10,"validation":{"minLength":10,"maxLength":500}},
    {"key":"deliveryRequirements","label":"数字人设置","description":"JSON 对象：presetVoiceId（预设音色 ID，必填）、gender（female / male / neutral，可选）、age（18 ~ 65，可选）","valueType":"JSON","required":true,"orderNo":20}
  ],"documents":[]}'::jsonb)
ON CONFLICT DO NOTHING;

-- ============================================================
-- 数字人营销视频生成 Blueprint
-- 脚本是工作产物（JSON：totalDuration + scenes），由人工或助理产出并采纳；
-- 视频动作绑定 digital_human_video_generation（见文末绑定），直接消费脚本的 scenes，不再二次拆分
-- ============================================================

INSERT INTO aigc_project_blueprint
    (code, name, project_type_code, blueprint_version, production_mode, description, status,
     slot_template_spec, relation_spec, action_spec, deliverable_spec, process_policy, project_initialization)
VALUES
('digital-human-marketing-video', '数字人营销视频', 'digital_human_video', '1.0.0', 'standard',
 '使用数字人生成营销视频内容，按脚本场景生成并合成', 'published',
 '{"slotTemplates":[
    {"templateKey":"script","stableKeyPattern":"script","objectType":"video_script","displayNamePattern":"视频脚本","description":"JSON 文档：{totalDuration, scenes:[{order, title, content, duration, sceneDescription}]}，单场景时长不超过 15 秒","orderNo":10,"defaultCount":1,"minCount":1,"maxCount":1,"defaultContractRole":"REQUIRED","semanticRole":"WORKING_ARTIFACT","outputContract":"DOCUMENT_VERSION"},
    {"templateKey":"marketing_video_package","stableKeyPattern":"marketing_video_package","objectType":"deliverable_set","displayNamePattern":"营销视频包","orderNo":20,"defaultCount":1,"minCount":1,"maxCount":1,"defaultContractRole":"REQUIRED","semanticRole":"DELIVERABLE","outputContract":"COMPOSITE_MANIFEST"},
    {"templateKey":"video","stableKeyPattern":"video","objectType":"video_deliverable","displayNamePattern":"数字人视频","parentTemplateKey":"marketing_video_package","orderNo":30,"defaultCount":1,"minCount":1,"maxCount":1,"defaultContractRole":"REQUIRED","semanticRole":"DELIVERABLE","outputContract":"MEDIA_VERSION"}
  ]}'::jsonb,
 '{"relations":[
    {"sourceTemplateKey":"script","targetTemplateKey":"video","type":"derives"}
  ]}'::jsonb,
 '{"actions":[
    {"actionKey":"video.generate","targetTemplateKey":"video","dependencyTemplateKeys":["script"],"requiredContextKeys":["deliveryRequirements"],"isDefault":true}
  ]}'::jsonb,
 '{"sets":[
    {"setTemplateKey":"marketing_video_package","allowedSlotTemplateKeys":["video"],"completionMode":"ALL_REQUIRED","reviewMode":"NONE","publicationPolicy":"REQUIRED","postDeliveryAction":{"type":"update_digital_human_stats","endpoint":"POST /api/digital-humans/{{digital_human.id}}/usage-stats","inputMapping":{"videoCount":1,"duration":"{{video.adoptedVersion.metadata.duration}}","creditsConsumed":"{{project.totalCreditsConsumed}}"},"successMessage":"视频已生成！[下载视频]({{video.adoptedVersion.url}})"}}
  ]}'::jsonb,
 '{"reviewRequired":false,"publicationPolicy":"REQUIRED","autoActionKeys":[],"confirmationGateKeys":["script.adopt"]}'::jsonb,
 '{"fields":[
    {"key":"description","label":"视频主题","description":"描述视频要表达的主题内容","valueType":"TEXTAREA","required":true,"aiAssist":true,"orderNo":10,"validation":{"minLength":10,"maxLength":500}},
    {"key":"styleGuidance","label":"视频风格","description":"professional（专业）/ casual（休闲）/ energetic（活力）","valueType":"TEXT","orderNo":20},
    {"key":"deliveryRequirements","label":"视频设置","description":"JSON 对象：digitalHumanId（数字人 ID，必填）、targetDuration（目标总时长，5 ~ 600 秒）、aspectRatio（16:9 / 9:16 / 1:1，缺省 16:9）","valueType":"JSON","required":true,"orderNo":30}
  ],"documents":[]}'::jsonb)
ON CONFLICT DO NOTHING;

-- ============================================================
-- 项目类型配置
-- ============================================================

INSERT INTO aigc_project_type
    (code, name, description, default_channels, default_production_mode, status)
VALUES
    ('digital_human_asset', '数字人资产', '数字人形象生成', '[]'::jsonb, 'standard', 'published'),
    ('digital_human_video', '数字人视频', '数字人营销视频生成', '["douyin","wechat_channels"]'::jsonb, 'standard', 'published')
ON CONFLICT DO NOTHING;

-- ============================================================
-- 权限配置
-- ============================================================

INSERT INTO sys_permission_code (name, code, module, resource, action, status)
VALUES
    ('创建数字人资产', 'aigc:digital-human-asset:create', 'aigc', 'digital-human-asset', 'create', 0),
    ('创建数字人视频', 'aigc:digital-human-video:create', 'aigc', 'digital-human-video', 'create', 0),
    ('查看数字人', 'digital-human:digital-human:read', 'digital-human', 'digital-human', 'read', 0),
    ('创建数字人', 'digital-human:digital-human:create', 'digital-human', 'digital-human', 'create', 0),
    ('更新数字人', 'digital-human:digital-human:update', 'digital-human', 'digital-human', 'update', 0),
    ('删除数字人', 'digital-human:digital-human:delete', 'digital-human', 'digital-human', 'delete', 0),
    ('导出数字人', 'digital-human:digital-human:export', 'digital-human', 'digital-human', 'export', 0),
    ('切换数字人音色', 'digital-human:digital-human:change-voice', 'digital-human', 'digital-human', 'change-voice', 0),
    ('关联助理', 'digital-human:digital-human:link-assistant', 'digital-human', 'digital-human', 'link-assistant', 0),
    ('归档数字人', 'digital-human:digital-human:archive', 'digital-human', 'digital-human', 'archive', 0),
    ('恢复数字人', 'digital-human:digital-human:restore', 'digital-human', 'digital-human', 'restore', 0),
    ('查看预设音色', 'digital-human:preset-voice:read', 'digital-human', 'preset-voice', 'read', 0),
    ('导出预设音色', 'digital-human:preset-voice:export', 'digital-human', 'preset-voice', 'export', 0),
    ('查看数字人积分记录', 'digital-human:credit-consumption:read', 'digital-human', 'credit-consumption', 'read', 0),
    ('导出数字人积分记录', 'digital-human:credit-consumption:export', 'digital-human', 'credit-consumption', 'export', 0)
ON CONFLICT (code) WHERE deleted = FALSE DO NOTHING;

-- ============================================================
-- 角色权限绑定
-- ============================================================

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission_code permission ON permission.module IN ('digital-human')
WHERE role.code IN ('admin', 'super_admin', 'member', 'org_admin')
ON CONFLICT DO NOTHING;

-- ============================================================
-- 数字人视频执行绑定
-- ============================================================
-- 仅对 digital_human_video 项目覆盖通用的 video.generate 绑定（priority 高于 v17 的 aigc.video.generate）。
-- 目标 digital_human_video_generation 由 DigitalHumanVideoGenerationAdapter 执行：
--   - 单场景且 ≤15 秒：直接调用阿里云
--   - 其余：每个场景用阿里云出片，再调用 Hyperframes 合成
-- 提供商地址、超时、密钥属于运行配置，接入真实调用时放入 Spring 配置，不放在绑定表。
-- estimated_credits 仅用于预算校验；实际消耗以执行记录为准。
INSERT INTO aigc_execution_binding
    (action_key, project_type_code, target_type, target_ref, binding_version, priority,
     confirmation_required, estimated_credits, status)
VALUES
('video.generate', 'digital_human_video', 'tool', 'digital_human_video_generation', '1.0.0', 10, TRUE, 50.00, 'published')
ON CONFLICT DO NOTHING;

-- ==================== 数字人模块字典 ====================

-- 字典类型
INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('数字人状态', 'digital_human_status', 0, '对应 DigitalHumanStatusEnum'),
('数字人生成来源', 'digital_human_generation_source', 0, '对应 GenerationSourceEnum'),
('视频提供商', 'digital_human_video_provider', 0, '对应 VideoProviderEnum'),
('音色供应商', 'digital_human_voice_provider', 0, '对应 VoiceProviderEnum')
ON CONFLICT DO NOTHING;

-- digital_human_status 数字人状态（与 DigitalHumanStatusEnum 一致）
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('digital_human_status', '活跃',   'active',    1, 'success'),
('digital_human_status', '已归档', 'archived',  2, 'default')
ON CONFLICT DO NOTHING;

-- digital_human_generation_source 数字人生成来源（与 GenerationSourceEnum 一致）
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('digital_human_generation_source', '手动创建', 'manual',        1, 'primary'),
('digital_human_generation_source', 'AI 生成',  'ai_generated',  2, 'success'),
('digital_human_generation_source', '导入',     'imported',      3, 'info'),
('digital_human_generation_source', '模板克隆', 'template',      4, 'default')
ON CONFLICT DO NOTHING;

-- digital_human_video_provider 视频提供商（与 VideoProviderEnum 一致）
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('digital_human_video_provider', '阿里云',       'aliyun',       1, 'primary'),
('digital_human_video_provider', 'Hyperframes', 'hyperframes',  2, 'success')
ON CONFLICT DO NOTHING;

-- digital_human_voice_provider 音色供应商（与 VoiceProviderEnum 一致）
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('digital_human_voice_provider', '阿里云',   'aliyun',  1, 'primary'),
('digital_human_voice_provider', 'Azure',   'azure',   2, 'info'),
('digital_human_voice_provider', '自定义',   'custom',  3, 'default')
ON CONFLICT DO NOTHING;

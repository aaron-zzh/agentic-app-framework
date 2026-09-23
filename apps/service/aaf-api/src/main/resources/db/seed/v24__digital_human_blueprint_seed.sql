-- ============================================================
-- 数字人系统 Blueprint 种子数据
-- 版本: v1.0
-- 日期: 2026-09-22
-- 依赖: v7__aigc_schema.sql, v21__digital_human_schema.sql
-- ============================================================

-- ============================================================
-- 1. 数字人资产生成 Blueprint
-- ============================================================

INSERT INTO aigc_project_blueprint
    (code, name, project_type_code, blueprint_version, production_mode, description, status,
     slot_template_spec, relation_spec, action_spec, deliverable_spec, process_policy, project_initialization)
VALUES
('digital-human-asset-generation', '数字人资产生成', 'digital_human_asset', '1.0.0', 'standard',
 '通过提示词或参考图生成数字人形象，配合预设音色创建完整的数字人资产', 'published',
 '{"slotTemplates":[
    {"templateKey":"avatar_image","stableKeyPattern":"avatar_image","objectType":"image_deliverable","displayNamePattern":"数字人形象","orderNo":10,"defaultCount":1,"minCount":1,"maxCount":1,"defaultContractRole":"REQUIRED","semanticRole":"digital_human_avatar","outputContract":"MEDIA_VERSION","outputContractDetails":{"format":"image/png","aspectRatio":"1:1","minResolution":"1024x1024","maxFileSize":5242880},"candidateSettings":{"maxCandidates":5,"allowRegeneration":true}}
  ]}'::jsonb,
 '{"relations":[]}'::jsonb,
 '{"actions":[
    {"actionKey":"generate_avatar","targetTemplateKey":"avatar_image","isDefault":true,"boundExecutionId":"aliyun_image_generation","inputMapping":{"prompt":"{{appearance_prompt}}, professional digital human portrait, high quality, 4K, front facing, neutral background, studio lighting, suitable for video generation","referenceImage":"{{reference_image}}","aspectRatio":"1:1","quality":"hd","model":"stable-diffusion-xl"},"outputMapping":{"url":"{{execution.output.imageUrl}}","metadata":{"originalPrompt":"{{appearance_prompt}}","hasReference":"{{reference_image != null}}"}},"errorHandling":{"retryPolicy":{"maxRetries":2,"backoffMultiplier":2},"fallbackMessage":"形象生成失败，请调整提示词后重试"}}
  ]}'::jsonb,
 '{"sets":[
    {"setTemplateKey":"digital_human_asset","label":"数字人资产包","description":"包含形象图片和音色配置的完整数字人资产","allowedSlotTemplateKeys":["avatar_image"],"completionMode":"ALL_REQUIRED","reviewMode":"NONE","publicationPolicy":"REQUIRED","postDeliveryAction":{"type":"create_digital_human","endpoint":"POST /api/digital-humans","inputMapping":{"name":"{{project.name}}","description":"{{appearance_prompt}}","avatarImageUrl":"{{avatar_image.adoptedVersion.url}}","appearance":{"prompt":"{{appearance_prompt}}","style":"AI 生成","gender":"{{gender}}","age":"{{age}}"},"voiceConfig":{"provider":"aliyun","presetVoiceId":"{{preset_voice}}","speechRate":0,"pitchRate":0,"volume":50},"tags":[],"generationSource":"aigc_project","aigcProjectId":"{{project.id}}"},"successMessage":"数字人已创建！[查看详情](/digital-humans/{{response.id}})","errorMessage":"数字人创建失败，请联系管理员"}}
  ]}'::jsonb,
 '{"reviewRequired":false,"publicationPolicy":"REQUIRED","autoActionKeys":["generate_avatar"],"confirmationGateKeys":["avatar_image.adopt"]}'::jsonb,
 '{"fields":[
    {"key":"appearance_prompt","label":"形象描述","description":"描述越详细，生成效果越好。包括年龄、性别、发型、服装、表情等","valueType":"TEXTAREA","required":true,"placeholder":"25岁职业女性，短发，商务装，温柔微笑","validation":{"minLength":10,"maxLength":500},"orderNo":10},
    {"key":"reference_image","label":"参考图片","description":"上传参考图片可以提高生成准确度","valueType":"IMAGE","required":false,"validation":{"maxSize":10485760,"formats":["image/jpeg","image/png","image/webp"]},"orderNo":20},
    {"key":"preset_voice","label":"选择音色","valueType":"ENUM","required":true,"defaultValue":"1","options":[{"value":"1","label":"艾佳 - 温柔亲切","description":"适合客服场景"},{"value":"2","label":"艾彤 - 活力甜美","description":"适合营销推广"},{"value":"3","label":"小云 - 知性优雅","description":"适合教育培训"},{"value":"4","label":"艾诚 - 专业沉稳","description":"适合商务场景"},{"value":"5","label":"艾伟 - 温和亲切","description":"适合内容讲解"}],"orderNo":30},
    {"key":"gender","label":"性别","valueType":"ENUM","required":false,"options":[{"value":"female","label":"女性"},{"value":"male","label":"男性"},{"value":"neutral","label":"中性"}],"orderNo":40},
    {"key":"age","label":"年龄","valueType":"INTEGER","required":false,"validation":{"min":18,"max":65},"orderNo":50}
  ],"documents":[],"estimatedCredits":50,"uiConfig":{"layout":"vertical","sections":[{"title":"基础信息","fields":["appearance_prompt"]},{"title":"参考图片（可选）","fields":["reference_image"]},{"title":"音色配置","fields":["preset_voice"]},{"title":"外观设置（可选）","fields":["gender","age"]}],"costEstimate":{"enabled":true,"message":"预计消耗约 50 积分"}}}'::jsonb)
ON CONFLICT (code) DO NOTHING;

-- ============================================================
-- 2. 数字人营销视频生成 Blueprint
-- ============================================================

INSERT INTO aigc_project_blueprint
    (code, name, project_type_code, blueprint_version, production_mode, description, status,
     slot_template_spec, relation_spec, action_spec, deliverable_spec, process_policy, project_initialization)
VALUES
('digital-human-marketing-video', '数字人营销视频', 'digital_human_video', '1.0.0', 'standard',
 '使用数字人生成营销视频内容，支持自动脚本创作和场景编排', 'published',
 '{"slotTemplates":[
    {"templateKey":"script","stableKeyPattern":"script","objectType":"video_script","displayNamePattern":"视频脚本","orderNo":10,"defaultCount":1,"minCount":1,"maxCount":1,"defaultContractRole":"REQUIRED","semanticRole":"video_script","outputContract":"DOCUMENT_VERSION","outputContractDetails":{"format":"application/json","schema":{"type":"object","properties":{"totalDuration":{"type":"number"},"scenes":{"type":"array","items":{"type":"object","properties":{"order":{"type":"number"},"title":{"type":"string"},"content":{"type":"string"},"duration":{"type":"number"},"sceneDescription":{"type":"string"}}}}}}},"candidateSettings":{"maxCandidates":3,"allowRegeneration":true,"allowManualEdit":true}},
    {"templateKey":"video","stableKeyPattern":"video","objectType":"video_deliverable","displayNamePattern":"数字人视频","orderNo":20,"defaultCount":1,"minCount":1,"maxCount":1,"defaultContractRole":"REQUIRED","semanticRole":"marketing_video","outputContract":"MEDIA_VERSION","outputContractDetails":{"format":"video/mp4","minDuration":5,"maxDuration":600,"codec":"h264","maxFileSize":104857600},"candidateSettings":{"maxCandidates":2,"allowRegeneration":true},"readinessCriteria":{"dependencies":["script"],"policy":"after_dependency"}}
  ]}'::jsonb,
 '{"relations":[
    {"sourceTemplateKey":"script","targetTemplateKey":"video","type":"derives"}
  ]}'::jsonb,
 '{"actions":[
    {"actionKey":"generate_script","targetTemplateKey":"script","isDefault":true,"boundExecutionId":"aigc_text_generation","inputMapping":{"prompt":"创作一个 {{target_duration}} 秒的营销视频脚本，主题：{{video_topic}}\n\n视频风格：{{video_style}}\n数字人信息：\n- 名称：{{digital_human.name}}\n- 性别：{{digital_human.appearance.gender}}\n- 音色：{{digital_human.voiceConfig.voiceName}}\n\n要求：\n1. 根据时长自动分段（单段不超过 15 秒）\n2. 每段包含：顺序、标题、内容、时长、场景描述\n3. 语言简洁有力，适合数字人播报\n4. 内容符合视频风格定位\n5. 总时长尽量接近目标时长\n\n返回严格的 JSON 格式：\n{\n  \"totalDuration\": 数字,\n  \"scenes\": [\n    {\n      \"order\": 1,\n      \"title\": \"开场\",\n      \"content\": \"具体的播报文案\",\n      \"duration\": 15,\n      \"sceneDescription\": \"场景描述（如：正面特写、微笑表情）\"\n    }\n  ]\n}\n\n注意：\n- 每段 content 字数要与 duration 匹配（约 3-4 字/秒）\n- 不要包含 markdown 代码块标记\n- 直接返回 JSON 对象","model":"claude-sonnet-5","temperature":0.8,"maxTokens":2000,"responseFormat":"json"},"outputMapping":{"content":"{{execution.output.text}}","metadata":{"generatedAt":"{{execution.completedAt}}","model":"{{execution.model}}"}},"errorHandling":{"retryPolicy":{"maxRetries":2},"fallbackMessage":"脚本生成失败，请调整主题后重试"}},
    {"actionKey":"generate_video_simple","targetTemplateKey":"video","condition":"{{target_duration}} <= 15","boundExecutionId":"aliyun_video_generation","inputMapping":{"prompt":"数字人播报视频，内容：{{script.adoptedVersion.content.scenes[0].content}}","referenceImage":"{{digital_human.avatarImageUrl}}","voiceConfig":{"provider":"aliyun","voiceId":"{{digital_human.voiceConfig.presetVoiceId}}","speechRate":"{{digital_human.voiceConfig.speechRate}}","pitchRate":"{{digital_human.voiceConfig.pitchRate}}","volume":"{{digital_human.voiceConfig.volume}}"},"duration":"{{target_duration}}","aspectRatio":"{{aspect_ratio}}","resolution":"1080p"},"outputMapping":{"url":"{{execution.output.videoUrl}}","duration":"{{execution.output.duration}}","metadata":{"generationMethod":"simple","provider":"aliyun"}},"costTracking":{"operation":"simple_video","baseCredits":20,"perSecondCredits":5}},
    {"actionKey":"generate_video_hyperframes","targetTemplateKey":"video","condition":"{{target_duration}} > 15","boundExecutionId":"hyperframes_integration","inputMapping":{"digitalHumanId":"{{digital_human.id}}","avatarImageUrl":"{{digital_human.avatarImageUrl}}","voiceConfig":"{{digital_human.voiceConfig}}","scenes":"{{script.adoptedVersion.content.scenes}}","aspectRatio":"{{aspect_ratio}}","resolution":"1080p","globalConfig":{"backgroundMusic":{"enabled":false},"brandElements":{"logo":null,"watermark":null}}},"outputMapping":{"url":"{{execution.output.videoUrl}}","duration":"{{execution.output.duration}}","metadata":{"generationMethod":"complex","provider":"hyperframes","sceneCount":"{{script.adoptedVersion.content.scenes.length}}"}},"costTracking":{"operation":"complex_video","baseCredits":50,"perSecondCredits":10,"perSceneCredits":20}}
  ]}'::jsonb,
 '{"sets":[
    {"setTemplateKey":"marketing_video_package","label":"营销视频包","description":"包含脚本和视频的完整交付物","allowedSlotTemplateKeys":["script","video"],"completionMode":"ALL_REQUIRED","reviewMode":"NONE","publicationPolicy":"REQUIRED","postDeliveryAction":{"type":"update_digital_human_stats","endpoint":"POST /api/digital-humans/{{digital_human.id}}/usage-stats","inputMapping":{"videoCount":1,"duration":"{{video.adoptedVersion.metadata.duration}}","creditsConsumed":"{{project.totalCreditsConsumed}}"},"successMessage":"视频已生成！[下载视频]({{video.adoptedVersion.url}})"}}
  ]}'::jsonb,
 '{"reviewRequired":false,"publicationPolicy":"REQUIRED","autoActionKeys":["generate_script"],"confirmationGateKeys":["script.adopt"]}'::jsonb,
 '{"fields":[
    {"key":"digital_human","label":"选择数字人","valueType":"REFERENCE","referenceType":"digital_human","required":true,"validation":{"resourceType":"digital_human","status":"active"},"orderNo":10},
    {"key":"video_topic","label":"视频主题","description":"描述视频要表达的主题内容","valueType":"TEXTAREA","required":true,"placeholder":"介绍我们的 SaaS 产品核心功能和优势","validation":{"minLength":10,"maxLength":500},"orderNo":20},
    {"key":"target_duration","label":"目标时长（秒）","description":"单段最大 15 秒，总长最大 600 秒（10 分钟）","valueType":"INTEGER","required":true,"defaultValue":30,"validation":{"min":5,"max":600},"orderNo":30},
    {"key":"video_style","label":"视频风格","valueType":"ENUM","required":true,"defaultValue":"professional","options":[{"value":"professional","label":"专业","description":"正式、严谨、商务场景"},{"value":"casual","label":"休闲","description":"轻松、自然、日常交流"},{"value":"energetic","label":"活力","description":"热情、激昂、激励场景"}],"orderNo":40},
    {"key":"aspect_ratio","label":"画面比例","valueType":"ENUM","required":true,"defaultValue":"16:9","options":[{"value":"16:9","label":"横屏 16:9","description":"适合电脑、电视播放"},{"value":"9:16","label":"竖屏 9:16","description":"适合手机、短视频平台"},{"value":"1:1","label":"方形 1:1","description":"适合社交媒体"}],"orderNo":50}
  ],"documents":[],"uiConfig":{"layout":"vertical","sections":[{"title":"选择数字人","fields":["digital_human"]},{"title":"视频内容","fields":["video_topic","target_duration"]},{"title":"视频配置","fields":["video_style","aspect_ratio"]}],"costEstimate":{"enabled":true,"formula":"if (target_duration <= 15) { return 20 + target_duration * 5 } else { const scenes = Math.ceil(target_duration / 15); return 50 + target_duration * 10 + scenes * 20 }"},"customEditors":{"scene-editor":{"type":"json-form","schema":{"type":"array","items":{"type":"object","properties":{"title":{"type":"string","label":"场景标题"},"content":{"type":"string","label":"播报内容","widget":"textarea"},"duration":{"type":"number","label":"时长（秒）"},"sceneDescription":{"type":"string","label":"场景描述","optional":true}}}},"features":["reorder","add-scene","delete-scene","preview-scene"]}}}}'::jsonb)
ON CONFLICT (code) DO NOTHING;

-- ============================================================
-- 项目类型配置（如果不存在）
-- ============================================================

INSERT INTO aigc_project_type (code, name, description, status)
VALUES
    ('digital_human_asset', '数字人资产', '数字人形象生成', 'published'),
    ('digital_human_video', '数字人视频', '数字人营销视频生成', 'published')
ON CONFLICT (code) DO NOTHING;

-- ============================================================
-- 权限配置
-- ============================================================

INSERT INTO sys_permission_code (name, code, module, resource, action, status)
VALUES
    ('创建数字人资产', 'aigc:digital-human-asset:create', 'aigc', 'digital-human-asset', 'create', 0),
    ('创建数字人视频', 'aigc:digital-human-video:create', 'aigc', 'digital-human-video', 'create', 0),
    ('查看数字人', 'digital-human:read', 'digital-human', 'digital-human', 'read', 0),
    ('创建数字人', 'digital-human:create', 'digital-human', 'digital-human', 'create', 0),
    ('更新数字人', 'digital-human:update', 'digital-human', 'digital-human', 'update', 0),
    ('删除数字人', 'digital-human:delete', 'digital-human', 'digital-human', 'delete', 0),
    ('切换数字人音色', 'digital-human:change-voice', 'digital-human', 'digital-human', 'change-voice', 0),
    ('关联助理', 'digital-human:link-assistant', 'digital-human', 'digital-human', 'link-assistant', 0)
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

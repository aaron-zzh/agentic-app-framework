-- ============================================================
-- Content Studio 字典、内置配置与权限种子
-- ============================================================

-- ---------------- 字典类型 ----------------
INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('内容项目状态', 'content_project_status', 0, 'Content Studio 项目生命周期状态'),
('内容项目类型', 'content_project_type', 0, 'Content Studio 项目业务目标类型'),
('内容生产模式', 'content_production_mode', 0, '标准、短剧或漫剧生产模式'),
('内容生成模式', 'content_generation_mode', 0, '手动或自动生成模式'),
('内容对象类型', 'content_object_type', 0, '项目图谱对象类型'),
('内容对象状态', 'content_object_status', 0, '项目图谱对象状态'),
('内容对象来源', 'content_object_source', 0, '项目图谱对象来源'),
('内容关系类型', 'content_relation_type', 0, '项目图谱语义关系类型'),
('内容关系图层', 'content_relation_layer', 0, '项目图谱关系图层'),
('内容执行状态', 'content_execution_status', 0, '内容动作执行状态'),
('内容执行目标类型', 'content_execution_target_type', 0, 'Agent、Tool 或 Workflow'),
('品牌资料类型', 'content_brand_profile_kind', 0, '品牌/IP 资料类型'),
('内容配置状态', 'content_config_status', 0, '蓝图、扩展和渠道配置状态'),
('内容渠道', 'content_channel', 0, '内容发布渠道'),
('品牌资料引用范围', 'content_profile_ref_scope', 0, '项目品牌资料主引用或辅助引用')
ON CONFLICT DO NOTHING;

-- ---------------- 字典数据 ----------------
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('content_project_status', '草稿', 'draft', 1, 'default'),
('content_project_status', '进行中', 'in_progress', 2, 'primary'),
('content_project_status', '审核中', 'reviewing', 3, 'warning'),
('content_project_status', '已完成', 'completed', 4, 'success'),
('content_project_status', '已归档', 'archived', 5, 'info')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('content_project_type', '新品推广', 'new_product', 1, 'primary'),
('content_project_type', '活动促销', 'promotion', 2, 'danger'),
('content_project_type', '品牌视觉', 'brand_visual', 3, 'warning'),
('content_project_type', '门店宣传', 'store', 4, 'success'),
('content_project_type', '社媒内容', 'social', 5, 'info'),
('content_project_type', '个人 IP 内容', 'personal_ip', 6, 'primary'),
('content_project_type', '系列叙事内容', 'narrative_series', 7, 'default')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('content_production_mode', '标准', 'standard', 1, 'primary'),
('content_production_mode', '短剧', 'short_drama', 2, 'warning'),
('content_production_mode', '漫剧', 'motion_comic', 3, 'info'),
('content_generation_mode', '手动', 'manual', 1, 'default'),
('content_generation_mode', '自动', 'auto', 2, 'primary')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('content_object_type', '简报', 'brief', 1, 'default'),
('content_object_type', '创意方向', 'creative_concept', 2, 'primary'),
('content_object_type', '内容包', 'deliverable_set', 3, 'success'),
('content_object_type', '图片交付物', 'image_deliverable', 4, 'warning'),
('content_object_type', '视频交付物', 'video_deliverable', 5, 'danger'),
('content_object_type', '文案交付物', 'copy_deliverable', 6, 'info'),
('content_object_type', '分集', 'episode', 7, 'primary'),
('content_object_type', '场次', 'scene', 8, 'primary'),
('content_object_type', '镜头', 'shot', 9, 'primary'),
('content_object_type', '镜头关键帧', 'shot_keyframe', 10, 'warning'),
('content_object_type', '审核', 'review', 11, 'success'),
('content_object_type', '灵感板', 'inspiration_board', 12, 'info'),
('content_object_type', '楼盘资料', 'property_subject', 13, 'default'),
('content_object_type', '主张证据', 'claim_evidence', 14, 'default')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('content_object_status', '空槽位', 'empty', 1, 'default'),
('content_object_status', '草稿', 'draft', 2, 'info'),
('content_object_status', '待确认', 'pending_confirm', 3, 'warning'),
('content_object_status', '已采用', 'adopted', 4, 'primary'),
('content_object_status', '已阻断', 'blocked', 5, 'danger'),
('content_object_status', '已完成', 'done', 6, 'success'),
('content_object_source', '蓝图', 'blueprint', 1, 'primary'),
('content_object_source', '用户', 'user', 2, 'success'),
('content_object_source', '助手', 'assistant', 3, 'info'),
('content_object_source', '工作流', 'workflow', 4, 'warning'),
('content_object_source', '导入', 'import', 5, 'default')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('content_relation_type', '包含', 'contains', 1, 'primary'),
('content_relation_type', '约束', 'constrains', 2, 'warning'),
('content_relation_type', '派生', 'derives', 3, 'success'),
('content_relation_type', '引用', 'references', 4, 'info'),
('content_relation_type', '组成', 'composes', 5, 'primary'),
('content_relation_type', '顺序', 'orders', 6, 'default'),
('content_relation_type', '变体', 'variant', 7, 'warning'),
('content_relation_type', '执行依赖', 'execution_depends', 8, 'danger'),
('content_relation_layer', '领域关系', 'domain', 1, 'primary'),
('content_relation_layer', '引用关系', 'reference', 2, 'info'),
('content_relation_layer', '故事顺序', 'story_order', 3, 'warning'),
('content_relation_layer', '执行依赖', 'execution', 4, 'danger')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('content_execution_status', '待执行', 'pending', 1, 'default'),
('content_execution_status', '执行中', 'running', 2, 'primary'),
('content_execution_status', '已成功', 'succeeded', 3, 'success'),
('content_execution_status', '已失败', 'failed', 4, 'danger'),
('content_execution_status', '已取消', 'canceled', 5, 'info'),
('content_execution_target_type', 'Agent', 'agent', 1, 'primary'),
('content_execution_target_type', 'Tool', 'tool', 2, 'warning'),
('content_execution_target_type', 'Workflow', 'workflow', 3, 'info')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('content_brand_profile_kind', '企业品牌', 'enterprise', 1, 'primary'),
('content_brand_profile_kind', '子品牌', 'sub_brand', 2, 'info'),
('content_brand_profile_kind', '产品线', 'product_line', 3, 'success'),
('content_brand_profile_kind', '个人 IP', 'personal_ip', 4, 'warning'),
('content_config_status', '草稿', 'draft', 1, 'default'),
('content_config_status', '验证', 'verifying', 2, 'warning'),
('content_config_status', '已发布', 'published', 3, 'success'),
('content_config_status', '已弃用', 'deprecated', 4, 'info'),
('content_config_status', '已撤回', 'withdrawn', 5, 'danger')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('content_channel', '小红书', 'xiaohongshu', 1, 'danger'),
('content_channel', '抖音', 'douyin', 2, 'default'),
('content_channel', '视频号', 'wechat_channels', 3, 'success'),
('content_channel', '公众号', 'wechat_mp', 4, 'success'),
('content_channel', '线下海报', 'offline_poster', 5, 'warning'),
('content_channel', '哔哩哔哩', 'bilibili', 6, 'primary'),
('content_profile_ref_scope', '主资料', 'primary', 1, 'primary'),
('content_profile_ref_scope', '辅助资料', 'auxiliary', 2, 'info')
ON CONFLICT DO NOTHING;

-- ---------------- 内置项目类型 ----------------
INSERT INTO cs_project_type
    (code, name, icon, description, brief_placeholder, default_channels,
     default_production_mode, quick_entry, builtin, sort_order, status)
VALUES
('new_product', '新品推广', 'sparkles', '围绕新品建立图文与视频推广内容包',
 '产品是什么？核心卖点、价格或活动信息是什么？', '["xiaohongshu","douyin"]'::jsonb,
 'standard', TRUE, TRUE, 1, 'published'),
('promotion', '活动促销', 'badge-percent', '围绕营销活动生成多渠道促销内容',
 '活动时间、优惠、门店/渠道和目标人群是什么？', '["xiaohongshu","douyin","wechat_mp"]'::jsonb,
 'standard', TRUE, TRUE, 2, 'published'),
('brand_visual', '品牌视觉', 'palette', '更新品牌视觉和渠道视觉交付物',
 '希望更新哪些视觉元素？哪些必须保持不变？', '["offline_poster"]'::jsonb,
 'standard', TRUE, TRUE, 3, 'published'),
('store', '门店宣传', 'store', '为门店到店转化创建宣传内容包',
 '门店位置、主推服务、到店理由和活动信息是什么？', '["xiaohongshu","douyin","offline_poster"]'::jsonb,
 'standard', TRUE, TRUE, 4, 'published'),
('social', '社媒内容', 'megaphone', '持续生产社交媒体内容',
 '本周想传播什么主题？面向谁？希望用户做什么？', '["xiaohongshu","wechat_channels"]'::jsonb,
 'standard', TRUE, TRUE, 5, 'published'),
('personal_ip', '个人 IP 内容', 'user-round', '围绕个人 IP 观点和故事创建内容',
 '这期想表达什么观点或故事？发布到哪里？', '["xiaohongshu","douyin"]'::jsonb,
 'standard', TRUE, TRUE, 6, 'published'),
('narrative_series', '系列叙事内容', 'clapperboard', '按分集、场次与镜头组织系列叙事内容',
 '这个系列讲什么故事？共几集？每集时长和风格是什么？', '["douyin","bilibili"]'::jsonb,
 'short_drama', FALSE, TRUE, 7, 'published')
ON CONFLICT DO NOTHING;

-- ---------------- 内置项目蓝图 ----------------
INSERT INTO cs_project_blueprint
    (code, name, project_type_code, blueprint_version, production_mode, description, status,
     action_keys, confirmation_gates, brief_fields)
VALUES
('new-product-standard', '新品推广标准蓝图', 'new_product', '1.0.0', 'standard', '新品推广默认内容包骨架', 'published',
 '["concept.generate","deliverable.generate","brand.validate"]'::jsonb,
 '["concept.adopt","deliverable.batch_generate","project.archive"]'::jsonb,
 '["product","sellingPoints","priceOrPromotion","audience"]'::jsonb),
('promotion-standard', '活动促销标准蓝图', 'promotion', '1.0.0', 'standard', '活动促销默认内容包骨架', 'published',
 '["concept.generate","deliverable.generate","brand.validate"]'::jsonb,
 '["concept.adopt","deliverable.batch_generate","project.archive"]'::jsonb,
 '["timeRange","offer","channel","audience"]'::jsonb),
('brand-visual-standard', '品牌视觉标准蓝图', 'brand_visual', '1.0.0', 'standard', '品牌视觉默认内容包骨架', 'published',
 '["concept.generate","deliverable.generate","brand.validate"]'::jsonb,
 '["concept.adopt","deliverable.batch_generate","project.archive"]'::jsonb,
 '["visualElements","preservedElements","style"]'::jsonb),
('store-standard', '门店宣传标准蓝图', 'store', '1.0.0', 'standard', '门店宣传默认内容包骨架', 'published',
 '["concept.generate","deliverable.generate","brand.validate"]'::jsonb,
 '["concept.adopt","deliverable.batch_generate","project.archive"]'::jsonb,
 '["location","services","visitReason","promotion"]'::jsonb),
('social-standard', '社媒内容标准蓝图', 'social', '1.0.0', 'standard', '社媒内容默认内容包骨架', 'published',
 '["concept.generate","deliverable.generate","brand.validate"]'::jsonb,
 '["concept.adopt","deliverable.batch_generate","project.archive"]'::jsonb,
 '["topic","audience","callToAction"]'::jsonb),
('personal-ip-standard', '个人 IP 内容标准蓝图', 'personal_ip', '1.0.0', 'standard', '个人 IP 内容默认内容包骨架', 'published',
 '["concept.generate","deliverable.generate","brand.validate"]'::jsonb,
 '["concept.adopt","deliverable.batch_generate","project.archive"]'::jsonb,
 '["viewpoint","story","channels"]'::jsonb),
('narrative-series-short-drama', '系列短剧蓝图', 'narrative_series', '1.0.0', 'short_drama', '系列叙事短剧默认骨架', 'published',
 '["story.plan","episode.generate","shot.generate","video.generate"]'::jsonb,
 '["story.adopt","video.batch_generate","project.archive"]'::jsonb,
 '["story","episodeCount","episodeDuration","style"]'::jsonb)
ON CONFLICT DO NOTHING;

-- ---------------- 内置渠道规格 ----------------
INSERT INTO cs_channel_spec
    (code, name, spec_version, aspect_ratio, width, height, max_duration_seconds,
     copy_structure, required_disclaimers, export_format, sort_order, status)
VALUES
('xiaohongshu', '小红书', '1.0.0', '3:4', 1080, 1440, 300,
 '{"parts":["title","body","tags","cta"]}'::jsonb, NULL, 'png,jpg,mp4,txt', 1, 'published'),
('douyin', '抖音', '1.0.0', '9:16', 1080, 1920, 300,
 '{"parts":["hook","body","cta","tags"]}'::jsonb, NULL, 'mp4,png,txt', 2, 'published'),
('wechat_channels', '视频号', '1.0.0', '9:16', 1080, 1920, 300,
 '{"parts":["title","body","cta"]}'::jsonb, NULL, 'mp4,png,txt', 3, 'published'),
('wechat_mp', '公众号', '1.0.0', NULL, 900, NULL, NULL,
 '{"parts":["title","lead","sections","cta"]}'::jsonb, NULL, 'html,md,png', 4, 'published'),
('offline_poster', '线下海报', '1.0.0', '2:3', 2480, 3508, NULL,
 '{"parts":["headline","selling_points","cta","disclaimer"]}'::jsonb, NULL, 'png,pdf', 5, 'published'),
('bilibili', '哔哩哔哩', '1.0.0', '16:9', 1920, 1080, 7200,
 '{"parts":["title","description","chapters","tags"]}'::jsonb, NULL, 'mp4,png,txt', 6, 'published')
ON CONFLICT DO NOTHING;

-- ---------------- 房地产行业扩展 ----------------
INSERT INTO cs_domain_extension
    (code, name, extension_version, industry, region, language, status,
     profile_schema_ext, object_definitions, knowledge_requirements, rule_sets,
     validators, role_recommendations, action_constraints, channel_overrides)
VALUES
('real-estate-cn', '中国房地产内容扩展', '1.0.0', 'real_estate', 'CN', 'zh-CN', 'published',
 '{"fields":["developer","propertyName","salesOffice"]}'::jsonb,
 '{"objects":["property_subject","claim_evidence"]}'::jsonb,
 '{"required":["license","priceSheet","promotionTerms"]}'::jsonb,
 '{"hard":["claimsRequireEvidence","plannedFacilitiesMustBeMarked","expiredOffersForbidden"]}'::jsonb,
 '["property.fact.validate","claim.validate"]'::jsonb,
 '["real_estate_planner","compliance_reviewer"]'::jsonb,
 '{"confirm":["license","price","promotion","planningStatus"]}'::jsonb,
 '{}'::jsonb)
ON CONFLICT DO NOTHING;

-- ---------------- 资源权限码 ----------------
WITH resources(resource, resource_name) AS (
    VALUES
        ('brand-profile', '品牌/IP 资料'),
        ('project-type', '项目类型'),
        ('blueprint', '项目蓝图'),
        ('domain-extension', '行业扩展'),
        ('channel-spec', '渠道规格'),
        ('project', '内容项目'),
        ('project-profile-ref', '项目资料引用'),
        ('project-object', '项目对象'),
        ('project-relation', '项目关系'),
        ('execution-run', '执行记录'),
        ('snippet', '创作片段')
), actions(action, action_name) AS (
    VALUES
        ('read', '读取'),
        ('create', '创建'),
        ('update', '更新'),
        ('delete', '删除'),
        ('export', '导出'),
        ('reference', '引用')
)
INSERT INTO sys_permission_code (name, code, module, resource, action, status)
SELECT resource_name || action_name,
       'content:' || resource || ':' || action,
       'content', resource, action, 0
FROM resources
CROSS JOIN actions
ON CONFLICT (code) DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission_code permission ON permission.module = 'content'
WHERE role.code IN ('member', 'org_admin', 'admin', 'super_admin')
ON CONFLICT DO NOTHING;

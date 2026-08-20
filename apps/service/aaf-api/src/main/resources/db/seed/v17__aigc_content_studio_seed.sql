-- ============================================================
-- AIGC 统一字典、内置配置与权限种子
-- 依赖 v7__aigc_schema.sql；只写最终 AIGC 字典、配置与权限。
-- ============================================================

-- ============================================================
-- 字典类型
-- ============================================================

INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('AIGC 项目状态', 'aigc_project_status', 0, '项目生命周期状态'),
('AIGC 项目类型', 'aigc_project_type', 0, '项目业务目标类型'),
('AIGC 生产模式', 'aigc_production_mode', 0, '标准、短剧或漫剧生产模式'),
('AIGC 生成模式', 'aigc_generation_mode', 0, '手动或自动生成模式'),
('AIGC 对象类型', 'aigc_object_type', 0, '项目图谱对象类型'),
('AIGC 对象状态', 'aigc_object_status', 0, '项目图谱对象状态'),
('AIGC 对象来源', 'aigc_object_source', 0, '项目图谱对象来源'),
('AIGC 关系类型', 'aigc_relation_type', 0, '项目图谱语义关系类型'),
('AIGC 关系图层', 'aigc_relation_layer', 0, '项目图谱关系图层'),
('AIGC 执行状态', 'aigc_execution_status', 0, '统一执行状态'),
('AIGC 执行目标类型', 'aigc_execution_target_type', 0, 'Agent、Tool 或 Workflow'),
('AIGC 品牌资料类型', 'aigc_brand_profile_kind', 0, '品牌/IP 资料类型'),
('AIGC 配置状态', 'aigc_config_status', 0, '类型、蓝图、扩展和渠道配置状态'),
('AIGC 渠道', 'aigc_channel', 0, '内容发布渠道'),
('AIGC 品牌资料引用范围', 'aigc_profile_ref_scope', 0, '项目品牌资料主引用或辅助引用'),
('AIGC 对象版本状态', 'aigc_object_version_status', 0, '对象候选与采用状态'),
('AIGC 作品状态', 'aigc_work_status', 0, '作品收录、发布和归档状态'),
('AIGC 作品可见范围', 'aigc_work_visibility', 0, '作品个人、工作区或公开范围'),
('AIGC 发布状态', 'aigc_publication_status', 0, '渠道发布生命周期状态'),
('AIGC 时间线状态', 'aigc_timeline_status', 0, '轻时间线编辑和归档状态'),
('AIGC 时间线轨道类型', 'aigc_timeline_track_type', 0, '视频、语音、音乐、字幕和叠加轨道'),
('文案输出语言', 'ai.copywriting.output-locale', 0, '文案生成的受控输出语言'),
('AIGC 动作', 'aigc_action_key', 0, '内置动作展示标签')
ON CONFLICT DO NOTHING;

-- ============================================================
-- 字典数据
-- ============================================================

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('ai.copywriting.output-locale', '英文', 'EN', 1, 'primary'),
('ai.copywriting.output-locale', '日文', 'JA', 2, 'info'),
('ai.copywriting.output-locale', '韩文', 'KO', 3, 'success'),
('ai.copywriting.output-locale', '法文', 'FR', 4, 'warning'),
('ai.copywriting.output-locale', '西班牙文', 'ES', 5, 'danger')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('aigc_project_status', '草稿', 'draft', 1, 'default'),
('aigc_project_status', '进行中', 'in_progress', 2, 'primary'),
('aigc_project_status', '审核中', 'reviewing', 3, 'warning'),
('aigc_project_status', '交付中', 'delivering', 4, 'primary'),
('aigc_project_status', '已完成', 'completed', 5, 'success'),
('aigc_project_status', '已归档', 'archived', 6, 'info')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('aigc_project_type', '新品推广', 'new_product', 1, 'primary'),
('aigc_project_type', '活动促销', 'promotion', 2, 'danger'),
('aigc_project_type', '品牌视觉', 'brand_visual', 3, 'warning'),
('aigc_project_type', '门店宣传', 'store', 4, 'success'),
('aigc_project_type', '社媒内容', 'social', 5, 'info'),
('aigc_project_type', '个人 IP 内容', 'personal_ip', 6, 'primary'),
('aigc_project_type', 'AI 博客', 'blog_content', 7, 'info'),
('aigc_project_type', '系列叙事内容', 'narrative_series', 8, 'default')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('aigc_production_mode', '标准', 'standard', 1, 'primary'),
('aigc_production_mode', '短剧', 'short_drama', 2, 'warning'),
('aigc_production_mode', '漫剧', 'motion_comic', 3, 'info'),
('aigc_generation_mode', '手动', 'manual', 1, 'default'),
('aigc_generation_mode', '自动', 'auto', 2, 'primary')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('aigc_object_type', '简报', 'brief', 1, 'default'),
('aigc_object_type', '创意方向', 'creative_concept', 2, 'primary'),
('aigc_object_type', '内容包', 'deliverable_set', 3, 'success'),
('aigc_object_type', '图片交付物', 'image_deliverable', 4, 'warning'),
('aigc_object_type', '视频交付物', 'video_deliverable', 5, 'danger'),
('aigc_object_type', '文案交付物', 'copy_deliverable', 6, 'info'),
('aigc_object_type', '文章交付物', 'article_deliverable', 7, 'info'),
('aigc_object_type', '分集', 'episode', 8, 'primary'),
('aigc_object_type', '场次', 'scene', 9, 'primary'),
('aigc_object_type', '镜头', 'shot', 10, 'primary'),
('aigc_object_type', '镜头关键帧', 'shot_keyframe', 11, 'warning'),
('aigc_object_type', '角色', 'character', 12, 'primary'),
('aigc_object_type', '道具', 'prop', 13, 'default'),
('aigc_object_type', '审核', 'review', 14, 'success'),
('aigc_object_type', '画布节点', 'canvas_board', 15, 'info'),
('aigc_object_type', '楼盘资料', 'property_subject', 16, 'default'),
('aigc_object_type', '主张证据', 'claim_evidence', 17, 'default'),
('aigc_object_type', '选题', 'topic', 18, 'primary'),
('aigc_object_type', '来源资料集', 'source_material_set', 19, 'default'),
('aigc_object_type', '文章提纲', 'article_outline', 20, 'info'),
('aigc_object_type', 'SEO 元数据', 'seo_metadata', 21, 'warning'),
('aigc_object_type', '分发变体', 'distribution_variant', 22, 'success')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('aigc_object_status', '空槽位', 'empty', 1, 'default'),
('aigc_object_status', '草稿', 'draft', 2, 'info'),
('aigc_object_status', '待确认', 'pending_confirm', 3, 'warning'),
('aigc_object_status', '已采用', 'adopted', 4, 'primary'),
('aigc_object_status', '已阻断', 'blocked', 5, 'danger'),
('aigc_object_status', '已完成', 'done', 6, 'success'),
('aigc_object_source', '蓝图', 'blueprint', 1, 'primary'),
('aigc_object_source', '用户', 'user', 2, 'success'),
('aigc_object_source', '助手', 'assistant', 3, 'info'),
('aigc_object_source', '工作流', 'workflow', 4, 'warning'),
('aigc_object_source', '导入', 'import', 5, 'default')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('aigc_relation_type', '包含', 'contains', 1, 'primary'),
('aigc_relation_type', '约束', 'constrains', 2, 'warning'),
('aigc_relation_type', '派生', 'derives', 3, 'success'),
('aigc_relation_type', '引用', 'references', 4, 'info'),
('aigc_relation_type', '组成', 'composes', 5, 'primary'),
('aigc_relation_type', '顺序', 'orders', 6, 'default'),
('aigc_relation_type', '变体', 'variant', 7, 'warning'),
('aigc_relation_type', '执行依赖', 'execution_depends', 8, 'danger'),
('aigc_relation_layer', '领域关系', 'domain', 1, 'primary'),
('aigc_relation_layer', '引用关系', 'reference', 2, 'info'),
('aigc_relation_layer', '故事顺序', 'story_order', 3, 'warning'),
('aigc_relation_layer', '执行依赖', 'execution', 4, 'danger')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('aigc_execution_status', '待执行', 'pending', 1, 'default'),
('aigc_execution_status', '执行中', 'running', 2, 'primary'),
('aigc_execution_status', '已成功', 'succeeded', 3, 'success'),
('aigc_execution_status', '已失败', 'failed', 4, 'danger'),
('aigc_execution_status', '已取消', 'canceled', 5, 'info'),
('aigc_execution_target_type', 'Agent', 'agent', 1, 'primary'),
('aigc_execution_target_type', 'Tool', 'tool', 2, 'warning'),
('aigc_execution_target_type', 'Workflow', 'workflow', 3, 'info')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('aigc_brand_profile_kind', '企业品牌', 'enterprise', 1, 'primary'),
('aigc_brand_profile_kind', '子品牌', 'sub_brand', 2, 'info'),
('aigc_brand_profile_kind', '产品线', 'product_line', 3, 'success'),
('aigc_brand_profile_kind', '个人 IP', 'personal_ip', 4, 'warning'),
('aigc_config_status', '草稿', 'draft', 1, 'default'),
('aigc_config_status', '验证', 'verifying', 2, 'warning'),
('aigc_config_status', '已发布', 'published', 3, 'success'),
('aigc_config_status', '已弃用', 'deprecated', 4, 'info'),
('aigc_config_status', '已撤回', 'withdrawn', 5, 'danger')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('aigc_channel', '小红书', 'xiaohongshu', 1, 'danger'),
('aigc_channel', '抖音', 'douyin', 2, 'default'),
('aigc_channel', '视频号', 'wechat_channels', 3, 'success'),
('aigc_channel', '公众号', 'wechat_mp', 4, 'success'),
('aigc_channel', '线下海报', 'offline_poster', 5, 'warning'),
('aigc_channel', '哔哩哔哩', 'bilibili', 6, 'primary'),
('aigc_channel', '网站/博客', 'website', 7, 'info'),
('aigc_profile_ref_scope', '主资料', 'primary', 1, 'primary'),
('aigc_profile_ref_scope', '辅助资料', 'auxiliary', 2, 'info')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('aigc_object_version_status', '候选', 'candidate', 1, 'warning'),
('aigc_object_version_status', '已采用', 'adopted', 2, 'success'),
('aigc_object_version_status', '已否决', 'rejected', 3, 'danger'),
('aigc_object_version_status', '已被取代', 'superseded', 4, 'info'),
('aigc_work_status', '已收录', 'collected', 1, 'primary'),
('aigc_work_status', '已发布', 'published', 2, 'success'),
('aigc_work_status', '已归档', 'archived', 3, 'info'),
('aigc_work_visibility', '仅自己', 'PRIVATE', 1, 'default'),
('aigc_work_visibility', '工作区', 'WORKSPACE', 2, 'primary'),
('aigc_work_visibility', '公开', 'PUBLIC', 3, 'success'),
('aigc_publication_status', '待发布', 'pending', 1, 'default'),
('aigc_publication_status', '已排期', 'scheduled', 2, 'info'),
('aigc_publication_status', '发布中', 'publishing', 3, 'primary'),
('aigc_publication_status', '已发布', 'published', 4, 'success'),
('aigc_publication_status', '失败', 'failed', 5, 'danger'),
('aigc_publication_status', '已取消', 'canceled', 6, 'info'),
('aigc_timeline_status', '草稿', 'draft', 1, 'default'),
('aigc_timeline_status', '已归档', 'archived', 2, 'info'),
('aigc_timeline_track_type', '视频', 'VIDEO', 1, 'primary'),
('aigc_timeline_track_type', '语音', 'VOICE', 2, 'info'),
('aigc_timeline_track_type', '音乐', 'MUSIC', 3, 'success'),
('aigc_timeline_track_type', '字幕', 'SUBTITLE', 4, 'warning'),
('aigc_timeline_track_type', '叠加层', 'OVERLAY', 5, 'default'),
('aigc_action_key', '完善简报', 'brief.refine', 1, 'default'),
('aigc_action_key', '生成创意方向', 'concept.generate', 2, 'primary'),
('aigc_action_key', '生成文案', 'copy.generate', 3, 'info'),
('aigc_action_key', '生成图片', 'image.generate', 4, 'warning'),
('aigc_action_key', '局部修改图片', 'image.edit', 5, 'warning'),
('aigc_action_key', '重新生成交付物', 'deliverable.regenerate', 6, 'primary'),
('aigc_action_key', '生成文章提纲', 'outline.generate', 7, 'info'),
('aigc_action_key', '撰写文章草稿', 'article.draft', 8, 'primary'),
('aigc_action_key', '改写文章', 'article.rewrite', 9, 'warning'),
('aigc_action_key', '优化文章 SEO', 'article.seo_optimize', 10, 'success')
ON CONFLICT DO NOTHING;

-- ============================================================
-- 内置项目类型
-- ============================================================

INSERT INTO aigc_project_type
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
('blog_content', 'AI 博客', 'notebook-pen', '围绕选题、来源、提纲、正文、SEO 和发布建立长内容生产项目',
 '想写什么主题？目标读者、核心观点、关键词和参考资料是什么？', '["website","wechat_mp"]'::jsonb,
 'standard', TRUE, TRUE, 7, 'published'),
('narrative_series', '系列叙事内容', 'clapperboard', '按分集、场次与镜头组织系列叙事内容',
 '这个系列讲什么故事？共几集？每集时长和风格是什么？', '["douyin","bilibili"]'::jsonb,
 'short_drama', FALSE, TRUE, 8, 'published')
ON CONFLICT DO NOTHING;

-- ============================================================
-- 内置项目蓝图
-- ============================================================

INSERT INTO aigc_project_blueprint
    (code, name, project_type_code, blueprint_version, production_mode, description, status,
     action_keys, confirmation_gates, brief_fields)
VALUES
('new-product-standard', '新品推广标准蓝图', 'new_product', '1.0.0', 'standard', '新品推广默认内容包骨架', 'published',
 '["brief.refine","concept.generate","copy.generate","image.generate","deliverable.regenerate"]'::jsonb,
 '["concept.adopt","deliverable.batch_generate","project.archive"]'::jsonb,
 '["product","sellingPoints","priceOrPromotion","audience"]'::jsonb),
('promotion-standard', '活动促销标准蓝图', 'promotion', '1.0.0', 'standard', '活动促销默认内容包骨架', 'published',
 '["brief.refine","concept.generate","copy.generate","image.generate","deliverable.regenerate"]'::jsonb,
 '["concept.adopt","deliverable.batch_generate","project.archive"]'::jsonb,
 '["timeRange","offer","channel","audience"]'::jsonb),
('brand-visual-standard', '品牌视觉标准蓝图', 'brand_visual', '1.0.0', 'standard', '品牌视觉默认内容包骨架', 'published',
 '["brief.refine","concept.generate","image.generate","image.edit","deliverable.regenerate"]'::jsonb,
 '["concept.adopt","deliverable.batch_generate","project.archive"]'::jsonb,
 '["visualElements","preservedElements","style"]'::jsonb),
('store-standard', '门店宣传标准蓝图', 'store', '1.0.0', 'standard', '门店宣传默认内容包骨架', 'published',
 '["brief.refine","concept.generate","copy.generate","image.generate","deliverable.regenerate"]'::jsonb,
 '["concept.adopt","deliverable.batch_generate","project.archive"]'::jsonb,
 '["location","services","visitReason","promotion"]'::jsonb),
('social-standard', '社媒内容标准蓝图', 'social', '1.0.0', 'standard', '社媒内容默认内容包骨架', 'published',
 '["brief.refine","concept.generate","copy.generate","image.generate","deliverable.regenerate"]'::jsonb,
 '["concept.adopt","deliverable.batch_generate","project.archive"]'::jsonb,
 '["topic","audience","callToAction"]'::jsonb),
('personal-ip-standard', '个人 IP 内容标准蓝图', 'personal_ip', '1.0.0', 'standard', '个人 IP 内容默认内容包骨架', 'published',
 '["brief.refine","concept.generate","copy.generate","image.generate","deliverable.regenerate"]'::jsonb,
 '["concept.adopt","deliverable.batch_generate","project.archive"]'::jsonb,
 '["viewpoint","story","channels"]'::jsonb),
('ai-blog-standard', 'AI 博客标准蓝图', 'blog_content', '1.0.0', 'standard', '选题、来源、提纲、正文、SEO、封面和分发变体的长内容骨架', 'published',
 '["brief.refine","outline.generate","article.draft","article.rewrite","article.seo_optimize","image.generate"]'::jsonb,
 '["outline.adopt","article.adopt","article.publish","project.archive"]'::jsonb,
 '["topic","audience","keywords","tone","sourceMaterials","channels"]'::jsonb),
('narrative-series-short-drama', '系列短剧蓝图', 'narrative_series', '1.0.0', 'short_drama', '系列叙事短剧默认骨架', 'published',
 '["brief.refine","concept.generate","copy.generate","image.generate"]'::jsonb,
 '["story.adopt","video.batch_generate","project.archive"]'::jsonb,
 '["story","episodeCount","episodeDuration","style"]'::jsonb)
ON CONFLICT DO NOTHING;

UPDATE aigc_project_blueprint
SET object_spec = '{"objects":[
      {"key":"brief","type":"brief","title":"创作简报","status":"draft","sortOrder":1},
      {"key":"topic","type":"topic","title":"文章选题","status":"empty","sortOrder":2},
      {"key":"sources","type":"source_material_set","title":"来源资料","status":"empty","sortOrder":3},
      {"key":"outline","type":"article_outline","title":"文章提纲","status":"empty","sortOrder":4},
      {"key":"article","type":"article_deliverable","title":"博客文章","status":"empty","sortOrder":5},
      {"key":"seo","type":"seo_metadata","title":"SEO 元数据","status":"empty","parentKey":"article","sortOrder":6},
      {"key":"cover","type":"image_deliverable","title":"文章封面","status":"empty","parentKey":"article","sortOrder":7},
      {"key":"variants","type":"distribution_variant","title":"渠道分发变体","status":"empty","parentKey":"article","sortOrder":8},
      {"key":"review","type":"review","title":"内容审核","status":"empty","sortOrder":9}
    ]}'::jsonb,
    relation_spec = '{"relations":[
      {"sourceKey":"brief","targetKey":"topic","type":"derives"},
      {"sourceKey":"topic","targetKey":"outline","type":"derives"},
      {"sourceKey":"sources","targetKey":"article","type":"constrains"},
      {"sourceKey":"outline","targetKey":"article","type":"derives"},
      {"sourceKey":"article","targetKey":"seo","type":"contains"},
      {"sourceKey":"article","targetKey":"cover","type":"contains"},
      {"sourceKey":"article","targetKey":"variants","type":"contains"},
      {"sourceKey":"review","targetKey":"article","type":"constrains"}
    ]}'::jsonb
WHERE code = 'ai-blog-standard' AND blueprint_version = '1.0.0';

-- ============================================================
-- 内置渠道规格
-- ============================================================

INSERT INTO aigc_channel_spec
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
 '{"parts":["title","description","chapters","tags"]}'::jsonb, NULL, 'mp4,png,txt', 6, 'published'),
('website', '网站/博客', '1.0.0', NULL, 1200, NULL, NULL,
 '{"parts":["title","summary","sections","citations","seo","cta"]}'::jsonb, NULL, 'html,md,json,png', 7, 'published')
ON CONFLICT DO NOTHING;

-- ============================================================
-- 房地产领域扩展
-- ============================================================

INSERT INTO aigc_domain_extension
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

-- ============================================================
-- 内置执行绑定
-- ============================================================

INSERT INTO aigc_execution_binding
    (action_key, target_type, target_ref, binding_version, priority,
     confirmation_required, estimated_credits, status)
VALUES
('image.generate', 'tool', 'aigc.image.generate', '1.0.0', 0, TRUE, 10.00, 'published'),
('image.edit', 'tool', 'aigc.image.edit', '1.0.0', 0, TRUE, 10.00, 'published')
ON CONFLICT DO NOTHING;

-- ============================================================
-- 内置项目类型兼容包
-- ============================================================

INSERT INTO aigc_project_type_package
    (package_version, project_type_id, blueprint_id, domain_extension_id,
     channel_spec_ids, execution_binding_ids, production_mode,
     compatibility_result, status)
SELECT blueprint.blueprint_version,
       project_type.id,
       blueprint.id,
       NULL,
       COALESCE(
           (SELECT jsonb_agg(channel_spec.id ORDER BY channel_spec.id)
            FROM aigc_channel_spec channel_spec
            WHERE channel_spec.deleted = FALSE
              AND channel_spec.status = 'published'
              AND project_type.default_channels ? channel_spec.code),
           '[]'::jsonb),
       COALESCE(
           (SELECT jsonb_agg(binding.id ORDER BY binding.id)
            FROM aigc_execution_binding binding
            WHERE binding.deleted = FALSE
              AND binding.status = 'published'
              AND blueprint.action_keys ? binding.action_key),
           '[]'::jsonb),
       blueprint.production_mode,
       jsonb_build_object(
           'compatible', TRUE,
           'projectTypeCode', project_type.code,
           'blueprintCode', blueprint.code,
           'domainExtensionCode', NULL::text,
           'channelCodes', project_type.default_channels,
           'coveredActionKeys', blueprint.action_keys),
       'published'
FROM aigc_project_type project_type
JOIN aigc_project_blueprint blueprint
  ON blueprint.project_type_code = project_type.code
 AND blueprint.production_mode = project_type.default_production_mode
 AND blueprint.status = 'published'
 AND blueprint.deleted = FALSE
WHERE project_type.status = 'published'
  AND project_type.deleted = FALSE
ON CONFLICT (project_type_id, package_version) WHERE deleted = FALSE DO NOTHING;

-- ============================================================
-- AIGC 资源权限
-- CRUD 权限与 Resource capability 对齐；业务命令单独声明。
-- ============================================================


-- ============================================================
-- 内置创作片段
-- owner_id 为空表示平台内置，只读；is_public=true 允许所有创作者复用。
-- ============================================================

INSERT INTO aigc_snippet
    (org_id, workspace_id, builtin_code, name, category, content,
     reference_media_version_ids, variable_slots, project_type_code, use_count,
     is_public, owner_id)
SELECT organization.id,
       NULL,
       seed.builtin_code,
       seed.name,
       seed.category,
       seed.content,
       '[]'::jsonb,
       NULL,
       seed.project_type_code,
       0,
       TRUE,
       NULL
FROM sys_organization organization
CROSS JOIN (VALUES
    ('cinematic-lighting', '电影感光影', '风格',
     '电影级布光，柔和体积光与自然阴影，冷暖色调平衡，层次丰富，高动态范围，画面具有叙事感',
     NULL::varchar),
    ('commercial-product-shot', '商业产品棚拍', '产品',
     '专业商业产品摄影，主体居中，材质纹理清晰，干净渐变背景，柔光箱反射，高级广告质感，细节锐利',
     'new_product'::varchar),
    ('natural-portrait', '自然人像质感', '人像',
     '自然真实的人像摄影，肤色准确，保留细腻皮肤纹理，眼神清晰，柔和轮廓光，浅景深，背景虚化自然',
     'personal_ip'::varchar),
    ('golden-ratio-composition', '黄金比例构图', '构图',
     '黄金比例构图，视觉焦点明确，前中后景层次分明，主体与留白平衡，引导线自然，画面稳定且富有张力',
     NULL::varchar),
    ('high-quality-details', '高质量细节增强', '画质',
     '高清细节，边缘干净，纹理真实，光照一致，色彩自然，避免过度锐化与塑料质感，专业级成片质量',
     NULL::varchar)
) AS seed(builtin_code, name, category, content, project_type_code)
WHERE organization.deleted = FALSE
ON CONFLICT (org_id, builtin_code) WHERE deleted = FALSE AND builtin_code IS NOT NULL
DO NOTHING;

INSERT INTO sys_permission_code (name, code, module, resource, action, status)
VALUES
    ('品牌/IP 资料读取', 'aigc:brand-profile:read', 'aigc', 'brand-profile', 'read', 0),
    ('品牌/IP 资料创建', 'aigc:brand-profile:create', 'aigc', 'brand-profile', 'create', 0),
    ('品牌/IP 资料更新', 'aigc:brand-profile:update', 'aigc', 'brand-profile', 'update', 0),
    ('品牌/IP 资料删除', 'aigc:brand-profile:delete', 'aigc', 'brand-profile', 'delete', 0),
    ('品牌/IP 资料导出', 'aigc:brand-profile:export', 'aigc', 'brand-profile', 'export', 0),
    ('项目类型读取', 'aigc:project-type:read', 'aigc', 'project-type', 'read', 0),
    ('项目类型创建', 'aigc:project-type:create', 'aigc', 'project-type', 'create', 0),
    ('项目类型更新', 'aigc:project-type:update', 'aigc', 'project-type', 'update', 0),
    ('项目类型删除', 'aigc:project-type:delete', 'aigc', 'project-type', 'delete', 0),
    ('项目类型导出', 'aigc:project-type:export', 'aigc', 'project-type', 'export', 0),
    ('项目类型兼容包读取', 'aigc:project-type-package:read', 'aigc', 'project-type-package', 'read', 0),
    ('项目类型兼容包创建', 'aigc:project-type-package:create', 'aigc', 'project-type-package', 'create', 0),
    ('项目类型兼容包更新', 'aigc:project-type-package:update', 'aigc', 'project-type-package', 'update', 0),
    ('项目类型兼容包删除', 'aigc:project-type-package:delete', 'aigc', 'project-type-package', 'delete', 0),
    ('项目类型兼容包导出', 'aigc:project-type-package:export', 'aigc', 'project-type-package', 'export', 0),
    ('项目蓝图读取', 'aigc:blueprint:read', 'aigc', 'blueprint', 'read', 0),
    ('项目蓝图创建', 'aigc:blueprint:create', 'aigc', 'blueprint', 'create', 0),
    ('项目蓝图更新', 'aigc:blueprint:update', 'aigc', 'blueprint', 'update', 0),
    ('项目蓝图删除', 'aigc:blueprint:delete', 'aigc', 'blueprint', 'delete', 0),
    ('项目蓝图导出', 'aigc:blueprint:export', 'aigc', 'blueprint', 'export', 0),
    ('领域扩展读取', 'aigc:domain-extension:read', 'aigc', 'domain-extension', 'read', 0),
    ('领域扩展创建', 'aigc:domain-extension:create', 'aigc', 'domain-extension', 'create', 0),
    ('领域扩展更新', 'aigc:domain-extension:update', 'aigc', 'domain-extension', 'update', 0),
    ('领域扩展删除', 'aigc:domain-extension:delete', 'aigc', 'domain-extension', 'delete', 0),
    ('领域扩展导出', 'aigc:domain-extension:export', 'aigc', 'domain-extension', 'export', 0),
    ('渠道规格读取', 'aigc:channel-spec:read', 'aigc', 'channel-spec', 'read', 0),
    ('渠道规格创建', 'aigc:channel-spec:create', 'aigc', 'channel-spec', 'create', 0),
    ('渠道规格更新', 'aigc:channel-spec:update', 'aigc', 'channel-spec', 'update', 0),
    ('渠道规格删除', 'aigc:channel-spec:delete', 'aigc', 'channel-spec', 'delete', 0),
    ('渠道规格导出', 'aigc:channel-spec:export', 'aigc', 'channel-spec', 'export', 0),
    ('创作项目读取', 'aigc:project:read', 'aigc', 'project', 'read', 0),
    ('创作项目更新', 'aigc:project:update', 'aigc', 'project', 'update', 0),
    ('创作项目导出', 'aigc:project:export', 'aigc', 'project', 'export', 0),
    ('执行记录读取', 'aigc:execution-run:read', 'aigc', 'execution-run', 'read', 0),
    ('执行记录导出', 'aigc:execution-run:export', 'aigc', 'execution-run', 'export', 0),
    ('执行绑定读取', 'aigc:execution-binding:read', 'aigc', 'execution-binding', 'read', 0),
    ('执行绑定创建', 'aigc:execution-binding:create', 'aigc', 'execution-binding', 'create', 0),
    ('执行绑定更新', 'aigc:execution-binding:update', 'aigc', 'execution-binding', 'update', 0),
    ('执行绑定删除', 'aigc:execution-binding:delete', 'aigc', 'execution-binding', 'delete', 0),
    ('执行绑定导出', 'aigc:execution-binding:export', 'aigc', 'execution-binding', 'export', 0),
    ('生成任务读取', 'aigc:task:read', 'aigc', 'task', 'read', 0),
    ('素材读取', 'aigc:media:read', 'aigc', 'media', 'read', 0),
    ('素材更新', 'aigc:media:update', 'aigc', 'media', 'update', 0),
    ('素材删除', 'aigc:media:delete', 'aigc', 'media', 'delete', 0),
    ('资产读取', 'aigc:asset:read', 'aigc', 'asset', 'read', 0),
    ('资产创建', 'aigc:asset:create', 'aigc', 'asset', 'create', 0),
    ('资产更新', 'aigc:asset:update', 'aigc', 'asset', 'update', 0),
    ('资产标签维护', 'aigc:asset:tag', 'aigc', 'asset', 'tag', 0),
    ('资产删除', 'aigc:asset:delete', 'aigc', 'asset', 'delete', 0),
    ('资产分类读取', 'aigc:asset-category:read', 'aigc', 'asset-category', 'read', 0),
    ('资产分类创建', 'aigc:asset-category:create', 'aigc', 'asset-category', 'create', 0),
    ('资产分类更新', 'aigc:asset-category:update', 'aigc', 'asset-category', 'update', 0),
    ('资产分类删除', 'aigc:asset-category:delete', 'aigc', 'asset-category', 'delete', 0),
    ('资产分类导出', 'aigc:asset-category:export', 'aigc', 'asset-category', 'export', 0),
    ('资产标签读取', 'aigc:asset-tag:read', 'aigc', 'asset-tag', 'read', 0),
    ('资产标签创建', 'aigc:asset-tag:create', 'aigc', 'asset-tag', 'create', 0),
    ('资产标签更新', 'aigc:asset-tag:update', 'aigc', 'asset-tag', 'update', 0),
    ('资产标签删除', 'aigc:asset-tag:delete', 'aigc', 'asset-tag', 'delete', 0),
    ('资产标签导出', 'aigc:asset-tag:export', 'aigc', 'asset-tag', 'export', 0),
    ('资产集合读取', 'aigc:asset-collection:read', 'aigc', 'asset-collection', 'read', 0),
    ('资产集合创建', 'aigc:asset-collection:create', 'aigc', 'asset-collection', 'create', 0),
    ('资产集合更新', 'aigc:asset-collection:update', 'aigc', 'asset-collection', 'update', 0),
    ('资产集合删除', 'aigc:asset-collection:delete', 'aigc', 'asset-collection', 'delete', 0),
    ('资产集合导出', 'aigc:asset-collection:export', 'aigc', 'asset-collection', 'export', 0),
    ('作品读取', 'aigc:work:read', 'aigc', 'work', 'read', 0),
    ('作品更新', 'aigc:work:update', 'aigc', 'work', 'update', 0),
    ('作品导出', 'aigc:work:export', 'aigc', 'work', 'export', 0),
    ('时间线读取', 'aigc:timeline:read', 'aigc', 'timeline', 'read', 0),
    ('时间线删除', 'aigc:timeline:delete', 'aigc', 'timeline', 'delete', 0),
    ('时间线导出', 'aigc:timeline:export', 'aigc', 'timeline', 'export', 0),
    ('创作片段读取', 'aigc:snippet:read', 'aigc', 'snippet', 'read', 0),
    ('创作片段创建', 'aigc:snippet:create', 'aigc', 'snippet', 'create', 0),
    ('创作片段更新', 'aigc:snippet:update', 'aigc', 'snippet', 'update', 0),
    ('创作片段删除', 'aigc:snippet:delete', 'aigc', 'snippet', 'delete', 0),
    ('创作片段导出', 'aigc:snippet:export', 'aigc', 'snippet', 'export', 0)
ON CONFLICT (code) WHERE deleted = FALSE DO NOTHING;

INSERT INTO sys_permission_code (name, code, module, resource, action, status)
VALUES
    ('发布项目类型兼容包', 'aigc:project-type-package:publish', 'aigc', 'project-type-package', 'publish', 0),
    ('创建创作项目', 'aigc:project:create', 'aigc', 'project', 'create', 0),
    ('执行创作项目动作', 'aigc:project:action', 'aigc', 'project', 'action', 0),
    ('采用项目对象版本', 'aigc:project:adopt', 'aigc', 'project', 'adopt', 0),
    ('执行生成动作', 'aigc:execution-run:execute', 'aigc', 'execution-run', 'execute', 0),
    ('提交生成任务', 'aigc:task:submit', 'aigc', 'task', 'submit', 0),
    ('取消生成任务', 'aigc:task:cancel', 'aigc', 'task', 'cancel', 0),
    ('维护资产集合成员', 'aigc:asset-collection:item', 'aigc', 'asset-collection', 'item', 0),
    ('创建作品', 'aigc:work:create', 'aigc', 'work', 'create', 0),
    ('发布作品', 'aigc:work:publish', 'aigc', 'work', 'publish', 0),
    ('创建时间线', 'aigc:timeline:create', 'aigc', 'timeline', 'create', 0),
    ('更新时间线编排', 'aigc:timeline:update', 'aigc', 'timeline', 'update', 0)
ON CONFLICT (code) WHERE deleted = FALSE DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission_code permission ON permission.module = 'aigc'
WHERE role.code IN ('admin', 'super_admin')
   OR (
       role.code IN ('member', 'org_admin')
       AND NOT (
           permission.resource IN (
               'project-type',
               'project-type-package',
               'blueprint',
               'domain-extension',
               'channel-spec',
               'execution-binding'
           )
           AND permission.action IN ('create', 'update', 'delete', 'publish')
       )
   )
ON CONFLICT DO NOTHING;

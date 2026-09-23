
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

-- ============================================================
-- AAF-120 养老业务模块字典与 M1 全局配置
-- 后续里程碑按字典组追加，保持每组独立分段。
-- ============================================================

-- ==================== elder_status 长者档案状态 ====================
INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('长者档案状态', 'elder_status', 0, NULL)
ON CONFLICT DO NOTHING;
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('elder_status', '启用', 'ACTIVE', 1, 'success'),
('elder_status', '停用', 'INACTIVE', 2, 'default')
ON CONFLICT DO NOTHING;

-- ==================== elder_living_arrangement 居住形态 ====================
INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('长者居住形态', 'elder_living_arrangement', 0, NULL)
ON CONFLICT DO NOTHING;
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('elder_living_arrangement', '独居', 'ALONE', 1, 'warning'),
('elder_living_arrangement', '与家人同住', 'WITH_FAMILY', 2, 'success'),
('elder_living_arrangement', '与照护人同住', 'WITH_CAREGIVER', 3, 'primary')
ON CONFLICT DO NOTHING;

-- ==================== elder_contact_relationship 联系人关系 ====================
INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('长者联系人关系', 'elder_contact_relationship', 0, NULL)
ON CONFLICT DO NOTHING;
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('elder_contact_relationship', '配偶', 'SPOUSE', 1, 'primary'),
('elder_contact_relationship', '子女', 'CHILD', 2, 'success'),
('elder_contact_relationship', '兄弟姐妹', 'SIBLING', 3, 'info'),
('elder_contact_relationship', '监护人', 'GUARDIAN', 4, 'warning'),
('elder_contact_relationship', '其他亲属', 'RELATIVE', 5, 'default'),
('elder_contact_relationship', '朋友', 'FRIEND', 6, 'default'),
('elder_contact_relationship', '其他', 'OTHER', 7, 'default')
ON CONFLICT DO NOTHING;

-- ==================== elder_authorization_scope 授权范围 ====================
INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('家属授权范围', 'elder_authorization_scope', 0, '联系人可访问的长者数据范围')
ON CONFLICT DO NOTHING;
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('elder_authorization_scope', '查看档案', 'VIEW_PROFILE', 1, 'primary'),
('elder_authorization_scope', '查看健康信息', 'VIEW_HEALTH', 2, 'danger'),
('elder_authorization_scope', '查看用药信息', 'VIEW_MEDICATION', 3, 'warning'),
('elder_authorization_scope', '查看预警', 'VIEW_ALERT', 4, 'danger'),
('elder_authorization_scope', '查看服务', 'VIEW_SERVICE', 5, 'info'),
('elder_authorization_scope', '代为预约', 'BOOK_SERVICE', 6, 'success'),
('elder_authorization_scope', '查看相册', 'VIEW_ALBUM', 7, 'primary')
ON CONFLICT DO NOTHING;

-- ==================== elder_health_item_type 健康条目类型 ====================
INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('长者健康条目类型', 'elder_health_item_type', 0, NULL)
ON CONFLICT DO NOTHING;
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('elder_health_item_type', '慢性病', 'CHRONIC_DISEASE', 1, 'warning'),
('elder_health_item_type', '过敏', 'ALLERGY', 2, 'danger'),
('elder_health_item_type', '长期用药', 'MEDICATION', 3, 'primary'),
('elder_health_item_type', '既往史', 'PAST_HISTORY', 4, 'info'),
('elder_health_item_type', '其他', 'OTHER', 5, 'default')
ON CONFLICT DO NOTHING;

-- ==================== elder_medication_plan_status 用药计划状态 ====================
INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('用药计划状态', 'elder_medication_plan_status', 0, NULL)
ON CONFLICT DO NOTHING;
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('elder_medication_plan_status', '启用', 'ACTIVE', 1, 'success'),
('elder_medication_plan_status', '停用', 'INACTIVE', 2, 'default')
ON CONFLICT DO NOTHING;

-- ==================== elder_medication_plan_source 用药计划来源 ====================
INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('用药计划来源', 'elder_medication_plan_source', 0, NULL)
ON CONFLICT DO NOTHING;
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('elder_medication_plan_source', '管理端', 'MANAGEMENT', 1, 'primary'),
('elder_medication_plan_source', '对话确认', 'CONVERSATION', 2, 'warning')
ON CONFLICT DO NOTHING;

-- ==================== elder_medication_record_result 服药结果 ====================
INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('服药记录结果', 'elder_medication_record_result', 0, NULL)
ON CONFLICT DO NOTHING;
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('elder_medication_record_result', '已服', 'TAKEN', 1, 'success'),
('elder_medication_record_result', '漏服', 'MISSED', 2, 'danger'),
('elder_medication_record_result', '跳过', 'SKIPPED', 3, 'warning')
ON CONFLICT DO NOTHING;

-- ==================== elder_medication_record_source 服药记录来源 ====================
INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('服药记录来源', 'elder_medication_record_source', 0, NULL)
ON CONFLICT DO NOTHING;
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('elder_medication_record_source', '设备确认', 'DEVICE', 1, 'success'),
('elder_medication_record_source', '联系人代记', 'CONTACT', 2, 'primary'),
('elder_medication_record_source', '运营代记', 'OPERATOR', 3, 'info'),
('elder_medication_record_source', '系统判定', 'SYSTEM', 4, 'warning')
ON CONFLICT DO NOTHING;

-- ==================== 向通用设备层登记养老场景 ====================
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('device_scenario', '养老', 'elder', 3, 'primary')
ON CONFLICT DO NOTHING;

-- 追加启用养老场景；已包含时不重复追加，保留运营已调整的其他场景与顺序
UPDATE sys_config
SET value = CASE WHEN btrim(coalesce(value, '')) = '' THEN 'elder' ELSE btrim(value) || ',elder' END,
    default_value = CASE WHEN btrim(coalesce(default_value, '')) = '' THEN 'elder'
                         ELSE btrim(default_value) || ',elder' END,
    update_time = CURRENT_TIMESTAMP
WHERE config_key = 'device.scenarios.enabled'
  AND deleted = FALSE
  AND NOT ('elder' = ANY (string_to_array(replace(coalesce(value, ''), ' ', ''), ',')));
-- ==================== elder_alert_type 预警类型 ====================
INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('养老预警类型', 'elder_alert_type', 0, '一期预警类型')
ON CONFLICT DO NOTHING;
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('elder_alert_type', '疑似跌倒', 'FALL_SUSPECTED', 1, 'danger'),
('elder_alert_type', '一键求助', 'HELP_REQUEST', 2, 'danger'),
('elder_alert_type', '长时间无活动', 'NO_ACTIVITY', 3, 'warning'),
('elder_alert_type', '设备离线', 'DEVICE_OFFLINE', 4, 'warning')
ON CONFLICT DO NOTHING;

-- ==================== elder_alert_level 预警级别 ====================
INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('养老预警级别', 'elder_alert_level', 0, NULL)
ON CONFLICT DO NOTHING;
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('elder_alert_level', '紧急', 'EMERGENCY', 1, 'danger'),
('elder_alert_level', '高', 'HIGH', 2, 'warning'),
('elder_alert_level', '中', 'MEDIUM', 3, 'primary'),
('elder_alert_level', '低', 'LOW', 4, 'info')
ON CONFLICT DO NOTHING;

-- ==================== elder_alert_status 预警状态 ====================
INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('养老预警状态', 'elder_alert_status', 0, NULL)
ON CONFLICT DO NOTHING;
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('elder_alert_status', '已触发', 'TRIGGERED', 1, 'danger'),
('elder_alert_status', '通知中', 'NOTIFYING', 2, 'warning'),
('elder_alert_status', '已知晓', 'ACKED', 3, 'primary'),
('elder_alert_status', '已处理', 'RESOLVED', 4, 'success'),
('elder_alert_status', '误报', 'FALSE_ALARM', 5, 'default'),
('elder_alert_status', '已取消', 'CANCELED', 6, 'default')
ON CONFLICT DO NOTHING;

-- ==================== elder_alert_handling_type 预警响应类型 ====================
INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('预警响应类型', 'elder_alert_handling_type', 0, NULL)
ON CONFLICT DO NOTHING;
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('elder_alert_handling_type', '通知', 'NOTIFICATION', 1, 'warning'),
('elder_alert_handling_type', '认领', 'CLAIM', 2, 'primary'),
('elder_alert_handling_type', '代记已知晓', 'ACKNOWLEDGEMENT', 3, 'info'),
('elder_alert_handling_type', '处理结论', 'RESOLUTION', 4, 'success')
ON CONFLICT DO NOTHING;

-- ==================== elder_alert_handling_channel 预警响应渠道 ====================
INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('预警响应渠道', 'elder_alert_handling_channel', 0, NULL)
ON CONFLICT DO NOTHING;
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('elder_alert_handling_channel', '站内信', 'INTERNAL', 1, 'primary'),
('elder_alert_handling_channel', '钉钉群', 'DINGTALK', 2, 'info'),
('elder_alert_handling_channel', '短信', 'SMS', 3, 'warning'),
('elder_alert_handling_channel', '人工操作', 'MANUAL', 4, 'default')
ON CONFLICT DO NOTHING;

-- ==================== elder_alert_handling_status 预警响应状态 ====================
INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('预警响应状态', 'elder_alert_handling_status', 0, NULL)
ON CONFLICT DO NOTHING;
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('elder_alert_handling_status', '待处理', 'PENDING', 1, 'default'),
('elder_alert_handling_status', '成功', 'SUCCESS', 2, 'success'),
('elder_alert_handling_status', '失败', 'FAILED', 3, 'danger'),
('elder_alert_handling_status', '已跳过', 'SKIPPED', 4, 'warning')
ON CONFLICT DO NOTHING;

-- ==================== elder_alert_recipient_type 预警接收方类型 ====================
INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('预警接收方类型', 'elder_alert_recipient_type', 0, NULL)
ON CONFLICT DO NOTHING;
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('elder_alert_recipient_type', '运营值班账号', 'DUTY_USER', 1, 'primary'),
('elder_alert_recipient_type', '长者联系人', 'CONTACT', 2, 'warning')
ON CONFLICT DO NOTHING;

-- ==================== M1 全局配置默认值（设备相关配置见 v301__device_dict_seed.sql） ====================
INSERT INTO sys_config
    (category, config_key, value, default_value, value_type, name, description, visible, editable)
VALUES
('elder', 'aaf.elder.alert.confirm-window-seconds', '60', '60', 'integer',
 '预警确认窗口（秒）', '疑似跌倒等待长者确认无恙的时间', TRUE, TRUE),
('elder', 'aaf.elder.alert.no-activity-hours', '4', '4', 'integer',
 '无活动预警阈值（小时）', '在线且非隐私模式下连续无活动的预警阈值', TRUE, TRUE),
('elder', 'aaf.elder.alert.awake-start', '07:00', '07:00', 'string',
 '清醒时段开始时间', '无活动判定的每日开始时间', TRUE, TRUE),
('elder', 'aaf.elder.alert.awake-end', '22:00', '22:00', 'string',
 '清醒时段结束时间', '无活动判定的每日结束时间', TRUE, TRUE),
('elder', 'aaf.elder.alert.sms.enabled', 'false', 'false', 'boolean',
 '预警短信开关', '短信模板审核完成前保持关闭', TRUE, TRUE),
('elder', 'aaf.elder.medication.missed-threshold', '2', '2', 'integer',
 '连续漏服通知阈值', '同一计划达到连续漏服次数后通知联系人', TRUE, TRUE)
ON CONFLICT (config_key) WHERE deleted = FALSE DO NOTHING;


-- ============================================================
-- AAF-120 #12018 rehab：康复指导字典
-- ============================================================

-- ==================== elder_rehab_guide_category 康复指导分类 ====================
INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('康复指导分类', 'elder_rehab_guide_category', 0, NULL)
ON CONFLICT DO NOTHING;
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('elder_rehab_guide_category', '运动功能', 'MOTOR_FUNCTION', 1, 'primary'),
('elder_rehab_guide_category', '日常活动', 'DAILY_ACTIVITY', 2, 'success'),
('elder_rehab_guide_category', '呼吸训练', 'RESPIRATORY', 3, 'info'),
('elder_rehab_guide_category', '认知训练', 'COGNITIVE', 4, 'warning'),
('elder_rehab_guide_category', '其他', 'OTHER', 5, 'default')
ON CONFLICT DO NOTHING;

-- ==================== elder_rehab_guide_status 康复指导状态 ====================
INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('康复指导状态', 'elder_rehab_guide_status', 0, NULL)
ON CONFLICT DO NOTHING;
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('elder_rehab_guide_status', '草稿', 'DRAFT', 1, 'default'),
('elder_rehab_guide_status', '已发布', 'PUBLISHED', 2, 'success')
ON CONFLICT DO NOTHING;


-- ==================== #12010 预警晚到判定配置 ====================
INSERT INTO sys_config
    (category, config_key, value, default_value, value_type, name, description, visible, editable)
VALUES
('elder', 'aaf.elder.alert.late-after-seconds', '60', '60', 'integer',
 '预警晚到阈值（秒）', '设备发生时间距服务端接收时间超过该值时标记为晚到并直接通知', TRUE, TRUE)
ON CONFLICT (config_key) WHERE deleted = FALSE DO NOTHING;



-- ==================== #12014 elder_photo_source 照片来源 ====================
INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('养老相册照片来源', 'elder_photo_source', 0, '只接收用户主动拍摄或经授权的自动拍摄照片')
ON CONFLICT DO NOTHING;
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('elder_photo_source', '机器人手动拍摄', 'DEVICE_MANUAL', 1, 'primary'),
('elder_photo_source', '机器人授权自动拍摄', 'DEVICE_AUTO', 2, 'info')
ON CONFLICT DO NOTHING;



-- ============================================================
-- AAF-120 #12015 mall：服务分类与预约状态字典
-- ============================================================

INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('养老服务分类', 'elder_service_category', 0, NULL)
ON CONFLICT DO NOTHING;
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('elder_service_category', '家政服务', 'HOUSEKEEPING', 1, 'primary'),
('elder_service_category', '助餐服务', 'MEAL_ASSISTANCE', 2, 'success'),
('elder_service_category', '生活照护', 'PERSONAL_CARE', 3, 'warning'),
('elder_service_category', '陪诊服务', 'MEDICAL_ESCORT', 4, 'danger'),
('elder_service_category', '康复服务', 'REHABILITATION', 5, 'info'),
('elder_service_category', '其他服务', 'OTHER', 6, 'default')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('服务项目状态', 'elder_service_item_status', 0, NULL)
ON CONFLICT DO NOTHING;
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('elder_service_item_status', '已上架', 'AVAILABLE', 1, 'success'),
('elder_service_item_status', '已下架', 'UNAVAILABLE', 2, 'default')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('服务预约状态', 'elder_service_order_status', 0, NULL)
ON CONFLICT DO NOTHING;
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('elder_service_order_status', '待确认', 'CREATED', 1, 'warning'),
('elder_service_order_status', '已确认', 'CONFIRMED', 2, 'primary'),
('elder_service_order_status', '已完成', 'COMPLETED', 3, 'success'),
('elder_service_order_status', '已取消', 'CANCELLED', 4, 'default')
ON CONFLICT DO NOTHING;

-- ============================================================
-- 通用设备层 module/device 字典与全局配置
-- 与具体业务场景无关；场景字典项与启用由各场景包的种子追加。
-- ============================================================

-- ==================== device_scenario 设备场景 ====================
INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('设备场景', 'device_scenario', 0, '设备绑定的业务场景')
ON CONFLICT DO NOTHING;
-- 通用与办公/伴学是不带业务逻辑的基础场景，只绑定设备所有者，不需要场景主体。
-- 其他场景包在自己的种子里追加自己的字典项与启用键。
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('device_scenario', '通用', 'general', 1, 'default'),
('device_scenario', '办公/伴学', 'office', 2, 'info')
ON CONFLICT DO NOTHING;

-- ==================== device_type 设备类型 ====================
INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('设备类型', 'device_type', 0, NULL)
ON CONFLICT DO NOTHING;
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('device_type', '陪伴机器人', 'ROBOT', 1, 'primary'),
('device_type', '固定摄像头节点', 'CAMERA_NODE', 2, 'info')
ON CONFLICT DO NOTHING;

-- ==================== device_zone 设备区域 ====================
INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('设备区域', 'device_zone', 0, NULL)
ON CONFLICT DO NOTHING;
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('device_zone', '客厅', 'LIVING_ROOM', 1, 'primary'),
('device_zone', '卧室', 'BEDROOM', 2, 'info'),
('device_zone', '卫生间门口', 'BATHROOM_DOOR', 3, 'warning'),
('device_zone', '入户门', 'ENTRANCE', 4, 'success'),
('device_zone', '其他', 'OTHER', 5, 'default')
ON CONFLICT DO NOTHING;

-- ==================== device_status 设备绑定状态 ====================
INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('设备状态', 'device_status', 0, NULL)
ON CONFLICT DO NOTHING;
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('device_status', '已绑定', 'BOUND', 1, 'success'),
('device_status', '已解绑', 'UNBOUND', 2, 'default'),
('device_status', '已停用', 'DISABLED', 3, 'danger')
ON CONFLICT DO NOTHING;

-- ==================== device_online_status 设备在线状态 ====================
INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('设备在线状态', 'device_online_status', 0, NULL)
ON CONFLICT DO NOTHING;
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('device_online_status', '在线', 'ONLINE', 1, 'success'),
('device_online_status', '疑似离线', 'SUSPECT', 2, 'warning'),
('device_online_status', '离线', 'OFFLINE', 3, 'danger')
ON CONFLICT DO NOTHING;

-- ==================== device_camera_status 摄像头状态 ====================
INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('设备摄像头状态', 'device_camera_status', 0, NULL)
ON CONFLICT DO NOTHING;
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('device_camera_status', '可用', 'AVAILABLE', 1, 'success'),
('device_camera_status', '被遮挡', 'BLOCKED', 2, 'danger'),
('device_camera_status', '已关闭', 'DISABLED', 3, 'warning'),
('device_camera_status', '未知', 'UNKNOWN', 4, 'default')
ON CONFLICT DO NOTHING;

-- ==================== device_message_direction 设备消息方向 ====================
INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('设备消息方向', 'device_message_direction', 0, 'MQTT 下行主题类别')
ON CONFLICT DO NOTHING;
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('device_message_direction', '指令', 'COMMAND', 1, 'primary'),
('device_message_direction', '撤销', 'CANCEL', 2, 'warning'),
('device_message_direction', '配置', 'CONFIG', 3, 'info')
ON CONFLICT DO NOTHING;

-- ==================== device_message_status 设备消息状态 ====================
INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('设备消息状态', 'device_message_status', 0, 'PUBACK 只推进到 DISPATCHED')
ON CONFLICT DO NOTHING;
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('device_message_status', '待下发', 'QUEUED', 1, 'default'),
('device_message_status', '已下发', 'DISPATCHED', 2, 'info'),
('device_message_status', '设备已接受', 'ACCEPTED', 3, 'primary'),
('device_message_status', '执行成功', 'SUCCEEDED', 4, 'success'),
('device_message_status', '执行失败', 'FAILED', 5, 'danger'),
('device_message_status', '已过期', 'EXPIRED', 6, 'warning'),
('device_message_status', '已撤销', 'CANCELLED', 7, 'default'),
('device_message_status', '送达未知', 'DELIVERY_UNKNOWN', 8, 'warning')
ON CONFLICT DO NOTHING;

-- ==================== 全局配置默认值 ====================
-- device.scenarios.enabled 默认启用通用与办公/伴学，顺序决定默认场景；其他场景包在自己的种子里追加自己的场景键。
INSERT INTO sys_config
    (category, config_key, value, default_value, value_type, name, description, visible, editable)
VALUES
('device', 'device.scenarios.enabled', 'general,office', 'general,office', 'string',
 '已启用设备场景', '逗号分隔的设备场景稳定键，顺序决定默认场景；场景包种子会追加自己的键', TRUE, TRUE),
('device', 'aaf.device.heartbeat-seconds', '30', '30', 'integer',
 '设备心跳间隔（秒）', '设备上报在线状态的标准间隔', TRUE, TRUE),
('device', 'aaf.device.suspect-after-seconds', '60', '60', 'integer',
 '疑似离线阈值（秒）', '连续两个心跳周期缺失后标记 SUSPECT', TRUE, TRUE),
('device', 'aaf.device.offline-after-seconds', '90', '90', 'integer',
 '离线阈值（秒）', '连续三个心跳周期缺失后标记 OFFLINE', TRUE, TRUE)
ON CONFLICT (config_key) WHERE deleted = FALSE DO NOTHING;

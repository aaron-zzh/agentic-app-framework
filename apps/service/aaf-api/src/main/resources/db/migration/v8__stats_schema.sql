-- =============================================
-- v8: 运营统计模块 - 用户行为事件表
-- =============================================

-- 用户行为事件表（追加写入，不含软删除）
CREATE TABLE IF NOT EXISTS user_event (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    event_type      VARCHAR(32)  NOT NULL,
    page            VARCHAR(255),
    target          VARCHAR(255),
    extra           JSONB,
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 索引：按时间范围 + 事件类型查询
CREATE INDEX idx_user_event_type_time ON user_event (event_type, create_time);
-- 索引：按用户查询
CREATE INDEX idx_user_event_user ON user_event (user_id, create_time);
-- 索引：按日期分区查询（用于留存分析）
CREATE INDEX idx_user_event_date ON user_event ((create_time::date));

-- =============================================
-- 字典数据：事件类型
-- =============================================
INSERT INTO sys_dict_type (name, type, status, deleted, version, create_time, update_time)
VALUES ('用户事件类型', 'stats_event_type', 0, false, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, status, deleted, version, create_time, update_time)
VALUES
    ('stats_event_type', '页面浏览', 'page_view', 1, 0, false, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('stats_event_type', '点击', 'click', 2, 0, false, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('stats_event_type', '注册', 'register', 3, 0, false, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('stats_event_type', '激活', 'activate', 4, 0, false, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('stats_event_type', '付费', 'purchase', 5, 0, false, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('stats_event_type', '登录', 'login', 6, 0, false, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('stats_event_type', '对话', 'chat', 7, 0, false, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('stats_event_type', '工具使用', 'tool_use', 8, 0, false, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

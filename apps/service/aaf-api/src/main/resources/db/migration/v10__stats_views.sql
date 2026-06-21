-- =====================================================
-- AAF 统计视图 & 趋势缓存
-- 设计原则：
--   1. 有预聚合表（ai_usage_daily）的指标直接查预聚合表，不建视图
--   2. 视图仅覆盖 ai_usage_daily 未涵盖的分布/明细维度
--   3. 高频实时趋势用缓存表（5 分钟粒度），避免扫描明细日志
-- =====================================================

-- =====================================================
-- 1. 工具调用分布统计视图
-- 用途: 各工具调用次数、成功率、调用来源分布
-- 来源: ai_tool_call_log（ai_usage_daily 未覆盖工具维度）
-- =====================================================
CREATE VIEW v_ai_tool_usage_stats AS
SELECT
    tcl.tool_name,
    tcl.tool_type,                              -- FUNCTION/MCP/HTTP/SCRIPT/WORKFLOW/AGENT
    tcl.caller_type,                            -- AGENT/WORKFLOW/SKILL/DIRECT
    DATE_TRUNC('day', tcl.create_time)          AS stat_date,
    COUNT(*)                                    AS total_calls,
    COUNT(CASE WHEN tcl.status = 'SUCCESS'  THEN 1 END) AS success_count,
    COUNT(CASE WHEN tcl.status = 'FAILED'   THEN 1 END) AS failed_count,
    COUNT(CASE WHEN tcl.status = 'TIMEOUT'  THEN 1 END) AS timeout_count,
    COUNT(CASE WHEN tcl.status = 'REJECTED' THEN 1 END) AS rejected_count,
    CASE
        WHEN COUNT(*) > 0
        THEN ROUND(COUNT(CASE WHEN tcl.status = 'SUCCESS' THEN 1 END) * 100.0 / COUNT(*), 2)
        ELSE 0
    END                                         AS success_rate,
    AVG(tcl.duration_ms)                        AS avg_duration_ms,
    MAX(tcl.duration_ms)                        AS max_duration_ms
FROM ai_tool_call_log tcl
WHERE tcl.create_time >= CURRENT_DATE - INTERVAL '90 days'  -- 只看近 90 天，避免全表扫
GROUP BY tcl.tool_name, tcl.tool_type, tcl.caller_type, DATE_TRUNC('day', tcl.create_time);

COMMENT ON VIEW v_ai_tool_usage_stats IS 'AI 工具调用分布统计（按工具/类型/来源/天）';

-- =====================================================
-- 2. 技能触发分布统计视图
-- 用途: 各技能触发次数、命中率、置信度分布
-- 来源: ai_skill_trigger_log
-- =====================================================
CREATE VIEW v_ai_skill_trigger_stats AS
SELECT
    stl.skill_id,
    stl.skill_name,
    DATE_TRUNC('day', stl.create_time)          AS stat_date,
    COUNT(*)                                    AS total_triggers,
    COUNT(CASE WHEN stl.status = 'SUCCESS' THEN 1 END) AS success_count,
    COUNT(CASE WHEN stl.status = 'FAILED'  THEN 1 END) AS failed_count,
    COUNT(CASE WHEN stl.status = 'SKIPPED' THEN 1 END) AS skipped_count,
    ROUND(AVG(stl.match_score)::numeric, 4)     AS avg_match_score,
    ROUND(MIN(stl.match_score)::numeric, 4)     AS min_match_score,
    ROUND(MAX(stl.match_score)::numeric, 4)     AS max_match_score,
    CASE
        WHEN COUNT(*) > 0
        THEN ROUND(COUNT(CASE WHEN stl.status = 'SUCCESS' THEN 1 END) * 100.0 / COUNT(*), 2)
        ELSE 0
    END                                         AS success_rate
FROM ai_skill_trigger_log stl
WHERE stl.create_time >= CURRENT_DATE - INTERVAL '90 days'
GROUP BY stl.skill_id, stl.skill_name, DATE_TRUNC('day', stl.create_time);

COMMENT ON VIEW v_ai_skill_trigger_stats IS 'AI 技能触发分布统计（按技能/天）';

-- =====================================================
-- 3. 工作流执行统计视图
-- 用途: 各工作流执行次数、成功率、Token 消耗
-- 来源: ai_workflow_run（ai_usage_daily 有 workflow_key 维度但无执行次数）
-- =====================================================
CREATE VIEW v_ai_workflow_run_stats AS
SELECT
    wr.workflow_key,
    wr.workflow_name,
    DATE_TRUNC('day', wr.started_at)            AS stat_date,
    COUNT(*)                                    AS total_runs,
    COUNT(CASE WHEN wr.status = 'DONE'      THEN 1 END) AS done_count,
    COUNT(CASE WHEN wr.status = 'FAILED'    THEN 1 END) AS failed_count,
    COUNT(CASE WHEN wr.status = 'RUNNING'   THEN 1 END) AS running_count,
    COUNT(CASE WHEN wr.status = 'CANCELLED' THEN 1 END) AS cancelled_count,
    CASE
        WHEN COUNT(*) > 0
        THEN ROUND(COUNT(CASE WHEN wr.status = 'DONE' THEN 1 END) * 100.0 / COUNT(*), 2)
        ELSE 0
    END                                         AS success_rate,
    -- Token 消耗汇总
    COALESCE(SUM(wr.total_tokens), 0)           AS total_tokens,
    COALESCE(AVG(wr.total_tokens), 0)           AS avg_tokens_per_run,
    -- 执行时长（毫秒）
    AVG(EXTRACT(EPOCH FROM (wr.ended_at - wr.started_at)) * 1000) AS avg_duration_ms
FROM ai_workflow_run wr
WHERE wr.started_at >= CURRENT_DATE - INTERVAL '90 days'
GROUP BY wr.workflow_key, wr.workflow_name, DATE_TRUNC('day', wr.started_at);

COMMENT ON VIEW v_ai_workflow_run_stats IS 'AI 工作流执行统计（按工作流/天）';

-- =====================================================
-- 4. 用户贡献量统计视图
-- 用途: 用户创作量、积分、素材综合统计（排行榜基础）
-- =====================================================
CREATE VIEW v_user_contribution_stats AS
SELECT
    u.id                                              AS user_id,
    u.nickname                                        AS user_name,
    u.email,
    u.create_time                                     AS register_time,
    u.last_login_time,
    -- AIGC 任务统计
    COUNT(DISTINCT t.id)                              AS total_aigc_tasks,
    COUNT(DISTINCT CASE WHEN t.status = 'SUCCESS' THEN t.id END) AS success_aigc_tasks,
    COUNT(DISTINCT CASE WHEN t.type = 'IMAGE'     THEN t.id END) AS image_tasks,
    COUNT(DISTINCT CASE WHEN t.type = 'VIDEO'     THEN t.id END) AS video_tasks,
    COUNT(DISTINCT CASE WHEN t.type = 'MODEL_3D'  THEN t.id END) AS model3d_tasks,
    COUNT(DISTINCT CASE WHEN t.type = 'MUSIC'     THEN t.id END) AS music_tasks,
    COUNT(DISTINCT CASE WHEN t.type = 'VOICE'     THEN t.id END) AS voice_tasks,
    -- 积分统计
    COALESCE(ca.balance, 0)                           AS credit_balance,
    COALESCE(ca.total_earned, 0)                      AS total_earned_credits,
    COALESCE(ca.total_spent, 0)                       AS total_spent_credits,
    -- 素材统计
    COUNT(DISTINCT ma.id)                             AS total_media_assets,
    -- Todo 统计
    COUNT(DISTINCT td.id)                             AS total_todos,
    COUNT(DISTINCT CASE WHEN td.status = 'done' THEN td.id END) AS done_todos
FROM sys_user u
LEFT JOIN aigc_task t      ON t.user_id = u.id AND t.deleted = FALSE
LEFT JOIN credit_account ca ON ca.user_id = u.id AND ca.deleted = FALSE
LEFT JOIN media_asset ma   ON ma.user_id = u.id AND ma.deleted = FALSE
LEFT JOIN sys_todo td      ON td.assignee_id = u.id AND td.deleted = FALSE
WHERE u.deleted = FALSE
GROUP BY u.id, u.nickname, u.email, u.create_time, u.last_login_time,
         ca.balance, ca.total_earned, ca.total_spent;

COMMENT ON VIEW v_user_contribution_stats IS '用户贡献量统计（创作量、积分、素材、Todo）';

-- =====================================================
-- 5. 积分消耗趋势视图（每日粒度）
-- 用途: 全局或按用户统计每日积分消耗，对应 StatsService credit_cost metric
-- 来源: credit_transaction（type='SPEND'）JOIN credit_account
-- =====================================================
CREATE VIEW v_credit_spend_daily AS
SELECT
    DATE_TRUNC('day', ct.create_time)           AS stat_date,
    ca.user_id,
    u.nickname                                  AS user_name,
    COUNT(*)                                    AS transaction_count,
    SUM(ct.amount)                              AS total_spent,
    AVG(ct.amount)                              AS avg_spent_per_tx,
    ct.source                                   AS spend_source   -- AIGC/AI_CALL/MANUAL 等
FROM credit_transaction ct
JOIN credit_account ca ON ca.id = ct.account_id AND ca.deleted = FALSE
LEFT JOIN sys_user u   ON u.id = ca.user_id AND u.deleted = FALSE
WHERE ct.type = 'SPEND'
GROUP BY DATE_TRUNC('day', ct.create_time), ca.user_id, u.nickname, ct.source;

COMMENT ON VIEW v_credit_spend_daily IS '积分消耗每日统计（按用户+来源）';

-- =====================================================
-- 5a. 积分消耗排行视图（历史累计）
-- 用途: 消耗积分最多的用户排行
-- =====================================================
CREATE VIEW v_credit_spend_ranking AS
SELECT
    ca.user_id,
    u.nickname                                  AS user_name,
    ca.total_spent                              AS total_spent_credits,
    ca.total_earned                             AS total_earned_credits,
    ca.balance                                  AS current_balance,
    RANK() OVER (ORDER BY ca.total_spent DESC)  AS spend_rank
FROM credit_account ca
JOIN sys_user u ON u.id = ca.user_id AND u.deleted = FALSE
WHERE ca.deleted = FALSE
ORDER BY ca.total_spent DESC;

COMMENT ON VIEW v_credit_spend_ranking IS '积分消耗排行（历史累计总消耗降序）';

-- =====================================================
-- 6. 积分余额区间分布视图（百分位统计）
-- =====================================================
CREATE VIEW v_credit_balance_distribution AS
SELECT
    COUNT(CASE WHEN ca.balance BETWEEN 0     AND 99    THEN 1 END) AS balance_0_100,
    COUNT(CASE WHEN ca.balance BETWEEN 100   AND 499   THEN 1 END) AS balance_100_500,
    COUNT(CASE WHEN ca.balance BETWEEN 500   AND 999   THEN 1 END) AS balance_500_1000,
    COUNT(CASE WHEN ca.balance BETWEEN 1000  AND 4999  THEN 1 END) AS balance_1000_5000,
    COUNT(CASE WHEN ca.balance BETWEEN 5000  AND 9999  THEN 1 END) AS balance_5000_10000,
    COUNT(CASE WHEN ca.balance >= 10000                THEN 1 END) AS balance_10000_plus,
    COUNT(ca.id)                                                    AS total_users,
    COALESCE(ROUND(AVG(ca.balance)::numeric, 2), 0)                 AS avg_balance,
    COALESCE(MAX(ca.balance), 0)                                    AS max_balance,
    COALESCE(ROUND(STDDEV(ca.balance)::numeric, 2), 0)              AS balance_stddev,
    CAST(PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY ca.balance) AS NUMERIC(12,2)) AS p25,
    CAST(PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY ca.balance) AS NUMERIC(12,2)) AS p50,
    CAST(PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY ca.balance) AS NUMERIC(12,2)) AS p75,
    CAST(PERCENTILE_CONT(0.90) WITHIN GROUP (ORDER BY ca.balance) AS NUMERIC(12,2)) AS p90,
    CAST(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY ca.balance) AS NUMERIC(12,2)) AS p95
FROM credit_account ca
WHERE ca.deleted = FALSE;

COMMENT ON VIEW v_credit_balance_distribution IS '用户积分余额区间分布 + 百分位统计';

-- =====================================================
-- 6. Todo 每日统计视图（趋势分析基础）
-- 用途: 每天新建/完成/逾期 Todo 数量趋势
-- =====================================================
CREATE VIEW v_todo_daily_stats AS
SELECT
    DATE_TRUNC('day', t.create_time)            AS stat_date,
    t.category,
    COUNT(*)                                    AS created_count,
    COUNT(CASE WHEN t.status = 'done'     THEN 1 END) AS done_count,
    COUNT(CASE WHEN t.status = 'pending'  THEN 1 END) AS pending_count,
    COUNT(CASE WHEN t.status = 'cancelled' THEN 1 END) AS cancelled_count,
    -- 逾期：due_date < now 且未完成
    COUNT(CASE WHEN t.due_date < CURRENT_TIMESTAMP AND t.status != 'done' THEN 1 END) AS overdue_count,
    -- 平均完成时长（小时）
    AVG(CASE
        WHEN t.status = 'done' AND t.archived_at IS NOT NULL
        THEN EXTRACT(EPOCH FROM (t.archived_at - t.create_time)) / 3600
    END)                                        AS avg_completion_hours
FROM sys_todo t
WHERE t.deleted = FALSE
GROUP BY DATE_TRUNC('day', t.create_time), t.category;

COMMENT ON VIEW v_todo_daily_stats IS 'Todo 每日统计（新建/完成/逾期趋势）';

-- =====================================================
-- 7. AIGC 任务完成趋势缓存表（5 分钟粒度）
-- =====================================================
CREATE TABLE aigc_task_trend_stats (
    id               BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    time_period      TIMESTAMP(6)  NOT NULL,
    task_type        VARCHAR(20)   NOT NULL DEFAULT 'ALL', -- IMAGE/VIDEO/MODEL_3D/MUSIC/VOICE/ALL
    period_count     INTEGER       NOT NULL DEFAULT 0,
    cumulative_count INTEGER       NOT NULL DEFAULT 0,
    create_time      TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          BOOLEAN       NOT NULL DEFAULT FALSE,
    delete_time      TIMESTAMP(6)
);

-- 唯一约束需要作为部分唯一索引创建（支持软删除）
CREATE UNIQUE INDEX uk_aigc_task_trend_unique ON aigc_task_trend_stats (time_period, task_type) WHERE deleted = FALSE;

COMMENT ON TABLE aigc_task_trend_stats IS 'AIGC 任务完成趋势统计缓存（5 分钟粒度）';

CREATE INDEX idx_aigc_task_trend_query ON aigc_task_trend_stats (task_type, time_period) WHERE deleted = FALSE;

-- =====================================================
-- 8. AIGC 任务趋势刷新函数（定时任务每 5 分钟调用）
-- =====================================================
CREATE OR REPLACE FUNCTION refresh_aigc_task_trend_stats(target_type VARCHAR DEFAULT NULL)
RETURNS TEXT AS $$
DECLARE
    current_5min  TIMESTAMP;
    prev_5min     TIMESTAMP;
    updated_count INTEGER := 0;
    existing_count INTEGER := 0;
BEGIN
    current_5min := DATE_TRUNC('minute', NOW())
                    - INTERVAL '1 minute' * (EXTRACT(MINUTE FROM NOW())::INT % 5);
    prev_5min    := current_5min - INTERVAL '5 minutes';

    SELECT COUNT(*) INTO existing_count
    FROM aigc_task_trend_stats
    WHERE time_period = prev_5min AND deleted = FALSE
      AND (target_type IS NULL OR task_type = target_type);

    IF existing_count > 0 THEN
        RETURN '跳过：时间段 ' || prev_5min || ' 已统计';
    END IF;

    INSERT INTO aigc_task_trend_stats (time_period, task_type, period_count, cumulative_count)
    SELECT prev_5min, agg.task_type, agg.period_count, agg.cumulative_count
    FROM (
        SELECT
            t.type AS task_type,
            COUNT(CASE WHEN t.update_time >= prev_5min AND t.update_time < current_5min THEN 1 END) AS period_count,
            COUNT(CASE WHEN t.update_time < current_5min THEN 1 END) AS cumulative_count
        FROM aigc_task t
        WHERE t.deleted = FALSE AND t.status = 'SUCCESS' AND t.update_time IS NOT NULL
          AND (target_type IS NULL OR t.type = target_type)
        GROUP BY t.type
        UNION ALL
        SELECT 'ALL',
            COUNT(CASE WHEN t.update_time >= prev_5min AND t.update_time < current_5min THEN 1 END),
            COUNT(CASE WHEN t.update_time < current_5min THEN 1 END)
        FROM aigc_task t
        WHERE t.deleted = FALSE AND t.status = 'SUCCESS' AND t.update_time IS NOT NULL
          AND target_type IS NULL
    ) agg
    WHERE agg.period_count > 0
    ON CONFLICT (time_period, task_type) DO UPDATE SET
        period_count     = EXCLUDED.period_count,
        cumulative_count = EXCLUDED.cumulative_count,
        update_time      = CURRENT_TIMESTAMP;

    GET DIAGNOSTICS updated_count = ROW_COUNT;
    RETURN '刷新完成，写入 ' || updated_count || ' 行，时间段: ' || prev_5min;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION refresh_aigc_task_trend_stats IS 'AIGC 任务趋势缓存刷新，建议定时任务每 5 分钟调用';

-- =====================================================
-- 9. 过期数据清理函数
-- =====================================================
CREATE OR REPLACE FUNCTION cleanup_aigc_task_trend_stats(retention_days INTEGER DEFAULT 30)
RETURNS TEXT AS $$
DECLARE
    cutoff TIMESTAMP;
    del_count INTEGER;
BEGIN
    cutoff := NOW() - INTERVAL '1 day' * retention_days;
    DELETE FROM aigc_task_trend_stats WHERE time_period < cutoff;
    GET DIAGNOSTICS del_count = ROW_COUNT;
    RETURN '清理完成，删除 ' || del_count || ' 行，保留 ' || retention_days || ' 天';
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- 10. 统计查询优化索引
-- =====================================================
CREATE INDEX IF NOT EXISTS idx_aigc_task_trend_update
    ON aigc_task (status, update_time) WHERE deleted = FALSE AND status = 'SUCCESS';

CREATE INDEX IF NOT EXISTS idx_credit_account_balance
    ON credit_account (balance, user_id) WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_todo_stats
    ON sys_todo (create_time, category, status) WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_ai_tool_call_log_stats
    ON ai_tool_call_log (tool_name, tool_type, status, create_time);

CREATE INDEX IF NOT EXISTS idx_ai_skill_trigger_stats
    ON ai_skill_trigger_log (skill_id, status, create_time);

CREATE INDEX IF NOT EXISTS idx_ai_workflow_run_stats
    ON ai_workflow_run (workflow_key, status, started_at);

-- =====================================================
-- 使用示例
-- =====================================================
-- 工具调用 TOP10（近 7 天）:
--   SELECT tool_name, SUM(total_calls), AVG(success_rate)
--   FROM v_ai_tool_usage_stats WHERE stat_date >= CURRENT_DATE - 7
--   GROUP BY tool_name ORDER BY SUM(total_calls) DESC LIMIT 10;
--
-- 技能触发趋势:
--   SELECT stat_date, skill_name, total_triggers FROM v_ai_skill_trigger_stats
--   WHERE stat_date >= CURRENT_DATE - 30 ORDER BY stat_date, total_triggers DESC;
--
-- 工作流成功率排行:
--   SELECT workflow_name, SUM(total_runs), AVG(success_rate)
--   FROM v_ai_workflow_run_stats WHERE stat_date >= CURRENT_DATE - 7
--   GROUP BY workflow_name ORDER BY SUM(total_runs) DESC;
--
-- 用户贡献排行（创作量 TOP10）:
--   SELECT user_name, total_aigc_tasks, credit_balance
--   FROM v_user_contribution_stats ORDER BY total_aigc_tasks DESC LIMIT 10;
--
-- Todo 完成趋势（近 30 天）:
--   SELECT stat_date, created_count, done_count, overdue_count
--   FROM v_todo_daily_stats WHERE stat_date >= CURRENT_DATE - 30
--   ORDER BY stat_date;
--
-- AIGC 5 分钟实时趋势:
--   SELECT * FROM aigc_task_trend_stats WHERE task_type = 'ALL'
--   ORDER BY time_period DESC LIMIT 50;
--
-- 手动刷新趋势缓存:
--   SELECT refresh_aigc_task_trend_stats();
--
-- 清理过期数据（每日执行）:
--   SELECT cleanup_aigc_task_trend_stats(30);

-- 数字人系统数据库迁移脚本
-- 版本: v1.0
-- 日期: 2026-09-22
-- 说明: 创建数字人相关表结构

-- ============================================
-- 1. 数字人实体表
-- ============================================

CREATE TABLE IF NOT EXISTS digital_human (
  -- 主键
  id VARCHAR(64) PRIMARY KEY,

  -- 所属关系
  user_id VARCHAR(64) NOT NULL,
  organization_id VARCHAR(64),

  -- 基础信息
  name VARCHAR(255) NOT NULL,
  description TEXT,
  tags TEXT[],

  -- 形象资产（仅 2D）
  avatar_image_url VARCHAR(512) NOT NULL,
  appearance JSONB,
  -- appearance 结构示例:
  -- {
  --   "age": 25,
  --   "gender": "female",
  --   "style": "商务装",
  --   "prompt": "25岁职业女性，短发，商务装，温柔微笑"
  -- }

  -- 音色配置（阿里云预设）
  voice_config JSONB NOT NULL,
  -- voice_config 结构示例:
  -- {
  --   "provider": "aliyun",
  --   "presetVoiceId": "aijia",
  --   "voiceName": "艾佳",
  --   "speechRate": 0,
  --   "pitchRate": 0,
  --   "volume": 50
  -- }

  -- 大模型配置
  model_config JSONB,
  -- model_config 结构示例:
  -- {
  --   "provider": "anthropic",
  --   "model": "claude-sonnet-5",
  --   "temperature": 0.7,
  --   "maxTokens": 2000,
  --   "systemPrompt": "你是一位专业的营销顾问..."
  -- }

  -- 双向关联助理
  assistant_id VARCHAR(64),
  sync_model_config BOOLEAN DEFAULT FALSE,

  -- 使用统计
  video_count INT DEFAULT 0,
  total_duration INT DEFAULT 0,  -- 秒
  credits_consumed INT DEFAULT 0,
  last_used_at TIMESTAMP,

  -- 状态（无审批流程）
  status VARCHAR(32) DEFAULT 'active' CHECK (status IN ('active', 'archived')),
  generation_source VARCHAR(32) CHECK (generation_source IN ('aigc_project')),
  aigc_project_id VARCHAR(64),

  -- 时间戳
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

  -- 约束
  CONSTRAINT fk_digital_human_user FOREIGN KEY (user_id) REFERENCES "user"(id) ON DELETE CASCADE,
  CONSTRAINT fk_digital_human_organization FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE CASCADE,
  CONSTRAINT fk_digital_human_assistant FOREIGN KEY (assistant_id) REFERENCES assistant(id) ON DELETE SET NULL
);

-- 索引
CREATE INDEX idx_digital_human_user ON digital_human(user_id);
CREATE INDEX idx_digital_human_org ON digital_human(organization_id);
CREATE INDEX idx_digital_human_status ON digital_human(status);
CREATE INDEX idx_digital_human_assistant ON digital_human(assistant_id);
CREATE INDEX idx_digital_human_created_at ON digital_human(created_at DESC);
CREATE INDEX idx_digital_human_tags USING GIN (tags);

-- 注释
COMMENT ON TABLE digital_human IS '数字人实体表';
COMMENT ON COLUMN digital_human.id IS '数字人唯一标识';
COMMENT ON COLUMN digital_human.user_id IS '创建者用户ID';
COMMENT ON COLUMN digital_human.organization_id IS '组织ID（支持组织共享）';
COMMENT ON COLUMN digital_human.name IS '数字人名称';
COMMENT ON COLUMN digital_human.description IS '数字人描述';
COMMENT ON COLUMN digital_human.tags IS '标签数组（营销、客服等）';
COMMENT ON COLUMN digital_human.avatar_image_url IS '数字人头像图片URL';
COMMENT ON COLUMN digital_human.appearance IS '形象信息（年龄、性别、风格、提示词）';
COMMENT ON COLUMN digital_human.voice_config IS '音色配置（阿里云预设音色）';
COMMENT ON COLUMN digital_human.model_config IS '大模型配置';
COMMENT ON COLUMN digital_human.assistant_id IS '关联的助理ID（双向绑定）';
COMMENT ON COLUMN digital_human.sync_model_config IS '是否同步助理的模型配置';
COMMENT ON COLUMN digital_human.video_count IS '生成视频数量';
COMMENT ON COLUMN digital_human.total_duration IS '累计视频时长（秒）';
COMMENT ON COLUMN digital_human.credits_consumed IS '累计消耗积分';
COMMENT ON COLUMN digital_human.last_used_at IS '最后使用时间';
COMMENT ON COLUMN digital_human.status IS '状态（active: 激活, archived: 归档）';
COMMENT ON COLUMN digital_human.generation_source IS '生成来源（aigc_project）';
COMMENT ON COLUMN digital_human.aigc_project_id IS '关联的AIGC项目ID';

-- 更新时间触发器
CREATE OR REPLACE FUNCTION update_digital_human_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = CURRENT_TIMESTAMP;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_digital_human_updated_at
  BEFORE UPDATE ON digital_human
  FOR EACH ROW
  EXECUTE FUNCTION update_digital_human_updated_at();

-- ============================================
-- 2. 预设音色库表
-- ============================================

CREATE TABLE IF NOT EXISTS preset_voice (
  -- 主键
  id VARCHAR(64) PRIMARY KEY,

  -- 基础信息
  name VARCHAR(128) NOT NULL,
  description TEXT,
  gender VARCHAR(16) CHECK (gender IN ('male', 'female')),

  -- 音频样本
  sample_audio_url VARCHAR(512),

  -- 第三方配置
  provider VARCHAR(64) DEFAULT 'aliyun',
  external_voice_id VARCHAR(128) NOT NULL,

  -- 状态
  is_active BOOLEAN DEFAULT TRUE,
  display_order INT DEFAULT 0,

  -- 时间戳
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 索引
CREATE INDEX idx_preset_voice_provider ON preset_voice(provider);
CREATE INDEX idx_preset_voice_active ON preset_voice(is_active);
CREATE INDEX idx_preset_voice_display_order ON preset_voice(display_order);

-- 注释
COMMENT ON TABLE preset_voice IS '预设音色库表';
COMMENT ON COLUMN preset_voice.id IS '音色唯一标识';
COMMENT ON COLUMN preset_voice.name IS '音色名称（如：艾佳）';
COMMENT ON COLUMN preset_voice.description IS '音色描述';
COMMENT ON COLUMN preset_voice.gender IS '性别（male/female）';
COMMENT ON COLUMN preset_voice.sample_audio_url IS '试听音频URL';
COMMENT ON COLUMN preset_voice.provider IS '提供商（aliyun）';
COMMENT ON COLUMN preset_voice.external_voice_id IS '第三方音色ID（阿里云音色ID）';
COMMENT ON COLUMN preset_voice.is_active IS '是否启用';
COMMENT ON COLUMN preset_voice.display_order IS '显示顺序';

-- 初始数据：5-6个常用音色
INSERT INTO preset_voice (id, name, description, gender, sample_audio_url, provider, external_voice_id, is_active, display_order) VALUES
('aijia', '艾佳', '温柔亲切，适合客服场景', 'female', '/audio/samples/aijia.mp3', 'aliyun', 'aijia', true, 1),
('aitong', '艾彤', '活力甜美，适合营销推广', 'female', '/audio/samples/aitong.mp3', 'aliyun', 'aitong', true, 2),
('xiaoyun', '小云', '知性优雅，适合教育培训', 'female', '/audio/samples/xiaoyun.mp3', 'aliyun', 'xiaoyun', true, 3),
('aicheng', '艾诚', '专业沉稳，适合商务场景', 'male', '/audio/samples/aicheng.mp3', 'aliyun', 'aicheng', true, 4),
('aiwei', '艾伟', '温和亲切，适合内容讲解', 'male', '/audio/samples/aiwei.mp3', 'aliyun', 'aiwei', true, 5)
ON CONFLICT (id) DO NOTHING;

-- ============================================
-- 3. Assistant 表新增字段（双向关联）
-- ============================================

-- 检查字段是否已存在
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'assistant' AND column_name = 'digital_human_id'
  ) THEN
    ALTER TABLE assistant ADD COLUMN digital_human_id VARCHAR(64);
    ALTER TABLE assistant ADD CONSTRAINT fk_assistant_digital_human
      FOREIGN KEY (digital_human_id) REFERENCES digital_human(id) ON DELETE SET NULL;
    CREATE INDEX idx_assistant_digital_human ON assistant(digital_human_id);

    COMMENT ON COLUMN assistant.digital_human_id IS '关联的数字人ID（双向绑定）';
  END IF;
END $$;

-- ============================================
-- 4. 积分消耗记录表
-- ============================================

CREATE TABLE IF NOT EXISTS credit_consumption (
  -- 主键
  id VARCHAR(64) PRIMARY KEY,

  -- 所属关系
  user_id VARCHAR(64) NOT NULL,
  organization_id VARCHAR(64),

  -- 关联资源
  digital_human_id VARCHAR(64),
  project_id VARCHAR(64) NOT NULL,

  -- 操作信息
  operation VARCHAR(64) NOT NULL CHECK (operation IN ('avatar_generation', 'simple_video', 'complex_video')),
  credits_consumed INT NOT NULL,

  -- 成本明细
  breakdown JSONB,
  -- breakdown 结构示例:
  -- {
  --   "apiProvider": "hyperframes",
  --   "apiCost": 7.30,
  --   "duration": 60,
  --   "sceneCount": 4,
  --   "elements": ["background_music", "text_overlay"]
  -- }

  -- 时间戳
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

  -- 约束
  CONSTRAINT fk_credit_consumption_user FOREIGN KEY (user_id) REFERENCES "user"(id) ON DELETE CASCADE,
  CONSTRAINT fk_credit_consumption_organization FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE CASCADE,
  CONSTRAINT fk_credit_consumption_digital_human FOREIGN KEY (digital_human_id) REFERENCES digital_human(id) ON DELETE SET NULL
);

-- 索引
CREATE INDEX idx_credit_consumption_user ON credit_consumption(user_id);
CREATE INDEX idx_credit_consumption_org ON credit_consumption(organization_id);
CREATE INDEX idx_credit_consumption_digital_human ON credit_consumption(digital_human_id);
CREATE INDEX idx_credit_consumption_project ON credit_consumption(project_id);
CREATE INDEX idx_credit_consumption_created_at ON credit_consumption(created_at DESC);
CREATE INDEX idx_credit_consumption_operation ON credit_consumption(operation);

-- 注释
COMMENT ON TABLE credit_consumption IS '积分消耗记录表';
COMMENT ON COLUMN credit_consumption.id IS '记录唯一标识';
COMMENT ON COLUMN credit_consumption.user_id IS '用户ID';
COMMENT ON COLUMN credit_consumption.organization_id IS '组织ID';
COMMENT ON COLUMN credit_consumption.digital_human_id IS '关联的数字人ID';
COMMENT ON COLUMN credit_consumption.project_id IS '关联的项目ID';
COMMENT ON COLUMN credit_consumption.operation IS '操作类型（avatar_generation/simple_video/complex_video）';
COMMENT ON COLUMN credit_consumption.credits_consumed IS '消耗的积分数';
COMMENT ON COLUMN credit_consumption.breakdown IS '成本明细（API提供商、费用、时长、场景数等）';

-- ============================================
-- 5. 视图：数字人统计视图（可选）
-- ============================================

CREATE OR REPLACE VIEW v_digital_human_stats AS
SELECT
  dh.id,
  dh.name,
  dh.avatar_image_url,
  dh.user_id,
  dh.organization_id,
  dh.video_count,
  dh.total_duration,
  dh.credits_consumed,
  dh.last_used_at,
  dh.status,
  dh.created_at,
  u.name AS creator_name,
  COUNT(DISTINCT cc.id) AS consumption_records_count,
  COALESCE(SUM(cc.credits_consumed), 0) AS verified_credits_consumed
FROM digital_human dh
LEFT JOIN "user" u ON dh.user_id = u.id
LEFT JOIN credit_consumption cc ON dh.id = cc.digital_human_id
GROUP BY
  dh.id, dh.name, dh.avatar_image_url, dh.user_id, dh.organization_id,
  dh.video_count, dh.total_duration, dh.credits_consumed, dh.last_used_at,
  dh.status, dh.created_at, u.name;

COMMENT ON VIEW v_digital_human_stats IS '数字人统计视图（含积分消耗验证）';

-- ============================================
-- 6. 权限检查函数
-- ============================================

-- 检查用户是否可以访问数字人
CREATE OR REPLACE FUNCTION can_access_digital_human(
  p_user_id VARCHAR(64),
  p_digital_human_id VARCHAR(64),
  p_user_org_id VARCHAR(64) DEFAULT NULL
) RETURNS BOOLEAN AS $$
DECLARE
  v_owner_id VARCHAR(64);
  v_org_id VARCHAR(64);
BEGIN
  SELECT user_id, organization_id
  INTO v_owner_id, v_org_id
  FROM digital_human
  WHERE id = p_digital_human_id;

  -- 不存在
  IF v_owner_id IS NULL THEN
    RETURN FALSE;
  END IF;

  -- 是创建者
  IF v_owner_id = p_user_id THEN
    RETURN TRUE;
  END IF;

  -- 是组织成员
  IF v_org_id IS NOT NULL AND v_org_id = p_user_org_id THEN
    RETURN TRUE;
  END IF;

  RETURN FALSE;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION can_access_digital_human IS '检查用户是否可以访问数字人（查看/使用）';

-- 检查用户是否可以编辑数字人
CREATE OR REPLACE FUNCTION can_edit_digital_human(
  p_user_id VARCHAR(64),
  p_digital_human_id VARCHAR(64)
) RETURNS BOOLEAN AS $$
DECLARE
  v_owner_id VARCHAR(64);
BEGIN
  SELECT user_id
  INTO v_owner_id
  FROM digital_human
  WHERE id = p_digital_human_id;

  RETURN v_owner_id = p_user_id;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION can_edit_digital_human IS '检查用户是否可以编辑数字人（仅创建者）';

-- ============================================
-- 7. 统计查询函数
-- ============================================

-- 获取用户数字人统计概览
CREATE OR REPLACE FUNCTION get_digital_human_overview(
  p_user_id VARCHAR(64),
  p_org_id VARCHAR(64) DEFAULT NULL,
  p_period VARCHAR(32) DEFAULT 'month'
) RETURNS TABLE (
  total_digital_humans BIGINT,
  active_digital_humans BIGINT,
  total_videos BIGINT,
  total_duration BIGINT,
  total_credits_consumed BIGINT
) AS $$
DECLARE
  v_start_date TIMESTAMP;
BEGIN
  -- 计算时间范围
  CASE p_period
    WHEN 'day' THEN
      v_start_date := CURRENT_TIMESTAMP - INTERVAL '1 day';
    WHEN 'week' THEN
      v_start_date := CURRENT_TIMESTAMP - INTERVAL '7 days';
    WHEN 'month' THEN
      v_start_date := CURRENT_TIMESTAMP - INTERVAL '30 days';
    ELSE
      v_start_date := NULL;  -- all
  END CASE;

  RETURN QUERY
  SELECT
    COUNT(DISTINCT dh.id) AS total_digital_humans,
    COUNT(DISTINCT CASE WHEN dh.status = 'active' THEN dh.id END) AS active_digital_humans,
    COALESCE(SUM(dh.video_count), 0) AS total_videos,
    COALESCE(SUM(dh.total_duration), 0) AS total_duration,
    COALESCE(SUM(dh.credits_consumed), 0) AS total_credits_consumed
  FROM digital_human dh
  WHERE
    (dh.user_id = p_user_id OR dh.organization_id = p_org_id)
    AND (v_start_date IS NULL OR dh.created_at >= v_start_date);
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION get_digital_human_overview IS '获取用户数字人统计概览';

-- ============================================
-- 8. 数据清理（可选）
-- ============================================

-- 清理归档超过 90 天的数字人（可选，根据业务需求）
CREATE OR REPLACE FUNCTION cleanup_old_archived_digital_humans()
RETURNS INT AS $$
DECLARE
  v_deleted_count INT;
BEGIN
  WITH deleted AS (
    DELETE FROM digital_human
    WHERE
      status = 'archived'
      AND updated_at < CURRENT_TIMESTAMP - INTERVAL '90 days'
    RETURNING id
  )
  SELECT COUNT(*) INTO v_deleted_count FROM deleted;

  RETURN v_deleted_count;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION cleanup_old_archived_digital_humans IS '清理归档超过90天的数字人';

-- ============================================
-- 9. 回滚脚本（如需回滚）
-- ============================================

-- 取消注释以下代码以回滚迁移：

/*
-- 删除函数
DROP FUNCTION IF EXISTS cleanup_old_archived_digital_humans();
DROP FUNCTION IF EXISTS get_digital_human_overview(VARCHAR, VARCHAR, VARCHAR);
DROP FUNCTION IF EXISTS can_edit_digital_human(VARCHAR, VARCHAR);
DROP FUNCTION IF EXISTS can_access_digital_human(VARCHAR, VARCHAR, VARCHAR);

-- 删除视图
DROP VIEW IF EXISTS v_digital_human_stats;

-- 删除表
DROP TABLE IF EXISTS credit_consumption CASCADE;
DROP TABLE IF EXISTS digital_human CASCADE;
DROP TABLE IF EXISTS preset_voice CASCADE;

-- 删除 assistant 表字段
ALTER TABLE assistant DROP COLUMN IF EXISTS digital_human_id;

-- 删除触发器和函数
DROP TRIGGER IF EXISTS trigger_digital_human_updated_at ON digital_human;
DROP FUNCTION IF EXISTS update_digital_human_updated_at();
*/

-- ============================================
-- 10. 迁移完成
-- ============================================

-- 插入迁移记录（如果有迁移管理表）
-- INSERT INTO schema_migrations (version, description, applied_at)
-- VALUES ('20260922_digital_human_system', '数字人系统表结构', CURRENT_TIMESTAMP);

-- 打印完成信息
DO $$
BEGIN
  RAISE NOTICE '========================================';
  RAISE NOTICE '数字人系统数据库迁移完成';
  RAISE NOTICE '版本: v1.0';
  RAISE NOTICE '日期: 2026-09-22';
  RAISE NOTICE '========================================';
  RAISE NOTICE '已创建表:';
  RAISE NOTICE '  - digital_human (数字人实体表)';
  RAISE NOTICE '  - preset_voice (预设音色库表)';
  RAISE NOTICE '  - credit_consumption (积分消耗记录表)';
  RAISE NOTICE '已修改表:';
  RAISE NOTICE '  - assistant (新增 digital_human_id 字段)';
  RAISE NOTICE '已创建视图:';
  RAISE NOTICE '  - v_digital_human_stats (数字人统计视图)';
  RAISE NOTICE '已创建函数:';
  RAISE NOTICE '  - can_access_digital_human()';
  RAISE NOTICE '  - can_edit_digital_human()';
  RAISE NOTICE '  - get_digital_human_overview()';
  RAISE NOTICE '  - cleanup_old_archived_digital_humans()';
  RAISE NOTICE '已插入初始数据:';
  RAISE NOTICE '  - 5 个预设音色';
  RAISE NOTICE '========================================';
END $$;

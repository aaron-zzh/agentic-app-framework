-- ============================================================
-- v7: 为所有表添加 Operator 相关字段
-- create_by_type / update_by_type / owner_id
-- 历史数据默认 HUMAN，owner_id 回填为 create_by
-- ============================================================

-- 批量添加列：通过 DO 块遍历所有含 create_by 的表
DO $$
DECLARE
    tbl TEXT;
BEGIN
    FOR tbl IN
        SELECT table_name
        FROM information_schema.columns
        WHERE column_name = 'create_by'
          AND table_schema = 'public'
    LOOP
        -- create_by_type
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = tbl AND column_name = 'create_by_type' AND table_schema = 'public'
        ) THEN
            EXECUTE format('ALTER TABLE %I ADD COLUMN create_by_type VARCHAR(16) DEFAULT ''HUMAN''', tbl);
        END IF;

        -- update_by_type
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = tbl AND column_name = 'update_by_type' AND table_schema = 'public'
        ) THEN
            EXECUTE format('ALTER TABLE %I ADD COLUMN update_by_type VARCHAR(16) DEFAULT ''HUMAN''', tbl);
        END IF;

        -- owner_id
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = tbl AND column_name = 'owner_id' AND table_schema = 'public'
        ) THEN
            EXECUTE format('ALTER TABLE %I ADD COLUMN owner_id BIGINT', tbl);
            -- 回填：历史数据 owner_id = create_by
            EXECUTE format('UPDATE %I SET owner_id = create_by WHERE owner_id IS NULL', tbl);
        END IF;
    END LOOP;
END $$;

-- 为 owner_id 添加索引（高频查询"我的数据"）
DO $$
DECLARE
    tbl TEXT;
BEGIN
    FOR tbl IN
        SELECT table_name
        FROM information_schema.columns
        WHERE column_name = 'owner_id'
          AND table_schema = 'public'
    LOOP
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_owner_id ON %I (owner_id)', tbl, tbl);
    END LOOP;
END $$;

COMMENT ON COLUMN sys_user.create_by_type IS '创建者类型：HUMAN / AI';
COMMENT ON COLUMN sys_user.update_by_type IS '更新者类型：HUMAN / AI';
COMMENT ON COLUMN sys_user.owner_id IS '数据归属者（始终为 user.id）';

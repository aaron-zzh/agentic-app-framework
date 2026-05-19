-- 记忆系统：原子记忆引擎表结构
-- AtomMemoryEngine 支撑 Cognition.Memory 模块

-- 启用 pgvector 扩展（如未启用）
CREATE EXTENSION IF NOT EXISTS vector;

-- 记忆原子表
CREATE TABLE memory_atom (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         BIGINT NOT NULL,
    scope           VARCHAR(20) NOT NULL,
    content         TEXT NOT NULL,
    embedding       vector(1536),
    event_time      TIMESTAMP WITH TIME ZONE NOT NULL,
    valid_from      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    valid_to        TIMESTAMP WITH TIME ZONE,
    weight          DOUBLE PRECISION NOT NULL DEFAULT 0.5,
    access_count    INT NOT NULL DEFAULT 0,
    last_accessed_at TIMESTAMP WITH TIME ZONE,
    tags            TEXT[],
    metadata        JSONB,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- 索引
CREATE INDEX idx_memory_atom_user_scope ON memory_atom(user_id, scope);
CREATE INDEX idx_memory_atom_user_event ON memory_atom(user_id, event_time DESC);
CREATE INDEX idx_memory_atom_user_weight ON memory_atom(user_id, weight DESC);
CREATE INDEX idx_memory_atom_valid_to ON memory_atom(valid_to);
CREATE INDEX idx_memory_atom_tags ON memory_atom USING GIN(tags);

-- 向量索引（IVFFlat，适合中等数据量；大数据量可换 HNSW）
CREATE INDEX idx_memory_atom_embedding ON memory_atom
    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- 记忆关系表（原子间关系）
CREATE TABLE memory_relation (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id       UUID NOT NULL REFERENCES memory_atom(id) ON DELETE CASCADE,
    target_id       UUID NOT NULL REFERENCES memory_atom(id) ON DELETE CASCADE,
    relation_type   VARCHAR(50) NOT NULL,
    weight          DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_memory_relation_source ON memory_relation(source_id);
CREATE INDEX idx_memory_relation_target ON memory_relation(target_id);
CREATE INDEX idx_memory_relation_type ON memory_relation(relation_type);

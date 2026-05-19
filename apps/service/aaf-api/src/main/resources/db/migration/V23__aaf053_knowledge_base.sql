-- AAF-053 知识库基础表结构
-- 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 知识库
CREATE TABLE knowledge_base (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100)  NOT NULL,
    description     VARCHAR(500),
    embedding_model VARCHAR(50)   NOT NULL DEFAULT 'text-embedding-3-small',
    chunk_strategy  VARCHAR(30)   NOT NULL DEFAULT 'fixed_size',
    chunk_size      INT           NOT NULL DEFAULT 512,
    chunk_overlap   INT           NOT NULL DEFAULT 64,
    status          SMALLINT      NOT NULL DEFAULT 0,
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN       NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE knowledge_base IS '知识库';

-- 知识库文档
CREATE TABLE knowledge_document (
    id                BIGSERIAL PRIMARY KEY,
    knowledge_base_id BIGINT        NOT NULL REFERENCES knowledge_base(id),
    title             VARCHAR(200)  NOT NULL,
    file_path         VARCHAR(500),
    file_type         VARCHAR(20),
    file_size         BIGINT        DEFAULT 0,
    content_hash      VARCHAR(64),
    status            SMALLINT      NOT NULL DEFAULT 0,
    error_message     TEXT,
    chunk_count       INT           DEFAULT 0,
    created_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    deleted           BOOLEAN       NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_knowledge_document_base_id ON knowledge_document(knowledge_base_id);
COMMENT ON TABLE knowledge_document IS '知识库文档';

-- 知识库文本块
CREATE TABLE knowledge_chunk (
    id                BIGSERIAL PRIMARY KEY,
    document_id       BIGINT   NOT NULL REFERENCES knowledge_document(id),
    knowledge_base_id BIGINT   NOT NULL REFERENCES knowledge_base(id),
    content           TEXT     NOT NULL,
    chunk_index       INT      NOT NULL DEFAULT 0,
    metadata          JSONB,
    token_count       INT      DEFAULT 0,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_knowledge_chunk_document_id ON knowledge_chunk(document_id);
CREATE INDEX idx_knowledge_chunk_base_id ON knowledge_chunk(knowledge_base_id);
COMMENT ON TABLE knowledge_chunk IS '知识库文本块';

-- 知识库向量嵌入（兼容 Spring AI PgVectorStore 列要求）
CREATE TABLE knowledge_embedding (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content           TEXT,
    metadata          JSONB,
    embedding         vector(1536),
    chunk_id          BIGINT        REFERENCES knowledge_chunk(id),
    knowledge_base_id BIGINT        REFERENCES knowledge_base(id),
    model_name        VARCHAR(50),
    created_at        TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_knowledge_embedding_chunk_id ON knowledge_embedding(chunk_id);
CREATE INDEX idx_knowledge_embedding_base_id ON knowledge_embedding(knowledge_base_id);
-- HNSW 索引加速向量相似度搜索
CREATE INDEX idx_knowledge_embedding_hnsw ON knowledge_embedding
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

COMMENT ON TABLE knowledge_embedding IS '知识库向量嵌入（兼容 Spring AI VectorStore）';

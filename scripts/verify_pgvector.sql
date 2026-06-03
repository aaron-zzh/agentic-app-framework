-- 验证 pgvector 扩展和向量距离查询是否可用。
-- 使用临时表，连接结束后自动清理，不污染业务 schema。

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TEMP TABLE test_items (
    id SERIAL PRIMARY KEY,
    embedding VECTOR(3),
    name TEXT NOT NULL
);

INSERT INTO test_items (embedding, name) VALUES
    ('[1,1,1]', 'item1'),
    ('[2,2,2]', 'item2');

SELECT id, name, embedding
FROM test_items
ORDER BY embedding <-> '[1.5,1.5,1.5]'::vector;

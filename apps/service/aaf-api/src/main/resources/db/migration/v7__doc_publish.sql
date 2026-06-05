-- doc_document 加 publish 字段（draft / published）
ALTER TABLE doc_document
    ADD COLUMN IF NOT EXISTS publish VARCHAR(20) NOT NULL DEFAULT 'draft';

COMMENT ON COLUMN doc_document.publish IS '发布状态：draft=草稿，published=已发布';

CREATE INDEX IF NOT EXISTS idx_doc_document_publish ON doc_document (publish) WHERE deleted = FALSE;

package com.xuejiai.aaf.module.document.api;

/** Assistant 工具写入数据库草稿的显式模块边界。 */
public interface DocumentDraftApi {

    DraftDocument upsertDraft(DraftUpsertCommand command);

    record DraftUpsertCommand(
            String title,
            String content,
            String documentType,
            Long ownerId,
            Long orgId,
            Long workspaceId) {}

    record DraftDocument(Long id) {}
}

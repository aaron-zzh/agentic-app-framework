package com.xuejiai.aaf.module.document.api;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/** 文档引用与只读查询接口，供其他业务模块建立稳定文档引用。 */
public interface DocumentReferenceApi {

    List<OwnedDocument> requireAccessible(
            Collection<Long> documentIds, Long ownerId, Long orgId, Long workspaceId);

    DocumentPage query(DocumentQuery query);

    record OwnedDocument(Long id, Long ownerId) {}

    record DocumentQuery(
            Long ownerId,
            Long orgId,
            Long workspaceId,
            String documentType,
            String keyword,
            Collection<Long> includeIds,
            Collection<Long> excludeIds,
            int pageNo,
            int pageSize) {

        public DocumentQuery {
            includeIds = includeIds == null ? null : List.copyOf(new LinkedHashSet<>(includeIds));
            excludeIds = excludeIds == null ? null : List.copyOf(new LinkedHashSet<>(excludeIds));
        }
    }

    record DocumentItem(Long documentId, String title, String content, LocalDateTime updateTime) {}

    record DocumentPage(
            List<DocumentItem> list, long total, int pageNo, int pageSize, boolean hasMore) {

        public DocumentPage {
            list = List.copyOf(list);
        }
    }
}

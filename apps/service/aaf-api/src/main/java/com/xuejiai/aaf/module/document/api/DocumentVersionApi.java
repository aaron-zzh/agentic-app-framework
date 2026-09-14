package com.xuejiai.aaf.module.document.api;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 不可变文档版本的创建、读取、恢复与 Diff 边界。 */
public interface DocumentVersionApi {

    DocumentVersionView create(CreateDocumentVersionCommand command);

    DocumentVersionView append(AppendDocumentVersionCommand command);

    DocumentVersionView restore(RestoreDocumentVersionCommand command);

    List<DocumentVersionView> history(Long documentId, Long ownerId, Long orgId, Long workspaceId);

    DocumentDiffView diff(
            Long documentId,
            Long baseVersionId,
            Long targetVersionId,
            Long ownerId,
            Long orgId,
            Long workspaceId);

    DocumentVersionView requireAccessible(
            Long documentVersionId, Long ownerId, Long orgId, Long workspaceId);

    List<DocumentVersionView> requireAccessible(
            Collection<Long> documentVersionIds, Long ownerId, Long orgId, Long workspaceId);

    Optional<DocumentVersionView> findExact(Long documentId, Long documentVersionId);

    Map<DocumentVersionIdentity, DocumentVersionView> findExact(
            Collection<DocumentVersionIdentity> identities);

    Map<Long, DocumentVersionView> findCurrent(Collection<Long> documentIds);

    record CreateDocumentVersionCommand(
            String title,
            String content,
            String format,
            String documentType,
            String sourceType,
            String sourceRef,
            String changeSummary,
            Long ownerId,
            Long orgId,
            Long workspaceId) {}

    record AppendDocumentVersionCommand(
            Long documentId,
            Long expectedCurrentVersionId,
            String title,
            String content,
            String format,
            String sourceType,
            String sourceRef,
            String changeSummary,
            Long ownerId,
            Long orgId,
            Long workspaceId) {}

    record RestoreDocumentVersionCommand(
            Long documentId,
            Long sourceVersionId,
            Long expectedCurrentVersionId,
            String changeSummary,
            Long ownerId,
            Long orgId,
            Long workspaceId) {}

    record DocumentVersionIdentity(Long documentId, Long documentVersionId) {
        public DocumentVersionIdentity {
            java.util.Objects.requireNonNull(documentId, "documentId 不能为空");
            java.util.Objects.requireNonNull(documentVersionId, "documentVersionId 不能为空");
        }
    }

    record DocumentVersionView(
            Long id,
            Long documentId,
            Integer versionNo,
            Long parentVersionId,
            String title,
            String content,
            String format,
            String checksum,
            String sourceType,
            String sourceRef,
            String changeSummary,
            Long createdBy,
            String createdByType,
            LocalDateTime createdTime) {}

    record DocumentDiffView(
            Long documentId,
            Long baseVersionId,
            Long targetVersionId,
            List<DocumentDiffHunk> hunks) {
        public DocumentDiffView {
            hunks = List.copyOf(hunks);
        }
    }

    record DocumentDiffHunk(
            int oldStart, int oldCount, int newStart, int newCount, List<DocumentDiffLine> lines) {
        public DocumentDiffHunk {
            lines = List.copyOf(lines);
        }
    }

    record DocumentDiffLine(String type, Integer oldLine, Integer newLine, String content) {}
}

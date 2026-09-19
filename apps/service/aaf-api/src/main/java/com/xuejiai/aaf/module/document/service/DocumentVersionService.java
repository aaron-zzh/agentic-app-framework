package com.xuejiai.aaf.module.document.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.document.api.DocumentVersionApi;
import com.xuejiai.aaf.module.document.domain.Document;
import com.xuejiai.aaf.module.document.domain.DocumentVersion;
import com.xuejiai.aaf.module.document.event.DocumentVersionCreatedEvent;
import com.xuejiai.aaf.module.document.repository.DocumentRepository;
import com.xuejiai.aaf.module.document.repository.DocumentVersionRepository;

import lombok.RequiredArgsConstructor;

/** 文档版本事实服务；正文只追加，不提供原地更新或删除。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentVersionService implements DocumentVersionApi {

    private static final Set<String> FORMATS = Set.of("MARKDOWN", "HTML", "PLAIN_TEXT", "JSON");
    private static final Set<String> SOURCES = Set.of("MANUAL", "AIGC", "IMPORT", "RESTORE");

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public DocumentVersionView create(CreateDocumentVersionCommand command) {
        validateCreate(command);
        var title = command.title().trim();
        var content = normalizeContent(command.content());
        var format = normalizeFormat(command.format());
        var sourceType = normalizeSource(command.sourceType());

        var document = new Document();
        document.setTitle(title);
        document.setDocType(command.documentType().trim());
        document.setStatus("active");
        document.setPublish("draft");
        document.setOwnerId(command.ownerId());
        document.setOrgId(command.orgId());
        document.setWorkspaceId(command.workspaceId());
        documentRepository.saveAndFlush(document);

        var version =
                newVersion(
                        document,
                        1,
                        null,
                        title,
                        content,
                        format,
                        sourceType,
                        command.sourceRef(),
                        command.changeSummary());
        versionRepository.saveAndFlush(version);
        document.setCurrentVersionId(version.getId());
        document.setContent(content);
        documentRepository.saveAndFlush(document);
        eventPublisher.publishEvent(
                new DocumentVersionCreatedEvent(
                        version.getId(),
                        version.getOwnerId(),
                        version.getOrgId(),
                        version.getWorkspaceId(),
                        version.getSourceType()));
        return toView(version);
    }

    @Override
    @Transactional
    public DocumentVersionView append(AppendDocumentVersionCommand command) {
        if (command == null
                || command.documentId() == null
                || command.ownerId() == null
                || command.orgId() == null
                || command.content() == null) {
            throw badRequest("追加文档版本参数不正确");
        }
        var document =
                requireLockedDocument(
                        command.documentId(),
                        command.ownerId(),
                        command.orgId(),
                        command.workspaceId());
        if (!Objects.equals(document.getCurrentVersionId(), command.expectedCurrentVersionId())) {
            throw conflict("文档当前版本已变化，请刷新后重试");
        }
        var current = document.getCurrentVersionId() == null ? null : current(document);
        var title =
                command.title() == null || command.title().isBlank()
                        ? document.getTitle()
                        : command.title().trim();
        var content = normalizeContent(command.content());
        var format =
                command.format() == null || command.format().isBlank()
                        ? current == null ? "MARKDOWN" : current.getFormat()
                        : normalizeFormat(command.format());
        var sourceType = normalizeSource(command.sourceType());
        if (current != null
                && Objects.equals(current.getTitle(), title)
                && Objects.equals(current.getContent(), content)
                && Objects.equals(current.getFormat(), format)) {
            return toView(current);
        }
        var version =
                newVersion(
                        document,
                        current == null ? 1 : current.getVersionNo() + 1,
                        current == null ? null : current.getId(),
                        title,
                        content,
                        format,
                        sourceType,
                        command.sourceRef(),
                        command.changeSummary());
        versionRepository.saveAndFlush(version);
        document.setTitle(title);
        document.setCurrentVersionId(version.getId());
        document.setContent(content);
        documentRepository.saveAndFlush(document);
        eventPublisher.publishEvent(
                new DocumentVersionCreatedEvent(
                        version.getId(),
                        version.getOwnerId(),
                        version.getOrgId(),
                        version.getWorkspaceId(),
                        version.getSourceType()));
        return toView(version);
    }

    @Override
    @Transactional
    public DocumentVersionView restore(RestoreDocumentVersionCommand command) {
        if (command == null
                || command.documentId() == null
                || command.sourceVersionId() == null
                || command.expectedCurrentVersionId() == null) {
            throw badRequest("恢复文档版本参数不正确");
        }
        var source =
                requireAccessible(
                        command.sourceVersionId(),
                        command.ownerId(),
                        command.orgId(),
                        command.workspaceId());
        if (!Objects.equals(source.documentId(), command.documentId())) {
            throw badRequest("恢复来源版本不属于目标文档");
        }
        return append(
                new AppendDocumentVersionCommand(
                        command.documentId(),
                        command.expectedCurrentVersionId(),
                        source.title(),
                        source.content(),
                        source.format(),
                        "RESTORE",
                        "document-version:" + source.id(),
                        command.changeSummary() == null || command.changeSummary().isBlank()
                                ? "恢复至 v" + source.versionNo()
                                : command.changeSummary(),
                        command.ownerId(),
                        command.orgId(),
                        command.workspaceId()));
    }

    @Override
    public List<DocumentVersionView> history(
            Long documentId, Long ownerId, Long orgId, Long workspaceId) {
        requireAccessibleDocument(documentId, ownerId, orgId, workspaceId);
        return versionRepository
                .findAllByDocumentIdAndDeletedFalseOrderByVersionNoDesc(documentId)
                .stream()
                .map(this::toView)
                .toList();
    }

    @Override
    public DocumentDiffView diff(
            Long documentId,
            Long baseVersionId,
            Long targetVersionId,
            Long ownerId,
            Long orgId,
            Long workspaceId) {
        requireAccessibleDocument(documentId, ownerId, orgId, workspaceId);
        var base = requireVersion(documentId, baseVersionId);
        var target = requireVersion(documentId, targetVersionId);
        return new DocumentDiffView(
                documentId,
                base.getId(),
                target.getId(),
                DocumentLineDiff.compare(base.getContent(), target.getContent()));
    }

    @Override
    public DocumentVersionView requireAccessible(
            Long documentVersionId, Long ownerId, Long orgId, Long workspaceId) {
        if (documentVersionId == null) {
            throw notFound();
        }
        var version = versionRepository.findById(documentVersionId).orElseThrow(this::notFound);
        requireAccessibleDocument(version.getDocumentId(), ownerId, orgId, workspaceId);
        if (Boolean.TRUE.equals(version.getDeleted())) {
            throw notFound();
        }
        return toView(version);
    }

    @Override
    public List<DocumentVersionView> requireAccessible(
            Collection<Long> documentVersionIds, Long ownerId, Long orgId, Long workspaceId) {
        if (documentVersionIds == null || documentVersionIds.isEmpty()) {
            return List.of();
        }
        var ids = new LinkedHashSet<>(documentVersionIds);
        if (ids.contains(null)) {
            throw notFound();
        }
        var byId =
                versionRepository.findAllByIdIn(ids).stream()
                        .filter(version -> !Boolean.TRUE.equals(version.getDeleted()))
                        .collect(Collectors.toMap(DocumentVersion::getId, value -> value));
        return ids.stream()
                .map(byId::get)
                .map(
                        version -> {
                            if (version == null) throw notFound();
                            requireAccessibleDocument(
                                    version.getDocumentId(), ownerId, orgId, workspaceId);
                            return toView(version);
                        })
                .toList();
    }

    @Override
    public java.util.Optional<DocumentVersionView> findExact(
            Long documentId, Long documentVersionId) {
        if (documentId == null || documentVersionId == null) return java.util.Optional.empty();
        return versionRepository
                .findByIdAndDocumentIdAndDeletedFalse(documentVersionId, documentId)
                .map(this::toView);
    }

    @Override
    public Map<DocumentVersionIdentity, DocumentVersionView> findExact(
            Collection<DocumentVersionIdentity> identities) {
        if (identities == null || identities.isEmpty()) return Map.of();
        var requested = new LinkedHashSet<>(identities);
        var versionsById =
                versionRepository
                        .findAllByIdIn(
                                requested.stream()
                                        .map(DocumentVersionIdentity::documentVersionId)
                                        .toList())
                        .stream()
                        .filter(version -> !Boolean.TRUE.equals(version.getDeleted()))
                        .collect(Collectors.toMap(DocumentVersion::getId, value -> value));
        return requested.stream()
                .filter(
                        identity -> {
                            var version = versionsById.get(identity.documentVersionId());
                            return version != null
                                    && Objects.equals(
                                            version.getDocumentId(), identity.documentId());
                        })
                .collect(
                        Collectors.toUnmodifiableMap(
                                identity -> identity,
                                identity ->
                                        toView(versionsById.get(identity.documentVersionId()))));
    }

    @Override
    public Map<Long, DocumentVersionView> findCurrent(Collection<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) return Map.of();
        var documents = documentRepository.findAllById(new LinkedHashSet<>(documentIds));
        var versionIds =
                documents.stream()
                        .map(Document::getCurrentVersionId)
                        .filter(Objects::nonNull)
                        .toList();
        var versions =
                versionRepository.findAllByIdIn(versionIds).stream()
                        .collect(Collectors.toMap(DocumentVersion::getId, value -> value));
        return documents.stream()
                .filter(document -> document.getCurrentVersionId() != null)
                .filter(document -> versions.containsKey(document.getCurrentVersionId()))
                .collect(
                        Collectors.toUnmodifiableMap(
                                Document::getId,
                                document -> toView(versions.get(document.getCurrentVersionId()))));
    }

    public List<Long> matchingCurrentDocumentIds(String query) {
        if (query == null || query.isBlank()) return List.of();
        return versionRepository.findCurrentDocumentIdsMatching(query.trim());
    }

    public Document hydratePublished(Document document) {
        if (document == null || document.getPublishedVersionId() == null) {
            throw notFound();
        }
        var published = requireVersion(document.getId(), document.getPublishedVersionId());
        document.setCurrentVersionNo(published.getVersionNo());
        document.setContent(published.getContent());
        return document;
    }

    public Document hydrateCurrent(Document document) {
        if (document == null) return null;
        var current = current(document);
        document.setCurrentVersionNo(current.getVersionNo());
        document.setContent(current.getContent());
        return document;
    }

    public DocumentVersion current(Document document) {
        if (document == null || document.getCurrentVersionId() == null) {
            throw notFound();
        }
        return requireVersion(document.getId(), document.getCurrentVersionId());
    }

    private DocumentVersion newVersion(
            Document document,
            int versionNo,
            Long parentVersionId,
            String title,
            String content,
            String format,
            String sourceType,
            String sourceRef,
            String changeSummary) {
        validateText(sourceRef, 255, "版本来源引用");
        validateText(changeSummary, 500, "版本修改说明");
        var version =
                DocumentVersion.create(
                        document.getId(),
                        versionNo,
                        parentVersionId,
                        title,
                        content,
                        format,
                        checksum(title, content, format),
                        sourceType,
                        blankToNull(sourceRef),
                        blankToNull(changeSummary));
        version.setOwnerId(document.getOwnerId());
        version.setOrgId(document.getOrgId());
        version.setWorkspaceId(document.getWorkspaceId());
        return version;
    }

    private Document requireLockedDocument(
            Long documentId, Long ownerId, Long orgId, Long workspaceId) {
        var document = documentRepository.findByIdForUpdate(documentId).orElseThrow(this::notFound);
        requireScope(document, ownerId, orgId, workspaceId);
        return document;
    }

    private Document requireAccessibleDocument(
            Long documentId, Long ownerId, Long orgId, Long workspaceId) {
        if (documentId == null) throw notFound();
        var document = documentRepository.findById(documentId).orElseThrow(this::notFound);
        requireScope(document, ownerId, orgId, workspaceId);
        return document;
    }

    private void requireScope(Document document, Long ownerId, Long orgId, Long workspaceId) {
        if (Boolean.TRUE.equals(document.getDeleted())
                || !"active".equals(document.getStatus())
                || ownerId == null
                || orgId == null
                || !Objects.equals(document.getOwnerId(), ownerId)
                || !Objects.equals(document.getOrgId(), orgId)
                || !workspaceAccessible(document.getWorkspaceId(), workspaceId)) {
            throw notFound();
        }
    }

    private DocumentVersion requireVersion(Long documentId, Long versionId) {
        return versionRepository
                .findByIdAndDocumentIdAndDeletedFalse(versionId, documentId)
                .orElseThrow(this::notFound);
    }

    private void validateCreate(CreateDocumentVersionCommand command) {
        if (command == null
                || command.ownerId() == null
                || command.orgId() == null
                || command.title() == null
                || command.title().isBlank()
                || command.title().length() > 200
                || command.content() == null
                || command.documentType() == null
                || command.documentType().isBlank()) {
            throw badRequest("文档版本参数不正确");
        }
    }

    private String normalizeFormat(String value) {
        var format =
                value == null || value.isBlank()
                        ? "MARKDOWN"
                        : value.trim().toUpperCase(Locale.ROOT);
        if (!FORMATS.contains(format)) {
            throw badRequest("不支持的文档格式: " + value);
        }
        return format;
    }

    private String normalizeSource(String value) {
        var source =
                value == null || value.isBlank() ? "MANUAL" : value.trim().toUpperCase(Locale.ROOT);
        if (!SOURCES.contains(source)) {
            throw badRequest("不支持的文档版本来源: " + value);
        }
        return source;
    }

    private String normalizeContent(String value) {
        return Objects.requireNonNullElse(value, "").replace("\r\n", "\n").replace('\r', '\n');
    }

    private void validateText(String value, int maxLength, String field) {
        if (value != null && value.length() > maxLength) {
            throw badRequest(field + "长度不能超过 " + maxLength);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean workspaceAccessible(Long actual, Long requested) {
        return requested == null
                ? actual == null
                : actual == null || Objects.equals(actual, requested);
    }

    private String checksum(String title, String content, String format) {
        try {
            var bytes =
                    (title + "\u0000" + format + "\u0000" + content)
                            .getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("JDK 不支持 SHA-256", error);
        }
    }

    private DocumentVersionView toView(DocumentVersion version) {
        return new DocumentVersionView(
                version.getId(),
                version.getDocumentId(),
                version.getVersionNo(),
                version.getParentVersionId(),
                version.getTitle(),
                version.getContent(),
                version.getFormat(),
                version.getChecksum(),
                version.getSourceType(),
                version.getSourceRef(),
                version.getChangeSummary(),
                version.getCreateBy(),
                version.getCreateByType(),
                version.getCreateTime());
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(GlobalErrorCode.BAD_REQUEST, message);
    }

    private BusinessException conflict(String message) {
        return new BusinessException(409, message);
    }

    private BusinessException notFound() {
        return new BusinessException(GlobalErrorCode.NOT_FOUND, "文档或版本不存在");
    }
}

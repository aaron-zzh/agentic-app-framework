package com.xuejiai.aaf.module.document.service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.document.api.DocumentDraftApi;
import com.xuejiai.aaf.module.document.api.DocumentDraftApi.DraftDocument;
import com.xuejiai.aaf.module.document.api.DocumentDraftApi.DraftUpsertCommand;
import com.xuejiai.aaf.module.document.api.DocumentReferenceApi;
import com.xuejiai.aaf.module.document.api.DocumentSourceApi;
import com.xuejiai.aaf.module.document.api.DocumentSourceApi.SourceDocument;
import com.xuejiai.aaf.module.document.api.DocumentSourceApi.SourceDocumentCommand;
import com.xuejiai.aaf.module.document.api.DocumentVersionApi.AppendDocumentVersionCommand;
import com.xuejiai.aaf.module.document.api.DocumentVersionApi.CreateDocumentVersionCommand;
import com.xuejiai.aaf.module.document.api.DocumentVersionApi.DocumentDiffView;
import com.xuejiai.aaf.module.document.api.DocumentVersionApi.DocumentVersionView;
import com.xuejiai.aaf.module.document.api.DocumentVersionApi.RestoreDocumentVersionCommand;
import com.xuejiai.aaf.module.document.api.DocumentVersionSemanticMetadataApi.SemanticMetadataView;
import com.xuejiai.aaf.module.document.domain.DocLink;
import com.xuejiai.aaf.module.document.domain.Document;
import com.xuejiai.aaf.module.document.repository.DocLinkRepository;
import com.xuejiai.aaf.module.document.repository.DocumentRepository;
import com.xuejiai.aaf.module.document.vo.*;

/** 文档管理服务。 */
@Service
public class DocumentService implements DocumentSourceApi, DocumentReferenceApi, DocumentDraftApi {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;
    private final DocLinkRepository docLinkRepository;
    private final DocImportService docImportService;
    private final DocumentVersionService documentVersionService;
    private final DocumentVersionSemanticMetadataService semanticMetadataService;
    private final OperatorContext operatorContext;

    /** 按 owner、组织、工作区与文档隔离的 SSE 订阅者。 */
    private final ConcurrentHashMap<SubscriptionKey, CopyOnWriteArrayList<SseEmitter>> subscribers =
            new ConcurrentHashMap<>();

    public DocumentService(
            DocumentRepository documentRepository,
            DocLinkRepository docLinkRepository,
            DocImportService docImportService,
            DocumentVersionService documentVersionService,
            DocumentVersionSemanticMetadataService semanticMetadataService,
            OperatorContext operatorContext) {
        this.documentRepository = documentRepository;
        this.docLinkRepository = docLinkRepository;
        this.docImportService = docImportService;
        this.documentVersionService = documentVersionService;
        this.semanticMetadataService = semanticMetadataService;
        this.operatorContext = operatorContext;
    }

    /** 将已登记文件建档为知识来源文档。 */
    @Override
    @Transactional
    public SourceDocument register(SourceDocumentCommand command) {
        var scope = requireCurrentWriteScope();
        if (command == null || !Objects.equals(command.ownerId(), scope.ownerId())) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "文档建档范围与当前账号不一致");
        }
        if (command.content() == null) {
            var document = new Document();
            document.setTitle(command.title());
            document.setFilePath(command.sourceKey());
            document.setSourceFileId(command.sourceFileId());
            document.setDocType(command.documentType());
            document.setStatus("active");
            document.setPublish("draft");
            document.setOwnerId(scope.ownerId());
            document.setOrgId(scope.orgId());
            document.setWorkspaceId(scope.workspaceId());
            documentRepository.saveAndFlush(document);
            return new SourceDocument(
                    document.getId(),
                    null,
                    null,
                    document.getSourceFileId(),
                    document.getFilePath(),
                    document.getOwnerId());
        }
        var version =
                documentVersionService.create(
                        new CreateDocumentVersionCommand(
                                command.title(),
                                command.content(),
                                "MARKDOWN",
                                command.documentType(),
                                "IMPORT",
                                command.sourceKey(),
                                "登记知识来源",
                                scope.ownerId(),
                                scope.orgId(),
                                scope.workspaceId()));
        var document = documentRepository.findById(version.documentId()).orElseThrow();
        document.setFilePath(command.sourceKey());
        document.setSourceFileId(command.sourceFileId());
        documentRepository.save(document);
        return new SourceDocument(
                document.getId(),
                version.id(),
                version.checksum(),
                document.getSourceFileId(),
                document.getFilePath(),
                document.getOwnerId());
    }

    /** 使用工具网关传入的可信范围保存数据库草稿；既有目标按 CAS 追加版本且永不发布。 */
    @Override
    @Transactional
    public DraftDocument upsertDraft(DraftUpsertCommand command) {
        if (command == null
                || command.ownerId() == null
                || command.ownerId() <= 0
                || command.orgId() == null
                || command.orgId() <= 0
                || command.title() == null
                || command.title().isBlank()
                || command.content() == null
                || command.content().isBlank()
                || command.documentType() == null
                || command.documentType().isBlank()
                || (command.documentId() == null) != (command.expectedCurrentVersionId() == null)
                || (command.documentId() != null && command.documentId() <= 0)
                || (command.expectedCurrentVersionId() != null
                        && command.expectedCurrentVersionId() <= 0)) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "草稿参数不正确");
        }
        DocumentVersionView version;
        if (command.documentId() == null) {
            version =
                    documentVersionService.create(
                            new CreateDocumentVersionCommand(
                                    command.title().trim(),
                                    command.content(),
                                    "MARKDOWN",
                                    command.documentType().trim(),
                                    "AIGC",
                                    "content.draft.upsert",
                                    "文案智能体生成草稿",
                                    command.ownerId(),
                                    command.orgId(),
                                    command.workspaceId()));
        } else {
            version =
                    documentVersionService.append(
                            new AppendDocumentVersionCommand(
                                    command.documentId(),
                                    command.expectedCurrentVersionId(),
                                    command.title().trim(),
                                    command.content(),
                                    "MARKDOWN",
                                    "AIGC",
                                    "content.draft.upsert",
                                    "文案智能体生成新版本",
                                    command.ownerId(),
                                    command.orgId(),
                                    command.workspaceId()));
        }
        return new DraftDocument(version.documentId(), version.id());
    }

    /** 统计当前范围内的有效文档数量。 */
    @Transactional(readOnly = true)
    public long countCurrentUser() {
        var ownerId = operatorContext.currentOwnerId().orElse(null);
        return ownerId == null ? 0L : documentRepository.count(currentScopeSpecification(ownerId));
    }

    /** 获取当前范围内的文档列表（不含正文）。 */
    @Transactional(readOnly = true)
    public List<DocListItemVO> listCurrentUser() {
        var ownerId = operatorContext.currentOwnerId().orElse(null);
        if (ownerId == null) {
            return List.of();
        }
        return documentRepository
                .findAll(currentScopeSpecification(ownerId), Sort.by(Sort.Order.desc("updateTime")))
                .stream()
                .map(
                        document ->
                                new DocListItemVO(
                                        document.getId(),
                                        document.getTitle(),
                                        document.getDocType(),
                                        document.getPublish(),
                                        document.getUpdateTime()))
                .toList();
    }

    /** 获取当前范围内的文档树。 */
    public List<DocTreeNodeVO> getTree() {
        var ownerId = operatorContext.currentOwnerId().orElse(null);
        if (ownerId == null) {
            return List.of();
        }
        return buildTree(
                documentRepository.findAll(
                        currentScopeSpecification(ownerId),
                        Sort.by(Sort.Order.desc("createTime"))));
    }

    /** 获取当前范围内的有效文档详情。 */
    public Document getById(Long id) {
        var ownerId = operatorContext.currentOwnerId().orElse(null);
        return documentVersionService.hydrateCurrent(requireCurrentScopeDocument(id, ownerId));
    }

    /** 校验一组文档在指定 owner、组织与工作区范围内均可见且处于有效状态。 */
    @Override
    @Transactional(readOnly = true)
    public List<OwnedDocument> requireAccessible(
            Collection<Long> documentIds, Long ownerId, Long orgId, Long workspaceId) {
        if (documentIds == null || documentIds.isEmpty()) {
            return List.of();
        }
        return new LinkedHashSet<>(documentIds)
                .stream()
                        .map(
                                documentId ->
                                        requireAccessibleDocument(
                                                documentId, ownerId, orgId, workspaceId))
                        .map(document -> new OwnedDocument(document.getId(), document.getOwnerId()))
                        .toList();
    }

    /** 按稳定文档条件执行数据库分页，供只读业务 facade 聚合使用。 */
    @Override
    @Transactional(readOnly = true)
    public DocumentPage query(DocumentQuery request) {
        if (request == null
                || request.ownerId() == null
                || request.orgId() == null
                || request.documentType() == null
                || request.documentType().isBlank()
                || request.pageNo() < 1
                || request.pageSize() < 1
                || request.pageSize() > 50) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "文档分页查询参数不正确");
        }
        var currentOwnerId =
                operatorContext
                        .currentOwnerId()
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.UNAUTHORIZED, "账号未登录"));
        var currentOrgId = OrgContext.getCurrentOrgId();
        if (currentOrgId == null) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "当前组织不能为空");
        }
        if (OrgContext.isAllWorkspaces()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "文档分页查询不支持全部工作区");
        }
        if (!Objects.equals(request.ownerId(), currentOwnerId)
                || !Objects.equals(request.orgId(), currentOrgId)
                || !Objects.equals(request.workspaceId(), OrgContext.getCurrentWorkspaceId())) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "文档查询范围与当前上下文不一致");
        }
        if (request.includeIds() != null && request.excludeIds() != null) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "文档包含与排除条件不可同时设置");
        }
        if (request.keyword() != null && request.keyword().length() > 100) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "关键词长度不能超过100");
        }

        var pageable =
                PageRequest.of(
                        request.pageNo() - 1,
                        request.pageSize(),
                        Sort.by(Sort.Order.desc("updateTime"), Sort.Order.desc("id")));
        if (request.includeIds() != null && request.includeIds().isEmpty()) {
            return new DocumentPage(List.of(), 0L, request.pageNo(), request.pageSize(), false);
        }
        var keywordDocumentIds =
                request.keyword() == null || request.keyword().isBlank()
                        ? null
                        : documentVersionService.matchingCurrentDocumentIds(request.keyword());
        if (keywordDocumentIds != null && keywordDocumentIds.isEmpty()) {
            return new DocumentPage(List.of(), 0L, request.pageNo(), request.pageSize(), false);
        }

        Specification<Document> specification =
                (root, criteriaQuery, builder) -> {
                    var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
                    predicates.add(builder.equal(root.get("ownerId"), request.ownerId()));
                    predicates.add(builder.equal(root.get("orgId"), request.orgId()));
                    if (request.workspaceId() == null) {
                        predicates.add(builder.isNull(root.get("workspaceId")));
                    } else {
                        predicates.add(
                                builder.or(
                                        builder.isNull(root.get("workspaceId")),
                                        builder.equal(
                                                root.get("workspaceId"), request.workspaceId())));
                    }
                    predicates.add(
                            builder.equal(root.get("docType"), request.documentType().trim()));
                    predicates.add(builder.equal(root.get("status"), "active"));
                    predicates.add(builder.isFalse(root.get("deleted")));
                    if (keywordDocumentIds != null) {
                        predicates.add(root.get("id").in(keywordDocumentIds));
                    }
                    if (request.includeIds() != null) {
                        predicates.add(root.get("id").in(request.includeIds()));
                    } else if (request.excludeIds() != null && !request.excludeIds().isEmpty()) {
                        predicates.add(builder.not(root.get("id").in(request.excludeIds())));
                    }
                    return builder.and(
                            predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
                };
        var page = documentRepository.findAll(specification, pageable);
        var list =
                page.getContent().stream()
                        .map(
                                document -> {
                                    var current = documentVersionService.current(document);
                                    document.setCurrentVersionNo(current.getVersionNo());
                                    document.setContent(current.getContent());
                                    return new DocumentItem(
                                            document.getId(),
                                            current.getId(),
                                            current.getVersionNo(),
                                            document.getTitle(),
                                            current.getContent(),
                                            document.getUpdateTime());
                                })
                        .toList();
        return new DocumentPage(
                list,
                page.getTotalElements(),
                request.pageNo(),
                request.pageSize(),
                page.hasNext());
    }

    /** 获取文档版本历史。 */
    @Transactional(readOnly = true)
    public List<DocumentVersionView> versions(Long documentId) {
        var document = getById(documentId);
        return documentVersionService.history(
                documentId, document.getOwnerId(), document.getOrgId(), document.getWorkspaceId());
    }

    /** 获取当前范围内属于指定文档的单个不可变版本。 */
    @Transactional(readOnly = true)
    public DocumentVersionView version(Long documentId, Long documentVersionId) {
        var ownerId = operatorContext.currentOwnerId().orElse(null);
        requireCurrentScopeDocument(documentId, ownerId);
        return documentVersionService
                .findExact(documentId, documentVersionId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "文档版本不存在"));
    }

    /** 获取精确正文版本的语义元数据。 */
    @Transactional(readOnly = true)
    public SemanticMetadataView semanticMetadata(Long documentId, Long documentVersionId) {
        version(documentId, documentVersionId);
        return semanticMetadataService
                .find(documentVersionId)
                .orElseThrow(
                        () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "文档版本语义元数据不存在"));
    }

    /** 人工覆盖精确正文版本的语义元数据，不修改正文版本。 */
    @Transactional
    public SemanticMetadataView updateSemanticMetadata(
            Long documentId,
            Long documentVersionId,
            DocumentVersionSemanticMetadataUpdateDTO request) {
        version(documentId, documentVersionId);
        return semanticMetadataService.updateManual(
                documentVersionId,
                request.expectedMetadataVersion(),
                request.summary(),
                request.topics(),
                request.language());
    }

    /** 比较同一文档的任意两个不可变版本。 */
    @Transactional(readOnly = true)
    public DocumentDiffView diff(Long documentId, Long baseVersionId, Long targetVersionId) {
        var document = getById(documentId);
        return documentVersionService.diff(
                documentId,
                baseVersionId,
                targetVersionId,
                document.getOwnerId(),
                document.getOrgId(),
                document.getWorkspaceId());
    }

    /** 将历史内容恢复为新的当前版本。 */
    @Transactional
    public Document restore(
            Long documentId,
            Long sourceVersionId,
            Long expectedCurrentVersionId,
            String changeSummary) {
        var document = getById(documentId);
        documentVersionService.restore(
                new RestoreDocumentVersionCommand(
                        documentId,
                        sourceVersionId,
                        expectedCurrentVersionId,
                        changeSummary,
                        document.getOwnerId(),
                        document.getOrgId(),
                        document.getWorkspaceId()));
        var restored = getById(documentId);
        if (restored.getFilePath() != null) {
            writeToLocalFile(restored.getFilePath(), restored.getContent());
        }
        broadcastChange(restored);
        return restored;
    }

    /** 更新文档；标题或正文变化时追加不可变版本，同步写回本地文件。 */
    @Transactional
    public Document update(Long id, DocUpdateDTO dto) {
        var currentDocument = getById(id);
        var changesVersion = dto.title() != null || dto.content() != null;
        if (changesVersion) {
            if (dto.expectedCurrentVersionId() == null) {
                throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "修改标题或正文时必须提供当前版本编号");
            }
            documentVersionService.append(
                    new AppendDocumentVersionCommand(
                            id,
                            dto.expectedCurrentVersionId(),
                            dto.title(),
                            dto.content() == null ? currentDocument.getContent() : dto.content(),
                            "MARKDOWN",
                            "MANUAL",
                            "document:" + id,
                            dto.changeSummary(),
                            currentDocument.getOwnerId(),
                            currentDocument.getOrgId(),
                            currentDocument.getWorkspaceId()));
            currentDocument = getById(id);
        }
        if (dto.docType() != null) currentDocument.setDocType(dto.docType());
        documentRepository.save(currentDocument);

        if (changesVersion && currentDocument.getFilePath() != null) {
            writeToLocalFile(currentDocument.getFilePath(), currentDocument.getContent());
        }

        docImportService.extractLinks();
        broadcastChange(currentDocument);
        return currentDocument;
    }

    /** 发布调用方确认的精确当前文档版本。 */
    @Transactional
    public Document publish(Long id, Long expectedCurrentVersionId) {
        if (expectedCurrentVersionId == null || expectedCurrentVersionId <= 0) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "发布文档必须提供当前版本编号");
        }
        var ownerId = operatorContext.currentOwnerId().orElse(null);
        var orgId = OrgContext.getCurrentOrgId();
        if (ownerId == null
                || orgId == null
                || OrgContext.isAllOrganizations()
                || OrgContext.isAllWorkspaces()) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "文档不存在");
        }
        var workspaceId = OrgContext.getCurrentWorkspaceId();
        var document =
                documentRepository
                        .findByIdForUpdate(id)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "文档不存在"));
        var workspaceAccessible =
                workspaceId == null
                        ? document.getWorkspaceId() == null
                        : document.getWorkspaceId() == null
                                || Objects.equals(document.getWorkspaceId(), workspaceId);
        if (!"active".equals(document.getStatus())
                || !Objects.equals(document.getOwnerId(), ownerId)
                || !Objects.equals(document.getOrgId(), orgId)
                || !workspaceAccessible) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "文档不存在");
        }
        if (!Objects.equals(document.getCurrentVersionId(), expectedCurrentVersionId)) {
            throw new BusinessException(409, "文档当前版本已变化，请刷新后重新确认发布");
        }
        applyPublishState(document, "published");
        documentRepository.save(document);
        var published = documentVersionService.hydrateCurrent(document);
        broadcastChange(published);
        return published;
    }

    /** 取消发布（转草稿并清除发布版本指针）。 */
    @Transactional
    public Document unpublish(Long id) {
        Document doc = getById(id);
        applyPublishState(doc, "draft");
        documentRepository.save(doc);
        broadcastChange(doc);
        return doc;
    }

    /** 删除文档（逻辑删除，走 {@code @SQLDelete} 软删除）。 */
    @Transactional
    public void delete(Long id) {
        Document doc = getById(id);
        documentRepository.delete(doc);
        broadcastChange(doc);
    }

    /** 获取所有已发布文档（公开端）。 */
    public List<Document> getPublished() {
        return documentRepository.findByPublishOrderByUpdateTimeDesc("published").stream()
                .filter(document -> document.getPublishedVersionId() != null)
                .map(documentVersionService::hydratePublished)
                .toList();
    }

    /**
     * 获取单个已发布文档的精确版本正文（公开端，无需认证）。
     *
     * <p>只在 {@code publish=published} 且存在 {@code publishedVersionId} 时返回，未发布或不存在均视为不存在；
     * title/content/createTime 全部来自 published 精确版本本身，不混用可变根实体的 current 标题或更新时间，
     * 避免公开正文与未发布草稿的展示内容串糅。{@code docType} 是文档身份分类标签（如 guide/spec）， 语义上不属于正文历史、从未由 DocumentVersion
     * 承载，因此按设计使用根实体当前值。
     *
     * <p>{@code @OrgIgnore}：匿名公开访问没有组织上下文，业务范围已由 published/deleted 过滤限定， 不依赖组织边界，需豁免 {@code
     * OrgFilterAspect} 的 orgId 缺失 fail-closed 拒绝。
     */
    @com.xuejiai.aaf.framework.org.OrgIgnore
    public com.xuejiai.aaf.module.document.vo.DocumentPublicVO getPublishedById(Long id) {
        var document =
                documentRepository
                        .findById(id)
                        .filter(candidate -> !Boolean.TRUE.equals(candidate.getDeleted()))
                        .filter(candidate -> "active".equals(candidate.getStatus()))
                        .filter(candidate -> "published".equals(candidate.getPublish()))
                        .filter(candidate -> candidate.getPublishedVersionId() != null)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "文档不存在或未发布"));
        var published =
                documentVersionService
                        .findExact(document.getId(), document.getPublishedVersionId())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "文档不存在或未发布"));
        return new com.xuejiai.aaf.module.document.vo.DocumentPublicVO(
                document.getId(),
                published.id(),
                published.title(),
                published.content(),
                document.getDocType(),
                published.createdTime());
    }

    /** 新建文档：写入本地文件 + 插入数据库 + 提取链接。 */
    @Transactional
    public DocTreeNodeVO create(DocCreateDTO dto) {
        var scope = requireCurrentWriteScope();
        String content = dto.content() != null ? dto.content() : "";

        // filePath 有值时才做路径安全校验和本地文件写入
        if (dto.filePath() != null && !dto.filePath().isBlank()) {
            validateFilePath(dto.filePath());
            writeToLocalFile(dto.filePath(), content);
        }

        var version =
                documentVersionService.create(
                        new CreateDocumentVersionCommand(
                                dto.title(),
                                content,
                                "MARKDOWN",
                                dto.docType() != null ? dto.docType() : "guide",
                                "MANUAL",
                                dto.filePath(),
                                "创建文档",
                                scope.ownerId(),
                                scope.orgId(),
                                scope.workspaceId()));
        var doc = documentRepository.findById(version.documentId()).orElseThrow();
        doc.setFilePath(dto.filePath());
        if (dto.publish() != null) {
            applyPublishState(doc, dto.publish());
        }
        documentRepository.save(doc);

        // 提取链接关系
        docImportService.extractLinks();

        return new DocTreeNodeVO(
                doc.getId(),
                doc.getTitle(),
                doc.getTitle(),
                doc.getFilePath(),
                false,
                doc.getDocType(),
                doc.getCurrentVersionId(),
                doc.getPublish(),
                List.of());
    }

    /** 订阅当前 owner、组织与工作区范围内的文档变更事件。 */
    public SseEmitter subscribe(Long docId) {
        var ownerId =
                operatorContext
                        .currentOwnerId()
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.UNAUTHORIZED, "账号未登录"));
        var orgId = OrgContext.getCurrentOrgId();
        if (orgId == null || OrgContext.isAllOrganizations() || OrgContext.isAllWorkspaces()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "文档事件订阅需要具体组织与工作区范围");
        }
        var effectiveDocId = docId == null ? 0L : docId;
        if (effectiveDocId < 0) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "文档 ID 不正确");
        }
        if (effectiveDocId != 0L) {
            requireCurrentScopeDocument(effectiveDocId, ownerId);
        }

        var key =
                new SubscriptionKey(
                        ownerId, orgId, OrgContext.getCurrentWorkspaceId(), effectiveDocId);
        var emitter = new SseEmitter(5 * 60 * 1000L);
        var list = subscribers.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        Runnable remove =
                () -> {
                    list.remove(emitter);
                    if (list.isEmpty()) {
                        subscribers.remove(key, list);
                    }
                };
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(error -> remove.run());
        return emitter;
    }

    /** 导入 PDF（委托 DocImportService）。 */
    public Document importPdf(MultipartFile file) throws IOException {
        var scope = requireCurrentWriteScope();
        return docImportService.importPdf(
                file, scope.ownerId(), scope.orgId(), scope.workspaceId());
    }

    /** 全文检索。 */
    public List<DocSearchResultVO> search(String query) {
        var ownerId = operatorContext.currentOwnerId().orElse(null);
        var orgId = OrgContext.getCurrentOrgId();
        if (ownerId == null
                || orgId == null
                || OrgContext.isAllOrganizations()
                || OrgContext.isAllWorkspaces()) {
            return List.of();
        }
        return documentRepository
                .fullTextSearch(query, ownerId, orgId, OrgContext.getCurrentWorkspaceId())
                .stream()
                .map(
                        doc -> {
                            documentVersionService.hydrateCurrent(doc);
                            return new DocSearchResultVO(
                                    doc.getId(),
                                    doc.getTitle(),
                                    doc.getFilePath(),
                                    extractSnippet(doc.getContent(), query));
                        })
                .collect(Collectors.toList());
    }

    /** 获取文档关系图数据（nodes + edges，1 跳）。 */
    public DocRelationGraphVO getRelations(Long id) {
        Document center = getById(id);

        List<DocLink> outgoing = docLinkRepository.findBySourceId(id);
        List<DocLink> incoming = docLinkRepository.findByTargetId(id);

        Set<Long> nodeIds = new HashSet<>();
        nodeIds.add(id);
        outgoing.forEach(link -> nodeIds.add(link.getTargetId()));
        incoming.forEach(link -> nodeIds.add(link.getSourceId()));

        var nodeSpecification =
                currentScopeSpecification(center.getOwnerId())
                        .and((root, criteriaQuery, builder) -> root.get("id").in(nodeIds));
        List<Document> nodes = documentRepository.findAll(nodeSpecification);
        Set<Long> visibleNodeIds = nodes.stream().map(Document::getId).collect(Collectors.toSet());

        List<DocRelationGraphVO.Edge> edges = new ArrayList<>();
        outgoing.stream()
                .filter(
                        link ->
                                visibleNodeIds.contains(link.getSourceId())
                                        && visibleNodeIds.contains(link.getTargetId()))
                .forEach(
                        link ->
                                edges.add(
                                        new DocRelationGraphVO.Edge(
                                                link.getSourceId(),
                                                link.getTargetId(),
                                                link.getLinkType())));
        incoming.stream()
                .filter(
                        link ->
                                visibleNodeIds.contains(link.getSourceId())
                                        && visibleNodeIds.contains(link.getTargetId()))
                .forEach(
                        link ->
                                edges.add(
                                        new DocRelationGraphVO.Edge(
                                                link.getSourceId(),
                                                link.getTargetId(),
                                                link.getLinkType())));

        List<DocRelationGraphVO.Node> graphNodes =
                nodes.stream()
                        .map(
                                document ->
                                        new DocRelationGraphVO.Node(
                                                document.getId(),
                                                document.getTitle(),
                                                document.getFilePath(),
                                                document.getId().equals(id)))
                        .collect(Collectors.toList());

        return new DocRelationGraphVO(graphNodes, edges);
    }

    private Document requireCurrentScopeDocument(Long documentId, Long ownerId) {
        var orgId = OrgContext.getCurrentOrgId();
        if (ownerId == null
                || orgId == null
                || OrgContext.isAllOrganizations()
                || OrgContext.isAllWorkspaces()) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "文档不存在");
        }
        return requireAccessibleDocument(
                documentId, ownerId, orgId, OrgContext.getCurrentWorkspaceId());
    }

    private WriteScope requireCurrentWriteScope() {
        var ownerId =
                operatorContext
                        .currentOwnerId()
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.UNAUTHORIZED, "账号未登录"));
        var orgId = OrgContext.getCurrentOrgId();
        if (orgId == null || OrgContext.isAllOrganizations() || OrgContext.isAllWorkspaces()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "文档写入需要具体组织与工作区范围");
        }
        return new WriteScope(ownerId, orgId, OrgContext.getCurrentWorkspaceId());
    }

    private Specification<Document> currentScopeSpecification(Long ownerId) {
        var orgId = OrgContext.getCurrentOrgId();
        var workspaceId = OrgContext.getCurrentWorkspaceId();
        return (root, criteriaQuery, builder) -> {
            if (ownerId == null
                    || orgId == null
                    || OrgContext.isAllOrganizations()
                    || OrgContext.isAllWorkspaces()) {
                return builder.disjunction();
            }
            var workspacePredicate =
                    workspaceId == null
                            ? builder.isNull(root.get("workspaceId"))
                            : builder.or(
                                    builder.isNull(root.get("workspaceId")),
                                    builder.equal(root.get("workspaceId"), workspaceId));
            return builder.and(
                    builder.equal(root.get("ownerId"), ownerId),
                    builder.equal(root.get("orgId"), orgId),
                    workspacePredicate,
                    builder.equal(root.get("status"), "active"),
                    builder.isFalse(root.get("deleted")));
        };
    }

    private Document requireOwnedDocument(Long documentId, Long ownerId) {
        if (ownerId == null) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "文档不存在");
        }
        return documentRepository
                .findByIdAndOwnerIdAndStatus(documentId, ownerId, "active")
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "文档不存在"));
    }

    private Document requireAccessibleDocument(
            Long documentId, Long ownerId, Long orgId, Long workspaceId) {
        if (ownerId == null || orgId == null) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "文档不存在");
        }
        var document = requireOwnedDocument(documentId, ownerId);
        var workspaceAccessible =
                workspaceId == null
                        ? document.getWorkspaceId() == null
                        : document.getWorkspaceId() == null
                                || Objects.equals(document.getWorkspaceId(), workspaceId);
        if (!Objects.equals(document.getOwnerId(), ownerId)
                || !Objects.equals(document.getOrgId(), orgId)
                || !workspaceAccessible) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "文档不存在");
        }
        return document;
    }

    private void applyPublishState(Document document, String publish) {
        if (!Set.of("draft", "published").contains(publish)) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "文档发布状态不正确");
        }
        document.setPublish(publish);
        document.setPublishedVersionId(
                "published".equals(publish) ? document.getCurrentVersionId() : null);
    }

    private void validateFilePath(String filePath) {
        if (filePath == null || !filePath.startsWith("docs/")) {
            throw new IllegalArgumentException("filePath 必须以 docs/ 开头");
        }
        // 规范化后检查是否仍在 docs/ 目录下（防 ../穿越）
        Path normalized = Path.of(filePath).normalize();
        if (!normalized.startsWith("docs")) {
            throw new IllegalArgumentException("filePath 包含非法路径穿越");
        }
        // 禁止包含空字节（防 null-byte 注入）
        if (filePath.contains("\0")) {
            throw new IllegalArgumentException("filePath 包含非法字符");
        }
    }

    private void broadcastChange(Document document) {
        String json =
                com.xuejiai.aaf.common.util.JsonUtils.toJsonString(
                        java.util.Map.of(
                                "type",
                                "doc_updated",
                                "docId",
                                document.getId(),
                                "title",
                                document.getTitle() != null ? document.getTitle() : ""));
        subscribers.forEach(
                (key, list) -> {
                    if (!key.accepts(document)) {
                        return;
                    }
                    for (var emitter : list) {
                        try {
                            emitter.send(SseEmitter.event().data(json));
                        } catch (IOException error) {
                            list.remove(emitter);
                        }
                    }
                    if (list.isEmpty()) {
                        subscribers.remove(key, list);
                    }
                });
    }

    private void writeToLocalFile(String filePath, String content) {
        try {
            Path path = Path.of(filePath).toAbsolutePath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
            log.info("文档已同步写回本地：{}", filePath);
        } catch (IOException e) {
            log.error("写回本地文件失败：{}，原因：{}", filePath, e.getMessage());
        }
    }

    private String extractSnippet(String content, String query) {
        if (content == null || query == null) return "";
        int idx = content.toLowerCase().indexOf(query.toLowerCase());
        if (idx < 0) return content.substring(0, Math.min(100, content.length()));
        int start = Math.max(0, idx - 50);
        int end = Math.min(content.length(), idx + query.length() + 50);
        return content.substring(start, end);
    }

    private List<DocTreeNodeVO> buildTree(List<Document> docs) {
        Map<String, DocTreeNodeVO> dirMap = new LinkedHashMap<>();
        List<DocTreeNodeVO> roots = new ArrayList<>();

        for (Document doc : docs) {
            String path = doc.getFilePath() != null ? doc.getFilePath() : doc.getTitle();
            String[] parts = path.split("/");

            DocTreeNodeVO parent = null;
            var currentPath = new StringBuilder();
            for (int i = 0; i < parts.length - 1; i++) {
                if (i > 0) currentPath.append("/");
                currentPath.append(parts[i]);
                String dirPath = currentPath.toString();
                final int idx = i;
                final DocTreeNodeVO parentRef = parent;
                DocTreeNodeVO dir =
                        dirMap.computeIfAbsent(
                                dirPath,
                                k -> {
                                    var node =
                                            new DocTreeNodeVO(
                                                    null,
                                                    parts[idx],
                                                    null,
                                                    k,
                                                    true,
                                                    null,
                                                    null,
                                                    null,
                                                    new ArrayList<>());
                                    if (parentRef == null) roots.add(node);
                                    else parentRef.children().add(node);
                                    return node;
                                });
                parent = dir;
            }

            var leaf =
                    new DocTreeNodeVO(
                            doc.getId(),
                            doc.getTitle(),
                            doc.getTitle(),
                            path,
                            false,
                            doc.getDocType(),
                            doc.getCurrentVersionId(),
                            doc.getPublish(),
                            List.of());
            if (parent != null) parent.children().add(leaf);
            else roots.add(leaf);
        }
        return roots;
    }

    private record WriteScope(Long ownerId, Long orgId, Long workspaceId) {}

    private record SubscriptionKey(Long ownerId, Long orgId, Long workspaceId, Long documentId) {

        private boolean accepts(Document document) {
            var workspaceAccessible =
                    workspaceId == null
                            ? document.getWorkspaceId() == null
                            : document.getWorkspaceId() == null
                                    || Objects.equals(document.getWorkspaceId(), workspaceId);
            return Objects.equals(document.getOwnerId(), ownerId)
                    && Objects.equals(document.getOrgId(), orgId)
                    && workspaceAccessible
                    && (documentId == 0L || Objects.equals(document.getId(), documentId));
        }
    }
}

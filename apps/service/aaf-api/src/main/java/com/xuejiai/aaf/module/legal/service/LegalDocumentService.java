package com.xuejiai.aaf.module.legal.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.document.domain.Document;
import com.xuejiai.aaf.module.document.repository.DocumentRepository;
import com.xuejiai.aaf.module.document.service.DocumentVersionService;
import com.xuejiai.aaf.module.legal.domain.LegalDocumentType;
import com.xuejiai.aaf.module.legal.domain.UserConsent;
import com.xuejiai.aaf.module.legal.repository.UserConsentRepository;
import com.xuejiai.aaf.module.legal.vo.LegalDocumentVO;
import com.xuejiai.aaf.module.legal.vo.PendingConsentVO;

/**
 * 法律文档服务（服务条款 / 隐私政策）。
 *
 * <p>读取层：从 {@code doc_document} 中按 docType + publish=published 过滤出最新一条法律文档。
 *
 * <p>合规层：维护 {@code sys_user_consent} 同意快照，登录后比对最新文档版本与用户最近同意版本，决定是否触发弹窗。
 *
 * @author AaronZZH &amp; Kiro
 */
@Service
public class LegalDocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionService documentVersionService;
    private final UserConsentRepository userConsentRepository;

    public LegalDocumentService(
            DocumentRepository documentRepository,
            DocumentVersionService documentVersionService,
            UserConsentRepository userConsentRepository) {
        this.documentRepository = documentRepository;
        this.documentVersionService = documentVersionService;
        this.userConsentRepository = userConsentRepository;
    }

    /** 获取指定类型的最新已发布法律文档，未发布则抛 NOT_FOUND。 */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    @com.xuejiai.aaf.framework.org.OrgIgnore
    public LegalDocumentVO getLatestPublished(LegalDocumentType type) {
        Document doc = findLatestPublished(type);
        return toVO(doc, type);
    }

    /** 获取指定类型精确版本的已发布法律文档；版本必须等于当前 publishedVersionId，否则拒绝（已过期，需刷新重读）。 */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    @com.xuejiai.aaf.framework.org.OrgIgnore
    public LegalDocumentVO getPublishedVersion(LegalDocumentType type, Long documentVersionId) {
        Document doc = findLatestPublished(type);
        if (!doc.getPublishedVersionId().equals(documentVersionId)) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "该法律文档版本已不是当前发布版本");
        }
        return toVO(doc, type);
    }

    /** 计算用户对所有法律文档类型的待同意列表（最新版本未同意 → 计入）。 */
    public PendingConsentVO listPendingForUser(Long userId) {
        List<LegalDocumentVO> pending = new ArrayList<>();
        for (LegalDocumentType type : LegalDocumentType.values()) {
            Optional<Document> latest = findLatestPublishedOptional(type);
            if (latest.isEmpty()) continue;
            Document doc = latest.get();
            Long latestVersionId = doc.getPublishedVersionId();
            Optional<UserConsent> last =
                    userConsentRepository.findFirstByUserIdAndDocumentTypeOrderByConsentTimeDesc(
                            userId, type.getCode());
            if (last.isEmpty() || !latestVersionId.equals(last.get().getDocumentVersionId())) {
                pending.add(toVO(doc, type));
            }
        }
        return new PendingConsentVO(pending.size(), pending);
    }

    /** 记录一次用户同意，写入快照；要求提交精确版本与当前发布版本一致，拒绝隐式同意未阅读的更新版本。 */
    @Transactional
    public void recordConsent(
            Long userId,
            Long documentId,
            Long documentVersionId,
            String clientIp,
            String sourceApp) {
        Document doc =
                documentRepository
                        .findByIdForUpdate(documentId)
                        .filter(candidate -> !Boolean.TRUE.equals(candidate.getDeleted()))
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "文档不存在"));
        if (!"published".equals(doc.getPublish()) || doc.getPublishedVersionId() == null) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "文档未发布，无法记录同意");
        }
        if (!doc.getPublishedVersionId().equals(documentVersionId)) {
            throw new BusinessException(409, "文档已发布新版本，请刷新后重新阅读并同意");
        }
        LegalDocumentType type =
                LegalDocumentType.fromCode(doc.getDocType())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.BAD_REQUEST, "非法律文档，不支持同意"));
        var publishedVersion =
                documentVersionService
                        .findExact(doc.getId(), doc.getPublishedVersionId())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "法律文档发布版本不存在"));
        UserConsent consent = new UserConsent();
        consent.setUserId(userId);
        consent.setDocumentId(doc.getId());
        consent.setDocumentVersionId(doc.getPublishedVersionId());
        consent.setDocumentType(type.getCode());
        consent.setDocumentVersion(resolveVersion(publishedVersion, doc.getFrontMatter()));
        consent.setConsentTime(LocalDateTime.now());
        consent.setConsentIp(clientIp);
        consent.setSourceApp(sourceApp);
        userConsentRepository.save(consent);
    }

    // ===== internals =====

    private Document findLatestPublished(LegalDocumentType type) {
        return findLatestPublishedOptional(type)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "法律文档不存在或未发布"));
    }

    private Optional<Document> findLatestPublishedOptional(LegalDocumentType type) {
        // 复用 DocumentRepository 现有方法，过滤 docType 后取第一条（按 update_time desc 排序）
        return documentRepository.findByPublishOrderByUpdateTimeDesc("published").stream()
                .filter(d -> type.getCode().equals(d.getDocType()))
                .filter(d -> "active".equals(d.getStatus()))
                .filter(d -> d.getPublishedVersionId() != null)
                .findFirst();
    }

    private LegalDocumentVO toVO(Document doc, LegalDocumentType type) {
        var published =
                documentVersionService
                        .findExact(doc.getId(), doc.getPublishedVersionId())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "法律文档不存在或未发布"));
        Map<String, Object> fm = doc.getFrontMatter();
        String version = resolveVersion(published, fm);
        String effectiveDate =
                fm.get("effectiveDate") != null ? String.valueOf(fm.get("effectiveDate")) : null;
        return new LegalDocumentVO(
                doc.getId(),
                type.getCode(),
                published.title(),
                published.content(),
                published.id(),
                version,
                effectiveDate,
                published.createdTime());
    }

    /** 法律展示版本优先使用不可变正文版本号，Front Matter 仅作为业务标签。 */
    private String resolveVersion(
            com.xuejiai.aaf.module.document.api.DocumentVersionApi.DocumentVersionView published,
            Map<String, Object> frontMatter) {
        if (published.versionNo() != null) return "v" + published.versionNo();
        Object value = frontMatter.get("version");
        return value == null || String.valueOf(value).isBlank() ? "unknown" : String.valueOf(value);
    }
}

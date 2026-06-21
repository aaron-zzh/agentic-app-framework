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
    private final UserConsentRepository userConsentRepository;

    public LegalDocumentService(
            DocumentRepository documentRepository, UserConsentRepository userConsentRepository) {
        this.documentRepository = documentRepository;
        this.userConsentRepository = userConsentRepository;
    }

    /** 获取指定类型的最新已发布法律文档，未发布则抛 NOT_FOUND。 */
    public LegalDocumentVO getLatestPublished(LegalDocumentType type) {
        Document doc = findLatestPublished(type);
        return toVO(doc, type);
    }

    /** 计算用户对所有法律文档类型的待同意列表（最新版本未同意 → 计入）。 */
    public PendingConsentVO listPendingForUser(Long userId) {
        List<LegalDocumentVO> pending = new ArrayList<>();
        for (LegalDocumentType type : LegalDocumentType.values()) {
            Optional<Document> latest = findLatestPublishedOptional(type);
            if (latest.isEmpty()) continue;
            Document doc = latest.get();
            String latestVersion = resolveVersion(doc);
            Optional<UserConsent> last =
                    userConsentRepository.findFirstByUserIdAndDocumentTypeOrderByConsentTimeDesc(
                            userId, type.getCode());
            if (last.isEmpty() || !latestVersion.equals(last.get().getDocumentVersion())) {
                pending.add(toVO(doc, type));
            }
        }
        return new PendingConsentVO(pending.size(), pending);
    }

    /** 记录一次用户同意，写入快照。 */
    @Transactional
    public void recordConsent(Long userId, Long documentId, String clientIp, String sourceApp) {
        Document doc =
                documentRepository
                        .findById(documentId)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "文档不存在"));
        if (!"published".equals(doc.getPublish())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "文档未发布，无法记录同意");
        }
        LegalDocumentType type =
                LegalDocumentType.fromCode(doc.getDocType())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.BAD_REQUEST, "非法律文档，不支持同意"));
        UserConsent consent = new UserConsent();
        consent.setUserId(userId);
        consent.setDocumentId(doc.getId());
        consent.setDocumentType(type.getCode());
        consent.setDocumentVersion(resolveVersion(doc));
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
                .findFirst();
    }

    private LegalDocumentVO toVO(Document doc, LegalDocumentType type) {
        Map<String, Object> fm = doc.getFrontMatter();
        String version = resolveVersion(doc);
        String effectiveDate =
                fm.get("effectiveDate") != null ? String.valueOf(fm.get("effectiveDate")) : null;
        return new LegalDocumentVO(
                doc.getId(),
                type.getCode(),
                doc.getTitle(),
                doc.getContent(),
                version,
                effectiveDate,
                doc.getUpdateTime());
    }

    /** 取版本号：front_matter.version 优先，否则回退到 update_time 字符串保证唯一性。 */
    private String resolveVersion(Document doc) {
        Object v = doc.getFrontMatter().get("version");
        if (v != null && !String.valueOf(v).isBlank()) return String.valueOf(v);
        return doc.getUpdateTime() != null ? doc.getUpdateTime().toString() : "unknown";
    }
}

/**
 * 记忆管理 Service。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.module.ai.memory;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.framework.intelligent.cognition.application.MemoryGovernanceService;
import com.xuejiai.aaf.framework.intelligent.cognition.application.MemoryGovernanceService.RememberStatus;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.ExplicitConfirmation;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.MemorySubject;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.SubjectKind;
import com.xuejiai.aaf.framework.intelligent.cognition.port.MemoryManagementPort;
import com.xuejiai.aaf.framework.intelligent.cognition.port.MemoryRecallPort;
import com.xuejiai.aaf.framework.intelligent.cognition.port.MemoryRecallPort.RecallQuery;
import com.xuejiai.aaf.framework.intelligent.cognition.port.MemoryWritePort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MemoryManagementService {

    private static final String DEFAULT_SCOPE = "long_term";
    private static final String SCOPE_TAG_PREFIX = "scope:";
    private static final int MAX_SEARCH_RESULTS = 100;
    private static final Set<String> ALLOWED_SCOPES =
            Set.of("short_term", DEFAULT_SCOPE, "episodic", "procedural");

    private final MemoryRecallPort memoryRecall;
    private final MemoryWritePort memoryWriter;
    private final MemoryManagementPort memoryManagement;
    private final MemoryGovernanceService memoryGovernance;
    private final OperatorContext operatorContext;

    public PageResult<MemoryRecordVO> list(String scope, Pageable pageable) {
        var page =
                memoryManagement.list(
                        subject(),
                        optionalScope(scope),
                        Math.toIntExact(pageable.getOffset()),
                        pageable.getPageSize(),
                        Instant.now());
        return new PageResult<>(page.items().stream().map(this::toVO).toList(), page.total());
    }

    public List<MemoryRecordVO> search(String keyword, String scope) {
        return memoryManagement
                .search(
                        subject(),
                        keyword,
                        optionalScope(scope),
                        MAX_SEARCH_RESULTS,
                        Instant.now())
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Transactional
    public void add(String content, String scope) {
        var at = Instant.now();
        var outcome =
                memoryGovernance.remember(
                        subject(),
                        content,
                        List.of(SCOPE_TAG_PREFIX + normalizeScope(scope)),
                        at);
        if (outcome.status() == RememberStatus.REJECTED
                || outcome.status() == RememberStatus.CONFLICT) {
            throw new IllegalArgumentException(outcome.reason());
        }
    }

    public List<MemoryRecordVO> semanticSearch(String query, Integer topK) {
        var limit = topK != null && topK > 0 ? topK : 8;
        return memoryRecall
                .recall(new RecallQuery(subject(), query, limit, Integer.MAX_VALUE, Instant.now()))
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Transactional
    public void delete(List<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        var subject = subject();
        var at = Instant.now();
        memoryWriter.forget(subject, List.copyOf(ids), confirmation(subject, "用户删除记忆", at), at);
    }

    @Transactional
    public void clearByScope(String scope) {
        var subject = subject();
        var at = Instant.now();
        memoryManagement.forgetScope(
                subject,
                normalizeScope(scope),
                confirmation(subject, "用户清空记忆范围", at),
                at);
    }

    public MemoryStatsVO getStats() {
        var subject = subject();
        var at = Instant.now();
        long shortTerm = memoryManagement.count(subject, "short_term", at);
        long longTerm = memoryManagement.count(subject, DEFAULT_SCOPE, at);
        long episodic = memoryManagement.count(subject, "episodic", at);
        long procedural = memoryManagement.count(subject, "procedural", at);
        return new MemoryStatsVO(
                shortTerm,
                longTerm,
                episodic,
                procedural,
                shortTerm + longTerm + episodic + procedural);
    }

    private String optionalScope(String scope) {
        return scope == null || scope.isBlank() ? null : normalizeScope(scope);
    }

    private String scopeOf(MemoryRecord memory) {
        return memory.tags().stream()
                .filter(tag -> tag.startsWith(SCOPE_TAG_PREFIX))
                .map(tag -> tag.substring(SCOPE_TAG_PREFIX.length()))
                .findFirst()
                .orElse(DEFAULT_SCOPE);
    }

    private String normalizeScope(String scope) {
        var normalized =
                scope == null || scope.isBlank()
                        ? DEFAULT_SCOPE
                        : scope.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_SCOPES.contains(normalized)) {
            throw new IllegalArgumentException("不支持的记忆范围: " + normalized);
        }
        return normalized;
    }

    private MemorySubject subject() {
        var orgId = OrgContext.getCurrentOrgId();
        if (orgId == null) throw new AccessDeniedException("请求缺少组织上下文");
        var userId =
                operatorContext
                        .currentOwnerId()
                        .orElseThrow(() -> new AccessDeniedException("请求未认证"));
        return new MemorySubject(
                new TenantId(orgId.toString()), SubjectKind.USER, userId.toString());
    }

    private ExplicitConfirmation confirmation(MemorySubject subject, String reason, Instant at) {
        return new ExplicitConfirmation(true, "USER/" + subject.subjectId(), reason, at);
    }

    private MemoryRecordVO toVO(MemoryRecord memory) {
        return new MemoryRecordVO(
                memory.memoryId(),
                scopeOf(memory),
                memory.content(),
                memory.redactedSummary(),
                memory.importance(),
                memory.confidence(),
                memory.privacy().name(),
                memory.tags(),
                memory.expiresAt(),
                memory.createdAt());
    }
}

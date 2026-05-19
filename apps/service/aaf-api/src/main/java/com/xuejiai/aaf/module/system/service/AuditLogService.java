package com.xuejiai.aaf.module.system.service;

import java.time.LocalDateTime;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.security.ActorContext;
import com.xuejiai.aaf.module.system.domain.AuditLog;
import com.xuejiai.aaf.module.system.repository.AuditLogRepository;
import com.xuejiai.aaf.module.system.vo.AuditLogPageDTO;
import com.xuejiai.aaf.module.system.vo.AuditLogVO;

import lombok.RequiredArgsConstructor;

/** 审计日志业务逻辑。 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ActorContext actorContext;

    /** 记录审计日志 */
    @Transactional
    public void record(String entityType, Long entityId, String action, String changes) {
        var log = new AuditLog();
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setAction(action);
        log.setChanges(changes);
        log.setUserId(actorContext.currentUserId().orElse(null));
        log.setIp(resolveIp());
        log.setCreatedAt(LocalDateTime.now());
        auditLogRepository.save(log);
    }

    /** 分页查询审计日志 */
    @Transactional(readOnly = true)
    public PageResult<AuditLogVO> page(AuditLogPageDTO req) {
        var pageable = req.toPageable(Sort.by("id").descending());
        Specification<AuditLog> spec = SpecificationBuilder.<AuditLog>builder()
                .eqIfPresent("entityType", req.getEntityType())
                .eqIfPresent("entityId", req.getEntityId())
                .eqIfPresent("action", req.getAction())
                .eqIfPresent("userId", req.getUserId())
                .build();
        var page = auditLogRepository.findAll(spec, pageable);
        return new PageResult<>(
                page.getContent().stream().map(this::toVO).toList(), page.getTotalElements());
    }

    private AuditLogVO toVO(AuditLog log) {
        return new AuditLogVO(
                log.getId(),
                log.getEntityType(),
                log.getEntityId(),
                log.getAction(),
                log.getUserId(),
                log.getChanges(),
                log.getIp(),
                log.getCreatedAt());
    }

    private String resolveIp() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            var request = sra.getRequest();
            var xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }
        return null;
    }
}

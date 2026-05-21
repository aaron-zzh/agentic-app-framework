package com.xuejiai.aaf.module.system.log.service;

import java.time.LocalDateTime;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.xuejiai.aaf.common.util.ServletUtils;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.security.ActorContext;
import com.xuejiai.aaf.module.system.log.domain.AuditLog;
import com.xuejiai.aaf.module.system.log.repository.AuditLogRepository;
import com.xuejiai.aaf.module.system.log.vo.AuditLogPageDTO;
import com.xuejiai.aaf.module.system.log.vo.AuditLogVO;

import lombok.RequiredArgsConstructor;

/** 审计日志业务逻辑。 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ActorContext actorContext;

    /** 记录审计日志（含链式哈希校验）。 */
    @Transactional
    public void record(String entityType, Long entityId, String action, String changes) {
        // 获取前一条记录的 hash
        var previousHash =
                auditLogRepository.findTopByOrderByIdDesc().map(AuditLog::getHash).orElse(null);

        var log = new AuditLog();
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setAction(action);
        log.setChanges(changes);
        log.setUserId(actorContext.currentUserId().orElse(null));
        log.setIp(ServletUtils.getClientIp());
        log.setCreatedAt(LocalDateTime.now());
        log.setPreviousHash(previousHash);

        // 计算当前记录哈希
        var content = entityType + entityId + action + (changes != null ? changes : "");
        log.setHash(
                com.xuejiai.aaf.framework.logging.AuditLogInterceptor.computeHash(
                        previousHash, content));

        auditLogRepository.save(log);
    }

    /** 分页查询审计日志 */
    @Transactional(readOnly = true)
    public PageResult<AuditLogVO> page(AuditLogPageDTO req) {
        var pageable = req.toPageable(Sort.by("id").descending());
        Specification<AuditLog> spec =
                SpecificationBuilder.<AuditLog>builder()
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

}

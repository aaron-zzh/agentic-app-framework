package com.xuejiai.aaf.module.ai.aigc.task.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.org.OrgIgnore;
import com.xuejiai.aaf.module.ai.aigc.task.event.AigcTaskTerminalEvent;
import com.xuejiai.aaf.module.ai.aigc.task.repository.AigcTaskRepository;

import lombok.RequiredArgsConstructor;

/** SUBMITTING 崩溃恢复只做证据保全和终态化，不重新调用 provider。 */
@Service
@RequiredArgsConstructor
public class AigcTaskSubmittingRecoveryService {

    private final AigcTaskRepository repository;
    private final AigcTaskProviderCapabilities providerCapabilities;
    private final ApplicationEventPublisher eventPublisher;

    @OrgIgnore
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markNeedsReconciliation(Long taskId, LocalDateTime cutoff) {
        var task = repository.findLockedById(taskId).orElse(null);
        if (task == null
                || !"SUBMITTING".equals(task.getStatus())
                || task.getSubmitLeaseUntil() == null
                || !task.getSubmitLeaseUntil().isBefore(cutoff)) {
            return false;
        }
        var capabilities = providerCapabilities.require(task.getProvider());
        var evidence = new LinkedHashMap<String, Object>();
        evidence.put("recoveryDecision", "NEEDS_RECONCILIATION");
        evidence.put("provider", task.getProvider());
        evidence.put("providerIdempotencyKey", task.getProviderKey());
        evidence.put("idempotentSubmission", capabilities.idempotentSubmission());
        evidence.put("receiptLookup", capabilities.receiptLookup());
        evidence.put("submitOwner", task.getSubmitOwner());
        evidence.put("submitLeaseUntil", task.getSubmitLeaseUntil().toString());
        evidence.put("previousProviderResult", task.getProviderResult());
        task.setProviderResult(JsonUtils.toJsonString(evidence));
        task.setStatus("NEEDS_RECONCILIATION");
        task.setErrorMsg("provider 调用结果不确定，已禁止自动重试并等待人工对账");
        task.setUpdateTime(LocalDateTime.now());
        task.setVersion(task.getVersion() + 1);
        repository.saveAndFlush(task);
        eventPublisher.publishEvent(AigcTaskTerminalEvent.from(task));
        return true;
    }
}

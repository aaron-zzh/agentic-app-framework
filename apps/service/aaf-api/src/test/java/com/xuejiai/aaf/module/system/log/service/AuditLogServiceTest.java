package com.xuejiai.aaf.module.system.log.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.system.log.domain.AuditLog;
import com.xuejiai.aaf.module.system.log.repository.AuditLogRepository;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

/**
 * AuditLogService 单元测试。
 *
 * <p>验证链式哈希填充、字段赋值及首条记录（无前置哈希）场景。
 */
class AuditLogServiceTest extends BaseMockitoUnitTest {

    @Mock AuditLogRepository auditLogRepository;

    @Mock OperatorContext operatorContext;

    @InjectMocks AuditLogService auditLogService;

    @Test
    void record_首条记录_previousHash为null() {
        when(auditLogRepository.findTopByOrderByIdDesc()).thenReturn(Optional.empty());
        when(operatorContext.currentUserId()).thenReturn(Optional.of(1L));
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        auditLogService.record("Document", 10L, "UPDATE", "{\"title\":\"新标题\"}");

        ArgumentCaptor<AuditLog> captor = forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();

        assertThat(saved.getEntityType()).isEqualTo("Document");
        assertThat(saved.getEntityId()).isEqualTo(10L);
        assertThat(saved.getAction()).isEqualTo("UPDATE");
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getPreviousHash()).isNull();
        assertThat(saved.getHash()).isNotBlank();
    }

    @Test
    void record_有前置记录_previousHash链式传递() {
        AuditLog prev = new AuditLog();
        prev.setHash("abc123");
        when(auditLogRepository.findTopByOrderByIdDesc()).thenReturn(Optional.of(prev));
        when(operatorContext.currentUserId()).thenReturn(Optional.empty());
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        auditLogService.record("Document", 20L, "DELETE", null);

        ArgumentCaptor<AuditLog> captor = forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();

        assertThat(saved.getPreviousHash()).isEqualTo("abc123");
        // 当前 hash 与前置不同（链式变化）
        assertThat(saved.getHash()).isNotEqualTo("abc123");
    }

    @Test
    void record_changes为null_不抛异常() {
        when(auditLogRepository.findTopByOrderByIdDesc()).thenReturn(Optional.empty());
        when(operatorContext.currentUserId()).thenReturn(Optional.empty());
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // 不抛异常即通过
        auditLogService.record("Document", 1L, "INSERT", null);
        verify(auditLogRepository).save(any());
    }
}

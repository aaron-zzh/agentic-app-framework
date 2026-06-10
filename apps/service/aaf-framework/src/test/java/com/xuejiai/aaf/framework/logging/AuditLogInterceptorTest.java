package com.xuejiai.aaf.framework.logging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;

import com.xuejiai.aaf.test.BaseMockitoUnitTest;

import jakarta.persistence.Id;

/**
 * AuditLogInterceptor 单元测试。
 *
 * <p>验证：有 @Auditable 的实体触发事件；无 @Auditable 的实体跳过。
 */
class AuditLogInterceptorTest extends BaseMockitoUnitTest {

    @Mock ApplicationEventPublisher publisher;

    AuditLogInterceptor interceptor = new AuditLogInterceptor();

    @BeforeEach
    void setup() {
        // 通过 Helper 注入静态 publisher
        new AuditLogInterceptorHelper(publisher);
    }

    @Auditable
    static class AuditableEntity {
        @Id Long id = 1L;
        String name = "test";
    }

    static class NonAuditableEntity {
        @Id Long id = 2L;
    }

    @Test
    void onInsert_有Auditable注解_发布事件() {
        interceptor.onInsert(new AuditableEntity());
        verify(publisher).publishEvent(any(AuditChangeEvent.class));
    }

    @Test
    void onInsert_无Auditable注解_不发布事件() {
        interceptor.onInsert(new NonAuditableEntity());
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void onUpdate_有Auditable注解_发布UPDATE事件() {
        var entity = new AuditableEntity();
        interceptor.beforeUpdate(entity);
        interceptor.onUpdate(entity);
        verify(publisher).publishEvent(any(AuditChangeEvent.class));
    }

    @Test
    void onDelete_有Auditable注解_发布DELETE事件() {
        interceptor.onDelete(new AuditableEntity());
        verify(publisher).publishEvent(any(AuditChangeEvent.class));
    }
}

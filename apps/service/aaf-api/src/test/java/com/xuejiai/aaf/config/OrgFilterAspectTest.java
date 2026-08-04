package com.xuejiai.aaf.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.aspectj.lang.ProceedingJoinPoint;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

import jakarta.persistence.EntityManager;

class OrgFilterAspectTest extends BaseMockitoUnitTest {

    @Mock private EntityManager entityManager;
    @Mock private Session session;
    @Mock private ProceedingJoinPoint joinPoint;
    @InjectMocks private OrgFilterAspect aspect;

    @AfterEach
    void tearDown() {
        OrgContext.clear();
    }

    @Test
    @DisplayName("Given 显式全组织上下文 When Repository 查询 Then 关闭组织过滤并继续执行")
    void should_disable_org_filter_in_all_organizations() throws Throwable {
        // 准备参数
        OrgContext.useAllOrganizations();
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(joinPoint.proceed()).thenReturn(List.of("todo"));

        // 调用
        var result = aspect.enableOrgFilter(joinPoint);

        // 断言
        assertThat(result).isEqualTo(List.of("todo"));
        verify(session).disableFilter("orgFilter");
        verify(joinPoint).proceed();
    }
}

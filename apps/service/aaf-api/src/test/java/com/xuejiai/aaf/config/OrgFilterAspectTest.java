package com.xuejiai.aaf.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.aspectj.lang.ProceedingJoinPoint;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

import jakarta.persistence.EntityManager;

class OrgFilterAspectTest extends BaseMockitoUnitTest {

    @Mock private EntityManager entityManager;
    @Mock private Session session;
    @Mock private ProceedingJoinPoint joinPoint;

    private OrgFilterAspect aspect;

    @BeforeEach
    void setUp() {
        // 显式构造而非 @InjectMocks：OrgFilterAspect 除 entityManager 外还有三个非 final 的
        // ConcurrentHashMap 字段（各查询缓存），@InjectMocks 的字段/构造混合注入策略在这种场景下
        // 曾经把 entityManager 解析成另一个未 stub 的实例，导致 unwrap 返回 null 而非测试里配置的
        // session mock。显式调用唯一构造函数没有任何歧义，行为可预期。
        aspect = new OrgFilterAspect(entityManager);
    }

    @AfterEach
    void tearDown() {
        OrgContext.clear();
    }

    @Test
    @DisplayName("Given 显式全组织上下文 When Repository 查询 Then 关闭组织过滤并继续执行")
    void should_disable_org_filter_in_all_organizations() throws Throwable {
        // 准备参数
        OrgContext.useAllOrganizations();
        when(joinPoint.getTarget()).thenReturn(new Object());
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

package com.xuejiai.aaf.module.ai.aigc.event.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.event.api.SubscriptionScope;
import com.xuejiai.aaf.module.ai.aigc.event.service.AigcActivityEventService;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class AigcActivityEventControllerTest extends BaseMockitoUnitTest {

    @Mock private AigcActivityEventService service;
    @Mock private OperatorContext operatorContext;
    @InjectMocks private AigcActivityEventController controller;

    @AfterEach
    void clearScope() {
        OrgContext.clear();
    }

    @Test
    @DisplayName("Given OrgFilter 已授权 scope When query 携带同 scope Then 订阅当前主体事件")
    void should_accept_query_scope_only_when_it_matches_authorized_context() {
        // 准备参数
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(7L));
        OrgContext.setCurrentOrgId(8L);
        OrgContext.setCurrentWorkspaceId(9L);

        // 调用
        controller.stream("11", 12L, 8L, 9L);

        // 断言
        verify(service).subscribe(new SubscriptionScope(7L, 8L, 9L), 12L);
    }

    @Test
    @DisplayName("Given 客户端 query scope 未经 OrgFilter 授权 When 订阅 Then 拒绝")
    void should_reject_untrusted_query_scope() {
        // 准备参数
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(7L));
        OrgContext.setCurrentOrgId(8L);
        OrgContext.setCurrentWorkspaceId(9L);

        // 调用 + 断言
        assertThatThrownBy(() -> controller.stream(null, null, 80L, 90L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未经授权");
    }
}

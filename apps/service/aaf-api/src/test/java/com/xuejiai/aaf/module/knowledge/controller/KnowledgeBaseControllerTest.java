package com.xuejiai.aaf.module.knowledge.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import com.xuejiai.aaf.framework.logging.OperationLog;
import com.xuejiai.aaf.framework.logging.OperationType;
import com.xuejiai.aaf.module.knowledge.service.KnowledgeBaseService;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeGraphVO.GraphProjectionStatusVO;

class KnowledgeBaseControllerTest {

    private static final String ADMIN_ONLY = "hasAnyRole('ADMIN', 'SUPER_ADMIN')";

    @Test
    @DisplayName("Given 图投影运维接口 When 检查方法权限 Then 仅允许管理员角色")
    void should_restrict_graph_projection_operations_to_admin_roles() throws Exception {
        var statusMethod =
                KnowledgeBaseController.class.getMethod("graphProjectionStatus", Long.class);
        var rebuildMethod =
                KnowledgeBaseController.class.getMethod(
                        "rebuildGraphProjection", Long.class, String.class);

        assertThat(statusMethod.getAnnotation(PreAuthorize.class).value()).isEqualTo(ADMIN_ONLY);
        assertThat(rebuildMethod.getAnnotation(PreAuthorize.class).value()).isEqualTo(ADMIN_ONLY);
    }

    @Test
    @DisplayName("Given 管理员查询图投影状态 When 调用控制器 Then 返回服务状态")
    void should_return_graph_projection_status() {
        var service = mock(KnowledgeBaseService.class);
        var controller = new KnowledgeBaseController(service);
        var status = status("READY", true, "request-1");
        when(service.getGraphProjectionStatus(9L)).thenReturn(status);

        var result = controller.graphProjectionStatus(9L);

        assertThat(result.data()).isSameAs(status);
        verify(service).getGraphProjectionStatus(9L);
    }

    @Test
    @DisplayName("Given 幂等键 When 请求重建图投影 Then 委托服务且记录运维审计")
    void should_delegate_rebuild_with_request_key_and_audit() throws Exception {
        var service = mock(KnowledgeBaseService.class);
        var controller = new KnowledgeBaseController(service);
        var status = status("READY", true, "request-2");
        when(service.rebuildGraphProjection(9L, "request-2")).thenReturn(status);

        var result = controller.rebuildGraphProjection(9L, "request-2");
        var method =
                KnowledgeBaseController.class.getMethod(
                        "rebuildGraphProjection", Long.class, String.class);
        var audit = method.getAnnotation(OperationLog.class);

        assertThat(result.data()).isSameAs(status);
        assertThat(audit.module()).isEqualTo("知识库");
        assertThat(audit.type()).isEqualTo(OperationType.OTHER);
        assertThat(audit.bizNo()).isEqualTo("#{p0}");
        verify(service).rebuildGraphProjection(9L, "request-2");
    }

    private GraphProjectionStatusVO status(String state, boolean ready, String requestKey) {
        return new GraphProjectionStatusVO(
                UUID.randomUUID(),
                "NEO4J",
                7,
                7,
                ready ? 7 : 0,
                state,
                requestKey,
                null,
                LocalDateTime.of(2026, 8, 2, 11, 30),
                ready);
    }
}

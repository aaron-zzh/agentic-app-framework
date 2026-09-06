package com.xuejiai.aaf.module.ai.aigc.execution.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcExecutionRunTreeView;
import com.xuejiai.aaf.module.ai.aigc.execution.service.AigcActionCommandService;
import com.xuejiai.aaf.module.ai.aigc.execution.service.AigcExecutionRunService;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class AigcExecutionRunControllerTest extends BaseMockitoUnitTest {

    @Mock private AigcExecutionRunService service;
    @Mock private AigcActionCommandService commandService;
    @InjectMocks private AigcExecutionRunController controller;

    @Test
    @DisplayName("Given execution run ID When 读取完整 run-tree Then 返回 command service 权限校验后的树")
    void should_return_complete_run_tree_when_reading_execution() {
        // 准备参数
        var tree = new AigcExecutionRunTreeView(null, List.of());
        when(commandService.requireRunTree(31L)).thenReturn(tree);

        // 调用
        var result = controller.tree(31L);

        // 断言
        assertThat(result.data()).isSameAs(tree);
        verify(commandService).requireRunTree(31L);
    }

    @Test
    @DisplayName("Given run-tree 公开方法 When 检查端口契约 Then 使用 read authority 与固定 GET 路径")
    void should_require_read_authority_on_tree_endpoint() throws NoSuchMethodException {
        // 准备参数
        var method = AigcExecutionRunController.class.getMethod("tree", Long.class);

        // 调用
        var mapping = method.getAnnotation(GetMapping.class);
        var authorization = method.getAnnotation(PreAuthorize.class);

        // 断言
        assertThat(mapping.value()).containsExactly("/{id}/tree");
        assertThat(authorization.value()).isEqualTo("hasAuthority('aigc:execution-run:read')");
    }
}

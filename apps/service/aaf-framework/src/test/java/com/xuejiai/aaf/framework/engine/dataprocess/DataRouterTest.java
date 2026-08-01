package com.xuejiai.aaf.framework.engine.dataprocess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DataRouterTest {

    private final DataRouter router = new DataRouter();

    @Test
    @DisplayName("Given 配置知识库路由 When 执行路由 Then 明确失败且不写入虚假计数")
    void should_fail_without_fake_count_when_knowledge_base_route_is_not_implemented() {
        // 准备参数
        var target =
                PipelineConfig.RouteTarget.builder().type("knowledge_base").target("kb-1").build();
        var config = PipelineConfig.builder().routeTarget(target).build();
        var context = new ProcessingContext(List.of(Map.of("content", "待写入内容")), config);

        // 调用 + 断言
        assertThatThrownBy(() -> router.execute(context))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("知识库路由尚未实现")
                .hasMessageContaining("kb-1");
        assertThat(context.getMetadata()).doesNotContainKey("inserted_count");
    }

    @Test
    @DisplayName("Given 未配置路由目标 When 执行路由 Then 跳过并返回原上下文")
    void should_skip_when_route_target_is_absent() {
        // 准备参数
        var context =
                new ProcessingContext(
                        List.of(Map.of("content", "无需路由")), PipelineConfig.builder().build());

        // 调用
        var result = router.execute(context);

        // 断言
        assertThat(result).isSameAs(context);
        assertThat(context.getLogs()).containsExactly("[DataRouter] 无路由目标，跳过");
        assertThat(context.getMetadata()).doesNotContainKey("inserted_count");
    }

    @Test
    @DisplayName("Given 管道包含未实现的知识库路由 When 执行管道 Then 标记中止并记录失败")
    void should_abort_pipeline_when_knowledge_base_route_is_not_implemented() {
        // 准备参数
        var target =
                PipelineConfig.RouteTarget.builder().type("knowledge_base").target("kb-1").build();
        var config = PipelineConfig.builder().pipelineId("pipeline-1").routeTarget(target).build();
        var pipeline = new DataPipeline(List.of(router));

        // 调用
        var context = pipeline.execute(List.of(Map.of("content", "待写入内容")), config);

        // 断言
        assertThat(context.isAborted()).isTrue();
        assertThat(context.getLogs()).anyMatch(log -> log.contains("失败: 知识库路由尚未实现"));
        assertThat(context.getMetadata()).doesNotContainKey("inserted_count");
    }
}

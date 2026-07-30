package com.xuejiai.aaf.module.ai.flow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.common.exception.BusinessException;

class AiFlowBpmnCompilerTest {

    private final AiFlowBpmnCompiler compiler = new AiFlowBpmnCompiler();

    @Test
    void shouldCompileSavedFlowDefinitionWithStableProcessKey() {
        var definition =
                """
                {
                  "nodes": [
                    {"id":"start","type":"start","position":{"x":0,"y":0},"data":{"label":"开始"}},
                    {"id":"llm_1","type":"llm","position":{"x":200,"y":0},"data":{"label":"生成","prompt":"{{input}}"}},
                    {"id":"end","type":"end","position":{"x":400,"y":0},"data":{"label":"结束"}}
                  ],
                  "edges": [
                    {"id":"e1","source":"start","target":"llm_1"},
                    {"id":"e2","source":"llm_1","target":"end"}
                  ]
                }
                """;

        var xml = compiler.compile(42L, definition);

        assertThat(xml)
                .contains("<process id=\"ai_flow_42\"")
                .contains("flowable:delegateExpression=\"${llmNode}\"")
                .contains("<bpmndi:BPMNDiagram");
    }

    @Test
    void shouldUseIsolatedProcessKeyForDebugCompilation() {
        var definition =
                """
                {"nodes":[
                  {"id":"start","type":"start","position":{"x":0,"y":0},"data":{}},
                  {"id":"end","type":"end","position":{"x":100,"y":0},"data":{}}
                ],"edges":[{"id":"e1","source":"start","target":"end"}]}
                """;

        var result = compiler.compileDebug(7L, "123e4567-e89b-12d3-a456-426614174000", definition);

        assertThat(result.processKey()).startsWith("ai_flow_debug_7_");
        assertThat(result.bpmnXml()).contains("<process id=\"" + result.processKey() + "\"");
    }

    @Test
    void shouldRejectUnsupportedNodeType() {
        var definition =
                """
                {"nodes":[
                  {"id":"start","type":"start","position":{"x":0,"y":0},"data":{}},
                  {"id":"unsafe","type":"shell","position":{"x":50,"y":0},"data":{}},
                  {"id":"end","type":"end","position":{"x":100,"y":0},"data":{}}
                ],"edges":[
                  {"id":"e1","source":"start","target":"unsafe"},
                  {"id":"e2","source":"unsafe","target":"end"}
                ]}
                """;

        assertThatThrownBy(() -> compiler.compile(1L, definition))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的工作流节点类型");
    }
}

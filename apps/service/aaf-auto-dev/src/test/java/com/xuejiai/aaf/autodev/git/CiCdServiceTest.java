package com.xuejiai.aaf.autodev.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

import tools.jackson.databind.json.JsonMapper;

class CiCdServiceTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final CiCdService service = new CiCdService(jsonMapper, mock(TaskScheduler.class));

    @Test
    @DisplayName("Given ref 和 inputs 含 JSON 特殊字符 When 构造工作流请求体 Then 字段可无损解析")
    void should_escape_special_characters_when_building_workflow_payload() throws Exception {
        // 准备参数
        var ref = "feature/\"quoted\"\nbranch";
        var inputs = Map.of("environment", "prod\"blue", "note", "line1\nline2");

        // 调用
        var payload = service.buildWorkflowPayload(ref, inputs);
        var json = jsonMapper.readTree(payload);

        // 断言
        assertThat(json.get("ref").asString()).isEqualTo(ref);
        assertThat(json.get("inputs").get("environment").asString()).isEqualTo("prod\"blue");
        assertThat(json.get("inputs").get("note").asString()).isEqualTo("line1\nline2");
    }

    @Test
    @DisplayName("Given inputs 为空 When 构造工作流请求体 Then 输出空 inputs 对象")
    void should_use_empty_inputs_object_when_inputs_are_null() throws Exception {
        // 调用
        var payload = service.buildWorkflowPayload("main", null);
        var json = jsonMapper.readTree(payload);

        // 断言
        assertThat(json.get("ref").asString()).isEqualTo("main");
        assertThat(json.get("inputs").isObject()).isTrue();
        assertThat(json.get("inputs").size()).isZero();
    }
}

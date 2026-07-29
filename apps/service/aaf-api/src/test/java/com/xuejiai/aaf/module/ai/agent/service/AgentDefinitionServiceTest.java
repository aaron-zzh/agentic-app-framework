package com.xuejiai.aaf.module.ai.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.agent.AgentDefinition;
import com.xuejiai.aaf.framework.intelligent.agent.AgentDefinitionRepository;
import com.xuejiai.aaf.module.ai.agent.vo.AgentDefinitionCreateDTO;
import com.xuejiai.aaf.module.ai.agent.vo.AgentDefinitionUpdateDTO;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

/** 预定义 Agent 模板管理服务单元测试。 */
class AgentDefinitionServiceTest extends BaseMockitoUnitTest {

    @Mock private AgentDefinitionRepository repository;
    @InjectMocks private AgentDefinitionService service;

    @Test
    @DisplayName("Given Agent 标识未占用 When 创建模板 Then 初始化版本、状态和默认执行策略")
    void should_initialize_definition_when_create() {
        // 准备参数
        var request = createRequest();
        when(repository.findByAgentId(request.agentId())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // 调用
        var result = service.create(request);

        // 断言
        assertThat(result.agentId()).isEqualTo("expert.code-reviewer");
        assertThat(result.version()).isEqualTo(1);
        assertThat(result.status()).isEqualTo("active");
        assertThat(result.maxIterations()).isEqualTo(10);
        assertThat(result.timeoutSeconds()).isEqualTo(120);
        assertThat(result.tools()).containsExactly("repository.read");
        assertThat(result.allowedTools()).containsExactly("repository.read");
    }

    @Test
    @DisplayName("Given Agent 标识已存在 When 创建模板 Then 拒绝重复创建")
    void should_reject_duplicate_agent_id_when_create() {
        // 准备参数
        var request = createRequest();
        when(repository.findByAgentId(request.agentId()))
                .thenReturn(Optional.of(definition("active")));

        // 调用 + 断言
        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("标识已存在");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Given 活跃模板 When 更新配置 Then agentId 不变且定义版本递增")
    void should_increment_version_and_keep_agent_id_when_update() {
        // 准备参数
        var entity = definition("active");
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var request =
                new AgentDefinitionUpdateDTO(
                        "高级代码审查",
                        "更新后的描述",
                        "严格审查代码并给出证据",
                        2L,
                        List.of("code_review", "security_review"),
                        List.of("repository.read", "scanner.run"),
                        List.of("repository.read"),
                        List.of(),
                        20,
                        300);

        // 调用
        var result = service.update(1L, request);

        // 断言
        assertThat(result.agentId()).isEqualTo("expert.code-reviewer");
        assertThat(result.version()).isEqualTo(3);
        assertThat(result.name()).isEqualTo("高级代码审查");
        assertThat(result.tools()).containsExactly("repository.read", "scanner.run");
        assertThat(entity.getAgentId()).isEqualTo("expert.code-reviewer");
    }

    @Test
    @DisplayName("Given 已归档模板 When 更新或启用 Then 拒绝修改")
    void should_reject_mutation_when_archived() {
        // 准备参数
        var entity = definition("archived");
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        // 调用 + 断言
        assertThatThrownBy(() -> service.update(1L, updateRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不可修改");
        assertThatThrownBy(() -> service.enable(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不可修改");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Given 模板已归档 When 再次归档 Then 保持幂等且不重复保存")
    void should_be_idempotent_when_archive_again() {
        // 准备参数
        var entity = definition("archived");
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        // 调用
        service.archive(1L);

        // 断言
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Given 非法状态筛选 When 查询分页 Then 返回参数错误")
    void should_reject_invalid_status_when_page() {
        // 调用 + 断言
        assertThatThrownBy(
                        () ->
                                service.page(
                                        "deleted",
                                        new com.xuejiai.aaf.common.model.PageParam()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态不合法");
    }

    private static AgentDefinitionCreateDTO createRequest() {
        return new AgentDefinitionCreateDTO(
                "expert.code-reviewer",
                "代码审查专家",
                "跨助理复用的代码审查模板",
                "审查代码并输出问题证据",
                1L,
                List.of("code_review"),
                List.of("repository.read"),
                List.of("repository.read"),
                List.of(),
                null,
                null);
    }

    private static AgentDefinitionUpdateDTO updateRequest() {
        return new AgentDefinitionUpdateDTO(
                "代码审查专家",
                "描述",
                "审查代码并输出问题证据",
                1L,
                List.of("code_review"),
                List.of("repository.read"),
                List.of("repository.read"),
                List.of(),
                10,
                120);
    }

    private static AgentDefinition definition(String status) {
        var entity = new AgentDefinition();
        entity.setId(1L);
        entity.setAgentId("expert.code-reviewer");
        entity.setVersion(2);
        entity.setName("代码审查专家");
        entity.setDescription("描述");
        entity.setSystemPrompt("审查代码并输出问题证据");
        entity.setModelId(1L);
        entity.setCapabilities(JsonUtils.toJsonString(List.of("code_review")));
        entity.setTools(JsonUtils.toJsonString(List.of("repository.read")));
        entity.setAllowedTools(JsonUtils.toJsonString(List.of("repository.read")));
        entity.setMcpServers(JsonUtils.toJsonString(List.of()));
        entity.setMaxIterations(10);
        entity.setTimeoutSeconds(120);
        entity.setStatus(status);
        return entity;
    }
}

package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.xuejiai.aaf.framework.intelligent.assistant.role.AiRoleRepository;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class JpaRoleDefinitionAdapterTest extends BaseMockitoUnitTest {

    @Mock private AiRoleRepository repository;

    private JpaRoleDefinitionAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new JpaRoleDefinitionAdapter(repository);
    }

    @Test
    @DisplayName("Given 角色业务码存在 When 查询角色 Then 返回完整只读领域投影")
    void should_map_role_when_code_exists() {
        // 准备参数
        var entity = role("system.role.customer-service");
        entity.setSkillIds(
                "[{\"skillKey\":\"knowledge-search\",\"activationMode\":\"ALWAYS\"},{\"skillKey\":\"faq-answer\",\"activationMode\":\"ON_DEMAND\"}]");
        entity.setToolWhitelist("[\"search_kb\",\"switch_kb\"]");
        when(repository.findByCode("system.role.customer-service")).thenReturn(Optional.of(entity));

        // 调用
        var result = adapter.findByCode("system.role.customer-service");

        // 断言
        assertThat(result).isPresent();
        assertThat(result.orElseThrow().key()).isEqualTo("system.role.customer-service");
        assertThat(result.orElseThrow().name()).isEqualTo("客服角色");
        assertThat(result.orElseThrow().responsibilities()).containsExactly("客服场景专用角色，工具限于知识库检索");
        assertThat(result.orElseThrow().nonResponsibilities()).isEmpty();
        assertThat(result.orElseThrow().skillKeys())
                .isEqualTo(Set.of("knowledge-search", "faq-answer"));
        assertThat(result.orElseThrow().toolKeys()).isEqualTo(Set.of("search_kb", "switch_kb"));
        verify(repository).findByCode("system.role.customer-service");
    }

    @Test
    @DisplayName("Given 角色业务码不存在 When 查询角色 Then 返回空结果")
    void should_return_empty_when_code_not_exists() {
        // mock 方法
        when(repository.findByCode("system.role.missing")).thenReturn(Optional.empty());

        // 调用 + 断言
        assertThat(adapter.findByCode("system.role.missing")).isEmpty();
    }

    @Test
    @DisplayName("Given 技能列表是旧字符串数组 When 查询角色 Then 拒绝兼容解析")
    void should_reject_legacy_string_skill_ids() {
        // 准备参数
        var entity = role("system.role.invalid");
        entity.setSkillIds("[\"knowledge-search\"]");
        when(repository.findByCode("system.role.invalid")).thenReturn(Optional.of(entity));

        // 调用 + 断言
        assertThatThrownBy(() -> adapter.findByCode("system.role.invalid"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("skillKey/activationMode");
    }

    @Test
    @DisplayName("Given 工具白名单不是合法 JSON When 查询角色 Then 快速失败")
    void should_reject_invalid_tool_whitelist_json() {
        // 准备参数
        var entity = role("system.role.invalid");
        entity.setToolWhitelist("[invalid]");
        when(repository.findByCode("system.role.invalid")).thenReturn(Optional.of(entity));

        // 调用 + 断言
        assertThatThrownBy(() -> adapter.findByCode("system.role.invalid"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("toolWhitelist 必须是合法 JSON 字符串数组");
    }

    @Test
    @DisplayName("Given 角色描述为空 When 查询角色 Then 不虚构默认职责")
    void should_reject_empty_description() {
        // 准备参数
        var entity = role("system.role.invalid");
        entity.setDescription(" ");
        when(repository.findByCode("system.role.invalid")).thenReturn(Optional.of(entity));

        // 调用 + 断言
        assertThatThrownBy(() -> adapter.findByCode("system.role.invalid"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("缺少职责描述");
    }

    @Test
    @DisplayName("Given 角色业务码为空白 When 查询角色 Then 拒绝模糊查询")
    void should_reject_blank_role_code() {
        assertThatThrownBy(() -> adapter.findByCode(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("roleCode 不能为空白");
    }

    private com.xuejiai.aaf.framework.intelligent.assistant.role.Role role(String code) {
        var entity = new com.xuejiai.aaf.framework.intelligent.assistant.role.Role();
        entity.setCode(code);
        entity.setName("客服角色");
        entity.setDescription("客服场景专用角色，工具限于知识库检索");
        entity.setSkillIds("[]");
        entity.setToolWhitelist("[]");
        return entity;
    }
}

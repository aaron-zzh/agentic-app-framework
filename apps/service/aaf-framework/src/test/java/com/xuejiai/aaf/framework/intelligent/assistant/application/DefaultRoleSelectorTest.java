package com.xuejiai.aaf.framework.intelligent.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.DefaultUserAssistantTemplate;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

class DefaultRoleSelectorTest {

    private final DefaultRoleSelector selector = new DefaultRoleSelector();
    private final AssistantDefinition definition =
            new DefaultUserAssistantTemplate().templates().getFirst();

    @Test
    @DisplayName("Given 未显式指定 Skill When 选择 Role Then 使用 is_default 对应 Role")
    void should_select_default_role_when_skill_is_not_requested() {
        var result = selector.select(request(null));

        assertThat(result.role().key())
                .isEqualTo(DefaultUserAssistantTemplate.PLATFORM_GUIDE_ROLE_KEY);
        assertThat(result.selectedBy()).isEqualTo("DEFAULT_BINDING");
    }

    @Test
    @DisplayName("Given 显式 Skill 唯一属于内容 Role When 选择 Role Then 选择该 Role")
    void should_select_unique_role_for_requested_skill() {
        var result = selector.select(request("aigc-copywriting"));

        assertThat(result.role().key())
                .isEqualTo(DefaultUserAssistantTemplate.CONTENT_CREATOR_ROLE_KEY);
        assertThat(result.selectedBy()).isEqualTo("REQUEST");
    }

    @Test
    @DisplayName("Given 显式 Skill 不属于当前 ON_DEMAND Scope When 选择 Role Then 拒绝越界")
    void should_reject_requested_skill_outside_role_scope() {
        assertThatThrownBy(() -> selector.select(request("unknown-skill")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ON_DEMAND");
    }

    private RoleSelector.RoleSelectionRequest request(String preferredSkillKey) {
        return new RoleSelector.RoleSelectionRequest(
                definition, Set.of(), "请处理当前任务", preferredSkillKey, new UserId("1"));
    }
}

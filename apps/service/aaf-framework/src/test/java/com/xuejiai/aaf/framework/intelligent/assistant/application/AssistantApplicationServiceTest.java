package com.xuejiai.aaf.framework.intelligent.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.intelligent.core.skill.SkillDef;

class AssistantApplicationServiceTest {

    @Test
    @DisplayName("Given 有效技能顺序不稳定且提示重复 When 合并 Then 按优先级和标识稳定排序并去重")
    void should_merge_skill_prompts_deterministically() {
        var skills =
                List.of(
                        skill(2L, "技能 B", "提示 B", 10),
                        skill(4L, "重复技能", "提示 A", 5),
                        skill(3L, "高优技能", "提示 高", 20),
                        skill(1L, "技能 A", " 提示 A ", 10),
                        skill(5L, "空提示", "   ", 30));

        var result = AssistantApplicationService.mergeSkillPrompts(skills);

        assertThat(result).isEqualTo("提示 高\n\n提示 A\n\n提示 B");
    }

    @Test
    @DisplayName("Given 技能没有可用系统提示 When 合并 Then 返回空附录")
    void should_return_empty_appendix_when_skill_prompts_are_unavailable() {
        var skills = List.of(skill(1L, "空白", " ", 10), skill(2L, "缺失", null, 20));

        var result = AssistantApplicationService.mergeSkillPrompts(skills);

        assertThat(result).isEmpty();
    }

    private static SkillDef skill(Long id, String name, String systemPrompt, int priority) {
        return new SkillDef(id, name, name + "描述", null, List.of(), systemPrompt, priority, false);
    }
}

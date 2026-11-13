package com.xuejiai.aaf.framework.intelligent.agent.port;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 冻结 SkillVersion 声明的工具清单（ai_skill_tool_requirement），供画像编译与 Skill 工具提示使用。 */
public interface SkillToolCatalogPort {

    /** 按 SkillVersion 批量读取工具声明，组内按 sortOrder 排序；无声明的版本不出现在结果中。 */
    Map<Long, List<SkillToolDeclaration>> findByVersionIds(Set<Long> skillVersionIds);

    /**
     * Skill 版本声明的单个工具。何时调用由 Skill 正文说明，这里只携带清单与缺失处理语义。
     *
     * @param required 为 true 时缺失即画像编译失败；为 false 时缺失则跳过，仅在提示中标为不可用
     */
    record SkillToolDeclaration(String toolName, boolean required) {
        public SkillToolDeclaration {
            Objects.requireNonNull(toolName, "toolName 不能为空");
        }
    }
}

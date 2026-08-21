package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.assistant.model.Actor;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition.Lifecycle;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition.RiskPolicy;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition.TemplateOwnership;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantVersion;
import com.xuejiai.aaf.framework.intelligent.assistant.model.MemoryStrategy;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Role;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillActivationMode;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillBinding;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ToolPolicy;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ToolPolicy.ActionEffect;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ToolPolicy.ToolRule;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AssistantId;

/** 通用 Assistant 行为测试的最小定义夹具；不复制生产系统模板配置。 */
public final class AssistantDefinitionFixtures {

    public static final String PLATFORM_GUIDE_ROLE_KEY = "system.role.platform-guide";
    public static final String CONTENT_CREATOR_ROLE_KEY = "system.role.content-creator";

    private AssistantDefinitionFixtures() {}

    public static AssistantDefinition defaultUser() {
        var platformGuide =
                new Role(
                        PLATFORM_GUIDE_ROLE_KEY,
                        "平台向导",
                        List.of("产品咨询", "人工转接"),
                        List.of("修改用户数据"),
                        List.of(binding("builtin-self-learning", SkillActivationMode.ON_DEMAND)),
                        Set.of("support.handoff"));
        var contentCreator =
                new Role(
                        CONTENT_CREATOR_ROLE_KEY,
                        "内容创作者",
                        List.of("生成草稿"),
                        List.of("自动发布"),
                        List.of(binding("aigc-copywriting", SkillActivationMode.ON_DEMAND)),
                        Set.of("content.draft.upsert"));
        return new AssistantDefinition(
                new AssistantId("test.assistant.default-user"),
                null,
                null,
                TemplateOwnership.USER_OWNED,
                new AssistantVersion(1),
                "test",
                new Actor("test.actor", "测试助理", "测试", "审慎", "简洁", "仅测试", null),
                List.of(platformGuide, contentCreator),
                List.of(binding("builtin-user-understanding", SkillActivationMode.ALWAYS)),
                Set.of("script.execute.javascript"),
                PLATFORM_GUIDE_ROLE_KEY,
                MemoryStrategy.hybridDefault(),
                null,
                new ToolPolicy(
                        Map.of(
                                "support.handoff",
                                new ToolRule(
                                        "support.handoff",
                                        ActionEffect.HUMAN_HANDOFF,
                                        false,
                                        false),
                                "content.draft.upsert",
                                new ToolRule(
                                        "content.draft.upsert",
                                        ActionEffect.REVERSIBLE_WRITE,
                                        true,
                                        true),
                                "script.execute.javascript",
                                new ToolRule(
                                        "script.execute.javascript",
                                        ActionEffect.READ,
                                        false,
                                        true))),
                Set.of(ControlMode.READ_ONLY, ControlMode.COLLABORATIVE, ControlMode.DELEGATED),
                RiskPolicy.CONFIRM_WRITES,
                Lifecycle.PUBLISHED);
    }

    private static SkillBinding binding(String key, SkillActivationMode activationMode) {
        return new SkillBinding(key, activationMode);
    }
}

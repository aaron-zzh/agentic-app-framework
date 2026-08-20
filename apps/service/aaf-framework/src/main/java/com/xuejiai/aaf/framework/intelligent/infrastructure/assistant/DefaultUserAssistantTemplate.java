package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant;

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
import com.xuejiai.aaf.framework.intelligent.assistant.port.SystemAssistantTemplateContributor;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AssistantId;

/** 系统提供的默认用户助理模板；模板配置共享，用户会话、记忆和执行状态按主体隔离。 */
public final class DefaultUserAssistantTemplate implements SystemAssistantTemplateContributor {

    public static final String ASSISTANT_ID = "system.assistant.default-user";
    public static final String SYSTEM_KEY = "aaf.assistant.default-user";
    public static final String PLATFORM_GUIDE_ROLE_KEY = "system.role.platform-guide";
    public static final String CONTENT_CREATOR_ROLE_KEY = "system.role.content-creator";
    public static final String SYSTEM_SELF_AWARENESS_SKILL_KEY = "builtin-self-awareness";
    public static final String USER_UNDERSTANDING_SKILL_KEY = "builtin-user-understanding";
    public static final String DEFAULT_COPYWRITING_SKILL_KEY = "aigc-copywriting";
    public static final String JAVASCRIPT_COMPUTE_SKILL_KEY = "builtin-javascript-compute";
    public static final String JAVASCRIPT_EXECUTION_TOOL_KEY = "script.execute.javascript";
    static final List<String> CONTENT_CREATION_SKILL_KEYS =
            List.of(
                    DEFAULT_COPYWRITING_SKILL_KEY,
                    "content-schedule",
                    "content-judge",
                    "content-clarify",
                    "content-architect",
                    "content-build",
                    "voiceover",
                    "redbook",
                    "product-copy",
                    "ip-position",
                    "short-script",
                    "title-topic",
                    "biz-analysis",
                    "rich-text-write");
    public static final AssistantVersion VERSION = new AssistantVersion(6);

    @Override
    public List<AssistantDefinition> templates() {
        return List.of(defaultUserAssistant());
    }

    private static AssistantDefinition defaultUserAssistant() {
        var actor =
                new Actor(
                        "system.actor.default-user",
                        "AAF 助理",
                        "帮助用户使用 AAF，并在需要时协助内容创作或使用受控计算技能",
                        "友好、准确、审慎、尊重用户表达与隐私",
                        "简洁、具体，未知事实不猜测，生成内容先提供可审查草稿",
                        "只使用授权资料和工具；不得发布、删除、付费或代表用户对外承诺",
                        "system://assistant/default-user");
        return new AssistantDefinition(
                new AssistantId(ASSISTANT_ID),
                SYSTEM_KEY,
                null,
                TemplateOwnership.SYSTEM_MANAGED,
                VERSION,
                "AAF",
                actor,
                List.of(platformGuideRole(), contentCreatorRole()),
                List.of(
                        binding(USER_UNDERSTANDING_SKILL_KEY, SkillActivationMode.ALWAYS),
                        binding(JAVASCRIPT_COMPUTE_SKILL_KEY, SkillActivationMode.ON_DEMAND)),
                Set.of(JAVASCRIPT_EXECUTION_TOOL_KEY),
                PLATFORM_GUIDE_ROLE_KEY,
                MemoryStrategy.hybridDefault(),
                null,
                toolPolicy(),
                Set.of(ControlMode.READ_ONLY, ControlMode.COLLABORATIVE, ControlMode.DELEGATED),
                RiskPolicy.CONFIRM_WRITES,
                Lifecycle.PUBLISHED);
    }

    private static Role platformGuideRole() {
        return new Role(
                PLATFORM_GUIDE_ROLE_KEY,
                "平台向导",
                List.of("AAF 平台咨询", "只读故障排查", "转人工"),
                List.of("修改工单", "修改用户数据", "访问未授权隐私数据"),
                List.of(
                        binding("builtin-self-learning", SkillActivationMode.ON_DEMAND),
                        binding("builtin-skill-creation", SkillActivationMode.ON_DEMAND),
                        binding("builtin-tool-generation", SkillActivationMode.ON_DEMAND)),
                Set.of("support.handoff"));
    }

    private static Role contentCreatorRole() {
        return new Role(
                CONTENT_CREATOR_ROLE_KEY,
                "内容创作者",
                List.of("内容策划", "生成草稿", "润色与事实核查"),
                List.of("自动发布", "不可逆删除", "未经确认的付费动作"),
                CONTENT_CREATION_SKILL_KEYS.stream()
                        .map(code -> binding(code, SkillActivationMode.ON_DEMAND))
                        .toList(),
                Set.of("content.draft.upsert"));
    }

    private static SkillBinding binding(String skillKey, SkillActivationMode activationMode) {
        return new SkillBinding(skillKey, activationMode);
    }

    private static ToolPolicy toolPolicy() {
        return new ToolPolicy(
                Map.of(
                        "support.handoff",
                        new ToolRule("support.handoff", ActionEffect.HUMAN_HANDOFF, false, false),
                        "content.draft.upsert",
                        new ToolRule(
                                "content.draft.upsert", ActionEffect.REVERSIBLE_WRITE, true, true),
                        JAVASCRIPT_EXECUTION_TOOL_KEY,
                        new ToolRule(
                                JAVASCRIPT_EXECUTION_TOOL_KEY, ActionEffect.READ, false, true)));
    }
}

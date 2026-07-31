package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.agent.model.ExecutionPolicy;
import com.xuejiai.aaf.framework.intelligent.agent.model.ModelSelectionRequirement;
import com.xuejiai.aaf.framework.intelligent.agent.model.SubagentSpec;
import com.xuejiai.aaf.framework.intelligent.agent.model.ToolRef;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Actor;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition.Lifecycle;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition.RiskPolicy;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition.TemplateOwnership;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantVersion;
import com.xuejiai.aaf.framework.intelligent.assistant.model.MemoryStrategy;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Role;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillRoute;
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
    public static final AssistantVersion VERSION = new AssistantVersion(1);

    @Override
    public List<AssistantDefinition> templates() {
        return List.of(defaultUserAssistant());
    }

    private static AssistantDefinition defaultUserAssistant() {
        var actor =
                new Actor(
                        "system.actor.default-user",
                        "AAF 助理",
                        "帮助用户使用 AAF，并在需要时协助完成内容创作",
                        "友好、准确、审慎、尊重用户表达与隐私",
                        "简洁、具体，未知事实不猜测，生成内容先提供可审查草稿",
                        "只使用授权资料和工具；不得发布、删除、付费或代表用户对外承诺",
                        "system://assistant/default-user");
        var platformGuideRole = platformGuideRole();
        var contentCreatorRole = contentCreatorRole();
        var platformGuideAgent = platformGuideAgent();
        var contentCreatorAgent = contentCreatorAgent();
        var routes =
                List.of(
                        new SkillRoute(
                                "content.draft",
                                CONTENT_CREATOR_ROLE_KEY,
                                Set.of("草稿", "撰写", "写一篇", "draft", "create"),
                                contentCreatorAgent,
                                "content.draft.generate",
                                ActionEffect.GENERATED_CONTENT,
                                SkillRoute.HandlingMode.DELEGATE,
                                200,
                                false),
                        new SkillRoute(
                                "content.plan",
                                CONTENT_CREATOR_ROLE_KEY,
                                Set.of("内容策划", "大纲", "选题", "plan", "outline"),
                                contentCreatorAgent,
                                "content.plan.read",
                                ActionEffect.READ,
                                SkillRoute.HandlingMode.DELEGATE,
                                150,
                                false),
                        new SkillRoute(
                                "support.handoff",
                                PLATFORM_GUIDE_ROLE_KEY,
                                Set.of("人工", "转人工", "human", "agent"),
                                platformGuideAgent,
                                "support.handoff",
                                ActionEffect.HUMAN_HANDOFF,
                                SkillRoute.HandlingMode.DIRECT,
                                100,
                                false),
                        new SkillRoute(
                                "support.read",
                                PLATFORM_GUIDE_ROLE_KEY,
                                Set.of(),
                                platformGuideAgent,
                                "support.read",
                                ActionEffect.READ,
                                SkillRoute.HandlingMode.DIRECT,
                                0,
                                true));
        return new AssistantDefinition(
                new AssistantId(ASSISTANT_ID),
                SYSTEM_KEY,
                null,
                TemplateOwnership.SYSTEM_MANAGED,
                VERSION,
                "AAF",
                actor,
                List.of(platformGuideRole, contentCreatorRole),
                PLATFORM_GUIDE_ROLE_KEY,
                MemoryStrategy.hybridDefault(),
                routes,
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
                Set.of("support.read", "support.handoff"),
                Set.of("knowledge.search", "support.diagnostics.read", "support.handoff"));
    }

    private static Role contentCreatorRole() {
        return new Role(
                CONTENT_CREATOR_ROLE_KEY,
                "内容创作者",
                List.of("内容策划", "生成草稿", "润色与事实核查"),
                List.of("自动发布", "不可逆删除", "未经确认的付费动作"),
                Set.of("content.plan", "content.draft"),
                Set.of("knowledge.search", "content.generate", "content.draft.create"));
    }

    private static ToolPolicy toolPolicy() {
        return new ToolPolicy(
                Map.of(
                        "knowledge.search",
                        new ToolRule("knowledge.search", ActionEffect.READ, false, false),
                        "support.diagnostics.read",
                        new ToolRule(
                                "support.diagnostics.read", ActionEffect.READ, false, false),
                        "support.handoff",
                        new ToolRule(
                                "support.handoff", ActionEffect.HUMAN_HANDOFF, false, false),
                        "content.generate",
                        new ToolRule(
                                "content.generate",
                                ActionEffect.GENERATED_CONTENT,
                                false,
                                false),
                        "content.draft.create",
                        new ToolRule(
                                "content.draft.create",
                                ActionEffect.REVERSIBLE_WRITE,
                                true,
                                true)));
    }

    private static SubagentSpec platformGuideAgent() {
        return new SubagentSpec.Dynamic(
                "system.agent.default-user",
                "默认用户助理的主执行体，直接处理平台向导职责内的请求。",
                "你是 AAF 默认用户助理。保持稳定 Persona，直接处理平台咨询、只读排查和转人工；仅依据授权资料作答，未知内容不得猜测。",
                List.of(
                        new ToolRef("knowledge.search", 1, "knowledge.search"),
                        new ToolRef("support.diagnostics.read", 1, "support.diagnostics.read"),
                        new ToolRef("support.handoff", 1, "support.handoff")),
                new ExecutionPolicy(8, 2, Duration.ofSeconds(90)),
                ModelSelectionRequirement.balanced(),
                false);
    }

    private static SubagentSpec contentCreatorAgent() {
        return new SubagentSpec.Dynamic(
                "system.agent.content-creator",
                "为默认用户助理生成可审查内容。",
                "你是内容创作 Agent。根据用户目标生成清晰、准确、可审查的策划或草稿；不得发布、删除、付费或代表用户对外承诺。",
                List.of(
                        new ToolRef("knowledge.search", 1, "knowledge.search"),
                        new ToolRef("content.generate", 1, "content.generate"),
                        new ToolRef("content.draft.create", 1, "content.draft.create")),
                new ExecutionPolicy(10, 2, Duration.ofSeconds(120)),
                ModelSelectionRequirement.reasoning(),
                false);
    }
}

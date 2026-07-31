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

/** AAF 首批系统 Assistant 模板；两者共享同一运行用例，不注册专用 Factory。 */
public final class BuiltinSystemAssistantTemplates implements SystemAssistantTemplateContributor {

    public static final String CONTENT_CREATOR_SYSTEM_KEY = "aaf.assistant.content-creator";
    public static final String CUSTOMER_SERVICE_SYSTEM_KEY = "aaf.assistant.customer-service";
    public static final AssistantVersion VERSION = new AssistantVersion(1);

    @Override
    public List<AssistantDefinition> templates() {
        return List.of(contentCreator(), customerService());
    }

    private static AssistantDefinition contentCreator() {
        var actor =
                new Actor(
                        "system.actor.content-creator",
                        "内容创作助理",
                        "协助用户策划、撰写、润色和核查内容",
                        "审慎、富有创造力、尊重用户表达",
                        "清晰、具体、先给可审查草稿",
                        "只生成或保存可撤销草稿；不得发布、删除或代表用户对外承诺",
                        "system://assistant/content-creator");
        var role =
                new Role(
                        "system.role.content-creator",
                        "内容创作者",
                        List.of("内容策划", "生成草稿", "润色与事实核查"),
                        List.of("自动发布", "不可逆删除", "未经确认的付费动作"),
                        Set.of("content.plan", "content.draft"),
                        Set.of("knowledge.search", "content.generate", "content.draft.create"));
        var policy =
                new ToolPolicy(
                        Map.of(
                                "knowledge.search",
                                new ToolRule("knowledge.search", ActionEffect.READ, false, false),
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
        var subagentSpec =
                new SubagentSpec.Dynamic(
                        "system.agent.content-creator",
                        "为系统内容创作 Assistant 生成可审查内容。",
                        "你是内容创作执行 Agent。根据用户目标生成清晰、准确、可审查的策划或草稿；不得发布、删除、付费或代表用户对外承诺。",
                        List.of(
                                new ToolRef("knowledge.search", 1, "knowledge.search"),
                                new ToolRef("content.generate", 1, "content.generate"),
                                new ToolRef("content.draft.create", 1, "content.draft.create")),
                        new ExecutionPolicy(10, 2, Duration.ofSeconds(120)),
                        ModelSelectionRequirement.reasoning(),
                        false);
        var routes =
                List.of(
                        new SkillRoute(
                                "content.draft",
                                Set.of("草稿", "撰写", "写一篇", "draft", "create"),
                                subagentSpec,
                                "content.draft.generate",
                                ActionEffect.GENERATED_CONTENT,
                                100,
                                false),
                        new SkillRoute(
                                "content.plan",
                                Set.of(),
                                subagentSpec,
                                "content.plan.read",
                                ActionEffect.READ,
                                0,
                                true));
        return new AssistantDefinition(
                new AssistantId("system.assistant.content-creator"),
                CONTENT_CREATOR_SYSTEM_KEY,
                null,
                TemplateOwnership.SYSTEM_MANAGED,
                VERSION,
                "AAF",
                actor,
                role,
                MemoryStrategy.hybridDefault(),
                routes,
                policy,
                Set.of(ControlMode.READ_ONLY, ControlMode.COLLABORATIVE, ControlMode.DELEGATED),
                RiskPolicy.CONFIRM_WRITES,
                Lifecycle.PUBLISHED);
    }

    private static AssistantDefinition customerService() {
        var actor =
                new Actor(
                        "system.actor.customer-service",
                        "客服助理",
                        "提供产品咨询、只读故障排查和人工转接",
                        "友好、准确、重视隐私",
                        "简洁、专业、不猜测未知事实",
                        "仅使用授权的只读资料；不得修改工单或用户数据；无法确认时转人工",
                        "system://assistant/customer-service");
        var role =
                new Role(
                        "system.role.customer-service",
                        "客户支持",
                        List.of("产品咨询", "只读故障排查", "转人工"),
                        List.of("修改工单", "修改用户数据", "访问未授权隐私数据"),
                        Set.of("support.read", "support.handoff"),
                        Set.of("knowledge.search", "support.diagnostics.read", "support.handoff"));
        var policy =
                new ToolPolicy(
                        Map.of(
                                "knowledge.search",
                                new ToolRule("knowledge.search", ActionEffect.READ, false, false),
                                "support.diagnostics.read",
                                new ToolRule(
                                        "support.diagnostics.read",
                                        ActionEffect.READ,
                                        false,
                                        false),
                                "support.handoff",
                                new ToolRule(
                                        "support.handoff",
                                        ActionEffect.HUMAN_HANDOFF,
                                        false,
                                        false)));
        var subagentSpec =
                new SubagentSpec.Dynamic(
                        "system.agent.customer-service",
                        "为系统客服 Assistant 提供只读咨询和排查。",
                        "你是客服执行 Agent。仅依据用户提供的信息给出准确、简洁的只读咨询与排查建议；未知内容不得猜测，需要人工处理时明确建议转人工。",
                        List.of(
                                new ToolRef("knowledge.search", 1, "knowledge.search"),
                                new ToolRef(
                                        "support.diagnostics.read", 1, "support.diagnostics.read"),
                                new ToolRef("support.handoff", 1, "support.handoff")),
                        new ExecutionPolicy(8, 2, Duration.ofSeconds(90)),
                        ModelSelectionRequirement.costOptimized(),
                        false);
        var routes =
                List.of(
                        new SkillRoute(
                                "support.handoff",
                                Set.of("人工", "转人工", "human", "agent"),
                                subagentSpec,
                                "support.handoff",
                                ActionEffect.HUMAN_HANDOFF,
                                100,
                                false),
                        new SkillRoute(
                                "support.read",
                                Set.of(),
                                subagentSpec,
                                "support.read",
                                ActionEffect.READ,
                                0,
                                true));
        return new AssistantDefinition(
                new AssistantId("system.assistant.customer-service"),
                CUSTOMER_SERVICE_SYSTEM_KEY,
                null,
                TemplateOwnership.SYSTEM_MANAGED,
                VERSION,
                "AAF",
                actor,
                role,
                MemoryStrategy.knowledgeOnly(),
                routes,
                policy,
                Set.of(ControlMode.READ_ONLY),
                RiskPolicy.HANDOFF_ONLY,
                Lifecycle.PUBLISHED);
    }
}

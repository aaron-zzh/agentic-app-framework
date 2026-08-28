package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.agent.port.ContextAwareToolHandler;
import com.xuejiai.aaf.framework.intelligent.agent.port.SkillCatalogPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.SkillReferenceCatalogPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocation;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 运行中途按需加载额外内容的内置工具：技能正文、技能挂载的参考文档。
 *
 * <p>不中断 ReAct 循环——直接把加载到的内容作为工具结果返回，模型在同一轮继续推理。这是有意选择的实现方式：技能正文与
 * 参考文档是知识性文本，不是有副作用的业务动作，不需要挂起等待外部介入（对比 {@link SupportHandoffTool} 那类必须转人工 的动作）。下一次物理调用的 {@code
 * PromptEnvelope} 会自然记录到消息集合的增长，不需要单独的画像升级续跑机制。
 *
 * <p>权限边界：{@code SKILL} 允许加载任意已发布 Skill，不限于当前 Assistant/Role 已绑定的候选范围——技能正文与 {@code builtIn}
 * 技能同级信任，任何已发布技能的内容都可被安全阅读；真正的能力边界在工具授权层 （{@link
 * com.xuejiai.aaf.framework.intelligent.agent.model.ToolAuthorizationContext}），不在这里收窄。 {@code
 * SKILL_REFERENCE} 同理：技能被授权使用即代表其挂载的参考文档可读，不对调用者单独校验文档可见性。
 *
 * <p>{@code KNOWLEDGE_BINDING} 尚未实现，依赖 L1 读管道三通道接入后单独排期（见 {@code ai_skill_knowledge_binding}）；
 * 枚举先行预留，命中即报错而非静默忽略。
 */
public final class ContextLoadTool implements ContextAwareToolHandler {

    public static final String TOOL_NAME = "context.load";

    private final SkillCatalogPort skills;
    private final SkillReferenceCatalogPort skillReferences;

    public ContextLoadTool(SkillCatalogPort skills, SkillReferenceCatalogPort skillReferences) {
        this.skills = Objects.requireNonNull(skills, "skills 不能为空");
        this.skillReferences = Objects.requireNonNull(skillReferences, "skillReferences 不能为空");
    }

    @Override
    public String toolName() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return "按需加载当前不在上下文中的内容。kind=SKILL 时 key 为技能 code，返回该技能正文；"
                + "kind=SKILL_REFERENCE 时 key 为 \"技能code:referenceKey\"，返回该技能挂载的参考文档内容。";
    }

    @Override
    public Mono<ToolInvocationResult> invoke(ToolInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation 不能为空");
        return Mono.fromCallable(() -> load(invocation.arguments()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private ToolInvocationResult load(Map<String, Object> arguments) {
        var kind = requireKind(arguments);
        return switch (kind) {
            case SKILL -> loadSkill(requireKey(arguments));
            case SKILL_REFERENCE -> loadSkillReference(requireKey(arguments));
            case KNOWLEDGE_BINDING ->
                    throw new IllegalArgumentException(
                            "context.load 尚不支持 KNOWLEDGE_BINDING：依赖 L1 读管道三通道接入，见改进意见记录");
        };
    }

    private ToolInvocationResult loadSkill(String skillCode) {
        var skill =
                skills.findByCode(skillCode)
                        .orElseThrow(
                                () -> new IllegalArgumentException("不存在已发布 Skill: " + skillCode));
        return result(skill.content(), Map.of("kind", "SKILL", "code", skill.code()));
    }

    private ToolInvocationResult loadSkillReference(String key) {
        var separatorIndex = key.indexOf(':');
        if (separatorIndex < 1 || separatorIndex == key.length() - 1) {
            throw new IllegalArgumentException(
                    "SKILL_REFERENCE 的 key 必须是 \"技能code:referenceKey\" 形式: " + key);
        }
        var skillCode = key.substring(0, separatorIndex);
        var referenceKey = key.substring(separatorIndex + 1);
        var skill =
                skills.findByCode(skillCode)
                        .orElseThrow(
                                () -> new IllegalArgumentException("不存在已发布 Skill: " + skillCode));
        var reference =
                skillReferences
                        .findByVersionIdAndKey(skill.version().versionId(), referenceKey)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Skill 未挂载该参考文档: "
                                                        + skillCode
                                                        + ':'
                                                        + referenceKey));
        var content =
                skillReferences
                        .readContent(reference.documentId(), reference.documentVersionId())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "参考文档内容不可用: " + skillCode + ':' + referenceKey));
        return result(
                content,
                Map.of(
                        "kind",
                        "SKILL_REFERENCE",
                        "skillCode",
                        skillCode,
                        "referenceKey",
                        referenceKey,
                        "title",
                        reference.title()));
    }

    private static ToolInvocationResult result(String content, Map<String, Object> metadata) {
        var values = new LinkedHashMap<>(metadata);
        values.put("content", content);
        return new ToolInvocationResult(JsonUtils.toJsonString(values), metadata);
    }

    private static Kind requireKind(Map<String, Object> arguments) {
        var value = arguments.get("kind");
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("context.load 需要非空 kind");
        }
        try {
            return Kind.valueOf(value.toString().trim());
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("context.load kind 不支持: " + value, failure);
        }
    }

    private static String requireKey(Map<String, Object> arguments) {
        var value = arguments.get("key");
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("context.load 需要非空 key");
        }
        return value.toString().trim();
    }

    /** 可加载的内容类型。{@code KNOWLEDGE_BINDING} 先行预留枚举值，命中即报错，不静默忽略。 */
    public enum Kind {
        SKILL,
        SKILL_REFERENCE,
        KNOWLEDGE_BINDING
    }
}

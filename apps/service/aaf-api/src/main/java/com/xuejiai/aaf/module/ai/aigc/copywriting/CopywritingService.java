package com.xuejiai.aaf.module.ai.aigc.copywriting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.constant.SysConfigKeys;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantInvocation;
import com.xuejiai.aaf.framework.intelligent.assistant.model.EffectiveContextManifest.SourceType;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskModelSelection;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.DefaultUserAssistantTemplate;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.system.config.service.SystemConfigService;
import com.xuejiai.aaf.module.ai.aigc.copywriting.api.AigcCopywritingApi;
import com.xuejiai.aaf.module.ai.aigc.copywriting.vo.CopywritingAssetPageVO;
import com.xuejiai.aaf.module.ai.aigc.copywriting.vo.CopywritingAssetPageVO.Asset;
import com.xuejiai.aaf.module.ai.aigc.copywriting.vo.CopywritingAssetPageVO.Project;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectApi;
import com.xuejiai.aaf.module.ai.assistant.service.AssistantExecutionService;
import com.xuejiai.aaf.module.ai.assistant.service.AssistantExecutionService.ControlledContext;
import com.xuejiai.aaf.module.ai.assistant.service.AssistantExecutionService.ExecutionSpec;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionEventVO;
import com.xuejiai.aaf.module.ai.skill.SkillService;
import com.xuejiai.aaf.module.document.api.DocumentReferenceApi;
import com.xuejiai.aaf.module.document.api.DocumentReferenceApi.DocumentQuery;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import tools.jackson.core.type.TypeReference;

/** 文案领域 facade；所有非 mock 执行统一进入默认 Assistant 的 content.draft route。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CopywritingService implements AigcCopywritingApi {

    private static final String COPYWRITING_ROUTE = "content.draft";
    private static final String COPYWRITING_DOCUMENT_TYPE = "copywriting";
    private static final int SUMMARY_CODE_POINT_LIMIT = 160;
    private static final Pattern WHITESPACE =
            Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private final AssistantExecutionService assistantExecutionService;
    private final SystemConfigService systemConfigService;
    private final SkillService skillService;
    private final DocumentReferenceApi documentReferenceApi;
    private final AigcProjectApi aigcProjectApi;
    private final OperatorContext operatorContext;

    public CopywritingAssetPageVO assets(
            CopywritingLinkStatus linkStatus,
            Long projectId,
            String keyword,
            int pageNo,
            int pageSize) {
        var effectiveLinkStatus = linkStatus == null ? CopywritingLinkStatus.ALL : linkStatus;
        if (effectiveLinkStatus == CopywritingLinkStatus.UNLINKED && projectId != null) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "查询未关联文案时不能指定项目");
        }
        if (projectId != null && projectId <= 0) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "项目 ID 必须为正数");
        }
        if (keyword != null && keyword.length() > 100) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "关键词长度不能超过100");
        }
        if (pageNo < 1 || pageSize < 1 || pageSize > 50) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "分页参数不正确");
        }

        var ownerId =
                operatorContext
                        .currentOwnerId()
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.UNAUTHORIZED, "账号未登录"));
        var orgId = OrgContext.getCurrentOrgId();
        var workspaceId = OrgContext.getCurrentWorkspaceId();
        var needsLinkedDocumentIds =
                projectId != null || effectiveLinkStatus != CopywritingLinkStatus.ALL;
        var linkedDocumentIds =
                needsLinkedDocumentIds
                        ? aigcProjectApi.findLinkedDocumentIds(
                                ownerId, orgId, workspaceId, projectId)
                        : Set.<Long>of();
        Collection<Long> includeIds = null;
        Collection<Long> excludeIds = null;
        switch (effectiveLinkStatus) {
            case ALL -> {
                if (projectId != null) {
                    includeIds = linkedDocumentIds;
                }
            }
            case LINKED -> includeIds = linkedDocumentIds;
            case UNLINKED -> excludeIds = linkedDocumentIds;
        }

        var page =
                documentReferenceApi.query(
                        new DocumentQuery(
                                ownerId,
                                orgId,
                                workspaceId,
                                COPYWRITING_DOCUMENT_TYPE,
                                keyword,
                                includeIds,
                                excludeIds,
                                pageNo,
                                pageSize));
        var documentIds = page.list().stream().map(item -> item.documentId()).toList();
        var projectReferences =
                aigcProjectApi.findDocumentProjects(ownerId, orgId, workspaceId, documentIds);
        var projectsByDocument = new LinkedHashMap<Long, List<Project>>();
        var seenProjectsByDocument = new LinkedHashMap<Long, Set<Long>>();
        for (var reference : projectReferences) {
            var seenProjectIds =
                    seenProjectsByDocument.computeIfAbsent(
                            reference.documentId(), ignored -> new HashSet<>());
            if (seenProjectIds.add(reference.projectId())) {
                projectsByDocument
                        .computeIfAbsent(reference.documentId(), ignored -> new ArrayList<>())
                        .add(new Project(reference.projectId(), reference.projectName()));
            }
        }

        var assets =
                page.list().stream()
                        .map(
                                document ->
                                        new Asset(
                                                document.documentId(),
                                                document.title(),
                                                summarize(document.content()),
                                                document.updateTime(),
                                                projectsByDocument.getOrDefault(
                                                        document.documentId(), List.of())))
                        .toList();
        return new CopywritingAssetPageVO(
                assets, page.total(), page.pageNo(), page.pageSize(), page.hasMore());
    }

    private static String summarize(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        var normalized = WHITESPACE.matcher(content).replaceAll(" ").trim();
        var codePointCount = normalized.codePointCount(0, normalized.length());
        if (codePointCount <= SUMMARY_CODE_POINT_LIMIT) {
            return normalized;
        }
        var endIndex = normalized.offsetByCodePoints(0, SUMMARY_CODE_POINT_LIMIT);
        return normalized.substring(0, endIndex) + "…";
    }

    @Override
    public Flux<String> generate(
            String modelId,
            String type,
            String prompt,
            String template,
            String length,
            String translateTo,
            List<String> referenceImageKeys) {
        return textDeltas(
                generateEvents(
                        modelId, type, prompt, template, length, translateTo, referenceImageKeys));
    }

    public Flux<AssistantExecutionEventVO> generateEvents(
            String modelId,
            String type,
            String prompt,
            String template,
            String length,
            String translateTo,
            List<String> referenceImageKeys) {
        if (isMockEnabled()) {
            return mockEventStream();
        }
        var skillContext = generationSkillContext(type);
        var input =
                buildGeneratePrompt(
                        type,
                        requireText(prompt, "创作提示词不能为空"),
                        template,
                        length,
                        translateTo,
                        type != null && !type.isBlank());
        log.info(
                "[文案生成] type={}, length={}, translateTo={}, modelId={}",
                type,
                length,
                translateTo,
                modelId);
        return execute(modelId, input, skillContext, referenceImageKeys);
    }

    public Flux<AssistantExecutionEventVO> generateFromAnalysisEvents(
            String modelId, String analysis, String userNotes) {
        if (isMockEnabled()) {
            return mockEventStream();
        }
        var input = new StringBuilder();
        if (userNotes != null && !userNotes.isBlank()) {
            input.append("创作主题：").append(userNotes.trim()).append('\n');
        }
        input.append("参考以下爆款结构分析来组织内容：\n").append(requireText(analysis, "结构分析不能为空"));
        log.info("[爆款复制生成] modelId={}, analysisLength={}", modelId, analysis.length());
        return execute(
                modelId,
                input.toString(),
                builtInContext(
                        "copywriting.generate-from-analysis",
                        "爆款结构参考创作",
                        CopywritingConstants.SYS_GENERATE));
    }

    public Flux<AssistantExecutionEventVO> rewriteEvents(String modelId, String content) {
        if (isMockEnabled()) {
            return mockEventStream();
        }
        var normalized = requireText(content, "原始文案不能为空");
        log.info("[文案改写] modelId={}, length={}", modelId, normalized.length());
        return execute(
                modelId,
                "请改写以下文案：\n\n" + normalized,
                builtInContext("copywriting.rewrite", "文案改写", CopywritingConstants.SYS_REWRITE));
    }

    public Flux<AssistantExecutionEventVO> analyzeEvents(String modelId, String content) {
        if (isMockEnabled()) {
            return mockEventStream();
        }
        var normalized = requireText(content, "待分析内容不能为空");
        log.info("[爆款分析] modelId={}, length={}", modelId, normalized.length());
        return execute(
                modelId,
                "请分析以下爆款内容：\n\n" + normalized,
                builtInContext("copywriting.analyze", "爆款结构分析", CopywritingConstants.SYS_ANALYZE));
    }

    private Flux<AssistantExecutionEventVO> execute(
            String modelId, String input, ControlledContext controlledContext) {
        return execute(modelId, input, controlledContext, List.of());
    }

    private Flux<AssistantExecutionEventVO> execute(
            String modelId,
            String input,
            ControlledContext controlledContext,
            List<String> referenceImageKeys) {
        var spec =
                new ExecutionSpec(
                        DefaultUserAssistantTemplate.ASSISTANT_ID,
                        DefaultUserAssistantTemplate.VERSION.value(),
                        input,
                        COPYWRITING_ROUTE,
                        Set.of(),
                        5,
                        0.2,
                        modelId == null || modelId.isBlank()
                                ? TaskModelSelection.auto()
                                : TaskModelSelection.explicit(modelId),
                        AssistantInvocation.MemoryMode.DISABLED,
                        List.of(),
                        referenceImageKeys,
                        List.of(controlledContext));
        return assistantExecutionService.execute(spec);
    }

    private ControlledContext generationSkillContext(String type) {
        if (type == null || type.isBlank()) {
            return builtInContext(
                    "copywriting.generate", "通用文案生成", CopywritingConstants.SYS_GENERATE);
        }
        var skill = skillService.requireVisibleActive(type);
        return new ControlledContext(
                SourceType.SKILL,
                "skill:" + skill.code(),
                skill.version() == null ? "1" : skill.version().toString(),
                "VISIBLE_ACTIVE",
                "用户选择的可见激活技能",
                "技能：" + skill.name(),
                true,
                skill.systemPrompt());
    }

    private static ControlledContext builtInContext(
            String sourceKey, String summary, String systemPrompt) {
        return new ControlledContext(
                SourceType.SKILL,
                sourceKey,
                "1",
                "SYSTEM",
                "文案领域受控系统提示",
                summary,
                false,
                systemPrompt);
    }

    private String buildGeneratePrompt(
            String type,
            String prompt,
            String template,
            String length,
            String translateTo,
            boolean hasSelectedSkill) {
        var lengthDesc =
                switch (length != null ? length : "medium") {
                    case "short" -> "短篇（200字以内）";
                    case "long" -> "长篇（优先保证内容完整，不超过3000字）";
                    default -> "中篇（200-500字）";
                };
        var content = new StringBuilder("创作提示词：").append(prompt).append('\n');
        if (!hasSelectedSkill && ("voiceover".equals(type) || "redbook".equals(type))) {
            appendFormatRule(content, type);
        }
        if (template != null && !template.isBlank()) {
            var templateLabel =
                    switch (template) {
                        case "product-launch" -> "新品上市";
                        case "promotion" -> "促销活动";
                        case "brand-story" -> "品牌故事";
                        case "tutorial" -> "教程攻略";
                        case "review" -> "测评分享";
                        default -> template;
                    };
            content.append("风格模板：").append(templateLabel).append('\n');
        }
        content.append("长度要求：").append(lengthDesc);
        if (translateTo != null && !translateTo.isBlank()) {
            var language =
                    switch (translateTo) {
                        case "en" -> "英文";
                        case "ja" -> "日文";
                        case "ko" -> "韩文";
                        case "fr" -> "法文";
                        case "es" -> "西班牙文";
                        default -> translateTo;
                    };
            content.append("\n翻译要求：生成完成后将内容翻译为").append(language);
        }
        return content.toString();
    }

    private static void appendFormatRule(StringBuilder content, String type) {
        if ("voiceover".equals(type)) {
            content.append("格式要求：使用标准 Markdown 格式，用 `##` 分段标题、`-` 列表组织结构，自然流畅，适合视频配音。\n");
        } else {
            content.append("格式要求：活泼有趣，多用 emoji，有吸引力的标题，直接输出纯文本，不要使用 Markdown 语法。\n");
        }
    }

    private Flux<String> textDeltas(Flux<AssistantExecutionEventVO> events) {
        return events.<String>handle(
                (event, sink) -> {
                    var errorMessage = event.payload().get("message");
                    if (event.payload().containsKey("errorCode")
                            && errorMessage instanceof String message) {
                        sink.error(
                                new BusinessException(
                                        GlobalErrorCode.INTERNAL_SERVER_ERROR, message));
                        return;
                    }
                    var delta = event.payload().get("delta");
                    if ("MESSAGE_DELTA".equals(event.type())
                            && delta instanceof String text
                            && !text.isEmpty()) {
                        sink.next(text);
                    }
                });
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private boolean isMockEnabled() {
        return systemConfigService.getBoolean(SysConfigKeys.Aigc.MOCK_ENABLED, false);
    }

    private Flux<AssistantExecutionEventVO> mockEventStream() {
        var text = mockText();
        var sequence = 1L;
        var events = new java.util.ArrayList<AssistantExecutionEventVO>();
        events.add(AssistantExecutionEventVO.mockStarted(sequence++));
        for (var codePoint : text.codePoints().toArray()) {
            events.add(
                    AssistantExecutionEventVO.mockDelta(
                            sequence++, new String(Character.toChars(codePoint))));
        }
        events.add(AssistantExecutionEventVO.mockCompleted(sequence++, text));
        events.add(AssistantExecutionEventVO.mockExecutionCompleted(sequence));
        return Flux.fromIterable(events);
    }

    private String mockText() {
        var json = systemConfigService.getString(SysConfigKeys.Aigc.MOCK_DATA);
        var mockText = "这是一段 Mock 固定文字内容。";
        if (json == null || json.isBlank()) {
            return mockText;
        }
        try {
            var values =
                    JsonUtils.parseObject(
                            json, new TypeReference<java.util.Map<String, String>>() {});
            var configured = values.get("text");
            return configured == null || configured.isBlank() ? mockText : configured;
        } catch (RuntimeException failure) {
            log.warn("[mock] 解析 aigc.mock_data 失败: {}", failure.getClass().getSimpleName());
            return mockText;
        }
    }
}

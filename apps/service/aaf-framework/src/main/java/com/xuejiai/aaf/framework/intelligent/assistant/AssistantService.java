/**
 * Assistant 统一入口——串联意图理解→Skill 匹配→Agent 调度→学习反馈。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.assistant;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.agent.AgentRegistryService;
import com.xuejiai.aaf.framework.intelligent.agent.CognitiveCycleExecutor;
import com.xuejiai.aaf.framework.intelligent.cognition.memory.MemoryMessage;
import com.xuejiai.aaf.framework.intelligent.cognition.memory.ShortTermMemoryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Assistant 会话处理入口： 用户输入 → 意图理解 → Skill 匹配 → Agent 认知循环 → 记忆更新 → 返回。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantService {

    private final SessionManager sessionManager;
    private final IntentUnderstandingService intentService;
    private final EmotionPerceptionService emotionService;
    private final SkillMatchService skillMatch;
    private final AgentRegistryService agentRegistry;
    private final CognitiveCycleExecutor cycleExecutor;
    private final ShortTermMemoryService shortTermMemory;
    private final LearningFeedbackService learningFeedback;
    private final AssistantDefinitionRepository assistantRepo;

    /**
     * 处理用户消息（完整链路）。
     *
     * @param sessionId 会话 ID
     * @param userId 用户 ID
     * @param assistantId Assistant ID
     * @param userInput 用户输入
     * @return 助理响应
     */
    public AssistantResponse handle(
            String sessionId, Long userId, String assistantId, String userInput) {
        // 0. 加载 Assistant 配置
        var assistantDef = assistantRepo.findByAssistantId(assistantId).orElse(null);

        // 1. 会话管理
        var session =
                sessionManager
                        .getSession(sessionId)
                        .orElseGet(() -> sessionManager.createSession(userId, assistantId));
        sessionManager.updateStatus(sessionId, SessionManager.SessionStatus.PROCESSING);

        // 2. 记录用户消息到短期记忆
        shortTermMemory.append(sessionId, new MemoryMessage("user", userInput, null));

        // 3. 情感感知
        var emotion = emotionService.analyze(userInput);
        var responseStyle = emotionService.suggestStyle(emotion);

        // 4. 意图理解
        var history =
                shortTermMemory.getAll(sessionId).stream().map(MemoryMessage::content).toList();
        var intent = intentService.analyze(userInput, history);

        // 5. Skill 匹配
        var skill = skillMatch.match(assistantId, userInput);
        var agentId = skill.map(s -> s.getAgentId()).orElse(null);

        // 6. Agent 调度 + 认知循环
        String response;
        boolean success;
        if (agentId != null) {
            var definition = agentRegistry.findById(agentId);
            if (definition != null) {
                // 如果 Skill 有专属提示词，覆盖 Agent 默认
                skill.ifPresent(
                        s -> {
                            if (s.getSystemPrompt() != null)
                                definition.setSystemPrompt(s.getSystemPrompt());
                        });
                // 注入情感感知的回复风格到 system prompt
                var styleHint = "[回复风格: tone=%s, density=%s, approach=%s]"
                        .formatted(responseStyle.tone(), responseStyle.density(), responseStyle.approach());
                definition.setSystemPrompt(
                        definition.getSystemPrompt() != null
                                ? definition.getSystemPrompt() + "\n" + styleHint
                                : styleHint);
                var result = cycleExecutor.execute(
                        definition, userInput, userId, sessionId,
                        assistantDef != null ? assistantDef.getMemoryStrategy() : null,
                        assistantDef != null ? assistantDef.getKnowledgeBaseId() : null);
                response = result.response();
                success = result.success();
            } else {
                response = "抱歉，当前无法处理该请求。";
                success = false;
            }
        } else {
            response = "我不确定如何帮助你，请尝试更具体的描述。";
            success = false;
        }

        // 7. 记录助理响应到短期记忆
        shortTermMemory.append(sessionId, new MemoryMessage("assistant", response, null));

        // 8. 学习反馈（异步）
        learningFeedback.recordExecution(sessionId, userId, intent.getIntent(), success);

        sessionManager.updateStatus(sessionId, SessionManager.SessionStatus.ACTIVE);

        return new AssistantResponse(
                response, intent.getIntent(), skill.map(s -> s.getName()).orElse(null), success);
    }

    /** 助理响应 */
    public record AssistantResponse(
            String content, String intent, String skillUsed, boolean success) {}
}

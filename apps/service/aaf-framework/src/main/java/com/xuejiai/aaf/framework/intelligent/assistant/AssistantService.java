/**
 * Assistant 统一入口——薄门面，委托 DefaultAssistantExecutor。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.assistant;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.cognition.personalization.EmotionPerceptionService;
import com.xuejiai.aaf.framework.intelligent.cognition.personalization.IntentUnderstandingService;
import com.xuejiai.aaf.framework.intelligent.core.assistant.AssistantExecutor;
import com.xuejiai.aaf.framework.intelligent.core.assistant.AssistantExecutor.AssistantResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Assistant 会话处理入口（统一门面）。
 *
 * <p>职责：意图理解 + 情感感知 + 委托 AssistantExecutor 执行。 不再包含 Agent 调度逻辑——那是 DefaultAssistantExecutor 的职责。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantService {

    private final AssistantExecutor executor;
    private final IntentUnderstandingService intentService;
    private final EmotionPerceptionService emotionService;

    /** 处理用户消息（完整链路）。 */
    public AssistantResponse handle(
            String sessionId, Long userId, String assistantId, String userInput) {
        // 情感感知 + 历史追踪
        emotionService.analyzeAndTrack(sessionId, userInput);

        // 意图理解（LLM 驱动）
        var intent = intentService.analyze(userInput, null);
        log.debug("意图: {} (置信度: {})", intent.getIntent(), intent.getConfidence());

        // 需要消歧时返回澄清问题
        if (intent.isNeedsClarification() && intent.getClarificationQuestion() != null) {
            return AssistantResponse.success(intent.getClarificationQuestion(), sessionId);
        }

        // 委托 AssistantExecutor 执行
        return executor.chat(sessionId, assistantId, userId, userInput);
    }
}

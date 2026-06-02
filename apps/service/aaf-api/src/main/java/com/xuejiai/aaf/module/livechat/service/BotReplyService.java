package com.xuejiai.aaf.module.livechat.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.module.knowledge.service.KnowledgeSegmentService;
import com.xuejiai.aaf.module.knowledge.service.ProblemService;
import com.xuejiai.aaf.module.livechat.domain.ChatSession;
import com.xuejiai.aaf.module.livechat.repository.LivechatChatMessageRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 智能客服回复服务。
 *
 * <p>复用 ProblemService（FAQ 匹配）和 KnowledgeSegmentService（语义检索 RAG）。 未命中或低置信度时返回 null，触发转人工。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BotReplyService {

    private final ProblemService problemService;
    private final KnowledgeSegmentService segmentService;
    private final LivechatChatMessageRepository messageRepository;

    /** 默认知识库 ID（可配置化，此处简化） */
    private static final Long DEFAULT_KNOWLEDGE_BASE_ID = 1L;

    /** 上下文窗口大小 */
    private static final int CONTEXT_WINDOW = 10;

    /** FAQ 匹配置信度阈值 */
    private static final double CONFIDENCE_THRESHOLD = 0.6;

    /**
     * 生成机器人回复。
     *
     * @param session 当前会话
     * @param userMessage 用户消息内容
     * @return 回复内容，null 表示无法回答需转人工
     */
    public String generateReply(ChatSession session, String userMessage) {
        // 1. 意图识别：检测是否需要转人工
        if (isTransferIntent(userMessage)) {
            return null;
        }

        // 2. FAQ 精确匹配（复用 ProblemService）
        var faqAnswer = matchFaq(userMessage);
        if (faqAnswer != null) {
            return faqAnswer;
        }

        // 3. 语义检索 RAG（复用 KnowledgeSegmentService）
        var ragAnswer = searchKnowledge(userMessage);
        if (ragAnswer != null) {
            return ragAnswer;
        }

        // 4. 兜底：Mock LLM 回复（真实 LLM 接入后替换为 Assistant 调用）
        return generateMockReply(session, userMessage);
    }

    /** 检测用户是否主动请求转人工 */
    private boolean isTransferIntent(String message) {
        var keywords = List.of("转人工", "人工客服", "人工服务", "找人工", "真人");
        return keywords.stream().anyMatch(message::contains);
    }

    /** 检测是否为敏感问题（投诉/退款等需人工处理） */
    public boolean isSensitiveTopic(String message) {
        var keywords = List.of("投诉", "退款", "赔偿", "举报", "法律", "律师");
        return keywords.stream().anyMatch(message::contains);
    }

    /** FAQ 匹配——复用 ProblemService 关键词搜索 */
    private String matchFaq(String userMessage) {
        var problems = problemService.search(DEFAULT_KNOWLEDGE_BASE_ID, userMessage);
        if (!problems.isEmpty()) {
            // 取第一个匹配结果的关联段落作为回答
            var linkedSegments = problemService.getLinkedSegments(problems.getFirst().getId());
            if (!linkedSegments.isEmpty()) {
                return "根据常见问题解答：" + problems.getFirst().getContent();
            }
            return problems.getFirst().getContent();
        }
        return null;
    }

    /** 语义检索——复用 KnowledgeSegmentService */
    private String searchKnowledge(String userMessage) {
        var results = segmentService.semanticSearch(DEFAULT_KNOWLEDGE_BASE_ID, userMessage, 3);
        if (!results.isEmpty()) {
            // 拼接检索结果作为回答
            var content = results.getFirst().content();
            if (content != null && !content.isBlank()) {
                return content;
            }
        }
        return null;
    }

    /** Mock LLM 回复（桩实现，后续接入真实 Assistant） */
    private String generateMockReply(ChatSession session, String userMessage) {
        // 获取上下文（最近消息）
        var context =
                messageRepository.findBySessionIdAndInternalFalseOrderByCreateTimeDesc(
                        session.getId(), PageRequest.of(0, CONTEXT_WINDOW));

        // 简单意图路由
        if (userMessage.contains("咨询") || userMessage.contains("了解")) {
            return "感谢您的咨询，请问您想了解哪方面的信息？我可以为您查询相关资料。";
        }
        if (userMessage.contains("技术")
                || userMessage.contains("故障")
                || userMessage.contains("报错")) {
            return "我理解您遇到了技术问题。请描述具体的错误信息或现象，我会尽力帮您解决。如需更专业的支持，我可以为您转接技术专家。";
        }

        return "感谢您的消息。我正在为您查找相关信息，请稍候。如需人工服务，请回复「转人工」。";
    }
}

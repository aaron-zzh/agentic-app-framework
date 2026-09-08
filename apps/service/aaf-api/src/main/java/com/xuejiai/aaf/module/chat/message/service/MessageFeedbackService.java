package com.xuejiai.aaf.module.chat.message.service;

import static com.xuejiai.aaf.module.chat.message.MessageFeedbackErrorCode.MESSAGE_FEEDBACK_MESSAGE_NOT_FOUND;
import static com.xuejiai.aaf.module.chat.message.MessageFeedbackErrorCode.MESSAGE_FEEDBACK_TYPE_INVALID;
import static com.xuejiai.aaf.module.chat.message.MessageFeedbackErrorCode.MESSAGE_FEEDBACK_TYPE_REQUIRED;

import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.module.ai.chat.service.ChatService;
import com.xuejiai.aaf.module.ai.chat.vo.MessageFeedbackDTO;
import com.xuejiai.aaf.module.chat.conversation.repository.ConversationRepository;
import com.xuejiai.aaf.module.chat.message.repository.ConversationMessageRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AG-UI 消息反馈服务（AAF-114 #11412）。
 *
 * <p>按 {@code threadId + aguiMessageId}（前端 assistant-ui {@code ThreadMessage.id}）定位消息，写入 {@code
 * ConversationMessage.metadata}。不新建独立反馈表——复用现有 JSONB 列，反馈与消息是 1:1（同一消息重复提交覆盖上一次 反馈，不追加历史），符合
 * assistant-ui {@code FeedbackAdapter} 每条消息最多一个当前反馈状态的语义。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageFeedbackService {

    private static final Set<String> VALID_TYPES = Set.of("positive", "negative");

    private final ChatService chatService;
    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;

    /**
     * 提交消息反馈。
     *
     * @param threadId AG-UI 线程 ID
     * @param aguiMessageId 前端可见的消息 ID（AI 消息为 {@code replyId:blockId}）
     * @param dto 反馈内容
     * @throws BusinessException threadId 不属于当前用户、消息不存在或反馈类型缺失时抛出
     */
    @Transactional
    public void submit(String threadId, String aguiMessageId, MessageFeedbackDTO dto) {
        if (dto.type() == null || dto.type().isBlank()) {
            throw new BusinessException(MESSAGE_FEEDBACK_TYPE_REQUIRED);
        }
        if (!VALID_TYPES.contains(dto.type())) {
            throw new BusinessException(MESSAGE_FEEDBACK_TYPE_INVALID);
        }
        // threadId 归属校验复用既有 AG-UI 边界校验，与 AssistantAguiController.run 入口一致
        chatService.requireOwnedAiThread(threadId);
        var conversation =
                conversationRepository
                        .findByThreadId(threadId)
                        .orElseThrow(
                                () -> new BusinessException(MESSAGE_FEEDBACK_MESSAGE_NOT_FOUND));
        var message =
                messageRepository
                        .findByConversationIdAndAguiMessageId(conversation.getId(), aguiMessageId)
                        .orElseThrow(
                                () -> new BusinessException(MESSAGE_FEEDBACK_MESSAGE_NOT_FOUND));

        var metadata = JsonUtils.createObjectNode();
        metadata.put("feedbackType", dto.type());
        if (dto.reason() != null && !dto.reason().isBlank()) {
            metadata.put("feedbackReason", dto.reason());
        }
        if (dto.model() != null && !dto.model().isBlank()) {
            metadata.put("feedbackModel", dto.model());
        }
        if (dto.runId() != null && !dto.runId().isBlank()) {
            metadata.put("feedbackRunId", dto.runId());
        }
        message.setMetadata(metadata.toString());
        messageRepository.save(message);
        log.info(
                "消息反馈已记录: threadId={}, aguiMessageId={}, type={}",
                threadId,
                aguiMessageId,
                dto.type());
    }
}

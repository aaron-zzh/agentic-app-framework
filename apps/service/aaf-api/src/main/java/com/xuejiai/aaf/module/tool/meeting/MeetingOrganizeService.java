package com.xuejiai.aaf.module.tool.meeting;

import java.time.LocalDate;
import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.ai.chat.ResilientChatService;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;
import com.xuejiai.aaf.framework.security.OperatorContext;

import lombok.RequiredArgsConstructor;

/**
 * 会议记录整理服务——写死提示词，同步执行，积分由 {@link ResilientChatService#call} 内部预检+结算。
 */
@Service
@RequiredArgsConstructor
public class MeetingOrganizeService {

    private static final String SYSTEM_PROMPT = """
            你是一名专业会议记录助理，擅长将口语化的会议转写文本整理为规范的结构化会议记录。
            
            请严格按照以下 Markdown 格式输出，不要输出格式之外的任何内容：
            
            # 会议记录
            
            **日期**：{date}
            
            ## 主要议题
            - （逐条列举讨论的核心议题）
            
            ## 决策事项
            - （列举本次会议达成的决定，格式：决策内容 — 决策人（如能识别））
            
            ## 待办列表
            - [ ] 任务描述 — 负责人（如能识别）— 截止日期（如提及）
            
            ## 重要备注
            （其他需要关注的信息，无则省略此节）
            
            规则：
            1. 保持客观，不添加原文没有的信息
            2. 日期使用占位符 {date}，由系统替换为实际日期
            3. 若无法识别某字段，用"未明确"代替
            """;

    private final ResilientChatService chatService;
    private final OperatorContext operatorContext;

    /**
     * 整理会议记录。
     *
     * @param dto 请求参数
     * @return 整理后的 Markdown 内容
     */
    public String organize(MeetingOrganizeDTO dto) {
        String date = dto.meetingDate() != null ? dto.meetingDate() : LocalDate.now().toString();
        String systemPrompt = SYSTEM_PROMPT.replace("{date}", date);

        List<Message> messages = List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(dto.transcript())
        );

        Long userId = operatorContext.currentOwnerId().orElse(null);
        var ctx = CapabilityRoutingContext.of(userId, CapabilityRoutingContext.CAP_CHAT, dto.modelId());
        var response = chatService.call(messages, ctx);

        return response.getResult().getOutput().getText();
    }
}

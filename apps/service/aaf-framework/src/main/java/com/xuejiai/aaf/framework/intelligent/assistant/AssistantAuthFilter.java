package com.xuejiai.aaf.framework.intelligent.assistant;

import java.io.IOException;

import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 解析 X-Assistant-Id，并建立 AI 委托双主体上下文。 */
@Component
public class AssistantAuthFilter extends OncePerRequestFilter {

    public static final String HEADER_ASSISTANT_ID = "X-Assistant-Id";

    private final AssistantDefinitionRepository assistantRepository;

    public AssistantAuthFilter(@Lazy AssistantDefinitionRepository assistantRepository) {
        this.assistantRepository = assistantRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var assistantId = request.getHeader(HEADER_ASSISTANT_ID);
        if (assistantId == null || assistantId.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        Long assistantDefinitionId;
        try {
            assistantDefinitionId = Long.parseLong(assistantId.trim());
        } catch (NumberFormatException e) {
            response.sendError(HttpStatus.FORBIDDEN.value(), "助理 ID 格式无效");
            return;
        }
        var assistant = assistantRepository.findById(assistantDefinitionId).orElse(null);
        if (assistant == null || !"active".equalsIgnoreCase(assistant.getStatus())) {
            response.sendError(HttpStatus.FORBIDDEN.value(), "助理不存在或已停用");
            return;
        }

        try {
            AssistantContextHolder.set(
                    new AssistantContextHolder.AssistantContext(
                            assistant.getId(),
                            assistantId.trim(),
                            assistant.getEffectiveDelegatorId()));
            filterChain.doFilter(request, response);
        } finally {
            AssistantContextHolder.clear();
        }
    }
}

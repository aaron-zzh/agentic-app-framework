package com.xuejiai.aaf.framework.engine.knowledge.rag;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/** 检索增强生成服务 — 检索→构建 Prompt→调用 LLM→返回带引用的答案 */
@Service
@RequiredArgsConstructor
public class RagGenerationService {

    private static final String SYSTEM_PROMPT =
            """
            你是一个知识库问答助手。请根据以下检索到的参考资料回答用户问题。
            要求：
            1. 仅基于提供的参考资料回答，不要编造信息
            2. 在回答中使用 [编号] 标注引用来源
            3. 如果参考资料不足以回答问题，请明确说明

            参考资料：
            %s
            """;

    private final HybridSearchService hybridSearchService;
    private final CitationService citationService;
    private final ChatModel chatModel;

    /** 执行 RAG：检索 → 生成 → 溯源 */
    public RagResponse generate(String question, Long knowledgeBaseId) {
        // 检索
        var sources =
                hybridSearchService.search(question, knowledgeBaseId, new HybridSearchConfig());

        // 构建上下文
        var context =
                IntStream.range(0, sources.size())
                        .mapToObj(i -> "[%d] %s".formatted(i + 1, sources.get(i).content()))
                        .collect(Collectors.joining("\n\n"));

        // 调用 LLM
        var response =
                ChatClient.create(chatModel)
                        .prompt()
                        .system(SYSTEM_PROMPT.formatted(context))
                        .user(question)
                        .call()
                        .chatResponse();

        var answer = response.getResult().getOutput().getText();
        var tokensUsed = (int) response.getMetadata().getUsage().getTotalTokens();

        // 引用溯源
        var citations = citationService.extractCitations(answer, sources);

        return new RagResponse(answer, citations, tokensUsed);
    }
}

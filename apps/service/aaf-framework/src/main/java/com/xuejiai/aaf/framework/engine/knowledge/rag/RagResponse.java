package com.xuejiai.aaf.framework.engine.knowledge.rag;

import java.util.List;

/** RAG 生成响应 */
public record RagResponse(String answer, List<Citation> citations, int tokensUsed) {}

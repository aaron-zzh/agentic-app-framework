package com.xuejiai.aaf.framework.intelligent.core.context;

/** 上下文摘要器。 */
public interface ContextSummarizer {

    SummaryResult summarize(SummaryRequest request);
}

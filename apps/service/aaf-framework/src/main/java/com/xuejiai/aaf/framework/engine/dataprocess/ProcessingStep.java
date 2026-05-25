package com.xuejiai.aaf.framework.engine.dataprocess;

/**
 * 数据处理步骤——Pipeline 中的单个处理环节。
 */
public interface ProcessingStep {

    /** 步骤名称。 */
    String name();

    /**
     * 处理数据。
     *
     * @param context 处理上下文（含数据和配置）
     * @return 处理后的上下文
     */
    ProcessingContext execute(ProcessingContext context);
}

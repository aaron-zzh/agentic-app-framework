package com.xuejiai.aaf.framework.engine.dataprocess;

import java.util.Map;

/**
 * 数据处理引擎——ETL、数据清洗、格式转换。
 *
 * <p>职责：结构化/非结构化数据的抽取、转换、加载。 v0.2+ 实现。
 */
public interface DataProcessEngine {

    /** 执行数据处理管道。 */
    ProcessResult process(ProcessRequest request);

    /** 处理请求 */
    record ProcessRequest(String pipelineId, Map<String, Object> input) {}

    /** 处理结果 */
    record ProcessResult(boolean success, Map<String, Object> output, String error) {}
}

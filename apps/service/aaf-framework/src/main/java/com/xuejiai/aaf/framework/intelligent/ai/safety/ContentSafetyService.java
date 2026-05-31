package com.xuejiai.aaf.framework.intelligent.ai.safety;

/** 生成式内容安全审查 SPI。 */
public interface ContentSafetyService {

    /** 提交生成前审查；实现可同步拒绝，也可返回等待人工/模型复审。 */
    ContentSafetyResult reviewBeforeGeneration(ContentSafetyRequest request);
}

package com.xuejiai.aaf.framework.engine.valuerule;

import java.util.List;

/**
 * 价值规则引擎——对内容进行价值观校验和过滤。
 *
 * <p>应用场景：
 *
 * <ul>
 *   <li>记忆检索后过滤不符合价值观的结果
 *   <li>Agent 输出前的安全校验
 *   <li>知识库内容入库前的合规检查
 * </ul>
 */
public interface ValueRuleEngine {

    /** 校验结果 */
    record ValidationResult(boolean passed, String reason) {
        public static ValidationResult pass() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult reject(String reason) {
            return new ValidationResult(false, reason);
        }
    }

    /**
     * 校验单条内容是否符合价值规则。
     *
     * @param content 待校验内容
     * @return 校验结果
     */
    ValidationResult validate(String content);

    /**
     * 批量过滤：保留通过校验的内容。
     *
     * @param contents 待过滤内容列表
     * @return 通过校验的内容
     */
    <T> List<T> filter(List<T> contents, ContentExtractor<T> extractor);

    /** 内容提取器（从泛型对象中提取待校验文本） */
    @FunctionalInterface
    interface ContentExtractor<T> {
        String extract(T item);
    }
}

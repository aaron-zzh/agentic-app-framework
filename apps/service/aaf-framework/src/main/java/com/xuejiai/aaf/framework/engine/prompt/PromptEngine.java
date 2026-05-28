/**
 * Prompt 引擎接口——提示词库存储、版本管理、链式组装、评估优化。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.engine.prompt;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Prompt 引擎：承载提示词的完整生命周期管理。 */
public interface PromptEngine {

    // ─── 存储与版本 ───

    /** 创建新模板 */
    PromptTemplate create(PromptTemplate template);

    /** 创建新版本（旧版本自动失活） */
    PromptTemplate createNewVersion(String name, String content);

    /** 查找当前激活版本 */
    Optional<PromptTemplate> findActive(String name);

    /** 按名称和版本查找 */
    Optional<PromptTemplate> findByVersion(String name, int version);

    /** 查找所有版本（按版本号降序） */
    List<PromptTemplate> findAllVersions(String name);

    /** 按分类查找 */
    List<PromptTemplate> findByCategory(String category);

    // ─── 渲染与组装 ───

    /** 渲染模板：变量注入 */
    String render(String templateName, Map<String, String> variables);

    /** 按指定版本渲染 */
    String render(String templateName, int version, Map<String, String> variables);

    /** 链式组装：多模板片段按顺序拼接 */
    String chain(List<String> templateNames, Map<String, String> variables);

    /** 渲染并注入 Few-shot 示例 */
    String renderWithExamples(String templateName, Map<String, String> variables, int maxExamples);

    // ─── 评估 ───

    /** 评估 Prompt 质量 */
    PromptEvalResult evaluate(String templateName, List<PromptEvalCase> testCases);

    /** 评估结果 */
    record PromptEvalResult(String templateName, double score, String summary) {}

    /** 评估用例 */
    record PromptEvalCase(Map<String, String> variables, String expectedOutput) {}
}

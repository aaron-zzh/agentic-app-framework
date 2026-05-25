package com.xuejiai.aaf.framework.engine.dsl;

/**
 * DSL 引擎——解析和执行领域特定语言指令。
 *
 * <p>职责：交互指令解析、声明式工作流定义、组件及知识定义。
 * v0.2+ 实现。
 */
public interface DslEngine {

    /** 解析 DSL 文本为可执行指令。 */
    ParseResult parse(String dslText);

    /** 执行已解析的指令。 */
    ExecutionResult execute(ParseResult parsed);

    /** 解析结果 */
    record ParseResult(boolean valid, String errorMessage, Object ast) {}

    /** 执行结果 */
    record ExecutionResult(boolean success, String output, String error) {}
}

/**
 * AgentScope 适配器环——AAF 与 AgentScope Java SDK 的唯一耦合边界。
 *
 * <p>领域层（{@code core} 接口 + {@code agent}/{@code assistant}/{@code cognition} 根）保持零 AgentScope
 * 依赖；本包是 ports-and-adapters 的 adapter：实现 AAF 接口并委托 AgentScope， 或反向实现 AgentScope SPI
 * 把自有引擎插入其扩展点。切换底层框架只需替换本包。
 *
 * <p>子包：runtime（构建 ReActAgent）/ hook（行为扩展）/ memory / knowledge / tool / session / a2a。
 *
 * <p>设计见 docs/design/framework/intelligent/assistant-agent-runtime-refactor.md。
 */
package com.xuejiai.aaf.framework.intelligent.agentscope;

package com.xuejiai.aaf.framework.engine.tool;

import java.time.Duration;

/**
 * 脚本执行器接口——支持多种实现按需切换/降级。
 *
 * <p>优先级：GraalVM Polyglot（JVM 内）→ 子进程（外部 python3/node）
 */
public interface ScriptExecutor {

    /** 执行 JavaScript 脚本。 */
    ScriptSandbox.ScriptResult executeJs(String code, String argsJson, Duration timeout);

    /** 执行 Python 脚本。 */
    ScriptSandbox.ScriptResult executePython(String code, String argsJson, Duration timeout);

    /** 当前执行器类型。 */
    String type();
}

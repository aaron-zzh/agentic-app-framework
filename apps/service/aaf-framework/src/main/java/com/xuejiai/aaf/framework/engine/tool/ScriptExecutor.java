package com.xuejiai.aaf.framework.engine.tool;

import java.time.Duration;

/** 脚本执行器接口——所有不可信脚本必须通过受限运行时执行。 */
public interface ScriptExecutor {

    /** 执行 JavaScript 脚本。 */
    ScriptResult executeJs(String code, String argsJson, Duration timeout);

    /** 执行 Python 脚本；未配置安全运行时时必须 fail-closed。 */
    ScriptResult executePython(String code, String argsJson, Duration timeout);

    /** 当前执行器类型。 */
    String type();

    /** 脚本执行结果。 */
    record ScriptResult(boolean success, String stdout, String stderr, int exitCode) {
        public static ScriptResult timeout() {
            return new ScriptResult(false, "", "执行超时", -1);
        }

        public static ScriptResult error(String message) {
            return new ScriptResult(false, "", message, -1);
        }
    }
}

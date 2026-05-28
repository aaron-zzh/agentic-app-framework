package com.xuejiai.aaf.framework.engine.tool;

import java.time.Duration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 子进程脚本执行器——降级方案，依赖系统安装的 python3/node。 */
@Slf4j
@RequiredArgsConstructor
public class ProcessScriptExecutor implements ScriptExecutor {

    private final ScriptSandbox sandbox;

    @Override
    public ScriptSandbox.ScriptResult executeJs(String code, String argsJson, Duration timeout) {
        // 通过 node 执行 JavaScript
        var wrappedCode =
                "var args = JSON.parse(process.argv[2]);\n"
                        + code
                        + "\nconsole.log(JSON.stringify(typeof __result !== 'undefined' ? __result : null));";
        return sandbox.executeShell(
                "echo '%s' | node -e \"$(cat)\" -- '%s'"
                        .formatted(
                                wrappedCode.replace("'", "'\\''"), argsJson.replace("'", "'\\''")),
                timeout);
    }

    @Override
    public ScriptSandbox.ScriptResult executePython(
            String code, String argsJson, Duration timeout) {
        var wrappedCode = "import json, sys\nargs = json.loads(sys.argv[1])\n" + code;
        return sandbox.executePython(wrappedCode, timeout);
    }

    @Override
    public String type() {
        return "process";
    }
}

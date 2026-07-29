package com.xuejiai.aaf.framework.intelligent.agent.port;

import java.time.Duration;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;

/** 受 namespace 和 fencing 约束的脚本执行边界。 */
public interface SandboxPort {

    SandboxResult execute(SandboxRequest request);

    record SandboxRequest(
            InvocationContext context, Language language, String code, Duration timeout) {
        public SandboxRequest {
            Objects.requireNonNull(context, "context 不能为空");
            Objects.requireNonNull(language, "language 不能为空");
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("sandbox code 不能为空白");
            }
            Objects.requireNonNull(timeout, "timeout 不能为空");
            if (timeout.isZero() || timeout.isNegative()) {
                throw new IllegalArgumentException("timeout 必须大于 0");
            }
        }
    }

    record SandboxResult(boolean success, String output, String error, int exitCode) {}

    enum Language {
        PYTHON,
        SHELL
    }
}

package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** 外部输入的确定性缓冲视图。 */
public record InputBuffer(List<ExecutionInput> inputs) {

    private static final Comparator<ExecutionInput> ORDER =
            Comparator.comparing(ExecutionInput::receivedAt).thenComparing(ExecutionInput::inputId);

    public InputBuffer {
        inputs = Objects.requireNonNull(inputs, "inputs 不能为空").stream().sorted(ORDER).toList();
    }

    public MergeResult apply(ClarificationRequest current, Instant at) {
        Objects.requireNonNull(current, "current clarification 不能为空");
        Objects.requireNonNull(at, "at 不能为空");
        if (inputs.stream().anyMatch(input -> input.kind() == ExecutionInput.Kind.CANCEL)) {
            return new MergeResult(current.cancel(at), true, false, inputs);
        }
        if (!current.deadline().isAfter(at)) {
            return new MergeResult(current.expire(at), false, false, inputs);
        }
        var merged = current;
        for (var input : inputs) {
            merged =
                    switch (input.kind()) {
                        case MODIFY -> merged.apply(input.values(), true);
                        case SUPPLEMENT -> merged.apply(input.values(), false);
                        case UNRELATED -> merged;
                        case CANCEL -> throw new IllegalStateException("CANCEL 必须在合并前处理");
                    };
        }
        if (merged.complete()) {
            merged = merged.resolve(at);
        }
        return new MergeResult(
                merged, false, merged.status() == ClarificationRequest.Status.RESOLVED, inputs);
    }

    public record MergeResult(
            ClarificationRequest clarification,
            boolean canceled,
            boolean resolved,
            List<ExecutionInput> consumedInputs) {
        public MergeResult {
            Objects.requireNonNull(clarification, "clarification 不能为空");
            consumedInputs =
                    List.copyOf(Objects.requireNonNull(consumedInputs, "consumedInputs 不能为空"));
        }
    }
}

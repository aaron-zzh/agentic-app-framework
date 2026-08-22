package com.xuejiai.aaf.framework.intelligent.core.prompt;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Prompt 调用前的脱敏长度摘要。
 *
 * <p>仅保存 code point 数、启发式 Token 数和条目数，不保存任何 Prompt、用户、工具或附件正文。Token 估算按每四个 code point
 * 向上取整，只用于容量趋势和排障，不得用于计费或精确预算门控。
 */
public record PromptLengthSummary(
        Map<PromptInputKind, InputLength> inputs,
        long totalCharacters,
        long totalEstimatedTokens,
        int attachmentCount) {

    public PromptLengthSummary {
        Objects.requireNonNull(inputs, "inputs 不能为空");
        var frozen = new EnumMap<PromptInputKind, InputLength>(PromptInputKind.class);
        for (var kind : PromptInputKind.values()) {
            frozen.put(kind, Objects.requireNonNullElse(inputs.get(kind), InputLength.empty()));
        }
        var computedCharacters = frozen.values().stream().mapToLong(InputLength::characters).sum();
        var computedEstimatedTokens =
                frozen.values().stream()
                        .mapToLong(InputLength::estimatedTokensAtFourCodePoints)
                        .sum();
        if (totalCharacters != computedCharacters
                || totalEstimatedTokens != computedEstimatedTokens) {
            throw new IllegalArgumentException("Prompt 总长度必须等于分类长度之和");
        }
        inputs = Map.copyOf(frozen);
        if (totalCharacters < 0 || totalEstimatedTokens < 0 || attachmentCount < 0) {
            throw new IllegalArgumentException("Prompt 长度与附件数不能小于 0");
        }
    }

    /** 从临时正文集合计算摘要；返回对象不会持有传入正文。 */
    public static PromptLengthSummary measure(
            Map<PromptInputKind, ? extends Collection<String>> contentByKind, int attachmentCount) {
        Objects.requireNonNull(contentByKind, "contentByKind 不能为空");
        if (attachmentCount < 0) {
            throw new IllegalArgumentException("attachmentCount 不能小于 0");
        }
        var inputs = new EnumMap<PromptInputKind, InputLength>(PromptInputKind.class);
        long totalCharacters = 0;
        long totalEstimatedTokens = 0;
        for (var kind : PromptInputKind.values()) {
            var contents = contentByKind.get(kind);
            var length = measure(contents == null ? List.of() : contents);
            inputs.put(kind, length);
            totalCharacters += length.characters();
            totalEstimatedTokens += length.estimatedTokensAtFourCodePoints();
        }
        return new PromptLengthSummary(
                inputs, totalCharacters, totalEstimatedTokens, attachmentCount);
    }

    public InputLength input(PromptInputKind kind) {
        return inputs.get(Objects.requireNonNull(kind, "kind 不能为空"));
    }

    private static InputLength measure(Collection<String> contents) {
        long characters = 0;
        var itemCount = 0;
        for (var content : contents) {
            Objects.requireNonNull(content, "Prompt 统计内容不能为 null");
            characters += content.codePointCount(0, content.length());
            itemCount++;
        }
        var estimatedTokens = characters == 0 ? 0 : Math.addExact(characters, 3) / 4;
        return new InputLength(characters, estimatedTokens, itemCount);
    }

    /** 单类输入的脱敏长度。 */
    public record InputLength(
            long characters, long estimatedTokensAtFourCodePoints, int itemCount) {
        public InputLength {
            if (characters < 0 || estimatedTokensAtFourCodePoints < 0 || itemCount < 0) {
                throw new IllegalArgumentException("输入长度不能小于 0");
            }
        }

        private static InputLength empty() {
            return new InputLength(0, 0, 0);
        }
    }
}

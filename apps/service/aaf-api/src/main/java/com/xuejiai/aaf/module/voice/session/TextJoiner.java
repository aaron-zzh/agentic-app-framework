package com.xuejiai.aaf.module.voice.session;

import java.util.List;

/** 把多段识别文本拼成一句：中日文直接相连，拉丁字符之间补一个空格。 */
final class TextJoiner {

    private TextJoiner() {}

    static String join(List<String> sentences, String tail) {
        var builder = new StringBuilder();
        for (var sentence : sentences) {
            append(builder, sentence);
        }
        append(builder, tail);
        return builder.toString();
    }

    private static void append(StringBuilder builder, String part) {
        if (part == null || part.isBlank()) {
            return;
        }
        var text = part.strip();
        if (!builder.isEmpty()
                && isLatin(builder.charAt(builder.length() - 1))
                && isLatin(text.charAt(0))) {
            builder.append(' ');
        }
        builder.append(text);
    }

    private static boolean isLatin(char ch) {
        return ch < 128 && (Character.isLetterOrDigit(ch) || ".!?,;:".indexOf(ch) >= 0);
    }
}

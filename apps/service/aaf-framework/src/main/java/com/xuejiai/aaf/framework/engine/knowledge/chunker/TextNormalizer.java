package com.xuejiai.aaf.framework.engine.knowledge.chunker;

import java.util.regex.Pattern;

/**
 * 分块前的文本规范化：只压缩空白与装饰性重复符号，不删除标点与文字。
 *
 * <ul>
 *   <li>统一换行为 \n，删除零宽字符，全角空格、不间断空格等特殊空白转为普通空格
 *   <li>行内连续空格与制表符压缩为一个空格，去掉每行首尾空格
 *   <li>连续多个空行压缩为一个空行
 *   <li>连续 4 个以上的点、横线、下划线等压缩为 3 个，连续 3 个以上的省略号、破折号压缩为 2 个
 * </ul>
 */
final class TextNormalizer {

    private static final Pattern ZERO_WIDTH =
            Pattern.compile("[\\u200B\\u200C\\u200D\\u2060\\uFEFF]");
    private static final Pattern SPECIAL_SPACES =
            Pattern.compile("[\\u00A0\\u1680\\u2000-\\u200A\\u202F\\u205F\\u3000]");
    private static final Pattern INLINE_SPACES = Pattern.compile("[ \\t\\f\\u000B]+");
    private static final Pattern LINE_EDGE_SPACES = Pattern.compile("(?m)^ +| +$");
    private static final Pattern REPEATED_DECORATION = Pattern.compile("([.．·\\-_=~*＊])\\1{3,}");
    private static final Pattern REPEATED_DASH = Pattern.compile("([…—])\\1{2,}");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");

    private TextNormalizer() {}

    static String normalize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        var normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        normalized = ZERO_WIDTH.matcher(normalized).replaceAll("");
        normalized = SPECIAL_SPACES.matcher(normalized).replaceAll(" ");
        normalized = INLINE_SPACES.matcher(normalized).replaceAll(" ");
        normalized = LINE_EDGE_SPACES.matcher(normalized).replaceAll("");
        normalized = REPEATED_DECORATION.matcher(normalized).replaceAll("$1$1$1");
        normalized = REPEATED_DASH.matcher(normalized).replaceAll("$1$1");
        normalized = BLANK_LINES.matcher(normalized).replaceAll("\n\n");
        return normalized.strip();
    }
}

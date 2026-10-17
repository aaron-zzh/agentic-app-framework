package com.xuejiai.aaf.module.voice.session;

import java.util.ArrayList;
import java.util.List;

/**
 * 回复文本切句器：按增量喂入，句末标点处切出整句，长句在逗号类标点处强制切分以限制首句延迟。
 *
 * <p>切出的每句都是回复文本的原样切片（不改写、不丢字符），协议要求宣告的句子文本与 {@code reply_delta} 拼出的文本一致。每句至少含一个字母或数字，
 * 纯标点片段并入前一句之后的下一句。
 */
final class SentenceSplitter {

    private static final String HARD_END = "。！？；!?;\n…";
    private static final String SOFT_END = "，,、：:";

    private final int minChars;
    private final int maxChars;
    private final StringBuilder pending = new StringBuilder();

    SentenceSplitter(int minChars, int maxChars) {
        this.minChars = minChars;
        this.maxChars = maxChars;
    }

    /** 喂入增量，返回此刻可以切出的整句。 */
    List<String> feed(String delta) {
        var sentences = new ArrayList<String>();
        for (var i = 0; i < delta.length(); i++) {
            var ch = delta.charAt(i);
            pending.append(ch);
            if (shouldCut(ch, i + 1 < delta.length() ? delta.charAt(i + 1) : 0)) {
                emit(sentences);
            }
        }
        return sentences;
    }

    /** 回复结束，吐出剩余文本；剩余内容不含可朗读字符时丢弃。 */
    List<String> flush() {
        var sentences = new ArrayList<String>();
        if (speakable(pending)) {
            sentences.add(pending.toString());
        }
        pending.setLength(0);
        return sentences;
    }

    private boolean shouldCut(char ch, char next) {
        if (HARD_END.indexOf(ch) >= 0
                || isAsciiPeriodEnd(ch, next)
                || periodSplitAcrossDeltas(ch)) {
            return count(pending) >= minChars;
        }
        return SOFT_END.indexOf(ch) >= 0 && count(pending) >= maxChars;
    }

    /** 句号落在上一个增量末尾、空白落在本增量开头的情形。 */
    private boolean periodSplitAcrossDeltas(char ch) {
        var length = pending.length();
        return Character.isWhitespace(ch) && length >= 2 && pending.charAt(length - 2) == '.';
    }

    /** 英文句号：后面是空白才算句末，避免切断小数（3.14）与缩写。 */
    private static boolean isAsciiPeriodEnd(char ch, char next) {
        return ch == '.' && (next == ' ' || next == '\n');
    }

    private void emit(List<String> sentences) {
        if (!speakable(pending)) {
            return;
        }
        sentences.add(pending.toString());
        pending.setLength(0);
    }

    private static int count(CharSequence text) {
        var n = 0;
        for (var i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                n++;
            }
        }
        return n;
    }

    /** 是否含至少一个字母或数字。 */
    static boolean speakable(CharSequence text) {
        for (var i = 0; i < text.length(); i++) {
            if (Character.isLetterOrDigit(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}

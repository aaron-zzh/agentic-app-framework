package com.xuejiai.aaf.framework.engine.knowledge.chunker;

/** 粗略估算块的 token 数：每个汉字计 1，连续的非空白非汉字字符计 1。 */
final class TokenEstimator {

    private TokenEstimator() {}

    static int estimate(String text) {
        int count = 0;
        boolean inWord = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\u4e00' && c <= '\u9fff') {
                count++;
                inWord = false;
            } else if (c == ' ' || c == '\n' || c == '\t') {
                inWord = false;
            } else if (!inWord) {
                count++;
                inWord = true;
            }
        }
        return count;
    }
}

package com.xuejiai.aaf.module.voice.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SentenceSplitterTest {

    private final SentenceSplitter splitter = new SentenceSplitter(4, 20);

    @Test
    @DisplayName("Given 增量文本含句末标点 When 喂入 Then 在句末切出整句且保持原文")
    void should_cut_at_sentence_end_when_feeding_deltas() {
        // 调用
        var first = splitter.feed("今天天气");
        var second = splitter.feed("很好。明天");
        var rest = splitter.flush();

        // 断言
        assertThat(first).isEmpty();
        assertThat(second).containsExactly("今天天气很好。");
        assertThat(rest).containsExactly("明天");
    }

    @Test
    @DisplayName("Given 句子短于最短字数 When 遇到句末标点 Then 并入下一句而不是单独成句")
    void should_merge_short_sentence_when_below_minimum() {
        // 调用
        var sentences = splitter.feed("好的！我这就去办理。");

        // 断言
        assertThat(sentences).containsExactly("好的！我这就去办理。");
    }

    @Test
    @DisplayName("Given 小数点 When 喂入 Then 不在小数点处切句")
    void should_not_cut_inside_decimal_number() {
        // 调用
        var sentences = splitter.feed("圆周率约等于3.14159，是个无理数。");

        // 断言
        assertThat(sentences).containsExactly("圆周率约等于3.14159，是个无理数。");
    }

    @Test
    @DisplayName("Given 很长的无句末标点文本 When 遇到逗号 Then 在逗号处强制切分以限制首句延迟")
    void should_cut_at_comma_when_sentence_too_long() {
        // 调用
        var sentences = splitter.feed("这是一个非常非常非常非常非常非常长的句子，后面还有内容。");

        // 断言
        assertThat(sentences).containsExactly("这是一个非常非常非常非常非常非常长的句子，", "后面还有内容。");
    }

    @Test
    @DisplayName("Given 切出的全部句子 When 拼接 Then 与输入文本完全一致")
    void should_preserve_full_text_when_concatenated() {
        // 准备参数
        var input = "你好，我是助手。English sentence one. 第二句！结尾没有标点";

        // 调用
        var sentences = new ArrayList<String>();
        sentences.addAll(splitter.feed(input));
        sentences.addAll(splitter.flush());

        // 断言
        assertThat(String.join("", sentences)).isEqualTo(input);
    }

    @Test
    @DisplayName("Given 剩余内容只有标点 When flush Then 丢弃不产生空句")
    void should_drop_punctuation_only_remainder() {
        // 调用
        var rest = splitter.flush();
        splitter.feed("……");

        // 断言
        assertThat(rest).isEqualTo(List.of());
        assertThat(splitter.flush()).isEmpty();
    }
}

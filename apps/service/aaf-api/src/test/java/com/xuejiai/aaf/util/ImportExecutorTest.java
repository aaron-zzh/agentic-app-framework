package com.xuejiai.aaf.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@DisplayName("ImportExecutor 单元测试")
class ImportExecutorTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SampleRow {
        @NotBlank(message = "名称不能为空")
        private String name;
    }

    @Test
    @DisplayName("校验通过时全部入库")
    void should_import_all_when_validation_passes() {
        var data = List.of(new SampleRow("张三"), new SampleRow("李四"), new SampleRow("王五"));
        var saved = new ArrayList<SampleRow>();

        var result =
                ImportExecutor.<SampleRow>builder()
                        .validator(validator)
                        .data(data)
                        .consumer(saved::addAll)
                        .build()
                        .execute();

        assertThat(result.successCount()).isEqualTo(3);
        assertThat(result.failureCount()).isEqualTo(0);
        assertThat(saved).hasSize(3);
    }

    @Test
    @DisplayName("校验失败时不入库，返回错误信息")
    void should_not_import_when_validation_fails() {
        var data = List.of(new SampleRow("张三"), new SampleRow(""), new SampleRow(null));
        var saved = new ArrayList<SampleRow>();

        var result =
                ImportExecutor.<SampleRow>builder()
                        .validator(validator)
                        .data(data)
                        .consumer(saved::addAll)
                        .build()
                        .execute();

        assertThat(result.successCount()).isEqualTo(0);
        assertThat(result.failureCount()).isEqualTo(2);
        assertThat(result.failureMessages()).allMatch(msg -> msg.contains("名称不能为空"));
        assertThat(saved).isEmpty();
    }

    @Test
    @DisplayName("重复检测失败时不入库")
    void should_not_import_when_duplicate_detected() {
        var data = List.of(new SampleRow("张三"), new SampleRow("李四"));
        var saved = new ArrayList<SampleRow>();

        var result =
                ImportExecutor.<SampleRow>builder()
                        .data(data)
                        .duplicateChecker(row -> "张三".equals(row.getName()) ? "名称已存在" : null)
                        .consumer(saved::addAll)
                        .build()
                        .execute();

        assertThat(result.successCount()).isEqualTo(0);
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.failureMessages().get(0)).contains("名称已存在");
        assertThat(saved).isEmpty();
    }

    @Test
    @DisplayName("分批入库：batchSize=2 时分两批")
    void should_import_in_batches() {
        var data = List.of(new SampleRow("A"), new SampleRow("B"), new SampleRow("C"));
        var batches = new ArrayList<List<SampleRow>>();

        var result =
                ImportExecutor.<SampleRow>builder()
                        .data(data)
                        .consumer(batch -> batches.add(new ArrayList<>(batch)))
                        .batchSize(2)
                        .build()
                        .execute();

        assertThat(result.successCount()).isEqualTo(3);
        assertThat(batches).hasSize(2);
        assertThat(batches.get(0)).hasSize(2);
        assertThat(batches.get(1)).hasSize(1);
    }

    @Test
    @DisplayName("错误超过 50 条时截断显示")
    void should_truncate_errors_at_50() {
        var data = IntStream.range(0, 60).mapToObj(i -> new SampleRow(null)).toList();
        var saved = new ArrayList<SampleRow>();

        var result =
                ImportExecutor.<SampleRow>builder()
                        .validator(validator)
                        .data(data)
                        .consumer(saved::addAll)
                        .build()
                        .execute();

        assertThat(result.failureCount()).isEqualTo(60);
        assertThat(result.failureMessages()).hasSize(50);
        assertThat(saved).isEmpty();
    }

    @Test
    @DisplayName("data 为空时抛异常")
    void should_throw_when_data_is_null() {
        assertThatThrownBy(() -> ImportExecutor.<SampleRow>builder().consumer(batch -> {}).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("data");
    }

    @Test
    @DisplayName("consumer 为空时抛异常")
    void should_throw_when_consumer_is_null() {
        assertThatThrownBy(() -> ImportExecutor.<SampleRow>builder().data(List.of()).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("consumer");
    }
}

package com.xuejiai.aaf.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import jakarta.validation.Validator;

/**
 * 通用导入执行器。校验 → 分批入库，其他实体复用。
 *
 * <p>使用示例：
 * <pre>{@code
 * var result = ImportExecutor.<UserImportVO>builder()
 *     .validator(validator)
 *     .data(list)
 *     .duplicateChecker(row -> userRepo.existsByUsername(row.getUsername()) ? "用户名已存在" : null)
 *     .consumer(batch -> batch.forEach(row -> userRepo.save(toEntity(row))))
 *     .build()
 *     .execute();
 * }</pre>
 */
public class ImportExecutor<T> {

    private static final int DEFAULT_BATCH_SIZE = 1000;
    private static final int MAX_ERROR_DISPLAY = 50;

    private final Validator validator;
    private final List<T> data;
    private final Function<T, String> duplicateChecker;
    private final Consumer<List<T>> consumer;
    private final int batchSize;

    private ImportExecutor(Builder<T> builder) {
        this.validator = builder.validator;
        this.data = builder.data;
        this.duplicateChecker = builder.duplicateChecker;
        this.consumer = builder.consumer;
        this.batchSize = builder.batchSize;
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /** 执行导入：先全部校验，通过后分批入库。 */
    public ImportResult execute() {
        var errors = validate();
        if (!errors.isEmpty()) {
            var display = errors.size() > MAX_ERROR_DISPLAY
                    ? errors.subList(0, MAX_ERROR_DISPLAY) : errors;
            return new ImportResult(0, errors.size(), display);
        }
        // 分批入库
        for (int i = 0; i < data.size(); i += batchSize) {
            int end = Math.min(i + batchSize, data.size());
            consumer.accept(data.subList(i, end));
        }
        return new ImportResult(data.size(), 0, List.of());
    }

    private List<String> validate() {
        var errors = new ArrayList<String>();
        for (int i = 0; i < data.size(); i++) {
            var row = data.get(i);
            int rowNum = i + 2; // Excel 行号（跳过表头）
            // Bean Validation
            if (validator != null) {
                var violations = validator.validate(row);
                if (!violations.isEmpty()) {
                    errors.add("第 " + rowNum + " 行：" + violations.iterator().next().getMessage());
                    continue;
                }
            }
            // 业务校验（如重复检测）
            if (duplicateChecker != null) {
                String error = duplicateChecker.apply(row);
                if (error != null) {
                    errors.add("第 " + rowNum + " 行：" + error);
                }
            }
        }
        return errors;
    }

    /** 导入结果。 */
    public record ImportResult(int successCount, int failureCount, List<String> failureMessages) {}

    public static class Builder<T> {
        private Validator validator;
        private List<T> data;
        private Function<T, String> duplicateChecker;
        private Consumer<List<T>> consumer;
        private int batchSize = DEFAULT_BATCH_SIZE;

        /** Bean Validation 校验器。 */
        public Builder<T> validator(Validator validator) {
            this.validator = validator;
            return this;
        }

        /** 待导入数据。 */
        public Builder<T> data(List<T> data) {
            this.data = data;
            return this;
        }

        /** 重复检测，返回 null 表示通过，返回错误信息表示失败。 */
        public Builder<T> duplicateChecker(Function<T, String> duplicateChecker) {
            this.duplicateChecker = duplicateChecker;
            return this;
        }

        /** 批量入库逻辑。 */
        public Builder<T> consumer(Consumer<List<T>> consumer) {
            this.consumer = consumer;
            return this;
        }

        /** 分批大小，默认 1000。 */
        public Builder<T> batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public ImportExecutor<T> build() {
            if (data == null) throw new IllegalArgumentException("data 不能为空");
            if (consumer == null) throw new IllegalArgumentException("consumer 不能为空");
            return new ImportExecutor<>(this);
        }
    }
}

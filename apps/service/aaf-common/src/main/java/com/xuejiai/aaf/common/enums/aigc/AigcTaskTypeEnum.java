package com.xuejiai.aaf.common.enums.aigc;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** AIGC 任务类型枚举，对应字典 aigc_task_type。与 AigcTaskController#submit 分支一致。 */
@Getter
@AllArgsConstructor
public enum AigcTaskTypeEnum implements ArrayValuable<String> {
    IMAGE("IMAGE", "图像"),
    VIDEO("VIDEO", "视频"),
    MODEL_3D("MODEL_3D", "3D 模型"),
    MUSIC("MUSIC", "音乐"),
    VOICE("VOICE", "配音"),
    IMAGE_PROCESS("IMAGE_PROCESS", "图像处理");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(AigcTaskTypeEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }

    /**
     * 按 code 解析任务类型，忽略大小写。
     *
     * <p>aaf-common 禁止依赖 aaf-api，故统一抛 {@link IllegalArgumentException}；调用方（业务模块） 捕获后按需转换为 {@code
     * BusinessException} 等用户友好的业务异常。
     *
     * @param code 任务类型编码，如 {@code image}/{@code IMAGE}
     * @return 对应枚举值
     * @throws IllegalArgumentException code 为空，或不在取值范围内
     */
    public static AigcTaskTypeEnum fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("任务类型编码不能为空");
        }
        try {
            return valueOf(code.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("不支持的任务类型: " + code, e);
        }
    }
}

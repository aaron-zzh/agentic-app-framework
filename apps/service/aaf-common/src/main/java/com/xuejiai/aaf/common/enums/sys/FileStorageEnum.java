package com.xuejiai.aaf.common.enums.sys;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 文件存储类型枚举，对应字典 sys_file_storage，值与 StorageProperties.StorageType 一致。 */
@Getter
@AllArgsConstructor
public enum FileStorageEnum implements ArrayValuable<String> {
    LOCAL("LOCAL", "本地存储"),
    S3("S3", "S3 兼容（MinIO / 阿里云 OSS / AWS S3）");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(FileStorageEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}

package com.xuejiai.aaf.common.enums.sys;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 文件存储类型枚举，对应字典 sys_file_storage，值与 StorageProperties.StorageType 一致。 */
@Getter
@AllArgsConstructor
public enum FileStorageEnum {
    LOCAL("LOCAL", "本地存储"),
    S3("S3", "S3 兼容（MinIO / 阿里云 OSS / AWS S3）");

    private final String code;
    private final String label;
}

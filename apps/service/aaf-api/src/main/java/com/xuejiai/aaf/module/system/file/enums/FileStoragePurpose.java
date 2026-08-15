package com.xuejiai.aaf.module.system.file.enums;

/** 文件上传目标用途，由服务端解析为受控存储配置。 */
public enum FileStoragePurpose {
    /** 当前全局主存储。 */
    MASTER,

    /** 唯一启用且允许公开访问的 OSS 资产存储。 */
    PUBLIC_ASSET
}

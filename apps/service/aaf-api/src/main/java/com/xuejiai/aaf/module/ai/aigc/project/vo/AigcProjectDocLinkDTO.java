package com.xuejiai.aaf.module.ai.aigc.project.vo;

import jakarta.validation.constraints.NotNull;

/** 关联文档到项目请求。 */
public record AigcProjectDocLinkDTO(
        @NotNull Long docId,
        /** spec=创作规范 / ref=参考资料 / output=产出文档 */
        String role) {}

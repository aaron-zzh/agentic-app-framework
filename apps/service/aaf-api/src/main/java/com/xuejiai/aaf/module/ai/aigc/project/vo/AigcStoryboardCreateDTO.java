package com.xuejiai.aaf.module.ai.aigc.project.vo;

import jakarta.validation.constraints.NotNull;

public record AigcStoryboardCreateDTO(@NotNull Long projectId, String title, Long docId) {}

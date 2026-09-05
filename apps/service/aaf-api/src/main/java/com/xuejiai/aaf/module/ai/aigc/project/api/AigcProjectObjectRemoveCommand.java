package com.xuejiai.aaf.module.ai.aigc.project.api;

public record AigcProjectObjectRemoveCommand(
        Long projectId, Long objectId, Integer expectedProjectVersion, String reason) {}

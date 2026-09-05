package com.xuejiai.aaf.module.ai.aigc.project.api;

public record AigcProjectObjectContractCommand(
        Long projectId, Long objectId, String contractRole, Integer expectedProjectVersion) {}

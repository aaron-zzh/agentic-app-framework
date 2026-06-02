package com.xuejiai.aaf.module.system.license.vo;

import java.util.List;

/** 官方服务控制台摘要。 */
public record OfficialConsoleSummaryVO(
        String ownerUserId, String tier, List<String> enabledModules) {}

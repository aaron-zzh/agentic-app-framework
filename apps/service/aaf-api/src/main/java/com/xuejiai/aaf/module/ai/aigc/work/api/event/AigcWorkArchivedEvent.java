package com.xuejiai.aaf.module.ai.aigc.work.api.event;

import java.time.Instant;
import java.util.UUID;

public record AigcWorkArchivedEvent(
        UUID eventId, Long workId, Long projectId, Instant occurredAt) {}

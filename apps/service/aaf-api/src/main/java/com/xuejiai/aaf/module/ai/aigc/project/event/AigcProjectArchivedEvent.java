package com.xuejiai.aaf.module.ai.aigc.project.event;

import java.time.Instant;
import java.util.UUID;

public record AigcProjectArchivedEvent(UUID eventId, Long projectId, Instant occurredAt) {}

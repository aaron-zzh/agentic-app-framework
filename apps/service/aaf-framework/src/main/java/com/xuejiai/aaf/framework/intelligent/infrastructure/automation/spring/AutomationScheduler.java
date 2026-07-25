package com.xuejiai.aaf.framework.intelligent.infrastructure.automation.spring;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import com.xuejiai.aaf.framework.intelligent.automation.application.AutomationApplicationService;
import com.xuejiai.aaf.framework.intelligent.automation.model.AutomationDefinition;
import com.xuejiai.aaf.framework.intelligent.infrastructure.automation.persistence.AutomationDefinitionRepository;

/** Spring scheduler 负责长期确定性触发，AgentScope 不参与长期调度。 */
public final class AutomationScheduler {
    private final AutomationApplicationService application;
    private final AutomationDefinitionRepository definitions;
    private final Clock clock;
    public AutomationScheduler(AutomationApplicationService application, AutomationDefinitionRepository definitions, Clock clock) {
        this.application = application; this.definitions = definitions; this.clock = clock;
    }

    @Scheduled(fixedDelay = 1000L)
    public void tick() {
        var now = clock.instant();
        definitions.findAll().stream().map(entity -> entity.getDefinition()).filter(AutomationDefinition::enabled)
                .filter(definition -> due(definition, now)).forEach(definition -> application.trigger(
                        definition.tenantId(), definition.automationId(), triggerKey(definition, now), Map.of(), "SYSTEM/scheduler"));
        application.dispatchPending(50);
    }

    private static boolean due(AutomationDefinition definition, Instant now) {
        return switch (definition.trigger().type()) {
            case MANUAL -> false;
            case FREQUENCY -> now.getEpochSecond() % definition.trigger().frequency().toSeconds() == 0;
            case CRON -> {
                var expression = CronExpression.parse(definition.trigger().cron());
                var previousMinute = ZonedDateTime.ofInstant(now.minusSeconds(60), ZoneOffset.UTC);
                var next = expression.next(previousMinute);
                yield next != null && !next.toInstant().isAfter(now);
            }
        };
    }

    private static String triggerKey(AutomationDefinition definition, Instant now) {
        return switch (definition.trigger().type()) {
            case MANUAL -> "manual:" + now.toEpochMilli();
            case FREQUENCY -> "frequency:" + now.getEpochSecond() / definition.trigger().frequency().toSeconds();
            case CRON -> "cron:" + now.getEpochSecond() / 60;
        };
    }
}

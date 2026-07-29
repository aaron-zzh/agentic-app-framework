package com.xuejiai.aaf.framework.intelligent.infrastructure.automation.spring;

import java.time.Clock;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Bean;

import com.xuejiai.aaf.framework.intelligent.assistant.application.DelegatedTaskCoordinator;
import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskBoardPort;
import com.xuejiai.aaf.framework.intelligent.automation.application.AutomationApplicationService;
import com.xuejiai.aaf.framework.intelligent.automation.application.DefinitionLifecycleService;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.spring.AssistantInfrastructureAutoConfiguration;
import com.xuejiai.aaf.framework.intelligent.infrastructure.automation.persistence.*;

/** P5 非 Team 自动化生产装配。 */
@AutoConfiguration
@AutoConfigureAfter(AssistantInfrastructureAutoConfiguration.class)
public class AutomationInfrastructureAutoConfiguration {
    @Bean
    JpaAutomationStore automationStore(
            AutomationDefinitionRepository definitions,
            AutomationRunRepository runs,
            AutomationPolicyRepository policies,
            DefinitionLifecycleRepository lifecycles,
            AutomationAuditRepository audits) {
        return new JpaAutomationStore(definitions, runs, policies, lifecycles, audits);
    }

    @Bean
    AutomationDelegatedDispatchAdapter automationDispatchAdapter(
            DelegatedTaskCoordinator coordinator) {
        return new AutomationDelegatedDispatchAdapter(coordinator, Clock.systemUTC());
    }

    @Bean
    AutomationApplicationService automationApplicationService(
            JpaAutomationStore store,
            AutomationDelegatedDispatchAdapter dispatcher,
            DelegatedTaskPort delegatedTasks,
            TaskBoardPort taskBoards) {
        return new AutomationApplicationService(
                store,
                store,
                store,
                store,
                dispatcher,
                delegatedTasks,
                taskBoards,
                Clock.systemUTC());
    }

    @Bean
    DefinitionLifecycleService definitionLifecycleService(JpaAutomationStore store) {
        return new DefinitionLifecycleService(store, Clock.systemUTC());
    }

    @Bean
    AutomationScheduler automationScheduler(
            AutomationApplicationService application, AutomationDefinitionRepository definitions) {
        return new AutomationScheduler(application, definitions, Clock.systemUTC());
    }
}

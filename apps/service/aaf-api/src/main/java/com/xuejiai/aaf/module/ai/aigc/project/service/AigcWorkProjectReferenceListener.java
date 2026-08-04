package com.xuejiai.aaf.module.ai.aigc.project.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.xuejiai.aaf.module.ai.aigc.work.api.event.AigcWorkArchivedEvent;
import com.xuejiai.aaf.module.ai.aigc.work.api.event.AigcWorkCollectedEvent;
import com.xuejiai.aaf.module.ai.aigc.work.api.event.AigcWorkPublicationChangedEvent;

import lombok.RequiredArgsConstructor;

/** Work 反向状态通过事件进入 Project，不建立 project → work 同步依赖。 */
@Component
@RequiredArgsConstructor
public class AigcWorkProjectReferenceListener {

    private final AigcProjectService projectService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onWorkCollected(AigcWorkCollectedEvent event) {
        projectService.registerExternalResource(
                event.projectId(), "work", String.valueOf(event.workId()), "deliverable");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPublicationChanged(AigcWorkPublicationChangedEvent event) {
        projectService.registerExternalResource(
                event.projectId(),
                "publication",
                String.valueOf(event.publicationId()),
                event.status());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onWorkArchived(AigcWorkArchivedEvent event) {
        projectService.registerExternalResource(
                event.projectId(), "work", String.valueOf(event.workId()), "archived");
    }
}

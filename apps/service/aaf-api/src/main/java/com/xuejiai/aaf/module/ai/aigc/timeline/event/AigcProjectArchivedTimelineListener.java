package com.xuejiai.aaf.module.ai.aigc.timeline.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.xuejiai.aaf.module.ai.aigc.project.event.AigcProjectArchivedEvent;
import com.xuejiai.aaf.module.ai.aigc.timeline.service.AigcTimelineService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AigcProjectArchivedTimelineListener {

    private final AigcTimelineService timelineService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onProjectArchived(AigcProjectArchivedEvent event) {
        timelineService.archiveProjectTimelines(event.projectId());
    }
}

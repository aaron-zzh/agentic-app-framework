package com.xuejiai.aaf.module.ai.aigc.timeline.api;

import java.util.List;

public interface AigcTimelineApi {

    AigcTimelineView create(AigcTimelineCreateCommand command);

    AigcTimelineView replaceComposition(AigcTimelineReplaceCommand command);

    AigcTimelineView requireTimeline(Long timelineId);

    List<AigcStoryboardExportView> storyboardExports(Long timelineId);
}

package com.xuejiai.aaf.framework.intelligent.agent.trace;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.cognition.learning.TrajectoryCollector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * TrajectoryCollector 实现——采集执行轨迹并发布事件触发异步持久化。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublishingTrajectoryCollector implements TrajectoryCollector {

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void collect(Trajectory trajectory) {
        var steps = trajectory.toolCalls().stream()
                .map(tc -> new ExecutionCompletedEvent.StepRecord(
                        trajectory.toolCalls().indexOf(tc),
                        null,
                        StepType.TOOL_CALL,
                        null,
                        tc.toolName(),
                        tc.arguments(),
                        tc.result(),
                        ExecutionStatus.SUCCESS,
                        null,
                        null,
                        null))
                .toList();

        var event = new ExecutionCompletedEvent(
                trajectory.executionId(),
                null,
                trajectory.agentId(),
                null,
                trajectory.userId(),
                null,
                trajectory.input(),
                trajectory.output(),
                trajectory.success() ? ExecutionStatus.SUCCESS : ExecutionStatus.FAILED,
                null,
                0, 0,
                Instant.now().minusMillis(trajectory.durationMs()),
                Instant.now(),
                0,
                steps,
                Map.of());

        eventPublisher.publishEvent(event);
        log.debug("执行轨迹已发布 [{}] success={}", trajectory.executionId(), trajectory.success());
    }
}

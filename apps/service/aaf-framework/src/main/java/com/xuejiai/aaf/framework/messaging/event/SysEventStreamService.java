package com.xuejiai.aaf.framework.messaging.event;

import java.io.IOException;
import java.util.Objects;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.framework.messaging.sse.SseConnection;
import com.xuejiai.aaf.framework.messaging.sse.SseSessionManager;
import com.xuejiai.aaf.framework.org.OrgIgnore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 全局事件通道的订阅、游标重放与补发。DB 为真理源，{@link SseSessionManager} 只负责连接传输。
 *
 * <p>每条连接的重放、补发与游标推进都在连接发送锁内完成，因此同一连接不会重复或乱序投递；连接先登记再重放，重放期间到达的广播会等待锁后从游标继续。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysEventStreamService {

    /** SSE 命名事件名，业务类型只在信封 type 字段中。 */
    static final String WIRE_EVENT = "event";

    private static final int REPLAY_LIMIT = 500;

    private final SysEventRepository repository;
    private final SseSessionManager sessionManager;

    /** 订阅：按 {@code afterId} 重放后挂入 live 连接。 */
    public SseEmitter subscribe(SubscriptionScope scope, long afterId) {
        return subscribe(scope, afterId, null);
    }

    SseEmitter subscribe(SubscriptionScope scope, long afterId, SseEmitter emitter) {
        var connection =
                emitter == null
                        ? sessionManager.open(scope.ownerId(), scope.orgId(), scope.workspaceId())
                        : sessionManager.open(
                                scope.ownerId(), scope.orgId(), scope.workspaceId(), emitter);
        connection.lock();
        try {
            var upperBound = upperBound(connection);
            connection.cursor(Math.min(afterId, upperBound));
            catchUp(connection, upperBound);
            connection.comment("connected");
        } catch (IOException | RuntimeException error) {
            sessionManager.remove(connection);
            connection.fail(error);
        } finally {
            connection.unlock();
        }
        return connection.emitter();
    }

    /** 事件提交后向该用户所有匹配连接广播（实质为按游标补发）。 */
    void broadcast(SysEvent event) {
        for (var connection : sessionManager.connections(event.getOwnerId())) {
            catchUpSafely(connection);
        }
    }

    /** 兜底补发：多实例下其他实例提交的事件，最迟一个心跳周期内送达。 */
    @OrgIgnore
    @Scheduled(fixedDelay = 15_000)
    public void catchUpAll() {
        for (var ownerId : sessionManager.ownerIds()) {
            for (var connection : sessionManager.connections(ownerId)) {
                catchUpSafely(connection);
            }
        }
    }

    private void catchUpSafely(SseConnection connection) {
        connection.lock();
        try {
            catchUp(connection, upperBound(connection));
        } catch (IOException error) {
            sessionManager.remove(connection);
            connection.fail(error);
        } catch (RuntimeException error) {
            // 单个连接异常（如 DB 查询失败）断开即可，由前端按游标重连，不得影响其他连接
            log.error("事件补发失败，已断开连接: owner={}", connection.ownerId(), error);
            sessionManager.remove(connection);
            connection.fail(error);
        } finally {
            connection.unlock();
        }
    }

    private long upperBound(SseConnection connection) {
        return repository.replayUpperBound(
                connection.ownerId(), connection.orgId(), connection.workspaceId());
    }

    /** 将连接游标推进到 {@code upperBound}，调用方须持有连接发送锁。 */
    private void catchUp(SseConnection connection, long upperBound) throws IOException {
        while (connection.cursor() < upperBound) {
            var events =
                    repository.replay(
                            connection.ownerId(),
                            connection.orgId(),
                            connection.workspaceId(),
                            connection.cursor(),
                            upperBound,
                            PageRequest.of(0, REPLAY_LIMIT));
            if (events.isEmpty()) {
                return;
            }
            for (var event : events) {
                if (event.getId() <= connection.cursor()) {
                    continue;
                }
                send(connection, event);
                connection.cursor(event.getId());
            }
            if (events.size() < REPLAY_LIMIT) {
                return;
            }
        }
    }

    /** 发送单条事件。事件本身无法构造信封时记录 error 并跳过，调用方照常推进游标。 */
    private void send(SseConnection connection, SysEvent event) throws IOException {
        EventEnvelope envelope;
        try {
            envelope = EventEnvelope.from(event);
        } catch (RuntimeException error) {
            log.error(
                    "事件构造信封失败，已跳过: eventId={}, type={}",
                    event.getId(),
                    event.getEventType(),
                    error);
            return;
        }
        connection.send(Objects.requireNonNull(event.getId()), WIRE_EVENT, envelope);
    }
}

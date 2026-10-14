package com.xuejiai.aaf.framework.messaging.sse;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.framework.org.OrgIgnore;

import lombok.extern.slf4j.Slf4j;

/**
 * SSE 传输层——按 ownerId 维护长连接，每条连接带订阅范围（组织、工作区）。
 *
 * <p>只负责连接生命周期与心跳保活，不依赖数据库；事件落库、游标补发与广播由 {@code SysEventStreamService} 完成，业务模块不得直接使用本类。
 *
 * <p>心跳为 SSE 注释帧，不产生客户端事件。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Component
public class SseSessionManager {

    /** 永不超时，由心跳保活 */
    private static final long SSE_TIMEOUT = -1L;

    /** ownerId → 连接集合（一用户多 tab / 多范围） */
    private final Map<Long, Set<SseConnection>> connections = new ConcurrentHashMap<>();

    /** 建立并登记一条连接，连接结束（完成、超时、出错）时自动移除。 */
    public SseConnection open(Long ownerId, Long orgId, Long workspaceId) {
        return open(ownerId, orgId, workspaceId, new SseEmitter(SSE_TIMEOUT));
    }

    /** 使用调用方提供的 emitter 建立连接（便于单测注入）。 */
    public SseConnection open(Long ownerId, Long orgId, Long workspaceId, SseEmitter emitter) {
        var connection = new SseConnection(ownerId, orgId, workspaceId, emitter);
        connections.computeIfAbsent(ownerId, k -> new CopyOnWriteArraySet<>()).add(connection);
        emitter.onCompletion(() -> remove(connection));
        emitter.onTimeout(() -> remove(connection));
        emitter.onError(e -> remove(connection));
        log.debug("用户 {} SSE 已连接，当前连接数: {}", ownerId, connections(ownerId).size());
        return connection;
    }

    /** 用户当前所有活跃连接。 */
    public List<SseConnection> connections(Long ownerId) {
        var set = connections.get(ownerId);
        return set == null ? List.of() : List.copyOf(set);
    }

    /** 当前存在连接的用户。 */
    public List<Long> ownerIds() {
        return List.copyOf(connections.keySet());
    }

    /** 当前在线用户数 */
    public int onlineCount() {
        return connections.size();
    }

    /** 移除连接（幂等）。 */
    public void remove(SseConnection connection) {
        var set = connections.get(connection.ownerId());
        if (set == null) {
            return;
        }
        set.remove(connection);
        if (set.isEmpty()) {
            connections.remove(connection.ownerId(), set);
        }
    }

    /** 每 15 秒向所有活跃连接发送注释帧，防止 CDN/代理 30 秒无数据断连 */
    @OrgIgnore
    @Scheduled(fixedDelay = 15_000)
    public void heartbeat() {
        connections.values().forEach(set -> set.forEach(this::heartbeat));
    }

    private void heartbeat(SseConnection connection) {
        try {
            connection.comment("heartbeat");
        } catch (Exception e) {
            remove(connection);
            connection.fail(e);
        }
    }
}

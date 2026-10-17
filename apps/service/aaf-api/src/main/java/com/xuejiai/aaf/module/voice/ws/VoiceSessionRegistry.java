package com.xuejiai.aaf.module.voice.ws;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.module.voice.session.VoiceSession;

/** 在线会话注册表：同一客户端（设备/用户）同时只允许一条会话，新连接顶替旧连接。 */
@Component
public class VoiceSessionRegistry {

    private final Map<String, VoiceSession> sessions = new ConcurrentHashMap<>();

    /** 登记新会话；已有同键会话时先通知其 {@code session_close(replaced)} 并关闭。 */
    public void register(VoiceSession session) {
        var previous = sessions.put(session.clientKey(), session);
        if (previous != null && previous != session) {
            previous.closeBy("replaced");
        }
    }

    /** 仅当登记的仍是该会话时才移除，避免新会话被旧会话的关闭事件误删。 */
    public void unregister(VoiceSession session) {
        sessions.remove(session.clientKey(), session);
    }

    /** 对全部在线会话执行空闲检查。 */
    public void checkIdle() {
        sessions.values().forEach(VoiceSession::checkIdle);
    }

    /** 关闭全部会话，用于服务关闭。 */
    public void closeAll(String reason) {
        sessions.values().forEach(session -> session.closeBy(reason));
    }

    public int size() {
        return sessions.size();
    }
}

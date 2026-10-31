package com.xuejiai.aaf.module.ai.aigc.realtime;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.ai.realtime.RealtimeConversationEngine;
import com.xuejiai.aaf.module.ai.aigc.realtime.context.RealtimeConversationContextResolver;
import com.xuejiai.aaf.module.ai.aigc.realtime.service.RealtimeKnowledgeSearchService;
import com.xuejiai.aaf.module.device.api.DeviceRealtimeContextClient;

/** 统一创建并初始化 Omni Realtime v1 会话。 */
@Service
public class DefaultRealtimeConversationSessionFactory
        implements RealtimeConversationSessionFactory {

    private final ObjectProvider<RealtimeConversationEngine> engineProvider;
    private final List<RealtimeConversationContextResolver> contextResolvers;
    private final RealtimeKnowledgeSearchService knowledgeSearchService;
    private final DeviceRealtimeContextClient deviceContextClient;

    public DefaultRealtimeConversationSessionFactory(
            ObjectProvider<RealtimeConversationEngine> engineProvider,
            List<RealtimeConversationContextResolver> contextResolvers,
            RealtimeKnowledgeSearchService knowledgeSearchService,
            DeviceRealtimeContextClient deviceContextClient) {
        this.engineProvider = engineProvider;
        this.contextResolvers = List.copyOf(contextResolvers);
        this.knowledgeSearchService = knowledgeSearchService;
        this.deviceContextClient = deviceContextClient;
    }

    @Override
    public RealtimeConversationSession open(
            RealtimeCaller caller, RealtimeHello hello, RealtimeSessionTransport transport) {
        validateHello(caller, hello);
        var resolver =
                contextResolvers.stream()
                        .filter(item -> item.supports(caller))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("实时会话上下文解析器不存在"));
        var contextResolveStartedAt = System.nanoTime();
        var context = resolver.resolve(caller, hello);
        var contextResolveMs = elapsedMillis(contextResolveStartedAt);
        var engine = engineProvider.getIfAvailable();
        if (engine == null) {
            throw new IllegalStateException("实时对话服务未启用");
        }
        var knowledgeEnabled =
                !context.assistantContext().knowledgeBaseIds().isEmpty()
                        && (!(caller instanceof RealtimeCaller.Web) || hello.knowledgeEnabled());
        var session =
                new DefaultRealtimeConversationSession(
                        context,
                        contextResolveMs,
                        knowledgeEnabled,
                        transport,
                        engine,
                        knowledgeSearchService,
                        deviceContextClient);
        session.initialize();
        return session;
    }

    private static void validateHello(RealtimeCaller caller, RealtimeHello hello) {
        if (hello == null
                || !"omni-realtime.v1".equals(hello.protocol())
                || (!"server".equals(hello.endpointing()) && !"client".equals(hello.endpointing()))
                || (caller instanceof RealtimeCaller.Device && hello.knowledge() != null)) {
            throw new IllegalArgumentException("hello 协议或 endpointing 无效");
        }
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}

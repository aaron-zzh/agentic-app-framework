package com.xuejiai.aaf.module.ai.aigc.realtime.context;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.module.ai.aigc.realtime.RealtimeCaller;
import com.xuejiai.aaf.module.ai.aigc.realtime.RealtimeHello;
import com.xuejiai.aaf.module.ai.assistant.api.AssistantConversationContextApi;
import com.xuejiai.aaf.module.ai.assistant.api.AssistantConversationContextApi.ConversationIdentity;
import com.xuejiai.aaf.module.system.org.api.SystemOrgContextApi;

/** 浏览器 JWT 的默认 Assistant 实时会话上下文。 */
@Service
public class WebRealtimeConversationContextResolver implements RealtimeConversationContextResolver {

    private final SystemOrgContextApi systemOrgContextApi;
    private final AssistantConversationContextApi assistantContextApi;

    public WebRealtimeConversationContextResolver(
            SystemOrgContextApi systemOrgContextApi,
            AssistantConversationContextApi assistantContextApi) {
        this.systemOrgContextApi = systemOrgContextApi;
        this.assistantContextApi = assistantContextApi;
    }

    @Override
    public boolean supports(RealtimeCaller caller) {
        return caller instanceof RealtimeCaller.Web;
    }

    @Override
    public ResolvedRealtimeContext resolve(RealtimeCaller caller, RealtimeHello hello) {
        if (!(caller instanceof RealtimeCaller.Web webCaller)) {
            throw new IllegalArgumentException("浏览器实时调用者无效");
        }
        if (!"server".equals(hello.endpointing())) {
            throw new IllegalArgumentException("浏览器 endpointing 必须为 server");
        }
        var defaultContext = systemOrgContextApi.resolvePersonalDefault(webCaller.userId());
        var identity =
                new ConversationIdentity(
                        webCaller.userId(),
                        webCaller.userId(),
                        defaultContext.orgId(),
                        defaultContext.workspaceId());
        var context = assistantContextApi.resolve(identity, hello.roleKey());
        return new ResolvedRealtimeContext(identity, context, "server", null);
    }
}

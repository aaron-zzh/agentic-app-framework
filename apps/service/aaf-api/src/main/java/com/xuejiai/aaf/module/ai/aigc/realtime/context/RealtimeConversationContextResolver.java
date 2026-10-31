package com.xuejiai.aaf.module.ai.aigc.realtime.context;

import com.xuejiai.aaf.module.ai.aigc.realtime.RealtimeCaller;
import com.xuejiai.aaf.module.ai.aigc.realtime.RealtimeHello;

/** 将可信调用者解析为一次会话内不可变的 Assistant 上下文。 */
public interface RealtimeConversationContextResolver {

    boolean supports(RealtimeCaller caller);

    ResolvedRealtimeContext resolve(RealtimeCaller caller, RealtimeHello hello);
}

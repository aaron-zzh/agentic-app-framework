package com.xuejiai.aaf.framework.intelligent.cognition.port;

import com.xuejiai.aaf.framework.intelligent.cognition.model.ContextRequest;
import com.xuejiai.aaf.framework.intelligent.cognition.model.ControlledContextSnapshot;

/** L1 Cognition 对 L2 Agent 暴露的受控上下文装配边界。 */
public interface L1ContextPort {

    ControlledContextSnapshot resolve(ContextRequest request);
}

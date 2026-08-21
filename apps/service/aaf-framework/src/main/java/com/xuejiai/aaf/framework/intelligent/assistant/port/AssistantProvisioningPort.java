package com.xuejiai.aaf.framework.intelligent.assistant.port;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/** 为用户提供默认 Assistant 副本的唯一写入边界。 */
public interface AssistantProvisioningPort {

    /** 幂等创建用户默认 Assistant；已存在时不修改现有定义。 */
    void provisionDefaultForUser(UserId userId);
}

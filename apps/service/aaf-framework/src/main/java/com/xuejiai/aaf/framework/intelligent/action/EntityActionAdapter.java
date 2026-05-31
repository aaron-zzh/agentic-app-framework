package com.xuejiai.aaf.framework.intelligent.action;

import java.util.List;
import java.util.Map;

/** 单个实体暴露给 AI 业务动作网关的适配器。 */
public interface EntityActionAdapter {

    /** 实体标识。 */
    String entitySlug();

    /** 实体名称。 */
    String entityName();

    /** 支持的动作。 */
    List<String> supportedActions();

    /** 动作对应权限码。 */
    String permissionCode(AiBusinessActionType action);

    /** 执行动作。 */
    Object execute(AiBusinessActionType action, Map<String, Object> params);
}

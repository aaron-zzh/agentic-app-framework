package com.xuejiai.aaf.module.ai.aigc.execution.api;

import java.util.Optional;

/** 执行绑定的只读查询入口；configuration 等其他子模块通过它读取绑定，不直接访问 execution 的 Entity 或 Repository。 */
public interface AigcExecutionBindingApi {

    /**
     * 查询动作当前生效的执行绑定版本。
     *
     * @param actionKey 动作键
     * @return 该动作最高优先级的已发布执行绑定版本 ID；没有已发布绑定时为空
     */
    Optional<Long> findPublishedBindingVersionId(String actionKey);
}

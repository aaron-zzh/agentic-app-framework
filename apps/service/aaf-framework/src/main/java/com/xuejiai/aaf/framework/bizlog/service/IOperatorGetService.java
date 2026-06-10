package com.xuejiai.aaf.framework.bizlog.service;

import com.xuejiai.aaf.framework.bizlog.beans.Operator;

/**
 * 获取当前操作人接口。
 *
 * <p>业务方实现此接口并注册为 Spring Bean，框架会自动调用获取操作人。 默认实现为 {@link
 * com.xuejiai.aaf.framework.bizlog.service.impl.DefaultOperatorGetServiceImpl}。
 */
public interface IOperatorGetService {

    /**
     * 获取当前操作人，通常从 ThreadLocal / SecurityContext 中取。
     *
     * @return 操作人信息，operatorId 不能为空
     */
    Operator getUser();
}

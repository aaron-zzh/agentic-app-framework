package com.xuejiai.aaf.framework.bizlog.service.impl;

import com.xuejiai.aaf.framework.bizlog.beans.Operator;
import com.xuejiai.aaf.framework.bizlog.service.IOperatorGetService;

/**
 * IOperatorGetService 默认实现（占位用）。
 *
 * <p>业务方应覆写此 Bean，从 SecurityContext / OperatorContext 中获取真实操作人。 AAF 对接实现见 {@code
 * AafOperatorGetService}（在 aaf-api 模块）。
 */
public class DefaultOperatorGetServiceImpl implements IOperatorGetService {

    @Override
    public Operator getUser() {
        return new Operator("SYSTEM");
    }
}

package com.xuejiai.aaf.framework.bizlog.service.impl;

import com.xuejiai.aaf.framework.bizlog.service.IFunctionService;
import com.xuejiai.aaf.framework.bizlog.service.IParseFunction;

/** IFunctionService 默认实现，委托给 ParseFunctionFactory。 */
public class DefaultFunctionServiceImpl implements IFunctionService {

    private final ParseFunctionFactory parseFunctionFactory;

    public DefaultFunctionServiceImpl(ParseFunctionFactory parseFunctionFactory) {
        this.parseFunctionFactory = parseFunctionFactory;
    }

    @Override
    public String apply(String functionName, Object value) {
        IParseFunction function = parseFunctionFactory.getFunction(functionName);
        if (function == null) {
            return value == null ? "" : value.toString();
        }
        return function.apply(value);
    }

    @Override
    public boolean beforeFunction(String functionName) {
        return parseFunctionFactory.isBeforeFunction(functionName);
    }
}

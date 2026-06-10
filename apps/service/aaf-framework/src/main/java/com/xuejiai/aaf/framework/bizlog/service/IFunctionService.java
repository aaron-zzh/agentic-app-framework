package com.xuejiai.aaf.framework.bizlog.service;

/** 函数执行服务，由 ParseFunctionFactory 代理所有注册的 IParseFunction。 */
public interface IFunctionService {

    String apply(String functionName, Object value);

    boolean beforeFunction(String functionName);
}

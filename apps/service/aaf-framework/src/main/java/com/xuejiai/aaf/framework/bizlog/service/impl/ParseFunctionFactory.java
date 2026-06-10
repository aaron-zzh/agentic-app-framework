package com.xuejiai.aaf.framework.bizlog.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.xuejiai.aaf.framework.bizlog.service.IParseFunction;

/** 持有所有注册的 IParseFunction，按函数名查找。 */
public class ParseFunctionFactory {

    private final Map<String, IParseFunction> allFunctionMap;

    public ParseFunctionFactory(List<IParseFunction> parseFunctions) {
        allFunctionMap = new HashMap<>();
        if (CollectionUtils.isEmpty(parseFunctions)) return;
        for (IParseFunction fn : parseFunctions) {
            if (StringUtils.hasText(fn.functionName())) {
                allFunctionMap.put(fn.functionName(), fn);
            }
        }
    }

    public IParseFunction getFunction(String functionName) {
        return allFunctionMap.get(functionName);
    }

    public boolean isBeforeFunction(String functionName) {
        IParseFunction fn = allFunctionMap.get(functionName);
        return fn != null && fn.executeBefore();
    }
}

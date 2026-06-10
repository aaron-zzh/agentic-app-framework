package com.xuejiai.aaf.framework.bizlog.service.impl;

import com.xuejiai.aaf.framework.bizlog.service.IParseFunction;

/** 兜底解析函数（函数名为 null，不会被实际调用，仅用于保证 Spring 上下文至少有一个 IParseFunction Bean）。 */
public class DefaultParseFunction implements IParseFunction {

    @Override
    public boolean executeBefore() {
        return true;
    }

    @Override
    public String functionName() {
        return null;
    }

    @Override
    public String apply(Object value) {
        return null;
    }
}

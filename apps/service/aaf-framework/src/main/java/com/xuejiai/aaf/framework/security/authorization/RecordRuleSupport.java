package com.xuejiai.aaf.framework.security.authorization;

import com.xuejiai.aaf.framework.crud.enforcement.RecordRule;

/** 记录规则编译 SPI；每次调用必须返回显式 allow/deny 规则和访问版本。 */
public interface RecordRuleSupport {

    <T> RecordRule<T> compile(String resourceKey, Long subjectId);
}

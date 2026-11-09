package com.xuejiai.aaf.framework.intelligent.ai.chat;

/** 模型供应商因限频拒绝请求：请求未被执行、不产生费用，调用方可以安全地延时重试。 */
public class ModelThrottledException extends RuntimeException {

    public ModelThrottledException(String message, Throwable cause) {
        super(message, cause);
    }
}

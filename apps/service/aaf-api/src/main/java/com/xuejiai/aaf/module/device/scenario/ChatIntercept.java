package com.xuejiai.aaf.module.device.scenario;

/** 场景命中确定性规则后直接返回的对话结果。 */
public record ChatIntercept(String reply) {

    public ChatIntercept {
        if (reply == null || reply.isBlank()) {
            throw new IllegalArgumentException("拦截回复不能为空");
        }
    }
}

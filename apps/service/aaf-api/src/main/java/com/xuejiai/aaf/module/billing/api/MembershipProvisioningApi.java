package com.xuejiai.aaf.module.billing.api;

/** 会员默认权益初始化契约。 */
public interface MembershipProvisioningApi {

    /** 用户尚无订阅时初始化 FREE_DEFAULT；已有订阅时保持不变。 */
    void ensureFreeSubscription(Long userId);
}

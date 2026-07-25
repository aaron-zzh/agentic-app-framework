package com.xuejiai.aaf.framework.engine.lease;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 分布式租约引擎自动配置。 */
@AutoConfiguration
public class LeaseAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(DistributedLeasePort.class)
    RedisDistributedLeaseAdapter distributedLeasePort(StringRedisTemplate redis) {
        return new RedisDistributedLeaseAdapter(redis);
    }
}

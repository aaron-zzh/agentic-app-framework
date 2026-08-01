package com.xuejiai.aaf.framework.engine.cache;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 缓存失效跨实例广播（M50）。
 *
 * <p>修复前：{@code CacheInvalidationEvent} 是 JVM 内 Spring ApplicationEvent，失效只清**本机** Caffeine +
 * Redis。多实例部署时其他节点的本地缓存不受影响，模型/Agent/Prompt 配置改完后， 别的实例最长会继续用 {@code LOCAL_TTL}（5 分钟）内的旧配置——表现为"改了没生效/时好时坏"。
 *
 * <p>现在失效在本机处理完后经 Redis pub/sub 广播，其他实例只清本地副本（Redis 侧已由发起方删除）。 消息带发送方实例 ID，发起方忽略自己的广播，避免回环。
 *
 * <p>Redis 不可用时广播失败只记日志——本机与 Redis 的失效已经完成，退化为原来的 TTL 收敛行为，不影响主流程。
 */
@Slf4j
@Component
public class CacheInvalidationBroadcaster {

    /** 广播频道 */
    static final String CHANNEL = "aaf:cache:invalidate";

    /** 消息分隔符——cacheName 与 key 都不允许包含它 */
    private static final String SEPARATOR = "\u0001";

    /** 本实例标识，用于忽略自身广播 */
    private final String instanceId = UUID.randomUUID().toString();

    private final StringRedisTemplate redisTemplate;
    private final TwoLevelCacheFactory cacheFactory;

    public CacheInvalidationBroadcaster(
            StringRedisTemplate redisTemplate, TwoLevelCacheFactory cacheFactory) {
        this.redisTemplate = redisTemplate;
        this.cacheFactory = cacheFactory;
    }

    /** 广播一次失效（key 为 null 表示全量）。 */
    public void broadcast(String cacheName, Object key) {
        try {
            var payload =
                    String.join(
                            SEPARATOR, instanceId, cacheName, key == null ? "" : String.valueOf(key));
            redisTemplate.convertAndSend(CHANNEL, payload);
        } catch (Exception e) {
            log.warn("缓存失效广播失败，其他实例将等本地 TTL 过期: cache={}, key={}", cacheName, key, e);
        }
    }

    /** 处理其他实例的广播——只清本机本地副本。 */
    @SuppressWarnings("unchecked")
    public void handleMessage(String payload) {
        if (payload == null || payload.isBlank()) {
            return;
        }
        var parts = payload.split(SEPARATOR, -1);
        if (parts.length != 3) {
            log.debug("忽略格式非法的缓存失效广播: {}", payload);
            return;
        }
        if (instanceId.equals(parts[0])) {
            return; // 自己发的，本机已处理
        }
        TwoLevelCache<Object, Object> cache = cacheFactory.getCache(parts[1]);
        if (cache == null) {
            return;
        }
        if (parts[2].isEmpty()) {
            cache.invalidateAllLocal();
            log.info("按广播清空本地缓存: {}", parts[1]);
        } else {
            // key 经广播只剩字符串形态：Long key 缓存需要转回数字，否则清不掉
            cache.invalidateLocal(restoreKey(parts[2]));
            log.debug("按广播清除本地缓存: {}:{}", parts[1], parts[2]);
        }
    }

    /** 纯数字 key 还原为 Long，其余按字符串处理（当前缓存 key 类型为 Long 或 String）。 */
    private Object restoreKey(String raw) {
        if (raw.chars().allMatch(Character::isDigit) && raw.length() < 19) {
            return Long.valueOf(raw);
        }
        return raw;
    }

    /** pub/sub 监听装配——与广播器同文件声明，避免为一个 Bean 再建配置类。 */
    @Configuration
    static class CacheInvalidationSubscriberConfig {

        @Bean
        RedisMessageListenerContainer cacheInvalidationListenerContainer(
                RedisConnectionFactory connectionFactory,
                CacheInvalidationBroadcaster broadcaster) {
            var container = new RedisMessageListenerContainer();
            container.setConnectionFactory(connectionFactory);
            container.addMessageListener(
                    (message, pattern) ->
                            broadcaster.handleMessage(
                                    new String(message.getBody(), StandardCharsets.UTF_8)),
                    new ChannelTopic(CHANNEL));
            return container;
        }
    }
}

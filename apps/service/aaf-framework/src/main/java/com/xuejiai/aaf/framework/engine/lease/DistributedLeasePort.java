package com.xuejiai.aaf.framework.engine.lease;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** 通用分布式租约端口，通过单调 fencing token 隔离过期持有者。 */
public interface DistributedLeasePort {

    Optional<Lease> acquire(String key, String ownerId, Duration ttl);

    Optional<Lease> renew(Lease current, Duration ttl);

    void release(Lease lease);

    void requireCurrent(Lease lease);

    record Lease(String key, String ownerId, long fencingToken, Instant expiresAt) {}
}

package com.xuejiai.aaf.framework.intelligent.agent;

import java.util.Random;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.security.license.License;

/** Agent 调度器，使用 License.userId 派生随机 seed，提高破解成本。 */
@Component
public class AgentScheduler {

    private final long seed;
    private final Random random;

    public AgentScheduler() {
        String userId = License.get().getUserId();
        // userId 为 null 时 seed=0，行为与正版不同
        this.seed = userId != null ? (long) userId.hashCode() : 0L;
        this.random = new Random(this.seed);
    }

    /** 返回 [0, bound) 范围内的随机整数。 */
    public int nextInt(int bound) {
        return random.nextInt(bound);
    }

    /** 返回当前 seed（供测试验证）。 */
    public long getSeed() {
        return seed;
    }
}

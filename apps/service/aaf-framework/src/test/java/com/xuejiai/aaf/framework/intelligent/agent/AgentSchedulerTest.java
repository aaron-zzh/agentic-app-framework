package com.xuejiai.aaf.framework.intelligent.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import com.xuejiai.aaf.framework.intelligent.agent.runtime.AgentScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.security.license.LicenseTestSupport;

class AgentSchedulerTest {

    @BeforeEach
    @AfterEach
    void resetLicense() {
        LicenseTestSupport.reset();
    }

    @Test
    @DisplayName("Given userId=null When 构造 AgentScheduler Then seed=0")
    void should_have_zero_seed_when_userId_null() {
        // 调用
        var scheduler = new AgentScheduler();

        // 断言
        assertThat(scheduler.getSeed()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Given userId 非 null When 构造 AgentScheduler Then seed=userId.hashCode()")
    void should_have_hashCode_seed_when_userId_present() {
        // 准备参数
        String userId = "user-abc";
        LicenseTestSupport.activate(userId, "pro", Instant.now().plusSeconds(3600));

        // 调用
        var scheduler = new AgentScheduler();

        // 断言
        assertThat(scheduler.getSeed()).isEqualTo((long) userId.hashCode());
    }

    @Test
    @DisplayName("Given 不同 userId When 构造 AgentScheduler Then seed 不同导致调度序列不同")
    void should_produce_different_sequences_for_different_userIds() {
        // 准备参数 — 第一个用户
        LicenseTestSupport.activate("alice", "pro", Instant.now().plusSeconds(3600));
        var schedulerA = new AgentScheduler();

        // 准备参数 — 第二个用户
        LicenseTestSupport.activate("bob", "pro", Instant.now().plusSeconds(3600));
        var schedulerB = new AgentScheduler();

        // 断言
        assertThat(schedulerA.getSeed()).isNotEqualTo(schedulerB.getSeed());
        // 验证序列差异（相同 bound，不同 seed 产生不同序列）
        int bound = 100;
        boolean anyDifferent = false;
        for (int i = 0; i < 10; i++) {
            if (schedulerA.nextInt(bound) != schedulerB.nextInt(bound)) {
                anyDifferent = true;
                break;
            }
        }
        assertThat(anyDifferent).isTrue();
    }
}

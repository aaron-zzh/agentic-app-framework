package com.xuejiai.aaf.module.system.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import com.xuejiai.aaf.framework.security.authorization.AuthorizationChallengeStore;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationSubject;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationTarget;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

@DisplayName("JpaAuthorizationChallengeStore 单元测试")
class JpaAuthorizationChallengeStoreTest extends BaseMockitoUnitTest {

    @Mock private AuthorizationChallengeRepository repository;
    @Captor private ArgumentCaptor<AuthorizationChallenge> challengeCaptor;

    @Test
    @DisplayName("Given 完整 challenge When 创建 Then 持久化全部请求和策略绑定")
    void should_persist_all_bindings_when_create() {
        // 准备参数
        var store = new JpaAuthorizationChallengeStore(repository);
        var subject = new AuthorizationSubject(11L, 12L, 13L, 14L);
        var target = new AuthorizationTarget("document", "update", "doc-15");
        var expiresAt = Instant.now().plusSeconds(300);
        var pending =
                new AuthorizationChallengeStore.PendingChallenge(
                        subject, target, "digest", 16L, 17L, "snapshot-18", expiresAt);
        when(repository.save(any(AuthorizationChallenge.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // 调用
        var challengeId = store.create(pending);

        // 断言
        verify(repository).save(challengeCaptor.capture());
        var persisted = challengeCaptor.getValue();
        assertThat(challengeId).isEqualTo(persisted.getId());
        assertThat(persisted.getOperatorId()).isEqualTo(11L);
        assertThat(persisted.getSubjectId()).isEqualTo(12L);
        assertThat(persisted.getTenantId()).isEqualTo(13L);
        assertThat(persisted.getWorkspaceId()).isEqualTo(14L);
        assertThat(persisted.getResource()).isEqualTo("document");
        assertThat(persisted.getAction()).isEqualTo("update");
        assertThat(persisted.getObjectId()).isEqualTo("doc-15");
        assertThat(persisted.getRequestDigest()).isEqualTo("digest");
        assertThat(persisted.getPolicyId()).isEqualTo(16L);
        assertThat(persisted.getPolicyVersion()).isEqualTo(17L);
        assertThat(persisted.getSnapshotVersion()).isEqualTo("snapshot-18");
        assertThat(persisted.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(persisted.getStatus()).isEqualTo("PENDING");
        assertThat(persisted.getCreateTime()).isNotNull();
    }

    @Test
    @DisplayName("Given challenge 缺少请求摘要 When 创建 Then 拒绝不完整绑定")
    void should_reject_incomplete_binding_when_create() {
        // 准备参数
        var store = new JpaAuthorizationChallengeStore(repository);
        var pending =
                new AuthorizationChallengeStore.PendingChallenge(
                        new AuthorizationSubject(1L, 2L, 3L, 4L),
                        new AuthorizationTarget("document", "read", null),
                        " ",
                        5L,
                        6L,
                        "snapshot-7",
                        Instant.now().plusSeconds(60));

        // 调用 + 断言
        assertThatThrownBy(() -> store.create(pending))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("绑定字段不完整");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Given 已过期 challenge When 创建 Then 拒绝持久化")
    void should_reject_expired_challenge_when_create() {
        // 准备参数
        var store = new JpaAuthorizationChallengeStore(repository);
        var pending =
                new AuthorizationChallengeStore.PendingChallenge(
                        new AuthorizationSubject(1L, 2L, 3L, 4L),
                        new AuthorizationTarget("document", "read", null),
                        "digest",
                        5L,
                        6L,
                        "snapshot-7",
                        Instant.now().minusSeconds(1));

        // 调用 + 断言
        assertThatThrownBy(() -> store.create(pending))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已过期");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Given CAS 条件更新与过期保护 When 批准消费或重放 Then 仅首次有效且过期记录不可用")
    void should_enforce_expiration_and_single_use_with_compare_and_set() {
        // 准备参数
        var store = new JpaAuthorizationChallengeStore(repository);
        var challengeId = UUID.randomUUID();
        var now = Instant.parse("2026-07-22T12:00:00Z");
        var afterExpiry = now.plusSeconds(300);
        when(repository.approve(challengeId, 2L, now)).thenReturn(1, 0);
        when(repository.consume(challengeId, 2L, now)).thenReturn(1, 0);
        when(repository.approve(challengeId, 2L, afterExpiry)).thenReturn(0);
        when(repository.findApproved(challengeId, 2L, afterExpiry))
                .thenReturn(Optional.empty());
        when(repository.consume(challengeId, 2L, afterExpiry)).thenReturn(0);

        // 调用 + 断言
        assertThat(store.approve(challengeId, 2L, now)).isTrue();
        assertThat(store.approve(challengeId, 2L, now)).isFalse();
        assertThat(store.consume(challengeId, 2L, now)).isTrue();
        assertThat(store.consume(challengeId, 2L, now)).isFalse();
        assertThat(store.approve(challengeId, 2L, afterExpiry)).isFalse();
        assertThat(store.findApproved(challengeId, 2L, afterExpiry)).isEmpty();
        assertThat(store.consume(challengeId, 2L, afterExpiry)).isFalse();
        verify(repository, org.mockito.Mockito.times(2)).approve(challengeId, 2L, now);
        verify(repository, org.mockito.Mockito.times(2)).consume(challengeId, 2L, now);
    }

    @Test
    @DisplayName("Given 已批准且未过期 challenge When 查询 Then 恢复完整绑定")
    void should_restore_all_bindings_when_find_approved() {
        // 准备参数
        var store = new JpaAuthorizationChallengeStore(repository);
        var challengeId = UUID.randomUUID();
        var now = Instant.parse("2026-07-22T12:00:00Z");
        var expiresAt = now.plusSeconds(60);
        var entity = challenge(challengeId, expiresAt);
        when(repository.findApproved(challengeId, 22L, now)).thenReturn(Optional.of(entity));

        // 调用
        var result = store.findApproved(challengeId, 22L, now);

        // 断言
        assertThat(result).isPresent();
        var restored = result.orElseThrow();
        assertThat(restored.id()).isEqualTo(challengeId);
        assertThat(restored.subject())
                .isEqualTo(new AuthorizationSubject(21L, 22L, 23L, 24L));
        assertThat(restored.target())
                .isEqualTo(new AuthorizationTarget("document", "update", "doc-25"));
        assertThat(restored.requestDigest()).isEqualTo("digest-26");
        assertThat(restored.policyId()).isEqualTo(27L);
        assertThat(restored.policyVersion()).isEqualTo(28L);
        assertThat(restored.snapshotVersion()).isEqualTo("snapshot-29");
        assertThat(restored.expiresAt()).isEqualTo(expiresAt);
    }

    private AuthorizationChallenge challenge(UUID id, Instant expiresAt) {
        var challenge = new AuthorizationChallenge();
        challenge.setId(id);
        challenge.setOperatorId(21L);
        challenge.setSubjectId(22L);
        challenge.setTenantId(23L);
        challenge.setWorkspaceId(24L);
        challenge.setResource("document");
        challenge.setAction("update");
        challenge.setObjectId("doc-25");
        challenge.setRequestDigest("digest-26");
        challenge.setPolicyId(27L);
        challenge.setPolicyVersion(28L);
        challenge.setSnapshotVersion("snapshot-29");
        challenge.setStatus("APPROVED");
        challenge.setExpiresAt(expiresAt);
        challenge.setApprovedAt(expiresAt.minusSeconds(30));
        challenge.setCreateTime(expiresAt.minusSeconds(60));
        return challenge;
    }
}

package com.xuejiai.aaf.module.system.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

@DisplayName("AuthorizationChallengeRepository 查询契约测试")
class AuthorizationChallengeRepositoryTest {

    @Test
    @DisplayName("Given PENDING challenge When 批准 Then 条件更新绑定 subject 和过期时间")
    void should_bind_subject_status_and_expiry_when_approve() throws NoSuchMethodException {
        // 准备参数
        var method = method("approve");

        // 调用
        var query = normalizedQuery(method);

        // 断言
        assertThat(method.isAnnotationPresent(Modifying.class)).isTrue();
        assertThat(query)
                .contains("set challenge.status = 'approved'")
                .contains("challenge.subjectid = :subjectid")
                .contains("challenge.status = 'pending'")
                .contains("challenge.expiresat > :now");
    }

    @Test
    @DisplayName("Given APPROVED challenge When 查询 Then 绑定 subject、状态和过期时间")
    void should_bind_subject_status_and_expiry_when_find_approved()
            throws NoSuchMethodException {
        // 准备参数
        var method = method("findApproved");

        // 调用
        var query = normalizedQuery(method);

        // 断言
        assertThat(method.isAnnotationPresent(Modifying.class)).isFalse();
        assertThat(query)
                .contains("challenge.subjectid = :subjectid")
                .contains("challenge.status = 'approved'")
                .contains("challenge.expiresat > :now");
    }

    @Test
    @DisplayName("Given APPROVED challenge When 消费 Then 条件更新保证一次性")
    void should_bind_subject_status_and_expiry_when_consume() throws NoSuchMethodException {
        // 准备参数
        var method = method("consume");

        // 调用
        var query = normalizedQuery(method);

        // 断言
        assertThat(method.isAnnotationPresent(Modifying.class)).isTrue();
        assertThat(query)
                .contains("set challenge.status = 'consumed'")
                .contains("challenge.subjectid = :subjectid")
                .contains("challenge.status = 'approved'")
                .contains("challenge.expiresat > :now");
    }

    private Method method(String name) throws NoSuchMethodException {
        return AuthorizationChallengeRepository.class.getMethod(
                name, UUID.class, Long.class, Instant.class);
    }

    private String normalizedQuery(Method method) {
        return method.getAnnotation(Query.class)
                .value()
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}

package com.xuejiai.aaf.framework.crud.enforcement;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QueryTokenServiceTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-13T14:00:00Z"), ZoneOffset.UTC);
    private static final String SECRET = "01234567890123456789012345678901";

    private final QueryTokenService service =
            new QueryTokenService(SECRET, Duration.ofMinutes(5), CLOCK);

    @Test
    @DisplayName("Given list 查询窗口 token When 读取 detail Then 允许字段投影切换")
    void should_allow_list_token_for_detail() {
        var token = service.issue(listContext(), List.of(7L));

        assertThatCode(() -> service.validateForDetail(token, detailContext(), 7L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Given 非 list 查询窗口 token When 读取 detail Then 拒绝")
    void should_reject_non_list_token_for_detail() {
        var context =
                new QueryTokenService.QueryTokenContext(
                        10L, 20L, 30L, "system.todo", "picker", "query-hash", "access-v1");
        var token = service.issue(context, List.of(7L));

        assertThatThrownBy(() -> service.validateForDetail(token, detailContext(), 7L))
                .isInstanceOf(com.xuejiai.aaf.common.exception.BusinessException.class);
    }

    @Test
    @DisplayName("Given list 查询窗口 token When 读取窗口外记录 Then 保持成员校验")
    void should_reject_record_outside_detail_window() {
        var token = service.issue(listContext(), List.of(7L));

        assertThatThrownBy(() -> service.validateForDetail(token, detailContext(), 8L))
                .isInstanceOf(com.xuejiai.aaf.common.exception.BusinessException.class);
    }

    @Test
    @DisplayName("Given list 查询窗口 token When 严格校验 detail 字段集 Then 仍拒绝")
    void should_keep_strict_validation_for_other_callers() {
        var token = service.issue(listContext(), List.of(7L));
        var detailContext =
                new QueryTokenService.QueryTokenContext(
                        10L, 20L, 30L, "system.todo", "detail", "query-hash", "access-v1");

        assertThatThrownBy(() -> service.validate(token, detailContext, 7L))
                .isInstanceOf(com.xuejiai.aaf.common.exception.BusinessException.class);
    }

    private QueryTokenService.QueryTokenContext listContext() {
        return new QueryTokenService.QueryTokenContext(
                10L, 20L, 30L, "system.todo", "list", "query-hash", "access-v1");
    }

    private QueryTokenService.DetailQueryTokenContext detailContext() {
        return new QueryTokenService.DetailQueryTokenContext(
                10L, 20L, 30L, "system.todo", "access-v1");
    }
}

package com.xuejiai.aaf.framework.crud.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.Test;

class DateTimeFilterValueResolverTest {

    @Test
    void resolves_declared_variables_and_iso_literals() {
        var now = LocalDateTime.of(2026, 7, 19, 18, 30, 15);
        var context =
                FilterEvaluationContext.create(
                        Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC), ZoneOffset.UTC);
        var allowed = Set.of(DateTimeFilterVariable.NOW, DateTimeFilterVariable.TODAY_START);

        assertThat(DateTimeFilterValueResolver.resolve("$now", context, allowed)).isEqualTo(now);
        assertThat(DateTimeFilterValueResolver.resolve("2026-07-20", context, allowed))
                .isEqualTo(LocalDateTime.of(2026, 7, 20, 0, 0));
    }

    @Test
    void rejects_undeclared_variables() {
        assertThatThrownBy(
                        () ->
                                DateTimeFilterValueResolver.resolve(
                                        "$tomorrowStart",
                                        FilterEvaluationContext.create(
                                                Clock.fixed(
                                                        LocalDateTime.of(2026, 7, 19, 0, 0)
                                                                .toInstant(ZoneOffset.UTC),
                                                        ZoneOffset.UTC),
                                                ZoneOffset.UTC),
                                        Set.of(DateTimeFilterVariable.NOW)))
                .isInstanceOf(RuntimeException.class);
    }
}

package com.xuejiai.aaf.module.stats.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.xuejiai.aaf.module.stats.repository.UserEventRepository;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

/** 统计服务单元测试。 */
class StatsServiceTest extends BaseMockitoUnitTest {

    @Mock
    private UserEventRepository eventRepository;

    @InjectMocks
    private StatsService statsService;

    @Test
    void getTrend_应返回趋势数据() {
        when(eventRepository.countByDateRange(
                "page_view", LocalDate.now().minusDays(7).atStartOfDay(),
                LocalDate.now().atStartOfDay().plusDays(1)))
                .thenReturn(List.of());

        var result = statsService.getTrend("page_view", "day", 7);

        assertThat(result).isNotNull();
    }
}

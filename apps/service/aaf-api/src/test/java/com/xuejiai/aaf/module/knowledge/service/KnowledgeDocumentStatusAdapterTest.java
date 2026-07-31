package com.xuejiai.aaf.module.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.jdbc.core.JdbcTemplate;

import com.xuejiai.aaf.framework.engine.meta.runtime.TaskExecutionInProgressException;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class KnowledgeDocumentStatusAdapterTest extends BaseMockitoUnitTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @InjectMocks private KnowledgeDocumentStatusAdapter statusAdapter;

    @Test
    @DisplayName("Given 文档未删除 When 更新状态 Then SQL 限定 deleted false")
    void should_update_only_non_deleted_document() {
        // 准备参数
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        var sqlCaptor = ArgumentCaptor.forClass(String.class);

        // 调用
        statusAdapter.updateStatus(11L, 2, 4, null);

        // 断言
        verify(jdbcTemplate).update(sqlCaptor.capture(), any(Object[].class));
        assertThat(sqlCaptor.getValue()).contains("WHERE id = ? AND deleted = false");
    }

    @Test
    @DisplayName("Given 文档已删除 When 陈旧 worker 更新状态 Then 抛出执行中异常停止写入")
    void should_defer_when_document_is_deleted_or_missing() {
        // 准备参数
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);

        // 调用 + 断言
        assertThatThrownBy(() -> statusAdapter.updateStatus(11L, 3, 0, "failed"))
                .isInstanceOf(TaskExecutionInProgressException.class)
                .hasMessageContaining("11");
    }
}

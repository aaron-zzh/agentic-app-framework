package com.xuejiai.aaf.framework.engine.dataprocess.table;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.common.exception.BusinessException;

import jakarta.persistence.EntityManager;

/** DynamicTableService 单元测试（B16 SQL 标识符注入防护）。 */
class DynamicTableServiceTest {

    private final DataTableRepository repo = mock(DataTableRepository.class);
    private final EntityManager em = mock(EntityManager.class);
    private final DynamicTableService service = new DynamicTableService(repo, em);

    /** B16：建表 slug 含 SQL 元字符 → 非法标识符拒绝（不触达 DDL）。 */
    @Test
    void createTable_非法slug拒绝() {
        assertThatThrownBy(() -> service.createTable("a; DROP TABLE x", "d", null, List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非法标识符");
    }

    /** B16：列名含空格/注入片段 → 非法标识符拒绝。 */
    @Test
    void createTable_非法列名拒绝() {
        var col = mock(DataColumnDefinition.class);
        when(col.getName()).thenReturn("OR 1=1");
        assertThatThrownBy(() -> service.createTable("good_slug", "d", null, List.of(col)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非法标识符");
    }

    /** B16：写入未知列 → 拒绝（杜绝注入与 Mass-Assignment 脏写）。 */
    @Test
    void insertRow_未知列拒绝() {
        var col = mock(DataColumnDefinition.class);
        when(col.getName()).thenReturn("name");
        var def = mock(DataTableDefinition.class);
        when(def.getColumns()).thenReturn(List.of(col));
        when(repo.findBySlug("t")).thenReturn(Optional.of(def));

        assertThatThrownBy(() -> service.insertRow("t", Map.of("evil\"; DROP", 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未知列");
    }
}

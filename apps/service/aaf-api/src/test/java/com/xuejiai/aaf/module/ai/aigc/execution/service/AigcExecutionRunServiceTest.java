package com.xuejiai.aaf.module.ai.aigc.execution.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionRun;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionRunRepository;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionTaskRefRepository;
import com.xuejiai.aaf.module.ai.aigc.execution.vo.AigcExecutionRunPageDTO;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

class AigcExecutionRunServiceTest extends BaseMockitoUnitTest {

    @Mock private AigcExecutionRunRepository repository;
    @Mock private AigcExecutionTaskRefRepository taskRefRepository;
    @Mock private Root<AigcExecutionRun> root;
    @Mock private CriteriaQuery<?> query;
    @Mock private CriteriaBuilder criteriaBuilder;
    @Mock private Path<Object> parentPath;
    @Mock private Path<Object> idPath;
    @Mock private Path<Object> rootIdPath;
    @Mock private Predicate parentIsNull;
    @Mock private Predicate idIsRoot;
    @Mock private Predicate rootPredicate;

    @Test
    @DisplayName("Given rootOnly=true When 构造列表条件 Then 同时约束 parent 为空且 id 等于 rootExecutionRunId")
    void should_filter_exact_roots_when_root_only_is_true() {
        // 准备参数
        var request = new AigcExecutionRunPageDTO();
        request.setRootOnly(true);
        when(root.get("parentExecutionRunId")).thenReturn(parentPath);
        when(root.get("id")).thenReturn(idPath);
        when(root.get("rootExecutionRunId")).thenReturn(rootIdPath);
        when(criteriaBuilder.isNull(parentPath)).thenReturn(parentIsNull);
        when(criteriaBuilder.equal(idPath, rootIdPath)).thenReturn(idIsRoot);
        when(criteriaBuilder.and(parentIsNull, idIsRoot)).thenReturn(rootPredicate);
        var service = new AigcExecutionRunService(repository, taskRefRepository);

        // 调用
        var predicate = service.buildSpec(request).toPredicate(root, query, criteriaBuilder);

        // 断言
        assertThat(predicate).isSameAs(rootPredicate);
        verify(criteriaBuilder).isNull(parentPath);
        verify(criteriaBuilder).equal(idPath, rootIdPath);
        verify(criteriaBuilder).and(parentIsNull, idIsRoot);
    }

    @Test
    @DisplayName("Given rootOnly 未开启 When 构造列表条件 Then 不附加 parent/root 条件")
    void should_not_filter_roots_when_root_only_is_not_true() {
        // 准备参数
        var request = new AigcExecutionRunPageDTO();
        var service = new AigcExecutionRunService(repository, taskRefRepository);

        // 调用
        var predicate = service.buildSpec(request).toPredicate(root, query, criteriaBuilder);

        // 断言
        assertThat(predicate).isNull();
    }
}

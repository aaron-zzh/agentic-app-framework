package com.xuejiai.aaf.framework.crud.relation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.crud.definition.Patch;
import com.xuejiai.aaf.framework.crud.definition.ResourceKey;
import com.xuejiai.aaf.framework.crud.enforcement.ReferenceEnforcementService;
import com.xuejiai.aaf.framework.crud.reference.ReferenceRequest;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class GenericRelationHandlerTest extends BaseMockitoUnitTest {

    private static final ResourceKey SOURCE = ResourceKey.of("test.todo");

    @Mock private ReferenceEnforcementService referenceEnforcementService;

    private GenericRelationHandler handler;
    private RelationDefinition<Object, Object> relation;
    private TestParent parent;

    @BeforeEach
    void setUp() {
        handler = new GenericRelationHandler(referenceEnforcementService);
        relation =
                new RelationDefinition<>(
                        "participants",
                        ResourceKey.of("system.user"),
                        RelationDefinition.Cardinality.MANY,
                        RelationDefinition.SyncMode.REPLACE,
                        100,
                        TestLink.class,
                        AssociationKind.MANY_TO_MANY_JOIN,
                        "todoId",
                        "userId",
                        "participants",
                        "participants",
                        "");
        parent = new TestParent();
        parent.setId(10L);
    }

    @Test
    @DisplayName("Given 合法 M2M ID When 校验 Then 一次批量执行默认引用授权")
    @SuppressWarnings("unchecked")
    void should_validate_many_to_many_targets_in_batch() {
        when(referenceEnforcementService.referenceable(
                        eq(SOURCE), eq("participants"), anyCollection()))
                .thenAnswer(
                        invocation ->
                                Set.copyOf(
                                        (Collection<ReferenceRequest>) invocation.getArgument(2)));

        assertThatCode(
                        () ->
                                handler.validate(
                                        SOURCE,
                                        relation,
                                        parent,
                                        Patch.value(List.of(8L, 9L))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Given M2M ID 重复 When 校验 Then 在访问目标资源前拒绝")
    void should_reject_duplicate_many_to_many_targets() {
        assertThatThrownBy(
                        () ->
                                handler.validate(
                                        SOURCE,
                                        relation,
                                        parent,
                                        Patch.value(List.of(8L, 8L))))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(referenceEnforcementService);
    }

    private static final class TestParent extends BaseEntity {}

    private static final class TestLink {
        private Long todoId;
        private Long userId;

        public Long getTodoId() {
            return todoId;
        }

        public void setTodoId(Long todoId) {
            this.todoId = todoId;
        }

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }
    }
}

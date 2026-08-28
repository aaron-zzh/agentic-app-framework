package com.xuejiai.aaf.module.system.todo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.xuejiai.aaf.common.enums.sys.TodoSourceTypeEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.framework.crud.definition.CrudOperation;
import com.xuejiai.aaf.framework.crud.definition.Patch;
import com.xuejiai.aaf.framework.crud.enforcement.ReferenceEnforcementService;
import com.xuejiai.aaf.framework.crud.reference.ResourceReference;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.system.role.relation.ResourceRelationService;
import com.xuejiai.aaf.module.system.todo.domain.Todo;
import com.xuejiai.aaf.module.system.todo.repository.TodoRepository;
import com.xuejiai.aaf.module.system.todo.vo.TodoCreateDTO;
import com.xuejiai.aaf.module.system.todo.vo.TodoUpdateDTO;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class TodoServiceTest extends BaseMockitoUnitTest {

    @Mock private TodoRepository todoRepository;
    @Mock private ResourceRelationService resourceRelationService;
    @Mock private OperatorContext operatorContext;
    @Mock private ReferenceEnforcementService referenceEnforcementService;
    @InjectMocks private TodoService todoService;

    @Test
    @DisplayName("Given 未指定执行人和合法来源 When 转换创建请求 Then 默认当前用户并保存来源")
    void should_use_current_subject_and_source_when_creating() {
        // 准备参数
        var source = new ResourceReference("system.document", 99L);
        var request =
                new TodoCreateDTO("跟进客户", "call", null, Patch.value(source), null, Patch.absent());
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(7L));

        // 调用
        var todo = todoService.toEntity(request);

        // 断言
        assertThat(todo.getAssigneeId()).isEqualTo(7L);
        assertThat(todo.getSourceType()).isEqualTo(TodoSourceTypeEnum.MANUAL.getCode());
        assertThat(todo.getSourceEntity()).isEqualTo("system.document");
        assertThat(todo.getSourceId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("Given 创建请求显式 source null When 转换 Then 清空来源且不调用来源策略")
    void should_clear_source_without_policy_when_create_source_is_null() {
        // 准备参数
        var request = new TodoCreateDTO("整理笔记", null, 7L, Patch.nullValue(), null, Patch.absent());
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(7L));

        // 调用
        var todo = todoService.toEntity(request);

        // 断言
        assertThat(todo.getSourceEntity()).isNull();
        assertThat(todo.getSourceId()).isNull();
        verify(referenceEnforcementService, never())
                .canReference(any(), nullable(Long.class), eq("source"), any());
    }

    @Test
    @DisplayName("Given 版本一致 When 更新标量 Then 应用 Patch 并推进父 version")
    void should_apply_patch_and_increment_version_when_expected_version_matches() {
        // 准备参数
        var todo = todoWithSource(TodoSourceTypeEnum.MANUAL.getCode());
        todo.setVersion(3);
        var request =
                new TodoUpdateDTO(
                        Patch.value("更新标题"),
                        Patch.absent(),
                        Patch.absent(),
                        Patch.absent(),
                        Patch.nullValue(),
                        Patch.absent(),
                        Patch.value(java.util.List.of(8L)),
                        3);

        // 调用
        todoService.updateEntity(todo, request);

        // 断言
        assertThat(todo.getTitle()).isEqualTo("更新标题");
        assertThat(todo.getDueDate()).isNull();
        assertThat(todo.getVersion()).isEqualTo(4);
    }

    @Test
    @DisplayName("Given 期望版本过期 When 更新 Then 返回 409 且不修改实体")
    void should_reject_update_when_expected_version_is_stale() {
        // 准备参数
        var todo = todoWithSource(TodoSourceTypeEnum.MANUAL.getCode());
        todo.setVersion(2);
        var request = updateTitle("更新标题", 1);

        // 调用 + 断言
        assertThatThrownBy(() -> todoService.updateEntity(todo, request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(1_006_007);
        assertThat(todo.getTitle()).isEqualTo("原待办");
        assertThat(todo.getVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("Given 系统来源待办 When 真实修改 source Then 确定拒绝")
    void should_reject_source_change_when_source_is_system_managed() {
        // 准备参数
        var todo = todoWithSource(TodoSourceTypeEnum.TASK.getCode());
        var request =
                new TodoUpdateDTO(
                        Patch.absent(),
                        Patch.absent(),
                        Patch.absent(),
                        Patch.absent(),
                        Patch.absent(),
                        Patch.value(new ResourceReference("system.document", 8L)),
                        Patch.absent(),
                        0);

        // 调用 + 断言
        assertThatThrownBy(() -> todoService.updateEntity(todo, request))
                .isInstanceOf(BusinessException.class);
        assertThat(todo.getSourceId()).isEqualTo(7L);
        verify(referenceEnforcementService, never())
                .canReference(any(), nullable(Long.class), eq("source"), any());
    }

    @Test
    @DisplayName("Given 系统来源待办 When source 缺失 Then 更新其他字段并保留来源")
    void should_keep_system_source_when_source_patch_is_absent() {
        // 准备参数
        var todo = todoWithSource(TodoSourceTypeEnum.COMMENT.getCode());

        // 调用
        todoService.updateEntity(todo, updateTitle("更新标题", 0));

        // 断言
        assertThat(todo.getTitle()).isEqualTo("更新标题");
        assertThat(todo.getSourceEntity()).isEqualTo("system.document");
        assertThat(todo.getSourceId()).isEqualTo(7L);
        assertThat(todo.getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("Given Todo 单对象操作 When 声明关系要求 Then 仅 GET 返回 todo:{id}#can_read")
    void should_declare_relation_requirement_only_for_get() {
        // 调用
        var getRequirement = todoService.relationRequirement(99L, CrudOperation.GET);
        var updateRequirement = todoService.relationRequirement(99L, CrudOperation.UPDATE);
        var deleteRequirement = todoService.relationRequirement(99L, CrudOperation.DELETE);

        // 断言
        assertThat(getRequirement.objectType()).isEqualTo("todo");
        assertThat(getRequirement.objectId()).isEqualTo("99");
        assertThat(getRequirement.permission()).isEqualTo("can_read");
        assertThat(updateRequirement).isNull();
        assertThat(deleteRequirement).isNull();
    }

    private TodoUpdateDTO updateTitle(String title, int expectedVersion) {
        return new TodoUpdateDTO(
                Patch.value(title),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                expectedVersion);
    }

    private Todo todoWithSource(String sourceType) {
        var todo = new Todo();
        todo.setId(1L);
        todo.setAssigneeId(7L);
        todo.setTitle("原待办");
        todo.setSourceType(sourceType);
        todo.setSourceEntity("system.document");
        todo.setSourceId(7L);
        return todo;
    }
}

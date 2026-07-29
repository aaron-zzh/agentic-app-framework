package com.xuejiai.aaf.module.system.entity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.xuejiai.aaf.framework.crud.reference.ResourceReference;
import com.xuejiai.aaf.framework.crud.runtime.CrudResourceAccessRegistry;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class CodeEntityReferenceAccessTest extends BaseMockitoUnitTest {

    @Mock private CrudResourceAccessRegistry resourceAccessRegistry;
    @InjectMocks private CodeEntityReferenceAccess entityReferenceAccess;

    @Test
    @DisplayName("Given 代码资源可读 When 校验来源读取权限 Then 委托运行时目录")
    void should_delegate_read_check_to_runtime_registry() {
        var reference = new ResourceReference("system.todo", 99L);
        when(resourceAccessRegistry.readable(List.of(reference))).thenReturn(Set.of(reference));

        var readable = entityReferenceAccess.readable(List.of(reference));

        assertThat(readable).containsExactly(reference);
        verify(resourceAccessRegistry).readable(List.of(reference));
    }

    @Test
    @DisplayName("Given 代码资源可引用 When 校验来源引用权限 Then 委托运行时目录")
    void should_delegate_reference_check_to_runtime_registry() {
        var reference = new ResourceReference("system.todo", 99L);
        when(resourceAccessRegistry.referenceable(List.of(reference)))
                .thenReturn(Set.of(reference));

        var referenceable = entityReferenceAccess.referenceable(List.of(reference));

        assertThat(referenceable).containsExactly(reference);
        verify(resourceAccessRegistry).referenceable(List.of(reference));
    }
}

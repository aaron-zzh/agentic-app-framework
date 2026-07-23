package com.xuejiai.aaf.module.system.entity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.xuejiai.aaf.framework.crud.runtime.CrudResourceAccessRegistry;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class CodeEntityReferenceAccessTest extends BaseMockitoUnitTest {

    @Mock private CrudResourceAccessRegistry resourceAccessRegistry;
    @InjectMocks private CodeEntityReferenceAccess entityReferenceAccess;

    @Test
    @DisplayName("Given 代码资源可读 When 校验来源读取权限 Then 委托运行时目录")
    void should_delegate_read_check_to_runtime_registry() {
        when(resourceAccessRegistry.canRead("system.todo", 99L)).thenReturn(true);

        var readable = entityReferenceAccess.canRead("system.todo", 99L);

        assertThat(readable).isTrue();
        verify(resourceAccessRegistry).canRead("system.todo", 99L);
    }

    @Test
    @DisplayName("Given 代码资源可引用 When 校验来源引用权限 Then 委托运行时目录")
    void should_delegate_reference_check_to_runtime_registry() {
        when(resourceAccessRegistry.canReference("system.todo", 99L)).thenReturn(true);

        var referenceable = entityReferenceAccess.canReference("system.todo", 99L);

        assertThat(referenceable).isTrue();
        verify(resourceAccessRegistry).canReference("system.todo", 99L);
    }
}

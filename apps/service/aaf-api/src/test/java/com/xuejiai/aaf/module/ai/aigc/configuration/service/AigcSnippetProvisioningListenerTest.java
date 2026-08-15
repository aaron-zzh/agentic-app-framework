package com.xuejiai.aaf.module.ai.aigc.configuration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcSnippet;
import com.xuejiai.aaf.module.ai.aigc.configuration.repository.AigcSnippetRepository;
import com.xuejiai.aaf.module.system.org.event.OrganizationCreatedEvent;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class AigcSnippetProvisioningListenerTest extends BaseMockitoUnitTest {

    @Mock private AigcSnippetRepository repository;
    @InjectMocks private AigcSnippetProvisioningListener listener;

    @Test
    @DisplayName("Given 新组织 When 初始化内置片段 Then 创建五条组织级公开只读片段")
    void should_provision_builtin_snippets_for_new_organization() {
        when(repository.existsByOrgIdAndBuiltinCodeAndDeletedFalse(10L, "cinematic-lighting"))
                .thenReturn(true);

        listener.provision(new OrganizationCreatedEvent(10L));

        @SuppressWarnings("unchecked")
        var captor = ArgumentCaptor.forClass((Class<List<AigcSnippet>>) (Class<?>) List.class);
        verify(repository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .hasSize(4)
                .allSatisfy(
                        snippet -> {
                            assertThat(snippet.getOrgId()).isEqualTo(10L);
                            assertThat(snippet.getWorkspaceId()).isNull();
                            assertThat(snippet.getOwnerId()).isNull();
                            assertThat(snippet.getIsPublic()).isTrue();
                            assertThat(snippet.getBuiltinCode()).isNotBlank();
                        })
                .extracting(AigcSnippet::getBuiltinCode)
                .doesNotContain("cinematic-lighting");
    }
}

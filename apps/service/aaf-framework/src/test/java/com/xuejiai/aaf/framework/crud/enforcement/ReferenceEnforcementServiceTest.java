package com.xuejiai.aaf.framework.crud.enforcement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;

import com.xuejiai.aaf.framework.crud.definition.CrudResourceDefinition;
import com.xuejiai.aaf.framework.crud.definition.ResourceKey;
import com.xuejiai.aaf.framework.crud.reference.CrudReferenceDefinition;
import com.xuejiai.aaf.framework.crud.reference.EntityReferenceAccess;
import com.xuejiai.aaf.framework.crud.reference.ReferenceCapability;
import com.xuejiai.aaf.framework.crud.reference.ReferenceContext;
import com.xuejiai.aaf.framework.crud.reference.ReferencePolicy;
import com.xuejiai.aaf.framework.crud.reference.ReferenceRequest;
import com.xuejiai.aaf.framework.crud.reference.ResourceReference;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceCatalogEntry;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceRegistry;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class ReferenceEnforcementServiceTest extends BaseMockitoUnitTest {

    private static final ResourceKey SOURCE = ResourceKey.of("test.source");

    @Mock private CrudResourceRegistry catalog;
    @Mock private ApplicationContext applicationContext;
    @Mock private OperatorContext operatorContext;
    @Mock private ObjectProvider<EntityReferenceAccess> accessProvider;
    @Mock private EntityReferenceAccess access;
    @Mock private CrudResourceCatalogEntry entry;
    @Mock private CrudResourceDefinition<?> definition;
    @Mock private ReferencePolicy policy;

    private ReferenceEnforcementService service;

    @BeforeEach
    void setUp() {
        service =
                new ReferenceEnforcementService(
                        catalog, applicationContext, operatorContext, accessProvider);
        when(catalog.require(SOURCE)).thenReturn(entry);
        doReturn(definition).when(entry).definition();
        when(accessProvider.getObject()).thenReturn(access);
    }

    @Test
    @DisplayName("Given 未声明附加策略 When 基线允许引用 Then 直接返回允许")
    void should_use_default_baseline_without_business_policy() {
        var request = new ReferenceRequest(10L, new ResourceReference("test.target", 20L));
        when(definition.references()).thenReturn(List.of(reference("")));
        when(access.referenceable(anyCollection())).thenReturn(Set.of(request.target()));

        var allowed = service.referenceable(SOURCE, "target", List.of(request));

        assertThat(allowed).containsExactly(request);
        verifyNoInteractions(applicationContext);
    }

    @Test
    @DisplayName("Given 附加策略 When 基线允许两条但策略保留一条 Then 结果只能收紧")
    void should_only_narrow_baseline_with_additional_policy() {
        var first = new ReferenceRequest(10L, new ResourceReference("test.target", 20L));
        var second = new ReferenceRequest(11L, new ResourceReference("test.target", 21L));
        when(definition.references()).thenReturn(List.of(reference("constraintPolicy")));
        when(access.referenceable(anyCollection()))
                .thenReturn(Set.of(first.target(), second.target()));
        when(applicationContext.getBean("constraintPolicy", ReferencePolicy.class))
                .thenReturn(policy);
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(7L));
        when(policy.filterReferenceable(org.mockito.ArgumentMatchers.anySet()))
                .thenAnswer(
                        invocation -> {
                            Set<ReferenceContext> contexts = invocation.getArgument(0);
                            return contexts.stream()
                                    .filter(context -> context.sourceId().equals(first.sourceId()))
                                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
                        });

        var allowed = service.referenceable(SOURCE, "target", List.of(first, second));

        assertThat(allowed).containsExactlyInAnyOrder(first);
    }

    private CrudReferenceDefinition reference(String policyBean) {
        return new CrudReferenceDefinition(
                "target",
                "targetId",
                ResourceKey.of("test.target"),
                "",
                "targetId",
                "target",
                policyBean,
                Set.of(ReferenceCapability.READ, ReferenceCapability.REFERENCE));
    }
}

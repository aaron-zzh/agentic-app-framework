package com.xuejiai.aaf.module.ai.aigc.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.framework.crud.definition.CrudOperation;
import com.xuejiai.aaf.framework.crud.enforcement.AccessMode;
import com.xuejiai.aaf.framework.crud.enforcement.CompiledFieldPolicy;
import com.xuejiai.aaf.framework.crud.enforcement.CrudEnforcementDecision;
import com.xuejiai.aaf.framework.crud.enforcement.CrudEnforcementService;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceCatalogEntry;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceRegistry;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProject;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcContentRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectDocRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcStoryboardRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcTimelineRepository;
import com.xuejiai.aaf.module.document.repository.DocumentRepository;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class AigcProjectServiceTest extends BaseMockitoUnitTest {

    @Mock private AigcProjectRepository repository;
    @Mock private AigcStoryboardRepository storyboardRepository;
    @Mock private AigcTimelineRepository timelineRepository;
    @Mock private AigcContentRepository contentRepository;
    @Mock private AigcProjectDocRepository projectDocRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private OperatorContext operatorContext;
    @Mock private CrudResourceRegistry crudResourceRegistry;
    @Mock private CrudResourceCatalogEntry resourceEntry;
    @Mock private CrudEnforcementService crudEnforcementService;

    @InjectMocks private AigcProjectService service;

    private AigcProject ownerProject;

    @BeforeEach
    void setUp() {
        // operatorContext 是 @Autowired 字段（非 final），@RequiredArgsConstructor 不会注入它，
        // Mockito @InjectMocks 选中构造器注入后也不再补做字段注入，需手动 setField
        ReflectionTestUtils.setField(service, "operatorContext", operatorContext);
        ReflectionTestUtils.setField(service, "crudResourceRegistry", crudResourceRegistry);
        ReflectionTestUtils.setField(service, "crudEnforcementService", crudEnforcementService);

        ownerProject = new AigcProject();
        ownerProject.setId(1L);
        ownerProject.setName("用户A的项目");
        ownerProject.setCoverUrl("/covers/project-a.png");
        ownerProject.setDescription("用户A的项目描述");
        ownerProject.setPrompt("用户A的项目提示词");
        ownerProject.setUserId(10L);
    }

    @Test
    @DisplayName("Given 项目属于用户A When 用户A 调用 getByIdOwned Then 返回项目详情")
    void getByIdOwned_owner_returns_vo() {
        prepareCrudGetAccess();
        // mock：requireEntity 内部调用 repository.findOne(Specification)
        when(repository.findOne(any(Specification.class))).thenReturn(Optional.of(ownerProject));
        when(operatorContext.currentUserId()).thenReturn(Optional.of(10L));

        // 调用
        var vo = service.getByIdOwned(1L);

        // 断言
        assertThat(vo.getId()).isEqualTo(1L);
        assertThat(vo.getName()).isEqualTo("用户A的项目");
    }

    @Test
    @DisplayName("Given 项目属于用户A When 用户B 调用 getByIdOwned Then 抛出 BusinessException（404 语义，防探测）")
    void getByIdOwned_otherUser_throws() {
        prepareCrudGetAccess();
        // mock：项目属于 userId=10，但当前用户是 userId=99
        when(repository.findOne(any(Specification.class))).thenReturn(Optional.of(ownerProject));
        when(operatorContext.currentUserId()).thenReturn(Optional.of(99L));

        // 调用 + 断言：应抛 BusinessException（404 语义）
        assertThatThrownBy(() -> service.getByIdOwned(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目不存在");
    }

    @Test
    @DisplayName("Given 项目属于用户A When 用户B 进入 DELETE hook Then 抛出 BusinessException（404 语义，防探测）")
    void beforeDelete_otherUser_throws() {
        // mock
        when(operatorContext.currentUserId()).thenReturn(Optional.of(99L));

        // 调用 + 断言
        assertThatThrownBy(() -> service.beforeDelete(ownerProject))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目不存在");
    }

    private void prepareCrudGetAccess() {
        var decision = decision();
        when(crudResourceRegistry.requireByEntityType(AigcProject.class)).thenReturn(resourceEntry);
        when(crudEnforcementService.<AigcProject>enforceObjectPreflight(
                        resourceEntry, CrudOperation.GET, AccessMode.DEFAULT))
                .thenReturn(decision);
        when(crudEnforcementService.allowsCurrentTarget(any(), any(), any(), any(), any()))
                .thenReturn(true);
    }

    private CrudEnforcementDecision<AigcProject> decision() {
        Specification<AigcProject> unrestricted = (root, query, builder) -> null;
        return new CrudEnforcementDecision<>(
                1L,
                1L,
                null,
                CrudOperation.GET,
                AccessMode.DEFAULT,
                unrestricted,
                unrestricted,
                new CompiledFieldPolicy(Map.of()),
                "test-version");
    }
}

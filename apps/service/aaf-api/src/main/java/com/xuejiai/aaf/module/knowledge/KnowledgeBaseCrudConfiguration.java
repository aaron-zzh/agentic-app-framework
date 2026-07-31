package com.xuejiai.aaf.module.knowledge;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xuejiai.aaf.framework.crud.definition.PersonalScope;
import com.xuejiai.aaf.framework.crud.definition.TenantScope;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitionProvider;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitions;
import com.xuejiai.aaf.module.knowledge.controller.KnowledgeBaseController;

/** 知识库 CRUD 资源注册。 */
@Configuration(proxyBeanMethods = false)
public class KnowledgeBaseCrudConfiguration {

    @Bean
    CrudResourceDefinitionProvider<?> knowledgeBaseResource() {
        return CrudResourceDefinitions.crud(
                "knowledge.knowledge-base",
                "知识库",
                KnowledgeBaseController.class,
                "/api/knowledge-bases",
                "system:knowledge-base",
                TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                PersonalScope.byProperty("ownerId"));
    }
}

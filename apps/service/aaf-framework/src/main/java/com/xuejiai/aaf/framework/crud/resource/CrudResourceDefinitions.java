package com.xuejiai.aaf.framework.crud.resource;

import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Sort;

import com.xuejiai.aaf.framework.crud.definition.*;
import com.xuejiai.aaf.framework.crud.definition.CrudCapabilityDefinition;
import com.xuejiai.aaf.framework.crud.definition.CrudResourceDefinition;
import com.xuejiai.aaf.framework.crud.filter.CrudFilterSchema;

/**
 * 根据标准 CRUD Controller 创建资源定义和端点绑定。
 *
 * <p>返回的 {@link CrudResourceDefinitionProvider} 会在应用启动时由 {@link CrudResourceCompiler}
 * 收集、校验并发布到资源目录。
 *
 * <p>用于为没有自定义字段能力、查询、关系、引用或输出视图的资源生成静态契约。工厂从 Controller
 * 泛型推导实体、输入、输出和分页类型，并生成与端点类型匹配的默认能力、变更规则、输出视图和字段能力。
 *
 * <p>{@link #crud(String, String, Class, String, String, TenantScope)} 及其重载按配置复杂度逐步开放：
 * 基础重载使用无个人范围和默认暴露面；后续重载可声明个人范围或自定义暴露面。需要自定义查询、字段能力、
 * 关系、引用策略或输出视图时，应显式实现 {@link CrudResourceDefinitionProvider}。
 */
public final class CrudResourceDefinitions {

    private CrudResourceDefinitions() {}

    /**
     * 创建无个人范围、使用默认暴露面的标准 CRUD 资源 Provider。
     *
     * <p> 暴露面 表示可发现资源的受信任框架入口，不代表当前用户已获得访问权限。默认暴露面为 HTTP、
     * 实体定义和跨资源引用。完整参数说明见
     * {@link #crud(String, String, Class, String, String, TenantScope, PersonalScope, Set)}。
     */
    public static CrudResourceDefinitionProvider<?> crud(
            String key,
            String label,
            Class<?> controllerType,
            String apiPath,
            String permissionNamespace,
            TenantScope tenantScope) {
        return crud(
                key,
                label,
                controllerType,
                apiPath,
                permissionNamespace,
                tenantScope,
                PersonalScope.none());
    }

    /**
     * 创建使用指定个人范围的标准完整 CRUD 资源 Provider。
     *
     * <p>默认向 HTTP、实体定义和跨资源引用暴露资源；需要调整消费者暴露面时，使用包含
     * {@code exposures} 参数的重载。完整参数说明见
     * {@link #crud(String, String, Class, String, String, TenantScope, PersonalScope, Set)}。
     */
    public static CrudResourceDefinitionProvider<?> crud(
            String key,
            String label,
            Class<?> controllerType,
            String apiPath,
            String permissionNamespace,
            TenantScope tenantScope,
            PersonalScope personalScope) {
        return crud(
                key,
                label,
                controllerType,
                apiPath,
                permissionNamespace,
                tenantScope,
                personalScope,
                Set.of(
                        CrudResourceExposure.HTTP,
                        CrudResourceExposure.ENTITY_DEF,
                        CrudResourceExposure.REFERENCE));
    }

    /**
     * 创建可完整定制个人范围和暴露面的标准 CRUD 资源 Provider。
     *
     * <p>这是标准资源重载的最终入口。它仍使用框架默认的查询、变更、输出视图和字段能力；
     * {@code exposures} 仅控制哪些受信任消费者可发现该资源，不会授予未声明的字段或操作能力。
     *
     * @param key 带命名空间的稳定资源标识
     * @param label 资源显示名称
     * @param controllerType 标准 CRUD Controller 类型，用于推导资源类型和校验端点
     * @param apiPath 资源 API 路径
     * @param permissionNamespace 资源权限命名空间
     * @param tenantScope 资源租户范围
     * @param personalScope 个人范围规则
     * @param exposures 允许发现资源的受信任消费者暴露面
     * @return 已生成静态资源定义和端点绑定的 Provider
     */
    public static CrudResourceDefinitionProvider<?> crud(
            String key,
            String label,
            Class<?> controllerType,
            String apiPath,
            String permissionNamespace,
            TenantScope tenantScope,
            PersonalScope personalScope,
            Set<CrudResourceExposure> exposures) {
        var resourceKey = ResourceKey.of(key);
        return provider(
                CrudResourceEndpointBinding.crud(resourceKey, controllerType, apiPath),
                label,
                permissionNamespace,
                tenantScope,
                exposures,
                personalScope);
    }

    /**
     * 创建仅通过嵌套 HTTP 路由访问的 CRUD 资源 Provider。
     *
     * <p>该资源必须由 {@link com.xuejiai.aaf.framework.crud.web.NestedCrudResourceController}
     * 实现端点，并且默认仅向 HTTP 暴露，不会进入实体定义或跨资源引用目录。
     *
     * @param key 带命名空间的稳定资源标识
     * @param label 资源显示名称
     * @param controllerType 嵌套 CRUD Controller 类型，用于推导资源类型和校验端点
     * @param apiPath 资源 API 路径
     * @param permissionNamespace 资源权限命名空间
     * @param tenantScope 资源租户范围
     * @param personalScope 个人范围规则
     * @return 已生成静态资源定义和端点绑定的 Provider
     */
    public static CrudResourceDefinitionProvider<?> nestedCrud(
            String key,
            String label,
            Class<?> controllerType,
            String apiPath,
            String permissionNamespace,
            TenantScope tenantScope,
            PersonalScope personalScope) {
        var resourceKey = ResourceKey.of(key);
        return provider(
                CrudResourceEndpointBinding.nestedCrud(resourceKey, controllerType, apiPath),
                label,
                permissionNamespace,
                tenantScope,
                Set.of(CrudResourceExposure.HTTP),
                personalScope);
    }

    /**
     * 创建仅提供 {@code GET /_options} 选择器能力的资源 Provider。
     *
     * <p>该资源必须由 {@link com.xuejiai.aaf.framework.crud.web.ResourceOptionsController}
     * 实现端点。工厂只生成选择器所需的能力和输出视图，默认不设置个人范围，并向 HTTP 和实体定义暴露。
     *
     * @param key 带命名空间的稳定资源标识
     * @param label 资源显示名称
     * @param controllerType 选择器 Controller 类型，用于推导资源类型和校验端点
     * @param apiPath 资源 API 路径
     * @param permissionNamespace 资源权限命名空间
     * @param tenantScope 资源租户范围
     * @return 已生成静态资源定义和端点绑定的 Provider
     */
    public static CrudResourceDefinitionProvider<?> options(
            String key,
            String label,
            Class<?> controllerType,
            String apiPath,
            String permissionNamespace,
            TenantScope tenantScope) {
        var resourceKey = ResourceKey.of(key);
        return provider(
                CrudResourceEndpointBinding.options(resourceKey, controllerType, apiPath),
                label,
                permissionNamespace,
                tenantScope,
                Set.of(
                        CrudResourceExposure.HTTP,
                        CrudResourceExposure.ENTITY_DEF,
                        CrudResourceExposure.REFERENCE),
                PersonalScope.none());
    }

    /**
     * 在标准资源 Provider 上叠加具名自定义 UPDATE 命令契约。
     *
     * <p>具名命令复用资源 UPDATE permission，但不会把只读资源伪装成支持标准 PUT；调用必须经过
     * {@link com.xuejiai.aaf.framework.crud.BaseCrudService} 的固定命令模板。
     */
    public static CrudResourceDefinitionProvider<?> withCustomUpdateCommands(
            CrudResourceDefinitionProvider<?> provider,
            java.util.Map<String, Set<String>> customUpdateCommands) {
        return withCustomUpdateCommandsCaptured(provider, customUpdateCommands);
    }

    private static <E extends com.xuejiai.aaf.common.model.BaseEntity>
            CrudResourceDefinitionProvider<E> withCustomUpdateCommandsCaptured(
                    CrudResourceDefinitionProvider<E> provider,
                    java.util.Map<String, Set<String>> customUpdateCommands) {
        var definition = provider.definition().withCustomUpdateCommands(customUpdateCommands);
        return new StaticCrudResourceDefinitionProvider<>(definition, provider.endpointBinding());
    }

    /** 根据端点类型创建默认子契约，并封装为静态 Provider。 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static CrudResourceDefinitionProvider<?> provider(
            CrudResourceEndpointBinding endpoint,
            String label,
            String permissionNamespace,
            TenantScope tenantScope,
            Set<CrudResourceExposure> exposures,
            PersonalScope personalScope) {
        var types =
                CrudResourceTypeContract.fromEndpoint(endpoint.controllerType(), endpoint.kind());
        var optionsOnly = endpoint.kind() == CrudResourceEndpointKind.OPTIONS;
        var capabilities =
                optionsOnly
                        ? CrudCapabilityDefinition.optionsOnly()
                        : CrudCapabilityDefinition.forTypes(types);
        var view =
                optionsOnly
                        ? CrudViewDefinition.optionsOnly(types)
                        : CrudViewDefinition.forTypes(types);
        var query =
                new CrudQueryDefinition(
                        CrudFilterSchema.empty(),
                        optionsOnly ? Set.of() : Set.of("id"),
                        Sort.by("id").descending());
        var definition =
                new CrudResourceDefinition(
                        endpoint.resourceKey(),
                        types,
                        new CrudResourceDescriptor(label, endpoint.apiPath(), permissionNamespace),
                        capabilities,
                        query,
                        CrudMutationDefinition.forTypes(types),
                        view,
                        List.of(),
                        tenantScope,
                        personalScope,
                        exposures,
                        CrudResourceDefinition.CURRENT_SCHEMA_VERSION);
        return new StaticCrudResourceDefinitionProvider(definition, endpoint);
    }
}

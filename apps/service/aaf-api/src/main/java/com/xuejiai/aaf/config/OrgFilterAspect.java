package com.xuejiai.aaf.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.GenericTypeResolver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.org.OrgIgnore;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Table;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AOP 切面：在 Repository 方法执行前自动启用组织过滤器。
 *
 * <p>安全约束：orgId 缺失时 fail-closed（拒绝访问），不再静默跳过过滤器直接放行全部组织数据。 唯一例外是 {@link
 * #ORG_LIST_PATH}——该接口用于登录后查询"当前用户属于哪些组织"，语义上基于 userId 查询、不依赖 orgId，必须豁免，否则用户在拿到 orgId
 * 之前无法查到自己的组织列表（死锁）。
 *
 * <p>B1：切面覆盖范围从"包路径匹配"改为"类型匹配"——原 pointcut 是 {@code execution(* module..repository.*.*(..)) ||
 * execution(* framework..repository.*.*(..))}， 只命中放在 {@code repository} 子包下的仓储；framework
 * 层大量仓储不遵循这一约定（如 {@code engine/credit/ CreditAccountRepository}、{@code
 * intelligent/core/model/AiModelRepository}），完全绕过本切面。 现在改为对任意 {@code JpaRepository}
 * 子接口生效（不看包路径），一次性覆盖此前遗漏的全部仓储。为此已将其中 13 个 全局配置类实体补标 {@link
 * OrgIgnore}（AgentDefinition/AiAssistantRole/AiModelProvider/ModelPreference/
 * CreditAccount/CreditTransaction/PromptTemplate/Persona/ValueRule/PermissionTuple/TeamEntity/
 * AccessPolicy/AccessPolicySnapshot/AuthorizationAudit），避免切面扩面后把这些表误套组织过滤导致查询静默返回空。
 *
 * <p>后台任务（{@code @Scheduled}）运行在无 HTTP 请求上下文的独立线程，{@code OrgContext} 中不会有 orgId，需要在方法/类上显式加
 * {@code @OrgIgnore} 声明豁免，否则会被 fail-closed 拒绝——不能像 HTTP 场景一样用 URL 白名单处理。
 *
 * <p>全局配置类实体（如 {@code Role}/{@code PermissionCode}/{@code UserRole}/{@code RolePermission}）
 * 语义上不属于任何组织，其 {@code org_id} 列恒为 NULL——若被套用 {@code orgFilter}（{@code WHERE org_id = ?}）， SQL 中
 * {@code NULL = :orgId} 永远不成立，会导致查询静默返回空、权限判断全部失效（比 fail-closed 更隐蔽的回归）。 这类实体需在实体类上标注
 * {@code @OrgIgnore}，本切面通过反射解析 repository 的实体类型并按类型缓存判断结果， 不管调用方是谁、有没有 orgId，命中即跳过过滤器且不
 * fail-closed。
 *
 * <p>运行时防御性检测：启用过滤器的查询若返回空结果，且该实体对应表中存在 {@code org_id} 全部为 NULL 的记录 （即该表实际是全局配置数据，只是漏标了
 * {@code @OrgIgnore}），记录警告日志辅助排查——这类问题不会抛异常， 只是查询结果静默为空，人工排查成本很高（参见 SysMenu 漏标 @OrgIgnore 导致
 * /my-tree 返回空的真实案例）。 探测结果按实体类缓存，避免重复探测拖累性能；只在"怀疑有问题"（结果为空）时才触发一次探测查询。
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class OrgFilterAspect {

    /** 登录后查询用户所属组织列表的接口，基于 userId 查询，不依赖 orgId，需豁免组织过滤强制要求。 */
    private static final String ORG_LIST_PATH = "/api/system/orgs";

    private final EntityManager entityManager;

    /** repository 接口 → 其实体类型是否标注 @OrgIgnore，缓存避免每次调用重复反射解析泛型参数。 */
    private final Map<Class<?>, Boolean> globalEntityCache = new ConcurrentHashMap<>();

    /** repository 接口 → 其实体类型，配合疑似漏标探测使用，避免重复反射解析。 */
    private final Map<Class<?>, Class<?>> entityTypeCache = new ConcurrentHashMap<>();

    /** 已探测过的实体类型，避免同一实体重复触发探测查询（探测结果不会随运行时变化，进程内缓存一次即可）。 */
    private final Map<Class<?>, Boolean> suspectedMisconfigCache = new ConcurrentHashMap<>();

    @Around(
            "this(org.springframework.data.jpa.repository.JpaRepository) && "
                    + "within(com.xuejiai.aaf..*)")
    public Object enableOrgFilter(ProceedingJoinPoint joinPoint) throws Throwable {
        var session = entityManager.unwrap(org.hibernate.Session.class);
        if (OrgContext.isIgnore() || isGlobalEntityRepository(joinPoint)) {
            // 显式声明豁免（如全局性后台任务，见 @OrgIgnore）或目标实体本身是全局配置类型，
            // 不启用过滤器也不 fail-closed；同一 Session 可能被前序调用启用过 orgFilter
            // （Hibernate Filter 状态绑定在 Session 而非单次查询上），此处必须显式关闭，
            // 否则会残留污染本次本应豁免的查询，导致全局配置类实体被误套 org_id 条件。
            session.disableFilter("orgFilter");
            return joinPoint.proceed();
        }
        var orgId = OrgContext.getCurrentOrgId();
        if (orgId != null) {
            session.enableFilter("orgFilter").setParameter("orgId", orgId);
            var result = joinPoint.proceed();
            checkSuspectedMisconfig(joinPoint, result);
            return result;
        }
        if (isOrgListRequest()) {
            // 白名单：不启用过滤器，允许按 userId 语义查询，不代表放行其他数据
            session.disableFilter("orgFilter");
            return joinPoint.proceed();
        }
        throw new BusinessException(GlobalErrorCode.FORBIDDEN, "缺少组织上下文，无法访问该资源");
    }

    /**
     * 疑似漏标 {@code @OrgIgnore} 的运行时防御性检测。
     *
     * <p>只在启用了 orgFilter 的查询返回空结果时触发，且每个实体类型只探测一次（进程生命周期内）： 若该表存在记录、但全部记录的 {@code org_id} 均为
     * NULL，说明这本质是全局配置表，orgFilter 的 {@code org_id = ?} 条件必然永远不匹配，与业务预期不符——记录警告日志辅助排查，不阻断请求。
     */
    private void checkSuspectedMisconfig(ProceedingJoinPoint joinPoint, Object result) {
        if (!isEmptyResult(result)) {
            return;
        }
        var entityType = resolveEntityType(joinPoint);
        if (entityType == null || !BaseEntity.class.isAssignableFrom(entityType)) {
            // 非 BaseEntity 子类没有 org_id / deleted 列（如纯 M2M 关联表），
            // 不存在"漏标 @OrgIgnore"场景，探测查询会因列不存在报错，必须跳过。
            return;
        }
        if (suspectedMisconfigCache.containsKey(entityType)) {
            return;
        }
        var isSuspected = OrgContext.runIgnoring(() -> probeAllOrgIdNull(entityType));
        suspectedMisconfigCache.put(entityType, isSuspected);
        if (isSuspected) {
            log.warn(
                    "[OrgFilterAspect] 实体 {} 对应表存在数据但 org_id 全部为 NULL，"
                            + "疑似应标注 @OrgIgnore 却未标注，导致组织过滤条件（org_id = ?）永远不匹配、"
                            + "查询静默返回空。请确认该实体是否为全局配置数据，如是则补充 @OrgIgnore。",
                    entityType.getName());
        }
    }

    /** 判断方法返回值是否为空结果（当前只识别 Collection，Repository 常见的 List/Optional 返回类型）。 */
    private boolean isEmptyResult(Object result) {
        if (result instanceof java.util.Collection<?> collection) {
            return collection.isEmpty();
        }
        if (result instanceof java.util.Optional<?> optional) {
            return optional.isEmpty();
        }
        return false;
    }

    /** 探测该实体对应表是否存在记录、且这些记录的 org_id 全部为 NULL（即疑似全局配置表）。 */
    private boolean probeAllOrgIdNull(Class<?> entityType) {
        var table = entityType.getAnnotation(Table.class);
        if (table == null || table.name().isBlank()) {
            return false;
        }
        try {
            var sql =
                    "SELECT COUNT(*), COUNT(org_id) FROM "
                            + table.name()
                            + " WHERE deleted = FALSE";
            var row = (Object[]) entityManager.createNativeQuery(sql).getSingleResult();
            var total = ((Number) row[0]).longValue();
            var withOrgId = ((Number) row[1]).longValue();
            return total > 0 && withOrgId == 0;
        } catch (Exception e) {
            // 探测失败（如表无 org_id/deleted 列）不影响主流程，仅跳过本次检测
            return false;
        }
    }

    /** 解析当前调用的 repository 接口对应的实体类型（复用 isGlobalEntityRepository 的反射逻辑并缓存）。 */
    private Class<?> resolveEntityType(ProceedingJoinPoint joinPoint) {
        var targetClass = joinPoint.getTarget().getClass();
        return entityTypeCache.computeIfAbsent(
                targetClass,
                tc -> {
                    for (var candidate :
                            org.springframework.core.ResolvableType.forClass(tc).getInterfaces()) {
                        var typeArgs =
                                GenericTypeResolver.resolveTypeArguments(
                                        candidate.resolve(), JpaRepository.class);
                        if (typeArgs != null && typeArgs.length > 0 && typeArgs[0] != null) {
                            return typeArgs[0];
                        }
                    }
                    return null;
                });
    }

    /** 判断当前调用的 repository 接口对应的实体类型是否标注 {@code @OrgIgnore}（全局配置类实体）。 */
    private boolean isGlobalEntityRepository(ProceedingJoinPoint joinPoint) {
        // 不能用 joinPoint.getSignature().getDeclaringType()：对于 findAllById 等
        // repository 基类（如 ListCrudRepository）自带的方法，AOP 拿到的声明类型是该方法
        // 最初声明所在的父接口，而非业务 repository 接口本身，导致解析不到具体实体泛型参数。
        // 改用 joinPoint.getTarget().getClass()（repository 代理实例的运行时类型）反查其实现的、
        // 直接继承 JpaRepository<Entity, ID> 的业务接口，才能稳定解析出实体类型。
        var targetClass = joinPoint.getTarget().getClass();
        return globalEntityCache.computeIfAbsent(targetClass, this::resolveIsGlobalEntity);
    }

    private boolean resolveIsGlobalEntity(Class<?> targetClass) {
        for (var candidate :
                org.springframework.core.ResolvableType.forClass(targetClass).getInterfaces()) {
            var typeArgs =
                    GenericTypeResolver.resolveTypeArguments(
                            candidate.resolve(), JpaRepository.class);
            if (typeArgs != null && typeArgs.length > 0) {
                var entityType = typeArgs[0];
                return entityType != null && entityType.isAnnotationPresent(OrgIgnore.class);
            }
        }
        return false;
    }

    /** 判断当前请求是否是查询用户组织列表的白名单接口（GET /api/system/orgs，不含子路径）。 */
    private boolean isOrgListRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
            return false;
        }
        var request = servletAttrs.getRequest();
        return "GET".equalsIgnoreCase(request.getMethod())
                && ORG_LIST_PATH.equals(request.getRequestURI());
    }
}

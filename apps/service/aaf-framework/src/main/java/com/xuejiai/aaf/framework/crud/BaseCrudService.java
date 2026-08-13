package com.xuejiai.aaf.framework.crud;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.GenericTypeResolver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.crud.definition.*;
import com.xuejiai.aaf.framework.crud.dto.*;
import com.xuejiai.aaf.framework.crud.enforcement.*;
import com.xuejiai.aaf.framework.crud.export.DictFormat;
import com.xuejiai.aaf.framework.crud.export.DictLabelResolver;
import com.xuejiai.aaf.framework.crud.filter.CrudFilter;
import com.xuejiai.aaf.framework.crud.filter.CrudFilterSchema;
import com.xuejiai.aaf.framework.crud.filter.FilterEvaluationContext;
import com.xuejiai.aaf.framework.crud.relation.*;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceCatalogEntry;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceRegistry;
import com.xuejiai.aaf.framework.crud.view.CrudViewData;
import com.xuejiai.aaf.framework.crud.view.CrudViewMapper;
import com.xuejiai.aaf.framework.crud.view.CrudViewPlan;
import com.xuejiai.aaf.framework.engine.entitlement.EntitlementChecker;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationPlan;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationRequest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Table;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.metamodel.Attribute;

/**
 * 标准代码资源的 CRUD 业务基类。
 *
 * <p>业务子类继承本类后，可以直接获得分页、详情、创建、更新、删除、选择器、导出、字段集、关系同步和输出视图等通用能力。
 * 所有公开入口都会经过统一安全管线：先校验操作权限、租户和数据范围，再校验字段权限，最后查询或保存数据。 因此业务子类不需要、也不应自行补写这些通用安全步骤。
 *
 * <p><b>子类必须实现：</b>
 *
 * <ul>
 *   <li>{@link #getRepository()}：返回当前实体的 Repository；
 *   <li>{@link #toVO(BaseEntity)}：把单个实体转换为最基础的输出对象；
 *   <li>{@link #toEntity(Object)}：把创建请求转换为新实体；
 *   <li>{@link #updateEntity(BaseEntity, Object)}：把更新请求中的业务字段应用到已有实体。
 * </ul>
 *
 * <p><b>业务可按需覆写：</b>{@link #extractOwnerId(BaseEntity)}、{@link #buildSpec(PageParam)}、 {@link
 * #toVO(BaseEntity, String)}、{@link #toVOList(List, String)}、{@link #buildOptionSpec(String)}、
 * {@link #optionSearchFields()}、{@link #optionDisplayFields()}、{@link #displayName(BaseEntity)}、
 * {@link #entitlementCode()}、{@link #importRows(Object)}、{@link #group(CrudGroupRequestDTO)}、
 * {@link #archive(List)} 和 {@link #restore(List)}。这些扩展点用于补充领域规则或展示方式，不能代替安全校验。
 *
 * <p><b>不要覆写或绕过：</b>统一 enforcement、租户赋值与校验、字段读写权限、QueryToken、关系同步、
 * 输出视图计划和最终字段裁剪。业务需要特殊入口时，应使用本类提供的带 {@link AccessMode} 的 {@code final} 方法， 让特殊场景仍然进入统一安全管线。
 *
 * @param <E> 实体类型，必须继承 {@link BaseEntity}
 * @param <V> 输出视图类型，通常为 VO
 * @param <C> 创建请求 DTO 类型
 * @param <U> 更新请求 DTO 类型
 * @param <P> 分页查询 DTO 类型，必须继承 {@link PageParam}
 */
public abstract class BaseCrudService<E extends BaseEntity, V, C, U, P extends PageParam> {

    /** 批量读取单次最大条数，防止超长 IN 子句拖垮数据库 */
    protected static final int BATCH_READ_MAX_SIZE = 200;

    /** 按实体类型解析 CRUD 资源元数据及其快照。 */
    @Autowired private CrudResourceRegistry crudResourceRegistry;

    /** 在 CRUD 操作前执行统一的访问与字段权限约束。 */
    @Autowired private CrudEnforcementService crudEnforcementService;

    /** 为查询结果签发并校验防篡改的查询令牌。 */
    @Autowired private QueryTokenService queryTokenService;

    /** 提供当前操作者身份，用于归属及权限相关判断。 */
    @Autowired private OperatorContext operatorContext;

    /** 按资源配置的 Bean 名称动态获取自定义展示组件。 */
    @Autowired private ApplicationContext applicationContext;

    @Autowired private ReferenceEnforcementService referenceEnforcementService;

    @Autowired private GenericRelationHandler genericRelationHandler;

    @Autowired private GenericRelationLoader genericRelationLoader;

    @Autowired(required = false)
    private ObjectProvider<DictLabelResolver> dictLabelResolver;

    @Autowired(required = false)
    private EntitlementChecker entitlementChecker;

    @PersistenceContext private EntityManager entityManager;

    /**
     * 返回当前资源的数据访问入口。
     *
     * <p><b>必须由业务子类实现。</b>
     */
    protected abstract CrudEntityRepository<E> getRepository();

    /**
     * 将一个实体转换为基础输出 VO 对象
     *
     * <p><b>必须由业务子类实现。</b>适用于不声明 {@link CrudViewMapper} 的简单资源。
     */
    protected abstract V toVO(E entity);

    /**
     * 将创建请求转换为尚未保存的新实体
     *
     * <p><b>必须由业务子类实现。</b>在这里复制业务字段、设置业务默认值即可。框架自动设置组织、工作区、owner 或关系数据。
     */
    protected abstract E toEntity(C createDTO);

    /**
     * 更新实体
     *
     * <p><b>必须由业务子类实现。</b>只修改本资源的业务字段
     */
    protected abstract void updateEntity(E entity, U updateDTO);

    /**
     * UPDATE 已按完整 L3 范围加载实体后、固定 CURRENT 快照前执行的受控 Hook。
     *
     * <p>仅用于刷新实体或获取数据库锁，不得修改业务字段。对象级 L4 仍由框架在 {@link #updateEntity(BaseEntity, Object)} 应用变更后统一执行。
     */
    protected void beforeUpdate(E entity, U updateDTO) {}

    /** DELETE 已完成 CURRENT 对象授权后、执行删除前的受控业务 Hook。 */
    protected void beforeDelete(E entity) {}

    /** DELETE 主实体删除后、事务提交前的受控业务 Hook。 */
    protected void afterDelete(E entity) {}

    /**
     * 声明单条读取可使用的关系权限要求。
     *
     * <p><b>默认不声明。</b>只有确实支持协作者读取的资源才可覆写，并且只能为 {@link CrudOperation#GET} 返回与当前记录 ID 绑定的关系要求。该要求仅在默认
     * L3 范围未命中后使用，不能用于更新、删除或非默认访问模式。
     */
    protected AuthorizationPlan.RelationRequirement relationRequirement(
            Long id, CrudOperation operation) {
        return null;
    }

    /**
     * 根据业务子类声明的实体泛型，定位该实体唯一的已编译资源目录条目
     *
     * <p>这是框架内部步骤。它保证后续操作使用正确的资源定义、字段策略和输出视图计划；业务子类不需要调用或覆写。
     */
    @SuppressWarnings("unchecked")
    private CrudResourceCatalogEntry resourceEntry() {
        var types = GenericTypeResolver.resolveTypeArguments(getClass(), BaseCrudService.class);
        if (types == null || types.length == 0) {
            throw new IllegalStateException("无法解析 CRUD Service 实体类型: " + getClass().getName());
        }
        return crudResourceRegistry.requireByEntityType(types[0]);
    }

    /**
     * 返回当前资源的静态定义。
     *
     * <p>资源定义保存字段集、操作能力、租户范围、关系和输出视图等固定规则。业务子类应通过定义声明能力， 不要在运行时构造替代定义。
     */
    @SuppressWarnings("unchecked")
    private CrudResourceDefinition<E> resourceDefinition() {
        return (CrudResourceDefinition<E>) resourceEntry().definition();
    }

    /**
     * 按默认访问模式进入请求级安全管线。
     *
     * <p>列表、批量和无对象操作使用此入口，只执行一次请求级 L4，绝不按返回记录逐行评估。
     */
    private CrudEnforcementDecision<E> enforce(CrudOperation operation) {
        return enforce(operation, AccessMode.DEFAULT);
    }

    /** 为业务内部请求级场景进入统一安全管线。 */
    protected final CrudEnforcementDecision<E> enforce(
            CrudOperation operation, AccessMode accessMode) {
        return crudEnforcementService.enforceRequest(resourceEntry(), operation, accessMode);
    }

    /** 单对象 CRUD 的前置安全管线；对象加载前只执行 L1/L3。 */
    private CrudEnforcementDecision<E> enforceObject(
            CrudOperation operation, AccessMode accessMode) {
        return crudEnforcementService.enforceObjectPreflight(
                resourceEntry(), operation, accessMode);
    }

    /**
     * 返回实体所有者的用户 ID，供业务额外执行“仅本人可操作”规则。
     *
     * <p><b>可按需覆写。</b>默认返回 {@code null}，表示本资源不额外要求所有者匹配。只有实体确实有明确所有者字段，
     * 且业务入口需要所有者语义时才覆写；通用数据权限仍由统一安全管线负责。
     */
    protected Long extractOwnerId(E entity) {
        return null;
    }

    /**
     * 为对象级 L4 增补当前资源的纯数据属性。
     *
     * <p>默认暴露 {@link BaseEntity} 的安全字段。覆写时必须从 {@code super} 结果增补业务标量、数组或嵌套 Map，
     * 不得删除、覆盖安全字段，也不得返回实体、DTO 或其他可变对象。框架会在进入 PDP 前统一加 {@code crud.*} 前缀并深复制校验。
     */
    protected Map<String, Object> authorizationAttributes(E entity) {
        return authorizationAttributesFromBaseEntity(entity);
    }

    private void putAuthorizationAttribute(
            Map<String, Object> attributes, String name, Object value) {
        if (value != null) {
            attributes.put(name, value);
        }
    }

    /** 固定一次可信授权快照，并强制业务 Hook 只能增补 BaseEntity 安全字段。 */
    private Map<String, Object> authorizationSnapshot(E entity) {
        var required = baseAuthorizationAttributes(entity);
        var declared = authorizationAttributes(entity);
        if (declared == null) {
            throw new IllegalStateException("authorizationAttributes 不得返回 null");
        }
        for (var securityField : baseAuthorizationFieldNames()) {
            if (required.containsKey(securityField)) {
                if (!Objects.equals(required.get(securityField), declared.get(securityField))) {
                    throw new IllegalStateException(
                            "authorizationAttributes 不得删除或覆盖安全字段: " + securityField);
                }
            } else if (declared.containsKey(securityField)) {
                throw new IllegalStateException(
                        "authorizationAttributes 不得伪造空安全字段: " + securityField);
            }
        }
        try {
            return AuthorizationRequest.immutableData(declared);
        } catch (RuntimeException cause) {
            throw exception(GlobalErrorCode.FORBIDDEN);
        }
    }

    private Map<String, Object> baseAuthorizationAttributes(E entity) {
        return authorizationAttributesFromBaseEntity(entity);
    }

    private Map<String, Object> authorizationAttributesFromBaseEntity(E entity) {
        Objects.requireNonNull(entity, "entity");
        var attributes = new LinkedHashMap<String, Object>();
        putAuthorizationAttribute(attributes, "id", entity.getId());
        putAuthorizationAttribute(attributes, "version", entity.getVersion());
        putAuthorizationAttribute(attributes, "orgId", entity.getOrgId());
        putAuthorizationAttribute(attributes, "workspaceId", entity.getWorkspaceId());
        putAuthorizationAttribute(attributes, "createBy", entity.getCreateBy());
        putAuthorizationAttribute(attributes, "createByType", entity.getCreateByType());
        putAuthorizationAttribute(attributes, "updateBy", entity.getUpdateBy());
        putAuthorizationAttribute(attributes, "updateByType", entity.getUpdateByType());
        putAuthorizationAttribute(attributes, "ownerId", entity.getOwnerId());
        putAuthorizationAttribute(attributes, "deleted", entity.getDeleted());
        return Map.copyOf(attributes);
    }

    private Set<String> baseAuthorizationFieldNames() {
        return Set.of(
                "id",
                "version",
                "orgId",
                "workspaceId",
                "createBy",
                "createByType",
                "updateBy",
                "updateByType",
                "ownerId",
                "deleted");
    }

    /**
     * 追加当前资源特有的查询条件。
     *
     * <p><b>可按需覆写。</b>例如按标题、状态或领域分类筛选。这里只能追加业务条件；租户、记录范围、个人视角和客户端筛选条件 已由框架合并，不能在这里移除或替代。
     */
    protected Specification<E> buildSpec(P pageDTO) {
        return (root, query, cb) -> null;
    }

    /**
     * 构建普通列表的关键词搜索条件。
     *
     * <p><b>可按需覆写。</b>默认复用选择器的规范字段搜索；资源需要全文索引、拼音或组合名称搜索时可追加领域条件。
     */
    protected Specification<E> buildSearchSpec(String keyword) {
        return buildDefaultSearchSpec(keyword);
    }

    /** 返回资源定义中声明的筛选字段和操作符白名单 */
    private CrudFilterSchema<E> filterSchema() {
        return resourceDefinition().query().filterSchema();
    }

    /** 将客户端筛选条件转换为安全的 Specification，只允许资源定义中声明的字段和操作符 */
    private Specification<E> buildFilterSpec(
            List<CrudFilter> filters, FilterEvaluationContext context) {
        return filterSchema().build(filters, context);
    }

    /**
     * 合并本次查询必须同时满足的全部条件。
     *
     * <p>顺序包含统一安全范围、客户端筛选、关键词搜索和业务子类追加条件。此方法保持为私有，避免业务代码漏掉租户或数据范围。
     */
    private Specification<E> buildEffectiveSpec(
            P pageDTO, List<CrudFilter> filters, CrudEnforcementDecision<E> decision) {
        var filterContext = FilterEvaluationContext.create(Clock.systemUTC(), ZoneOffset.UTC);
        return Specification.allOf(
                decision.scopeSpecification(),
                buildFilterSpec(filters, filterContext),
                buildSearchSpec(pageDTO.getSearch()),
                buildSpec(pageDTO));
    }

    /** 返回资源定义声明的默认排序；业务子类不应再通过覆写静态 Hook 改变它 */
    private Sort defaultSort() {
        return resourceDefinition().query().defaultSort();
    }

    /**
     * 将分页参数转换为 Pageable，并拒绝当前用户无排序权限的字段。
     *
     * <p>这是统一安全步骤，业务子类不需要自行校验排序字段。
     */
    private Pageable buildPageable(P request, CrudEnforcementDecision<E> decision) {
        var requestedSort = request.buildSort();
        for (var order : requestedSort) {
            decision.fieldPolicy().require(order.getProperty(), FieldCapability.SORT);
        }
        return request.toPageable(defaultSort(), requestedSort);
    }

    /**
     * 返回默认列表字段集的分页结果。
     *
     * <p>适合只需要“当前页数据 + 总数”的简单列表。框架会自动合并租户、数据范围、业务查询条件、排序权限和字段读取权限。 业务子类通常不覆写；需要额外筛选条件时覆写 {@link
     * #buildSpec(PageParam)}。
     */
    public PageResult<V> page(P request) {
        var decision = enforce(CrudOperation.PAGE);
        var pageable = buildPageable(request, decision);
        Page<E> page =
                getRepository().findAll(buildEffectiveSpec(request, List.of(), decision), pageable);
        return new PageResult<>(
                toViews(page.getContent(), "list", decision),
                page.getTotalElements(),
                request.getPageNo(),
                request.getPageSize(),
                List.of(),
                null,
                null,
                page.hasNext());
    }

    /**
     * 查询可导航的列表窗口，并返回记录 ID 序列、字段集和 QueryToken。
     *
     * <p>适合通用列表页面进入详情、上一条/下一条切换和前端缓存复用。QueryToken 只绑定本次查询上下文， 不能替代详情读取时的实时权限校验。业务子类通常不覆写；领域筛选请覆写
     * {@link #buildSpec(PageParam)}。
     */
    public PageResult<V> queryWindow(P request, String fieldSet, List<CrudFilter> filters) {
        return queryWindow(request, fieldSet, filters, enforce(CrudOperation.QUERY));
    }

    /** 使用入口已生成的唯一决策执行查询窗口，避免导出等组合操作重复 preflight。 */
    private PageResult<V> queryWindow(
            P request,
            String fieldSet,
            List<CrudFilter> filters,
            CrudEnforcementDecision<E> decision) {
        filters.forEach(
                filter -> decision.fieldPolicy().require(filter.field(), FieldCapability.FILTER));
        var pageable = buildPageable(request, decision);
        Page<E> page =
                getRepository().findAll(buildEffectiveSpec(request, filters, decision), pageable);
        var entities = page.getContent();
        var ids = entities.stream().map(BaseEntity::getId).toList();
        var normalizedFieldSet = normalizeFieldSet(fieldSet);
        var list = toViews(entities, normalizedFieldSet, decision);
        var tokenContext =
                queryTokenContext(decision, normalizedFieldSet, queryHash(request, filters));
        return new PageResult<>(
                list,
                page.getTotalElements(),
                request.getPageNo(),
                request.getPageSize(),
                ids,
                queryTokenService.issue(tokenContext, ids),
                normalizedFieldSet,
                page.hasNext());
    }

    /**
     * 读取单条记录的详情字段集。
     *
     * <p>不存在和无权访问都会按资源不存在处理，避免泄露记录是否真实存在。业务子类不应覆写；需要自定义详情入口时， 使用 {@link #getByIdWithAccess(Long,
     * String, CrudOperation, AccessMode)} 保留统一安全管线。
     */
    @Transactional
    public V getById(Long id) {
        var decision = enforceObject(CrudOperation.GET, AccessMode.DEFAULT);
        return toView(requireCurrentEntity(id, decision), "detail", decision);
    }

    /**
     * 判断当前用户能否读取指定记录。
     *
     * <p>供跨资源引用、关联校验等框架内部流程复用，与详情查询使用相同的读取范围。返回 {@code false} 不区分记录不存在和无权访问。
     * 这是固定安全入口，业务子类不应覆写；额外范围限制应通过资源规则或 {@link #buildSpec(PageParam)} 声明。
     */
    public boolean isReadable(Long id) {
        if (id == null || id <= 0) {
            return false;
        }
        var decision = enforceObject(CrudOperation.GET, AccessMode.DEFAULT);
        var entity =
                getRepository()
                        .findOne(Specification.allOf(idSpec(id), decision.scopeSpecification()))
                        .orElse(null);
        return entity != null
                && crudEnforcementService.allowsCurrentTarget(
                        resourceEntry(), decision, entity, authorizationSnapshot(entity), null);
    }

    /**
     * 判断当前用户能否把指定记录作为其他资源的关联目标。
     *
     * <p>“可以查看”不等于“可以关联”。本方法除检查记录范围外，还要求资源的 ID 字段具有引用能力； 额外关联规则由 {@link ReferencePolicy}
     * 处理。该方法是固定安全入口，业务子类不应覆写。
     */
    public boolean isReferenceVisible(Long id) {
        if (id == null || id <= 0) {
            return false;
        }
        var decision = enforce(CrudOperation.REFERENCE);
        decision.fieldPolicy().require("id", FieldCapability.REFERENCE);
        return getRepository()
                .findOne(Specification.allOf(idSpec(id), decision.scopeSpecification()))
                .isPresent();
    }

    public Set<Long> readableReferenceIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        var decision = enforce(CrudOperation.GET);
        return getRepository()
                .findAll(
                        Specification.allOf(
                                idInSpec(ids.stream().toList()), decision.scopeSpecification()))
                .stream()
                .map(BaseEntity::getId)
                .collect(Collectors.toUnmodifiableSet());
    }

    public Set<Long> referenceableIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        var decision = enforce(CrudOperation.REFERENCE);
        decision.fieldPolicy().require("id", FieldCapability.REFERENCE);
        return getRepository()
                .findAll(
                        Specification.allOf(
                                idInSpec(ids.stream().toList()), decision.scopeSpecification()))
                .stream()
                .map(BaseEntity::getId)
                .collect(Collectors.toUnmodifiableSet());
    }

    public Map<Long, ResourceRefDTO> loadReadableRefs(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        var decision = enforce(CrudOperation.GET);
        var resource = resourceDefinition().key().value();
        var refs = new LinkedHashMap<Long, ResourceRefDTO>();
        getRepository()
                .findAll(
                        Specification.allOf(
                                idInSpec(ids.stream().toList()), decision.scopeSpecification()))
                .forEach(
                        entity ->
                                refs.put(
                                        entity.getId(),
                                        new ResourceRefDTO(
                                                resource,
                                                entity.getId(),
                                                optionDisplayName(entity),
                                                null)));
        return Map.copyOf(refs);
    }

    /**
     * 按指定字段集读取单条记录，并校验它是否仍属于查询窗口。
     *
     * <p>前端从 {@link #queryWindow(PageParam, String, List)} 进入详情时可携带 QueryToken，框架会检查查询上下文是否匹配，
     * 但仍会重新执行实时权限校验。普通详情调用可不传 Token；业务子类不应覆写。
     */
    @Transactional
    public V getById(Long id, String queryToken, String fieldSet) {
        var decision = enforceObject(CrudOperation.GET, AccessMode.DEFAULT);
        var normalizedFieldSet = normalizeFieldSet(fieldSet);
        validateQueryToken(id, queryToken, normalizedFieldSet, decision);
        return toView(requireCurrentEntity(id, decision), normalizedFieldSet, decision);
    }

    /**
     * 按请求顺序批量读取多条记录。
     *
     * <p>适合详情页预取相邻记录、批量操作前确认等场景。框架会过滤无权记录并限制单次数量，避免超长 SQL。 业务子类通常不覆写；复杂批量业务查询应使用 {@link
     * #queryListWithAccess(Specification, Sort, String, CrudOperation, AccessMode)}。
     */
    public List<V> batchRead(List<Long> ids, String fieldSet) {
        var decision = enforce(CrudOperation.BATCH_READ);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        if (ids.size() > BATCH_READ_MAX_SIZE) {
            throw exception(GlobalErrorCode.CRUD_BATCH_READ_LIMIT_EXCEEDED, BATCH_READ_MAX_SIZE);
        }
        var normalizedFieldSet = normalizeFieldSet(fieldSet == null ? "detail" : fieldSet);
        var entitiesById = new LinkedHashMap<Long, E>();
        getRepository()
                .findAll(
                        Specification.allOf(
                                idInSpec(ids.stream().toList()), decision.scopeSpecification()))
                .forEach(entity -> entitiesById.put(entity.getId(), entity));
        var orderedEntities = ids.stream().map(entitiesById::get).filter(Objects::nonNull).toList();
        return toViews(orderedEntities, normalizedFieldSet, decision);
    }

    /**
     * 返回可作为关联对象的轻量选择器选项。
     *
     * <p>用于下拉框、关联字段和弹窗选择器。框架会限制结果数量并应用数据范围；业务子类可覆写 {@link #buildOptionSpec(String)}、{@link
     * #optionSearchFields()}、{@link #optionDisplayFields()} 或 {@link #displayName(BaseEntity)}
     * 调整搜索与展示，但不能绕过引用权限。
     */
    public List<ResourceRefDTO> options(String keyword, Integer limit) {
        var decision = enforce(CrudOperation.OPTIONS);
        var safeLimit = limit == null ? 20 : Math.max(1, Math.min(limit, 100));
        var page =
                getRepository()
                        .findAll(
                                Specification.allOf(
                                        decision.scopeSpecification(), buildOptionSpec(keyword)),
                                PageRequest.of(0, safeLimit, defaultSort()));
        return page.getContent().stream()
                .map(entity -> new ResourceRefDTO(entity.getId(), optionDisplayName(entity), null))
                .toList();
    }

    // ==================== CRUD 元数据 ====================

    /** 返回资源的内部 slug，供错误信息和内部元数据组装使用；业务子类不需要调用 */
    private String entitySlug() {
        return resourceDefinition().entitySlug();
    }

    /** 返回资源显示名称，供错误信息和框架内部提示使用；名称来自资源定义而非业务子类 */
    private String entityName() {
        return resourceDefinition().displayName();
    }

    /** 返回资源声明的全部字段集名称，用于校验 list、detail、picker、export 等输出请求 */
    private List<String> fieldSets() {
        return resourceDefinition().view().fieldSets().keySet().stream().sorted().toList();
    }

    /** 返回资源声明的可排序字段白名单，供元数据输出和请求校验使用 */
    private Set<String> sortableFields() {
        return resourceDefinition().query().sortableFields();
    }

    /**
     * 返回当前资源可被前端和 AI 使用的元数据。
     *
     * <p>包含字段集、操作能力、筛选字段、排序字段和资源版本等信息，全部来自已编译 Catalog。业务子类不应覆写或手工拼装。
     */
    public CrudMetaDTO meta() {
        enforce(CrudOperation.META);
        var definition = resourceDefinition();
        var snapshot = crudResourceRegistry.require(definition.key()).snapshot();
        return CrudMetaAssembler.assemble(snapshot, definition);
    }

    /** 返回资源显示名称，供 Controller、AI 动作和错误提示使用；业务名称应在资源定义中维护 */
    public String getEntityName() {
        return resourceDefinition().displayName();
    }

    /**
     * 查询完整的 export 字段集数据，供上层生成文件或交给其他受控导出流程。
     *
     * <p>框架会执行导出权限、字段导出权限和数据范围校验，并复用查询窗口逻辑。业务子类一般不覆写； 需要改变文件格式时在上层适配器处理，不要跳过此方法直接查库。
     */
    public PageResult<V> exportData(P request) {
        var decision = enforce(CrudOperation.EXPORT);
        request.setPageNo(1);
        request.setPageSize(PageParam.PAGE_SIZE_NONE);
        return queryWindow(request, "export", List.of(), decision);
    }

    /**
     * 将 export 字段集转换为列名和单元格数据，供 HTTP 或文件适配器生成 Excel、CSV 等文件。
     *
     * <p>指定字段时会再次校验字段导出权限；标记 {@link DictFormat} 的字段会尝试转换为字典文本。 业务子类通常不覆写，文件格式和下载响应应由上层处理。
     *
     * @param fields 指定导出字段；为 null 或空时使用输出视图中的全部字段
     */
    public ExportSheet exportSheet(P request, List<String> fields) {
        var decision = enforce(CrudOperation.EXPORT);
        if (fields != null) {
            decision.fieldPolicy().requireAll(fields, FieldCapability.EXPORT);
        }
        request.setPageNo(1);
        request.setPageSize(PageParam.PAGE_SIZE_NONE);
        var page = queryWindow(request, "export", List.of(), decision);
        var rows = page.list();
        if (rows.isEmpty()) {
            return new ExportSheet(fields == null ? List.of() : fields, List.of());
        }
        @SuppressWarnings("unchecked")
        var firstRowMap =
                (LinkedHashMap<String, Object>)
                        JsonUtils.convertValue(rows.get(0), LinkedHashMap.class);
        var dictFields = collectDictFields(rows.get(0).getClass());
        var columns =
                fields == null || fields.isEmpty() ? List.copyOf(firstRowMap.keySet()) : fields;
        var resolver = dictLabelResolver == null ? null : dictLabelResolver.getIfAvailable();
        var dataRows =
                rows.stream()
                        .map(
                                vo -> {
                                    @SuppressWarnings("unchecked")
                                    var map =
                                            (Map<String, Object>)
                                                    JsonUtils.convertValue(vo, LinkedHashMap.class);
                                    return columns.stream()
                                            .<Object>map(
                                                    col ->
                                                            resolveCellValue(
                                                                    map, col, dictFields, resolver))
                                            .toList();
                                })
                        .toList();
        return new ExportSheet(columns, dataRows);
    }

    /** 将一个输出字段的原始值转换为导出单元格值；字典字段优先显示字典标签 */
    private Object resolveCellValue(
            Map<String, Object> row,
            String column,
            Map<String, String> dictFields,
            DictLabelResolver resolver) {
        Object value = row.get(column);
        var dictType = dictFields.get(column);
        if (dictType != null && resolver != null && value != null) {
            return resolver.resolve(dictType, String.valueOf(value));
        }
        return value;
    }

    /** 反射收集 VO 上标注 {@link DictFormat} 的字段/record 分量：字段名 → 字典类型 */
    private Map<String, String> collectDictFields(Class<?> voClass) {
        var result = new LinkedHashMap<String, String>();
        if (voClass.isRecord()) {
            for (var component : voClass.getRecordComponents()) {
                var format = component.getAnnotation(DictFormat.class);
                if (format != null) {
                    result.put(component.getName(), format.value());
                }
            }
        } else {
            for (var field : voClass.getDeclaredFields()) {
                var format = field.getAnnotation(DictFormat.class);
                if (format != null) {
                    result.put(field.getName(), format.value());
                }
            }
        }
        return result;
    }

    /** 导出表格结果：列名 + 数据行，供 Controller 层转成 Excel/CSV 文件流 */
    public record ExportSheet(List<String> columns, List<List<Object>> rows) {}

    /**
     * 导入多条创建请求。
     *
     * <p><b>默认不支持，业务子类可按需覆写。</b>覆写时应先定义每行校验、失败处理和事务语义；框架已经在调用前检查导入权限和可写字段， 业务实现不能跳过这些前置条件。
     */
    public CrudImportResultDTO importRows(CrudImportRequestDTO<C> request) {
        var decision = enforce(CrudOperation.IMPORT);
        request.rows().forEach(row -> requireWritableFields(row, decision.fieldPolicy()));
        return unsupported("导入");
    }

    /**
     * 对当前用户可见的数据执行分组聚合。
     *
     * <p><b>默认不支持，业务子类可按需覆写。</b>仅适用于已明确声明分组字段、聚合字段和聚合函数的资源； 不要把任意客户端字段直接传给数据库。框架会先校验聚合字段权限。
     */
    public List<CrudGroupResultDTO> group(CrudGroupRequestDTO request) {
        var decision = enforce(CrudOperation.GROUP);
        decision.fieldPolicy().require(request.groupBy(), FieldCapability.AGGREGATE);
        if (request.aggregateField() != null && !request.aggregateField().isBlank()) {
            decision.fieldPolicy().require(request.aggregateField(), FieldCapability.AGGREGATE);
        }
        return unsupported("分组聚合");
    }

    /**
     * 在保存前执行轻量业务预校验，不写入数据库。
     *
     * <p><b>可按需覆写。</b>默认只检查请求字段是否可写并返回成功。业务可补充唯一性、状态组合或格式校验， 但真正的创建和更新仍必须在 {@link
     * #create(Object)}、{@link #update(Long, Object)} 中保持完整校验。
     */
    public CrudValidationResultDTO validate(C request) {
        var decision = enforce(CrudOperation.VALIDATE);
        requireWritableFields(request, decision.fieldPolicy());
        return CrudValidationResultDTO.success();
    }

    /**
     * 使用默认访问模式创建一条记录。
     *
     * <p>这是普通 HTTP 创建入口对应的 Service 方法。框架会依次校验创建权限和字段权限、设置租户和所有者、处理关系、保存实体，
     * 再生成已裁剪字段的详情输出视图。业务子类通常只实现 {@link #toEntity(Object)}，不覆写本方法。
     */
    @Transactional
    public V create(C request) {
        return createWithAccess(request, AccessMode.DEFAULT);
    }

    /**
     * 使用明确访问模式创建记录，供业务内部特殊入口调用。
     *
     * <p>例如系统任务或管理员维护创建。<b>必须显式传入 {@link AccessMode}</b>，避免特殊入口悄悄获得默认权限； 本方法为 final，业务不能覆盖安全步骤，仍应通过
     * {@link #toEntity(Object)} 定义字段转换。
     */
    protected final V createWithAccess(C request, AccessMode accessMode) {
        var payloadDigest = payloadDigest(request);
        var decision = enforceObject(CrudOperation.CREATE, accessMode);
        requireWritableFields(request, decision.fieldPolicy());
        return persistCreatedEntity(toEntity(request), request, payloadDigest, decision);
    }

    /**
     * 保存业务代码已构造好的实体，并保留完整 CRUD 安全和关系处理流程。
     *
     * <p>用于系统根据领域事件生成记录等场景。<b>必须显式传入 {@link AccessMode}</b>；调用方仍不得自行设置不属于当前租户的数据， 也不得跳过关系校验。本方法为
     * final，业务只负责准备实体的领域字段。
     */
    protected final V createEntityWithAccess(E entity, C request, AccessMode accessMode) {
        var payloadDigest = payloadDigest(request);
        var decision = enforceObject(CrudOperation.CREATE, accessMode);
        requireWritableFields(request, decision.fieldPolicy());
        return persistCreatedEntity(entity, request, payloadDigest, decision);
    }

    /**
     * 执行创建流程中不能绕过的公共步骤。
     *
     * <p>依次处理权益、租户、默认所有者、关系校验、保存、关系同步和详情输出视图。保持私有以避免业务子类只调用其中一部分。
     */
    private V persistCreatedEntity(
            E entity, C request, String payloadDigest, CrudEnforcementDecision<E> decision) {
        checkEntitlement(1);
        applyTenant(entity, decision);
        if (entity.getOwnerId() == null) {
            entity.setOwnerId(decision.subjectId());
        }
        crudEnforcementService.requireCreatedTarget(
                resourceEntry(), decision, entity, authorizationSnapshot(entity), payloadDigest);
        validateEntityReferences(entity, request, true);
        validateRelationPatches(entity, request, decision);
        getRepository().save(entity);
        synchronizeRelationPatches(entity, request, decision);
        consumeEntitlement(1);
        return toView(entity, "detail", decision);
    }

    /**
     * 在同一事务中创建多条记录。
     *
     * <p>框架只执行一次不含 L4 的前置决策；应用服务端租户和 owner 后，将全部 CREATED 快照与各自原请求摘要绑定到一个批次 target PDP。整个批次最多产生一个
     * challenge，任一校验失败都会使整批回滚。
     */
    @Transactional
    public List<V> createBatch(List<C> requests) {
        var decision = enforceObject(CrudOperation.CREATE, AccessMode.DEFAULT);
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        requests.forEach(request -> requireWritableFields(request, decision.fieldPolicy()));
        var payloadDigests = requests.stream().map(this::payloadDigest).toList();
        var entities =
                requests.stream()
                        .map(this::toEntity)
                        .peek(entity -> applyTenant(entity, decision))
                        .peek(
                                entity -> {
                                    if (entity.getOwnerId() == null) {
                                        entity.setOwnerId(decision.subjectId());
                                    }
                                })
                        .toList();
        var createdSnapshots = entities.stream().map(this::authorizationSnapshot).toList();
        for (var index = 0; index < entities.size(); index++) {
            validateEntityReferences(entities.get(index), requests.get(index), true);
            validateRelationPatches(entities.get(index), requests.get(index), decision);
        }
        crudEnforcementService.requireCreatedBatchTarget(
                resourceEntry(), decision, createdSnapshots, payloadDigests);
        getRepository().saveAll(entities);
        for (var index = 0; index < entities.size(); index++) {
            synchronizeRelationPatches(entities.get(index), requests.get(index), decision);
        }
        return toViews(entities, "detail", decision);
    }

    /** target L4 通过后执行的受控动作；可产生后续映射所需结果。 */
    @FunctionalInterface
    protected interface AuthorizedUpdateAction<T extends BaseEntity, C, A> {
        A execute(T entity, C command);
    }

    /** 主实体按需保存后执行的动作。 */
    @FunctionalInterface
    protected interface AfterUpdateAction<T extends BaseEntity, C, A> {
        void execute(T entity, C command, A actionResult);
    }

    /** 将已授权命令结果映射为调用方返回类型。 */
    @FunctionalInterface
    protected interface UpdateResultMapper<T extends BaseEntity, C, A, R> {
        R map(T entity, C command, A actionResult);
    }

    /** 自定义 UPDATE 的不可变执行计划。 */
    protected record CustomUpdatePlan<T extends BaseEntity, C, A, R>(
            String commandType,
            Set<String> modifiedFields,
            java.util.function.BiConsumer<T, C> currentValidator,
            java.util.function.BiConsumer<T, C> proposalMutation,
            AuthorizedUpdateAction<T, C, A> authorizedAction,
            boolean saveEntity,
            AfterUpdateAction<T, C, A> afterSaveAction,
            UpdateResultMapper<T, C, A, R> resultMapper) {

        public CustomUpdatePlan {
            Objects.requireNonNull(commandType, "commandType");
            modifiedFields = Set.copyOf(Objects.requireNonNull(modifiedFields, "modifiedFields"));
            if (modifiedFields.isEmpty()) {
                throw new IllegalArgumentException("自定义 UPDATE 必须声明修改字段");
            }
            Objects.requireNonNull(currentValidator, "currentValidator");
            Objects.requireNonNull(proposalMutation, "proposalMutation");
            Objects.requireNonNull(authorizedAction, "authorizedAction");
            Objects.requireNonNull(afterSaveAction, "afterSaveAction");
            Objects.requireNonNull(resultMapper, "resultMapper");
        }
    }

    /** 以明确主实体 ID 执行具名自定义 UPDATE。 */
    protected final <C0, A, R> R executeCustomUpdateCommand(
            Long id, C0 command, CustomUpdatePlan<E, C0, A, R> plan) {
        return executeCustomUpdateCommand(() -> id, command, plan);
    }

    /**
     * 以授权前只读解析器定位主实体，再执行具名自定义 UPDATE。
     *
     * <p>解析器仅可返回主实体 ID，不得修改数据；它在 UPDATE preflight 后执行，随后主实体仍按同一 L3 决策加载。
     */
    protected final <C0, A, R> R executeCustomUpdateCommand(
            java.util.function.Supplier<Long> objectIdResolver,
            C0 command,
            CustomUpdatePlan<E, C0, A, R> plan) {
        Objects.requireNonNull(objectIdResolver, "objectIdResolver");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(plan, "plan");
        var entry = resourceEntry();
        CrudEnforcementDecision<E> decision =
                crudEnforcementService.<E>enforceCustomUpdatePreflight(
                        entry, AccessMode.DEFAULT, plan.commandType(), plan.modifiedFields());
        decision.fieldPolicy().requireAll(plan.modifiedFields(), FieldCapability.WRITE);
        var id = objectIdResolver.get();
        if (id == null || id <= 0) {
            throw exception(GlobalErrorCode.CRUD_RESOURCE_NOT_FOUND, entityName());
        }
        var entity = loadLockedEntity(id, decision);
        plan.currentValidator().accept(entity, command);
        var objectId = entity.getId().toString();
        var currentAttributes = authorizationSnapshot(entity);
        plan.proposalMutation().accept(entity, command);
        validateTenant(entity, decision);
        var proposedAttributes = authorizationSnapshot(entity);
        requireDeclaredAuthorizationChanges(
                currentAttributes, proposedAttributes, plan.modifiedFields());
        crudEnforcementService.requireUpdatedTarget(
                entry,
                decision,
                objectId,
                currentAttributes,
                proposedAttributes,
                payloadDigest(command),
                plan.commandType(),
                plan.modifiedFields());
        var actionResult = plan.authorizedAction().execute(entity, command);
        validateTenant(entity, decision);
        requireDeclaredAuthorizationChanges(
                currentAttributes, authorizationSnapshot(entity), plan.modifiedFields());
        if (plan.saveEntity()) {
            getRepository().save(entity);
        }
        plan.afterSaveAction().execute(entity, command, actionResult);
        return plan.resultMapper().map(entity, command, actionResult);
    }

    /** 防止 proposal 或授权后动作修改调用方未声明的授权属性。 */
    private void requireDeclaredAuthorizationChanges(
            Map<String, Object> current, Map<String, Object> changed, Set<String> modifiedFields) {
        var undeclared = new LinkedHashSet<String>();
        var keys = new LinkedHashSet<>(current.keySet());
        keys.addAll(changed.keySet());
        keys.stream()
                .filter(key -> !Objects.equals(current.get(key), changed.get(key)))
                .filter(key -> !modifiedFields.contains(key))
                .forEach(undeclared::add);
        if (!undeclared.isEmpty()) {
            throw exception(GlobalErrorCode.FORBIDDEN);
        }
    }

    /**
     * 使用默认访问模式更新一条记录。
     *
     * <p>框架先安全加载实体，再调用业务子类的 {@link #updateEntity(BaseEntity, Object)} 修改业务字段，
     * 随后校验租户、同步关系并返回详情输出视图。业务子类不应覆写本方法。
     */
    @Transactional
    public V update(Long id, U request) {
        var payloadDigest = payloadDigest(request);
        var decision = enforceObject(CrudOperation.UPDATE, AccessMode.DEFAULT);
        var modifiedFields = requireWritableFields(request, decision.fieldPolicy());
        if (modifiedFields.isEmpty()) {
            throw exception(GlobalErrorCode.BAD_REQUEST);
        }
        var entity = loadLockedEntity(id, decision);
        beforeUpdate(entity, request);
        var objectId = entity.getId().toString();
        var currentAttributes = authorizationSnapshot(entity);
        updateEntity(entity, request);
        validateTenant(entity, decision);
        crudEnforcementService.requireUpdatedTarget(
                resourceEntry(),
                decision,
                objectId,
                currentAttributes,
                authorizationSnapshot(entity),
                payloadDigest,
                "UPDATE",
                modifiedFields);
        validateEntityReferences(entity, request, false);
        validateRelationPatches(entity, request, decision);
        getRepository().save(entity);
        synchronizeRelationPatches(entity, request, decision);
        return toView(entity, "detail", decision);
    }

    /**
     * 使用默认访问模式删除一条记录。
     *
     * <p>框架会先校验删除权限和数据范围，再删除实体并处理权益归还。业务子类不应覆写；特殊删除场景使用 {@link #deleteWithAccess(Long,
     * AccessMode)}。
     */
    @Transactional
    public void delete(Long id) {
        deleteWithAccess(id, AccessMode.DEFAULT);
    }

    /**
     * 使用明确访问模式删除一条记录，供管理员维护或系统任务等内部入口使用。
     *
     * <p><b>必须显式传入 {@link AccessMode}</b>，且仍会执行该模式对应的统一安全校验和数据范围限制。 本方法为 final，业务不能通过覆写跳过删除授权。
     */
    protected final void deleteWithAccess(Long id, AccessMode accessMode) {
        var decision = enforceObject(CrudOperation.DELETE, accessMode);
        var entity = requireLockedCurrentEntity(id, decision);
        beforeDelete(entity);
        genericRelationHandler.cleanupSourceLinks(
                resourceDefinition().relations(), List.of(entity.getId()));
        getRepository().delete(entity);
        afterDelete(entity);
        consumeEntitlement(-1);
    }

    /**
     * 批量逻辑删除多条记录。
     *
     * <p>框架要求请求中的每条记录都在当前删除范围内，随后用一条 SQL 执行软删除以避免逐条操作。 业务子类通常不覆写；需要状态型归档时覆写 {@link
     * #archive(List)}，不要修改本方法绕过批量安全校验。
     */
    @Transactional
    public void deleteBatch(List<Long> ids) {
        var decision = enforce(CrudOperation.DELETE_BATCH);
        deleteEntities(requireLockedEntities(ids, decision));
    }

    /**
     * 以明确访问模式删除符合业务条件的记录。
     *
     * <p>用于管理清理等组合操作：入口只执行一次 {@link CrudOperation#DELETE_BATCH} PEP，随后以同一决策加载范围内实体并执行数据变更，
     * 不得先加载后逐条重新进入 DELETE PEP。
     */
    protected final long deleteMatchingWithAccess(
            Specification<E> businessSpec, AccessMode accessMode) {
        var decision = enforce(CrudOperation.DELETE_BATCH, accessMode);
        var effectiveScope = Specification.allOf(decision.scopeSpecification(), businessSpec);
        var entities = getRepository().findAll(effectiveScope);
        var lockedEntities = lockAndRevalidateEntities(entities, effectiveScope);
        deleteEntities(lockedEntities);
        return lockedEntities.size();
    }

    /** 使用入口唯一决策已加载的实体执行批量软删除，供归档和管理清理复用。 */
    private void deleteEntities(List<E> entities) {
        if (entities.isEmpty()) {
            return;
        }
        String tableName = resolveTableName();
        if (tableName == null) {
            throw new IllegalStateException("无法解析 CRUD 实体表名: " + getClass().getName());
        }
        var entityIds = entities.stream().map(BaseEntity::getId).toList();
        genericRelationHandler.cleanupSourceLinks(resourceDefinition().relations(), entityIds);
        entityManager
                .createNativeQuery(
                        "UPDATE "
                                + tableName
                                + " SET deleted = true, delete_time = CURRENT_TIMESTAMP"
                                + " WHERE id IN (:ids) AND deleted = false")
                .setParameter("ids", entityIds)
                .executeUpdate();
    }

    /** 从实体的 {@link Table} 注解解析物理表名，供批量软删除使用；解析失败时返回 null 由调用方拒绝执行 */
    @SuppressWarnings("unchecked")
    private String resolveTableName() {
        try {
            var type = (java.lang.reflect.ParameterizedType) getClass().getGenericSuperclass();
            var entityClass = (Class<E>) type.getActualTypeArguments()[0];
            var table = entityClass.getAnnotation(Table.class);
            return table != null ? table.name() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 归档多条记录。
     *
     * <p><b>可按需覆写。</b>默认直接复用逻辑删除。若业务需要保留记录并将状态改为 {@code archived}，可在子类实现状态迁移， 但必须先调用或保留 {@link
     * #enforce(CrudOperation, AccessMode)} 的归档授权。
     */
    @Transactional
    public void archive(List<Long> ids) {
        var decision = enforce(CrudOperation.ARCHIVE);
        deleteEntities(requireLockedEntities(ids, decision));
    }

    /**
     * 恢复已归档或逻辑删除的记录。
     *
     * <p><b>默认不支持，业务子类可按需覆写。</b>仅当资源明确具备可恢复语义时实现，例如恢复删除标记或状态； 实现时必须保留 RESTORE 操作的统一安全校验，不能直接更新数据库。
     */
    @Transactional
    public void restore(List<Long> ids) {
        enforce(CrudOperation.RESTORE);
        throw exception(GlobalErrorCode.CRUD_RESTORE_UNSUPPORTED, entityName());
    }

    /**
     * 返回本资源每创建一条记录所消耗的权益编码。
     *
     * <p><b>可按需覆写。</b>默认 {@code null} 表示不参与权益计量。返回编码后，框架会在创建前检查额度、创建后扣减， 删除时归还；业务子类不要自行重复扣减。
     */
    protected String entitlementCode() {
        return null;
    }

    /** 在创建前检查当前用户是否还有足够权益；未配置权益或无当前主体时不执行检查 */
    private void checkEntitlement(long cost) {
        var code = entitlementCode();
        if (code == null || entitlementChecker == null || operatorContext == null) return;
        var userId = operatorContext.currentOwnerId().orElse(null);
        if (userId == null) return;
        entitlementChecker.check(userId, code, cost);
    }

    /** 在创建成功后扣减、删除成功后归还权益；业务子类不应直接调用或重复计量 */
    private void consumeEntitlement(long delta) {
        var code = entitlementCode();
        if (code == null || entitlementChecker == null || operatorContext == null) return;
        var userId = operatorContext.currentOwnerId().orElse(null);
        if (userId == null) return;
        entitlementChecker.consume(userId, code, delta);
    }

    /**
     * 将实体转换为指定字段集的输出视图。
     *
     * <p><b>可按需覆写。</b>默认忽略字段集并调用 {@link #toVO(BaseEntity)}。简单资源保持默认即可； 需要为
     * list、detail、picker、export 返回不同字段形状时覆写。若资源声明 {@link CrudViewMapper}，正常查询会优先使用视图映射器。
     */
    protected V toVO(E entity, String fieldSet) {
        return toVO(entity);
    }

    /**
     * 批量将实体转换为指定字段集的输出视图。
     *
     * <p><b>可按需覆写。</b>默认逐条调用 {@link #toVO(BaseEntity, String)}。当简单转换中需要关联用户、字典或引用数据时，
     * 应在这里批量加载并组装，避免逐条查询产生 N+1；不要在循环中直接访问 Repository。
     */
    protected List<V> toVOList(List<E> entities, String fieldSet) {
        return entities.stream().map(entity -> toVO(entity, fieldSet)).toList();
    }

    /**
     * 构建选择器关键词的业务搜索条件。
     *
     * <p><b>可按需覆写。</b>默认在常见名称字段中做不区分大小写的模糊匹配。资源使用编码、拼音、组合名称等领域规则时， 可追加条件；不要自行加入租户或数据范围条件，框架会统一合并。
     */
    protected Specification<E> buildOptionSpec(String keyword) {
        return buildDefaultSearchSpec(keyword);
    }

    private Specification<E> buildDefaultSearchSpec(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return (root, query, cb) -> null;
        }
        var pattern = "%%%s%%".formatted(keyword.trim().toLowerCase(Locale.ROOT));
        return (root, query, cb) -> {
            var stringFieldNames =
                    root.getModel().getAttributes().stream()
                            .filter(attribute -> String.class.equals(attribute.getJavaType()))
                            .map(Attribute::getName)
                            .collect(Collectors.toSet());
            var predicates = new ArrayList<Predicate>();
            for (var fieldName : optionSearchFields()) {
                if (stringFieldNames.contains(fieldName)) {
                    predicates.add(
                            cb.like(cb.lower(root.get(fieldName).as(String.class)), pattern));
                }
            }
            if (predicates.isEmpty()) {
                return null;
            }
            return predicates.size() == 1
                    ? predicates.getFirst()
                    : cb.or(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * 返回选择器默认参与关键词搜索的字段顺序。
     *
     * <p><b>可按需覆写。</b>默认尝试 name、title、displayName、username、code。业务实体字段不同或希望限制搜索范围时覆写；
     * 返回不存在的字段会被框架忽略。
     */
    protected List<String> optionSearchFields() {
        return List.of("name", "title", "displayName", "username", "code");
    }

    /**
     * 返回选择器优先显示的字段顺序。
     *
     * <p><b>可按需覆写。</b>默认与 {@link #optionSearchFields()} 相同。希望按名称展示、但按编码和名称共同搜索时， 可只覆写本方法以分离展示与搜索规则。
     */
    protected List<String> optionDisplayFields() {
        return optionSearchFields();
    }

    /** 按展示字段顺序提取选择器文案；没有可用值时回退到 {@link #displayName(BaseEntity)} */
    private String optionDisplayName(E entity) {
        var bean = new BeanWrapperImpl(entity);
        for (var property : optionDisplayFields()) {
            if (!bean.isReadableProperty(property)) {
                continue;
            }
            var value = bean.getPropertyValue(property);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return displayName(entity);
    }

    /**
     * 返回实体在选择器、提示和轻量引用中的默认显示名称。
     *
     * <p><b>可按需覆写。</b>默认从 name、title、displayName、username、code 中取第一个非空值，再回退为“资源名#ID”。
     * 业务需要组合名称、国际化名称或脱敏展示时覆写本方法。
     */
    protected String displayName(E entity) {
        var bean = new BeanWrapperImpl(entity);
        for (String property : List.of("name", "title", "displayName", "username", "code")) {
            if (!bean.isReadableProperty(property)) {
                continue;
            }
            Object value = bean.getPropertyValue(property);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return "%s#%s".formatted(entityName(), entity.getId());
    }

    /**
     * 将动作名称转换为当前资源的权限码。
     *
     * <p>供本类和受控框架适配器使用。业务子类通常不需要调用；传入未知动作会被拒绝，避免拼接任意权限码。
     */
    protected String permissionCode(String action) {
        if (action == null || action.isBlank()) {
            throw exception(GlobalErrorCode.FORBIDDEN);
        }
        try {
            return resourceDefinition()
                    .permissionCode(CrudAction.valueOf(action.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException cause) {
            throw exception(GlobalErrorCode.FORBIDDEN);
        }
    }

    /**
     * 校验详情请求携带的 QueryToken 是否属于当前查询上下文。
     *
     * <p>Token 用于防止把一个列表窗口的导航令牌用于另一资源、另一用户或另一字段集；它不是权限凭证， 调用前后仍会执行实时数据范围校验。业务子类不应跳过或自行伪造 Token。
     */
    private void validateQueryToken(
            Long id, String queryToken, String fieldSet, CrudEnforcementDecision<E> decision) {
        if (queryToken == null || queryToken.isBlank()) {
            return;
        }
        if ("detail".equals(fieldSet)) {
            queryTokenService.validateForDetail(
                    queryToken,
                    new QueryTokenService.DetailQueryTokenContext(
                            decision.subjectId(),
                            decision.orgId(),
                            decision.workspaceId(),
                            resourceDefinition().key().value(),
                            decision.accessVersion()),
                    id);
            return;
        }
        queryTokenService.validate(
                queryToken, queryTokenContext(decision, fieldSet, "signed-query"), id);
    }

    /** 构建 QueryToken 的签名上下文，绑定当前主体、租户、资源、字段集和权限版本 */
    private QueryTokenService.QueryTokenContext queryTokenContext(
            CrudEnforcementDecision<E> decision, String fieldSet, String queryHash) {
        return new QueryTokenService.QueryTokenContext(
                decision.subjectId(),
                decision.orgId(),
                decision.workspaceId(),
                resourceDefinition().key().value(),
                fieldSet,
                queryHash,
                decision.accessVersion());
    }

    /** 将服务端收到的原始请求规范化为稳定 JSON，并生成 target PDP 绑定使用的 SHA-256。 */
    private String payloadDigest(Object request) {
        Objects.requireNonNull(request, "request");
        var jsonValue = JsonUtils.parseObject(JsonUtils.toJsonString(request), Object.class);
        var stableJson = JsonUtils.toJsonString(stableJsonValue(jsonValue));
        try {
            var digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(stableJson.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException cause) {
            throw new IllegalStateException("SHA-256 不可用", cause);
        }
    }

    private Object stableJsonValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            var sorted = new TreeMap<String, Object>();
            map.forEach((key, item) -> sorted.put(String.valueOf(key), stableJsonValue(item)));
            return sorted;
        }
        if (value instanceof Iterable<?> iterable) {
            var stable = new ArrayList<>();
            iterable.forEach(item -> stable.add(stableJsonValue(item)));
            return stable;
        }
        return value;
    }

    /** 将分页、排序和筛选条件规范化为查询摘要，再计算签名所需的哈希值 */
    private String queryHash(P request, List<CrudFilter> filters) {
        var query = new LinkedHashMap<String, Object>();
        query.put("pageNo", request.getPageNo());
        query.put("pageSize", request.getPageSize());
        query.put("sort", request.getSort());
        query.put("filters", filters);
        return sha256(JsonUtils.toJsonString(query));
    }

    /** 使用 SHA-256 计算稳定摘要；仅供 QueryToken 上下文绑定使用 */
    private String sha256(String source) {
        try {
            var digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(source.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException cause) {
            throw new IllegalStateException("SHA-256 不可用", cause);
        }
    }

    /**
     * 在默认读取范围内加载一条实体，供业务子类的普通领域逻辑使用。
     *
     * <p>记录不存在或当前用户无权读取时都抛资源不存在，避免信息泄露。需要更新、删除或特殊访问模式时， 使用带操作和 {@link AccessMode} 的重载方法。
     */
    protected E requireEntity(Long id) {
        return requireCurrentEntity(id, enforceObject(CrudOperation.GET, AccessMode.DEFAULT));
    }

    /**
     * 按明确操作和访问模式安全加载一条实体。
     *
     * <p>用于业务需要在更新前加锁、管理员维护或系统任务等场景。<b>必须同时传入实际 {@link CrudOperation} 和 {@link AccessMode}</b>，
     * 这样不会把更新或删除误当成普通读取。本方法为 final，业务不能覆盖安全判断。
     */
    protected final E requireEntity(Long id, CrudOperation operation, AccessMode accessMode) {
        var decision = enforceObject(operation, accessMode);
        return requireCurrentEntity(id, decision);
    }

    /**
     * 以明确操作和访问模式查询业务列表，并生成已裁剪字段的输出视图。
     *
     * <p>适合管理员维护列表、系统任务列表等非默认入口。业务子类传入的 {@code businessSpec} 只表达领域条件， 框架仍会合并数据范围和字段权限。<b>必须显式传入
     * {@link AccessMode}</b>。
     */
    protected final List<V> queryListWithAccess(
            Specification<E> businessSpec,
            Sort sort,
            String fieldSet,
            CrudOperation operation,
            AccessMode accessMode) {
        var decision = enforce(operation, accessMode);
        var entities =
                getRepository()
                        .findAll(
                                Specification.allOf(decision.scopeSpecification(), businessSpec),
                                sort);
        return toViews(entities, normalizeFieldSet(fieldSet), decision);
    }

    /**
     * 以明确操作和访问模式读取一条记录并生成输出视图。
     *
     * <p>用于非默认详情入口，例如管理员维护或系统任务。业务子类不应直接查 Repository； <b>必须显式传入 {@link
     * AccessMode}</b>，保证特殊访问仍受统一安全管线约束。
     */
    protected final V getByIdWithAccess(
            Long id, String fieldSet, CrudOperation operation, AccessMode accessMode) {
        var decision = enforceObject(operation, accessMode);
        return toView(requireCurrentEntity(id, decision), normalizeFieldSet(fieldSet), decision);
    }

    /**
     * 以明确操作和访问模式加载业务维护所需的实体集合。
     *
     * <p>适合批量状态迁移、清理或关系维护等后续事务操作。返回实体未转换为输出视图， 仅应在当前方法或事务内使用；<b>必须显式传入 {@link AccessMode}</b>。
     */
    protected final List<E> requireEntitiesWithAccess(
            Specification<E> businessSpec, CrudOperation operation, AccessMode accessMode) {
        var decision = enforce(operation, accessMode);
        return getRepository()
                .findAll(Specification.allOf(decision.scopeSpecification(), businessSpec));
    }

    /** 根据完整 L3 决策加载实体；关系读取只负责安全加载，L2 与对象 L4 在加载后合并为一次 target PDP。 */
    private LoadedEntity<E> loadEntity(Long id, CrudEnforcementDecision<E> decision) {
        var scopedEntity =
                getRepository()
                        .findOne(Specification.allOf(idSpec(id), decision.scopeSpecification()));
        if (scopedEntity.isPresent()) {
            return new LoadedEntity<>(scopedEntity.get(), null);
        }

        if (decision.operation() != CrudOperation.GET
                || decision.accessMode() != AccessMode.DEFAULT) {
            throw exception(GlobalErrorCode.CRUD_RESOURCE_NOT_FOUND, entityName());
        }
        var requirement = relationRequirement(id, decision.operation());
        if (requirement == null || !String.valueOf(id).equals(requirement.objectId())) {
            throw exception(GlobalErrorCode.CRUD_RESOURCE_NOT_FOUND, entityName());
        }
        var entity =
                getRepository()
                        .findOne(
                                Specification.allOf(
                                        idSpec(id), decision.tenantScopeSpecification()))
                        .orElseThrow(
                                () ->
                                        exception(
                                                GlobalErrorCode.CRUD_RESOURCE_NOT_FOUND,
                                                entityName()));
        return new LoadedEntity<>(entity, requirement);
    }

    /** 写操作在固定授权快照前锁定记录，并以同一 L3 范围重查，防止查询与授权之间发生 TOCTOU。 */
    private E loadLockedEntity(Long id, CrudEnforcementDecision<E> decision) {
        var entity = loadEntity(id, decision).entity();
        entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
        return getRepository()
                .findOne(Specification.allOf(idSpec(id), decision.scopeSpecification()))
                .orElseThrow(
                        () -> exception(GlobalErrorCode.CRUD_RESOURCE_NOT_FOUND, entityName()));
    }

    /** DELETE 在锁后生成 CURRENT 并执行对象级 L4。 */
    private E requireLockedCurrentEntity(Long id, CrudEnforcementDecision<E> decision) {
        var entity = loadLockedEntity(id, decision);
        if (!crudEnforcementService.allowsCurrentTarget(
                resourceEntry(), decision, entity, authorizationSnapshot(entity), null)) {
            throw exception(GlobalErrorCode.CRUD_RESOURCE_NOT_FOUND, entityName());
        }
        return entity;
    }

    /** 按 ID 排序获取写锁，随后以原始范围重新查询并核对集合，避免死锁和范围漂移。 */
    private List<E> lockAndRevalidateEntities(List<E> entities, Specification<E> effectiveScope) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        var expectedIds = entities.stream().map(BaseEntity::getId).distinct().sorted().toList();
        var byId = entities.stream().collect(Collectors.toMap(BaseEntity::getId, entity -> entity));
        for (var id : expectedIds) {
            var entity = byId.get(id);
            if (entity == null) {
                throw exception(GlobalErrorCode.CRUD_RESOURCE_NOT_FOUND, entityName());
            }
            entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
        }
        var locked =
                getRepository().findAll(Specification.allOf(idInSpec(expectedIds), effectiveScope));
        if (locked.size() != expectedIds.size()
                || !locked.stream()
                        .map(BaseEntity::getId)
                        .collect(Collectors.toSet())
                        .equals(Set.copyOf(expectedIds))) {
            throw exception(GlobalErrorCode.CRUD_RESOURCE_NOT_FOUND, entityName());
        }
        return locked;
    }

    /** 加载服务端实体后，以其 CURRENT 快照执行且只执行一次对象级 L4。 */
    private E requireCurrentEntity(Long id, CrudEnforcementDecision<E> decision) {
        var loaded = loadEntity(id, decision);
        if (!crudEnforcementService.allowsCurrentTarget(
                resourceEntry(),
                decision,
                loaded.entity(),
                authorizationSnapshot(loaded.entity()),
                loaded.relationRequirement())) {
            throw exception(GlobalErrorCode.CRUD_RESOURCE_NOT_FOUND, entityName());
        }
        return loaded.entity();
    }

    private record LoadedEntity<T extends BaseEntity>(
            T entity, AuthorizationPlan.RelationRequirement relationRequirement) {}

    /**
     * 在当前安全范围内加载一组实体，并要求请求的每个不同 ID 都存在且可见。
     *
     * <p>用于批量删除等不能部分成功的操作，避免调用方只校验其中一部分记录。
     */
    private List<E> requireEntities(List<Long> ids, CrudEnforcementDecision<E> decision) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        var entities =
                getRepository()
                        .findAll(
                                Specification.allOf(
                                        idInSpec(ids.stream().toList()),
                                        decision.scopeSpecification()));
        if (entities.size() != ids.stream().distinct().count()) {
            throw exception(GlobalErrorCode.CRUD_RESOURCE_NOT_FOUND, entityName());
        }
        return entities;
    }

    private List<E> requireLockedEntities(List<Long> ids, CrudEnforcementDecision<E> decision) {
        var entities = requireEntities(ids, decision);
        return lockAndRevalidateEntities(entities, decision.scopeSpecification());
    }

    /**
     * 校验实体所有者是否为当前主体。
     *
     * <p>仅在业务子类覆写 {@link #extractOwnerId(BaseEntity)} 后生效，适用于“仅创建者可编辑”等额外规则。
     * 它是数据范围校验之外的补充，不应替代统一安全管线。
     */
    protected void enforceOwnership(E entity) {
        var ownerId = extractOwnerId(entity);
        if (ownerId == null) {
            return;
        }
        var subjectId =
                operatorContext
                        .currentOwnerId()
                        .orElseThrow(() -> exception(GlobalErrorCode.UNAUTHORIZED));
        if (!ownerId.equals(subjectId)) {
            throw exception(GlobalErrorCode.CRUD_RESOURCE_NOT_FOUND, entityName());
        }
    }

    /** 构建单个 ID 的查询条件，仅供框架在已校验安全范围内组合使用 */
    private Specification<E> idSpec(Long id) {
        return (root, query, cb) -> cb.equal(root.get("id"), id);
    }

    /** 构建多个 ID 的查询条件，仅供框架在已校验安全范围内组合使用 */
    private Specification<E> idInSpec(List<Long> ids) {
        return (root, query, cb) -> root.get("id").in(ids);
    }

    /**
     * 将请求字段集规范化并校验是否在资源定义白名单中。
     *
     * <p>空值默认使用 list。保持私有可防止业务入口跳过字段集校验而输出未声明字段。
     */
    private String normalizeFieldSet(String fieldSet) {
        var normalized = fieldSet == null || fieldSet.isBlank() ? "list" : fieldSet.trim();
        if (!fieldSets().contains(normalized)) {
            throw exception(GlobalErrorCode.CRUD_FIELD_SET_UNSUPPORTED, normalized);
        }
        return normalized;
    }

    /**
     * 按资源定义的租户范围为新实体写入组织和工作区。
     *
     * <p>创建流程必须使用此方法，防止客户端或业务代码伪造跨租户归属；业务子类不应自行设置这些字段。
     */
    private void applyTenant(E entity, CrudEnforcementDecision<E> decision) {
        switch (resourceDefinition().tenantScope()) {
            case GLOBAL -> {
                entity.setOrgId(null);
                entity.setWorkspaceId(null);
            }
            case ORG_REQUIRED -> entity.setOrgId(decision.orgId());
            case WORKSPACE_REQUIRED, ORG_SHARED_WORKSPACE_OPTIONAL -> {
                entity.setOrgId(decision.orgId());
                entity.setWorkspaceId(decision.workspaceId());
            }
        }
    }

    /**
     * 确认实体的组织和工作区仍与当前安全决策一致。
     *
     * <p>更新后执行此校验，防止 {@link #updateEntity(BaseEntity, Object)} 或其他业务代码意外修改租户字段造成跨租户数据泄漏。
     */
    private void validateTenant(E entity, CrudEnforcementDecision<E> decision) {
        var valid =
                switch (resourceDefinition().tenantScope()) {
                    case GLOBAL -> entity.getOrgId() == null && entity.getWorkspaceId() == null;
                    case ORG_REQUIRED -> Objects.equals(entity.getOrgId(), decision.orgId());
                    case WORKSPACE_REQUIRED ->
                            Objects.equals(entity.getOrgId(), decision.orgId())
                                    && Objects.equals(
                                            entity.getWorkspaceId(), decision.workspaceId());
                    case ORG_SHARED_WORKSPACE_OPTIONAL ->
                            Objects.equals(entity.getOrgId(), decision.orgId())
                                    && (entity.getWorkspaceId() == null
                                            || Objects.equals(
                                                    entity.getWorkspaceId(),
                                                    decision.workspaceId()));
                };
        if (!valid) {
            throw exception(GlobalErrorCode.FORBIDDEN);
        }
    }

    /**
     * 检查请求中实际传入的每个字段是否具有 WRITE 权限。
     *
     * <p>未传入的 {@link Patch} 字段不会检查；已传入字段必须被当前字段策略允许。保持私有以确保创建、更新和导入使用同一规则。
     */
    private Set<String> requireWritableFields(Object input, CompiledFieldPolicy policy) {
        if (input == null) {
            throw exception(GlobalErrorCode.BAD_REQUEST);
        }
        var fields = new LinkedHashSet<String>();
        var inputBean = new BeanWrapperImpl(input);
        for (var descriptor : inputBean.getPropertyDescriptors()) {
            var field = descriptor.getName();
            if ("class".equals(field) || !inputBean.isReadableProperty(field)) {
                continue;
            }
            var value = inputBean.getPropertyValue(field);
            if (value == null || value instanceof Patch<?> patch && patch.isAbsent()) {
                continue;
            }
            policy.require(field, FieldCapability.WRITE);
            fields.add(field);
        }
        return Set.copyOf(fields);
    }

    private void validateEntityReferences(E entity, Object input, boolean create) {
        if (resourceDefinition().references().isEmpty()) {
            return;
        }
        var entityBean = new BeanWrapperImpl(entity);
        var inputBean = new BeanWrapperImpl(input);
        for (var reference : resourceDefinition().references()) {
            if (!reference.supports(
                            com.xuejiai.aaf.framework.crud.reference.ReferenceCapability.REFERENCE)
                    || !shouldValidateReference(reference.inputField(), inputBean, create)) {
                continue;
            }
            var id = entityBean.getPropertyValue(reference.idProperty());
            var resource =
                    reference.polymorphic()
                            ? entityBean.getPropertyValue(reference.resourceProperty())
                            : reference.targetResource().value();
            if (id == null && (!reference.polymorphic() || resource == null)) {
                continue;
            }
            if (!(id instanceof Long targetId)
                    || !(resource instanceof String targetResource)
                    || targetId <= 0
                    || targetResource.isBlank()) {
                throw exception(GlobalErrorCode.BAD_REQUEST);
            }
            var target =
                    new com.xuejiai.aaf.framework.crud.reference.ResourceReference(
                            targetResource, targetId);
            if (!referenceEnforcementService.canReference(
                    resourceDefinition().key(), entity.getId(), reference.key(), target)) {
                throw exception(GlobalErrorCode.CRUD_RESOURCE_NOT_FOUND, "引用记录");
            }
        }
    }

    private boolean shouldValidateReference(
            String inputField, BeanWrapperImpl inputBean, boolean create) {
        if (inputField.isBlank()) {
            return false;
        }
        if (create) {
            return true;
        }
        if (!inputBean.isReadableProperty(inputField)) {
            throw new IllegalStateException("引用输入字段不可读: " + inputField);
        }
        var value = inputBean.getPropertyValue(inputField);
        return !(value instanceof Patch<?> patch) || !patch.isAbsent();
    }

    /**
     * 在保存实体前校验请求中声明的关系 Patch。
     *
     * <p>关系处理器会检查数量、重复项、目标可引用性和租户边界。此步骤先于保存执行，防止写入无效或越权关联。
     */
    private void validateRelationPatches(
            E entity, Object input, CrudEnforcementDecision<E> decision) {
        relationPatches(input)
                .forEach(
                        (relation, patch) -> {
                            if (!patch.isAbsent()) {
                                genericRelationHandler.validate(
                                        resourceDefinition().key(), relation, entity, patch);
                            }
                        });
    }

    /**
     * 在主实体保存后同步请求中声明的关系 Patch。
     *
     * <p>例如按 REPLACE 模式更新参与人。必须在主实体获得 ID 后执行，且只处理已通过前置校验的关系；业务子类不应手工重复同步。
     */
    private void synchronizeRelationPatches(
            E entity, Object input, CrudEnforcementDecision<E> decision) {
        relationPatches(input)
                .forEach(
                        (relation, patch) -> {
                            if (!patch.isAbsent()) {
                                genericRelationHandler.synchronize(relation, entity, patch);
                            }
                        });
    }

    /** 从请求对象中提取资源定义声明的关系 Patch，未声明关系时返回空集合 */
    private Map<RelationDefinition<?, ?>, Patch<?>> relationPatches(Object input) {
        if (resourceDefinition().mutation().relationFields().isEmpty()) {
            return Map.of();
        }
        var inputBean = new BeanWrapperImpl(input);
        var patches = new LinkedHashMap<RelationDefinition<?, ?>, Patch<?>>();
        resourceDefinition()
                .mutation()
                .relationFields()
                .forEach(
                        (relationKey, inputField) -> {
                            if (!inputBean.isReadableProperty(inputField)) {
                                throw new IllegalStateException("关系 Patch 输入字段不可读: " + inputField);
                            }
                            var rawPatch = inputBean.getPropertyValue(inputField);
                            var patch = rawPatch == null ? Patch.absent() : rawPatch;
                            if (!(patch instanceof Patch<?> typedPatch)) {
                                throw new IllegalStateException("关系输入字段必须使用 Patch: " + inputField);
                            }
                            patches.put(relationDefinition(relationKey), typedPatch);
                        });
        return Map.copyOf(patches);
    }

    /** 按关系 key 查找资源定义；不存在表示资源声明和调用逻辑不一致，立即失败 */
    private RelationDefinition<?, ?> relationDefinition(String relationKey) {
        return resourceDefinition().relations().stream()
                .filter(relation -> relation.key().equals(relationKey))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Catalog 缺少关系: " + relationKey));
    }

    /** 将一条实体转换为输出视图；复用批量视图管线，保证单条和列表的字段权限一致 */
    private V toView(E entity, String fieldSet, CrudEnforcementDecision<E> decision) {
        return toViews(List.of(entity), fieldSet, decision).getFirst();
    }

    /**
     * 将实体列表转换为当前字段集允许输出的视图。
     *
     * <p>框架先按 READ 字段权限收窄视图计划，再按需批量加载关系数据，最后使用 {@link CrudViewMapper} 或业务转换方法生成视图，
     * 并再次裁剪字段作为最终安全防线。业务子类不应绕过本方法直接返回 VO。
     */
    private List<V> toViews(
            List<E> entities, String fieldSet, CrudEnforcementDecision<E> decision) {
        if (entities.isEmpty()) {
            return List.of();
        }
        var plan =
                resourceEntry()
                        .viewPlans()
                        .get(fieldSet)
                        .restrictTo(decision.fieldPolicy().fields(FieldCapability.READ));
        if (!plan.usesViewMapper()) {
            return toVOList(entities, fieldSet).stream()
                    .map(view -> applyReadFieldPolicy(view, decision.fieldPolicy()))
                    .toList();
        }
        var viewData = loadViewData(entities, plan, decision);
        var mapper = viewMapper(plan);
        return entities.stream()
                .map(
                        entity ->
                                mapper.toView(
                                        entity,
                                        (Class<V>) resourceDefinition().types().viewType(),
                                        plan,
                                        viewData))
                .map(view -> applyReadFieldPolicy(view, decision.fieldPolicy()))
                .toList();
    }

    /**
     * 根据视图计划批量加载输出视图所需的关系数据。
     *
     * <p>只加载当前字段集和 READ 权限仍需要的关系，避免加载无权字段或产生 N+1 查询。Loader 返回 null 视为实现错误并立即失败。
     */
    private CrudViewData loadViewData(
            List<E> entities, CrudViewPlan plan, CrudEnforcementDecision<E> decision) {
        if (plan.relationKeys().isEmpty()) {
            return CrudViewData.empty();
        }
        var loaded = new LinkedHashMap<String, Map<Long, ?>>();
        plan.relationKeys()
                .forEach(
                        key -> {
                            var reference =
                                    resourceDefinition().references().stream()
                                            .filter(candidate -> candidate.key().equals(key))
                                            .findFirst();
                            if (reference.isPresent()) {
                                loaded.put(
                                        key,
                                        genericRelationLoader.loadReference(
                                                resourceDefinition().key(),
                                                reference.get(),
                                                entities));
                                return;
                            }
                            var relation = relationDefinition(key);
                            loaded.put(
                                    key,
                                    genericRelationLoader.loadRelation(
                                            resourceDefinition().key(), relation, entities));
                        });
        return CrudViewData.of(loaded);
    }

    /** 从视图计划指定的 Bean 获取输出视图映射器；Bean 类型已在启动期校验 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private CrudViewMapper<E, V> viewMapper(CrudViewPlan plan) {
        return (CrudViewMapper)
                applicationContext.getBean(plan.viewMapperBean(), CrudViewMapper.class);
    }

    /**
     * 最终输出安全检查：移除当前用户没有 READ 权限的字段。
     *
     * <p>即使业务转换或视图映射器意外填充了未授权字段，本方法仍会在响应前删除它们。它是最后一道防线， 不能由业务子类替代或跳过。
     */
    @SuppressWarnings("unchecked")
    private V applyReadFieldPolicy(V view, CompiledFieldPolicy policy) {
        if (view == null) {
            return null;
        }
        var denied =
                policy.capabilities().keySet().stream()
                        .filter(field -> !policy.allows(field, FieldCapability.READ))
                        .collect(Collectors.toSet());
        if (denied.isEmpty()) {
            return view;
        }
        var values = (Map<String, Object>) JsonUtils.convertValue(view, LinkedHashMap.class);
        denied.forEach(values::remove);
        if (view instanceof Map<?, ?>) {
            return (V) Map.copyOf(values);
        }
        var converted = JsonUtils.convertValue(values, view.getClass());
        return (V) view.getClass().cast(converted);
    }

    /** 为默认未实现的导入、分组等操作返回统一的“不支持”错误；业务子类覆写对应公开方法后不再经过这里 */
    private <T> T unsupported(String operation) {
        throw exception(GlobalErrorCode.CRUD_OPERATION_UNSUPPORTED, entityName(), operation);
    }
}

package com.xuejiai.aaf.framework.crud;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Table;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.security.access.FieldAccessSupport;
import com.xuejiai.aaf.framework.security.access.RecordRuleSupport;

/**
 * 通用 CRUD Service 基类。继承此类即可获得以下完整能力：
 *
 * <ul>
 *   <li><b>查询</b>：分页查询 {@link #page}、查询窗口 {@link #queryWindow}、单条查询 {@link #getById}、 批量读取 {@link
 *       #batchRead}、选择器选项 {@link #options}
 *   <li><b>写入</b>：创建 {@link #create}、批量创建 {@link #createBatch}、更新 {@link #update}
 *   <li><b>删除</b>：单条删除 {@link #delete}、批量删除 {@link #deleteBatch}、 归档 {@link #archive}、恢复 {@link
 *       #restore}
 *   <li><b>数据权限</b>：行级权限由 {@link RecordRuleSupport} 自动注入；字段级权限由 {@link FieldAccessSupport}
 *       自动过滤（所有返回 VO 的方法均已应用）
 *   <li><b>元数据</b>：{@link #meta} 返回实体标识、字段集、可用操作，供 AI 动作注册表消费
 *   <li><b>导出/导入</b>：{@link #exportData} 默认启用；{@link #importRows} 需子类覆写
 *   <li><b>扩展点</b>：子类可覆写 {@link #buildSpec}、{@link #toVO(BaseEntity, String)}、 {@link
 *       #defaultSort}、{@link #displayName}、{@link #buildOptionSpec} 等方法定制行为
 * </ul>
 *
 * <p>子类必须实现：{@link #getRepository}、{@link #getSpecExecutor}、{@link #toVO(BaseEntity)}、 {@link
 * #toEntity}、{@link #updateEntity}。
 *
 * @param <E> 实体类型，必须继承 {@link BaseEntity}
 * @param <V> 响应 VO 类型
 * @param <C> 创建 DTO 类型
 * @param <U> 更新 DTO 类型
 * @param <P> 分页查询 DTO 类型，必须继承 {@link PageParam}
 */
public abstract class BaseCrudService<E extends BaseEntity, V, C, U, P extends PageParam> {

    /** 批量读取单次最大条数，防止超长 IN 子句拖垮数据库。 */
    protected static final int BATCH_READ_MAX_SIZE = 200;

    @Autowired(required = false)
    private ObjectProvider<RecordRuleSupport> recordRuleSupport;

    @Autowired(required = false)
    private OperatorContext operatorContext;

    @Autowired(required = false)
    private ObjectProvider<FieldAccessSupport> fieldAccessSupport;

    @PersistenceContext
    private EntityManager entityManager;

    /** 子类提供 Repository 实例。 */
    protected abstract JpaRepository<E, Long> getRepository();

    /** 子类提供 JpaSpecificationExecutor（通常与 Repository 是同一个接口）。 */
    protected abstract JpaSpecificationExecutor<E> getSpecExecutor();

    /** 实体转 VO。 */
    protected abstract V toVO(E entity);

    /** 创建 DTO 转实体。 */
    protected abstract E toEntity(C createDTO);

    /** 更新 DTO 应用到已有实体。 */
    protected abstract void updateEntity(E entity, U updateDTO);

    /**
     * 构建业务查询条件，默认无条件。
     *
     * <p>只处理页面筛选、搜索等业务参数；分页和排序由 {@link PageParam#toPageable(Sort)} 处理， 行级数据权限由 {@link
     * #buildAccessSpec()} 处理。
     */
    protected Specification<E> buildSpec(P pageDTO) {
        return (root, query, cb) -> null;
    }

    /** 构建行级数据权限条件。默认通过 RecordRuleSupport 注入，子类仍可覆写追加特殊规则。 */
    protected Specification<E> buildAccessSpec() {
        var slug = entitySlug();
        if (slug == null || slug.isBlank()) {
            return (root, query, cb) -> null;
        }
        var support = recordRuleSupport == null ? null : recordRuleSupport.getIfAvailable();
        if (support == null || operatorContext == null) {
            return (root, query, cb) -> null;
        }
        var userId = operatorContext.currentOwnerId().orElse(null);
        if (userId == null) {
            return (root, query, cb) -> cb.disjunction();
        }
        Specification<E> spec = support.buildAccessSpec(slug, userId);
        return spec == null ? (root, query, cb) -> null : spec;
    }

    /** 合并业务查询条件与行级数据权限条件。 */
    protected Specification<E> buildEffectiveSpec(P pageDTO) {
        return Specification.allOf(buildSpec(pageDTO), buildAccessSpec());
    }

    /** 默认排序，子类可覆写。 */
    protected Sort defaultSort() {
        return Sort.by("id").descending();
    }

    /** 分页查询。 */
    public PageResult<V> page(P request) {
        var pageable = request.toPageable(defaultSort());
        Page<E> page = getSpecExecutor().findAll(buildEffectiveSpec(request), pageable);
        return new PageResult<>(
                page.getContent().stream()
                        .map(this::toVO)
                        .map(vo -> applyFieldAccess(vo, "read"))
                        .toList(),
                page.getTotalElements(),
                request.getPageNo(),
                request.getPageSize(),
                List.of(),
                null,
                null,
                page.hasNext());
    }

    /** 查询窗口。用于列表打开详情后的快速切换和前端缓存复用。 */
    public PageResult<V> queryWindow(P request, String fieldSet) {
        var pageable = request.toPageable(defaultSort());
        Page<E> page = getSpecExecutor().findAll(buildEffectiveSpec(request), pageable);
        var entities = page.getContent();
        var ids = entities.stream().map(BaseEntity::getId).toList();
        var list =
                entities.stream()
                        .map(entity -> toVO(entity, normalizeFieldSet(fieldSet)))
                        .map(vo -> applyFieldAccess(vo, "read"))
                        .toList();
        return new PageResult<>(
                list,
                page.getTotalElements(),
                request.getPageNo(),
                request.getPageSize(),
                ids,
                buildQueryToken(normalizeFieldSet(fieldSet), ids),
                normalizeFieldSet(fieldSet),
                page.hasNext());
    }

    /** 查询单条记录。 */
    public V getById(Long id) {
        return applyFieldAccess(toVO(requireEntity(id)), "read");
    }

    /** 按字段集查询单条记录。queryToken 校验由子类或后续 QueryWindowService 接入。 */
    public V getById(Long id, String queryToken, String fieldSet) {
        var normalizedFieldSet = normalizeFieldSet(fieldSet);
        validateQueryToken(id, queryToken, normalizedFieldSet);
        return applyFieldAccess(toVO(requireEntity(id), normalizedFieldSet), "read");
    }

    /**
     * 批量读取。用于详情相邻记录预取和批量操作前确认。
     *
     * <p>ids 数量上限为 {@link #BATCH_READ_MAX_SIZE}，超出时抛 {@link BusinessException}， 防止生成超长 {@code IN}
     * 子句拖垮数据库。
     */
    public List<V> batchRead(List<Long> ids, String fieldSet) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        if (ids.size() > BATCH_READ_MAX_SIZE) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "批量读取数量不得超过 " + BATCH_READ_MAX_SIZE + " 条");
        }
        var normalizedFieldSet = normalizeFieldSet(fieldSet == null ? "detail" : fieldSet);
        var entitiesById = new LinkedHashMap<Long, E>();
        getSpecExecutor()
                .findAll(Specification.allOf(idInSpec(ids), buildAccessSpec()))
                .forEach(entity -> entitiesById.put(entity.getId(), entity));
        return ids.stream()
                .map(entitiesById::get)
                .filter(Objects::nonNull)
                .map(entity -> applyFieldAccess(toVO(entity, normalizedFieldSet), "read"))
                .toList();
    }

    /** 选择器选项。子类可覆写 buildOptionSpec/displayName 以提供更精准搜索和展示。 */
    public List<CrudOption> options(String keyword, Integer limit) {
        var safeLimit = limit == null ? 20 : Math.max(1, Math.min(limit, 100));
        var page =
                getSpecExecutor()
                        .findAll(
                                buildOptionSpec(keyword),
                                PageRequest.of(0, safeLimit, defaultSort()));
        return page.getContent().stream()
                .map(entity -> new CrudOption(entity.getId(), displayName(entity)))
                .toList();
    }

    /** 通用 CRUD 元数据。 */
    public CrudMeta meta() {
        return new CrudMeta(entitySlug(), entityName(), fieldSets(), operations());
    }

    /** 实体标识。用于 AI 业务动作、前端实体引擎和审计日志。 */
    public String getEntitySlug() {
        return entitySlug();
    }

    /** 实体名称。用于 AI 业务动作结果和错误提示。 */
    public String getEntityName() {
        return entityName();
    }

    /** 可用操作清单。用于 AI 业务动作注册表对外声明能力。 */
    public List<String> getOperations() {
        return operations();
    }

    /** 解析 CRUD 动作对应的权限码。 */
    public String resolvePermissionCode(String action) {
        return permissionCode(action);
    }

    /** 导出数据。默认返回过滤后的完整 export 字段集数据，文件生成由子类或上层适配。 */
    public PageResult<V> exportData(P request) {
        request.setPageNo(1);
        request.setPageSize(PageParam.PAGE_SIZE_NONE);
        return queryWindow(request, "export");
    }

    /** JSON 导入。默认未启用，业务子类确认语义后覆写。 */
    public CrudImportResult importRows(CrudImportRequest<C> request) {
        return unsupported("导入");
    }

    /** 分组聚合。默认未启用，业务子类确认字段白名单后覆写。 */
    public List<CrudGroupResult> group(CrudGroupRequest request) {
        return unsupported("分组聚合");
    }

    /** 创建/更新前预校验。默认通过，子类可补充唯一性等业务校验。 */
    public CrudValidationResult validate(C request) {
        return CrudValidationResult.success();
    }

    /** 创建。返回的 VO 已应用字段级权限过滤。 */
    @Transactional
    public V create(C request) {
        E entity = toEntity(request);
        getRepository().save(entity);
        return applyFieldAccess(toVO(entity), "read");
    }

    /**
     * 批量创建。事务内全部成功或全部回滚。
     *
     * <p>使用 {@link JpaRepository#saveAll} 单次批量 INSERT，效率优于循环调用 {@link #create}。 返回的每条 VO
     * 均已应用字段级权限过滤。
     */
    @Transactional
    public List<V> createBatch(List<C> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        var entities = requests.stream().map(this::toEntity).toList();
        getRepository().saveAll(entities);
        return entities.stream().map(this::toVO).map(vo -> applyFieldAccess(vo, "read")).toList();
    }

    /** 更新。返回的 VO 已应用字段级权限过滤。 */
    @Transactional
    public V update(Long id, U request) {
        E entity = requireEntity(id);
        updateEntity(entity, request);
        getRepository().save(entity);
        return applyFieldAccess(toVO(entity), "read");
    }

    /** 删除。 */
    @Transactional
    public void delete(Long id) {
        E entity = requireEntity(id);
        getRepository().delete(entity);
    }

    /**
     * 批量删除。先经行级数据权限校验，确认所有记录可见后再删除。
     *
     * <p>用原生 SQL 一条 UPDATE 批量软删除，避免逐条触发 {@code @SQLDelete} 的 N 次请求。
     */
    @Transactional
    public void deleteBatch(List<Long> ids) {
        requireEntities(ids);
        String tableName = resolveTableName();
        if (tableName != null) {
            entityManager.createNativeQuery(
                    "UPDATE " + tableName
                    + " SET deleted = true, delete_time = CURRENT_TIMESTAMP"
                    + " WHERE id IN (:ids) AND deleted = false")
                    .setParameter("ids", ids)
                    .executeUpdate();
        } else {
            // 兜底：逐条软删除（触发 @SQLDelete）
            getRepository().deleteAll(getRepository().findAllById(ids));
        }
    }

    /** 通过泛型参数上的 @Table 注解解析表名，避免查库。 */
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

    /** 归档。默认采用逻辑删除语义；如业务有 archived 状态，子类应覆写。 */
    @Transactional
    public void archive(List<Long> ids) {
        deleteBatch(ids);
    }

    /** 恢复。默认未启用；逻辑删除恢复需要绕过 @SQLRestriction，业务子类确认后覆写。 */
    @Transactional
    public void restore(List<Long> ids) {
        throw new BusinessException(GlobalErrorCode.BAD_REQUEST, entityName() + "恢复能力未启用");
    }

    /** 实体名称，用于错误提示。子类可覆写。 */
    protected String entityName() {
        return "记录";
    }

    /** 字段集转换。子类可覆写以提供 list/detail/picker 等不同 VO。 */
    protected V toVO(E entity, String fieldSet) {
        return toVO(entity);
    }

    /** 选择器搜索条件。默认无条件，子类可按 name/title/code 等字段覆写。 */
    protected Specification<E> buildOptionSpec(String keyword) {
        return (root, query, cb) -> null;
    }

    /** 选择器显示名。默认尝试读取 name/title/code/username/displayName，均无则返回 实体名#ID。 */
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

    /** 可用字段集。 */
    protected List<String> fieldSets() {
        return List.of("list", "detail", "picker");
    }

    /** 可用通用操作。 */
    protected List<String> operations() {
        return List.of(
                "page",
                "queryWindow",
                "get",
                "create",
                "update",
                "delete",
                "deleteBatch",
                "batchRead",
                "options",
                "meta",
                "export",
                "import",
                "batchDelete",
                "group",
                "validate",
                "archive",
                "restore");
    }

    /** 权限码模块段，子类可覆写。 */
    protected String permissionModule() {
        return "system";
    }

    /** 权限码资源段，子类可覆写。 */
    protected String permissionResource() {
        return entitySlug();
    }

    /** 将 CRUD 动作解析为最终权限码，子类可覆写以支持 manage 等聚合权限。 */
    protected String permissionCode(String action) {
        if (action == null || action.isBlank()) {
            return null;
        }
        return "%s:%s:%s".formatted(permissionModule(), permissionResource(), action.trim());
    }

    /** 查询窗口实体标识。子类可覆写为稳定 entitySlug。 */
    protected String entitySlug() {
        var slug = entityName().trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "-");
        return slug.isBlank() ? "entity" : slug;
    }

    /** 查询窗口 token 校验。为空时允许直接详情查询；有值时必须匹配当前用户、实体、字段集、权限版本与窗口 ID。 */
    protected void validateQueryToken(Long id, String queryToken, String fieldSet) {
        if (queryToken == null || queryToken.isBlank()) {
            return;
        }
        try {
            validateQueryTokenPayload(id, queryToken, fieldSet);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "查询窗口已失效");
        }
    }

    private void validateQueryTokenPayload(Long id, String queryToken, String fieldSet) {
        var parts = queryToken.split("\\.", 3);
        if (parts.length != 3 || !"v1".equals(parts[0])) {
            throw new IllegalArgumentException("invalid token format");
        }
        var payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        if (!sha256(parts[1]).equals(parts[2])) {
            throw new IllegalArgumentException("invalid token signature");
        }
        var values = payload.split("\\|", -1);
        if (values.length != 5) {
            throw new IllegalArgumentException("invalid token payload");
        }
        var userId = currentOwnerId();
        var accessVersion = currentAccessVersion();
        if (!entitySlug().equals(values[0])
                || !String.valueOf(userId).equals(values[1])
                || !fieldSet.equals(values[2])
                || !accessVersion.equals(values[3])) {
            throw new IllegalArgumentException("stale token");
        }
        var ids =
                values[4].isBlank()
                        ? List.<Long>of()
                        : Arrays.stream(values[4].split(",")).map(Long::valueOf).toList();
        if (!ids.contains(id)) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, entityName() + "不在当前查询窗口中");
        }
    }

    /** 根据 ID 查询实体，不存在则抛异常。 */
    protected E requireEntity(Long id) {
        return getSpecExecutor()
                .findOne(Specification.allOf(idSpec(id), buildAccessSpec()))
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        GlobalErrorCode.NOT_FOUND, entityName() + "不存在"));
    }

    /** 根据 ID 列表查询实体；任一记录不存在或不可见时按 404 处理，避免泄露存在性。 */
    protected List<E> requireEntities(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        var entities =
                getSpecExecutor().findAll(Specification.allOf(idInSpec(ids), buildAccessSpec()));
        if (entities.size() != ids.stream().distinct().count()) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, entityName() + "不存在");
        }
        return entities;
    }

    private Specification<E> idSpec(Long id) {
        return (root, query, cb) -> cb.equal(root.get("id"), id);
    }

    private Specification<E> idInSpec(List<Long> ids) {
        return (root, query, cb) -> root.get("id").in(ids);
    }

    private String normalizeFieldSet(String fieldSet) {
        return fieldSet == null || fieldSet.isBlank() ? "list" : fieldSet.trim();
    }

    private String buildQueryToken(String fieldSet, List<Long> ids) {
        var payload =
                "%s|%s|%s|%s|%s"
                        .formatted(
                                entitySlug(),
                                currentOwnerId(),
                                fieldSet,
                                currentAccessVersion(),
                                ids.stream().map(String::valueOf).collect(Collectors.joining(",")));
        var encodedPayload =
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return "v1.%s.%s".formatted(encodedPayload, sha256(encodedPayload));
    }

    private Long currentOwnerId() {
        return operatorContext == null ? null : operatorContext.currentOwnerId().orElse(null);
    }

    private String currentAccessVersion() {
        var support = recordRuleSupport == null ? null : recordRuleSupport.getIfAvailable();
        var userId = currentOwnerId();
        return support == null || userId == null
                ? "0"
                : support.accessVersion(entitySlug(), userId);
    }

    private String sha256(String source) {
        try {
            var digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(source.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private <T> T unsupported(String operation) {
        throw new BusinessException(
                GlobalErrorCode.BAD_REQUEST, entityName() + operation + "能力未启用");
    }

    @SuppressWarnings("unchecked")
    private V applyFieldAccess(V vo, String action) {
        var support = fieldAccessSupport == null ? null : fieldAccessSupport.getIfAvailable();
        var userId = currentOwnerId();
        if (support == null || vo == null || userId == null) {
            return vo;
        }
        var hiddenFields = support.hiddenFields(entitySlug(), userId, action);
        if (hiddenFields == null || hiddenFields.isEmpty()) {
            return vo;
        }
        if (vo instanceof java.util.Map<?, ?> map) {
            var copy = new java.util.LinkedHashMap<>((java.util.Map<String, Object>) map);
            hiddenFields.forEach(copy::remove);
            return (V) copy;
        }
        var bean = new BeanWrapperImpl(vo);
        var visible = new java.util.LinkedHashMap<String, Object>();
        for (var descriptor : BeanUtils.getPropertyDescriptors(vo.getClass())) {
            var name = descriptor.getName();
            if ("class".equals(name)
                    || hiddenFields.contains(name)
                    || !bean.isReadableProperty(name)) {
                continue;
            }
            visible.put(name, bean.getPropertyValue(name));
        }
        return (V) visible;
    }
}

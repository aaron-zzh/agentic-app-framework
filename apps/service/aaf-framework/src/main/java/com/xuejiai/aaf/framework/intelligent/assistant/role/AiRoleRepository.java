/**
 * AI Role 仓储。
 *
 * <p>继承的 {@code findAll / findById / findAllById} 等无条件读取只供 CRUD 管线（管理端）使用； 其他入口一律使用下面带可见性条件的查询方法，见
 * {@link RoleVisibility}。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.assistant.role;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;

public interface AiRoleRepository extends CrudEntityRepository<Role> {

    /** 按 code 取指定可见性的角色；传入 PRIVATE 之外的集合时才不会读到私有角色。 */
    Optional<Role> findByCodeAndVisibilityIn(String code, Collection<RoleVisibilityLevel> levels);

    /** code 是否已被占用（含所有可见性，用于生成与校验 code 唯一）。 */
    boolean existsByCode(String code);

    /** 按 ID 取对用户可见的角色，不可见等同不存在。 */
    @Query("SELECT r FROM AiRole r WHERE r.id = :id AND " + RoleVisibility.VISIBLE_TO_USER)
    Optional<Role> findVisibleById(@Param("id") Long id, @Param("userId") Long userId);

    /** 按 ID 批量取对用户可见的角色。 */
    @Query("SELECT r FROM AiRole r WHERE r.id IN :ids AND " + RoleVisibility.VISIBLE_TO_USER)
    List<Role> findVisibleByIds(@Param("ids") Collection<Long> ids, @Param("userId") Long userId);

    /** 角色库：对用户可见且处于可用状态的全部角色。 */
    @Query(
            "SELECT r FROM AiRole r WHERE r.status = 'active' AND "
                    + RoleVisibility.VISIBLE_TO_USER
                    + " ORDER BY r.id")
    List<Role> findLibrary(@Param("userId") Long userId);

    /** 所有者的全部私有角色。 */
    @Query(
            "SELECT r FROM AiRole r WHERE "
                    + RoleVisibility.PRIVATE_OF_OWNER
                    + " ORDER BY r.id DESC")
    List<Role> findPrivateByOwner(@Param("ownerId") Long ownerId);

    /** 按 ID 取所有者自己的私有角色，他人的私有角色等同不存在。 */
    @Query("SELECT r FROM AiRole r WHERE r.id = :id AND " + RoleVisibility.PRIVATE_OF_OWNER)
    Optional<Role> findPrivateByIdAndOwner(@Param("id") Long id, @Param("ownerId") Long ownerId);

    /**
     * 反向引用查询：引用了指定技能的全部角色（含私有角色）。
     *
     * @param skillRef 形如 {@code [{"skillKey":"x"}]} 的 JSON，用 JSONB 包含运算匹配 {@code skill_ids}
     */
    @Query(
            value =
                    "SELECT * FROM ai_role WHERE deleted = FALSE"
                            + " AND skill_ids @> CAST(:skillRef AS jsonb)",
            nativeQuery = true)
    List<Role> findReferencingSkill(@Param("skillRef") String skillRef);
}

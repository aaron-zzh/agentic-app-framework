package com.xuejiai.aaf.module.system.user.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.module.system.user.domain.User;
import com.xuejiai.aaf.module.system.user.vo.UserSimpleVO;

/**
 * 用户数据访问层。Hibernate @SoftDelete 自动过滤已删除记录。
 *
 * @author AaronZZH & Kiro
 */
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    Optional<User> findByContactId(Long contactId);

    /** 批量按 contact_id 查询用户（用于"我邀请的好友"等聚合场景）。 */
    List<User> findByContactIdIn(java.util.Collection<Long> contactIds);

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);

    /** 查询简要列表（仅 id/username/nickname），用于下拉选择等场景。 */
    @Query(
            "SELECT new com.xuejiai.aaf.module.system.user.vo.UserSimpleVO(u.id, u.username, u.nickname) FROM User u")
    List<UserSimpleVO> findSimpleList();

    /** 查询指定状态的简要列表。 */
    @Query(
            "SELECT new com.xuejiai.aaf.module.system.user.vo.UserSimpleVO(u.id, u.username, u.nickname) FROM User u WHERE u.status = :status")
    List<UserSimpleVO> findSimpleListByStatus(Integer status);

    /** 查询可分配的启用用户，支持用户名和昵称关键字匹配。 */
    @Query(
            """
            SELECT new com.xuejiai.aaf.module.system.user.vo.UserSimpleVO(u.id, u.username, u.nickname)
            FROM User u
            WHERE u.status = :status
              AND (:keyword = '' OR lower(u.username) LIKE lower(concat('%', :keyword, '%'))
                   OR lower(coalesce(u.nickname, '')) LIKE lower(concat('%', :keyword, '%')))
            ORDER BY u.nickname ASC, u.username ASC
            """)
    List<UserSimpleVO> findPickerOptions(
            @Param("status") Integer status, @Param("keyword") String keyword, Pageable pageable);
}

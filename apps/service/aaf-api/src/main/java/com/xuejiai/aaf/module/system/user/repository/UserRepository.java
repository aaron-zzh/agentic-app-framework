package com.xuejiai.aaf.module.system.user.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.xuejiai.aaf.module.system.user.domain.User;
import com.xuejiai.aaf.module.system.user.vo.UserSimpleVO;

/**
 * 用户数据访问层。Hibernate @SoftDelete 自动过滤已删除记录。
 *
 * @author AaronZZH & Kiro
 */
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    Optional<User> findByEmail(String email);

    /** 查询简要列表（仅 id/username/nickname），用于下拉选择等场景。 */
    @Query(
            "SELECT new com.xuejiai.aaf.module.system.user.vo.UserSimpleVO(u.id, u.username, u.nickname) FROM User u")
    List<UserSimpleVO> findSimpleList();

    /** 查询指定状态的简要列表。 */
    @Query(
            "SELECT new com.xuejiai.aaf.module.system.user.vo.UserSimpleVO(u.id, u.username, u.nickname) FROM User u WHERE u.status = :status")
    List<UserSimpleVO> findSimpleListByStatus(Integer status);
}

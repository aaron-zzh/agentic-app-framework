package com.xuejiai.aaf.framework.crud;

/**
 * BaseCrudService 乐观锁行为单元测试。
 *
 * <p>暂未启用 Hibernate @Version 乐观锁（已从 BaseEntity 去掉 @Version 注解，以解决
 * @SQLDelete 软删除时的参数绑定冲突）。待未来手动实现乐观锁时恢复此测试。
 *
 * @author Kiro
 */
class BaseCrudServiceOptimisticLockTest {
    // 乐观锁测试暂时禁用，原测试内容已注释保留在 git history
}

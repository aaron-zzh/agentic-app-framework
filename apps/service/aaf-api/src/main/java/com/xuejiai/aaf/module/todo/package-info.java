/**
 * 待办模块：标准业务模块参考实现，演示一个业务资源接入 AAF CRUD 框架的完整写法。
 *
 * <p>目录与职责：
 *
 * <ul>
 *   <li>{@code domain}：JPA 实体（{@code Todo}）与多对多关联实体（{@code TodoParticipant}），用注解声明引用与关联
 *   <li>{@code repository}：数据访问，继承框架的 {@code CrudEntityRepository}
 *   <li>{@code vo}：创建/更新/分页请求与输出视图；更新请求使用 {@code Patch} 区分"缺失、null、有值"
 *   <li>{@code mapper}：MapStruct 创建转换；更新由 Patch 逐字段应用，输出由框架视图装配
 *   <li>{@code service}：{@code TodoService} 实现扩展点、协作者分享、指派通知；{@code TodoQueueService} 演示异步队列任务；
 *       {@code TodoUserReferencePolicy} 演示引用附加策略
 *   <li>{@code controller}：继承 {@code BaseCrudController} 获得标准 REST 端点，仅声明业务专属端点
 *   <li>{@link com.xuejiai.aaf.module.todo.TodoResource}：资源定义（路径、字段集、能力、筛选排序、具名命令、租户与个人范围）
 *   <li>{@link com.xuejiai.aaf.module.todo.ErrorCodeConstants}：模块独立错误码段
 * </ul>
 *
 * <p>框架能力对照：Patch 部分更新与乐观锁、字段集输出、个人范围隔离、ReBAC 协作者（分享/撤销/列出）、 具名自定义 UPDATE 命令、系统任务与管理维护两种受控访问模式、
 * 事务提交后通知、持久化异步队列任务。
 */
package com.xuejiai.aaf.module.todo;

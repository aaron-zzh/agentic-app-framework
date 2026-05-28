/**
 * 异步任务调度框架：定时任务 + 异步队列 + 重试 + 分布式锁。
 *
 * <p>职责：后台异步任务的入队、消费、定时触发、失败重试。 典型场景：定时清理过期数据、异步发送通知、批量导入处理。
 *
 * <p><b>与元引擎 runtime 的区别</b>：
 *
 * <ul>
 *   <li>本包 = 后台任务队列（无交互，异步，类似 Celery / Spring Scheduler）
 *   <li>元引擎 runtime = 编排执行引擎（有交互，可暂停等人确认，类似 Flowable Engine）
 *   <li>元引擎执行编排时，可将长时间子任务委托给本包异步执行——是调用关系，不是替代关系
 * </ul>
 */
package com.xuejiai.aaf.framework.task;

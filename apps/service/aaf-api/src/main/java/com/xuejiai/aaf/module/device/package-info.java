/**
 * 通用设备层：机器人等语音终端的配对绑定、设备认证、MQTT 设备通道、在线状态、简报与语音网关（ASR、对话、TTS）。
 *
 * <p>本模块与具体业务场景无关。养老、儿童教育、办公助理、情感陪伴等场景通过 {@code scenario} 包中的 {@code DeviceScenario}
 * 扩展点接入，由场景包提供默认角色、简报补充、对话前后处理、入站事件处理与工具上下文。
 *
 * <p>依赖规则：device 不得依赖任何具体场景模块；场景模块可以依赖 device 的 {@code api} 与 {@code scenario} 包。
 *
 * <ul>
 *   <li>{@code domain}、{@code repository}、{@code service}、{@code controller}、{@code vo}、{@code
 *       mapper}：设备与设备消息的 CRUD
 *   <li>{@code security}：设备凭据认证，独立过滤链只匹配 {@code /api/device/**}
 *   <li>{@code mqtt}：MQTT 适配、信封与 Ed25519 签名、Outbox 发布
 *   <li>{@code api}：对场景模块开放的接口（下发消息、入站处理、连接控制）
 * </ul>
 */
package com.xuejiai.aaf.module.device;

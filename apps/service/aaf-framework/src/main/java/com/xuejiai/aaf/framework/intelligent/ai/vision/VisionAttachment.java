package com.xuejiai.aaf.framework.intelligent.ai.vision;

/**
 * 视觉附件——前端上传的 OSS fileKey 经后端解析后的不可变载体。
 *
 * <p>设计原则：
 *
 * <ul>
 *   <li>前端只传 fileKey，不传 URL（避免 URL 伪造与过期问题）
 *   <li>后端 {@link VisionMediaResolver} 从 sys_file 读取 mimeType，从 StorageService 生成签名 URL
 *   <li>该对象后续被两条业务路径消费：Spring AI 链路转 {@code Spring AI Media}、AgentScope 链路转 {@code Msg
 *       ImageBlock/VideoBlock}
 * </ul>
 *
 * @param fileKey OSS 内部 key（与 sys_file.file_key 一致）
 * @param mimeType MIME 类型，如 image/png、video/mp4，必须非空
 * @param signedUrl OSS 预签名 GET URL，调用方使用前需自行判断是否在有效期内
 * @param type 附件类型，由 mimeType 解析得到
 */
public record VisionAttachment(
        String fileKey, String mimeType, String signedUrl, AttachmentType type) {

    /** 视觉附件类型——按 mimeType 前缀简单分类。 */
    public enum AttachmentType {
        /** image/* */
        IMAGE,
        /** video/* */
        VIDEO
    }
}

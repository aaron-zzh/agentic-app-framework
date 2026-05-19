package com.xuejiai.aaf.framework.messaging.email;

/**
 * 邮件附件。
 *
 * @param filename 文件名
 * @param content 文件内容
 * @param contentType MIME 类型
 */
public record Attachment(String filename, byte[] content, String contentType) {}

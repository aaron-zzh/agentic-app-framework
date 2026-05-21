package com.xuejiai.aaf.framework.storage;

/** 文件上传结果。 */
public record FileVO(String key, String url, String filename, long size, String contentType) {}

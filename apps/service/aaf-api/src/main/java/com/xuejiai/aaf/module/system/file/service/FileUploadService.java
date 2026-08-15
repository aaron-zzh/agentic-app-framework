package com.xuejiai.aaf.module.system.file.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.xuejiai.aaf.framework.storage.StorageClient;
import com.xuejiai.aaf.framework.storage.StorageException;
import com.xuejiai.aaf.framework.storage.UploadPolicy;
import com.xuejiai.aaf.module.system.file.api.FileReference;
import com.xuejiai.aaf.module.system.file.api.FileStoragePort;
import com.xuejiai.aaf.module.system.file.api.StoredFile;
import com.xuejiai.aaf.module.system.file.config.FileStorageProperties;
import com.xuejiai.aaf.module.system.file.enums.FileStoragePurpose;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 文件上传门面，原子组合动态主存储、文件登记与引用生命周期。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadService implements FileStoragePort {

    private final FileRecordService fileRecordService;
    private final FileStorageReferenceService storageReferenceService;
    private final FileStorageProperties storageProperties;

    @Override
    public StoredFile uploadCurrent(MultipartFile file) {
        return uploadCurrent(file, FileStoragePurpose.MASTER);
    }

    @Override
    public StoredFile uploadCurrent(MultipartFile file, FileStoragePurpose storagePurpose) {
        var target = uploadTarget(storagePurpose);
        var policy = uploadPolicy();
        policy.validate(file.getOriginalFilename(), file.getContentType(), file.getSize());
        try {
            var bytes = file.getBytes();
            var physical =
                    uploadBytes(
                            target.client(),
                            bytes,
                            file.getOriginalFilename(),
                            file.getContentType());
            return registerCurrentOrCompensate(physical, target);
        } catch (IOException failure) {
            throw new StorageException("文件上传失败", failure);
        }
    }

    @Override
    public StoredFile uploadFromUrl(String url, String path, String contentType, Long uploaderId) {
        var target = currentUploadTarget();
        var connection = open(url);
        try (var input = connection.getInputStream()) {
            var bytes = uploadPolicy().readWithLimit(input);
            uploadPolicy().validate(path, contentType, bytes.length);
            var physical = uploadBytes(target.client(), bytes, path, contentType);
            return registerOrCompensate(physical, uploaderId, target);
        } catch (IOException failure) {
            log.error("从 URL 上传文件失败: url={}, path={}", url, path, failure);
            throw new StorageException("从 URL 上传文件失败", failure);
        } finally {
            connection.disconnect();
        }
    }

    @Override
    public StoredFile uploadFromBytes(
            byte[] bytes, String path, String contentType, Long uploaderId) {
        uploadPolicy().validate(path, contentType, bytes == null ? 0 : bytes.length);
        var target = currentUploadTarget();
        var physical = uploadBytes(target.client(), bytes, path, contentType);
        return registerOrCompensate(physical, uploaderId, target);
    }

    @Override
    public StoredFile uploadFromBase64(String base64, String path, Long uploaderId) {
        var parsed = parseBase64(base64);
        return uploadFromBytes(parsed.bytes(), path, parsed.contentType(), uploaderId);
    }

    @Override
    public StoredFile get(Long fileId) {
        return fileRecordService.get(fileId);
    }

    @Override
    public StoredFile getByKey(String key) {
        return fileRecordService.getByKey(key);
    }

    @Override
    public StoredFile requireCurrentOwner(Long fileId) {
        return fileRecordService.requireCurrentOwner(fileId);
    }

    @Override
    public List<String> prepareCurrentOwnerImageInputs(List<Long> fileIds) {
        return fileRecordService.prepareCurrentOwnerImageInputs(fileIds);
    }

    @Override
    public String getAccessibleUrl(Long fileId) {
        return fileRecordService.getAccessibleUrl(fileId);
    }

    @Override
    public void retain(Long fileId, FileReference reference) {
        fileRecordService.retain(fileId, reference);
    }

    @Override
    public void release(Long fileId, FileReference reference) {
        fileRecordService.release(fileId, reference);
    }

    @Override
    public void requestDelete(Long fileId) {
        fileRecordService.requestDelete(fileId);
    }

    @Override
    public java.io.InputStream openByKey(String key) {
        return fileRecordService.openByKey(key);
    }

    @Override
    public void requestDeleteByKey(String key) {
        fileRecordService.requestDeleteByKey(key);
    }

    @Override
    public String prepareExternalAccessByKey(String key, java.time.Duration expiry) {
        return fileRecordService.prepareExternalAccessByKey(key, expiry);
    }

    private UploadTarget currentUploadTarget() {
        return uploadTarget(FileStoragePurpose.MASTER);
    }

    private UploadTarget uploadTarget(FileStoragePurpose storagePurpose) {
        var storage = storageReferenceService.resolveUploadTarget(storagePurpose);
        return new UploadTarget(storage.storageConfigId(), storage.client());
    }

    private PhysicalFile uploadBytes(
            StorageClient client, byte[] bytes, String path, String contentType) {
        try {
            var key = client.upload(new ByteArrayInputStream(bytes), path, contentType);
            return new PhysicalFile(key, path, bytes.length, contentType, sha256(bytes));
        } catch (RuntimeException failure) {
            throw new StorageException("文件上传失败: " + path, failure);
        }
    }

    private StoredFile registerCurrentOrCompensate(PhysicalFile physical, UploadTarget target) {
        try {
            return fileRecordService.registerCurrentForStorage(
                    physical.key(),
                    physical.filename(),
                    physical.contentType(),
                    physical.size(),
                    physical.contentHash(),
                    target.storageConfigId());
        } catch (RuntimeException failure) {
            compensate(target.client(), physical.key(), failure);
            throw failure;
        }
    }

    private StoredFile registerOrCompensate(
            PhysicalFile physical, Long uploaderId, UploadTarget target) {
        try {
            return fileRecordService.registerForStorage(
                    physical.key(),
                    physical.filename(),
                    physical.contentType(),
                    physical.size(),
                    physical.contentHash(),
                    uploaderId,
                    target.storageConfigId());
        } catch (RuntimeException failure) {
            compensate(target.client(), physical.key(), failure);
            throw failure;
        }
    }

    private void compensate(StorageClient client, String key, RuntimeException original) {
        try {
            client.delete(key);
        } catch (RuntimeException failure) {
            original.addSuppressed(failure);
        }
    }

    private HttpURLConnection open(String url) {
        try {
            var connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(30_000);
            connection.connect();
            return connection;
        } catch (IOException failure) {
            throw new StorageException("远程文件连接失败", failure);
        }
    }

    private ParsedBase64 parseBase64(String value) {
        var contentType = "application/octet-stream";
        var data = value;
        if (value != null && value.startsWith("data:")) {
            var comma = value.indexOf(',');
            if (comma > 0) {
                var header = value.substring(5, comma);
                contentType =
                        header.contains(";") ? header.substring(0, header.indexOf(';')) : header;
                data = value.substring(comma + 1);
            }
        }
        var bytes = Base64.getDecoder().decode(data);
        if ("application/octet-stream".equals(contentType)) {
            contentType = inferImageContentType(bytes);
        }
        return new ParsedBase64(bytes, contentType);
    }

    private String inferImageContentType(byte[] bytes) {
        if (bytes.length >= 8
                && bytes[0] == (byte) 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4e
                && bytes[3] == 0x47) {
            return "image/png";
        }
        if (bytes.length >= 3
                && bytes[0] == (byte) 0xff
                && bytes[1] == (byte) 0xd8
                && bytes[2] == (byte) 0xff) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }

    private UploadPolicy uploadPolicy() {
        return new UploadPolicy(storageProperties.uploadLimits());
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("运行环境不支持 SHA-256", failure);
        }
    }

    private record UploadTarget(Long storageConfigId, StorageClient client) {}

    private record PhysicalFile(
            String key, String filename, long size, String contentType, String contentHash) {}

    private record ParsedBase64(byte[] bytes, String contentType) {}
}

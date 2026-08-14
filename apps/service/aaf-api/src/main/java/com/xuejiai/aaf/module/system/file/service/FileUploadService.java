package com.xuejiai.aaf.module.system.file.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.xuejiai.aaf.framework.storage.FileService;
import com.xuejiai.aaf.framework.storage.FileVO;
import com.xuejiai.aaf.framework.storage.StorageProperties;
import com.xuejiai.aaf.module.system.file.api.FileReference;
import com.xuejiai.aaf.module.system.file.api.FileStoragePort;
import com.xuejiai.aaf.module.system.file.api.StoredFile;

import lombok.RequiredArgsConstructor;

/** 文件上传门面，原子组合按主配置选定的物理上传、文件登记与引用生命周期。 */
@Service
@RequiredArgsConstructor
public class FileUploadService implements FileStoragePort {

    private final FileRecordService fileRecordService;
    private final FileStorageReferenceService storageReferenceService;
    private final StorageProperties storageProperties;

    @Override
    public StoredFile uploadCurrent(MultipartFile file) {
        var target = currentUploadTarget();
        var physical = target.fileService().upload(file);
        return registerCurrentOrCompensate(physical, target);
    }

    @Override
    public StoredFile uploadFromUrl(String url, String path, String contentType, Long uploaderId) {
        var target = currentUploadTarget();
        var physical = target.fileService().uploadFromUrl(url, path, contentType);
        return registerOrCompensate(physical, uploaderId, target);
    }

    @Override
    public StoredFile uploadFromBytes(
            byte[] bytes, String path, String contentType, Long uploaderId) {
        var target = currentUploadTarget();
        var physical = target.fileService().uploadFromBytes(bytes, path, contentType);
        return registerOrCompensate(physical, uploaderId, target);
    }

    @Override
    public StoredFile uploadFromBase64(String base64, String path, Long uploaderId) {
        var target = currentUploadTarget();
        var physical = target.fileService().uploadFromBase64(base64, path);
        return registerOrCompensate(physical, uploaderId, target);
    }

    @Override
    public StoredFile get(Long fileId) {
        return fileRecordService.get(fileId);
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

    private UploadTarget currentUploadTarget() {
        var storage = storageReferenceService.resolveCurrentMaster();
        return new UploadTarget(
                storage.storageConfigId(),
                new FileService(storage.storageService(), storageProperties.uploadOrDefault()));
    }

    private StoredFile registerCurrentOrCompensate(FileVO physical, UploadTarget target) {
        try {
            return fileRecordService.registerCurrentForStorage(
                    physical.key(),
                    physical.filename(),
                    physical.contentType(),
                    physical.size(),
                    physical.contentHash(),
                    target.storageConfigId());
        } catch (RuntimeException registrationFailure) {
            compensatePhysicalObject(target, physical.key(), registrationFailure);
            throw registrationFailure;
        }
    }

    private StoredFile registerOrCompensate(FileVO physical, Long uploaderId, UploadTarget target) {
        try {
            return fileRecordService.registerForStorage(
                    physical.key(),
                    physical.filename(),
                    physical.contentType(),
                    physical.size(),
                    physical.contentHash(),
                    uploaderId,
                    target.storageConfigId());
        } catch (RuntimeException registrationFailure) {
            compensatePhysicalObject(target, physical.key(), registrationFailure);
            throw registrationFailure;
        }
    }

    private void compensatePhysicalObject(
            UploadTarget target, String key, RuntimeException registrationFailure) {
        try {
            target.fileService().delete(key);
        } catch (RuntimeException compensationFailure) {
            registrationFailure.addSuppressed(compensationFailure);
        }
    }

    private record UploadTarget(Long storageConfigId, FileService fileService) {}
}

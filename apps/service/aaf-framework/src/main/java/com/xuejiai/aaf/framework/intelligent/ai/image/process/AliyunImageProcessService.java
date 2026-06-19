package com.xuejiai.aaf.framework.intelligent.ai.image.process;

import java.net.URI;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import com.aliyun.imageenhan20190930.Client;
import com.aliyun.imageenhan20190930.models.EnhanceImageColorAdvanceRequest;
import com.aliyun.imageenhan20190930.models.GenerateCartoonizedImageAdvanceRequest;
import com.aliyun.imageenhan20190930.models.GetAsyncJobResultRequest;
import com.aliyun.teautil.models.RuntimeOptions;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于阿里云 imageenhan SDK 的图像处理实现。
 *
 * <p>支持的处理方式：
 *
 * <ul>
 *   <li>COLOR_ENHANCE — 色彩增强（同步）
 *   <li>CARTOONIZE — 卡通化（异步），需调用 {@link #queryTask} 轮询结果
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(Client.class)
public class AliyunImageProcessService implements ImageProcessService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Client client;

    @Override
    public ProcessResult process(ProcessRequest request) {
        try {
            var runtime = new RuntimeOptions();
            var inputStream = URI.create(request.imageUrl()).toURL().openStream();

            return switch (request.method()) {
                case "COLOR_ENHANCE" -> {
                    var req =
                            new EnhanceImageColorAdvanceRequest()
                                    .setImageURLObject(inputStream)
                                    .setMode(request.options().getOrDefault("mode", "normal"))
                                    .setOutputFormat(
                                            request.options().getOrDefault("format", "jpg"));
                    var resp = client.enhanceImageColorAdvance(req, runtime);
                    var url = resp.getBody().getData().getImageURL();
                    log.info("色彩增强完成: url={}", url);
                    yield ProcessResult.success(url);
                }
                case "CARTOONIZE" -> {
                    var req =
                            new GenerateCartoonizedImageAdvanceRequest()
                                    .setImageUrlObject(inputStream)
                                    .setIndex(request.options().getOrDefault("effect", "7"));
                    var resp = client.generateCartoonizedImageAdvance(req, runtime);
                    var taskId = resp.getBody().getRequestId();
                    log.info("卡通化任务提交: taskId={}", taskId);
                    yield ProcessResult.pending(taskId);
                }
                default -> throw new IllegalArgumentException("不支持的处理方式: " + request.method());
            };
        } catch (Exception e) {
            log.error("图像处理失败: method={}", request.method(), e);
            return ProcessResult.failed(e.getMessage());
        }
    }

    @Override
    public ProcessResult queryTask(String taskId) {
        try {
            var resp =
                    client.getAsyncJobResultWithOptions(
                            new GetAsyncJobResultRequest().setJobId(taskId), new RuntimeOptions());
            var data = resp.getBody().getData();
            return switch (data.getStatus()) {
                case "PROCESS_SUCCESS" -> {
                    var node = MAPPER.readTree(data.getResult());
                    yield ProcessResult.success(node.path("resultUrl").asText());
                }
                case "PROCESS_FAILED", "TIMEOUT_FAILED" ->
                        ProcessResult.failed(data.getErrorMessage());
                default -> ProcessResult.pending(taskId);
            };
        } catch (Exception e) {
            log.error("查询任务失败: taskId={}", taskId, e);
            return ProcessResult.failed(e.getMessage());
        }
    }
}

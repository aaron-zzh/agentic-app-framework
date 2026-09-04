package com.xuejiai.aaf.framework.intelligent.ai.image.process;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;

import com.aliyun.imageenhan20190930.Client;
import com.aliyun.imageenhan20190930.models.EnhanceImageColorAdvanceRequest;
import com.aliyun.imageenhan20190930.models.GenerateCartoonizedImageAdvanceRequest;
import com.aliyun.imageenhan20190930.models.GetAsyncJobResultRequest;
import com.aliyun.imageseg20191230.models.SegmentHDBodyAdvanceRequest;
import com.aliyun.teautil.models.RuntimeOptions;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.ai.image.ImageInputReader;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于阿里云图像 SDK 的图像处理实现。
 *
 * <p>支持的处理方式及计费（100 积分 = 1 元，系统加价倍率默认 5x）：
 *
 * <ul>
 *   <li>COLOR_ENHANCE — 色彩增强（同步，imageenhan）
 *   <li>CARTOONIZE — 卡通化（异步，imageenhan），需调用 {@link #queryTask} 轮询结果
 *   <li>SEGMENT_HD_COMMON_IMAGE — 通用高清分割（异步，imageseg），需轮询，0.007 元/次
 *   <li>SEGMENT_HD_BODY — 人像高清抠图（同步，imageseg），0.007 元/次
 * </ul>
 */
@Slf4j
public class AliyunImageProcessService implements ImageProcessService {

    private final Client client;

    /** imageseg 客户端，可空——未配置时图像分割方法降级为不支持 */
    private final com.aliyun.imageseg20191230.Client imagesegClient;

    public AliyunImageProcessService(
            Client client, com.aliyun.imageseg20191230.Client imagesegClient) {
        this.client = client;
        this.imagesegClient = imagesegClient;
    }

    /** 阿里云异步任务查询限速：2 QPS（GetAsyncJobResult 接口限制） */
    private final Bucket queryRateLimiter =
            Bucket.builder()
                    .addLimit(
                            Bandwidth.builder()
                                    .capacity(2)
                                    .refillIntervally(2, Duration.ofSeconds(1))
                                    .build())
                    .build();

    @Override
    public ProcessResult process(ProcessRequest request) {
        try {
            var runtime = new RuntimeOptions();
            // Policy 有效期短，上传慢时会出现 Policy expired；加大读写超时避免此问题
            runtime.readTimeout = 120000;
            runtime.connectTimeout = 30000;

            return switch (request.method()) {
                case "COLOR_ENHANCE" -> {
                    var inputStream = openImageInput(request.imageUrl());
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
                    var inputStream = openImageInput(request.imageUrl());
                    var req =
                            new GenerateCartoonizedImageAdvanceRequest()
                                    .setImageUrlObject(inputStream)
                                    .setIndex(request.options().getOrDefault("effect", "7"));
                    var resp = client.generateCartoonizedImageAdvance(req, runtime);
                    var taskId = resp.getBody().getRequestId();
                    log.info("卡通化任务提交: taskId={}", taskId);
                    yield ProcessResult.pending(taskId);
                }
                case "SEGMENT_HD_COMMON_IMAGE" -> {
                    if (imagesegClient == null) {
                        throw new IllegalStateException(
                                "imageseg 客户端未配置，无法调用 SEGMENT_HD_COMMON_IMAGE");
                    }
                    // 官方文档明确该接口为纯异步能力：提交响应仅含 RequestId，不含结果，
                    // 必须以 RequestId 作为 jobId 再调用 GetAsyncJobResult 轮询获取 Data.ImageUrl。
                    var inputStream = openImageInput(request.imageUrl());
                    var req =
                            new com.aliyun.imageseg20191230.models
                                            .SegmentHDCommonImageAdvanceRequest()
                                    .setImageUrlObject(inputStream);
                    var resp = imagesegClient.segmentHDCommonImageAdvance(req, runtime);
                    var jobId = resp.getBody().getRequestId();
                    log.info("通用高清分割任务已提交: jobId={}", jobId);
                    yield ProcessResult.pending(jobId);
                }
                case "SEGMENT_HD_BODY" -> {
                    if (imagesegClient == null) {
                        throw new IllegalStateException("imageseg 客户端未配置，无法调用 SEGMENT_HD_BODY");
                    }
                    var inputStream = openImageInput(request.imageUrl());
                    var req = new SegmentHDBodyAdvanceRequest().setImageURLObject(inputStream);
                    var resp = imagesegClient.segmentHDBodyAdvance(req, runtime);
                    var body = resp.getBody();
                    var data = body.getData();
                    if (data == null || data.getImageURL() == null) {
                        throw new IllegalStateException(
                                "阿里云返回结果为空，请检查图片格式或账号权限（requestId=" + body.getRequestId() + "）");
                    }
                    var url = data.getImageURL();
                    log.info("人像高清抠图完成: url={}", url);
                    yield ProcessResult.success(url);
                }
                default -> throw new IllegalArgumentException("不支持的处理方式: " + request.method());
            };
        } catch (Exception e) {
            log.error("图像处理失败: method={}", request.method(), e);
            return ProcessResult.failed(friendlyMessage(e));
        }
    }

    /**
     * 打开图像输入流，复用 {@link ImageInputReader} 统一解析 Data URL / HTTP(S) URL——避免把受权限保护的内部 URL（如本地存储的
     * {@code /api/system/files/{id}/content}）直接交给外部 SDK 下载导致 401，同时获得 该工具类已有的大小限制与格式校验。
     */
    private static InputStream openImageInput(String imageInput) throws Exception {
        return new ByteArrayInputStream(ImageInputReader.read(imageInput).bytes());
    }

    @Override
    public ProcessResult queryTask(String taskId, String method) {
        try {
            queryRateLimiter.asBlocking().consume(1);
            log.debug("[queryTask] taskId={}, method={}", taskId, method);
            if ("SEGMENT_HD_COMMON_IMAGE".equals(method)) {
                return querySegmentHDCommonImageTask(taskId);
            }
            var resp =
                    client.getAsyncJobResultWithOptions(
                            new GetAsyncJobResultRequest().setJobId(taskId), new RuntimeOptions());
            var data = resp.getBody().getData();
            return switch (data.getStatus()) {
                case "PROCESS_SUCCESS" -> {
                    var node = JsonUtils.readTree(data.getResult());
                    yield ProcessResult.success(node.path("resultUrl").asString());
                }
                case "PROCESS_FAILED", "TIMEOUT_FAILED" ->
                        ProcessResult.failed(data.getErrorMessage());
                default -> ProcessResult.pending(taskId);
            };
        } catch (Exception e) {
            log.error("查询任务失败: taskId={}", taskId, e);
            return ProcessResult.failed(friendlyMessage(e));
        }
    }

    /**
     * 查询 imageseg 类目（分割抠图）的异步任务结果。
     *
     * <p>imageseg 与 imageenhan 是两个独立类目，各自的 GetAsyncJobResult 接口互相不通用， 必须用 {@link #imagesegClient}
     * 查询才能取到 SEGMENT_HD_COMMON_IMAGE 提交时的 jobId 对应结果。 Result JSON 字段为 {@code imageUrl}（首字母小写，实测生产环境
     * 返回值——阿里云官方文档示例展示的是 {@code ImageUrl} 大写，与实际不一致，与 imageenhan 类目的 {@code resultUrl} 也不同）。
     */
    private ProcessResult querySegmentHDCommonImageTask(String taskId) throws Exception {
        if (imagesegClient == null) {
            return ProcessResult.failed("imageseg 客户端未配置，无法查询 SEGMENT_HD_COMMON_IMAGE 任务");
        }
        var resp =
                imagesegClient.getAsyncJobResultWithOptions(
                        new com.aliyun.imageseg20191230.models.GetAsyncJobResultRequest()
                                .setJobId(taskId),
                        new RuntimeOptions());
        var data = resp.getBody().getData();
        log.debug(
                "[querySegmentHDCommonImageTask] jobId={}, status={}, errorMessage={}",
                taskId,
                data.getStatus(),
                data.getErrorMessage());
        return switch (data.getStatus()) {
            case "PROCESS_SUCCESS" -> {
                var node = JsonUtils.readTree(data.getResult());
                yield ProcessResult.success(node.path("imageUrl").asString());
            }
            case "PROCESS_FAILED", "TIMEOUT_FAILED" -> ProcessResult.failed(data.getErrorMessage());
            default -> ProcessResult.pending(taskId);
        };
    }

    /** 将底层异常转为对用户友好的中文描述 */
    private static String friendlyMessage(Exception e) {
        Throwable t = e;
        while (t != null) {
            String msg = t.getMessage() != null ? t.getMessage() : "";
            if (t instanceof java.net.SocketTimeoutException
                    || msg.contains("timed out")
                    || msg.contains("timeout")
                    || msg.contains("I/O error")) {
                return "请求超时，请稍后重试";
            }
            if (t instanceof java.net.ConnectException || msg.contains("Connection refused")) {
                return "无法连接到图像处理服务，请检查网络";
            }
            if (t instanceof java.net.UnknownHostException) {
                return "域名解析失败，请检查网络连接";
            }
            t = t.getCause();
        }
        return e.getMessage();
    }
}

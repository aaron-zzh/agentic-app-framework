package com.xuejiai.aaf.framework.intelligent.ai.image.decorator;

import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.intelligent.ai.image.DashScopeImageGenerationService;
import com.xuejiai.aaf.framework.intelligent.ai.image.ImageGenerationService;
import com.xuejiai.aaf.framework.intelligent.ai.image.vo.ImageEditRequest;
import com.xuejiai.aaf.framework.intelligent.ai.image.vo.ImageRequest;
import com.xuejiai.aaf.framework.intelligent.ai.image.vo.ImageResult;
import com.xuejiai.aaf.framework.intelligent.core.decorator.AbstractAiServiceDecorator;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.security.OperatorContext;

/**
 * 图像生成服务装饰器。
 *
 * <p><b>职责</b>：为 {@link ImageGenerationService} 的每个方法声明积分处理策略， 具体积分逻辑全部委托给父类 {@link
 * AbstractAiServiceDecorator#creditCall}。
 *
 * <p><b>图像生成为异步任务模式</b>，precheck=false 的原因： 调用方 {@code AigcTaskService} 在创建 {@code AigcTask}
 * 记录之前已经做过精确预检， 若在此处（异步线程内）再 precheck，任务记录已落库，失败只能标 FAIL，产生无效数据。
 * 因此装饰器只负责<b>结算</b>（调用成功后扣积分），预检职责在上层。
 *
 * <p><b>由谁创建</b>：{@link DefaultAiServiceRegistry} 通过 {@link ImageServiceFactory} 路由到具体实现后， 每次请求动态
 * new 此装饰器包裹返回。不缓存的原因：内部持有 {@code OperatorContext}（请求级用户上下文）， 若跨请求复用会导致积分归账到错误用户。装饰器本身仅含三个引用，new
 * 开销可忽略。
 */
public class ImageGenServiceDecorator extends AbstractAiServiceDecorator<ImageGenerationService>
        implements ImageGenerationService {

    public ImageGenServiceDecorator(
            ImageGenerationService delegate,
            AiCreditGuard creditGuard,
            OperatorContext operatorContext) {
        super(delegate, creditGuard, operatorContext);
    }

    @Override
    public ImageResult generate(AiModel model, ImageRequest req) {
        // precheck=false：预检已在 AigcTaskService 完成，此处只结算
        return creditCall(model, false, () -> delegate.generate(model, req));
    }

    @Override
    public ImageResult imageToImage(AiModel model, ImageEditRequest req) {
        return creditCall(model, false, () -> delegate.imageToImage(model, req));
    }

    @Override
    public ImageResult editImage(AiModel model, ImageEditRequest req) {
        return creditCall(model, false, () -> delegate.editImage(model, req));
    }

    /**
     * DashScope 多图生成入口（{@code AigcTaskExecutor} 直接调用）。
     *
     * <p>DashScope 的多图生成是 DashScope 独有的扩展方法，不在 {@link ImageGenerationService} 接口上， 故需在此处显式委托，积分仍由
     * creditCall 统一处理。
     */
    public ImageResult generateWithImages(AiModel model, ImageRequest req) {
        if (delegate instanceof DashScopeImageGenerationService ds) {
            return creditCall(model, false, () -> ds.generateWithImages(model, req));
        }
        // 非 DashScope 模型：把 imageUrls 转为 ImageEditRequest 走图生图
        if (req.getImageUrls() != null && !req.getImageUrls().isEmpty()) {
            var editReq =
                    new ImageEditRequest(
                            req.getImageUrls().get(0),
                            null,
                            req.getPrompt(),
                            null,
                            req.getModelId(),
                            req.getQuality(),
                            req.getFormat(),
                            req.getBackground(),
                            req.getModeration(),
                            req.getImageCount() > 1 ? req.getImageCount() : null,
                            req.getImageUrls());
            editReq.setWidth(req.getWidth());
            editReq.setHeight(req.getHeight());
            editReq.setSizePreset(req.getSizePreset());
            editReq.setAspectRatio(req.getAspectRatio());
            return creditCall(model, false, () -> delegate.imageToImage(model, editReq));
        }
        return creditCall(model, false, () -> delegate.generate(model, req));
    }
}

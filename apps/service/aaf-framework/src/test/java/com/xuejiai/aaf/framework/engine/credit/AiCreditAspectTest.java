package com.xuejiai.aaf.framework.engine.credit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.xuejiai.aaf.common.exception.InsufficientCreditsException;
import com.xuejiai.aaf.framework.intelligent.ai.ocr.vo.OcrResult;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

/** AiCreditAspect 单元测试——验证 precheck/settle 在各场景下的行为。 */
class AiCreditAspectTest extends BaseMockitoUnitTest {

    @Mock private AiCreditGuard creditGuard;
    @Mock private OperatorContext operatorContext;
    @Mock private ProceedingJoinPoint pjp;
    @Mock private AiCredit aiCredit;

    @InjectMocks private AiCreditAspect aspect;

    private AiModel tokenModel; // quotaType=0，按 token
    private AiModel perUseModel; // quotaType=1，按次

    @BeforeEach
    void setUp() throws Throwable {
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(100L));
        when(aiCredit.capability()).thenReturn("ocr");
        when(aiCredit.precheck()).thenReturn(true);

        tokenModel = new AiModel();
        tokenModel.setId(1L);
        tokenModel.setQuotaType((short) 0);

        perUseModel = new AiModel();
        perUseModel.setId(2L);
        perUseModel.setQuotaType((short) 1);
    }

    @Test
    void 按token计费_成功时调用settleByUsage() throws Throwable {
        when(aiCredit.settle()).thenReturn(true);
        when(aiCredit.bizName()).thenReturn("");
        var result = OcrResult.ofText("识别结果", 100, 50);
        when(pjp.getArgs()).thenReturn(new Object[] {tokenModel});
        when(pjp.proceed()).thenReturn(result);

        Object ret = aspect.around(pjp, aiCredit);

        assertThat(ret).isEqualTo(result);
        verify(creditGuard).precheck(100L, "ocr", 0L);
        verify(creditGuard).settleByUsage(100L, tokenModel, 100L, 50L, "ocr", null);
    }

    @Test
    void 按次计费_成功时调用settleByUsage() throws Throwable {
        when(aiCredit.settle()).thenReturn(true);
        when(aiCredit.bizName()).thenReturn("");
        var result = OcrResult.ofText("识别结果", 0, 0);
        when(pjp.getArgs()).thenReturn(new Object[] {perUseModel});
        when(pjp.proceed()).thenReturn(result);

        aspect.around(pjp, aiCredit);

        verify(creditGuard).settleByUsage(100L, perUseModel, 0L, 0L, "ocr", null);
    }

    @Test
    void 方法抛异常时不结算() throws Throwable {
        when(pjp.getArgs()).thenReturn(new Object[] {tokenModel});
        when(pjp.proceed()).thenThrow(new RuntimeException("调用失败"));

        assertThatThrownBy(() -> aspect.around(pjp, aiCredit)).isInstanceOf(RuntimeException.class);

        verify(creditGuard, never()).settleByUsage(any(), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void 余额不足时precheck抛异常_不执行方法() throws Throwable {
        when(pjp.getArgs()).thenReturn(new Object[] {tokenModel});
        org.mockito.Mockito.doThrow(new InsufficientCreditsException(100L, 0L))
                .when(creditGuard)
                .precheck(eq(100L), eq("ocr"), eq(0L));

        assertThatThrownBy(() -> aspect.around(pjp, aiCredit))
                .isInstanceOf(InsufficientCreditsException.class);

        verify(pjp, never()).proceed();
        verify(creditGuard, never()).settleByUsage(any(), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void model为null时_降级处理_仍能完成调用() throws Throwable {
        when(aiCredit.settle()).thenReturn(true);
        when(aiCredit.bizName()).thenReturn("");
        var result = OcrResult.ofText("结果", 0, 0);
        when(pjp.getArgs()).thenReturn(new Object[] {null});
        when(pjp.proceed()).thenReturn(result);

        Object ret = aspect.around(pjp, aiCredit);
        assertThat(ret).isEqualTo(result);
        verify(creditGuard).settleByUsage(eq(100L), eq(null), eq(0L), eq(0L), eq("ocr"), eq(null));
    }
}

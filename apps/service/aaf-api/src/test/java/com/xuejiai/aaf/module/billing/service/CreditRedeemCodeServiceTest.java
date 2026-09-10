package com.xuejiai.aaf.module.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.xuejiai.aaf.framework.engine.credit.CreditService;
import com.xuejiai.aaf.framework.messaging.MessageService;
import com.xuejiai.aaf.module.billing.domain.CreditRedeemCode;
import com.xuejiai.aaf.module.billing.domain.SubscriptionPlan;
import com.xuejiai.aaf.module.billing.domain.SubscriptionSku;
import com.xuejiai.aaf.module.billing.repository.CreditRedeemCodeRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionPlanRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionSkuRepository;
import com.xuejiai.aaf.module.system.user.api.UserRelationService;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

/** 会员兑换码仅绑定受校验 SKU，且核销时间与订阅生效时间完全一致。 */
class CreditRedeemCodeServiceTest extends BaseMockitoUnitTest {

    @Mock private CreditRedeemCodeRepository redeemCodeRepository;
    @Mock private SubscriptionSkuRepository skuRepository;
    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private UserRelationService userRelationService;
    @Mock private CreditService creditService;
    @Mock private SubscriptionService subscriptionService;
    @Mock private MessageService messageService;

    @InjectMocks private CreditRedeemCodeService redeemCodeService;

    private SubscriptionSku sku;
    private SubscriptionPlan plan;

    @BeforeEach
    void setUp() {
        sku = new SubscriptionSku();
        sku.setId(301L);
        sku.setPlanId(30L);
        sku.setCode("TEAM_YEAR");
        sku.setBillingCycle("YEAR");
        sku.setCycleMonths(12);
        sku.setPrice(99900L);
        sku.setMarketPrice(109900L);
        sku.setStatus("ENABLED");

        plan = new SubscriptionPlan();
        plan.setId(30L);
        plan.setCode("TEAM");
        plan.setName("团队版");
        plan.setStatus("ENABLED");
        plan.setSort(30);

        lenient().when(skuRepository.findById(301L)).thenReturn(Optional.of(sku));
        lenient().when(skuRepository.findByCode("TEAM_YEAR")).thenReturn(Optional.of(sku));
        lenient().when(planRepository.findById(30L)).thenReturn(Optional.of(plan));
    }

    @Test
    @DisplayName("Given 可兑换会员 SKU When 查询导出元数据 Then 返回真实 SKU、Plan 和周期")
    void membershipSkuInfo_returnsValidatedMetadata() {
        var info = redeemCodeService.membershipSkuInfo(" TEAM_YEAR ");

        assertThat(info.skuCode()).isEqualTo("TEAM_YEAR");
        assertThat(info.planName()).isEqualTo("团队版");
        assertThat(info.billingCycle()).isEqualTo("YEAR");
    }

    @Test
    @DisplayName("Given 未使用会员码 When 兑换 Then 核销时间与订阅生效时间使用同一微秒值")
    void redeem_membershipUsesSameEffectiveTimestamp() {
        var rawCode = "CRED-test-membership";
        var code = new CreditRedeemCode();
        code.setId(901L);
        code.setCodeHash(CreditRedeemSecurityUtil.sha256(rawCode));
        code.setCodePrefix("CRED-test...");
        code.setCreditAmount(0L);
        code.setType("MEMBERSHIP");
        code.setSkuId(301L);
        code.setStatus("UNUSED");
        when(redeemCodeRepository.findByCodeHashForUpdate(code.getCodeHash()))
                .thenReturn(Optional.of(code));
        var timeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        var amount = redeemCodeService.redeem(100L, rawCode);

        assertThat(amount).isZero();
        verify(subscriptionService)
                .activateGrantedSku(
                        eq(100L),
                        eq(301L),
                        eq("REDEEM_CODE"),
                        eq(901L),
                        timeCaptor.capture());
        assertThat(code.getStatus()).isEqualTo("REDEEMED");
        assertThat(code.getRedeemedByUserId()).isEqualTo(100L);
        assertThat(code.getRedeemedAt()).isEqualTo(timeCaptor.getValue());
        assertThat(code.getRedeemedAt().getNano() % 1_000).isZero();
    }
}

package com.xuejiai.aaf.config;

import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitionProvider;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitions;
import com.xuejiai.aaf.framework.crud.definition.CrudResourceExposure;
import com.xuejiai.aaf.framework.crud.definition.PersonalScope;
import com.xuejiai.aaf.framework.crud.definition.TenantScope;
import com.xuejiai.aaf.module.ai.aigc.avatar.controller.AiDigitalAvatarController;
import com.xuejiai.aaf.module.ai.aigc.image.controller.GenerationTemplateController;
import com.xuejiai.aaf.module.ai.aigc.project.controller.AigcContentController;
import com.xuejiai.aaf.module.ai.aigc.project.controller.AigcProjectController;
import com.xuejiai.aaf.module.ai.aigc.project.controller.AigcShotController;
import com.xuejiai.aaf.module.ai.aigc.project.controller.AigcStoryboardController;
import com.xuejiai.aaf.module.ai.aigc.project.controller.AigcTimelineController;
import com.xuejiai.aaf.module.ai.aigc.task.controller.AigcTaskController;
import com.xuejiai.aaf.module.ai.aigc.template.controller.UserProjectTemplateController;
import com.xuejiai.aaf.module.ai.aigc.video.controller.VideoTemplateController;
import com.xuejiai.aaf.module.ai.aigc.voice.controller.AiClonedVoiceController;
import com.xuejiai.aaf.module.ai.aigc.workflow.controller.UserWorkflowTemplateController;
import com.xuejiai.aaf.module.ai.flow.controller.AiFlowController;
import com.xuejiai.aaf.module.ai.persona.outfit.controller.AvatarOutfitController;
import com.xuejiai.aaf.module.ai.role.AiRoleController;
import com.xuejiai.aaf.module.ai.role.PersonaController;
import com.xuejiai.aaf.module.ai.skill.SkillController;
import com.xuejiai.aaf.module.billing.controller.CreditRedeemCodeController;
import com.xuejiai.aaf.module.billing.controller.EntitlementController;
import com.xuejiai.aaf.module.billing.controller.LevelController;
import com.xuejiai.aaf.module.billing.controller.SubscriptionController;
import com.xuejiai.aaf.module.billing.controller.SubscriptionPlanController;
import com.xuejiai.aaf.module.billing.controller.WalletTransactionController;
import com.xuejiai.aaf.module.brokerage.controller.BrokerageInviteCodeController;
import com.xuejiai.aaf.module.brokerage.controller.BrokerageLevelBonusController;
import com.xuejiai.aaf.module.brokerage.controller.BrokerageRecordController;
import com.xuejiai.aaf.module.brokerage.controller.BrokerageRuleController;
import com.xuejiai.aaf.module.brokerage.controller.BrokerageUserController;
import com.xuejiai.aaf.module.brokerage.controller.BrokerageWithdrawController;
import com.xuejiai.aaf.module.chat.conversation.controller.ConversationController;
import com.xuejiai.aaf.module.chat.livechat.seat.controller.SeatController;
import com.xuejiai.aaf.module.chat.livechat.ticket.controller.TicketController;
import com.xuejiai.aaf.module.chat.message.controller.MessageController;
import com.xuejiai.aaf.module.developer.controller.DeveloperSubscriptionPlanAdminController;
import com.xuejiai.aaf.module.system.contact.controller.ContactController;
import com.xuejiai.aaf.module.system.contact.controller.ContactIdentityController;
import com.xuejiai.aaf.module.system.dict.controller.DictDataController;
import com.xuejiai.aaf.module.system.dict.controller.DictTypeController;
import com.xuejiai.aaf.module.system.lead.controller.GuestLeadController;
import com.xuejiai.aaf.module.system.log.controller.CommentController;
import com.xuejiai.aaf.module.system.menu.controller.MenuController;
import com.xuejiai.aaf.module.system.notify.controller.NoticeController;
import com.xuejiai.aaf.module.system.org.controller.WorkspaceController;
import com.xuejiai.aaf.module.system.role.controller.DataAccessRuleController;
import com.xuejiai.aaf.module.system.role.controller.RoleController;
import com.xuejiai.aaf.module.system.user.controller.UserController;
import com.xuejiai.aaf.module.system.user.favorite.controller.UserFavoriteController;

/**
 * 当前全部代码 CRUD 端点的显式 Catalog Provider。
 *
 * <p>cutover 决策：Comment 使用 NESTED_CRUD 显式注册；Rating 无 Controller/调用方，已退出 BaseCrud， 不注册隐藏运行时 CRUD 资源。
 */
@Configuration(proxyBeanMethods = false)
public class CrudResourceProviderConfiguration {

    private static final TenantScope TENANT = TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL;

    @Bean
    CrudResourceDefinitionProvider<?> aiDigitalAvatarResource() {
        return crud(
                "aigc.ai-digital-avatar",
                "数字人",
                AiDigitalAvatarController.class,
                "/api/aigc/avatars",
                "system:ai-digital-avatar");
    }

    @Bean
    CrudResourceDefinitionProvider<?> generationTemplateResource() {
        return crud(
                "aigc.generation-template",
                "参数模板",
                GenerationTemplateController.class,
                "/api/aigc/templates",
                "system:generation-template");
    }

    @Bean
    CrudResourceDefinitionProvider<?> aigcContentResource() {
        return crud(
                "aigc.aigc-content",
                "内容产出",
                AigcContentController.class,
                "/api/aigc/contents",
                "system:aigc-content");
    }

    @Bean
    CrudResourceDefinitionProvider<?> aigcProjectResource() {
        return crud(
                "aigc.aigc-project",
                "创作项目",
                AigcProjectController.class,
                "/api/aigc/projects",
                "system:aigc-project");
    }

    @Bean
    CrudResourceDefinitionProvider<?> aigcShotResource() {
        return crud(
                "aigc.aigc-shot",
                "分镜",
                AigcShotController.class,
                "/api/aigc/shots",
                "system:aigc-shot");
    }

    @Bean
    CrudResourceDefinitionProvider<?> aigcStoryboardResource() {
        return crud(
                "aigc.aigc-storyboard",
                "分镜规划",
                AigcStoryboardController.class,
                "/api/aigc/storyboards",
                "system:aigc-storyboard");
    }

    @Bean
    CrudResourceDefinitionProvider<?> aigcTimelineResource() {
        return crud(
                "aigc.aigc-timeline",
                "时间轴",
                AigcTimelineController.class,
                "/api/aigc/timelines",
                "system:aigc-timeline");
    }

    @Bean
    CrudResourceDefinitionProvider<?> aigcTaskResource() {
        return crud(
                "aigc.aigc-task",
                "AIGC任务",
                AigcTaskController.class,
                "/api/aigc/tasks",
                "system:aigc-task");
    }

    @Bean
    CrudResourceDefinitionProvider<?> userProjectTemplateResource() {
        return crud(
                "aigc.user-project-template",
                "项目模板",
                UserProjectTemplateController.class,
                "/api/aigc/project-templates",
                "system:user-project-template");
    }

    @Bean
    CrudResourceDefinitionProvider<?> videoTemplateResource() {
        return crud(
                "aigc.video-template",
                "视频模板",
                VideoTemplateController.class,
                "/api/aigc/video/templates",
                "system:video-template");
    }

    @Bean
    CrudResourceDefinitionProvider<?> aiClonedVoiceResource() {
        return crud(
                "aigc.ai-cloned-voice",
                "克隆音色",
                AiClonedVoiceController.class,
                "/api/aigc/cloned-voices",
                "system:ai-cloned-voice");
    }

    @Bean
    CrudResourceDefinitionProvider<?> userWorkflowTemplateResource() {
        return crud(
                "aigc.user-workflow-template",
                "工作流模板",
                UserWorkflowTemplateController.class,
                "/api/aigc/workflow-templates",
                "system:user-workflow-template");
    }

    @Bean
    CrudResourceDefinitionProvider<?> aiFlowResource() {
        return crud(
                "ai.ai-flow",
                "AI 工作流",
                AiFlowController.class,
                "/api/ai/workflows",
                "system:ai-flow-definition");
    }

    @Bean
    CrudResourceDefinitionProvider<?> avatarOutfitResource() {
        return crud(
                "ai.avatar-outfit",
                "Avatar Outfit",
                AvatarOutfitController.class,
                "/api/avatar-outfits",
                "system:avatar-outfit");
    }

    @Bean
    CrudResourceDefinitionProvider<?> aiRoleResource() {
        return crud(
                "ai.ai-role", "AI Role", AiRoleController.class, "/api/ai/roles", "system:role");
    }

    @Bean
    CrudResourceDefinitionProvider<?> personaResource() {
        return crud(
                "ai.persona",
                "Persona",
                PersonaController.class,
                "/api/ai/actors",
                "system:persona");
    }

    @Bean
    CrudResourceDefinitionProvider<?> skillResource() {
        return crud(
                "ai.skill",
                "技能",
                SkillController.class,
                "/api/system/skills",
                "system:skill-definition");
    }

    @Bean
    CrudResourceDefinitionProvider<?> creditRedeemCodeResource() {
        return crud(
                "billing.credit-redeem-code",
                "积分兑换码",
                CreditRedeemCodeController.class,
                "/api/billing/credit-redeem-codes",
                "billing:credit-redeem-code");
    }

    @Bean
    CrudResourceDefinitionProvider<?> entitlementQuotaResource() {
        return crud(
                "billing.entitlement-quota",
                "权益额度",
                EntitlementController.class,
                "/api/billing/entitlement-quotas",
                "billing:entitlement-quota",
                "ownerId");
    }

    @Bean
    CrudResourceDefinitionProvider<?> levelResource() {
        return crud(
                "billing.level",
                "会员等级",
                LevelController.class,
                "/api/billing/levels",
                "billing:level");
    }

    @Bean
    CrudResourceDefinitionProvider<?> subscriptionResource() {
        return crud(
                "billing.subscription",
                "订阅",
                SubscriptionController.class,
                "/api/billing/subscriptions",
                "billing:subscription",
                "ownerId");
    }

    @Bean
    CrudResourceDefinitionProvider<?> subscriptionPlanResource() {
        return crud(
                "billing.subscription-plan",
                "订阅套餐",
                SubscriptionPlanController.class,
                "/api/billing/subscription-plans",
                "billing:subscription-plan");
    }

    @Bean
    CrudResourceDefinitionProvider<?> walletTransactionResource() {
        return crud(
                "billing.wallet-transaction",
                "钱包流水",
                WalletTransactionController.class,
                "/api/billing/wallet-transactions",
                "billing:wallet-transaction");
    }

    @Bean
    CrudResourceDefinitionProvider<?> brokerageInviteCodeResource() {
        return crud(
                "brokerage.brokerage-invite-code",
                "邀请码",
                BrokerageInviteCodeController.class,
                "/api/brokerage/invite-codes",
                "system:brokerage-invite-code");
    }

    @Bean
    CrudResourceDefinitionProvider<?> brokerageLevelBonusResource() {
        return crud(
                "brokerage.brokerage-level-bonus",
                "等级佣金加成",
                BrokerageLevelBonusController.class,
                "/api/brokerage/level-bonuses",
                "brokerage:brokerage-level-bonus");
    }

    @Bean
    CrudResourceDefinitionProvider<?> brokerageRecordResource() {
        return crud(
                "brokerage.brokerage-record",
                "佣金记录",
                BrokerageRecordController.class,
                "/api/brokerage/records",
                "brokerage:brokerage-record");
    }

    @Bean
    CrudResourceDefinitionProvider<?> brokerageRuleResource() {
        return crud(
                "brokerage.brokerage-rule",
                "佣金规则",
                BrokerageRuleController.class,
                "/api/brokerage/rules",
                "brokerage:brokerage-rule");
    }

    @Bean
    CrudResourceDefinitionProvider<?> brokerageUserResource() {
        return crud(
                "brokerage.brokerage-user",
                "分销员",
                BrokerageUserController.class,
                "/api/brokerage/users",
                "brokerage:brokerage-user");
    }

    @Bean
    CrudResourceDefinitionProvider<?> brokerageWithdrawResource() {
        return crud(
                "brokerage.brokerage-withdraw",
                "佣金提现",
                BrokerageWithdrawController.class,
                "/api/brokerage/withdraws",
                "brokerage:brokerage-withdraw");
    }

    @Bean
    CrudResourceDefinitionProvider<?> conversationResource() {
        return crud(
                "chat.conversation",
                "会话",
                ConversationController.class,
                "/api/chat/conversations",
                "system:conversation");
    }

    @Bean
    CrudResourceDefinitionProvider<?> livechatSeatResource() {
        return crud(
                "chat.livechat-seat",
                "坐席",
                SeatController.class,
                "/api/chat/livechat/seats",
                "system:livechat-seat");
    }

    @Bean
    CrudResourceDefinitionProvider<?> livechatTicketResource() {
        return crud(
                "chat.livechat-ticket",
                "客服工单",
                TicketController.class,
                "/api/chat/livechat/tickets",
                "system:ticket");
    }

    @Bean
    CrudResourceDefinitionProvider<?> messageResource() {
        return crud(
                "chat.message",
                "消息",
                MessageController.class,
                "/api/chat/messages",
                "system:conversation-message");
    }

    @Bean
    CrudResourceDefinitionProvider<?> developerSubscriptionPlanResource() {
        return crud(
                "developer.developer-subscription-plan",
                "开发者订阅套餐",
                DeveloperSubscriptionPlanAdminController.class,
                "/api/developer/admin/subscription-plans",
                "developer:subscription-plan");
    }

    @Bean
    CrudResourceDefinitionProvider<?> contactResource() {
        return crud(
                "system.contact",
                "联系人",
                ContactController.class,
                "/api/contacts",
                "system:contact");
    }

    @Bean
    CrudResourceDefinitionProvider<?> contactIdentityResource() {
        return crud(
                "system.contact-identity",
                "渠道身份",
                ContactIdentityController.class,
                "/api/contact-identities",
                "system:contact-identity");
    }

    @Bean
    CrudResourceDefinitionProvider<?> dictDataResource() {
        return crud(
                "system.dict-data",
                "字典数据",
                DictDataController.class,
                "/api/system/dict-data",
                "system:dict-data");
    }

    @Bean
    CrudResourceDefinitionProvider<?> dictTypeResource() {
        return crud(
                "system.dict-type",
                "字典类型",
                DictTypeController.class,
                "/api/system/dict-types",
                "system:dict-type");
    }

    @Bean
    CrudResourceDefinitionProvider<?> guestLeadResource() {
        return crud(
                "system.guest-lead",
                "访客线索",
                GuestLeadController.class,
                "/api/system/leads",
                "system:guest-lead");
    }

    @Bean
    CrudResourceDefinitionProvider<?> commentResource() {
        return CrudResourceDefinitions.nestedCrud(
                "system.comment",
                "评论",
                CommentController.class,
                "/api/{entity}/{entityId}/comments",
                "system:comment",
                TENANT,
                PersonalScope.none());
    }

    @Bean
    CrudResourceDefinitionProvider<?> menuResource() {
        return crud("system.menu", "菜单", MenuController.class, "/api/system/menus", "system:menu");
    }

    @Bean
    CrudResourceDefinitionProvider<?> noticeResource() {
        return crud(
                "system.notice",
                "通知公告",
                NoticeController.class,
                "/api/system/notices",
                "system:notice");
    }

    @Bean
    CrudResourceDefinitionProvider<?> workspaceResource() {
        return crud(
                "system.workspace",
                "工作区",
                WorkspaceController.class,
                "/api/system/workspaces",
                "system:workspace");
    }

    @Bean
    CrudResourceDefinitionProvider<?> dataAccessRuleResource() {
        return crud(
                "system.data-access-rule",
                "数据权限规则",
                DataAccessRuleController.class,
                "/api/admin/data-access-rules",
                "system:data-access-rule");
    }

    @Bean
    CrudResourceDefinitionProvider<?> roleResource() {
        return CrudResourceDefinitions.crud(
                "system.system-role",
                "角色",
                RoleController.class,
                "/api/system/roles",
                "system:role",
                TENANT,
                PersonalScope.none(),
                Set.of(
                        CrudResourceExposure.HTTP,
                        CrudResourceExposure.ENTITY_DEF,
                        CrudResourceExposure.REFERENCE,
                        CrudResourceExposure.AI_ACTION));
    }

    @Bean
    CrudResourceDefinitionProvider<?> userFavoriteResource() {
        return crud(
                "system.user-favorite",
                "收藏",
                UserFavoriteController.class,
                "/api/user-favorites",
                "system:user-favorite");
    }

    @Bean
    CrudResourceDefinitionProvider<?> userOptionsResource() {
        return CrudResourceDefinitions.options(
                "system.user",
                "用户",
                UserController.class,
                "/api/system/users",
                "system:user",
                TENANT);
    }

    private CrudResourceDefinitionProvider<?> crud(
            String key,
            String label,
            Class<?> controllerType,
            String apiPath,
            String permissionNamespace,
            String personalScopeProperty) {
        return CrudResourceDefinitions.crud(
                key,
                label,
                controllerType,
                apiPath,
                permissionNamespace,
                TENANT,
                PersonalScope.byProperty(personalScopeProperty));
    }

    private CrudResourceDefinitionProvider<?> crud(
            String key,
            String label,
            Class<?> controllerType,
            String apiPath,
            String permissionNamespace) {
        return CrudResourceDefinitions.crud(
                key, label, controllerType, apiPath, permissionNamespace, TENANT);
    }
}

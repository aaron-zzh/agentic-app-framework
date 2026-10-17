package com.xuejiai.aaf.module.device.service;

import static com.xuejiai.aaf.module.device.ErrorCodeConstants.DEVICE_OWNER_CREDITS_INSUFFICIENT;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.InsufficientCreditsException;
import com.xuejiai.aaf.framework.engine.cache.ConfigCacheManager;
import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.intelligent.ai.speech.AsrResult;
import com.xuejiai.aaf.framework.intelligent.ai.speech.SpeechService;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.module.ai.assistant.service.AssistantExecutionService;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.ActionAuthorizationPolicy;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.ArtifactPersistence;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.ClarificationPolicy;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.ExecutionOptions;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.Input;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.InteractionMode;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.KnowledgeMode;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.KnowledgeOptions;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.MemoryMode;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.MemoryOptions;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.ModelMode;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.ModelSelection;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.OutputOptions;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.RoleSelection;
import com.xuejiai.aaf.module.device.scenario.DeviceScenario;
import com.xuejiai.aaf.module.device.scenario.DeviceScenarioAccess;
import com.xuejiai.aaf.module.device.security.DevicePrincipal;
import com.xuejiai.aaf.module.device.vo.DeviceBriefVO;

import reactor.core.publisher.Flux;

/** 设备语音网关业务入口，现有用户语音与 AG-UI 端点保持独立。 */
@Service
public class DeviceVoiceGatewayService {

    private static final String ASR_MODEL_ID = "qwen:fun-asr-realtime";
    private static final String TTS_MODEL_ID = "qwen:cosyvoice-v3-flash";

    private final SpeechService speechService;
    private final AiCreditGuard creditGuard;
    private final ConfigCacheManager configCacheManager;
    private final AssistantExecutionService assistantExecutionService;
    private final DeviceOwnerExecutionService ownerExecutionService;
    private final DeviceBriefService briefService;
    private final DeviceScenarioAccess scenarioAccess;
    private final DeviceRoleResolver roleResolver;

    public DeviceVoiceGatewayService(
            SpeechService speechService,
            AiCreditGuard creditGuard,
            ConfigCacheManager configCacheManager,
            AssistantExecutionService assistantExecutionService,
            DeviceOwnerExecutionService ownerExecutionService,
            DeviceBriefService briefService,
            DeviceScenarioAccess scenarioAccess,
            DeviceRoleResolver roleResolver) {
        this.speechService = speechService;
        this.creditGuard = creditGuard;
        this.configCacheManager = configCacheManager;
        this.assistantExecutionService = assistantExecutionService;
        this.ownerExecutionService = ownerExecutionService;
        this.briefService = briefService;
        this.scenarioAccess = scenarioAccess;
        this.roleResolver = roleResolver;
    }

    public void precheckAsr(DevicePrincipal principal) {
        scenarioAccess.requireEnabled(principal.scenarioKey());
        precheck(
                principal, CapabilityRoutingContext.CAP_SPEECH_ASR, AiCreditGuard.INESTIMABLE_COST);
    }

    public Flux<AsrResult> transcribe(
            DevicePrincipal principal, Flux<byte[]> audio, String language) {
        scenarioAccess.requireEnabled(principal.scenarioKey());
        return Flux.defer(
                () ->
                        ownerExecutionService.runAsOwner(
                                principal,
                                "voice-asr",
                                () -> speechService.transcribeStream(audio, language)));
    }

    public void settleAsr(DevicePrincipal principal, int durationMs) {
        if (durationMs <= 0) {
            return;
        }
        var model = configCacheManager.getAiModelByModelId(ASR_MODEL_ID);
        ownerExecutionService.runAsOwner(
                principal,
                "voice-asr-settlement",
                () ->
                        creditGuard.settleByUsage(
                                principal.ownerUserId(),
                                model,
                                AsrResult.ofUsage(durationMs).usage(),
                                CapabilityRoutingContext.CAP_SPEECH_ASR,
                                "设备语音识别"));
    }

    public Flux<byte[]> synthesize(DevicePrincipal principal, String text, String voice) {
        scenarioAccess.requireEnabled(principal.scenarioKey());
        var model = configCacheManager.getAiModelByModelId(TTS_MODEL_ID);
        var estimated = speechService.estimateCost(model, text, creditGuard.getMarkupRate());
        precheck(principal, CapabilityRoutingContext.CAP_SPEECH_TTS, estimated);
        return Flux.defer(
                        () ->
                                ownerExecutionService.runAsOwner(
                                        principal,
                                        "voice-tts",
                                        () -> speechService.synthesizeStream(text, voice)))
                .doOnComplete(
                        () ->
                                ownerExecutionService.runAsOwner(
                                        principal,
                                        "voice-tts-settlement",
                                        () ->
                                                creditGuard.settleFixedReturningTxId(
                                                        principal.ownerUserId(),
                                                        estimated,
                                                        CapabilityRoutingContext.CAP_SPEECH_TTS,
                                                        "设备语音合成")));
    }

    public ChatStream chat(DevicePrincipal principal, String text, String conversationKey) {
        var threadId = threadId(principal, conversationKey);
        var runId = UUID.randomUUID().toString();
        var scenario = scenarioAccess.requireEnabled(principal.scenarioKey());
        var intercept =
                ownerExecutionService.runAsOwner(
                        principal,
                        "voice-chat-pre",
                        () ->
                                OrgContext.runIfAbsent(
                                        principal.orgId(),
                                        principal.workspaceId(),
                                        () -> scenario.preChat(principal, text)));
        if (intercept.isPresent()) {
            return new ChatStream(threadId, runId, Flux.just(intercept.orElseThrow().reply()));
        }

        precheck(principal, CapabilityRoutingContext.CAP_CHAT, AiCreditGuard.INESTIMABLE_COST);
        var brief = briefService.getBrief(principal);
        var execution =
                ownerExecutionService.runAsOwner(
                        principal,
                        "voice-chat",
                        () ->
                                OrgContext.runIfAbsent(
                                        principal.orgId(),
                                        principal.workspaceId(),
                                        () -> {
                                            var roleKey = roleResolver.resolve(principal, scenario);
                                            var variables =
                                                    chatVariables(principal, scenario, brief);
                                            return assistantExecutionService.startRoleRestricted(
                                                    chatRequest(text, variables, roleKey),
                                                    threadId,
                                                    runId);
                                        }));
        var textStream =
                Flux.using(
                        () -> openToolScope(principal, scenario),
                        scope ->
                                execution
                                        .events()
                                        .concatMap(
                                                event -> {
                                                    if (event.type()
                                                            != ExecutionEventType.MESSAGE_DELTA) {
                                                        return Flux.empty();
                                                    }
                                                    var delta =
                                                            event.payload().values().get("delta");
                                                    return delta instanceof String value
                                                                    && !value.isEmpty()
                                                            ? Flux.just(value)
                                                            : Flux.empty();
                                                }),
                        DeviceVoiceGatewayService::closeScope);
        var processed =
                ownerExecutionService.runAsOwner(
                        principal,
                        "voice-chat-post",
                        () ->
                                OrgContext.runIfAbsent(
                                        principal.orgId(),
                                        principal.workspaceId(),
                                        () -> scenario.postChat(principal, text, textStream)));
        return new ChatStream(threadId, runId, processed);
    }

    private void precheck(DevicePrincipal principal, String capability, long estimatedCost) {
        try {
            ownerExecutionService.runAsOwner(
                    principal,
                    "voice-credit-precheck",
                    () -> creditGuard.precheck(principal.ownerUserId(), capability, estimatedCost));
        } catch (InsufficientCreditsException exception) {
            throw new BusinessException(DEVICE_OWNER_CREDITS_INSUFFICIENT);
        }
    }

    private AssistantExecutionRequest chatRequest(
            String text, Map<String, Object> variables, String roleKey) {
        return new AssistantExecutionRequest(
                null,
                new ExecutionOptions(
                        InteractionMode.CONVERSATIONAL,
                        ClarificationPolicy.MINIMAL,
                        ActionAuthorizationPolicy.DENY_AUTHORIZED_ACTIONS,
                        ArtifactPersistence.RETURN_ONLY,
                        null,
                        null,
                        null),
                new Input(text, variables, List.of()),
                new RoleSelection(roleKey),
                null,
                new KnowledgeOptions(KnowledgeMode.DEFAULT, Set.of(), 8, 0.55),
                new ModelSelection(ModelMode.AUTO, null),
                new MemoryOptions(MemoryMode.DEFAULT),
                new OutputOptions(800, null, null));
    }

    private Map<String, Object> chatVariables(
            DevicePrincipal principal, DeviceScenario scenario, DeviceBriefVO brief) {
        var values = new LinkedHashMap<String, Object>();
        values.put("deviceBrief", brief);
        values.putAll(scenario.chatVariables(principal));
        return Map.copyOf(values);
    }

    private AutoCloseable openToolScope(DevicePrincipal principal, DeviceScenario scenario) {
        return ownerExecutionService.runAsOwner(
                principal,
                "voice-chat-tools",
                () ->
                        OrgContext.runIfAbsent(
                                principal.orgId(),
                                principal.workspaceId(),
                                () -> scenario.openToolScope(principal)));
    }

    private static void closeScope(AutoCloseable scope) {
        try {
            scope.close();
        } catch (Exception exception) {
            throw new IllegalStateException("设备场景工具上下文关闭失败", exception);
        }
    }

    private static String threadId(DevicePrincipal principal, String conversationKey) {
        var suffix =
                conversationKey == null || conversationKey.isBlank()
                        ? "default"
                        : conversationKey.trim();
        return "device-%d-%s".formatted(principal.deviceId(), suffix);
    }

    public record ChatStream(String threadId, String runId, Flux<String> text) {}
}

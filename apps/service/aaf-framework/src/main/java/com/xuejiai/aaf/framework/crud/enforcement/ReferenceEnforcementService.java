package com.xuejiai.aaf.framework.crud.enforcement;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.crud.definition.ResourceKey;
import com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO;
import com.xuejiai.aaf.framework.crud.reference.EntityReferenceAccess;
import com.xuejiai.aaf.framework.crud.reference.ReferenceCapability;
import com.xuejiai.aaf.framework.crud.reference.ReferenceContext;
import com.xuejiai.aaf.framework.crud.reference.ReferencePolicy;
import com.xuejiai.aaf.framework.crud.reference.ReferenceRequest;
import com.xuejiai.aaf.framework.crud.reference.ResourceReference;
import com.xuejiai.aaf.framework.crud.relation.RelationDefinition;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceRegistry;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;

import lombok.RequiredArgsConstructor;

/** 统一执行默认资源基线与可选业务收紧策略。 */
@Component
@RequiredArgsConstructor
public final class ReferenceEnforcementService {

    private final CrudResourceRegistry catalog;
    private final ApplicationContext applicationContext;
    private final OperatorContext operatorContext;
    private final ObjectProvider<EntityReferenceAccess> entityReferenceAccess;

    public boolean canRead(
            ResourceKey sourceResource, Long sourceId, String field, ResourceReference target) {
        var request = new ReferenceRequest(sourceId, target);
        return readable(sourceResource, field, Set.of(request)).contains(request);
    }

    public boolean canReference(
            ResourceKey sourceResource, Long sourceId, String field, ResourceReference target) {
        var request = new ReferenceRequest(sourceId, target);
        return referenceable(sourceResource, field, Set.of(request)).contains(request);
    }

    public Set<ReferenceRequest> readable(
            ResourceKey sourceResource, String field, Collection<ReferenceRequest> requests) {
        return authorize(sourceResource, field, requests, ReferenceCapability.READ);
    }

    public Set<ReferenceRequest> referenceable(
            ResourceKey sourceResource, String field, Collection<ReferenceRequest> requests) {
        return authorize(sourceResource, field, requests, ReferenceCapability.REFERENCE);
    }

    public Map<ReferenceRequest, ResourceRefDTO> resolveReadable(
            ResourceKey sourceResource, String field, Collection<ReferenceRequest> requests) {
        var contract = contract(sourceResource, field, ReferenceCapability.READ);
        var normalized = normalize(contract, requests);
        if (normalized.isEmpty()) {
            return Map.of();
        }
        var loaded =
                access().loadReadable(normalized.stream().map(ReferenceRequest::target).toList());
        var baseline =
                normalized.stream()
                        .filter(request -> loaded.containsKey(request.target()))
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        var allowed = applyAdditionalPolicy(sourceResource, field, contract, baseline, true);
        var result = new LinkedHashMap<ReferenceRequest, ResourceRefDTO>();
        allowed.forEach(request -> result.put(request, loaded.get(request.target())));
        return Map.copyOf(result);
    }

    private Set<ReferenceRequest> authorize(
            ResourceKey sourceResource,
            String field,
            Collection<ReferenceRequest> requests,
            ReferenceCapability capability) {
        var contract = contract(sourceResource, field, capability);
        var normalized = normalize(contract, requests);
        if (normalized.isEmpty()) {
            return Set.of();
        }
        var targets = normalized.stream().map(ReferenceRequest::target).toList();
        var baselineTargets =
                capability == ReferenceCapability.READ
                        ? access().readable(targets)
                        : access().referenceable(targets);
        var baseline =
                normalized.stream()
                        .filter(request -> baselineTargets.contains(request.target()))
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return applyAdditionalPolicy(
                sourceResource, field, contract, baseline, capability == ReferenceCapability.READ);
    }

    private Set<ReferenceRequest> applyAdditionalPolicy(
            ResourceKey sourceResource,
            String field,
            Contract contract,
            Set<ReferenceRequest> baseline,
            boolean read) {
        if (baseline.isEmpty() || contract.additionalPolicyBean().isBlank()) {
            return Set.copyOf(baseline);
        }
        var contexts = new LinkedHashMap<ReferenceContext, ReferenceRequest>();
        baseline.forEach(
                request -> {
                    var context =
                            new ReferenceContext(
                                    sourceResource,
                                    request.sourceId(),
                                    field,
                                    request.target(),
                                    operatorContext.currentOwnerId().orElse(null),
                                    OrgContext.getCurrentOrgId(),
                                    OrgContext.getCurrentWorkspaceId());
                    contexts.put(context, request);
                });
        var policy =
                applicationContext.getBean(contract.additionalPolicyBean(), ReferencePolicy.class);
        var input = Set.copyOf(contexts.keySet());
        var filtered = read ? policy.filterReadable(input) : policy.filterReferenceable(input);
        if (filtered == null) {
            throw new IllegalStateException(
                    "ReferencePolicy 返回 null: " + contract.additionalPolicyBean());
        }
        var allowed = new LinkedHashSet<ReferenceRequest>();
        filtered.forEach(
                context -> {
                    var request = contexts.get(context);
                    if (request != null) {
                        allowed.add(request);
                    }
                });
        return Set.copyOf(allowed);
    }

    private Set<ReferenceRequest> normalize(
            Contract contract, Collection<ReferenceRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return Set.of();
        }
        var normalized = new LinkedHashSet<ReferenceRequest>();
        for (var request : requests) {
            if (request == null || !valid(request.target())) {
                continue;
            }
            if (contract.targetResource() != null
                    && !contract.targetResource().value().equals(request.target().resource())) {
                continue;
            }
            normalized.add(request);
        }
        return Set.copyOf(normalized);
    }

    private boolean valid(ResourceReference target) {
        return target != null
                && target.resource() != null
                && !target.resource().isBlank()
                && target.id() != null
                && target.id() > 0;
    }

    private Contract contract(
            ResourceKey sourceResource, String field, ReferenceCapability capability) {
        var definition = catalog.require(sourceResource).definition();
        var reference =
                definition.references().stream()
                        .filter(candidate -> candidate.key().equals(field))
                        .findFirst();
        if (reference.isPresent()) {
            var value = reference.get();
            requireCapability(sourceResource, field, capability, value.supports(capability));
            return new Contract(value.targetResource(), value.additionalPolicyBean());
        }
        var relation =
                definition.relations().stream()
                        .filter(candidate -> candidate.key().equals(field))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "资源 %s 未声明引用或关系: %s"
                                                        .formatted(sourceResource.value(), field)));
        var supported =
                capability == ReferenceCapability.READ
                        || relation.syncMode() != RelationDefinition.SyncMode.READ_ONLY;
        requireCapability(sourceResource, field, capability, supported);
        return new Contract(relation.targetResource(), relation.additionalPolicyBean());
    }

    private void requireCapability(
            ResourceKey sourceResource,
            String field,
            ReferenceCapability capability,
            boolean supported) {
        if (!supported) {
            throw new IllegalStateException(
                    "资源 %s 的引用字段 %s 未声明能力 %s".formatted(sourceResource.value(), field, capability));
        }
    }

    private EntityReferenceAccess access() {
        return entityReferenceAccess.getObject();
    }

    private record Contract(ResourceKey targetResource, String additionalPolicyBean) {}
}

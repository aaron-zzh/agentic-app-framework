package com.xuejiai.aaf.framework.bizlog.util.diff;

import java.util.*;

import de.danielbechler.diff.access.Accessor;
import de.danielbechler.diff.access.Instances;
import de.danielbechler.diff.comparison.ComparisonStrategy;
import de.danielbechler.diff.comparison.ComparisonStrategyResolver;
import de.danielbechler.diff.differ.Differ;
import de.danielbechler.diff.differ.DifferDispatcher;
import de.danielbechler.diff.identity.IdentityStrategy;
import de.danielbechler.diff.identity.IdentityStrategyResolver;
import de.danielbechler.diff.node.DiffNode;
import de.danielbechler.util.Assert;

/** 扩展 java-object-diff 的数组类型 Differ，使数组与 Collection 享有相同的 diff 能力。 */
public class ArrayDiffer implements Differ {

    private final DifferDispatcher differDispatcher;
    private final ComparisonStrategyResolver comparisonStrategyResolver;
    private final IdentityStrategyResolver identityStrategyResolver;

    public ArrayDiffer(
            DifferDispatcher differDispatcher,
            ComparisonStrategyResolver comparisonStrategyResolver,
            IdentityStrategyResolver identityStrategyResolver) {
        Assert.notNull(differDispatcher, "differDispatcher");
        Assert.notNull(comparisonStrategyResolver, "comparisonStrategyResolver");
        Assert.notNull(identityStrategyResolver, "identityStrategyResolver");
        this.differDispatcher = differDispatcher;
        this.comparisonStrategyResolver = comparisonStrategyResolver;
        this.identityStrategyResolver = identityStrategyResolver;
    }

    @Override
    public boolean accepts(Class<?> type) {
        return !type.isPrimitive() && type.isArray();
    }

    @Override
    public DiffNode compare(DiffNode parentNode, Instances collectionInstances) {
        DiffNode collectionNode = newNode(parentNode, collectionInstances);
        IdentityStrategy identityStrategy =
                identityStrategyResolver.resolveIdentityStrategy(collectionNode);
        if (identityStrategy != null) {
            collectionNode.setChildIdentityStrategy(identityStrategy);
        }
        if (collectionInstances.hasBeenAdded()) {
            compareItems(
                    collectionNode,
                    collectionInstances,
                    findCollection(collectionInstances.getWorking()),
                    identityStrategy);
            collectionNode.setState(DiffNode.State.ADDED);
        } else if (collectionInstances.hasBeenRemoved()) {
            compareItems(
                    collectionNode,
                    collectionInstances,
                    findCollection(collectionInstances.getBase()),
                    identityStrategy);
            collectionNode.setState(DiffNode.State.REMOVED);
        } else if (collectionInstances.areSame()) {
            collectionNode.setState(DiffNode.State.UNTOUCHED);
        } else {
            ComparisonStrategy comparisonStrategy =
                    comparisonStrategyResolver.resolveComparisonStrategy(collectionNode);
            if (comparisonStrategy == null) {
                compareInternally(collectionNode, collectionInstances, identityStrategy);
            } else {
                compareUsingComparisonStrategy(
                        collectionNode, collectionInstances, comparisonStrategy);
            }
        }
        return collectionNode;
    }

    private Collection<?> findCollection(Object source) {
        return source == null
                ? new ArrayList<>()
                : new LinkedList<>(Arrays.asList((Object[]) source));
    }

    private static DiffNode newNode(DiffNode parentNode, Instances collectionInstances) {
        Accessor accessor = collectionInstances.getSourceAccessor();
        Class<?> type = collectionInstances.getType();
        return new DiffNode(parentNode, accessor, type);
    }

    private void compareItems(
            DiffNode collectionNode,
            Instances collectionInstances,
            Iterable<?> items,
            IdentityStrategy identityStrategy) {
        for (Object item : items) {
            Accessor itemAccessor = new ArrayItemAccessor(item, identityStrategy);
            differDispatcher.dispatch(collectionNode, collectionInstances, itemAccessor);
        }
    }

    private void compareInternally(
            DiffNode collectionNode,
            Instances collectionInstances,
            IdentityStrategy identityStrategy) {
        Collection<?> working = Arrays.asList((Object[]) collectionInstances.getWorking());
        Collection<?> base = Arrays.asList((Object[]) collectionInstances.getBase());

        List<Object> added = new LinkedList<>(working);
        List<Object> removed = new LinkedList<>(base);
        List<Object> known = new LinkedList<>(base);

        remove(added, base, identityStrategy);
        remove(removed, working, identityStrategy);
        remove(known, added, identityStrategy);
        remove(known, removed, identityStrategy);

        compareItems(collectionNode, collectionInstances, added, identityStrategy);
        compareItems(collectionNode, collectionInstances, removed, identityStrategy);
        compareItems(collectionNode, collectionInstances, known, identityStrategy);
    }

    private static void compareUsingComparisonStrategy(
            DiffNode collectionNode,
            Instances collectionInstances,
            ComparisonStrategy comparisonStrategy) {
        comparisonStrategy.compare(
                collectionNode,
                collectionInstances.getType(),
                collectionInstances.getWorking(Collection.class),
                collectionInstances.getBase(Collection.class));
    }

    private void remove(List<Object> from, Iterable<?> these, IdentityStrategy identityStrategy) {
        var iterator = from.iterator();
        while (iterator.hasNext()) {
            Object item = iterator.next();
            if (contains(these, item, identityStrategy)) iterator.remove();
        }
    }

    private boolean contains(
            Iterable<?> haystack, Object needle, IdentityStrategy identityStrategy) {
        for (Object item : haystack) {
            if (identityStrategy.equals(needle, item)) return true;
        }
        return false;
    }
}

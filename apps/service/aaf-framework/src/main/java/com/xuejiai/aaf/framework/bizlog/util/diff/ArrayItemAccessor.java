package com.xuejiai.aaf.framework.bizlog.util.diff;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.springframework.util.Assert;

import de.danielbechler.diff.access.Accessor;
import de.danielbechler.diff.access.TypeAwareAccessor;
import de.danielbechler.diff.identity.EqualsIdentityStrategy;
import de.danielbechler.diff.identity.IdentityStrategy;
import de.danielbechler.diff.selector.CollectionItemElementSelector;
import de.danielbechler.diff.selector.ElementSelector;

/** 数组元素访问器，用于 java-object-diff 的数组 diff 支持。 */
public class ArrayItemAccessor implements TypeAwareAccessor, Accessor {

    private final Object referenceItem;
    private final IdentityStrategy identityStrategy;

    public ArrayItemAccessor(Object referenceItem) {
        this(referenceItem, EqualsIdentityStrategy.getInstance());
    }

    public ArrayItemAccessor(Object referenceItem, IdentityStrategy identityStrategy) {
        Assert.notNull(identityStrategy, "identityStrategy");
        this.referenceItem = referenceItem;
        this.identityStrategy = identityStrategy;
    }

    @Override
    public Class<?> getType() {
        return referenceItem != null ? referenceItem.getClass() : null;
    }

    @Override
    public String toString() {
        return "collection item " + getElementSelector();
    }

    @Override
    public ElementSelector getElementSelector() {
        var selector = new CollectionItemElementSelector(referenceItem);
        return identityStrategy == null
                ? selector
                : selector.copyWithIdentityStrategy(identityStrategy);
    }

    @Override
    public Object get(Object target) {
        Collection<?> targetCollection = objectAsCollection(target);
        if (targetCollection == null) return null;
        for (Object item : targetCollection) {
            if (item != null && identityStrategy.equals(item, referenceItem)) return item;
        }
        return null;
    }

    @Override
    public void set(Object target, Object value) {
        @SuppressWarnings("unchecked")
        Collection<Object> targetCollection = (Collection<Object>) objectAsCollection(target);
        if (targetCollection == null) return;
        Object previous = get(target);
        if (previous != null) unset(target);
        targetCollection.add(value);
    }

    @Override
    public void unset(Object target) {
        Collection<?> targetCollection = objectAsCollection(target);
        if (targetCollection == null) return;
        var iterator = targetCollection.iterator();
        while (iterator.hasNext()) {
            Object item = iterator.next();
            if (item != null && identityStrategy.equals(item, referenceItem)) {
                iterator.remove();
                break;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Collection<Object> objectAsCollection(Object object) {
        if (object == null) return null;
        if (object.getClass().isArray()) return new ArrayList<>(Arrays.asList((Object[]) object));
        throw new IllegalArgumentException(object.getClass().toString());
    }
}

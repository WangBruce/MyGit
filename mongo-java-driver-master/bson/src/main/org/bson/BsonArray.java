/*
 * Copyright (c) 2008-2014 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bson;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * A type-safe representation of the BSON array type.
 *
 * @since 3.0
 */
public class BsonArray extends BsonValue implements List<BsonValue>, Serializable {

    private static final long serialVersionUID = -6848772175446898432L;
    private final List<BsonValue> values;

    /**
     * Construct an instance with the given list of values.
     *
     * @param values the list of values, none of whose members may be null.
     */
    public BsonArray(final List<? extends BsonValue> values) {
        this.values = new ArrayList<BsonValue>(values);
    }

    /**
     * Construct an empty B
     */
    public BsonArray() {
        values = new ArrayList<BsonValue>();
    }

    /**
     * Gets the values in this array as a list of {@code BsonValue} objects.
     *
     * @return the values in this array.
     */
    public List<BsonValue> getValues() {
        return Collections.unmodifiableList(values);
    }

    @Override
    public BsonType getBsonType() {
        return BsonType.ARRAY;
    }

    @Override
    public int size() {
        return values.size();
    }

    @Override
    public boolean isEmpty() {
        return values.isEmpty();
    }

    @Override
    public boolean contains(final Object o) {
        return values.contains(o);
    }

    @Override
    public Iterator<BsonValue> iterator() {
        return values.iterator();
    }

    @Override
    public Object[] toArray() {
        return values.toArray();
    }

    @Override
    public <T> T[] toArray(final T[] a) {
        return values.toArray(a);
    }

    @Override
    public boolean add(final BsonValue bsonValue) {
        return values.add(bsonValue);
    }

    @Override
    public boolean remove(final Object o) {
        return values.remove(o);
    }

    @Override
    public boolean containsAll(final Collection<?> c) {
        return values.containsAll(c);
    }

    @Override
    public boolean addAll(final Collection<? extends BsonValue> c) {
        return values.addAll(c);
    }

    @Override
    public boolean addAll(final int index, final Collection<? extends BsonValue> c) {
        return values.addAll(index, c);
    }

    @Override
    public boolean removeAll(final Collection<?> c) {
        return values.removeAll(c);
    }

    @Override
    public boolean retainAll(final Collection<?> c) {
        return values.retainAll(c);
    }

    @Override
    public void clear() {
        values.clear();
    }

    @Override
    public BsonValue get(final int index) {
        return values.get(index);
    }

    @Override
    public BsonValue set(final int index, final BsonValue element) {
        return values.set(index, element);
    }

    @Override
    public void add(final int index, final BsonValue element) {
        values.add(index, element);
    }

    @Override
    public BsonValue remove(final int index) {
        return values.remove(index);
    }

    @Override
    public int indexOf(final Object o) {
        return values.indexOf(o);
    }

    @Override
    public int lastIndexOf(final Object o) {
        return values.lastIndexOf(o);
    }

    @Override
    public ListIterator<BsonValue> listIterator() {
        return values.listIterator();
    }

    @Override
    public ListIterator<BsonValue> listIterator(final int index) {
        return values.listIterator(index);
    }

    @Override
    public List<BsonValue> subList(final int fromIndex, final int toIndex) {
        return values.subList(fromIndex, toIndex);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        BsonArray that = (BsonArray) o;

        if (!values.equals(that.values)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return "BsonArray{"
               + "values=" + values
               + '}';
    }
}

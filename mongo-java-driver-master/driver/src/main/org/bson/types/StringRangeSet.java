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

package org.bson.types;

import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

class StringRangeSet {
    private final Set<String> numbersAsStrings;

    StringRangeSet(final int size) {
        numbersAsStrings = new TreeSet<String>(new NumberStringComparator());
        for (int i = 0; i < size; i++) {
            numbersAsStrings.add(String.valueOf(i));
        }
    }

    public Set<String> getSet() {
        return numbersAsStrings;
    }

    private static class NumberStringComparator implements Comparator<String> {
        @Override
        public int compare(final String o1, final String o2) {
            return Integer.valueOf(o1).compareTo(Integer.valueOf(o2));
        }
    }
}

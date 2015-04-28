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

package org.bson.types

import com.mongodb.BasicDBList
import org.bson.BSONObject
import spock.lang.Specification

import static junit.framework.TestCase.fail

class BasicBSONListSpecification extends Specification {

    def 'should support int keys'() {
        when:
        BSONObject obj = new BasicBSONList()
        obj.put(0, 'a')
        obj.put(1, 'b')
        obj.put(2, 'c')

        then:
        obj == ['a', 'b', 'c'] as BasicDBList
    }

    def 'should support keys that are strings which be converted to ints'() {
        when:
        BSONObject obj = new BasicBSONList()
        obj.put('0', 'a')
        obj.put('1', 'b')
        obj.put('2', 'c')

        then:
        obj == ['a', 'b', 'c'] as BasicDBList
    }

    def 'should throw IllegalArgumentException if passed invalid string key'() {
        when:
        BSONObject obj = new BasicBSONList()
        obj.put('ZERO', 'a')

        then:
        thrown IllegalArgumentException
    }

    def 'should insert null values for missing keys'() {
        when:
        BSONObject obj = new BasicBSONList()
        obj.put(0, 'a')
        obj.put(1, 'b')
        obj.put(5, 'c')

        then:
        obj == ['a', 'b', null, null, null, 'c'] as BasicDBList
    }

    def 'should provide an iterable keySet'() {
        when:
        def counter = 0
        BSONObject obj = new BasicBSONList()
        obj.put(0, 'a')
        obj.put(1, 'b')
        obj.put(5, 'c')
        def iter = obj.keySet().iterator()
        while (iter.next()) {
            if (counter > 5) { fail() }
            counter++
        }

        then:
        thrown NoSuchElementException
    }
}

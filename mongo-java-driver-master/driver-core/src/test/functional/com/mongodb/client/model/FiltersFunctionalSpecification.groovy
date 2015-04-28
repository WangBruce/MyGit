/*
 * Copyright 2015 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONObjectITIONS OF ANY KINObject, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.client.model

import com.mongodb.MongoQueryException
import com.mongodb.OperationFunctionalSpecification
import org.bson.BsonType
import org.bson.Document
import org.bson.conversions.Bson
import spock.lang.IgnoreIf

import java.util.regex.Pattern

import static com.mongodb.ClusterFixture.serverVersionAtLeast
import static com.mongodb.client.model.Filters.all
import static com.mongodb.client.model.Filters.and
import static com.mongodb.client.model.Filters.elemMatch
import static com.mongodb.client.model.Filters.eq
import static com.mongodb.client.model.Filters.exists
import static com.mongodb.client.model.Filters.gt
import static com.mongodb.client.model.Filters.gte
import static com.mongodb.client.model.Filters.lt
import static com.mongodb.client.model.Filters.lte
import static com.mongodb.client.model.Filters.mod
import static com.mongodb.client.model.Filters.ne
import static com.mongodb.client.model.Filters.nin
import static com.mongodb.client.model.Filters.nor
import static com.mongodb.client.model.Filters.not
import static com.mongodb.client.model.Filters.or
import static com.mongodb.client.model.Filters.regex
import static com.mongodb.client.model.Filters.size
import static com.mongodb.client.model.Filters.text
import static com.mongodb.client.model.Filters.type
import static com.mongodb.client.model.Filters.where

class FiltersFunctionalSpecification extends OperationFunctionalSpecification {
    def a = new Document('_id', 1).append('x', 1)
                                  .append('y', 'a')
                                  .append('a', [1, 2, 3])
                                  .append('a1', [new Document('c', 1).append('d', 2), new Document('c', 2).append('d', 3)])

    def b = new Document('_id', 2).append('x', 2)
                                  .append('y', 'b')
                                  .append('a', [3, 4, 5, 6])
                                  .append('a1', [new Document('c', 2).append('d', 3), new Document('c', 3).append('d', 4)])

    def c = new Document('_id', 3).append('x', 3)
                                  .append('y', 'c')
                                  .append('z', true)

    def setup() {
        getCollectionHelper().insertDocuments(a, b, c)
        getCollectionHelper().createIndex(new Document('y', 'text'))
    }

    def 'find'(Bson filter) {
        getCollectionHelper().find(filter, new Document('_id', 1)) // sort by _id
    }

    def 'eq'() {
        expect:
        find(eq('x', 1)) == [a]
    }

    def '$ne'() {
        expect:
        find(ne('x', 1)) == [b, c]
    }

    @IgnoreIf({ !serverVersionAtLeast([2, 6, 0]) })
    def '$not'() {
        expect:
        find(not(eq('x', 1))) == [b, c]
        find(not(gt('x', 1))) == [a]
        find(not(regex('y', 'a.*'))) == [b, c]

        when:
        find(not(and(eq('x', 1), eq('x', 1)))) == [b, c]

        then:
        thrown MongoQueryException
    }

    def '$nor'() {
        expect:
        find(nor(eq('x', 1))) == [b, c]
        find(nor(eq('x', 1), eq('x', 2))) == [c]
        find(nor(and(eq('x', 1), eq('y', 'b')))) == [a, b, c]
    }

    def '$gt'() {
        expect:
        find(gt('x', 1)) == [b, c]
    }

    def '$lt'() {
        expect:
        find(lt('x', 3)) == [a, b]
    }

    def '$gte'() {
        expect:
        find(gte('x', 2)) == [b, c]
    }

    def '$lte'() {
        expect:
        find(lte('x', 2)) == [a, b]
    }

    def '$exists'() {
        expect:
        find(exists('z')) == [c]
        find(exists('z', false)) == [a, b]
    }

    def '$or'() {
        expect:
        find(or([eq('x', 1)])) == [a]
        find(or([eq('x', 1), eq('y', 'b')])) == [a, b]
    }

    def 'and'() {
        expect:
        find(and([eq('x', 1)])) == [a]
        find(and([eq('x', 1), eq('y', 'a')])) == [a]
    }

    def 'and should duplicate clashing keys'() {
        expect:
        find(and([eq('x', 1), eq('x', 1)])) == [a]
    }

    def 'and should flatten multiple operators for the same key'() {
        expect:
        find(and([gte('x', 1), lte('x', 2)])) == [a, b]
    }

    def 'and should flatten nested'() {
        expect:
        find(and([and([eq('x', 3), eq('y', 'c')]), eq('z', true)])) == [c]
        find(and([and([eq('x', 3), eq('x', 3)]), eq('z', true)])) == [c]
        find(and([gt('x', 1), gt('y', 'a')])) == [b, c]
        find(and([lt('x', 4), lt('x', 3)])) == [a, b]
    }

    def 'should render $all'() {
        expect:
        find(all('a', [1, 2])) == [a]
    }

    def 'should render $elemMatch'() {
        expect:
        find(elemMatch('a', new Document('$gte', 2).append('$lte', 2))) == [a]
        find(elemMatch('a1', and(eq('c', 1), gte('d', 2)))) == [a]
        find(elemMatch('a1', and(eq('c', 2), eq('d', 3)))) == [a, b]
    }

    def 'should render $in'() {
        expect:
        find(Filters.in('a', [0, 1, 2])) == [a]
    }

    def 'should render $nin'() {
        expect:
        find(nin('a', [1, 2])) == [b, c]
    }

    def 'should render $mod'() {
        expect:
        find(mod('x', 2, 0)) == [b]
    }

    def 'should render $size'() {
        expect:
        find(size('a', 4)) == [b]
    }

    def 'should render $type'() {
        expect:
        find(type('x', BsonType.INT32)) == [a, b, c]
        find(type('x', BsonType.ARRAY)) == []
    }

    @IgnoreIf({ !serverVersionAtLeast([2, 6, 0]) })
    def 'should render $text'() {
        expect:
        find(text('I love MongoDB')) == []
        find(text('I love MongoDB', 'English')) == []
    }

    def 'should render $regex'() {
        expect:
        find(regex('y', 'a.*')) == [a]
        find(regex('y', 'a.*', 'si')) ==  [a]
        find(regex('y', Pattern.compile('a.*'))) == [a]
    }

    def 'should render $where'() {
        expect:
        find(where('Array.isArray(this.a)')) == [a, b]
    }
}

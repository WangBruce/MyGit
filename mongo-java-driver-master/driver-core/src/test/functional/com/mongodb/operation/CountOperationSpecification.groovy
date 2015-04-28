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

package com.mongodb.operation

import category.Async
import com.mongodb.ExplainVerbosity
import com.mongodb.MongoException
import com.mongodb.MongoExecutionTimeoutException
import com.mongodb.OperationFunctionalSpecification
import com.mongodb.bulk.IndexRequest
import org.bson.BsonDocument
import org.bson.BsonInt32
import org.bson.BsonString
import org.bson.Document
import org.bson.codecs.DocumentCodec
import org.junit.experimental.categories.Category
import spock.lang.IgnoreIf

import static com.mongodb.ClusterFixture.disableMaxTimeFailPoint
import static com.mongodb.ClusterFixture.enableMaxTimeFailPoint
import static com.mongodb.ClusterFixture.executeAsync
import static com.mongodb.ClusterFixture.getBinding
import static com.mongodb.ClusterFixture.isSharded
import static com.mongodb.ClusterFixture.serverVersionAtLeast
import static java.util.Arrays.asList
import static java.util.concurrent.TimeUnit.SECONDS

class CountOperationSpecification extends OperationFunctionalSpecification {

    private documents;

    def setup() {
        documents = [
                new Document('x', 1),
                new Document('x', 2),
                new Document('x', 3),
                new Document('x', 4),
                new Document('x', 5)
        ]
        getCollectionHelper().insertDocuments(new DocumentCodec(), documents)
    }

    def 'should get the count'() {
        expect:
        new CountOperation(getNamespace()).execute(getBinding()) == documents.size()
    }

    @Category(Async)
    def 'should get the count asynchronously'() {
        expect:
        executeAsync(new CountOperation(getNamespace())) ==
        documents.size()
    }

    @IgnoreIf({ !serverVersionAtLeast(asList(2, 6, 0)) })
    def 'should throw execution timeout exception from execute'() {
        given:
        def countOperation = new CountOperation(getNamespace())
                .maxTime(1, SECONDS)
        enableMaxTimeFailPoint()

        when:
        countOperation.execute(getBinding())

        then:
        thrown(MongoExecutionTimeoutException)

        cleanup:
        disableMaxTimeFailPoint()
    }

    @Category(Async)
    @IgnoreIf({ !serverVersionAtLeast(asList(2, 6, 0)) })
    def 'should throw execution timeout exception from executeAsync'() {
        given:
        def countOperation = new CountOperation(getNamespace())
                .maxTime(1, SECONDS)
        enableMaxTimeFailPoint()

        when:
        executeAsync(countOperation)

        then:
        thrown(MongoExecutionTimeoutException)

        cleanup:
        disableMaxTimeFailPoint()
    }

    def 'should use limit with the count'() {
        when:
        def countOperation = new CountOperation(getNamespace())
                .limit(1)
        then:
        countOperation.execute(getBinding()) == 1
    }

    @Category(Async)
    def 'should use limit with the count asynchronously'() {
        when:
        def countOperation = new CountOperation(getNamespace())
                .limit(1)

        then:
        executeAsync(countOperation) == 1
    }

    def 'should use skip with the count'() {
        when:
        def countOperation = new CountOperation(getNamespace()).skip(documents.size() - 2)

        then:
        countOperation.execute(getBinding()) == 2
    }

    @Category(Async)
    def 'should use skip with the count asynchronously'() {
        when:
        def countOperation = new CountOperation(getNamespace()).skip(documents.size() - 2)

        then:
        executeAsync(countOperation)  == 2
    }

    def 'should use hint with the count'() {
        given:
        def createIndexOperation = new CreateIndexesOperation(getNamespace(),
                                                              [new IndexRequest(new BsonDocument('x', new BsonInt32(1))).sparse(true)])
        def countOperation = new CountOperation(getNamespace()).hint(new BsonString('x_1'))

        when:
        createIndexOperation.execute(getBinding())

        then:
        countOperation.execute(getBinding()) == serverVersionAtLeast(asList(2, 6, 0)) ? 1 : documents.size()
    }

    @Category(Async)
    def 'should use hint with the count asynchronously'() {
        given:
        def createIndexOperation = new CreateIndexesOperation(getNamespace(),
                                                              [new IndexRequest(new BsonDocument('x', new BsonInt32(1))).sparse(true)])
        def countOperation = new CountOperation(getNamespace()).hint(new BsonString('x_1'))

        when:
        executeAsync(createIndexOperation)

        then:
        executeAsync(countOperation) == serverVersionAtLeast(asList(2, 6, 0)) ? 1 : documents.size()
    }

    @IgnoreIf({ !serverVersionAtLeast(asList(2, 6, 0)) })
    def 'should throw with bad hint with mongod 2.6+'() {
        given:
        def countOperation = new CountOperation(getNamespace())
                .filter(new BsonDocument('a', new BsonInt32(1)))
                .hint(new BsonString('BAD HINT'))

        when:
        countOperation.execute(getBinding())

        then:
        thrown(MongoException)
    }

    @Category(Async)
    @IgnoreIf({ !serverVersionAtLeast(asList(2, 6, 0)) })
    def 'should throw with bad hint with mongod 2.6+ asynchronously'() {
        given:
        def countOperation = new CountOperation(getNamespace())
                .filter(new BsonDocument('a', new BsonInt32(1)))
                .hint(new BsonString('BAD HINT'))

        when:
        executeAsync(countOperation)

        then:
        thrown(MongoException)
    }

    @IgnoreIf({ serverVersionAtLeast(asList(2, 6, 0)) })
    def 'should ignore with bad hint with mongod < 2.6'() {
        given:
        def countOperation = new CountOperation(getNamespace())
                .filter(new BsonDocument('a', new BsonInt32(1)))
                .hint(new BsonString('BAD HINT'))

        when:
        countOperation.execute(getBinding())

        then:
        notThrown(MongoException)
    }

    @Category(Async)
    @IgnoreIf({ serverVersionAtLeast(asList(2, 6, 0)) })
    def 'should ignore with bad hint with mongod < 2.6 asynchronously'() {
        given:
        def countOperation = new CountOperation(getNamespace())
                .filter(new BsonDocument('a', new BsonInt32(1)))
                .hint(new BsonString('BAD HINT'))

        when:
        executeAsync(countOperation)

        then:
        notThrown(MongoException)
    }

    @IgnoreIf({ !serverVersionAtLeast(asList(3, 0, 0)) || isSharded() })
    def 'should explain'() {
        given:
        def countOperation = new CountOperation(getNamespace())
                .filter(new BsonDocument('a', new BsonInt32(1)))

        when:
        BsonDocument result = countOperation.asExplainableOperation(ExplainVerbosity.QUERY_PLANNER).execute(getBinding())

        then:
        result.getNumber('ok').intValue() == 1
    }

    @Category(Async)
    @IgnoreIf({ !serverVersionAtLeast(asList(3, 0, 0)) || isSharded() })
    def 'should explain asynchronously'() {
        given:
        def countOperation = new CountOperation(getNamespace())
                .filter(new BsonDocument('a', new BsonInt32(1)))

        when:
        BsonDocument result = executeAsync(countOperation.asExplainableOperationAsync(ExplainVerbosity.QUERY_PLANNER))

        then:
        result.getNumber('ok').intValue() == 1
    }

}

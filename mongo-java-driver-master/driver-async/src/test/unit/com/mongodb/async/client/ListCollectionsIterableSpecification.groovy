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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.async.client

import com.mongodb.Block
import com.mongodb.Function
import com.mongodb.async.AsyncBatchCursor
import com.mongodb.async.FutureResultCallback
import com.mongodb.operation.ListCollectionsOperation
import org.bson.BsonDocument
import org.bson.BsonInt32
import org.bson.Document
import org.bson.codecs.BsonValueCodecProvider
import org.bson.codecs.DocumentCodec
import org.bson.codecs.DocumentCodecProvider
import org.bson.codecs.ValueCodecProvider
import spock.lang.Specification

import static com.mongodb.CustomMatchers.isTheSameAs
import static com.mongodb.ReadPreference.secondary
import static java.util.concurrent.TimeUnit.MILLISECONDS
import static org.bson.codecs.configuration.CodecRegistries.fromProviders
import static spock.util.matcher.HamcrestSupport.expect

class ListCollectionsIterableSpecification extends Specification {

    def codecRegistry = fromProviders([new ValueCodecProvider(), new DocumentCodecProvider(), new BsonValueCodecProvider()])
    def readPreference = secondary()


    def 'should build the expected listCollectionOperation'() {
        given:
        def cursor = Stub(AsyncBatchCursor) {
            next(_) >> {
                it[0].onResult(null, null)
            }
        }
        def executor = new TestOperationExecutor([cursor, cursor]);
        def listCollectionIterable = new ListCollectionsIterableImpl<Document>('db', Document, codecRegistry, readPreference, executor)
                .filter(new Document('filter', 1))
                .batchSize(100)
                .maxTime(1000, MILLISECONDS)

        when: 'default input should be as expected'
        listCollectionIterable.into([]) { result, t -> }

        def operation = executor.getReadOperation() as ListCollectionsOperation<Document>
        def readPreference = executor.getReadPreference()

        then:
        expect operation, isTheSameAs(new ListCollectionsOperation<Document>('db', new DocumentCodec())
                .filter(new BsonDocument('filter', new BsonInt32(1))).batchSize(100).maxTime(1000, MILLISECONDS))
        readPreference == secondary()

        when: 'overriding initial options'
        listCollectionIterable.filter(new Document('filter', 2)).batchSize(99).maxTime(999, MILLISECONDS).into([]) { result, t -> }

        operation = executor.getReadOperation() as ListCollectionsOperation<Document>

        then: 'should use the overrides'
        expect operation, isTheSameAs(new ListCollectionsOperation<Document>('db', new DocumentCodec())
                .filter(new BsonDocument('filter', new BsonInt32(2))).batchSize(99).maxTime(999, MILLISECONDS))
    }

    def 'should follow the MongoIterable interface as expected'() {
        given:
        def cannedResults = [new Document('_id', 1), new Document('_id', 1), new Document('_id', 1)]
        def cursor = {
            Stub(AsyncBatchCursor) {
                def count = 0
                def results;
                def getResult = {
                    count++
                    results = count == 1 ? cannedResults : null
                    results
                }
                next(_) >> {
                    it[0].onResult(getResult(), null)
                }
                isClosed() >> { count >= 1 }
            }
        }
        def executor = new TestOperationExecutor([cursor(), cursor(), cursor(), cursor(), cursor()]);
        def mongoIterable = new ListCollectionsIterableImpl<Document>('db', Document, codecRegistry, readPreference, executor)

        when:
        def results = new FutureResultCallback()
        mongoIterable.first(results)

        then:
        results.get() == cannedResults[0]

        when:
        def count = 0
        results = new FutureResultCallback()
        mongoIterable.forEach(new Block<Document>() {
            @Override
            void apply(Document document) {
                count++
            }
        }, results)
        results.get()

        then:
        count == 3

        when:
        def target = []
        results = new FutureResultCallback()
        mongoIterable.into(target, results)

        then:
        results.get() == cannedResults

        when:
        target = []
        results = new FutureResultCallback()
        mongoIterable.map(new Function<Document, Integer>() {
            @Override
            Integer apply(Document document) {
                document.getInteger('_id')
            }
        }).into(target, results)
        then:
        results.get() == [1, 1, 1]

        when:
        results = new FutureResultCallback()
        mongoIterable.batchCursor(results)
        def batchCursor = results.get()

        then:
        !batchCursor.isClosed()

        when:
        results = new FutureResultCallback()
        batchCursor.next(results)

        then:
        results.get() == cannedResults
        batchCursor.isClosed()
    }

}

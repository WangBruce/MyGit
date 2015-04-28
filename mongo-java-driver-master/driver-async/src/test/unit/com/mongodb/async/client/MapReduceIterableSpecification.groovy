/*
 * Copyright 2015 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
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
import com.mongodb.MongoException
import com.mongodb.MongoNamespace
import com.mongodb.async.AsyncBatchCursor
import com.mongodb.async.FutureResultCallback
import com.mongodb.client.model.MapReduceAction
import com.mongodb.operation.FindOperation
import com.mongodb.operation.MapReduceStatistics
import com.mongodb.operation.MapReduceToCollectionOperation
import com.mongodb.operation.MapReduceWithInlineResultsOperation
import org.bson.BsonDocument
import org.bson.BsonInt32
import org.bson.BsonJavaScript
import org.bson.Document
import org.bson.codecs.BsonValueCodecProvider
import org.bson.codecs.DocumentCodec
import org.bson.codecs.DocumentCodecProvider
import org.bson.codecs.ValueCodecProvider
import org.bson.codecs.configuration.CodecConfigurationException
import spock.lang.Specification

import static com.mongodb.CustomMatchers.isTheSameAs
import static com.mongodb.ReadPreference.secondary
import static java.util.concurrent.TimeUnit.MILLISECONDS
import static org.bson.codecs.configuration.CodecRegistries.fromProviders
import static spock.util.matcher.HamcrestSupport.expect

class MapReduceIterableSpecification extends Specification {

    def namespace = new MongoNamespace('db', 'coll')
    def codecRegistry = fromProviders([new ValueCodecProvider(), new DocumentCodecProvider(), new BsonValueCodecProvider()])
    def readPreference = secondary()

    def 'should build the expected MapReduceWithInlineResultsOperation'() {
        given:
        def cursor = Stub(AsyncBatchCursor) {
            next(_) >> {
                it[0].onResult(null, null)
            }
        }
        def executor = new TestOperationExecutor([cursor, cursor]);
        def mapReduceIterable = new MapReduceIterableImpl(namespace, Document, Document, codecRegistry, readPreference, executor,
                                                          'map', 'reduce')

        when: 'default input should be as expected'
        mapReduceIterable.into([]) { result, t -> }

        def operation = executor.getReadOperation() as MapReduceWithInlineResultsOperation<Document>
        def readPreference = executor.getReadPreference()

        then:
        expect operation, isTheSameAs(new MapReduceWithInlineResultsOperation<Document>(namespace, new BsonJavaScript('map'),
                new BsonJavaScript('reduce'), new DocumentCodec()).verbose(true));
        readPreference == secondary()

        when: 'overriding initial options'
        mapReduceIterable.filter(new Document('filter', 1))
                .finalizeFunction('finalize')
                .limit(999)
                .maxTime(999, MILLISECONDS)
                .scope(new Document('scope', 1))
                .sort(new Document('sort', 1))
                .verbose(false)
                .into([]) { result, t -> }

        operation = executor.getReadOperation() as MapReduceWithInlineResultsOperation<Document>

        then: 'should use the overrides'
        expect operation, isTheSameAs(new MapReduceWithInlineResultsOperation<Document>(namespace, new BsonJavaScript('map'),
                new BsonJavaScript('reduce'), new DocumentCodec())
                .filter(new BsonDocument('filter', new BsonInt32(1)))
                .finalizeFunction(new BsonJavaScript('finalize'))
                .limit(999)
                .maxTime(999, MILLISECONDS)
                .scope(new BsonDocument('scope', new BsonInt32(1)))
                .sort(new BsonDocument('sort', new BsonInt32(1)))
                .verbose(false)
        )
    }

    def 'should build the expected MapReduceToCollectionOperation'() {
        given:
        def cursor = Stub(AsyncBatchCursor) {
            next(_) >> {
                it[0].onResult(null, null)
            }
        }
        def executor = new TestOperationExecutor([cursor, cursor, Stub(MapReduceStatistics)]);

        when: 'mapReduce to a collection'
        def collectionNamespace = new MongoNamespace('dbName', 'collName')
        def mapReduceIterable = new MapReduceIterableImpl(namespace, Document, Document, codecRegistry, readPreference, executor,
                                                          'map', 'reduce')
                .collectionName(collectionNamespace.getCollectionName())
                .databaseName(collectionNamespace.getDatabaseName())
                .filter(new Document('filter', 1))
                .finalizeFunction('finalize')
                .limit(999)
                .maxTime(999, MILLISECONDS)
                .scope(new Document('scope', 1))
                .sort(new Document('sort', 1))
                .verbose(false)
                .batchSize(99)
                .nonAtomic(true)
                .action(MapReduceAction.MERGE)
                .sharded(true)
                .jsMode(true)
        mapReduceIterable.into([]) { result, t -> }

        def operation = executor.getWriteOperation() as MapReduceToCollectionOperation
        def expectedOperation = new MapReduceToCollectionOperation(namespace, new BsonJavaScript('map'),
                new BsonJavaScript('reduce'), 'collName')
                .databaseName(collectionNamespace.getDatabaseName())
                .filter(new BsonDocument('filter', new BsonInt32(1)))
                .finalizeFunction(new BsonJavaScript('finalize'))
                .limit(999)
                .maxTime(999, MILLISECONDS)
                .scope(new BsonDocument('scope', new BsonInt32(1)))
                .sort(new BsonDocument('sort', new BsonInt32(1)))
                .verbose(false)
                .nonAtomic(true)
                .action(MapReduceAction.MERGE.getValue())
                .jsMode(true)
                .sharded(true)

        then: 'should use the overrides'
        expect operation, isTheSameAs(expectedOperation)

        when: 'the subsequent read should have the batchSize set'
        operation = executor.getReadOperation() as FindOperation<Document>

        then: 'should use the correct settings'
        operation.getNamespace() == collectionNamespace
        operation.getBatchSize() == 99

        when: 'toCollection should work as expected'
        def futureResultCallback = new FutureResultCallback()
        mapReduceIterable.toCollection(futureResultCallback)
        futureResultCallback.get()

        operation = executor.getWriteOperation() as MapReduceToCollectionOperation

        then:
        expect operation, isTheSameAs(expectedOperation)
    }

    def 'should handle exceptions correctly'() {
        given:
        def codecRegistry = fromProviders([new ValueCodecProvider(), new BsonValueCodecProvider()])
        def executor = new TestOperationExecutor([new MongoException('failure')])
        def mapReduceIterable = new MapReduceIterableImpl(namespace, Document, BsonDocument, codecRegistry, readPreference, executor,
                                                          'map', 'reduce')

        def futureResultCallback = new FutureResultCallback<List<BsonDocument>>()

        when: 'The operation fails with an exception'
        mapReduceIterable.into([], futureResultCallback)
        futureResultCallback.get()

        then: 'the future should handle the exception'
        thrown(MongoException)

        when: 'toCollection should throw IllegalArgumentException its inline'
        mapReduceIterable.toCollection(new FutureResultCallback())

        then:
        thrown(IllegalArgumentException)

        when: 'a codec is missing'
        futureResultCallback = new FutureResultCallback<List<BsonDocument>>()
        new MapReduceIterableImpl(namespace, Document, Document, codecRegistry, readPreference, executor,
                                  'map', 'reduce').into([], futureResultCallback)
        futureResultCallback.get()

        then:
        thrown(CodecConfigurationException)
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
        def mongoIterable = new MapReduceIterableImpl(namespace, Document, Document, codecRegistry, readPreference, executor,
                                                      'map', 'reduce')

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

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

package com.mongodb

import com.mongodb.operation.BatchCursor
import spock.lang.IgnoreIf
import spock.lang.Subject

import static com.mongodb.ClusterFixture.serverVersionAtLeast
import static java.util.Arrays.asList

class DBCursorFunctionalSpecification extends FunctionalSpecification {

    def cursorMap = [a: 1]

    @Subject
    private DBCursor dbCursor

    def setup() {
        collection.insert(new BasicDBObject('a', 1))
    }

    def 'should use provided decoder factory'() {
        given:
        DBDecoder decoder = Mock()
        DBDecoderFactory factory = Mock()
        factory.create() >> decoder

        when:
        dbCursor = collection.find()
        dbCursor.setDecoderFactory(factory)
        dbCursor.next()

        then:
        1 * decoder.decode(_ as byte[], collection)
    }

    @IgnoreIf({ serverVersionAtLeast(asList(3, 0, 0)) })
    def 'should use provided hints for queries'() {
        given:
        collection.createIndex(new BasicDBObject('a', 1))

        when:
        dbCursor = collection.find().hint(new BasicDBObject('a', 1))

        then:
        dbCursor.explain().get('cursor') == 'BtreeCursor a_1'

        when:
        dbCursor = collection.find().addSpecial('$hint', new BasicDBObject('a', 1))

        then:
        dbCursor.explain().get('cursor') == 'BtreeCursor a_1'
    }

    @IgnoreIf({ !serverVersionAtLeast(asList(3, 0, 0)) })
    def 'should use provided hints for queries mongod > 2.7'() {
        given:
        collection.createIndex(new BasicDBObject('a', 1))

        when:
        dbCursor = collection.find().hint(new BasicDBObject('a', 1))

        then:
        dbCursor.explain().queryPlanner.winningPlan.inputStage.keyPattern == cursorMap

        when:
        dbCursor = collection.find().addSpecial('$hint', new BasicDBObject('a', 1))

        then:
        dbCursor.explain().queryPlanner.winningPlan.inputStage.keyPattern == cursorMap
    }

    def 'should use provided hint for count'() {
        expect:
        collection.find().hint('a_1').count() == 1
        collection.find().hint(new BasicDBObject('a', 1)).count() == 1
    }

    @IgnoreIf({ serverVersionAtLeast(asList(3, 0, 0)) })
    def 'should use provided string hints for queries'() {
        given:
        collection.createIndex(new BasicDBObject('a', 1))

        when:
        dbCursor = collection.find().hint('a_1')

        then:
        dbCursor.explain().get('cursor') == 'BtreeCursor a_1'

        when:
        dbCursor = collection.find().addSpecial('$hint', 'a_1')

        then:
        dbCursor.explain().get('cursor') == 'BtreeCursor a_1'
    }


    @IgnoreIf({ !serverVersionAtLeast(asList(3, 0, 0)) })
    def 'should use provided string hints for queries mongodb > 2.7'() {
        given:
        collection.createIndex(new BasicDBObject('a', 1))

        when:
        dbCursor = collection.find().hint('a_1')

        then:
        dbCursor.explain().queryPlanner.winningPlan.inputStage.keyPattern == cursorMap

        when:
        dbCursor = collection.find().addSpecial('$hint', 'a_1')

        then:
        dbCursor.explain().queryPlanner.winningPlan.inputStage.keyPattern == cursorMap
    }

    def 'should use provided hints for count'() {
        when:
        collection.insert(new BasicDBObject('a', 2))

        then:
        collection.find().count() == 2

        when:
        collection.createIndex(new BasicDBObject('a', 1));

        then:
        collection.find(new BasicDBObject('a', 1)).hint('_id_').count() == 1
        collection.find().hint('_id_').count() == 2

        when:
        collection.createIndex(new BasicDBObject('x', 1), new BasicDBObject('sparse', true));

        then:
        collection.find(new BasicDBObject('a', 1)).hint('x_1').count() == serverVersionAtLeast(asList(2, 6, 0)) ? 0 : 1
        collection.find().hint('a_1').count() == 2
    }

    @IgnoreIf({ !serverVersionAtLeast(asList(2, 6, 0)) })
    def 'should throw with bad hint with mongod 2.6+'() {
        when:
        collection.find(new BasicDBObject('a', 1)).hint('BAD HINT').count()
        then:
        thrown(MongoException)
    }

    @IgnoreIf({ serverVersionAtLeast(asList(2, 6, 0)) })
    def 'should ignore bad hints with mongod < 2.6'() {
        when:
        collection.find(new BasicDBObject('a', 1)).hint('BAD HINT').count()
        then:
        notThrown(MongoException)
    }

    def 'should be able to use addSpecial with count'() {
        when:
        collection.insert(new BasicDBObject('a', 2));

        then:
        collection.find().count() == 2

        when:
        collection.createIndex(new BasicDBObject('a', 1));
        collection.createIndex(new BasicDBObject('x', 1), new BasicDBObject('sparse', true));

        then:
        collection.find(new BasicDBObject('a', 1)).addSpecial('$hint', '_id_').count() == 1

        when:
        def countWithHint = collection.find(new BasicDBObject('a', 1)).addSpecial('$hint', 'x_1').count()

        then:
        countWithHint == serverVersionAtLeast(asList(2, 6, 0)) ? 0 : 1
    }

    @IgnoreIf({ serverVersionAtLeast(asList(3, 0, 0)) })
    def 'should be able to use addSpecial with $explain'() {
        given:
        collection.createIndex(new BasicDBObject('a', 1))

        when:
        dbCursor = collection.find().hint(new BasicDBObject('a', 1))
        dbCursor.addSpecial('$explain', 1)

        then:
        dbCursor.next().get('cursor') == 'BtreeCursor a_1'
    }

    @IgnoreIf({ !serverVersionAtLeast(asList(3, 0, 0)) })
    def 'should be able to use addSpecial with $explain mongod > 2.7'() {
        given:
        collection.createIndex(new BasicDBObject('a', 1))

        when:
        dbCursor = collection.find().hint(new BasicDBObject('a', 1))
        dbCursor.addSpecial('$explain', 1)

        then:
        dbCursor.explain().queryPlanner.winningPlan.inputStage.keyPattern == cursorMap
    }


    def 'should return results in the order they are on disk when natural sort applied'() {
        given:
        collection.insert(new BasicDBObject('name', 'Chris'))
        collection.insert(new BasicDBObject('name', 'Adam'))
        collection.insert(new BasicDBObject('name', 'Bob'))

        when:
        dbCursor = collection.find(new BasicDBObject('name', new BasicDBObject('$exists', true)))
                .sort(new BasicDBObject('$natural', 1))

        then:
        dbCursor*.get('name') == ['Chris', 'Adam', 'Bob']

        when:
        dbCursor = collection.find(new BasicDBObject('name', new BasicDBObject('$exists', true)))
                .addSpecial('$natural', 1)

        then:
        dbCursor*.get('name') == ['Chris', 'Adam', 'Bob']
    }

    def 'should return results in the reverse order they are on disk when natural sort of minus one applied'() {
        given:
        collection.insert(new BasicDBObject('name', 'Chris'))
        collection.insert(new BasicDBObject('name', 'Adam'))
        collection.insert(new BasicDBObject('name', 'Bob'))

        when:
        dbCursor = collection.find(new BasicDBObject('name', new BasicDBObject('$exists', true)))
                .sort(new BasicDBObject('$natural', -1))

        then:
        dbCursor*.get('name') == ['Bob', 'Adam', 'Chris']

        when:
        dbCursor = collection.find(new BasicDBObject('name', new BasicDBObject('$exists', true)))
                .addSpecial('$natural', -1)

        then:
        dbCursor*.get('name') == ['Bob', 'Adam', 'Chris']
    }

    def 'should sort in reverse order'() {
        given:
        def range = 1..10
        for (i in range) {
            collection.insert(new BasicDBObject('x', i))
        }

        when:
        def cursor = collection.find()
                .sort(new BasicDBObject('x', -1))

        then:
        cursor.next().get('x') == 10
    }

    def 'should sort in order'() {
        given:
        def range = 80..89
        for (i in range) {
            def document = new BasicDBObject('x', i)
            collection.insert(document)
        }

        when:
        def cursor = collection.find(new BasicDBObject('x', new BasicDBObject('$exists', true)))
                .sort(new BasicDBObject('x', 1))

        then:
        cursor.next().get('x') == 80
    }

    def 'should sort on two fields'() {
        given:
        collection.insert(new BasicDBObject('_id', 1).append('name', 'Chris'))
        collection.insert(new BasicDBObject('_id', 2).append('name', 'Adam'))
        collection.insert(new BasicDBObject('_id', 3).append('name', 'Bob'))
        collection.insert(new BasicDBObject('_id', 5).append('name', 'Adam'))
        collection.insert(new BasicDBObject('_id', 4).append('name', 'Adam'))

        when:
        dbCursor = collection.find(new BasicDBObject('name', new BasicDBObject('$exists', true)))
                .sort(new BasicDBObject('name', 1).append('_id', 1))

        then:
        dbCursor.collect { it -> [it.get('name'), it.get('_id')] } == [['Adam', 2], ['Adam', 4], ['Adam', 5], ['Bob', 3], ['Chris', 1]]

        when:
        dbCursor = collection.find(new BasicDBObject('name', new BasicDBObject('$exists', true)))
                .addSpecial('$orderby', new BasicDBObject('name', 1).append('_id', 1))

        then:
        dbCursor.collect { it -> [it.get('name'), it.get('_id')] } == [['Adam', 2], ['Adam', 4], ['Adam', 5], ['Bob', 3], ['Chris', 1]]
    }

    // Spock bug as MongoCursor does implement closeable
    @SuppressWarnings('CloseWithoutCloseable')
    def 'DBCursor options should set the correct read preference'() {
        given:
        def tailableCursor =
                new BatchCursor<DBObject>() {
                    @Override
                    List<DBObject> tryNext() { null }

                    @Override
                    void close() { }

                    @Override
                    boolean hasNext() { true }

                    @Override
                    List<DBObject> next() { null }

                    @Override
                    void setBatchSize(final int batchSize) { }

                    @Override
                    int getBatchSize() { 0 }

                    @Override
                    void remove() { }

                    @Override
                    ServerCursor getServerCursor() { null }

                    @Override
                    ServerAddress getServerAddress() { null }
                }

        def executor = new TestOperationExecutor([tailableCursor, tailableCursor, tailableCursor, tailableCursor])
        def collection = new DBCollection('collectionName', database, executor)

        when:
        collection.find().hasNext()

        then:
        executor.getReadPreference() == ReadPreference.primary()

        when:
        collection.find().addOption(Bytes.QUERYOPTION_SLAVEOK).hasNext()

        then:
        executor.getReadPreference() == ReadPreference.secondaryPreferred()

        when:
        collection.find().addOption(Bytes.QUERYOPTION_TAILABLE).tryNext()

        then:
        executor.getReadPreference() == ReadPreference.primary()

        when:
        collection.find().addOption(Bytes.QUERYOPTION_TAILABLE).addOption(Bytes.QUERYOPTION_SLAVEOK).tryNext()

        then:
        executor.getReadPreference() == ReadPreference.secondaryPreferred()
    }
}

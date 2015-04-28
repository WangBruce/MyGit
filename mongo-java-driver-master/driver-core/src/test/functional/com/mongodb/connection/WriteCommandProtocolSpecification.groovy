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


package com.mongodb.connection

import category.Slow
import com.mongodb.OperationFunctionalSpecification
import com.mongodb.MongoBulkWriteException
import com.mongodb.bulk.BulkWriteUpsert
import com.mongodb.bulk.InsertRequest
import com.mongodb.bulk.UpdateRequest
import com.mongodb.bulk.WriteRequest
import com.mongodb.connection.netty.NettyStreamFactory
import org.bson.BsonBinary
import org.bson.BsonDocument
import org.bson.BsonInt32
import org.bson.codecs.BsonDocumentCodec
import org.junit.experimental.categories.Category
import spock.lang.IgnoreIf

import static com.mongodb.ClusterFixture.getCredentialList
import static com.mongodb.ClusterFixture.getPrimary
import static com.mongodb.ClusterFixture.getSslSettings
import static com.mongodb.ClusterFixture.serverVersionAtLeast
import static com.mongodb.WriteConcern.ACKNOWLEDGED

@IgnoreIf({ !serverVersionAtLeast([2, 6, 0]) })
class WriteCommandProtocolSpecification extends OperationFunctionalSpecification {

    InternalStreamConnection connection;

    def setup() {
        connection = new InternalStreamConnectionFactory(new NettyStreamFactory(SocketSettings.builder().build(), getSslSettings()),
                                                         getCredentialList(), new NoOpConnectionListener())
                .create(new ServerId(new ClusterId(), getPrimary()))
        connection.open();
    }

    def cleanup() {
        connection?.close()
    }

    def 'should insert a document'() {
        given:
        def document = new BsonDocument('_id', new BsonInt32(1))

        def insertRequest = [new InsertRequest(document)]
        def protocol = new InsertCommandProtocol(getNamespace(), true, ACKNOWLEDGED, insertRequest)
        when:
        def result = protocol.execute(connection)

        then:
        result.insertedCount == 1
        result.upserts == []
        collectionHelper.find(document, new BsonDocumentCodec()).first() == document
    }

    def 'should insert documents'() {
        def requests = [new InsertRequest(new BsonDocument('_id', new BsonInt32(1))),
                        new InsertRequest(new BsonDocument('_id', new BsonInt32(2)))]
        given:
        def protocol = new InsertCommandProtocol(getNamespace(), true, ACKNOWLEDGED, requests
        )
        when:
        protocol.execute(connection)

        then:
        collectionHelper.count() == 2
    }

    def 'should throw exception'() {
        given:
        def protocol = new InsertCommandProtocol(getNamespace(), false, ACKNOWLEDGED,
                                                 [new InsertRequest(new BsonDocument('_id', new BsonInt32(1))),
                                                  new InsertRequest(new BsonDocument('_id', new BsonInt32(2)))]
        )
        protocol.execute(connection)

        when:
        protocol.execute(connection)  // now do it again

        then:
        def e = thrown(MongoBulkWriteException)
        e.serverAddress == connection.getServerAddress()
        e.writeErrors.size() == 2
        e.writeResult != null
        e.writeResult.insertedCount == 0;
        e.writeResult.upserts == []
        with(e.writeErrors[0]) {
            index == 0
            code == 11000
            message != null
        }
        with(e.writeErrors[1]) {
            index == 1
            code == 11000
            message != null
        }
    }

    @Category(Slow)
    def 'should split a large batch'() {
        given:
        def hugeBinary = new BsonBinary(new byte[1024 * 1024 * 16 - 100]);

        def documents = [
                new BsonDocument('_id', new BsonInt32(1)).append('bytes', hugeBinary),
                new BsonDocument('_id', new BsonInt32(2)).append('bytes', hugeBinary),
                new BsonDocument('_id', new BsonInt32(3)).append('bytes', hugeBinary),
                new BsonDocument('_id', new BsonInt32(4)).append('bytes', hugeBinary)
        ]

        List<InsertRequest> insertList = new ArrayList<InsertRequest>(documents.size());
        for (def cur : documents) {
            insertList.add(new InsertRequest(cur));
        }
        def protocol = new InsertCommandProtocol(getNamespace(), true, ACKNOWLEDGED, insertList)

        when:
        def result = protocol.execute(connection)

        then:
        result.insertedCount == 4
        documents.size() == collectionHelper.count()
    }

    @Category(Slow)
    def 'should have correct list of processed and unprocessed requests after error on split'() {
        given:
        def hugeBinary = new BsonBinary(new byte[1024 * 1024 * 16 - 100]);

        def documents = [
                new BsonDocument('_id', new BsonInt32(1)).append('bytes', hugeBinary),
                new BsonDocument('_id', new BsonInt32(2)).append('bytes', hugeBinary),
                new BsonDocument('_id', new BsonInt32(3)).append('bytes', hugeBinary),
                new BsonDocument('_id', new BsonInt32(4)).append('bytes', hugeBinary)
        ]

        List<InsertRequest> insertList = new ArrayList<InsertRequest>(documents.size());
        for (def cur : documents) {
            insertList.add(new InsertRequest(cur));
        }

        // Force a duplicate key error in the second insert request
        new InsertCommandProtocol(getNamespace(), true, ACKNOWLEDGED, [new InsertRequest(new BsonDocument('_id', new BsonInt32(2)))])
                .execute(connection)

        def protocol = new InsertCommandProtocol(getNamespace(), true, ACKNOWLEDGED, insertList)

        when:
        protocol.execute(connection)

        then:
        def exception = thrown(MongoBulkWriteException)
        exception.writeResult.insertedCount == 1
    }

    @Category(Slow)
    def 'should map indices in exception when split is required'() {
        given:
        def hugeBinary = new BsonBinary(new byte[1024 * 1024 * 16 - 100]);

        def documents = [
                new BsonDocument('_id', new BsonInt32(1)),
                new BsonDocument('_id', new BsonInt32(2)),
                new BsonDocument('_id', new BsonInt32(3)),
                new BsonDocument('_id', new BsonInt32(4))
        ]

        List<InsertRequest> insertList = new ArrayList<InsertRequest>(documents.size());
        for (def cur : documents) {
            insertList.add(new InsertRequest(cur));
        }
        new InsertCommandProtocol(getNamespace(), false, ACKNOWLEDGED, insertList).execute(connection)

        // add a large byte array to each document to force a split after each
        for (def document : documents) {
            document.append('bytes', hugeBinary);
        }
        documents[1].put('_id', new BsonInt32(5))  // Make the second document a new one

        def protocol = new InsertCommandProtocol(getNamespace(), false, ACKNOWLEDGED, insertList)

        when:
        protocol.execute(connection)

        then:
        def e = thrown(MongoBulkWriteException)
        e.serverAddress == connection.getServerAddress()
        e.writeErrors.size() == 3
        e.writeErrors[0].index == 0
        e.writeErrors[1].index == 2
        e.writeErrors[2].index == 3
    }

    def 'should upsert items'() {
        given:
        def protocol = new UpdateCommandProtocol(getNamespace(), true, ACKNOWLEDGED,
                                                 [new UpdateRequest(new BsonDocument('_id', new BsonInt32(1)),
                                                                    new BsonDocument('$set', new BsonDocument('x', new BsonInt32(1))),
                                                                    WriteRequest.Type.UPDATE)
                                                          .upsert(true),
                                                  new UpdateRequest(new BsonDocument('_id', new BsonInt32(2)),
                                                                    new BsonDocument('$set', new BsonDocument('x', new BsonInt32(2))),
                                                                    WriteRequest.Type.UPDATE)
                                                          .upsert(true)]
        );

        when:
        def result = protocol.execute(connection);

        then:
        result.matchedCount == 0;
        result.upserts == [new BulkWriteUpsert(0, new BsonInt32(1)), new BulkWriteUpsert(1, new BsonInt32(2))]
    }
}

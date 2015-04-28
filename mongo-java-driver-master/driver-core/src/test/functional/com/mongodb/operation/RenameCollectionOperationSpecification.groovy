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
import com.mongodb.MongoNamespace
import com.mongodb.MongoServerException
import com.mongodb.OperationFunctionalSpecification
import org.bson.Document
import org.bson.codecs.DocumentCodec
import org.junit.experimental.categories.Category
import spock.lang.IgnoreIf

import static com.mongodb.ClusterFixture.executeAsync
import static com.mongodb.ClusterFixture.getBinding
import static com.mongodb.ClusterFixture.isSharded

@IgnoreIf( { isSharded() } )  // these tests don't reliably pass against mongos
class RenameCollectionOperationSpecification extends OperationFunctionalSpecification {

    def cleanup() {
        new DropCollectionOperation(new MongoNamespace(getDatabaseName(), 'newCollection')).execute(getBinding())
    }

    def 'should return rename a collection'() {
        given:
        getCollectionHelper().insertDocuments(new DocumentCodec(), new Document('documentThat', 'forces creation of the Collection'))
        assert collectionNameExists(getCollectionName())

        when:
        new RenameCollectionOperation(getNamespace(), new MongoNamespace(getDatabaseName(), 'newCollection')).execute(getBinding())

        then:
        !collectionNameExists(getCollectionName())
        collectionNameExists('newCollection')
    }

    @Category(Async)
    def 'should return rename a collection asynchronously'() {
        given:
        getCollectionHelper().insertDocuments(new DocumentCodec(), new Document('documentThat', 'forces creation of the Collection'))
        assert collectionNameExists(getCollectionName())

        when:
        executeAsync(new RenameCollectionOperation(getNamespace(), new MongoNamespace(getDatabaseName(), 'newCollection')))

        then:
        !collectionNameExists(getCollectionName())
        collectionNameExists('newCollection')
    }

    def 'should throw if not drop and collection exists'() {
        given:
        getCollectionHelper().insertDocuments(new DocumentCodec(), new Document('documentThat', 'forces creation of the Collection'))
        assert collectionNameExists(getCollectionName())

        when:
        new RenameCollectionOperation(getNamespace(), getNamespace()).execute(getBinding())

        then:
        thrown(MongoServerException)
        collectionNameExists(getCollectionName())
    }

    @Category(Async)
    def 'should throw if not drop and collection exists asynchronously'() {
        given:
        getCollectionHelper().insertDocuments(new DocumentCodec(), new Document('documentThat', 'forces creation of the Collection'))
        assert collectionNameExists(getCollectionName())

        when:
        executeAsync(new RenameCollectionOperation(getNamespace(), getNamespace()))

        then:
        thrown(MongoServerException)
        collectionNameExists(getCollectionName())
    }

    def collectionNameExists(String collectionName) {
        def cursor = new ListCollectionsOperation(databaseName, new DocumentCodec()).execute(getBinding())
        if (!cursor.hasNext()) {
            return false
        }
        cursor.next()*.get('name').contains(collectionName)
    }

}

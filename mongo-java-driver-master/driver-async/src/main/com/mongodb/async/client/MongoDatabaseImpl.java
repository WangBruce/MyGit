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

package com.mongodb.async.client;

import com.mongodb.Function;
import com.mongodb.MongoNamespace;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.async.SingleResultCallback;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.operation.AsyncOperationExecutor;
import com.mongodb.operation.CommandReadOperation;
import com.mongodb.operation.CommandWriteOperation;
import com.mongodb.operation.CreateCollectionOperation;
import com.mongodb.operation.DropDatabaseOperation;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;

import static com.mongodb.assertions.Assertions.notNull;
import static com.mongodb.async.client.MongoClientImpl.getDefaultCodecRegistry;

class MongoDatabaseImpl implements MongoDatabase {
    private final String name;
    private final ReadPreference readPreference;
    private final CodecRegistry codecRegistry;
    private final WriteConcern writeConcern;
    private final AsyncOperationExecutor executor;

    MongoDatabaseImpl(final String name, final CodecRegistry codecRegistry, final ReadPreference readPreference,
                      final WriteConcern writeConcern, final AsyncOperationExecutor executor) {
        this.name = notNull("name", name);
        this.codecRegistry = notNull("codecRegistry", codecRegistry);
        this.readPreference = notNull("readPreference", readPreference);
        this.writeConcern = notNull("writeConcern", writeConcern);
        this.executor = notNull("executor", executor);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public CodecRegistry getCodecRegistry() {
        return codecRegistry;
    }

    @Override
    public ReadPreference getReadPreference() {
        return readPreference;
    }

    @Override
    public WriteConcern getWriteConcern() {
        return writeConcern;
    }

    @Override
    public MongoDatabase withCodecRegistry(final CodecRegistry codecRegistry) {
        return new MongoDatabaseImpl(name, codecRegistry, readPreference, writeConcern, executor);
    }

    @Override
    public MongoDatabase withReadPreference(final ReadPreference readPreference) {
        return new MongoDatabaseImpl(name, codecRegistry, readPreference, writeConcern, executor);
    }

    @Override
    public MongoDatabase withWriteConcern(final WriteConcern writeConcern) {
        return new MongoDatabaseImpl(name, codecRegistry, readPreference, writeConcern, executor);
    }

    @Override
    public MongoIterable<String> listCollectionNames() {
        return new ListCollectionsIterableImpl<BsonDocument>(name, BsonDocument.class, getDefaultCodecRegistry(), ReadPreference.primary(),
                executor).map(new Function<BsonDocument, String>() {
            @Override
            public String apply(final BsonDocument result) {
                return result.getString("name").getValue();
            }
        });
    }

    @Override
    public ListCollectionsIterable<Document> listCollections() {
        return listCollections(Document.class);
    }

    @Override
    public <TResult> ListCollectionsIterable<TResult> listCollections(final Class<TResult> resultClass) {
        return new ListCollectionsIterableImpl<TResult>(name, resultClass, codecRegistry, ReadPreference.primary(), executor);
    }

    @Override
    public MongoCollection<Document> getCollection(final String collectionName) {
        return getCollection(collectionName, Document.class);
    }

    @Override
    public <TDocument> MongoCollection<TDocument> getCollection(final String collectionName, final Class<TDocument> documentClass) {
        return new MongoCollectionImpl<TDocument>(new MongoNamespace(name, collectionName), documentClass, codecRegistry, readPreference,
                                                  writeConcern, executor);
    }

    @Override
    public void runCommand(final Bson command, final SingleResultCallback<Document> callback) {
        runCommand(command, Document.class, callback);
    }

    @Override
    public void runCommand(final Bson command, final ReadPreference readPreference, final SingleResultCallback<Document> callback) {
        runCommand(command, readPreference, Document.class, callback);
    }

    @Override
    public <TResult> void runCommand(final Bson command, final Class<TResult> resultClass,
                                     final SingleResultCallback<TResult> callback) {
        notNull("command", command);
        executor.execute(new CommandWriteOperation<TResult>(getName(), toBsonDocument(command), codecRegistry.get(resultClass)), callback);
    }

    @Override
    public <TResult> void runCommand(final Bson command, final ReadPreference readPreference, final Class<TResult> resultClass,
                                     final SingleResultCallback<TResult> callback) {
        notNull("command", command);
        notNull("readPreference", readPreference);
        executor.execute(new CommandReadOperation<TResult>(getName(), toBsonDocument(command), codecRegistry.get(resultClass)),
                         readPreference, callback);
    }

    @Override
    public void drop(final SingleResultCallback<Void> callback) {
        executor.execute(new DropDatabaseOperation(name), callback);
    }

    @Override
    public void createCollection(final String collectionName, final SingleResultCallback<Void> callback) {
        createCollection(collectionName, new CreateCollectionOptions(), callback);
    }

    @Override
    public void createCollection(final String collectionName, final CreateCollectionOptions createCollectionOptions,
                                 final SingleResultCallback<Void> callback) {
        executor.execute(new CreateCollectionOperation(name, collectionName)
                         .capped(createCollectionOptions.isCapped())
                         .sizeInBytes(createCollectionOptions.getSizeInBytes())
                         .autoIndex(createCollectionOptions.isAutoIndex())
                         .maxDocuments(createCollectionOptions.getMaxDocuments())
                         .usePowerOf2Sizes(createCollectionOptions.isUsePowerOf2Sizes())
                         .storageEngineOptions(toBsonDocument(createCollectionOptions.getStorageEngineOptions())), callback);
    }

    private BsonDocument toBsonDocument(final Bson document) {
        return document == null ? null : document.toBsonDocument(BsonDocument.class, codecRegistry);
    }
}

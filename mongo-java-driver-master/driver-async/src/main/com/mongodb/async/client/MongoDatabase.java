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

import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.annotations.ThreadSafe;
import com.mongodb.async.SingleResultCallback;
import com.mongodb.client.model.CreateCollectionOptions;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;

/**
 * The MongoDatabase interface.
 * <p>
 * Note: Additions to this interface will not be considered to break binary compatibility.</p>
 *
 * @since 3.0
 */
@ThreadSafe
public interface MongoDatabase {

    /**
     * Gets the name of the database.
     *
     * @return the database name
     */
    String getName();

    /**
     * Get the codec registry for the MongoDatabase.
     *
     * @return the {@link org.bson.codecs.configuration.CodecRegistry}
     */
    CodecRegistry getCodecRegistry();

    /**
     * Get the read preference for the MongoDatabase.
     *
     * @return the {@link com.mongodb.ReadPreference}
     */
    ReadPreference getReadPreference();

    /**
     * Get the write concern for the MongoDatabase.
     *
     * @return the {@link com.mongodb.WriteConcern}
     */
    WriteConcern getWriteConcern();

    /**
     * Create a new MongoDatabase instance with a different codec registry.
     *
     * @param codecRegistry the new {@link org.bson.codecs.configuration.CodecRegistry} for the collection
     * @return a new MongoDatabase instance with the different codec registry
     */
    MongoDatabase withCodecRegistry(CodecRegistry codecRegistry);

    /**
     * Create a new MongoDatabase instance with a different read preference.
     *
     * @param readPreference the new {@link com.mongodb.ReadPreference} for the collection
     * @return a new MongoDatabase instance with the different readPreference
     */
    MongoDatabase withReadPreference(ReadPreference readPreference);

    /**
     * Create a new MongoDatabase instance with a different write concern.
     *
     * @param writeConcern the new {@link com.mongodb.WriteConcern} for the collection
     * @return a new MongoDatabase instance with the different writeConcern
     */
    MongoDatabase withWriteConcern(WriteConcern writeConcern);

    /**
     * Gets a collection.
     *
     * @param collectionName the name of the collection to return
     * @return the collection
     */
    MongoCollection<Document> getCollection(String collectionName);

    /**
     * Gets a collection, with a specific default document class.
     *
     * @param collectionName the name of the collection to return
     * @param documentClass the default class to cast any documents returned from the database into.
     * @param <TDocument>   the type of the class to use instead of {@code Document}.
     * @return the collection
     */
    <TDocument> MongoCollection<TDocument> getCollection(String collectionName, Class<TDocument> documentClass);

    /**
     * Executes command in the context of the current database.
     *
     * @param command  the command to be run
     * @param callback the callback that is passed the command result
     */
    void runCommand(Bson command, SingleResultCallback<Document> callback);

    /**
     * Executes command in the context of the current database.
     *
     * @param command        the command to be run
     * @param readPreference the {@link com.mongodb.ReadPreference} to be used when executing the command
     * @param callback       the callback that is passed the command result
     */
    void runCommand(Bson command, ReadPreference readPreference, SingleResultCallback<Document> callback);

    /**
     * Executes command in the context of the current database.
     *
     * @param command     the command to be run
     * @param resultClass the default class to cast any documents returned from the database into.
     * @param <TResult>   the type of the class to use instead of {@code Document}.
     * @param callback    the callback that is passed the command result
     */
    <TResult> void runCommand(Bson command, Class<TResult> resultClass, SingleResultCallback<TResult> callback);

    /**
     * Executes command in the context of the current database.
     *
     * @param command        the command to be run
     * @param readPreference the {@link com.mongodb.ReadPreference} to be used when executing the command
     * @param resultClass    the default class to cast any documents returned from the database into.
     * @param <TResult>      the type of the class to use instead of {@code Document}.
     * @param callback       the callback that is passed the command result
     */
    <TResult> void runCommand(Bson command, ReadPreference readPreference, Class<TResult> resultClass,
                              SingleResultCallback<TResult> callback);

    /**
     * Drops this database.
     *
     * @param callback the callback that is completed once the database has been dropped
     * @mongodb.driver.manual reference/commands/dropDatabase/#dbcmd.dropDatabase Drop database
     */
    void drop(SingleResultCallback<Void> callback);

    /**
     * Gets the names of all the collections in this database.
     *
     * @return an iterable containing all the names of all the collections in this database
     */
    MongoIterable<String> listCollectionNames();

    /**
     * Finds all the collections in this database.
     *
     * @return the list collections iterable interface
     * @mongodb.driver.manual reference/command/listCollections listCollections
     */
    ListCollectionsIterable<Document> listCollections();

    /**
     * Finds all the collections in this database.
     *
     * @param resultClass the class to decode each document into
     * @param <TResult>   the target document type of the iterable.
     * @return the list collections iterable interface
     * @mongodb.driver.manual reference/command/listCollections listCollections
     */
    <TResult> ListCollectionsIterable<TResult> listCollections(Class<TResult> resultClass);

    /**
     * Create a new collection with the given name.
     *
     * @param collectionName the name for the new collection to create
     * @param callback       the callback that is completed once the collection has been created
     * @mongodb.driver.manual reference/commands/create Create Command
     */
    void createCollection(String collectionName, SingleResultCallback<Void> callback);

    /**
     * Create a new collection with the selected options
     *
     * @param collectionName the name for the new collection to create
     * @param options        various options for creating the collection
     * @param callback       the callback that is completed once the collection has been created
     * @mongodb.driver.manual reference/commands/create Create Command
     */
    void createCollection(String collectionName, CreateCollectionOptions options, SingleResultCallback<Void> callback);

}

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

import com.mongodb.ConnectionString;
import com.mongodb.MongoCommandException;
import com.mongodb.MongoNamespace;
import com.mongodb.async.FutureResultCallback;
import com.mongodb.connection.ClusterSettings;
import com.mongodb.connection.ConnectionPoolSettings;
import com.mongodb.connection.ServerSettings;
import com.mongodb.connection.SocketSettings;
import com.mongodb.connection.SslSettings;
import org.bson.Document;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static com.mongodb.connection.ClusterType.SHARDED;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Helper class for asynchronous tests.
 */
public final class Fixture {
    public static final String DEFAULT_URI = "mongodb://localhost:27017";
    public static final String MONGODB_URI_SYSTEM_PROPERTY_NAME = "org.mongodb.test.uri";
    private static final String DEFAULT_DATABASE_NAME = "JavaDriverTest";

    private static ConnectionString connectionString;
    private static MongoClientImpl mongoClient;


    private Fixture() {
    }

    public static synchronized MongoClient getMongoClient() {
        if (mongoClient == null) {
            SslSettings.Builder sslSettingsBuilder = SslSettings.builder().applyConnectionString(getConnectionString());
            if (System.getProperty("java.version").startsWith("1.6.")) {
                sslSettingsBuilder.invalidHostNameAllowed(true);
            }
            ClusterSettings clusterSettings = ClusterSettings.builder()
                                                             .applyConnectionString(getConnectionString())
                                                             .build();
            ConnectionPoolSettings connectionPoolSettings = ConnectionPoolSettings.builder()
                                                                                  .applyConnectionString(getConnectionString())
                                                                                  .build();
            SocketSettings socketSettings = SocketSettings.builder()
                                                          .applyConnectionString(getConnectionString())
                                                          .build();
            MongoClientSettings settings = MongoClientSettings.builder()
                                                           .clusterSettings(clusterSettings)
                                                           .connectionPoolSettings(connectionPoolSettings)
                                                           .serverSettings(ServerSettings.builder().build())
                                                           .credentialList(getConnectionString().getCredentialList())
                                                           .sslSettings(sslSettingsBuilder.build())
                                                           .socketSettings(socketSettings)
                                                           .build();
            mongoClient = (MongoClientImpl) MongoClients.create(settings);
            Runtime.getRuntime().addShutdownHook(new ShutdownHook());
        }
        return mongoClient;
    }

    public static synchronized ConnectionString getConnectionString() {
        if (connectionString == null) {
            String mongoURIProperty = System.getProperty(MONGODB_URI_SYSTEM_PROPERTY_NAME);
            String mongoURIString = mongoURIProperty == null || mongoURIProperty.isEmpty()
                                    ? DEFAULT_URI : mongoURIProperty;
            connectionString = new ConnectionString(mongoURIString);
        }
        return connectionString;
    }

    public static String getDefaultDatabaseName() {
        return DEFAULT_DATABASE_NAME;
    }

    public static MongoDatabase getDefaultDatabase() {
        return getMongoClient().getDatabase(getDefaultDatabaseName());
    }

    public static MongoCollection<Document> initializeCollection(final MongoNamespace namespace)
    throws InterruptedException, ExecutionException, TimeoutException {
        MongoDatabase database = getMongoClient().getDatabase(namespace.getDatabaseName());
        try {
            FutureResultCallback<Document> futureResultCallback = new FutureResultCallback<Document>();
            database.runCommand(new Document("drop", namespace.getCollectionName()), futureResultCallback);
            futureResultCallback.get(60, SECONDS);
        } catch (MongoCommandException e) {
            if (!e.getErrorMessage().startsWith("ns not found")) {
                throw e;
            }
        }
        return database.getCollection(namespace.getCollectionName());
    }

    public static boolean isSharded() {
        getMongoClient();
        return mongoClient.getCluster().getDescription().getType() == SHARDED;
    }

    public static void dropDatabase(final String name) throws InterruptedException, ExecutionException, TimeoutException {
        if (name == null) {
            return;
        }
        try {
            FutureResultCallback<Document> futureResultCallback = new FutureResultCallback<Document>();
            getMongoClient().getDatabase(name)
                            .runCommand(new Document("dropDatabase", 1), futureResultCallback);
            futureResultCallback.get(60, SECONDS);
        } catch (MongoCommandException e) {
            if (!e.getErrorMessage().startsWith("ns not found")) {
                throw e;
            }
        }
    }

    public static void drop(final MongoNamespace namespace) throws ExecutionException, InterruptedException {
        try {
            FutureResultCallback<Document> futureResultCallback = new FutureResultCallback<Document>();
            getMongoClient().getDatabase(namespace.getDatabaseName())
                            .runCommand(new Document("drop", namespace.getCollectionName()), futureResultCallback);
            futureResultCallback.get();
        } catch (MongoCommandException e) {
            if (!e.getErrorMessage().contains("ns not found")) {
                throw e;
            }
        }
    }

    static class ShutdownHook extends Thread {
        @Override
        public void run() {
            try {
                dropDatabase(getDefaultDatabaseName());
            } catch (Exception e) {
                // ignore
            }
            mongoClient.close();
            mongoClient = null;
        }
    }
}

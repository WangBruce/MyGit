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

package com.mongodb;

import com.mongodb.async.AsyncBatchCursor;
import com.mongodb.async.FutureResultCallback;
import com.mongodb.async.SingleResultCallback;
import com.mongodb.binding.AsyncClusterBinding;
import com.mongodb.binding.AsyncConnectionSource;
import com.mongodb.binding.AsyncReadWriteBinding;
import com.mongodb.binding.AsyncSingleConnectionBinding;
import com.mongodb.binding.ClusterBinding;
import com.mongodb.binding.ReadWriteBinding;
import com.mongodb.binding.SingleConnectionBinding;
import com.mongodb.connection.AsyncConnection;
import com.mongodb.connection.AsynchronousSocketChannelStreamFactory;
import com.mongodb.connection.Cluster;
import com.mongodb.connection.ClusterDescription;
import com.mongodb.connection.ClusterSettings;
import com.mongodb.connection.ClusterType;
import com.mongodb.connection.ConnectionPoolSettings;
import com.mongodb.connection.DefaultClusterFactory;
import com.mongodb.connection.ServerDescription;
import com.mongodb.connection.ServerSettings;
import com.mongodb.connection.ServerVersion;
import com.mongodb.connection.SocketSettings;
import com.mongodb.connection.SocketStreamFactory;
import com.mongodb.connection.SslSettings;
import com.mongodb.connection.StreamFactory;
import com.mongodb.connection.netty.NettyStreamFactory;
import com.mongodb.management.JMXConnectionPoolListener;
import com.mongodb.operation.AsyncReadOperation;
import com.mongodb.operation.AsyncWriteOperation;
import com.mongodb.operation.CommandWriteOperation;
import com.mongodb.operation.DropDatabaseOperation;
import org.bson.BsonDocument;
import org.bson.BsonDocumentWrapper;
import org.bson.Document;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.DocumentCodec;

import java.util.ArrayList;
import java.util.List;

import static com.mongodb.connection.ClusterConnectionMode.MULTIPLE;
import static com.mongodb.connection.ClusterType.REPLICA_SET;
import static com.mongodb.connection.ClusterType.SHARDED;
import static java.lang.Thread.sleep;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assume.assumeThat;

/**
 * Helper class for the acceptance tests.  Used primarily by DatabaseTestCase and FunctionalSpecification.  This fixture allows Test
 * super-classes to share functionality whilst minimising duplication.
 */
public final class ClusterFixture {
    public static final String DEFAULT_URI = "mongodb://localhost:27017";
    public static final String MONGODB_URI_SYSTEM_PROPERTY_NAME = "org.mongodb.test.uri";
    private static final String DEFAULT_DATABASE_NAME = "JavaDriverTest";
    public static final long TIMEOUT = 60L;

    private static ConnectionString connectionString;
    private static Cluster cluster;
    private static Cluster asyncCluster;

    static {
        String mongoURIProperty = System.getProperty(MONGODB_URI_SYSTEM_PROPERTY_NAME);
        String mongoURIString = mongoURIProperty == null || mongoURIProperty.isEmpty()
                                ? DEFAULT_URI : mongoURIProperty;
        connectionString = new ConnectionString(mongoURIString);
        Runtime.getRuntime().addShutdownHook(new ShutdownHook());
    }

    private ClusterFixture() {
    }

    public static String getDefaultDatabaseName() {
        return DEFAULT_DATABASE_NAME;
    }

    public static boolean clusterIsType(final ClusterType clusterType) {
        return getCluster().getDescription().getType() == clusterType;
    }

    public static ServerVersion getServerVersion() {
        return getCluster().getDescription().getAny().get(0).getVersion();
    }

    public static boolean serverVersionAtLeast(final List<Integer> versionArray) {
        ClusterDescription clusterDescription = getCluster().getDescription();
        int retries = 0;
        while (clusterDescription.getAny().isEmpty() && retries <= 3) {
            try {
                Thread.sleep(1000);
                retries++;
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted", e);
            }
            clusterDescription = getCluster().getDescription();
        }
        if (clusterDescription.getAny().isEmpty()) {
            throw new RuntimeException("There are no servers available in " + clusterDescription);
        }
        return clusterDescription.getAny().get(0).getVersion().compareTo(new ServerVersion(versionArray)) >= 0;
    }

    static class ShutdownHook extends Thread {
        @Override
        public void run() {
            if (cluster != null) {
                new DropDatabaseOperation(getDefaultDatabaseName()).execute(getBinding());
                cluster.close();
            }
        }
    }

    public static synchronized ConnectionString getConnectionString() {
        return connectionString;
    }

    public static ReadWriteBinding getBinding() {
        return getBinding(getCluster());
    }

    public static ReadWriteBinding getBinding(final Cluster cluster) {
        return new ClusterBinding(cluster, ReadPreference.primary());
    }

    public static SingleConnectionBinding getSingleConnectionBinding() {
        return new SingleConnectionBinding(getCluster(), ReadPreference.primary());
    }

    public static AsyncSingleConnectionBinding getAsyncSingleConnectionBinding() {
        return getAsyncSingleConnectionBinding(getAsyncCluster());
    }

    public static AsyncSingleConnectionBinding getAsyncSingleConnectionBinding(final Cluster cluster) {
        return new AsyncSingleConnectionBinding(cluster, 20, SECONDS);
    }

    public static AsyncReadWriteBinding getAsyncBinding() {
        return getAsyncBinding(getAsyncCluster());
    }

    public static AsyncReadWriteBinding getAsyncBinding(final Cluster cluster) {
        return new AsyncClusterBinding(cluster, ReadPreference.primary());
    }

    public static Cluster getCluster() {
        if (cluster == null) {
            cluster = createCluster(new SocketStreamFactory(getSocketSettings(), getSslSettings()));
        }
        return cluster;
    }

    public static Cluster getAsyncCluster() {
        if (asyncCluster == null) {
            asyncCluster = createCluster(getAsyncStreamFactory());
        }
        return asyncCluster;
    }

    public static Cluster createCluster(final StreamFactory streamFactory) {
        return new DefaultClusterFactory().create(ClusterSettings.builder().applyConnectionString(getConnectionString()).build(),
                                                  ServerSettings.builder().build(),
                                                  ConnectionPoolSettings.builder().applyConnectionString(getConnectionString()).build(),
                                                  streamFactory,
                                                  new SocketStreamFactory(SocketSettings.builder().build(), getSslSettings()),
                                                  getConnectionString().getCredentialList(), null, new JMXConnectionPoolListener(), null);

    }

    public static StreamFactory getAsyncStreamFactory() {
        String streamType = System.getProperty("org.mongodb.async.type", "nio2");

        if (streamType.equals("netty") || getSslSettings().isEnabled()) {
            return new NettyStreamFactory(getSocketSettings(), getSslSettings());
        } else if (streamType.equals("nio2")) {
            return new AsynchronousSocketChannelStreamFactory(getSocketSettings(), getSslSettings());
        } else {
            throw new IllegalArgumentException("Unsupported stream type " + streamType);
        }
    }

    private static SocketSettings getSocketSettings() {
        return SocketSettings.builder().applyConnectionString(getConnectionString()).build();
    }

    public static SslSettings getSslSettings() {
        SslSettings.Builder builder = SslSettings.builder().applyConnectionString(getConnectionString());
        if (System.getProperty("java.version").startsWith("1.6.")) {
            builder.invalidHostNameAllowed(true);
        }
        return builder.build();
    }

    public static ServerAddress getPrimary() throws InterruptedException {
        List<ServerDescription> serverDescriptions = getCluster().getDescription().getPrimaries();
        while (serverDescriptions.isEmpty()) {
            sleep(100);
            serverDescriptions = getCluster().getDescription().getPrimaries();
        }
        return serverDescriptions.get(0).getAddress();
    }

    public static List<MongoCredential> getCredentialList() {
        return getConnectionString().getCredentialList();
    }

    public static boolean isDiscoverableReplicaSet() {
        return getCluster().getDescription().getType() == REPLICA_SET
               && getCluster().getDescription().getConnectionMode() == MULTIPLE;
    }

    public static boolean isSharded() {
        return getCluster().getDescription().getType() == SHARDED;
    }

    public static boolean isAuthenticated() {
        return !getConnectionString().getCredentialList().isEmpty();
    }

    public static void enableMaxTimeFailPoint() {
        assumeThat(isSharded(), is(false));
        new CommandWriteOperation<BsonDocument>("admin",
                                                new BsonDocumentWrapper<Document>(new Document("configureFailPoint", "maxTimeAlwaysTimeOut")
                                                                                  .append("mode", "alwaysOn"),
                                                                                  new DocumentCodec()),
                                                new BsonDocumentCodec())
        .execute(getBinding());
    }

    public static void disableMaxTimeFailPoint() {
        assumeThat(isSharded(), is(false));
        if (serverVersionAtLeast(asList(2, 6, 0)) && !isSharded()) {
            new CommandWriteOperation<BsonDocument>("admin",
                                                    new BsonDocumentWrapper<Document>(new Document("configureFailPoint",
                                                                                                   "maxTimeAlwaysTimeOut")
                                                                                      .append("mode", "off"),
                                                                                      new DocumentCodec()),
                                                    new BsonDocumentCodec())
            .execute(getBinding());
        }
    }

    public static <T> T executeAsync(final AsyncWriteOperation<T> op) throws Throwable {
        return executeAsync(op, getAsyncBinding());
    }

    public static <T> T executeAsync(final AsyncWriteOperation<T> op, final AsyncReadWriteBinding binding) throws Throwable {
        final FutureResultCallback<T> futureResultCallback = new FutureResultCallback<T>();
        op.executeAsync(binding, futureResultCallback);
        return futureResultCallback.get(TIMEOUT, SECONDS);
    }

    public static <T> T executeAsync(final AsyncReadOperation<T> op) throws Throwable {
        return executeAsync(op, getAsyncBinding());
    }

    public static <T> T executeAsync(final AsyncReadOperation<T> op, final AsyncReadWriteBinding binding) throws Throwable {
        final FutureResultCallback<T> futureResultCallback = new FutureResultCallback<T>();
        op.executeAsync(binding, futureResultCallback);
        return futureResultCallback.get(TIMEOUT, SECONDS);
    }

    public static <T> void loopCursor(final List<AsyncBatchCursor<T>> batchCursors, final Block<T> block) throws Throwable {
        List<FutureResultCallback<Void>> futures = new ArrayList<FutureResultCallback<Void>>();
        for (AsyncBatchCursor<T> batchCursor : batchCursors) {
            FutureResultCallback<Void> futureResultCallback = new FutureResultCallback<Void>();
            futures.add(futureResultCallback);
            loopCursor(batchCursor, block, futureResultCallback);
        }
        for (int i = 0; i < batchCursors.size(); i++) {
            futures.get(i).get(TIMEOUT, SECONDS);
        }
    }

    public static <T> void loopCursor(final AsyncReadOperation<AsyncBatchCursor<T>> op, final Block<T> block) throws Throwable {
        final FutureResultCallback<Void> futureResultCallback = new FutureResultCallback<Void>();
        loopCursor(executeAsync(op), block, futureResultCallback);
        futureResultCallback.get(TIMEOUT, SECONDS);
    }

    public static <T> void loopCursor(final AsyncBatchCursor<T> batchCursor, final Block<T> block,
                                      final SingleResultCallback<Void> callback) {
        batchCursor.next(new SingleResultCallback<List<T>>() {
            @Override
            public void onResult(final List<T> results, final Throwable t) {
                    if (t != null || results == null) {
                        batchCursor.close();
                        callback.onResult(null, t);
                    } else {
                        try {
                            for (T result : results) {
                                block.apply(result);
                            }
                            loopCursor(batchCursor, block, callback);
                        } catch (Throwable tr) {
                            batchCursor.close();
                            callback.onResult(null, tr);
                        }
                    }
            }
        });
    }

    public static AsyncConnectionSource getWriteConnectionSource(final AsyncReadWriteBinding binding) throws Throwable {
        final FutureResultCallback<AsyncConnectionSource> futureResultCallback = new FutureResultCallback<AsyncConnectionSource>();
        binding.getWriteConnectionSource(futureResultCallback);
        return futureResultCallback.get(TIMEOUT, SECONDS);
    }

    public static AsyncConnectionSource getReadConnectionSource(final AsyncReadWriteBinding binding) throws Throwable {
        final FutureResultCallback<AsyncConnectionSource> futureResultCallback = new FutureResultCallback<AsyncConnectionSource>();
        binding.getReadConnectionSource(futureResultCallback);
        return futureResultCallback.get(TIMEOUT, SECONDS);
    }

    public static AsyncConnection getConnection(final AsyncConnectionSource source) throws Throwable {
        final FutureResultCallback<AsyncConnection> futureResultCallback = new FutureResultCallback<AsyncConnection>();
        source.getConnection(futureResultCallback);
        return futureResultCallback.get(TIMEOUT, SECONDS);
    }
}

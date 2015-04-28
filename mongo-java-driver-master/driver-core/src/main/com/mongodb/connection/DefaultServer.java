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

package com.mongodb.connection;

import com.mongodb.MongoException;
import com.mongodb.MongoNodeIsRecoveringException;
import com.mongodb.MongoNotPrimaryException;
import com.mongodb.MongoSecurityException;
import com.mongodb.MongoSocketException;
import com.mongodb.MongoSocketReadTimeoutException;
import com.mongodb.ServerAddress;
import com.mongodb.async.SingleResultCallback;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.mongodb.assertions.Assertions.isTrue;
import static com.mongodb.assertions.Assertions.notNull;
import static com.mongodb.connection.ServerConnectionState.CONNECTING;
import static com.mongodb.internal.async.ErrorHandlingResultCallback.errorHandlingCallback;

class DefaultServer implements ClusterableServer {
    private final ServerAddress serverAddress;
    private final ConnectionPool connectionPool;
    private final ClusterConnectionMode clusterConnectionMode;
    private final ConnectionFactory connectionFactory;
    private final ServerMonitor serverMonitor;
    private final Set<ChangeListener<ServerDescription>> changeListeners =
    Collections.newSetFromMap(new ConcurrentHashMap<ChangeListener<ServerDescription>, Boolean>());
    private final ChangeListener<ServerDescription> serverStateListener;
    private volatile ServerDescription description;
    private volatile boolean isClosed;

    public DefaultServer(final ServerAddress serverAddress,
                         final ClusterConnectionMode clusterConnectionMode,
                         final ConnectionPool connectionPool,
                         final ConnectionFactory connectionFactory,
                         final ServerMonitorFactory serverMonitorFactory) {
        notNull("serverMonitorFactory", serverMonitorFactory);
        this.clusterConnectionMode = notNull("clusterConnectionMode", clusterConnectionMode);
        this.connectionFactory = notNull("connectionFactory", connectionFactory);
        this.serverAddress = notNull("serverAddress", serverAddress);
        this.connectionPool = notNull("connectionPool", connectionPool);
        this.serverStateListener = new DefaultServerStateListener();
        description = ServerDescription.builder().state(CONNECTING).address(serverAddress).build();
        serverMonitor = serverMonitorFactory.create(serverStateListener);
        serverMonitor.start();
    }

    @Override
    public Connection getConnection() {
        isTrue("open", !isClosed());
        try {
            return connectionFactory.create(connectionPool.get(), new DefaultServerProtocolExecutor(), clusterConnectionMode);
        } catch (MongoSecurityException e) {
            invalidate();
            throw e;
        }
    }

    @Override
    public void getConnectionAsync(final SingleResultCallback<AsyncConnection> callback) {
        isTrue("open", !isClosed());
        connectionPool.getAsync(new SingleResultCallback<InternalConnection>() {
            @Override
            public void onResult(final InternalConnection result, final Throwable t) {
                if (t instanceof MongoSecurityException) {
                    invalidate();
                }
                if (t != null) {
                    callback.onResult(null, t);
                } else {
                    callback.onResult(connectionFactory.createAsync(result, new DefaultServerProtocolExecutor(), clusterConnectionMode),
                                      null);
                }
            }
        });
    }

    @Override
    public ServerDescription getDescription() {
        isTrue("open", !isClosed());

        return description;
    }

    @Override
    public void addChangeListener(final ChangeListener<ServerDescription> changeListener) {
        isTrue("open", !isClosed());

        changeListeners.add(changeListener);
    }

    @Override
    public void invalidate() {
        isTrue("open", !isClosed());

        serverStateListener.stateChanged(new ChangeEvent<ServerDescription>(description, ServerDescription.builder()
                                                                                                          .state(CONNECTING)
                                                                                                          .address(serverAddress).build()));
        connectionPool.invalidate();
        serverMonitor.invalidate();
    }

    @Override
    public void close() {
        if (!isClosed()) {
            connectionPool.close();
            serverMonitor.close();
            isClosed = true;
        }
    }

    @Override
    public boolean isClosed() {
        return isClosed;
    }

    @Override
    public void connect() {
        serverMonitor.connect();
    }

    ConnectionPool getConnectionPool() {
        return connectionPool;
    }

    private void handleThrowable(final Throwable t) {
        if ((t instanceof MongoSocketException && !(t instanceof MongoSocketReadTimeoutException))
            || t instanceof MongoNotPrimaryException
            || t instanceof MongoNodeIsRecoveringException) {
            invalidate();
        }
    }

    private class DefaultServerProtocolExecutor implements ProtocolExecutor {
        @Override
        public <T> T execute(final Protocol<T> protocol, final InternalConnection connection) {
            try {
                return protocol.execute(connection);
            } catch (MongoException e) {
                handleThrowable(e);
                throw e;
            }
        }

        @Override
        public <T> void executeAsync(final Protocol<T> protocol, final InternalConnection connection,
                                     final SingleResultCallback<T> callback) {
            protocol.executeAsync(connection, errorHandlingCallback(new SingleResultCallback<T>() {
                @Override
                public void onResult(final T result, final Throwable t) {
                    if (t != null) {
                        handleThrowable(t);
                    }
                    callback.onResult(result, t);
                }
            }));
        }
    }

    private final class DefaultServerStateListener implements ChangeListener<ServerDescription> {
        @Override
        public void stateChanged(final ChangeEvent<ServerDescription> event) {
            description = event.getNewValue();
            for (ChangeListener<ServerDescription> listener : changeListeners) {
                listener.stateChanged(event);
            }
        }
    }
}

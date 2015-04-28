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

import com.mongodb.ServerAddress;
import com.mongodb.async.SingleResultCallback;

import static com.mongodb.connection.ServerConnectionState.CONNECTING;

public class TestServer implements ClusterableServer {
    private ChangeListener<ServerDescription> changeListener;
    private ServerDescription description;
    private boolean isClosed;
    private final ServerAddress serverAddress;
    private int connectCount;

    public TestServer(final ServerAddress serverAddress) {
        this.serverAddress = serverAddress;
        invalidate();
    }

    public void sendNotification(final ServerDescription newDescription) {
        ServerDescription currentDescription = description;
        description = newDescription;
        if (changeListener != null) {
            changeListener.stateChanged(new ChangeEvent<ServerDescription>(currentDescription, newDescription));
        }
    }

    @Override
    public void addChangeListener(final ChangeListener<ServerDescription> newChangeListener) {
        this.changeListener = newChangeListener;
    }

    @Override
    public void invalidate() {
        sendNotification(ServerDescription.builder().state(CONNECTING).address(serverAddress).build());
    }

    @Override
    public void close() {
        isClosed = true;
    }

    @Override
    public boolean isClosed() {
        return isClosed;
    }

    @Override
    public void connect() {
        connectCount++;
    }

    public int getConnectCount() {
        return connectCount;
    }

    @Override
    public ServerDescription getDescription() {
        return description;
    }

    @Override
    public Connection getConnection() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void getConnectionAsync(final SingleResultCallback<AsyncConnection> callback) {
        throw new UnsupportedOperationException();
    }

}

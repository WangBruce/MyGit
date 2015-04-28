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

import com.mongodb.async.SingleResultCallback;
import org.bson.ByteBuf;

import java.util.List;

import static com.mongodb.assertions.Assertions.isTrue;
import static com.mongodb.internal.async.ErrorHandlingResultCallback.errorHandlingCallback;

/**
 * A connection that tracks when it was opened and when it was last used.
 */
class UsageTrackingInternalConnection implements InternalConnection {
    private volatile long openedAt;
    private volatile long lastUsedAt;
    private final int generation;
    private volatile InternalConnection wrapped;

    UsageTrackingInternalConnection(final InternalConnection wrapped, final int generation) {
        this.wrapped = wrapped;
        this.generation = generation;
        openedAt = Long.MAX_VALUE;
        lastUsedAt = openedAt;
    }

    @Override
    public void open() {
        isTrue("open", wrapped != null);
        wrapped.open();
        openedAt = System.currentTimeMillis();
        lastUsedAt = openedAt;
    }

    @Override
    public void openAsync(final SingleResultCallback<Void> callback) {
        isTrue("open", wrapped != null);
        wrapped.openAsync(new SingleResultCallback<Void>() {
            @Override
            public void onResult(final Void result, final Throwable t) {
                if (t != null) {
                    callback.onResult(null, t);
                } else {
                    openedAt = System.currentTimeMillis();
                    lastUsedAt = openedAt;
                    callback.onResult(null, null);
                }
            }
        });
    }

    @Override
    public void close() {
        isTrue("open", wrapped != null);
        wrapped.close();
        wrapped = null;
    }

    @Override
    public boolean opened() {
        isTrue("open", wrapped != null);
        return wrapped.opened();
    }

    @Override
    public boolean isClosed() {
        return wrapped == null || wrapped.isClosed();
    }

    @Override
    public ByteBuf getBuffer(final int size) {
        isTrue("open", wrapped != null);
        return wrapped.getBuffer(size);
    }

    @Override
    public void sendMessage(final List<ByteBuf> byteBuffers, final int lastRequestId) {
        isTrue("open", wrapped != null);
        wrapped.sendMessage(byteBuffers, lastRequestId);
        lastUsedAt = System.currentTimeMillis();
    }

    @Override
    public ResponseBuffers receiveMessage(final int responseTo) {
        isTrue("open", wrapped != null);
        ResponseBuffers responseBuffers = wrapped.receiveMessage(responseTo);
        lastUsedAt = System.currentTimeMillis();
        return responseBuffers;
    }

    @Override
    public void sendMessageAsync(final List<ByteBuf> byteBuffers, final int lastRequestId, final SingleResultCallback<Void> callback) {
        isTrue("open", wrapped != null);
        SingleResultCallback<Void> wrappedCallback = errorHandlingCallback(new SingleResultCallback<Void>() {
            @Override
            public void onResult(final Void result, final Throwable t) {
                lastUsedAt = System.currentTimeMillis();
                callback.onResult(result, t);
            }
        });
        wrapped.sendMessageAsync(byteBuffers, lastRequestId, wrappedCallback);
    }

    @Override
    public void receiveMessageAsync(final int responseTo, final SingleResultCallback<ResponseBuffers> callback) {
        isTrue("open", wrapped != null);
        SingleResultCallback<ResponseBuffers> wrappedCallback = errorHandlingCallback(new SingleResultCallback<ResponseBuffers>() {
            @Override
            public void onResult(final ResponseBuffers result, final Throwable t) {
                lastUsedAt = System.currentTimeMillis();
                callback.onResult(result, t);
            }
        });
        wrapped.receiveMessageAsync(responseTo, wrappedCallback);
    }

    @Override
    public ConnectionDescription getDescription() {
        isTrue("open", wrapped != null);
        return wrapped.getDescription();
    }

    /**
     * Gets the generation of this connection.  This can be used by connection pools to track whether the connection is stale.
     *
     * @return the generation.
     */
    int getGeneration() {
        return generation;
    }

    /**
     * Returns the time at which this connection was opened, or {@code Long.MAX_VALUE} if it has not yet been opened.
     *
     * @return the time when this connection was opened, in milliseconds since the epoch.
     */
    long getOpenedAt() {
        return openedAt;
    }

    /**
     * Returns the time at which this connection was last used, or {@code Long.MAX_VALUE} if it has not yet been used.
     *
     * @return the time when this connection was last used, in milliseconds since the epoch.
     */
    long getLastUsedAt() {
        return lastUsedAt;
    }
}

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

package com.mongodb.connection.netty;

import com.mongodb.MongoInternalException;
import com.mongodb.MongoInterruptedException;
import com.mongodb.MongoSocketOpenException;
import com.mongodb.MongoSocketReadTimeoutException;
import com.mongodb.ServerAddress;
import com.mongodb.connection.AsyncCompletionHandler;
import com.mongodb.connection.SocketSettings;
import com.mongodb.connection.SslSettings;
import com.mongodb.connection.Stream;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.bson.ByteBuf;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static com.mongodb.internal.connection.SslHelper.enableHostNameVerification;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * A Stream implementation based on Netty 4.0.
 */
final class NettyStream implements Stream {
    private final ServerAddress address;
    private final SocketSettings settings;
    private final SslSettings sslSettings;
    private final EventLoopGroup workerGroup;
    private final ByteBufAllocator allocator;

    private volatile boolean isClosed;
    private volatile Channel channel;

    private final LinkedList<io.netty.buffer.ByteBuf> pendingInboundBuffers = new LinkedList<io.netty.buffer.ByteBuf>();
    private volatile PendingReader pendingReader;
    private volatile Throwable pendingException;

    public NettyStream(final ServerAddress address, final SocketSettings settings, final SslSettings sslSettings,
                       final EventLoopGroup workerGroup, final ByteBufAllocator allocator) {
        this.address = address;
        this.settings = settings;
        this.sslSettings = sslSettings;
        this.workerGroup = workerGroup;
        this.allocator = allocator;
    }

    @Override
    public ByteBuf getBuffer(final int size) {
        return new NettyByteBuf(allocator.buffer(size, size));
    }

    @Override
    public void open() throws IOException {
        FutureAsyncCompletionHandler<Void> handler = new FutureAsyncCompletionHandler<Void>();
        openAsync(handler);
        handler.get();
    }

    @Override
    public void openAsync(final AsyncCompletionHandler<Void> handler) {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workerGroup);
        bootstrap.channel(NioSocketChannel.class);

        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, settings.getConnectTimeout(MILLISECONDS));
        bootstrap.option(ChannelOption.TCP_NODELAY, true);
        bootstrap.option(ChannelOption.SO_KEEPALIVE, settings.isKeepAlive());

        if (settings.getReceiveBufferSize() > 0) {
            bootstrap.option(ChannelOption.SO_RCVBUF, settings.getReceiveBufferSize());
        }
        if (settings.getSendBufferSize() > 0) {
            bootstrap.option(ChannelOption.SO_SNDBUF, settings.getSendBufferSize());
        }
        bootstrap.option(ChannelOption.ALLOCATOR, allocator);

        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(final SocketChannel ch) throws Exception {
                if (sslSettings.isEnabled()) {
                    SSLEngine engine = SSLContext.getDefault().createSSLEngine(address.getHost(), address.getPort());
                    engine.setUseClientMode(true);
                    if (!sslSettings.isInvalidHostNameAllowed()) {
                        engine.setSSLParameters(enableHostNameVerification(engine.getSSLParameters()));
                    }
                    ch.pipeline().addFirst("ssl", new SslHandler(engine, false));
                }
                ch.pipeline().addLast("readTimeoutHandler", new ReadTimeoutHandler(settings.getReadTimeout(MILLISECONDS), MILLISECONDS));
                ch.pipeline().addLast(new InboundBufferHandler());
            }
        });
        final ChannelFuture channelFuture = bootstrap.connect(address.getHost(), address.getPort());
        channelFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(final ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    channel = channelFuture.channel();
                    handler.completed(null);
                } else {
                    handler.failed(future.cause());
                }
            }
        });
    }

    @Override
    public void write(final List<ByteBuf> buffers) throws IOException {
        FutureAsyncCompletionHandler<Void> future = new FutureAsyncCompletionHandler<Void>();
        writeAsync(buffers, future);
        future.get();
    }

    @Override
    public ByteBuf read(final int numBytes) throws IOException {
        FutureAsyncCompletionHandler<ByteBuf> future = new FutureAsyncCompletionHandler<ByteBuf>();
        readAsync(numBytes, future);
        return future.get();
    }

    @Override
    public void writeAsync(final List<ByteBuf> buffers, final AsyncCompletionHandler<Void> handler) {
        CompositeByteBuf composite = PooledByteBufAllocator.DEFAULT.compositeBuffer();
        for (ByteBuf cur : buffers) {
            io.netty.buffer.ByteBuf byteBuf = ((NettyByteBuf) cur).asByteBuf();
            composite.addComponent(byteBuf.retain());
            composite.writerIndex(composite.writerIndex() + byteBuf.writerIndex());
        }

        channel.writeAndFlush(composite).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(final ChannelFuture future) throws Exception {
                if (!future.isSuccess()) {
                    handler.failed(future.cause());
                } else {
                    handler.completed(null);
                }
            }
        });
    }

    @Override
    public void readAsync(final int numBytes, final AsyncCompletionHandler<ByteBuf> handler) {
        ByteBuf buffer = null;
        Throwable exceptionResult = null;
        synchronized (this) {
            exceptionResult = pendingException;
            if (exceptionResult == null) {
                if (!hasBytesAvailable(numBytes)) {
                    pendingReader = new PendingReader(numBytes, handler);
                } else {
                    CompositeByteBuf composite = allocator.compositeBuffer(pendingInboundBuffers.size());
                    int bytesNeeded = numBytes;
                    for (Iterator<io.netty.buffer.ByteBuf> iter = pendingInboundBuffers.iterator(); iter.hasNext();) {
                        io.netty.buffer.ByteBuf next = iter.next();
                        int bytesNeededFromCurrentBuffer = Math.min(next.readableBytes(), bytesNeeded);
                        if (bytesNeededFromCurrentBuffer == next.readableBytes()) {
                            composite.addComponent(next);
                            iter.remove();
                        } else {
                            next.retain();
                            composite.addComponent(next.readSlice(bytesNeededFromCurrentBuffer));
                        }
                        composite.writerIndex(composite.writerIndex() + bytesNeededFromCurrentBuffer);
                        bytesNeeded -= bytesNeededFromCurrentBuffer;
                        if (bytesNeeded == 0) {
                            break;
                        }
                    }
                    buffer = new NettyByteBuf(composite).flip();
                }
            }
        }
        if (exceptionResult != null) {
            handler.failed(exceptionResult);
        }
        if (buffer != null) {
            handler.completed(buffer);
        }
    }

    private boolean hasBytesAvailable(final int numBytes) {
        int bytesAvailable = 0;
        for (io.netty.buffer.ByteBuf cur : pendingInboundBuffers) {
            bytesAvailable += cur.readableBytes();
            if (bytesAvailable >= numBytes) {
                return true;
            }
        }
        return false;
    }

    private void handleReadResponse(final io.netty.buffer.ByteBuf buffer, final Throwable t) {
        PendingReader localPendingReader = null;
        synchronized (this) {
            if (buffer != null) {
                pendingInboundBuffers.add(buffer.retain());
            } else {
                pendingException = t;
            }
            if (pendingReader != null) {
                localPendingReader = pendingReader;
                pendingReader = null;
            }
        }

        if (localPendingReader != null) {
            readAsync(localPendingReader.numBytes, localPendingReader.handler);
        }
    }

    @Override
    public ServerAddress getAddress() {
        return address;
    }

    @Override
    public void close() {
        isClosed = true;
        if (channel != null) {
            channel.close();
            channel = null;
        }
        for (Iterator<io.netty.buffer.ByteBuf> iterator = pendingInboundBuffers.iterator(); iterator.hasNext();) {
            io.netty.buffer.ByteBuf nextByteBuf = iterator.next();
            iterator.remove();
            nextByteBuf.release();
        }
    }

    @Override
    public boolean isClosed() {
        return isClosed;
    }

    private class InboundBufferHandler extends SimpleChannelInboundHandler<io.netty.buffer.ByteBuf> {
        @Override
        protected void channelRead0(final ChannelHandlerContext ctx, final io.netty.buffer.ByteBuf buffer) throws Exception {
            handleReadResponse(buffer, null);
        }

        @Override
        public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable t) {
            if (t instanceof ReadTimeoutException) {
                handleReadResponse(null, new MongoSocketReadTimeoutException("Timeout while receiving message", address, t));
            } else {
                handleReadResponse(null, t);
            }
            ctx.close();
        }
    }

    private static final class PendingReader {
        private final int numBytes;
        private final AsyncCompletionHandler<ByteBuf> handler;

        private PendingReader(final int numBytes, final AsyncCompletionHandler<ByteBuf> handler) {
            this.numBytes = numBytes;
            this.handler = handler;
        }
    }

    private static final class FutureAsyncCompletionHandler<T> implements AsyncCompletionHandler<T> {
        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile T t;
        private volatile Throwable throwable;

        public FutureAsyncCompletionHandler() {
        }

        @Override
        public void completed(final T t) {
            this.t = t;
            latch.countDown();
        }

        @Override
        public void failed(final Throwable t) {
            this.throwable = t;
            latch.countDown();
        }

        public T get() throws IOException {
            try {
                latch.await();
                if (throwable != null) {
                    if (throwable instanceof IOException) {
                        throw (IOException) throwable;
                    } else if (throwable instanceof MongoSocketReadTimeoutException) {
                        throw (MongoSocketReadTimeoutException) throwable;
                    } else if (throwable instanceof MongoSocketOpenException) {
                        throw (MongoSocketOpenException) throwable;
                    } else {
                        throw new MongoInternalException("Exception thrown from Netty Stream", throwable);
                    }
                }
                return t;
            } catch (InterruptedException e) {
                throw new MongoInterruptedException("Interrupted", e);
            }
        }
    }
}

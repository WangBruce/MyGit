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

import com.mongodb.MongoSocketException;
import com.mongodb.annotations.ThreadSafe;
import com.mongodb.diagnostics.logging.Logger;
import com.mongodb.diagnostics.logging.Loggers;
import org.bson.BsonDocument;
import org.bson.BsonInt32;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.mongodb.assertions.Assertions.isTrue;
import static com.mongodb.connection.CommandHelper.executeCommand;
import static com.mongodb.connection.DescriptionHelper.createServerDescription;
import static com.mongodb.connection.ServerConnectionState.CONNECTING;
import static com.mongodb.connection.ServerType.UNKNOWN;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

@ThreadSafe
class DefaultServerMonitor implements ServerMonitor {

    private static final Logger LOGGER = Loggers.getLogger("cluster");

    private final ServerId serverId;
    private final ChangeListener<ServerDescription> serverStateListener;
    private final InternalConnectionFactory internalConnectionFactory;
    private final ConnectionPool connectionPool;
    private final ServerSettings settings;
    private volatile ServerMonitorRunnable monitor;
    private volatile Thread monitorThread;
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private volatile boolean isClosed;

    DefaultServerMonitor(final ServerId serverId, final ServerSettings settings,
                         final ChangeListener<ServerDescription> serverStateListener,
                         final InternalConnectionFactory internalConnectionFactory, final ConnectionPool connectionPool) {
        this.settings = settings;
        this.serverId = serverId;
        this.serverStateListener = serverStateListener;
        this.internalConnectionFactory = internalConnectionFactory;
        this.connectionPool = connectionPool;
        monitorThread = createMonitorThread();
        isClosed = false;
    }

    @Override
    public void start() {
        monitorThread.start();
    }

    @Override
    public void connect() {
        lock.lock();
        try {
            condition.signal();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void invalidate() {
        isTrue("open", !isClosed);
        monitor.close();
        monitorThread.interrupt();
        monitorThread = createMonitorThread();
        monitorThread.start();
    }

    @Override
    public void close() {
        monitor.close();
        monitorThread.interrupt();
        isClosed = true;
    }

    Thread createMonitorThread() {
        monitor = new ServerMonitorRunnable();
        Thread monitorThread = new Thread(monitor, "cluster-" + serverId.getClusterId() + "-" + serverId.getAddress());
        monitorThread.setDaemon(true);
        return monitorThread;
    }

    class ServerMonitorRunnable implements Runnable {
        private volatile boolean monitorIsClosed;
        private final ExponentiallyWeightedMovingAverage averageRoundTripTime = new ExponentiallyWeightedMovingAverage(0.2);

        public void close() {
            monitorIsClosed = true;
        }

        @Override
        @SuppressWarnings("unchecked")
        public synchronized void run() {
            InternalConnection connection = null;
            try {
                ServerDescription currentServerDescription = getConnectingServerDescription(null);
                Throwable currentException = null;
                while (!monitorIsClosed) {
                    ServerDescription previousServerDescription = currentServerDescription;
                    Throwable previousException = currentException;
                    try {
                        if (connection == null) {
                            connection = internalConnectionFactory.create(serverId);
                            try {
                                connection.open();
                            } catch (Throwable t) {
                                connection = null;
                                throw t;
                            }
                        }
                        try {
                            currentServerDescription = lookupServerDescription(connection);
                        } catch (MongoSocketException e) {
                            connectionPool.invalidate();
                            connection.close();
                            connection = null;
                            connection = internalConnectionFactory.create(serverId);
                            try {
                                connection.open();
                            } catch (Throwable t) {
                                connection = null;
                                throw t;
                            }
                            try {
                                currentServerDescription = lookupServerDescription(connection);
                            } catch (MongoSocketException e1) {
                                connection.close();
                                connection = null;
                                throw e1;
                            }
                        }
                    } catch (Throwable t) {
                        averageRoundTripTime.reset();
                        currentException = t;
                        currentServerDescription = getConnectingServerDescription(t);
                    }

                    if (!monitorIsClosed) {
                        try {
                            logStateChange(previousServerDescription, previousException, currentServerDescription, currentException);
                            sendStateChangedEvent(previousServerDescription, currentServerDescription);
                        } catch (Throwable t) {
                            LOGGER.warn("Exception in monitor thread during notification of server description state change", t);
                        }
                        waitForNext();
                    }
                }
            } finally {
                if (connection != null) {
                    connection.close();
                }
            }
        }

        private ServerDescription getConnectingServerDescription(final Throwable exception) {
            return ServerDescription.builder().type(UNKNOWN).state(CONNECTING).address(serverId.getAddress()).exception(exception).build();
        }

        private ServerDescription lookupServerDescription(final InternalConnection connection) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(format("Checking status of %s", serverId.getAddress()));
            }
            long start = System.nanoTime();
            BsonDocument isMasterResult = executeCommand("admin", new BsonDocument("ismaster", new BsonInt32(1)), connection);
            averageRoundTripTime.addSample(System.nanoTime() - start);

            return createServerDescription(serverId.getAddress(), isMasterResult, connection.getDescription().getServerVersion(),
                                           averageRoundTripTime.getAverage());
        }

        private void sendStateChangedEvent(final ServerDescription previousServerDescription,
                                           final ServerDescription currentServerDescription) {
            if (stateHasChanged(previousServerDescription, currentServerDescription)) {
                serverStateListener.stateChanged(new ChangeEvent<ServerDescription>(previousServerDescription,
                                                                                    currentServerDescription));
            }
        }

        private void logStateChange(final ServerDescription previousServerDescription, final Throwable previousException,
                                    final ServerDescription currentServerDescription, final Throwable currentException) {
            // Note that the ServerDescription.equals method does not include the average ping time as part of the comparison,
            // so this will not spam the logs too hard.
            if (descriptionHasChanged(previousServerDescription, currentServerDescription)
                || exceptionHasChanged(previousException, currentException)) {
                if (currentException != null) {
                    LOGGER.info(format("Exception in monitor thread while connecting to server %s", serverId.getAddress()),
                                currentException);
                } else {
                    LOGGER.info(format("Monitor thread successfully connected to server with description %s", currentServerDescription));
                }
            }
        }

        private void waitForNext() {
            try {
                long timeRemaining = waitForSignalOrTimeout();
                if (timeRemaining > 0) {
                    long timeWaiting = settings.getHeartbeatFrequency(NANOSECONDS) - timeRemaining;
                    long minimumNanosToWait = settings.getMinHeartbeatFrequency(NANOSECONDS);
                    if (timeWaiting < minimumNanosToWait) {
                        long millisToSleep = MILLISECONDS.convert(minimumNanosToWait - timeWaiting, NANOSECONDS);
                        if (millisToSleep > 0) {
                            Thread.sleep(millisToSleep);
                        }
                    }
                }
            } catch (InterruptedException e) {
                // fall through
            }
        }

        private long waitForSignalOrTimeout() throws InterruptedException {
            lock.lock();
            try {
                return condition.awaitNanos(settings.getHeartbeatFrequency(NANOSECONDS));
            } finally {
                lock.unlock();
            }
        }
    }

    static boolean descriptionHasChanged(final ServerDescription previousServerDescription,
                                         final ServerDescription currentServerDescription) {
        return !previousServerDescription.equals(currentServerDescription);
    }

    static boolean stateHasChanged(final ServerDescription previousServerDescription, final ServerDescription currentServerDescription) {
        return descriptionHasChanged(previousServerDescription, currentServerDescription)
               || previousServerDescription.getRoundTripTimeNanos() != currentServerDescription.getRoundTripTimeNanos();
    }

    static boolean exceptionHasChanged(final Throwable previousException, final Throwable currentException) {
        if (currentException == null) {
            return previousException != null;
        } else if (previousException == null) {
            return true;
        } else if (!currentException.getClass().equals(previousException.getClass())) {
            return true;
        } else if (currentException.getMessage() == null) {
            return previousException.getMessage() != null;
        } else {
            return !currentException.getMessage().equals(previousException.getMessage());
        }
    }
}

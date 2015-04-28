/*
 * Copyright (c) 2008-2014 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.connection

import com.mongodb.MongoClientException
import com.mongodb.MongoException
import com.mongodb.MongoInternalException
import com.mongodb.MongoTimeoutException
import com.mongodb.MongoWaitQueueFullException
import com.mongodb.ReadPreference
import com.mongodb.ServerAddress
import com.mongodb.event.ClusterListener
import com.mongodb.selector.PrimaryServerSelector
import com.mongodb.selector.ReadPreferenceServerSelector
import com.mongodb.selector.ServerAddressSelector
import spock.lang.Specification

import java.util.concurrent.CountDownLatch

import static com.mongodb.connection.ClusterConnectionMode.MULTIPLE
import static com.mongodb.connection.ClusterSettings.builder
import static com.mongodb.connection.ServerType.REPLICA_SET_PRIMARY
import static com.mongodb.connection.ServerType.REPLICA_SET_SECONDARY
import static java.util.concurrent.TimeUnit.MILLISECONDS
import static java.util.concurrent.TimeUnit.SECONDS

class BaseClusterSpecification extends Specification {

    private static final ClusterListener CLUSTER_LISTENER = new NoOpClusterListener()
    private final ServerAddress firstServer = new ServerAddress('localhost:27017')
    private final ServerAddress secondServer = new ServerAddress('localhost:27018')
    private final ServerAddress thirdServer = new ServerAddress('localhost:27019')
    private final List<ServerAddress> allServers = [firstServer, secondServer, thirdServer]
    private final TestClusterableServerFactory factory = new TestClusterableServerFactory()

    def 'should compose server selector passed to selectServer with server selector in cluster settings'() {
        given:
        def cluster = new MultiServerCluster(new ClusterId(),
                                             builder().mode(MULTIPLE)
                                                      .hosts([firstServer, secondServer, thirdServer])
                                                      .serverSelectionTimeout(1, SECONDS)
                                                      .serverSelector(new ServerAddressSelector(firstServer))
                                                      .build(),
                                             factory, CLUSTER_LISTENER)
        factory.sendNotification(firstServer, REPLICA_SET_SECONDARY, allServers)
        factory.sendNotification(secondServer, REPLICA_SET_SECONDARY, allServers)
        factory.sendNotification(thirdServer, REPLICA_SET_PRIMARY, allServers)

        expect:
        cluster.selectServer(new ReadPreferenceServerSelector(ReadPreference.secondary())).description.address == firstServer
    }

    def 'should use server selector passed to selectServer if server selector in cluster settings is null'() {
        given:
        def cluster = new MultiServerCluster(new ClusterId(),
                                             builder().mode(MULTIPLE)
                                                      .serverSelectionTimeout(1, SECONDS)
                                                      .hosts([firstServer, secondServer, thirdServer])
                                                      .build(),
                                             factory, CLUSTER_LISTENER)
        factory.sendNotification(firstServer, REPLICA_SET_SECONDARY, allServers)
        factory.sendNotification(secondServer, REPLICA_SET_SECONDARY, allServers)
        factory.sendNotification(thirdServer, REPLICA_SET_PRIMARY, allServers)

        expect:
        cluster.selectServer(new ServerAddressSelector(firstServer)).description.address == firstServer
    }

    def 'should timeout with useful message'() {
        given:
        def cluster = new MultiServerCluster(new ClusterId(),
                                             builder().mode(MULTIPLE)
                                                      .hosts([firstServer, secondServer])
                                                      .serverSelectionTimeout(1, MILLISECONDS)
                                                      .build(),
                                             factory, CLUSTER_LISTENER)

        when:
        factory.sendNotification(firstServer, ServerDescription.builder().type(ServerType.UNKNOWN)
                                                               .state(ServerConnectionState.CONNECTING)
                                                               .address(firstServer)
                                                               .exception(new MongoInternalException('oops'))
                                                               .build())

        cluster.getDescription()

        then:
        def e = thrown(MongoTimeoutException)
        e.getMessage() == 'Timed out after 1 ms while waiting to connect. Client view of cluster state is {type=UNKNOWN, ' +
        'servers=[{address=localhost:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoInternalException: oops}}, ' +
        '{address=localhost:27018, type=UNKNOWN, state=CONNECTING}]'

        when:
        cluster.selectServer(new PrimaryServerSelector())

        then:
        e = thrown(MongoTimeoutException)
        e.getMessage() == 'Timed out after 1 ms while waiting for a server that matches PrimaryServerSelector. Client view of cluster ' +
        'state is {type=UNKNOWN, servers=[{address=localhost:27017, type=UNKNOWN, state=CONNECTING, ' +
        'exception={com.mongodb.MongoInternalException: oops}}, {address=localhost:27018, type=UNKNOWN, state=CONNECTING}]'


    }

    def 'should select server asynchronously'() {
        given:
        def cluster = new MultiServerCluster(new ClusterId(),
                                             builder().mode(MULTIPLE)
                                                      .hosts([firstServer, secondServer, thirdServer])
                                                      .build(),
                                             factory, CLUSTER_LISTENER)
        factory.sendNotification(firstServer, REPLICA_SET_SECONDARY, allServers)

        when:
        def server = selectServerAsyncAndGet(cluster, firstServer)

        then:
        server.description.address == firstServer

        when:
        def secondServerLatch = selectServerAsync(cluster, secondServer)
        def thirdServerLatch = selectServerAsync(cluster, thirdServer)
        factory.sendNotification(secondServer, REPLICA_SET_SECONDARY, allServers)
        factory.sendNotification(thirdServer, REPLICA_SET_SECONDARY, allServers)
        secondServerLatch.latch.await()
        thirdServerLatch.latch.await()

        then:
        secondServerLatch.server.description.address == secondServer
        thirdServerLatch.server.description.address == thirdServer

        cleanup:
        cluster?.close()
    }

    def 'when selecting server asynchronously should send MongoClientException to callback if cluster is closed before success'() {
        given:
        def cluster = new MultiServerCluster(new ClusterId(),
                                             builder().mode(MULTIPLE)
                                                      .hosts([firstServer, secondServer, thirdServer])
                                                      .build(),
                                             factory, CLUSTER_LISTENER)

        when:
        def serverLatch = selectServerAsync(cluster, firstServer)
        cluster.close()
        serverLatch.get()

        then:
        thrown(MongoClientException)

        cleanup:
        cluster?.close()
    }

    def 'when selecting server asynchronously should send MongoTimeoutException to callback after timeout period'() {
        given:
        def cluster = new MultiServerCluster(new ClusterId(),
                                             builder().mode(MULTIPLE)
                                                      .hosts([firstServer, secondServer, thirdServer])
                                                      .serverSelectionTimeout(100, MILLISECONDS)
                                                      .build(),
                                             factory, CLUSTER_LISTENER)

        when:
        selectServerAsyncAndGet(cluster, firstServer)

        then:
        thrown(MongoTimeoutException)

        cleanup:
        cluster?.close()
    }

    def 'when selecting server asynchronously should send MongoWaitQueueFullException to callback if there are too many waiters'() {
        given:
        def cluster = new MultiServerCluster(new ClusterId(),
                                             builder().mode(MULTIPLE)
                                                      .hosts([firstServer, secondServer, thirdServer])
                                                      .serverSelectionTimeout(1, SECONDS)
                                                      .maxWaitQueueSize(1)
                                                      .build(),
                                             factory, CLUSTER_LISTENER)

        when:
        selectServerAsync(cluster, firstServer)
        selectServerAsyncAndGet(cluster, firstServer)

        then:
        thrown(MongoWaitQueueFullException)

        cleanup:
        cluster?.close()
    }

    def selectServerAsyncAndGet(BaseCluster cluster, ServerAddress serverAddress) {
        selectServerAsync(cluster, serverAddress).get()
    }

    def selectServerAsync(BaseCluster cluster, ServerAddress serverAddress) {
        def serverLatch = new ServerLatch()
        cluster.selectServerAsync(new ServerAddressSelector(serverAddress)) { Server result, MongoException e ->
            serverLatch.server = result
            serverLatch.throwable = e
            serverLatch.latch.countDown()
        }
        serverLatch
    }

    class ServerLatch {
        CountDownLatch latch = new CountDownLatch(1)
        Server server
        Throwable throwable

        def get() {
            latch.await()
            if (throwable != null) {
                throw throwable
            }
            server
        }
    }
}
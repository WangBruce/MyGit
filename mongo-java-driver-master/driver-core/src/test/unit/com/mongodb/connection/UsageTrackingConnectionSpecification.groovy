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

package com.mongodb.connection

import category.Async
import com.mongodb.ServerAddress
import com.mongodb.async.FutureResultCallback
import org.junit.experimental.categories.Category
import spock.lang.IgnoreIf
import spock.lang.Specification

import static java.util.concurrent.TimeUnit.SECONDS

class UsageTrackingConnectionSpecification extends Specification {
    private static final ServerId SERVER_ID = new ServerId(new ClusterId(), new ServerAddress())

    def 'generation is initialized'() {
        when:
        def connection = new UsageTrackingInternalConnection(new TestInternalConnectionFactory().create(SERVER_ID), 1);

        then:
        connection.generation == 1
    }

    def 'openAt should be set on open'() {
        when:
        def connection = new UsageTrackingInternalConnection(new TestInternalConnectionFactory().create(SERVER_ID), 0);

        then:
        connection.openedAt == Long.MAX_VALUE

        when:
        connection.open()

        then:
        connection.openedAt <= System.currentTimeMillis()
    }

    @Category(Async)
    @IgnoreIf({ javaVersion < 1.7 })
    def 'openAt should be set on open asynchronously'() {
        when:
        def futureResultCallback = new FutureResultCallback<Void>()
        def connection = new UsageTrackingInternalConnection(new TestInternalConnectionFactory().create(SERVER_ID), 0);

        then:
        connection.openedAt == Long.MAX_VALUE

        when:
        connection.openAsync(futureResultCallback)
        futureResultCallback.get(60, SECONDS)

        then:
        connection.openedAt <= System.currentTimeMillis()
    }

    def 'lastUsedAt should be set on open'() {
        when:
        def connection = new UsageTrackingInternalConnection(new TestInternalConnectionFactory().create(SERVER_ID), 0);

        then:
        connection.lastUsedAt == Long.MAX_VALUE

        when:
        connection.open()

        then:
        connection.lastUsedAt <= System.currentTimeMillis()
    }

    @Category(Async)
    @IgnoreIf({ javaVersion < 1.7 })
    def 'lastUsedAt should be set on open asynchronously'() {
        when:
        def futureResultCallback = new FutureResultCallback<Void>()
        def connection = new UsageTrackingInternalConnection(new TestInternalConnectionFactory().create(SERVER_ID), 0);

        then:
        connection.lastUsedAt == Long.MAX_VALUE

        when:
        connection.openAsync(futureResultCallback)
        futureResultCallback.get(60, SECONDS)

        then:
        connection.lastUsedAt <= System.currentTimeMillis()
    }

    def 'lastUsedAt should be set on sendMessage'() {
        given:
        def connection = new UsageTrackingInternalConnection(new TestInternalConnectionFactory().create(SERVER_ID), 0);
        connection.open()
        def openedLastUsedAt = connection.lastUsedAt

        when:
        connection.sendMessage(Arrays.asList(), 1)

        then:
        connection.lastUsedAt >= openedLastUsedAt
        connection.lastUsedAt <= System.currentTimeMillis()
    }

    @Category(Async)
    @IgnoreIf({ javaVersion < 1.7 })
    def 'lastUsedAt should be set on sendMessage asynchronously'() {
        given:
        def connection = new UsageTrackingInternalConnection(new TestInternalConnectionFactory().create(SERVER_ID), 0);
        connection.open()
        def openedLastUsedAt = connection.lastUsedAt
        def futureResultCallback = new FutureResultCallback<Void>()

        when:
        connection.sendMessageAsync(Arrays.asList(), 1, futureResultCallback)
        futureResultCallback.get(60, SECONDS)

        then:
        connection.lastUsedAt >= openedLastUsedAt
        connection.lastUsedAt <= System.currentTimeMillis()
    }

    def 'lastUsedAt should be set on receiveMessage'() {
        given:
        def connection = new UsageTrackingInternalConnection(new TestInternalConnectionFactory().create(SERVER_ID), 0);
        connection.open()
        def openedLastUsedAt = connection.lastUsedAt
        when:
        connection.receiveMessage(1)

        then:
        connection.lastUsedAt >= openedLastUsedAt
        connection.lastUsedAt <= System.currentTimeMillis()
    }

    def 'lastUsedAt should be set on receiveMessage asynchronously'() {
        given:
        def connection = new UsageTrackingInternalConnection(new TestInternalConnectionFactory().create(SERVER_ID), 0);
        connection.open()
        def openedLastUsedAt = connection.lastUsedAt
        def futureResultCallback = new FutureResultCallback<Void>()

        when:
        connection.receiveMessageAsync(1, futureResultCallback)
        futureResultCallback.get(60, SECONDS)

        then:
        connection.lastUsedAt >= openedLastUsedAt
        connection.lastUsedAt <= System.currentTimeMillis()
    }
}

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

import com.mongodb.ConnectionString
import spock.lang.Specification

import static java.util.concurrent.TimeUnit.MILLISECONDS

class SocketSettingsSpecification extends Specification {
    def 'should have correct defaults'() {
        when:
        def settings = SocketSettings.builder().build()

        then:
        settings.getConnectTimeout(MILLISECONDS) == 10000
        settings.getReadTimeout(MILLISECONDS) == 0
        !settings.keepAlive
        settings.receiveBufferSize == 0
        settings.sendBufferSize == 0
    }

    def 'should apply builder settings'() {
        when:
        def settings = SocketSettings.builder()
                                     .connectTimeout(5000, MILLISECONDS)
                                     .readTimeout(2000, MILLISECONDS)
                                     .keepAlive(true)
                                     .sendBufferSize(1000)
                                     .receiveBufferSize(1500)
                                     .keepAlive(true)
                                     .build()


        then:
        settings.getConnectTimeout(MILLISECONDS) == 5000
        settings.getReadTimeout(MILLISECONDS) == 2000
        settings.keepAlive
        settings.sendBufferSize == 1000
        settings.receiveBufferSize == 1500
    }

    def 'should apply connection string'() {
        when:
        def settings = SocketSettings.builder()
                                     .applyConnectionString(new ConnectionString
                                                                    ('mongodb://localhost/?connectTimeoutMS=5000&socketTimeoutMS=2000'))
                                     .build()


        then:
        settings.getConnectTimeout(MILLISECONDS) == 5000
        settings.getReadTimeout(MILLISECONDS) == 2000
        !settings.keepAlive
        settings.sendBufferSize == 0
        settings.receiveBufferSize == 0
    }

    def 'identical settings should be equal'() {
        expect:
        SocketSettings.builder().build() == SocketSettings.builder().build()
        SocketSettings.builder()
                      .connectTimeout(5000, MILLISECONDS)
                      .readTimeout(2000, MILLISECONDS)
                      .keepAlive(true)
                      .sendBufferSize(1000)
                      .receiveBufferSize(1500)
                      .keepAlive(true)
                      .build() ==
        SocketSettings.builder()
                      .connectTimeout(5000, MILLISECONDS)
                      .readTimeout(2000, MILLISECONDS)
                      .keepAlive(true)
                      .sendBufferSize(1000)
                      .receiveBufferSize(1500)
                      .keepAlive(true)
                      .build()
    }

    def 'different settings should not be equal'() {
        expect:
        SocketSettings.builder().keepAlive(true).build() != SocketSettings.builder().keepAlive(false).build()
    }

    def 'identical settings should have same hash code'() {
        expect:
        SocketSettings.builder().build().hashCode() == SocketSettings.builder().build().hashCode()
        SocketSettings.builder()
                      .connectTimeout(5000, MILLISECONDS)
                      .readTimeout(2000, MILLISECONDS)
                      .keepAlive(true)
                      .sendBufferSize(1000)
                      .receiveBufferSize(1500)
                      .keepAlive(true)
                      .build().hashCode() ==
        SocketSettings.builder()
                      .connectTimeout(5000, MILLISECONDS)
                      .readTimeout(2000, MILLISECONDS)
                      .keepAlive(true)
                      .sendBufferSize(1000)
                      .receiveBufferSize(1500)
                      .keepAlive(true)
                      .build().hashCode()
    }

    def 'different settings should have different hash codes'() {
        expect:
        SocketSettings.builder().keepAlive(true).build().hashCode() != SocketSettings.builder().keepAlive(false).build().hashCode()
    }
}
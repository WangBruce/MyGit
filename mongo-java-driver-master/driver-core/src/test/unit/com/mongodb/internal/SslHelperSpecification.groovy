/*
 * Copyright 2015 MongoDB, Inc.
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

package com.mongodb.internal

import com.mongodb.internal.connection.SslHelper
import spock.lang.IgnoreIf
import spock.lang.Specification

import javax.net.ssl.SSLParameters


@IgnoreIf({ System.getProperty('java.version').startsWith('1.6.') })
class SslHelperSpecification extends Specification {
    def 'should enable HTTPS host name verification'() {
        when:
        def sllParameters = SslHelper.enableHostNameVerification(new SSLParameters())

        then:
        sllParameters.getEndpointIdentificationAlgorithm() == 'HTTPS'
    }
}
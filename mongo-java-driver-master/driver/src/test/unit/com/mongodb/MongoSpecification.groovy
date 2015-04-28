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







package com.mongodb

import com.mongodb.connection.Cluster
import com.mongodb.connection.ClusterConnectionMode
import com.mongodb.connection.ClusterDescription
import com.mongodb.connection.ClusterType
import spock.lang.Specification
import spock.lang.Subject

@SuppressWarnings('UnnecessaryParenthesesForMethodCallWithClosure')
class MongoSpecification extends Specification {
    private final ClusterDescription clusterDescription = Mock()
    private final Cluster cluster = Mock() {
        getDescription() >> clusterDescription
    }

    @Subject
    private Mongo mongo = new Mongo(cluster, MongoClientOptions.builder().build(), []);

    def setupSpec() {
        Map.metaClass.bitwiseNegate = { new BasicDBObject(delegate) }
    }

    def 'should return replica set status if cluster type replica set and mode discovering'() {
        //TODO: this is not really the correct way to go about this
        //we should have a test builder for the cluster if we have to mock the behaviour to get it to do what we want
        //currently we're getting null pointers for description
        given:
        clusterDescription.getType() >> ClusterType.REPLICA_SET
        clusterDescription.getConnectionMode() >> ClusterConnectionMode.MULTIPLE

        expect:
        mongo.getReplicaSetStatus() != null;
    }


    def 'should return null if cluster type not replica'() {
        given:
        clusterDescription.getType() >> ClusterType.SHARDED
        clusterDescription.getConnectionMode() >> ClusterConnectionMode.MULTIPLE

        expect:
        mongo.getReplicaSetStatus() == null;
    }

    def 'should return null if cluster mode not discovering'() {
        given:
        clusterDescription.getType() >> ClusterType.REPLICA_SET
        clusterDescription.getConnectionMode() >> ClusterConnectionMode.SINGLE

        expect:
        mongo.getReplicaSetStatus() == null;
    }

    def 'should send fsync commands in expected form'() {
        given:
        DB db = Mock()
        mongo = Spy(Mongo, constructorArgs: [cluster, MongoClientOptions.builder().build(), []]) {
            getDB('admin') >> db
        }

        when:
        mongo.fsync(false)

        then:
        1 * db.command(~['fsync': 1])

        when:
        mongo.fsync(true)

        then:
        1 * db.command(~['fsync': 1, 'async': 1])
    }

    def 'should lock and unlock as server'() {
        given:
        DBCollection unlockCollection = Mock()
        DBCollection inprogCollection = Mock()
        DB db = Mock() {
            getCollection('$cmd.sys.unlock') >> unlockCollection
            getCollection('$cmd.sys.inprog') >> inprogCollection
        }
        mongo = Spy(Mongo, constructorArgs: [cluster, MongoClientOptions.builder().build(), []]) {
            getDB('admin') >> db
        }

        when:
        mongo.fsyncAndLock()

        then:
        1 * db.command(~['fsync': 1, 'lock': 1])

        when:
        mongo.isLocked()

        then:
        1 * inprogCollection.findOne() >> new BasicDBObject('fsyncLock', 1)

        when:
        mongo.unlock()

        then:
        1 * unlockCollection.findOne()
    }

}

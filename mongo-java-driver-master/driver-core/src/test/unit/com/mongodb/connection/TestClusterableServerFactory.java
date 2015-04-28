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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.mongodb.connection.ServerConnectionState.CONNECTED;

public class TestClusterableServerFactory implements ClusterableServerFactory {
    private final Map<ServerAddress, TestServer> addressToServerMap = new HashMap<ServerAddress, TestServer>();

    @Override
    public ClusterableServer create(final ServerAddress serverAddress) {
        addressToServerMap.put(serverAddress, new TestServer(serverAddress));
        return addressToServerMap.get(serverAddress);
    }

    @Override
    public ServerSettings getSettings() {
        return ServerSettings.builder().build();
    }

    public TestServer getServer(final ServerAddress serverAddress) {
        return addressToServerMap.get(serverAddress);
    }

    public void sendNotification(final ServerAddress serverAddress, final ServerDescription serverDescription) {
        getServer(serverAddress).sendNotification(serverDescription);
    }


    public void sendNotification(final ServerAddress serverAddress, final ServerType serverType, final List<ServerAddress> hosts) {
        sendNotification(serverAddress, serverType, hosts, "test");
    }

    public void sendNotification(final ServerAddress serverAddress, final ServerType serverType, final List<ServerAddress> hosts,
                                 final List<ServerAddress> passives) {
        getServer(serverAddress).sendNotification(getBuilder(serverAddress, serverType, hosts, passives, true, "test").build());
    }

    public void sendNotification(final ServerAddress serverAddress, final ServerType serverType, final List<ServerAddress> hosts,
                                 final String setName) {
        getServer(serverAddress).sendNotification(getBuilder(serverAddress, serverType, hosts, Collections.<ServerAddress>emptyList(),
                                                             true, setName)
                                                  .build());
    }

    public void sendNotification(final ServerAddress serverAddress, final ServerType serverType, final List<ServerAddress> hosts,
                                 final boolean ok) {
        getServer(serverAddress).sendNotification(getBuilder(serverAddress, serverType, hosts, Collections.<ServerAddress>emptyList(),
                                                             ok, null)
                                                  .build());
    }

    public ServerDescription getDescription(final ServerAddress server) {
        return getServer(server).getDescription();
    }

    public Set<ServerDescription> getDescriptions(final ServerAddress... servers) {
        Set<ServerDescription> serverDescriptions = new HashSet<ServerDescription>();
        for (ServerAddress cur : servers) {
            serverDescriptions.add(getServer(cur).getDescription());
        }
        return serverDescriptions;
    }

    private ServerDescription.Builder getBuilder(final ServerAddress serverAddress, final ServerType serverType,
                                                 final List<ServerAddress> hosts, final List<ServerAddress> passives, final boolean ok,
                                                 final String setName) {
        Set<String> hostsSet = new HashSet<String>();
        for (ServerAddress cur : hosts) {
            hostsSet.add(cur.toString());
        }

        Set<String> passivesSet = new HashSet<String>();
        for (ServerAddress cur : passives) {
            passivesSet.add(cur.toString());
        }
        return ServerDescription.builder()
                                .address(serverAddress)
                                .type(serverType)
                                .ok(ok)
                                .state(CONNECTED)
                                .hosts(hostsSet)
                                .passives(passivesSet)
                                .setName(setName);
    }
}

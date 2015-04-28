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

package com.mongodb;

import com.mongodb.client.test.CollectionHelper;
import com.mongodb.connection.ServerHelper;
import org.bson.Document;
import org.bson.codecs.DocumentCodec;
import org.junit.After;
import org.junit.Before;

import static com.mongodb.ClusterFixture.getDefaultDatabaseName;
import static com.mongodb.ClusterFixture.getPrimary;

public class FunctionalTest {
    @Before
    public void setUp() {
        CollectionHelper.drop(getNamespace());
    }

    @After
    public void tearDown() {
        CollectionHelper.drop(getNamespace());
        try {
            ServerHelper.checkPool(getPrimary());
        } catch (InterruptedException e) {
            // ignore
        }
    }

    protected String getDatabaseName() {
        return getDefaultDatabaseName();
    }

    protected String getCollectionName() {
        return getClass().getName();
    }

    protected MongoNamespace getNamespace() {
        return new MongoNamespace(getDatabaseName(), getCollectionName());
    }

    protected CollectionHelper<Document> getCollectionHelper() {
        return new CollectionHelper<Document>(new DocumentCodec(), getNamespace());
    }

}

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

package com.mongodb.operation;

import com.mongodb.MongoCredential;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;

import java.util.Arrays;

import static com.mongodb.internal.authentication.NativeAuthenticationHelper.createAuthenticationHash;

final class UserOperationHelper {

    static BsonDocument asCommandDocument(final MongoCredential credential, final boolean readOnly, final String commandName) {
        BsonDocument document = new BsonDocument();
        document.put(commandName, new BsonString(credential.getUserName()));
        document.put("pwd", new BsonString(createAuthenticationHash(credential.getUserName(),
                                                                    credential.getPassword())));
        document.put("digestPassword", BsonBoolean.FALSE);
        document.put("roles", new BsonArray(Arrays.<BsonValue>asList(new BsonString(getRoleName(credential, readOnly)))));
        return document;
    }

    private static String getRoleName(final MongoCredential credential, final boolean readOnly) {
        return credential.getSource().equals("admin")
               ? (readOnly ? "readAnyDatabase" : "root") : (readOnly ? "read" : "dbOwner");
    }

    static BsonDocument asCollectionQueryDocument(final MongoCredential credential) {
        return new BsonDocument("user", new BsonString(credential.getUserName()));
    }

    static BsonDocument asCollectionDocument(final MongoCredential credential, final boolean readOnly) {
        BsonDocument document = asCollectionQueryDocument(credential);
        document.put("pwd", new BsonString(createAuthenticationHash(credential.getUserName(),
                                                                    credential.getPassword())));
        document.put("readOnly", BsonBoolean.valueOf(readOnly));
        return document;
    }

    private UserOperationHelper() {
    }
}

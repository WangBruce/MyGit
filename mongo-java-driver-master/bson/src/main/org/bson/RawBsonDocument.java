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

package org.bson;

import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.BsonValueCodecProvider;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.io.BasicOutputBuffer;
import org.bson.io.ByteBufferBsonInput;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static org.bson.codecs.BsonValueCodecProvider.getClassForBsonType;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;

/**
 * An immutable BSON document that is represented using only the raw bytes.
 *
 * @since 3.0
 */
public class RawBsonDocument extends BsonDocument {
    private static final long serialVersionUID = 5551249268878132972L;
    private static CodecRegistry registry = fromProviders(new BsonValueCodecProvider());

    private final byte[] bytes;

    /**
     * Constructs a new instance with the given byte array.  Note that it does not make a copy of the array, so do not modify it after
     * passing it to this constructor.
     *
     * @param bytes the bytes representing a BSON document.  Note that the byte array is NOT copied, so care must be taken not to modify it
     *              after passing it to this construction, unless of course that is your intention.
     */
    public RawBsonDocument(final byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes can not be null");
        }
        this.bytes = bytes;
    }

    /**
     * Construct a new instance from the given document and codec for the document type.
     *
     * @param document the document to transform
     * @param codec    the codec to facilitate the transformation
     * @param <T>      the BSON type that the codec encodes/decodes
     */
    public <T> RawBsonDocument(final T document, final Codec<T> codec) {
        BasicOutputBuffer buffer = new BasicOutputBuffer();
        BsonBinaryWriter writer = new BsonBinaryWriter(buffer);
        try {
            codec.encode(writer, document, EncoderContext.builder().build());
            this.bytes = buffer.toByteArray();
        } finally {
            writer.close();
        }
    }

    /**
     * Returns a {@code ByteBuf} that wraps the byte array, with the proper byte order.  Any changes made to the returned will be reflected
     * in the underlying byte array owned by this instance.
     *
     * @return a byte buffer that wraps the byte array owned by this instance.
     */
    public ByteBuf getByteBuffer() {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        return new ByteBufNIO(buffer);
    }

    /**
     * Decode this into a document.
     *
     * @param codec the codec to facilitate the transformation
     * @param <T>   the BSON type that the codec encodes/decodes
     * @return the decoded document
     */
    public <T> T decode(final Codec<T> codec) {
        BsonBinaryReader reader = createReader();
        try {
            return codec.decode(reader, DecoderContext.builder().build());
        } finally {
            reader.close();
        }
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("RawBsonDocument instances are immutable");
    }

    @Override
    public BsonValue put(final String key, final BsonValue value) {
        throw new UnsupportedOperationException("RawBsonDocument instances are immutable");
    }

    @Override
    public BsonDocument append(final String key, final BsonValue value) {
        throw new UnsupportedOperationException("RawBsonDocument instances are immutable");
    }

    @Override
    public void putAll(final Map<? extends String, ? extends BsonValue> m) {
        throw new UnsupportedOperationException("RawBsonDocument instances are immutable");
    }

    @Override
    public BsonValue remove(final Object key) {
        throw new UnsupportedOperationException("RawBsonDocument instances are immutable");
    }

    @Override
    public boolean isEmpty() {
        BsonBinaryReader bsonReader = createReader();
        try {
            bsonReader.readStartDocument();
            if (bsonReader.readBsonType() != BsonType.END_OF_DOCUMENT) {
                return false;
            }
            bsonReader.readEndDocument();
        } finally {
            bsonReader.close();
        }

        return true;
    }

    @Override
    public int size() {
        int size = 0;
        BsonBinaryReader bsonReader = createReader();
        try {
            bsonReader.readStartDocument();
            while (bsonReader.readBsonType() != BsonType.END_OF_DOCUMENT) {
                size++;
                bsonReader.readName();
                bsonReader.skipValue();
            }
            bsonReader.readEndDocument();
        } finally {
            bsonReader.close();
        }

        return size;
    }

    @Override
    public Set<Entry<String, BsonValue>> entrySet() {
        return toBsonDocument().entrySet();
    }

    @Override
    public Collection<BsonValue> values() {
        return toBsonDocument().values();
    }

    @Override
    public Set<String> keySet() {
        return toBsonDocument().keySet();
    }

    @Override
    public boolean containsKey(final Object key) {
        if (key == null) {
            throw new IllegalArgumentException("key can not be null");
        }

        BsonBinaryReader bsonReader = createReader();
        try {
            bsonReader.readStartDocument();
            while (bsonReader.readBsonType() != BsonType.END_OF_DOCUMENT) {
                if (bsonReader.readName().equals(key)) {
                    return true;
                }
                bsonReader.skipValue();
            }
            bsonReader.readEndDocument();
        } finally {
            bsonReader.close();
        }

        return false;
    }

    @Override
    public boolean containsValue(final Object value) {
        BsonBinaryReader bsonReader = createReader();
        try {
            bsonReader.readStartDocument();
            while (bsonReader.readBsonType() != BsonType.END_OF_DOCUMENT) {
                bsonReader.skipName();
                if (deserializeBsonValue(bsonReader).equals(value)) {
                    return true;
                }
            }
            bsonReader.readEndDocument();
        } finally {
            bsonReader.close();
        }

        return false;
    }

    @Override
    public BsonValue get(final Object key) {
        if (key == null) {
            throw new IllegalArgumentException("key can not be null");
        }

        BsonBinaryReader bsonReader = createReader();
        try {
            bsonReader.readStartDocument();
            while (bsonReader.readBsonType() != BsonType.END_OF_DOCUMENT) {
                if (bsonReader.readName().equals(key)) {
                    return deserializeBsonValue(bsonReader);
                }
                bsonReader.skipValue();
            }
            bsonReader.readEndDocument();
        } finally {
            bsonReader.close();
        }

        return null;
    }

    @Override
    public boolean equals(final Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    private BsonValue deserializeBsonValue(final BsonBinaryReader bsonReader) {
        return registry.get(getClassForBsonType(bsonReader.getCurrentBsonType())).decode(bsonReader, DecoderContext.builder().build());
    }

    private BsonBinaryReader createReader() {
        return new BsonBinaryReader(new ByteBufferBsonInput(getByteBuffer()));
    }

    private BsonDocument toBsonDocument() {
        BsonBinaryReader bsonReader = createReader();
        try {
            return new BsonDocumentCodec().decode(bsonReader, DecoderContext.builder().build());
        } finally {
            bsonReader.close();
        }
    }
}

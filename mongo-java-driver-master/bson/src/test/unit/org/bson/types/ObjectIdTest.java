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

package org.bson.types;

import org.junit.Test;

import java.util.Date;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ObjectIdTest {
    @Test
    public void testToByteArray() {
        ObjectId objectId = new ObjectId(0x5106FC9A, 0x00BC8237, (short) 0x5581, 0x0036D289);
        assertArrayEquals(new byte[]{81, 6, -4, -102, -68, -126, 55, 85, -127, 54, -46, -119}, objectId.toByteArray());
    }

    @Test
    public void testFromByteArray() {
        ObjectId objectId = new ObjectId(new byte[]{81, 6, -4, -102, -68, -126, 55, 85, -127, 54, -46, -119});
        assertEquals(0x5106FC9A, objectId.getTimestamp());
        assertEquals(0x00BC8237, objectId.getMachineIdentifier());
        assertEquals((short) 0x5581, objectId.getProcessIdentifier());
        assertEquals(0x0036D289, objectId.getCounter());
    }

    @Test
    public void testBytes() {
        ObjectId expected = new ObjectId();
        ObjectId actual = new ObjectId(expected.toByteArray());
        assertEquals(expected, actual);

        byte[] b = new byte[12];
        Random r = new Random(17);
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) (r.nextInt());
        }
        expected = new ObjectId(b);
        assertEquals(expected, new ObjectId(expected.toByteArray()));
        assertEquals("41d91c58988b09375cc1fe9f", expected.toString());
    }

    @Test
    public void testTime() {
        long a = System.currentTimeMillis();
        long b = (new ObjectId()).getDate().getTime();
        assertTrue(Math.abs(b - a) < 3000);
    }

    @Test
    public void testDateCons() {
        Date d = new Date();
        ObjectId a = new ObjectId(d);
        assertEquals(d.getTime() / 1000, a.getDate().getTime() / 1000);
    }

    @Test
    public void testMachineIdentifier() {
        assertTrue(ObjectId.getGeneratedMachineIdentifier() > 0);
        assertEquals(0, ObjectId.getGeneratedMachineIdentifier() & 0xff000000);

        assertEquals(5, new ObjectId(0, 5, (short) 0, 0).getMachineIdentifier());
        assertEquals(0x00ffffff, new ObjectId(0, 0x00ffffff, (short) 0, 0).getMachineIdentifier());
        assertEquals(ObjectId.getGeneratedMachineIdentifier(), new ObjectId().getMachineIdentifier());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowIfMachineIdentifierIsTooLarge() {
        new ObjectId(0, 0x00ffffff + 1, (short) 0, 0);
    }

    @Test
    public void testProcessIdentifier() {
        assertEquals(5, new ObjectId(0, 0, (short) 5, 0).getProcessIdentifier());
        assertEquals(ObjectId.getGeneratedProcessIdentifier(), new ObjectId().getProcessIdentifier());
    }

    @Test
    public void testCounter() {
        assertEquals(new ObjectId().getCounter() + 1, new ObjectId().getCounter());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowIfCounterIsTooLarge() {
        new ObjectId(0, 0, (short) 0, 0x00ffffff + 1);
    }

    @Test
    public void testHexStringConstructor() {
        ObjectId id = new ObjectId();
        assertEquals(id, new ObjectId(id.toHexString()));
    }

    @Test
    public void testCompareTo() {
        assertEquals(-1, new ObjectId(0, 0, (short) 0, 0).compareTo(new ObjectId(1, 0, (short) 0, 0)));
        assertEquals(-1, new ObjectId(0, 0, (short) 0, 0).compareTo(new ObjectId(0, 1, (short) 0, 0)));
        assertEquals(-1, new ObjectId(0, 0, (short) 0, 0).compareTo(new ObjectId(0, 0, (short) 1, 0)));
        assertEquals(-1, new ObjectId(0, 0, (short) 0, 0).compareTo(new ObjectId(0, 0, (short) 0, 1)));
        assertEquals(0, new ObjectId(0, 0, (short) 0, 0).compareTo(new ObjectId(0, 0, (short) 0, 0)));
        assertEquals(1, new ObjectId(1, 0, (short) 0, 0).compareTo(new ObjectId(0, 0, (short) 0, 0)));
        assertEquals(1, new ObjectId(0, 1, (short) 0, 0).compareTo(new ObjectId(0, 0, (short) 0, 0)));
        assertEquals(1, new ObjectId(0, 0, (short) 1, 0).compareTo(new ObjectId(0, 0, (short) 0, 0)));
        assertEquals(1, new ObjectId(0, 0, (short) 0, 1).compareTo(new ObjectId(0, 0, (short) 0, 0)));
    }

    @Test
    public void testToHexString() {
        assertEquals("000000000000000000000000", new ObjectId(0, 0, (short) 0, 0).toHexString());
        assertEquals("7fffffff007fff7fff007fff",
                     new ObjectId(Integer.MAX_VALUE, Short.MAX_VALUE, Short.MAX_VALUE, Short.MAX_VALUE).toHexString());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testDeprecatedMethods() {

        ObjectId id = new ObjectId();
        assertEquals(id.getTimestamp(), id.getTimeSecond());
        assertEquals(id.getDate().getTime(), id.getTime());
        assertEquals(id.toHexString(), id.toStringMongod());
        assertArrayEquals(new byte[]{0x12, 0x34, 0x56, 0x78, 0x43, 0x21, 0xffffff87, 0x65, 0x74, 0xffffff92, 0xffffff87, 0x56},
                          new ObjectId(0x12345678, 0x43218765, 0x74928756).toByteArray());
    }

    // Got these values from 2.12.0 driver.  This test is ensuring that we properly round-trip old and new format ObjectIds.
    @Test
    public void testCreateFromLegacy() {
        assertArrayEquals(new byte[]{82, 23, -82, -78, -80, -58, -95, -92, -75, -38, 118, -16},
                          ObjectId.createFromLegacyFormat(1377283762, -1329159772, -1243973904).toByteArray());
    }
}


/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.vmware.xenon.jeeimpl.reflect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import static com.vmware.xenon.jeeimpl.reflect.TypeConverter.convertToPrimitiveOrEnum;

import org.junit.Test;

public class TypeConverterTest {

    enum TestEnum {
        VALUE_1, VALUE_2
    }

    @Test
    public void testConvert() throws Exception {
        assertEquals(5, convertToPrimitiveOrEnum("5", Integer.class));
        assertEquals(5L, convertToPrimitiveOrEnum("5", Long.class));
        assertEquals((short) 5, convertToPrimitiveOrEnum("5", Short.class));
        assertEquals(5.0d, convertToPrimitiveOrEnum("5", Double.class));
        assertEquals(5.0f, convertToPrimitiveOrEnum("5", Float.class));
        assertEquals(TestEnum.VALUE_1, convertToPrimitiveOrEnum("VALUE_1", TestEnum.class));
        assertEquals(TestEnum.VALUE_2, convertToPrimitiveOrEnum("VALUE_2", TestEnum.class));
        assertEquals("SomeString", convertToPrimitiveOrEnum("SomeString", String.class));
        assertTrue((Boolean) convertToPrimitiveOrEnum("true", Boolean.class));
    }

}

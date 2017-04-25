/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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

package com.vmware.xenon.jee.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Properties;

import org.junit.Test;

public class PropertyManagerTest {

    @Test
    public void testPopulatingViaFile() {
        PropertyManager.initializeResource("application.properties");

        assertEquals("prop_value_1", PropertyManager.getProperty("PROP_KEY1"));
        assertEquals("prop_value_2", PropertyManager.getProperty("PROP_KEY2"));
        System.setProperty("PROP_KEY2", "new_prop_value_2");
        assertEquals("new_prop_value_2", PropertyManager.getProperty("PROP_KEY2"));

    }

    @Test
    public void testPopulatingViaProperties() {
        String key1 = "KEY1";
        String key2 = "KEY2";
        String key3 = "KEY3";
        String value1 = "VALUE1";
        String value2 = "VALUE2";
        Properties p = new Properties();
        p.setProperty(key1, value1);
        p.setProperty(key2, value2);

        PropertyManager.initializeProperties(p);

        assertEquals(value1, PropertyManager.getProperty(key1));
        assertEquals(value2, PropertyManager.getProperty(key2));
        assertNull(PropertyManager.getProperty(key3));

        String newValue1 = "NEW_VALUE1";

        System.setProperty(key1, newValue1);
        assertEquals(newValue1, PropertyManager.getProperty(key1));
    }

}

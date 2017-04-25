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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.junit.Test;

import com.vmware.xenon.common.BasicTestCase;

public class LRUCacheTest extends BasicTestCase {

    private LRUCache<Integer, String> testClass = new LRUCache<>(2);

    @Test
    public void testLRUCache() throws Exception {
        this.host.setStressTest(true);
        this.testClass.put(1, "One");
        assertEquals(1, this.testClass.size());
        this.testClass.put(2, "Two");
        assertEquals(2, this.testClass.size());

        // test overflow to weak references
        this.testClass.put(3, "Three");
        assertEquals(3, this.testClass.size());

        //verify that key one is overflown
        Set<Integer> keys = this.testClass.keySet();
        assertTrue(keys.contains(2));
        assertTrue(keys.contains(3));
        assertFalse(keys.contains(1));

        for (int i = 0; i < 1000000; i++) {
            this.testClass.put(i, "Value of " + i);
        }

        int size = this.testClass.size();
        System.out.println("Size of cache is " + this.testClass.size());
        System.gc();
        System.gc();
        //This is not guaranteed, so commenting out. Works consistently in windows but not in other sys
        //        Thread.sleep(100);
        Runtime.getRuntime().gc();
        Runtime.getRuntime().gc();
        this.host.waitForGC();
        System.out.println("Size after GC is " + this.testClass.size());
        assertTrue(size > this.testClass.size());
    }
}

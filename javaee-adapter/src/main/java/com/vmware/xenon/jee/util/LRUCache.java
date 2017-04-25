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

package com.vmware.xenon.jee.util;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**

 * <p>
 * Provides normal functionality of a LRUCache with a fixed capacity and tries to retain older entries if memory is available.
 * <p>
 * Note that, under normal conditions when JVM has free memory, this cache overflows  than the desired capacity.
 * Entries overflowing desired capacity are stored as weak references, hence when JVM is in memory pressure (or GC runs),
 * the extra elements are garbage collected.
 * <p>
 * Please note that, weak references are leveraged only for key based operations and not for multi-value operations like keySet()
 */
public class LRUCache<K, V> extends LinkedHashMap<K, V> {

    static final long serialVersionUID = 1;

    private transient WeakHashMap<K, V> goodToHaveCache;

    private final int capacity;

    public LRUCache(int capacity) {
        super(capacity + 1, 1.0f, true);
        this.capacity = capacity;
        this.goodToHaveCache = new WeakHashMap<>();
    }

    @Override
    public void clear() {
        super.clear();
        this.goodToHaveCache.clear();
    }

    @Override
    public V get(Object key) {
        return super.get(key) == null ? this.goodToHaveCache.get(key) : super.get(key);
    }

    @Override
    public V remove(Object key) {
        V remove = super.remove(key);
        if (remove == null) {
            remove = this.goodToHaveCache.remove(key);
        }
        return remove;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> entry) {
        boolean shouldRemove = size() > this.capacity;
        if (shouldRemove) {
            this.goodToHaveCache.put(entry.getKey(), entry.getValue());
        }
        return shouldRemove;
    }

    @Override
    public int size() {
        return super.size() + this.goodToHaveCache.size();
    }

    private void readObject(java.io.ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        this.goodToHaveCache = new WeakHashMap<>();
    }
}

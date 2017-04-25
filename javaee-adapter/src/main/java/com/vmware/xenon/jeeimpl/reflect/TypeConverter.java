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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Converts object to primitive type if possible
 */
public class TypeConverter {

    private static Map<Class<?>, Function<String, Object>> primitiveConverters;

    static {
        primitiveConverters = new HashMap<>();
        primitiveConverters.put(int.class, Integer::parseInt);
        primitiveConverters.put(Integer.class, Integer::parseInt);
        primitiveConverters.put(long.class, Long::parseLong);
        primitiveConverters.put(Long.class, Long::parseLong);
        primitiveConverters.put(boolean.class, Boolean::parseBoolean);
        primitiveConverters.put(Boolean.class, Boolean::parseBoolean);
        primitiveConverters.put(byte.class, Byte::parseByte);
        primitiveConverters.put(Byte.class, Byte::parseByte);
        primitiveConverters.put(short.class, Short::parseShort);
        primitiveConverters.put(Short.class, Short::parseShort);
        primitiveConverters.put(float.class, Float::parseFloat);
        primitiveConverters.put(Float.class, Float::parseFloat);
        primitiveConverters.put(double.class, Double::parseDouble);
        primitiveConverters.put(Double.class, Double::parseDouble);
    }

    @SuppressWarnings("unchecked")
    public static Object asCollection(Object input, Class<?> collectionType) {
        try {
            Object col = null;
            if (collectionType.isInterface()) {
                if (java.util.List.class.isAssignableFrom(collectionType)) {
                    col = new ArrayList<>();
                } else if (java.util.Set.class.isAssignableFrom(collectionType)) {
                    col = new HashSet<>();
                } else if (java.util.SortedSet.class.isAssignableFrom(collectionType)) {
                    col = new TreeSet<>();
                } else if (java.util.Collection.class.isAssignableFrom(collectionType)) {
                    col = new ArrayList<>();
                }
            } else {
                col = collectionType.newInstance();
            }

            if (col instanceof Collection) {
                ((Collection<Object>) col).add(input);
                return col;
            }
        } catch (IllegalAccessException | InstantiationException e) {
           // not a collection type. return input
        }
        return input;
    }

    @SuppressWarnings("unchecked")
    public static Object convertToPrimitiveOrEnum(String obj, Class<?> tgtType) {
        if (tgtType.isEnum()) {
            return Enum.valueOf((Class<Enum>) tgtType, obj);
        }
        return primitiveConverters.getOrDefault(tgtType, s -> s).apply(obj);
    }

}

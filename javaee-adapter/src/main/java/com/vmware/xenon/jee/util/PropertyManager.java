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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**

 */
public class PropertyManager {

    private static Properties properties = new Properties();

    public static String getProperty(String key) {
        String property = System.getProperty(key);
        return property == null ? properties.getProperty(key) : property;
    }

    public static String getProperty(String key, String defaultValue) {
        String property = System.getProperty(key);
        property = (property == null ? properties.getProperty(key) : property);
        return property == null ? defaultValue : property;
    }

    /**
     * Note that, if property key is already present, it will be superseded
     * This is not thread safe. Intended to be used at start-up only once.
     *
     * @param filePath resource present outside of the jar typically
     */
    public static void initializeFile(String filePath) {
        Path path = Paths.get(filePath);
        if (Files.exists(path)) {
            try (InputStream input = new FileInputStream(filePath)) {
                properties.load(input);
            } catch (IOException e) {
                throw new RuntimeException("Unable to load file " + filePath, e);
            }
        }
    }

    /**
     * Note that, if property key is already present, it will be superseded
     * <p>
     * This is not thread safe. Intended to be used at start-up only once.
     *
     * @param newProps an instance of java.util.Properties
     */
    public static void initializeProperties(Properties newProps) {
        properties.putAll(newProps);
    }

    /**
     * Note that, if property key is already present, it will be superseded
     * This is not thread safe. Intended to be used at start-up only once.
     *
     * @param streamPath resource present with in jar typically
     */
    public static void initializeResource(String streamPath) {
        try (InputStream input = PropertyManager.class.getClassLoader()
                .getResourceAsStream(streamPath)) {
            properties.load(input);
        } catch (IOException e) {
            throw new RuntimeException("Unable to load file " + streamPath, e);
        }
    }

}

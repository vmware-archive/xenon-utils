/*
 * Copyright (c) 2014-2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.xenon.swagger;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.swagger.models.Model;
import io.swagger.models.ModelImpl;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.BinaryProperty;
import io.swagger.models.properties.BooleanProperty;
import io.swagger.models.properties.DateTimeProperty;
import io.swagger.models.properties.DoubleProperty;
import io.swagger.models.properties.LongProperty;
import io.swagger.models.properties.MapProperty;
import io.swagger.models.properties.ObjectProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import io.swagger.models.properties.StringProperty;
import io.swagger.models.properties.StringProperty.Format;

import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;

/**
 * Aggregates and indexes ServiceDocumentDescription's by their kind.
 */
class ModelRegistry {

    private static final Logger logger = Logger.getLogger(ModelRegistry.class.getName());

    private final TreeMap<String, Model> byKind;
    private Set<String> stripPackagePrefixes;

    public ModelRegistry() {
        this.byKind = new TreeMap<>();
    }

    public Set<String> getStripPackagePrefixes() {
        return this.stripPackagePrefixes;
    }

    public void setStripPackagePrefixes(Set<String> stripPackagePrefixes) {
        this.stripPackagePrefixes = stripPackagePrefixes;
    }

    public ModelImpl getModel(ServiceDocument template) {
        String kind = getStrippedKind(template.documentKind);
        ModelImpl model = (ModelImpl) this.byKind.get(kind);

        if (model == null) {
            model = load(template.documentDescription.propertyDescriptions.entrySet());
            model.setName(getStrippedKind(template.documentKind));
            this.byKind.put(kind, model);
        }

        return model;
    }

    public ModelImpl getModel(PropertyDescription desc) {
        String kind = getStrippedKind(desc.kind);
        ModelImpl model = (ModelImpl) this.byKind.get(kind);

        if (model == null) {
            model = load(desc.fieldDescriptions.entrySet());
            model.setName(kind);
            this.byKind.put(kind, model);
        }

        return model;
    }

    private ModelImpl load(Collection<Entry<String, PropertyDescription>> desc) {
        ModelImpl res = new ModelImpl();

        for (Entry<String, PropertyDescription> e : desc) {
            String name = e.getKey();
            PropertyDescription pd = e.getValue();
            if (pd.usageOptions.contains(PropertyUsageOption.INFRASTRUCTURE)
                    || pd.usageOptions.contains(PropertyUsageOption.SERVICE_USE)) {
                continue;
            }
            Property property = makeProperty(pd);
            property.description(pd.propertyDocumentation);
            property.setExample(pd.exampleValue);
            res.addProperty(name, property);
        }

        return res;
    }

    private Property makeProperty(PropertyDescription pd) {
        switch (pd.typeName) {
        case BOOLEAN:
            return new BooleanProperty();
        case BYTES:
            return new BinaryProperty();
        case COLLECTION:
            return new ArrayProperty(makeProperty(pd.elementDescription));
        case DATE:
            return new DateTimeProperty();
        case DOUBLE:
            return new DoubleProperty();
        case ENUM:
            StringProperty prop = new StringProperty();
            if (pd.enumValues != null) {
                prop._enum(Arrays.asList(pd.enumValues));
            }
            return prop;
        case InternetAddressV4:
            return new StringProperty();
        case InternetAddressV6:
            return new StringProperty();
        case LONG:
            return new LongProperty();
        case MAP:
            return new MapProperty(makeProperty(pd.elementDescription));
        case PODO:
            // special case for java.lang.Object
            if (Objects.equals(pd.kind, com.vmware.xenon.common.Utils.buildKind(Object.class))) {
                return new ObjectProperty();
            }
            return refProperty(pd);
        case STRING:
            return new StringProperty();
        case URI:
            return new StringProperty(Format.URI);
        default:
            throw new IllegalStateException("unknown type " + pd.typeName);
        }
    }

    private RefProperty refProperty(PropertyDescription pd) {
        String kind = getStrippedKind(pd.kind);
        ModelImpl model = (ModelImpl) this.byKind.get(kind);
        if (model == null) {
            model = load(pd.fieldDescriptions.entrySet());
            model.setName(kind);
            this.byKind.put(kind, model);
        }

        return new RefProperty(kind);
    }

    public Map<String, Model> getDefinitions() {
        return this.byKind;
    }

    private Map<String, String> strippedNames = new HashMap<>();

    private String getStrippedKind(String documentKind) {
        // look for and remove certain prefixes from documentKind
        String name = this.strippedNames.get(documentKind);
        if (name != null) {
            return name;
        }
        name = documentKind;
        for (String prefix : this.stripPackagePrefixes) {
            if (documentKind.startsWith(prefix)) {
                name = documentKind.substring(prefix.length());
                if (this.strippedNames.values().contains(name)) {
                    // collision - revert to original name
                    logger.log(Level.WARNING, "Conflict in simplified swagger document names, cannot simplify: " + documentKind);
                    name = documentKind;
                }
            }
        }
        this.strippedNames.put(documentKind, name);
        return name;
    }
}

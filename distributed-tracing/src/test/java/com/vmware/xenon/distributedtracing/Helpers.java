/*
 * Copyright (c) 2017-2017 VMware, Inc. All Rights Reserved.
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

package com.vmware.xenon.distributedtracing;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.Assert.assertNotNull;

import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.opentracing.TracerFactory;
import io.opentracing.Tracer;
import io.opentracing.mock.MockTracer;
import io.opentracing.util.ThreadLocalActiveSpanSource;
import org.junit.rules.TemporaryFolder;

public class Helpers {


    public static void runHost(Consumer<TestTracingHost> consumer) throws Throwable {
        TestTracingHost h = new TestTracingHost();
        String bindAddress = "127.0.0.1";
        String hostId = UUID.randomUUID().toString();
        try (CloseableFolder folder = new CloseableFolder()) {
            String[] args = {
                    "--port=0",
                    "--sandbox=" + folder.getRoot().getAbsolutePath(),
                    "--bindAddress=" + bindAddress,
                    "--id=" + hostId
            };
            try {
                h.initialize(args);
                h.start();
                consumer.accept(h);
            } finally {
                assertNotNull(h);
                h.stop();
            }
        }
    }

    public static void runHost() throws Throwable {
        runHost((TestTracingHost serviceHost) -> { });
    }

    public static class MockTracerFactory extends TracerFactory {
        MockTracer tracer = new MockTracer(new ThreadLocalActiveSpanSource(), MockTracer.Propagator.TEXT_MAP);

        @Override
        public synchronized Tracer create(ServiceHost host) {
            return tracer;
        }
    }

    // TODO: restore the original factory after tests.
    public static MockTracer injectTracer() {
        TracerFactory.factory = new MockTracerFactory();
        return (MockTracer) TracerFactory.factory.create(null);
    }

    private static class CloseableFolder implements AutoCloseable {
        public final TemporaryFolder folder;

        public CloseableFolder() throws Exception {
            this.folder = new TemporaryFolder();
            this.folder.create();
        }

        @Override
        public void close() throws IOException {
            this.folder.delete();
        }

        public File getRoot() {
            return this.folder.getRoot();
        }
    }

}

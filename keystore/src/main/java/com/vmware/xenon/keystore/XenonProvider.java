package com.vmware.xenon.keystore;

import java.io.IOException;
import java.io.InputStream;
import java.security.Provider;

import com.vmware.xenon.common.ServiceClient;

class XenonProvider extends Provider {
    public static final String KEYSTORE_TYPE = "xenon";

    final class LoaderInputStream extends InputStream {
        @Override
        public int read() throws IOException {
            throw new UnsupportedOperationException("this stream is not meant for reading");
        }

        public String getName() {
            return name;
        }

        public ServiceClient getServiceClient() {
            return client;
        }
    }

    private final String name;
    private final ServiceClient client;

    public XenonProvider(String name, ServiceClient client) {
        super("name", 1.0, name + "/" + client);
        this.put("KeyStore." + KEYSTORE_TYPE, XenonKeystoreSpi.class.getName());
        this.name = name;
        this.client = client;
    }

    public InputStream getLoaderStream() {
        return new LoaderInputStream();
    }
}

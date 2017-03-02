package com.vmware.xenon.keystore;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.concurrent.TimeUnit;

import com.vmware.xenon.common.ServiceClient;

public class XenonKeystoreBuilder {
    private final String keystoreName;

    private ServiceClient client;

    private XenonKeystoreBuilder(String keystoreName) {
        this.keystoreName = keystoreName;
    }

    /**
     * Many keystores are supported as part
     * @param keystoreName keystoreName of the keystore
     * @return
     */
    public static XenonKeystoreBuilder create(String keystoreName) {
        return new XenonKeystoreBuilder(keystoreName);
    }

    /**
     * Client used to interact with xenon cluster.
     * @param client
     * @return
     */
    public XenonKeystoreBuilder setClient(ServiceClient client) {
        this.client = client;
        return this;
    }

    /**
     * Timeout for blocking operations to xenon.
     * @param timeout
     * @param unit
     * @return
     */
    public XenonKeystoreBuilder setTimeout(long timeout, TimeUnit unit) {
        // TODO
        return this;
    }

    public XenonKeystoreBuilder setCacheTtl(long timeout, TimeUnit unit) {
        // TODO
        return this;
    }

    public KeyStore build() {
        try {
            XenonProvider provider = new XenonProvider(keystoreName, client);
            KeyStore ks = KeyStore.getInstance(XenonProvider.KEYSTORE_TYPE, provider);

            // load() initializes the keystore
            ks.load(provider.getLoaderStream(), null);
            return ks;
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}

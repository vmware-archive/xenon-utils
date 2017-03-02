package com.vmware.xenon.keystore;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.vmware.xenon.common.ServiceClient;

public class KeystoreEntryServiceTest {

    @Test
    public void example() throws KeyStoreException {
        ServiceClient client = null;

        KeyStore app = XenonKeystoreBuilder.create("my-app")
                .setClient(client)
                .setTimeout(30, TimeUnit.SECONDS)
                .setCacheTtl(10, TimeUnit.SECONDS)
                .build();

        Certificate[] chain = null;
        byte[] key = null;
        app.setKeyEntry("key", key, chain);

        Certificate cert = null;
        app.setCertificateEntry("cert", cert);
    }
}
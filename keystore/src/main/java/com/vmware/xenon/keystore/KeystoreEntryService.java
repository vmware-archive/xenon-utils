package com.vmware.xenon.keystore;

import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;

public class KeystoreEntryService extends StatefulService {
    public static final String FACTORY_LINK = "/core/keystore";

    public enum EntryType {
        CERTIFICATE, KEY
    }

    public static class KeystoreEntryState extends ServiceDocument {
        public String name;
        public String alias;
        public EntryType type;
        public byte[] data;
    }

    public KeystoreEntryService() {
        super(KeystoreEntryState.class);
    }
}

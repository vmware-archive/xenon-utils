package com.vmware.xenon;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.vmware.xenon.QuickstartHost.startHost;
import static junit.framework.TestCase.assertEquals;

import java.net.URI;
import java.nio.file.Paths;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestNodeGroupManager;
import com.vmware.xenon.common.test.TestRequestSender;

/**
 * Created by toddc on 10/5/17.
 */
public class ProxyServiceTest {
    private ServiceHost clientHost;
    private TestNodeGroupManager nodeGroupManager;
    private TemporaryFolder folder = new TemporaryFolder();
    private TestRequestSender sender;
    private URI proxyUri;

    static int mockPort = 8990;
    public String mockUri = "http://localhost:" + mockPort;
    public String xenonUri = "http://localhost:" + (mockPort + 1);

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(mockPort);

    /**
     * Start up a multi-node emulated cluster within a single JVM for all tests.
     * @throws Throwable - exception encountered while starting up
     */
    @Before
    public void startHosts() throws Throwable {
        int numNodes = 3;
        this.folder.create();
        ServiceHost.Arguments args = new ServiceHost.Arguments();
        QuickstartHost[] hostList = new QuickstartHost[numNodes];
        for (int i = 0; i < numNodes; i++) {
            args.id = "host" + i;   // human readable name instead of GUID
            args.sandbox = Paths.get(this.folder.getRoot().toString() + i);
            args.port = mockPort + 1 + i;
            hostList[i] = startHost(new String[0], args);
        }

        TestNodeGroupManager nodeGroup = new TestNodeGroupManager();
        for (QuickstartHost host : hostList) {
            nodeGroup.addHost(host);
        }

        // When you launch a cluster of nodes, they initiate a protocol to synchronize. If you start making changes
        // before the nodes are in sync, then your changes will trigger additional synchronization, which will take
        // longer for startup to complete.
        nodeGroup.waitForConvergence();
        this.clientHost = nodeGroup.getHost();  // grabs a random one of the hosts.
        this.proxyUri = UriUtils.buildFactoryUri(this.clientHost, ProxyService.class);
        this.nodeGroupManager = nodeGroup;
        sender = new TestRequestSender(this.clientHost);
    }

    @After
    public void cleanup() {
        this.nodeGroupManager.getAllHosts().forEach((h) -> h.stop());
        this.folder.delete();
    }

    /**
     * create a request have the proxy get a url
     * @return
     */
    private String xenonProxyGet(String url) {
        ProxyService.Proxy proxy = new ProxyService.Proxy();
        proxy.url = url;
        StringBuilder result = new StringBuilder();
        Operation op = Operation.createPost(URI.create(xenonUri + "/proxy"))
                .setBody(proxy)
                .forceRemote()
                .nestCompletion((o,e) -> {
                    result.append(o.getBodyRaw().toString());
                    o.complete();
                });
        sender.sendAndWait(op);
        return result.toString();
    }

    private void mockGet(String resource, String response) {
        stubFor(get(urlEqualTo(resource))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(response)));
    }

    /**
     * Test the proxy
     */
    @Test
    public void testProxy() {
        String path = "/test";
        String testString = "Test Test";

        mockGet(path, testString);
        String result = xenonProxyGet(mockUri + path);

        assertEquals(result, testString);
    }
}

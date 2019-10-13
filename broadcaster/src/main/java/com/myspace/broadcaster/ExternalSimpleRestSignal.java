package com.myspace.broadcaster;

import java.net.URI;
import java.util.HashMap;

import com.google.common.net.HttpHeaders;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.drools.core.process.instance.WorkItemHandler;
import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExternalSimpleRestSignal implements WorkItemHandler {
    private static final Logger logger = LoggerFactory.getLogger(ExternalSimpleRestSignal.class);

    private String host = "http://localhost:8080/kie-server";
    private String userName = "wbadmin";
    private String password = "wbadmin";
    private String deploymentUnitId = "receiver_1.0.0-SNAPSHOT";
    private String signalRef = "child_signal";

    private final static String SERVICE_URI = "%s/services/rest/server/containers/%s/processes/instances/signal/%s";
    private final static String PARAM_USERNAME = "userName";
    private final static String PARAM_PASSWORD = "password";
    private final static String PARAM_HOST = "host";
    private final static String PARAM_DEPLOYMENT_ID = "deploymentId";
    private final static String PARAM_SIGNAL_REF = "signalRef";

    public ExternalSimpleRestSignal() {
    }

    public ExternalSimpleRestSignal(String host, String deploymentId, String signalRef, String user,
            String password) {
        super();
        this.host = host;
        this.deploymentUnitId = deploymentId;
        this.signalRef = signalRef;
        this.userName = user;
        this.password = password;
    }

    @Override
    public void executeWorkItem(WorkItem workItem, WorkItemManager manager) {
        logger.info("Sending external signal through custom work item handler");
        logger.info("parameters:" + workItem.getParameters());

        // Read parameters
        String u = (String) workItem.getParameter(PARAM_USERNAME);
        String p = (String) workItem.getParameter(PARAM_PASSWORD);
        String host = (String) workItem.getParameter(PARAM_HOST);
        String containerId = (String) workItem.getParameter(PARAM_DEPLOYMENT_ID);
        String signalRef = (String) workItem.getParameter(PARAM_SIGNAL_REF);

        // If parameters read from workItem were not set, use the default or
        // constructor.
        if (u == null || p == null) {
            u = this.userName;
            p = this.password;
        }

        if (host == null) {
            host = this.host;
        }

        if (containerId == null) {
            containerId = this.deploymentUnitId;
        }

        if (signalRef == null) {
            signalRef = this.signalRef;
        }
        
        String urlStr = String.format(SERVICE_URI, host, containerId, signalRef);
        logger.info("Singal target: " + urlStr);


        RequestConfig config = RequestConfig.custom().setSocketTimeout(60000).setConnectTimeout(60000)
                .setConnectionRequestTimeout(60000).build();

        HttpClientBuilder clientBuilder = HttpClientBuilder.create().setDefaultRequestConfig(config);

        HttpClient httpClient = clientBuilder.build();

        RequestBuilder builder = RequestBuilder.post().setUri(urlStr);
        builder.addHeader(HttpHeaders.ACCEPT, "application/json");
        builder.addHeader(HttpHeaders.CONTENT_TYPE, "application/json");

        URI requestUri = builder.getUri();

        HttpHost targetHost = new HttpHost(requestUri.getHost(), requestUri.getPort(), requestUri.getScheme());

        // Create AuthCache instance and add it: so that HttpClient thinks that it has
        // already queried (as per the HTTP spec)
        // - generate BASIC scheme object and add it to the local auth cache
        AuthCache authCache = new BasicAuthCache();
        BasicScheme basicAuth = new BasicScheme();
        authCache.put(targetHost, basicAuth);

        // - add AuthCache to the execution context:
        HttpClientContext clientContext = HttpClientContext.create();
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
                // specify host and port, since that is safer/more secure
                new AuthScope(requestUri.getHost(), requestUri.getPort(), AuthScope.ANY_REALM),
                new UsernamePasswordCredentials(u, p));
        clientContext.setCredentialsProvider(credsProvider);
        clientContext.setAuthCache(authCache);

        // - execute request
        HttpUriRequest request = builder.build();
        try {
            logger.info("... CONNECTING AND SENDING SIGNAL ");
            httpClient.execute(targetHost, request, clientContext);
            logger.info("... SIGNAL SENT ");
        } catch (Exception e) {
            logger.error("Unable to send", e);
            throw new RuntimeException("Could not execute request with preemptive authentication ["
                    + request.getMethod() + "] " + request.getURI(), e);
        } finally {
            manager.completeWorkItem(workItem.getId(), new HashMap<String, Object>());
        }
    }

    @Override
    public void abortWorkItem(WorkItem workItem, WorkItemManager manager) {
        manager.abortWorkItem(workItem.getId());
    }

}
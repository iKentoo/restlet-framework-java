/*
 * Copyright 2005-2008 Noelios Consulting.
 * 
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License (the "License"). You may not use this file except in
 * compliance with the License.
 * 
 * You can obtain a copy of the license at
 * http://www.opensource.org/licenses/cddl1.txt See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL HEADER in each file and
 * include the License file at http://www.opensource.org/licenses/cddl1.txt If
 * applicable, add the following below this CDDL HEADER, with the fields
 * enclosed by brackets "[]" replaced with your own identifying information:
 * Portions Copyright [yyyy] [name of copyright owner]
 */

package com.noelios.restlet.ext.jetty;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

import org.mortbay.jetty.HttpConnection;
import org.restlet.Server;
import org.restlet.data.Parameter;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.util.Series;

import com.noelios.restlet.http.HttpServerCall;

/**
 * Call that is used by the Jetty 6 HTTP server connector.
 * 
 * @author Jerome Louvel (contact@noelios.com)
 */
public class JettyCall extends HttpServerCall {
    /** The wrapped Jetty HTTP connection. */
    private volatile HttpConnection connection;

    /** Indicates if the request headers were parsed and added. */
    private volatile boolean requestHeadersAdded;

    /**
     * Constructor.
     * 
     * @param server
     *                The parent server.
     * @param connection
     *                The wrapped Jetty HTTP connection.
     */
    public JettyCall(Server server, HttpConnection connection) {
        super(server);
        this.connection = connection;
        this.requestHeadersAdded = false;
    }

    @Override
    public String getClientAddress() {
        return getConnection().getRequest().getRemoteAddr();
    }

    @Override
    public int getClientPort() {
        return getConnection().getRequest().getRemotePort();
    }

    /**
     * Returns the wrapped Jetty HTTP connection.
     * 
     * @return The wrapped Jetty HTTP connection.
     */
    public HttpConnection getConnection() {
        return this.connection;
    }

    /**
     * Returns the request method.
     * 
     * @return The request method.
     */
    @Override
    public String getMethod() {
        return getConnection().getRequest().getMethod();
    }

    @Override
    public ReadableByteChannel getRequestEntityChannel(long size) {
        return null;
    }

    @Override
    public InputStream getRequestEntityStream(long size) {
        try {
            return getConnection().getRequest().getInputStream();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public ReadableByteChannel getRequestHeadChannel() {
        // Not available
        return null;
    }

    /**
     * Returns the list of request headers.
     * 
     * @return The list of request headers.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Series<Parameter> getRequestHeaders() {
        Series<Parameter> result = super.getRequestHeaders();

        if (!requestHeadersAdded) {
            // Copy the headers from the request object
            String headerName;
            String headerValue;
            for (Enumeration<String> names = getConnection().getRequestFields()
                    .getFieldNames(); names.hasMoreElements();) {
                headerName = names.nextElement();
                for (Enumeration<String> values = getConnection()
                        .getRequestFields().getValues(headerName); values
                        .hasMoreElements();) {
                    headerValue = values.nextElement();
                    result.add(new Parameter(headerName, headerValue));
                }
            }

            requestHeadersAdded = true;
        }

        return result;
    }

    @Override
    public InputStream getRequestHeadStream() {
        // Not available
        return null;
    }

    /**
     * Returns the URI on the request line (most like a relative reference, but
     * not necessarily).
     * 
     * @return The URI on the request line.
     */
    @Override
    public String getRequestUri() {
        return getConnection().getRequest().getUri().toString();
    }

    /**
     * Returns the response channel if it exists.
     * 
     * @return The response channel if it exists.
     */
    @Override
    public WritableByteChannel getResponseEntityChannel() {
        return null;
    }

    /**
     * Returns the response stream if it exists.
     * 
     * @return The response stream if it exists.
     */
    @Override
    public OutputStream getResponseEntityStream() {
        try {
            return getConnection().getResponse().getOutputStream();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Returns the response address.<br>
     * Corresponds to the IP address of the responding server.
     * 
     * @return The response address.
     */
    @Override
    public String getServerAddress() {
        return getConnection().getRequest().getLocalAddr();
    }

    /**
     * Indicates if the request was made using a confidential mean.<br>
     * 
     * @return True if the request was made using a confidential mean.<br>
     */
    @Override
    public boolean isConfidential() {
        return getConnection().getRequest().isSecure();
    }

    @Override
    public List<Certificate> getSslClientCertificates() {
        Certificate[] certificateArray = (Certificate[]) getConnection()
                .getRequest().getAttribute(
                        "javax.servlet.request.X509Certificate");
        if (certificateArray != null) {
            return Arrays.asList(certificateArray);
        } else {
            return null;
        }
    }

    @Override
    public String getSslCipherSuite() {
        return (String) getConnection().getRequest().getAttribute(
                "javax.servlet.request.cipher_suite");
    }

    @Override
    public Integer getSslKeySize() {
        Integer keySize = (Integer) getConnection().getRequest().getAttribute(
                "javax.servlet.request.key_size");
        if (keySize == null) {
            keySize = super.getSslKeySize();
        }
        return keySize;
    }

    @Override
    public void sendResponse(Response response) throws IOException {
        // Add call headers
        Parameter header;
        for (Iterator<Parameter> iter = getResponseHeaders().iterator(); iter
                .hasNext();) {
            header = iter.next();
            getConnection().getResponse().addHeader(header.getName(),
                    header.getValue());
        }

        // Set the status code in the response. We do this after adding the
        // headers because when we have to rely on the 'sendError' method,
        // the Servlet containers are expected to commit their response.
        if (Status.isError(getStatusCode()) && (response.getEntity() == null)) {
            try {
                getConnection().getResponse().sendError(getStatusCode(),
                        getReasonPhrase());
            } catch (IOException ioe) {
                getLogger().log(Level.WARNING,
                        "Unable to set the response error status", ioe);
            }
        } else {
            // Send the response entity
            getConnection().getResponse().setStatus(getStatusCode());
            super.sendResponse(response);
        }

    }

    @Override
    public void complete() {
        try {
            // Fully complete and commit the response
            this.connection.completeResponse();
            this.connection.commitResponse(true);
        } catch (IOException ex) {
            getLogger().log(Level.WARNING,
                    "Unable to complete or commit the response", ex);
        }
    }

}

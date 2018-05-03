/* START COPY NOTICE
 * MIT License
 * Copyright (c) 2018 Bozeman Pass, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * END COPY NOTICE */

package com.bozemanpass.newrelic.ldap.util;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;

public class DummySSLSocketFactory extends SSLSocketFactory {
    private SSLSocketFactory factory;

    public DummySSLSocketFactory() {
        try {
            SSLContext sslcontext = SSLContext.getInstance("TLS");
            sslcontext.init(null,
                    new TrustManager[]{new DummyTrustManager()},
                    null);
            factory = (SSLSocketFactory) sslcontext.getSocketFactory();
        } catch (Exception ex) {
            // ignore
        }
    }

    public static SocketFactory getDefault() {
        return new DummySSLSocketFactory();
    }

    private Socket prepSocket(Socket s) throws SocketException {
        //DO ANY CFG STUFF HERE
        return s;
    }

    @Override
    public Socket createSocket() throws IOException {
        return prepSocket(factory.createSocket());
    }

    public Socket createSocket(Socket socket, String s, int i, boolean flag)
            throws IOException {
        return prepSocket(factory.createSocket(socket, s, i, flag));
    }

    public Socket createSocket(InetAddress inaddr, int i,
                               InetAddress inaddr1, int j) throws IOException {
        return prepSocket(factory.createSocket(inaddr, i, inaddr1, j));
    }

    public Socket createSocket(InetAddress inaddr, int i)
            throws IOException {
        return prepSocket(factory.createSocket(inaddr, i));
    }

    public Socket createSocket(String s, int i, InetAddress inaddr, int j)
            throws IOException {
        return prepSocket(factory.createSocket(s, i, inaddr, j));
    }

    public Socket createSocket(String s, int i) throws IOException {
        return prepSocket(factory.createSocket(s, i));
    }

    public String[] getDefaultCipherSuites() {
        return factory.getDefaultCipherSuites();
    }

    public String[] getSupportedCipherSuites() {
        return factory.getSupportedCipherSuites();
    }
}
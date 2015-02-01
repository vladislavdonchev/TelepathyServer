/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2013 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package net.hardcodes.telepathyserver;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.websockets.WebSocketAddOn;
import org.glassfish.grizzly.websockets.WebSocketApplication;
import org.glassfish.grizzly.websockets.WebSocketEngine;

import javax.net.ssl.SSLContext;
import java.net.URL;

public class TelepathyWebSocketServer {

    public static final int PORT = 8021;

    public static void main(String[] args) throws Exception {
        final HttpServer server = HttpServer.createSimpleServer("", PORT);
        WebSocketAddOn webSocketAddOn = new WebSocketAddOn();
        webSocketAddOn.setTimeoutInSeconds(15);
        // Register the WebSockets add on with the HttpServer - TLS is temporarily disabled due to performance issues.
        //server.getListener("grizzly").setSSLEngineConfig(createSslConfiguration());
        //server.getListener("grizzly").setSecure(true);
        server.getListener("grizzly").registerAddOn(webSocketAddOn);

        final WebSocketApplication serverApplication = new ServerApplication();

        // Register the application.
        WebSocketEngine.getEngine().register("", "/tp", serverApplication);

        try {
            server.start();
            System.out.println("Press any key to stop the server...");
            System.in.read();
        } finally {
            server.shutdownNow();
        }
    }

    /**
     * Initialize server side SSL configuration.
     *
     * @return server side {@link SSLEngineConfigurator}.
     */
    private static SSLEngineConfigurator createSslConfiguration() {
        // Initialize SSLContext configuration
        SSLContextConfigurator sslContextConfig = new SSLContextConfigurator();
        sslContextConfig.setSecurityProtocol("SSL");

        ClassLoader cl = TelepathyWebSocketServer.class.getClassLoader();
        // Set key store
        URL keystoreUrl = cl.getResource("resources/test_512bit_keystore.jks");
        if (keystoreUrl != null) {
            sslContextConfig.setKeyStoreFile(keystoreUrl.getFile());
            sslContextConfig.setKeyPass("BCFFAAB67DF49E37C9E3DAD16A0F1A6F0F2BB93981D88BAC97CD7E293932E043");
            if (!sslContextConfig.validateConfiguration()){
                System.out.println("TLS config is broken...");
                return null;
            }
        } else {
            System.out.println("Where is the keyStore file?");
        }

        // Create SSLEngine configurator
        SSLEngineConfigurator sslEngineConfigurator = new SSLEngineConfigurator(sslContextConfig.createSSLContext(),
                false, false, false);
        sslEngineConfigurator.setClientMode(false);
        return sslEngineConfigurator;
    }
}
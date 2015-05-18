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
import org.glassfish.grizzly.websockets.WebSocketEngine;
import sun.misc.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

public class TelepathyWebSocketServer {

    public static final int PORT = 8021;

    public static void main(String[] args) throws Exception {
        boolean secure = true;
        if (args != null && args.length > 0) {
            secure = args[0].equals("--secure");
        }

        final HttpServer server = HttpServer.createSimpleServer("", PORT);
        WebSocketAddOn webSocketAddOn = new WebSocketAddOn();
        webSocketAddOn.setTimeoutInSeconds(15);

        if (secure) {
            server.getListener("grizzly").setSSLEngineConfig(createSslConfiguration());
            server.getListener("grizzly").setSecure(true);
            System.out.println("Starting " + server.getListener("grizzly").getSslEngineConfig().getEnabledProtocols()[0] + " secured service...");
        } else {
            System.out.println("Starting unencrypted service. Restart with '--secure' parameter to enable TLS...");
        }

        // Register the WebSockets add on with the HttpServer and setup the TLS configuration
        // if the '--secure' parameter has been provided.
        server.getListener("grizzly").registerAddOn(webSocketAddOn);

        final ServerApplication serverApplication = new ServerApplication();

        // Register the application.
        WebSocketEngine.getEngine().register("", "/tp", serverApplication);
        Utils.startSystemMonitor();

        try {
            server.start();
            System.out.println();
            System.out.println("Press enter to view system resource usage. Type 'exit' to stop the server.");
            System.out.println(Utils.getResourcesInfo());
            System.out.println();
            Scanner in = new Scanner(System.in);
            while (!in.nextLine().equals("exit")) {
                System.out.println(serverApplication.getUserInfo() + " | " + Utils.getResourcesInfo());
                System.out.println();
            }
        } finally {
            serverApplication.stopPinger();
            Utils.stopSystemMonitor();
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
        sslContextConfig.setSecurityProtocol("TLSv1.2");

        // Set key store
        ClassLoader classLoader = TelepathyWebSocketServer.class.getClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream("resources/telepathy.hardcodes.net.jks");
        try {
            sslContextConfig.setKeyStoreBytes(IOUtils.readFully(inputStream, -1, true));
        } catch (IOException e) {
            System.out.println("Error reading keyStore file...");
            return null;
        }
        sslContextConfig.setKeyPass("1ECEC069B552F3737F416E7B3E77C7D5A59DBDE8E194F7C0CEC82ED498DA49CF");
        if (!sslContextConfig.validateConfiguration()) {
            System.out.println("TLS config is broken...");
            return null;
        }

        // Create SSLEngine configurator
        SSLEngineConfigurator sslEngineConfigurator = new SSLEngineConfigurator(sslContextConfig.createSSLContext(),
                false, false, false);
        sslEngineConfigurator.setClientMode(false);
        //sslEngineConfigurator.setWantClientAuth(true);
        //sslEngineConfigurator.setNeedClientAuth(true);
        sslEngineConfigurator.setEnabledProtocols(new String[] { "TLSv1.2" });
        return sslEngineConfigurator;
    }
}
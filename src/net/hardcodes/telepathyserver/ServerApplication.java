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

import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.websockets.*;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class ServerApplication extends WebSocketApplication {

    private static final Logger logger = Grizzly.logger(ServerApplication.class);
    // Logged in users.
    private final ConcurrentHashMap<String, WebSocket> users = new ConcurrentHashMap<String, WebSocket>();
    // Connection pairs.
    private final ConcurrentHashMap<String, String> connections = new ConcurrentHashMap<String, String>();

    // Initialize optimized broadcaster for system-wide messages.
    private final Broadcaster broadcaster = new OptimizedBroadcaster();

    /**
     * Creates a customized {@link org.glassfish.grizzly.websockets.WebSocket} implementation.
     *
     * @return customized {@link org.glassfish.grizzly.websockets.WebSocket} implementation - {@link TelepathyWebSocket}
     */
    @Override
    public WebSocket createSocket(ProtocolHandler handler, HttpRequestPacket request, WebSocketListener... listeners) {
        return new TelepathyWebSocket(handler, request, listeners);
    }

    /**
     * Callback triggered when {@link TelepathyWebSocket} receives a {@link java.awt.Frame}.
     *
     * @param webSocket {@link TelepathyWebSocket}
     * @param data      {@link java.awt.Frame}
     * @throws java.io.IOException
     */
    @Override
    public void onMessage(WebSocket webSocket, String data) {

        System.out.println(data);

        if (data.startsWith(TelepathyAPI.MESSAGE_LOGIN + TelepathyAPI.MESSAGE_PAYLOAD_DELIMITER)) {
            String incomingUID = extractUID(data);
            login((TelepathyWebSocket) webSocket, incomingUID);

        } else if (data.startsWith(TelepathyAPI.MESSAGE_CONNECT + TelepathyAPI.MESSAGE_PAYLOAD_DELIMITER)) {
            String outgoingUID = extractUID(data);
            if (!users.containsKey(outgoingUID)) {
                webSocket.send(TelepathyAPI.MESSAGE_CONNECT_FAILED);
            } else {
                forwardConnectionRequest(outgoingUID, (TelepathyWebSocket) webSocket);
            }

        } else if (data.startsWith(TelepathyAPI.MESSAGE_CONNECT_ACCEPTED + TelepathyAPI.MESSAGE_PAYLOAD_DELIMITER)) {
            String outgoingUID = extractUID(data);
            bindConnection(((TelepathyWebSocket) webSocket).getUID(), outgoingUID);
            users.get(outgoingUID).send(TelepathyAPI.MESSAGE_CONNECT_ACCEPTED);

        } else if (data.startsWith(TelepathyAPI.MESSAGE_CONNECT_REJECTED + TelepathyAPI.MESSAGE_PAYLOAD_DELIMITER)) {
            String outgoingUID = extractUID(data);
            users.get(outgoingUID).send(TelepathyAPI.MESSAGE_CONNECT_REJECTED);

        } else if (data.startsWith(TelepathyAPI.MESSAGE_DISCONNECT + TelepathyAPI.MESSAGE_PAYLOAD_DELIMITER)) {
            String outgoingUID = extractUID(data);
            users.get(outgoingUID).send(TelepathyAPI.MESSAGE_DISCONNECT);

        } else {
            // Video stream frames, metadata or input data. Redirect to correct user based on connection pair bond.
            users.get(connections.get(((TelepathyWebSocket) webSocket).getUID())).send(data);

        }
    }

    @Override
    public void onMessage(WebSocket webSocket, byte[] bytes) {
        users.get(connections.get(((TelepathyWebSocket) webSocket).getUID())).send(bytes);
        super.onMessage(webSocket, bytes);

    }

    private String extractUID(String message) {
        return message.split(TelepathyAPI.MESSAGE_PAYLOAD_DELIMITER)[1];
    }

    private void forwardConnectionRequest(String outgoingUID, TelepathyWebSocket webSocket) {
        users.get(outgoingUID).send(TelepathyAPI.MESSAGE_CONNECT + TelepathyAPI.MESSAGE_PAYLOAD_DELIMITER + webSocket.getUID());
    }

    private void bindConnection(String leftUID, String rightUID) {
        connections.put(leftUID, rightUID);
        connections.put(rightUID, leftUID);
    }

    private void disbandConnection(String leftUID, String rightUID) {
        connections.remove(leftUID);
        connections.remove(rightUID);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onConnect(WebSocket socket) {
        System.out.println(socket.toString());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onClose(WebSocket websocket, DataFrame frame) {
        users.remove(websocket);
    }

    /**
     * Broadcasts a text message to all users.
     *
     * @param text the text message
     */
    private void broadcast(String text) {
        broadcaster.broadcast(users.values(), TelepathyAPI.MESSAGE_CHAT + TelepathyAPI.MESSAGE_PAYLOAD_DELIMITER + text);
    }

    /**
     * Process user log in.
     *
     * @param webSocket {@link TelepathyWebSocket}
     * @param uid       login {@link java.awt.Frame}
     */
    private void login(TelepathyWebSocket webSocket, String uid) {
        if (webSocket.getUID() == null) { // Is this user not registered yet?
            logger.info("ServerApplication.login");
            // Set the user ID.
            webSocket.setUID(uid);
            users.put(uid, webSocket);
        }
    }

    private static String escape(String orig) {
        StringBuilder buffer = new StringBuilder(orig.length());

        for (int i = 0; i < orig.length(); i++) {
            char c = orig.charAt(i);
            switch (c) {
                case '\b':
                    buffer.append("\\b");
                    break;
                case '\f':
                    buffer.append("\\f");
                    break;
                case '\n':
                    buffer.append("<br />");
                    break;
                case '\r':
                    // ignore
                    break;
                case '\t':
                    buffer.append("\\t");
                    break;
                case '\'':
                    buffer.append("\\'");
                    break;
                case '\"':
                    buffer.append("\\\"");
                    break;
                case '\\':
                    buffer.append("\\\\");
                    break;
                case '<':
                    buffer.append("&lt;");
                    break;
                case '>':
                    buffer.append("&gt;");
                    break;
                case '&':
                    buffer.append("&amp;");
                    break;
                default:
                    buffer.append(c);
            }
        }

        return buffer.toString();
    }
}

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

import com.google.gson.Gson;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.websockets.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

public class ServerApplication extends WebSocketApplication {

    // User profiles.
    private ConcurrentHashMap<String, User> userProfiles = new ConcurrentHashMap<String, User>();
    // Logged in users.
    private final ConcurrentHashMap<String, WebSocket> activeUsers = new ConcurrentHashMap<String, WebSocket>();
    // Connection pairs.
    private final ConcurrentHashMap<String, String> activeConnections = new ConcurrentHashMap<String, String>();

    // Initialize optimized broadcaster for system-wide messages.
    private final Broadcaster broadcaster = new OptimizedBroadcaster();

    public ServerApplication() {
        userProfiles = Utils.loadUserProfiles();
    }

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
     * {@inheritDoc}
     */
    @Override
    public void onConnect(WebSocket socket) {
        logToTerminal("WS CONNECT : " + socket.toString());
    }

    /**
     * Callback triggered when {@link TelepathyWebSocket} receives a {@link java.awt.Frame}.
     *
     * @param webSocket {@link TelepathyWebSocket}
     * @param message   {@link java.awt.Frame}
     * @throws java.io.IOException
     */
    @Override
    public void onMessage(WebSocket webSocket, String message) {

        // We don't want to log the metadata for every frame or any input commands...
        if (!message.startsWith(TelepathyAPI.MESSAGE_VIDEO_METADATA) && !message.startsWith(TelepathyAPI.MESSAGE_INPUT)) {
            logToTerminal("MESSAGE (" + ((TelepathyWebSocket) webSocket).getUID() + ") -> " + message);
        }

        // Ignore messages from users that are not logged in and close their sockets in order to conserve resources.
        // TODO: This should not happen!
        if (!message.startsWith(TelepathyAPI.MESSAGE_LOGIN) && !message.startsWith(TelepathyAPI.MESSAGE_REGISTER) && ((TelepathyWebSocket) webSocket).getUID() == null) {
            logToTerminal("Killing unknown motherfucker " + webSocket);
            webSocket.close();
            return;
        }

        if (message.startsWith(TelepathyAPI.MESSAGE_REGISTER)) {
            if (Utils.areThereResourcesLeft()) {
                String userProfileJson = extractMessagePayload(message);
                User userProfile = new Gson().fromJson(userProfileJson, User.class);
                if (userProfiles.containsKey(userProfile.getUserName())) {
                    logToTerminal("REGISTER FAIL : " + userProfile.getUserName());
                    send((TelepathyWebSocket) webSocket, TelepathyAPI.MESSAGE_ERROR + TelepathyAPI.ERROR_USER_ID_TAKEN);
                } else {
                    register(webSocket, userProfile);
                }
            } else {
                send(((TelepathyWebSocket) webSocket), TelepathyAPI.MESSAGE_ERROR + TelepathyAPI.ERROR_SERVER_OVERLOADED);
                webSocket.close();
                System.out.println("WARNING!!! " + Utils.getResourcesInfo());
            }

        } else if (message.startsWith(TelepathyAPI.MESSAGE_LOGIN)) {
            if (Utils.areThereResourcesLeft()) {
                String uid = extractMessageUID(message);
                String passHash = extractMessagePayload(message);
                if (activeUsers.containsKey(uid)) {
                    ((TelepathyWebSocket) activeUsers.get(uid)).setUID(null);
                }
                login((TelepathyWebSocket) webSocket, uid, passHash);
            } else {
                send(((TelepathyWebSocket) webSocket), TelepathyAPI.MESSAGE_ERROR + TelepathyAPI.ERROR_SERVER_OVERLOADED);
                webSocket.close();
                System.out.println("WARNING!!! " + Utils.getResourcesInfo());
            }

        } else if (message.startsWith(TelepathyAPI.MESSAGE_BIND)) {
            if (Utils.areThereResourcesLeft()) {
                String targetUID = extractMessageUID(message);
                if (!activeUsers.containsKey(targetUID)) {
                    send((TelepathyWebSocket) webSocket, TelepathyAPI.MESSAGE_ERROR + TelepathyAPI.ERROR_BIND_FAILED);
                } else {
                    if (activeConnections.contains(targetUID)) {
                        send((TelepathyWebSocket) webSocket, TelepathyAPI.MESSAGE_ERROR + TelepathyAPI.ERROR_OTHER_USER_BUSY);
                    } else {
                        forwardConnectionRequest(targetUID, (TelepathyWebSocket) webSocket);
                    }
                }
            } else {
                send(((TelepathyWebSocket) webSocket), TelepathyAPI.MESSAGE_ERROR + TelepathyAPI.ERROR_SERVER_OVERLOADED);
                webSocket.close();
                System.out.println("WARNING!!! " + Utils.getResourcesInfo());
            }

        } else if (message.startsWith(TelepathyAPI.MESSAGE_BIND_ACCEPTED)) {
            String targetUID = extractMessageUID(message);
            bindConnection(((TelepathyWebSocket) webSocket).getUID(), targetUID);
            send(targetUID, TelepathyAPI.MESSAGE_BIND_ACCEPTED);

        } else if (message.startsWith(TelepathyAPI.MESSAGE_BIND_REJECTED)) {
            String targetUID = extractMessageUID(message);
            send(targetUID, TelepathyAPI.MESSAGE_BIND_REJECTED);

        } else if (message.startsWith(TelepathyAPI.MESSAGE_DISBAND)) {
            String uid = ((TelepathyWebSocket) webSocket).getUID();
            String otherUID = activeConnections.get(uid);
            if (otherUID != null) {
                send(otherUID, TelepathyAPI.MESSAGE_DISBAND);
                disbandConnection(uid, otherUID);
            }

        } else if (message.startsWith(TelepathyAPI.MESSAGE_LOGOUT)) {
            logout((TelepathyWebSocket) webSocket);

        } else {
            // Video stream metadata or input commands. Redirect to correct user based on connection pair bond.
            String otherUserID = activeConnections.get(((TelepathyWebSocket) webSocket).getUID());
            if (otherUserID != null) {
                WebSocket otherUser = activeUsers.get(otherUserID);
                if (otherUser != null && otherUser.isConnected()) {
                    otherUser.send(message);
                }
            }
        }
    }

    @Override
    public void onMessage(WebSocket webSocket, byte[] bytes) {
        // Video stream frames. Redirect to correct user based on connection pair bond.
        String otherUserID = activeConnections.get(((TelepathyWebSocket) webSocket).getUID());
        if (otherUserID != null) {
            WebSocket otherUser = activeUsers.get(otherUserID);
            if (otherUser != null && otherUser.isConnected()) {
                otherUser.send(bytes);
            }
        }
    }

    private void register(WebSocket webSocket, User newUser) {
        logToTerminal("REGISTER SUCCESS : " + newUser.getUserName() + " " + newUser.getMailAddress());
        newUser.setRegistrationTimestamp(new Date().getTime());

        userProfiles.put(newUser.getUserName(), newUser);
        Utils.saveUserProfiles(new ArrayList<User>(userProfiles.values()));
        userProfiles = Utils.loadUserProfiles();

        send((TelepathyWebSocket) webSocket, TelepathyAPI.MESSAGE_REGISTRATION_SUCCESS);
    }

    /**
     * Process user log in.
     *
     * @param webSocket {@link TelepathyWebSocket}
     * @param uid       login {@link java.awt.Frame}
     */
    private void login(TelepathyWebSocket webSocket, String uid, String passHash) {
        if (userProfiles.get(uid) != null && userProfiles.get(uid).getPasswordHash().equals(passHash)) {
            // Set the user ID.
            webSocket.setUID(uid);
            activeUsers.put(uid, webSocket);
            userProfiles.get(uid).setLastLoginTimestamp(new Date().getTime());
            Utils.saveUserProfiles(new ArrayList<User>(userProfiles.values()));
            send(uid, TelepathyAPI.MESSAGE_LOGIN_SUCCESS);
            logToTerminal("LOGIN : " + uid);
        } else {
            send((TelepathyWebSocket) webSocket, TelepathyAPI.MESSAGE_ERROR + TelepathyAPI.ERROR_USER_AUTHENTICATION_FAILED);
        }
    }

    private void logout(TelepathyWebSocket webSocket) {
        String uid = webSocket.getUID();

        // Check if not already logged out...
        if (uid != null) {
            activeUsers.remove(uid);
            logToTerminal("LOGOUT : " + uid);
            send(webSocket, TelepathyAPI.MESSAGE_LOGOUT_SUCCESS);

            String otherUID = activeConnections.get(uid);

            // If socket closes unexpectedly and there is an active connection then disband it and inform the other end.
            if (otherUID != null) {
                send(otherUID, TelepathyAPI.MESSAGE_ERROR + TelepathyAPI.ERROR_OTHER_END_HUNG_UP_UNEXPECTEDLY);
                disbandConnection(uid, otherUID);
            }
            webSocket.setUID(null);
        }
    }

    private String extractMessageUID(String message) {
        String stringAfterUIDDelimiter = message.split(TelepathyAPI.MESSAGE_UID_DELIMITER)[1];

        // Is there a payload attached to this message? If yes, then the actual user name is contained before the payload delimiter.
        if (stringAfterUIDDelimiter.split(TelepathyAPI.MESSAGE_PAYLOAD_DELIMITER).length > 1) {
            return stringAfterUIDDelimiter.split(TelepathyAPI.MESSAGE_PAYLOAD_DELIMITER)[0];
        }
        return stringAfterUIDDelimiter;
    }

    private String extractMessagePayload(String message) {
        return message.split(TelepathyAPI.MESSAGE_PAYLOAD_DELIMITER)[1];
    }

    private void forwardConnectionRequest(String outgoingUID, TelepathyWebSocket webSocket) {
        send(outgoingUID, TelepathyAPI.MESSAGE_BIND + webSocket.getUID());
    }

    private void bindConnection(String leftUID, String rightUID) {
        activeConnections.put(leftUID, rightUID);
        activeConnections.put(rightUID, leftUID);
        logToTerminal("BINDING CONNECTION : " + leftUID + " <-> " + rightUID);
    }

    private void disbandConnection(String leftUID, String rightUID) {
        activeConnections.remove(leftUID);
        activeConnections.remove(rightUID);
        logToTerminal("DISBANDING CONNECTION : " + leftUID + " <-> " + rightUID);
    }


    private void send(String uid, String message) {
        TelepathyWebSocket webSocket = (TelepathyWebSocket) activeUsers.get(uid);
        send(webSocket, message);
    }

    private void send(TelepathyWebSocket webSocket, String message) {
        if (webSocket != null && webSocket.isConnected()) {
            webSocket.send(message);
            logToTerminal("SERVER (" + webSocket.getUID() + ") -> " + message);
        }
    }

    /**
     * Broadcasts a system message to all users.
     *
     * @param text the text message
     */
    private void broadcast(String text) {
        broadcaster.broadcast(activeUsers.values(), TelepathyAPI.MESSAGE_BROADCAST + TelepathyAPI.MESSAGE_UID_DELIMITER + text);
        logToTerminal("BROADCAST -> " + text);
    }

    /**
     * Broadcasts an error message to all users.
     *
     * @param text the text message
     */
    private void broadcastError(String text) {
        broadcaster.broadcast(activeUsers.values(), TelepathyAPI.MESSAGE_ERROR + TelepathyAPI.MESSAGE_UID_DELIMITER + text);
        logToTerminal("BROADCAST ERROR -> " + text);
    }

    @Override
    public void onPing(WebSocket socket, byte[] bytes) {
        logToTerminal("PING (" + ((TelepathyWebSocket) socket).getUID() + ") -> " + bytes.toString());
        super.onPing(socket, bytes);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onClose(WebSocket webSocket, DataFrame frame) {
        if (webSocket != null) {
            if (frame != null) {
                logToTerminal("WS DISCONNECT (" + ((TelepathyWebSocket) webSocket).getUID() + ") : " + webSocket.toString() + " -> " + frame.getTextPayload());
            } else {
                logToTerminal("WS DISCONNECT (" + ((TelepathyWebSocket) webSocket).getUID() + ") : " + webSocket.toString() + " -> DataFrame NULL");
            }
            logout((TelepathyWebSocket) webSocket);
        } else {
            logToTerminal("WS DISCONNECT NULL SOCKET ???");
        }
    }

    @Override
    protected boolean onError(WebSocket webSocket, Throwable t) {
        logToTerminal(t.toString() + " " + t.getMessage() + ": " + t.getCause());
        return super.onError(webSocket, t);
    }

    private void logToTerminal(String message) {
        String date = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date());
        System.out.println(date + "  " + message);
    }
}

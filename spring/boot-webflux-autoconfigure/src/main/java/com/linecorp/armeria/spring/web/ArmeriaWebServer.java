/*
 * Copyright 2018 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.linecorp.armeria.spring.web;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Optional;

import javax.annotation.Nullable;

import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.WebServerException;

import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.common.util.Exceptions;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.ServerPort;
import com.linecorp.armeria.spring.web.reactive.ArmeriaReactiveWebServerFactory;

/**
 * A {@link WebServer} that can be used to control an Armeria server.
 *
 * @see ArmeriaReactiveWebServerFactory to create a {@link WebServer} with the reactive stack
 */
public final class ArmeriaWebServer implements WebServer {

    private final Server server;
    private final SessionProtocol protocol;
    @Nullable
    private final InetAddress address;
    private int port;

    private boolean isRunning;

    /**
     * Creates a new {@link WebServer} instance with the specified Armeria {@link Server}.
     *
     * @param server the Armeria server
     * @param protocol the session protocol which is used for the primary port
     * @param address the primary local address that the server will be bound to
     * @param port the primary local port that the server will be bound to
     */
    public ArmeriaWebServer(Server server, SessionProtocol protocol, @Nullable InetAddress address, int port) {
        this.server = requireNonNull(server, "server");
        this.protocol = requireNonNull(protocol, "protocol");
        this.address = address;
        checkArgument(port >= 0 && port <= 65535, "port: %s (expected: 0...65535)", port);
        this.port = port;
    }

    @Override
    public synchronized void start() {
        try {
            if (!isRunning) {
                server.start().get();
                if (port == 0) {
                    // Replace the specified port number with the primary port number.
                    // Server#activePort doesn't return the first added port, so we need to find that.
                    final Optional<ServerPort> port =
                            server.activePorts().values().stream()
                                  .filter(p -> p.protocols().contains(protocol))
                                  .filter(p -> address == null ||
                                               Arrays.equals(address.getAddress(),
                                                             p.localAddress().getAddress().getAddress()))
                                  .findFirst();
                    assert port.isPresent() : "the primary port doest not exist.";
                    this.port = port.get().localAddress().getPort();
                }
                isRunning = true;
            }
        } catch (Exception cause) {
            throw new WebServerException("Failed to start " + ArmeriaWebServer.class.getSimpleName(),
                                         Exceptions.peel(cause));
        }
    }

    @Override
    public synchronized void stop() {
        try {
            if (isRunning) {
                server.stop().get();
                isRunning = false;
            }
        } catch (Exception cause) {
            throw new WebServerException("Failed to stop " + ArmeriaWebServer.class.getSimpleName(),
                                         Exceptions.peel(cause));
        }
    }

    @Override
    public int getPort() {
        return port;
    }
}

/* license: https://mit-license.org
 *
 *  DIMP : Decentralized Instant Messaging Protocol
 *
 *                                Written in 2022 by Moky <albert.moky@gmail.com>
 *
 * ==============================================================================
 * The MIT License (MIT)
 *
 * Copyright (c) 2022 Albert Moky
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
 * ==============================================================================
 */
package chat.dim.network;

import java.io.IOError;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;

import chat.dim.net.Connection;
import chat.dim.net.Hub;
import chat.dim.pack.DeparturePacker;
import chat.dim.port.Arrival;
import chat.dim.port.Departure;
import chat.dim.port.Docker;
import chat.dim.protocol.ReliableMessage;
import chat.dim.queue.MessageQueue;
import chat.dim.queue.MessageWrapper;
import chat.dim.skywalker.Runner;
import chat.dim.tcp.StreamChannel;
import chat.dim.tcp.StreamHub;
import chat.dim.utils.Log;

public class GateKeeper extends Runner implements Docker.Delegate {

    private final SocketAddress remoteAddress;
    private final CommonGate gate;
    private final MessageQueue queue;
    private boolean active;
    private long lastActive;  // last update time

    public GateKeeper(SocketAddress remote, SocketChannel sock) {
        super();
        remoteAddress = remote;
        gate = createGate(remote, sock);
        queue = new MessageQueue();
        active = false;
        lastActive = 0;
    }

    protected CommonGate createGate(SocketAddress remote, SocketChannel sock) {
        CommonGate streamGate;
        if (sock == null) {
            streamGate = new TCPClientGate(this);
        } else {
            streamGate = new TCPServerGate(this);
        }
        streamGate.setHub(createHub(streamGate, remote, sock));
        return streamGate;
    }

    protected StreamHub createHub(Connection.Delegate delegate, SocketAddress remote, SocketChannel sock) {
        if (sock == null) {
            // client
            assert remote != null : "remote address empty";
            StreamClientHub hub = new StreamClientHub(delegate);
            Connection conn = hub.connect(remote, null);
            assert conn != null : "failed to connect remote: " + remote;
            // TODO: reset send buffer size
            return hub;
        } else {
            // server
            SocketAddress local = null;
            try {
                sock.configureBlocking(false);
                local = sock.getLocalAddress();
                if (remote == null) {
                    remote = sock.getRemoteAddress();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            StreamChannel channel = new StreamChannel(remote, local, sock);
            StreamServerHub hub = new StreamServerHub(delegate);
            hub.putChannel(channel);
            return hub;
        }
    }

    private static boolean resetSendBufferSize(SocketChannel channel) throws SocketException {
        return resetSendBufferSize(channel.socket());
    }
    private static boolean resetSendBufferSize(Socket socket) throws SocketException {
        int size = socket.getSendBufferSize();
        if (size < SEND_BUFFER_SIZE) {
            socket.setSendBufferSize(SEND_BUFFER_SIZE);
            return true;
        } else {
            return false;
        }
    }
    public static int SEND_BUFFER_SIZE = 64 * 1024;  // 64 KB

    public SocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    public CommonGate getGate() {
        return gate;
    }

    public boolean isActive() {
        return active;
    }
    public boolean setActive(boolean flag, long when) {
        if (active == flag) {
            // flag not changed
            return false;
        }
        if (when <= 0) {
            when = System.currentTimeMillis();
        } else if (when <= lastActive) {
            return false;
        }
        active = flag;
        lastActive = when;
        return true;
    }

    @Override
    public boolean isRunning() {
        if (super.isRunning()) {
            return gate.isRunning();
        } else {
            return false;
        }
    }

    @Override
    public void stop() {
        super.stop();
        gate.stop();
    }

    @Override
    public void setup() {
        super.setup();
        gate.start();
    }

    @Override
    public void finish() {
        gate.stop();
        super.finish();
    }

    @Override
    public boolean process() {
        Hub hub = gate.getHub();
        try {
            boolean incoming = hub.process();
            boolean outgoing = gate.process();
            if (incoming || outgoing) {
                // processed income/outgo packages
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        if (!isActive()) {
            // inactive, wait a while to check again
            queue.purge();
            return false;
        }
        // get next message
        MessageWrapper wrapper = queue.next();
        if (wrapper == null) {
            // no more task now, purge failed task
            queue.purge();
            return false;
        }
        // if msg in this wrapper is null (means sent successfully),
        // it must have bean cleaned already, so iit should not be empty here
        ReliableMessage msg = wrapper.getMessage();
        if (msg == null) {
            // msg sent?
            return true;
        }
        // try to push
        boolean ok = gate.sendShip(wrapper, remoteAddress, null);
        if (!ok) {
            Log.error("gate error, failed to send data");
        }
        return true;
    }

    protected Departure dockerPack(byte[] payload, int priority) {
        Docker docker = gate.getDocker(remoteAddress, null, new ArrayList<>());
        assert docker instanceof DeparturePacker : "departure packer error: " + docker;
        return ((DeparturePacker) docker).packData(payload, priority);
    }

    protected boolean queueAppend(ReliableMessage msg, Departure ship) {
        return queue.append(msg, ship);
    }

    //
    //  Docker.Delegate
    //

    @Override
    public void onDockerStatusChanged(Docker.Status previous, Docker.Status current, Docker docker) {
        Log.info("docker status changed: " + previous + " => " + current + ", " + docker);
    }

    @Override
    public void onDockerReceived(Arrival ship, Docker docker) {
        Log.debug("docker received a ship: " + ship + ", " + docker);
    }

    @Override
    public void onDockerSent(Departure ship, Docker docker) {
        // TODO: remove sent message from local cache
    }

    @Override
    public void onDockerFailed(IOError error, Departure ship, Docker docker) {
        Log.error("docker failed to send ship: " + ship + ", " + docker);
    }

    @Override
    public void onDockerError(IOError error, Departure ship, Docker docker) {
        Log.error("docker error while sending ship: " + ship + ", " + docker);
    }
}

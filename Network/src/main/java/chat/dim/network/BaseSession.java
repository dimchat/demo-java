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

import java.lang.ref.WeakReference;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;

import chat.dim.CommonMessenger;
import chat.dim.Session;
import chat.dim.dbi.SessionDBI;
import chat.dim.port.Departure;
import chat.dim.protocol.Content;
import chat.dim.protocol.ID;
import chat.dim.protocol.InstantMessage;
import chat.dim.protocol.ReliableMessage;
import chat.dim.type.Pair;

public abstract class BaseSession extends GateKeeper implements Session {

    private final SessionDBI database;
    private ID identifier;
    private WeakReference<CommonMessenger> messengerRef;

    public BaseSession(SocketAddress remote, SocketChannel sock, SessionDBI sdb) {
        super(remote, sock);
        database = sdb;
        identifier = null;
        messengerRef = null;
    }

    @Override
    public SessionDBI getDatabase() {
        return database;
    }

    @Override
    public ID getIdentifier() {
        return identifier;
    }

    @Override
    public boolean setIdentifier(ID user) {
        if (identifier == null) {
            if (user == null) {
                return false;
            }
        } else if (identifier.equals(user)) {
            return false;
        }
        identifier = user;
        return true;
    }

    public CommonMessenger getMessenger() {
        WeakReference<CommonMessenger> ref = messengerRef;
        return ref == null ? null : ref.get();
    }
    public void setMessenger(CommonMessenger messenger) {
        messengerRef = messenger == null ? null : new WeakReference<>(messenger);
    }

    @Override
    public boolean queueMessagePackage(ReliableMessage msg, byte[] data, int priority) {
        Departure ship = dockerPack(data, priority);
        return queueAppend(msg, ship);
    }

    //
    //  Transmitter
    //

    @Override
    public Pair<InstantMessage, ReliableMessage> sendContent(Content content, ID sender, ID receiver, int priority) {
        CommonMessenger messenger = getMessenger();
        assert messenger != null : "messenger not set yet";
        return messenger.sendContent(content, sender, receiver, priority);
    }

    @Override
    public ReliableMessage sendInstantMessage(InstantMessage iMsg, int priority) {
        CommonMessenger messenger = getMessenger();
        assert messenger != null : "messenger not set yet";
        return messenger.sendInstantMessage(iMsg, priority);
    }

    @Override
    public boolean sendReliableMessage(ReliableMessage rMsg, int priority) {
        CommonMessenger messenger = getMessenger();
        assert messenger != null : "messenger not set yet";
        return messenger.sendReliableMessage(rMsg, priority);
    }

}

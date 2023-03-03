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
package chat.dim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import chat.dim.dbi.MessageDBI;
import chat.dim.mkm.Station;
import chat.dim.mkm.User;
import chat.dim.network.ClientSession;
import chat.dim.protocol.Content;
import chat.dim.protocol.DocumentCommand;
import chat.dim.protocol.Envelope;
import chat.dim.protocol.GroupCommand;
import chat.dim.protocol.HandshakeCommand;
import chat.dim.protocol.ID;
import chat.dim.protocol.InstantMessage;
import chat.dim.protocol.LoginCommand;
import chat.dim.protocol.Meta;
import chat.dim.protocol.MetaCommand;
import chat.dim.protocol.ReportCommand;
import chat.dim.protocol.Visa;
import chat.dim.utils.Log;
import chat.dim.utils.QueryFrequencyChecker;

/**
 *  Client Messenger for Handshake & Broadcast Report
 */
public class ClientMessenger extends CommonMessenger {

    public ClientMessenger(Session session, CommonFacebook facebook, MessageDBI database) {
        super(session, facebook, database);
    }

    @Override
    public ClientSession getSession() {
        return (ClientSession) super.getSession();
    }

    /**
     *  Send handshake command to current station
     *
     * @param sessionKey - respond session key
     */
    public void handshake(String sessionKey) {
        ClientSession session = getSession();
        Station station = session.getStation();
        ID sid = station.getIdentifier();
        if (sessionKey == null) {
            // first handshake
            CommonFacebook facebook = getFacebook();
            User user = facebook.getCurrentUser();
            assert user != null : "current user not found";
            ID uid = user.getIdentifier();
            Meta meta = user.getMeta();
            Visa visa = user.getVisa();
            Envelope env = Envelope.create(uid, sid, null);
            Content content = HandshakeCommand.start();
            // send first handshake command as broadcast message
            content.setGroup(Station.EVERY);
            // create instant message with meta & visa
            InstantMessage iMsg = InstantMessage.create(env, content);
            iMsg.put("meta", meta.toMap());
            iMsg.put("visa", visa.toMap());
            sendInstantMessage(iMsg, -1);
        } else {
            // handshake again
            Content content = HandshakeCommand.restart(sessionKey);
            sendContent(null, sid, content, -1);
        }
    }

    /**
     *  Callback for handshake success
     */
    public void handshakeSuccess() {
        // broadcast current documents after handshake success
        broadcastDocument();
    }

    /**
     *  Broadcast meta & visa document to all stations
     */
    public void broadcastDocument() {
        CommonFacebook facebook = getFacebook();
        User user = facebook.getCurrentUser();
        assert user != null : "current user not found";
        ID uid = user.getIdentifier();
        Meta meta = user.getMeta();
        Visa visa = user.getVisa();
        Content content = DocumentCommand.response(uid, meta, visa);
        // broadcast to 'everyone@everywhere'
        sendContent(uid, ID.EVERYONE, content, 1);
    }

    /**
     *  Send login command to keep roaming
     */
    public void broadcastLogin(ID sender, String userAgent) {
        ClientSession session = getSession();
        Station station = session.getStation();
        // create login command
        LoginCommand content = new LoginCommand(sender);
        content.setAgent(userAgent);
        content.setStation(station);
        // broadcast to 'everyone@everywhere'
        sendContent(sender, ID.EVERYONE, content, 1);
    }

    /**
     *  Send report command to keep user online
     */
    public void reportOnline(ID sender) {
        Content content = new ReportCommand(ReportCommand.ONLINE);
        sendContent(sender, Station.ANY, content, 1);
    }

    /**
     *  Send report command to let user offline
     */
    public void reportOffline(ID sender) {
        Content content = new ReportCommand(ReportCommand.OFFLINE);
        sendContent(sender, Station.ANY, content, 1);
    }

    @Override
    protected boolean queryMeta(ID identifier) {
        QueryFrequencyChecker checker = QueryFrequencyChecker.getInstance();
        if (!checker.isMetaQueryExpired(identifier, 0)) {
            // query not expired yet
            return false;
        }
        Content content = MetaCommand.query(identifier);
        sendContent(null, Station.ANY, content, 1);
        return true;
    }

    @Override
    protected boolean queryDocument(ID identifier) {
        QueryFrequencyChecker checker = QueryFrequencyChecker.getInstance();
        if (!checker.isDocumentQueryExpired(identifier, 0)) {
            // query not expired yet
            return false;
        }
        Content content = DocumentCommand.query(identifier);
        sendContent(null, Station.ANY, content, 1);
        return true;
    }

    /**
     *  Request for group members with group ID
     *
     * @param identifier - group ID
     * @return false on duplicated
     */
    protected boolean queryMembers(ID identifier) {
        QueryFrequencyChecker checker = QueryFrequencyChecker.getInstance();
        if (!checker.isMembersQueryExpired(identifier, 0)) {
            // query not expired yet
            return false;
        }
        assert identifier.isGroup() : "group ID error: " + identifier;
        CommonFacebook facebook = getFacebook();
        List<ID> assistants = facebook.getAssistants(identifier);
        if (assistants == null || assistants.size() == 0) {
            // group assistants not found
            return false;
        }
        // querying members from bots
        Content content = GroupCommand.query(identifier);
        for (ID bot : assistants) {
            sendContent(null, bot, content, 1);
        }
        return true;
    }

    @Override
    protected boolean checkReceiver(InstantMessage iMsg) {
        ID receiver = iMsg.getReceiver();
        if (receiver.isBroadcast()) {
            // broadcast message
            return true;
        } else if (receiver.isGroup()) {
            CommonFacebook facebook = getFacebook();
            // check group's meta
            Meta meta = facebook.getMeta(receiver);
            if (meta == null) {
                // group not ready, try to query meta for it
                if (queryMeta(receiver)) {
                    Log.info("querying meta for group: " + receiver);
                }
                Map<String, String> error = new HashMap<>();
                error.put("message", "group meta not found");
                error.put("group", receiver.toString());
                iMsg.put("error", error);
                return false;
            }
            // check group members
            List<ID> members = facebook.getMembers(receiver);
            if (members == null || members.size() == 0) {
                // group not ready, try to query members for it
                if (queryMembers(receiver)) {
                    Log.info("querying members for group: " + receiver);
                }
                Map<String, String> error = new HashMap<>();
                error.put("message", "members not found");
                error.put("group", receiver.toString());
                iMsg.put("error", error);
                return false;
            }
            List<ID> waiting = new ArrayList<>();
            for (ID item : members) {
                if (facebook.getPublicKeyForEncryption(item) == null) {
                    // group member not ready, try to query document for it
                    if (queryDocument(item)) {
                        Log.info("querying document for member: " + item + ", group: " + receiver);
                    }
                    waiting.add(item);
                }
            }
            if (waiting.size() > 0) {
                Map<String, Object> error = new HashMap<>();
                error.put("message", "encrypt keys not found");
                error.put("group", receiver.toString());
                error.put("members", ID.revert(waiting));
                iMsg.put("error", error);
                return false;
            }
            // receiver is OK
            return true;
        }
        // check user's meta & document
        return super.checkReceiver(iMsg);
    }
}

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

import java.util.List;

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
        broadcastDocument(false);
    }

    /**
     *  Broadcast meta & visa document to all stations
     */
    public void broadcastDocument(boolean updated) {
        CommonFacebook facebook = getFacebook();
        User user = facebook.getCurrentUser();
        assert user != null : "current user not found";
        ID uid = user.getIdentifier();
        Meta meta = user.getMeta();
        Visa visa = user.getVisa();
        DocumentCommand command = DocumentCommand.response(uid, meta, visa);
        // send to all contacts
        List<ID> contacts = facebook.getContacts(uid);
        for (ID item : contacts) {
            sendVisa(uid, item, command, updated);
        }
        // broadcast to 'everyone@everywhere'
        sendVisa(uid, ID.EVERYONE, command, updated);
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

    private void sendVisa(ID sender, ID receiver, DocumentCommand content, boolean force) {
        QueryFrequencyChecker checker = QueryFrequencyChecker.getInstance();
        if (checker.isDocumentResponseExpired(receiver, 0, force)) {
            Log.info("push visa to: " + receiver);
            sendContent(sender, receiver, content, 1);
        } else {
            // response not expired yet
            Log.debug("document response not expired yet: " + receiver);
        }
    }

    @Override
    protected boolean queryMeta(ID identifier) {
        QueryFrequencyChecker checker = QueryFrequencyChecker.getInstance();
        if (!checker.isMetaQueryExpired(identifier, 0)) {
            // query not expired yet
            Log.debug("meta query not expired yet: " + identifier);
            return false;
        }
        Log.info("querying meta from any station, ID: " + identifier);
        Content content = MetaCommand.query(identifier);
        sendContent(null, Station.ANY, content, 1);
        return true;
    }

    @Override
    protected boolean queryDocument(ID identifier) {
        QueryFrequencyChecker checker = QueryFrequencyChecker.getInstance();
        if (!checker.isDocumentQueryExpired(identifier, 0)) {
            // query not expired yet
            Log.debug("document query not expired yet: " + identifier);
            return false;
        }
        Log.info("querying document from any station, ID: " + identifier);
        Content content = DocumentCommand.query(identifier);
        sendContent(null, Station.ANY, content, 1);
        return true;
    }

    @Override
    protected boolean queryMembers(ID identifier) {
        QueryFrequencyChecker checker = QueryFrequencyChecker.getInstance();
        if (!checker.isMembersQueryExpired(identifier, 0)) {
            // query not expired yet
            Log.debug("members query not expired yet: " + identifier);
            return false;
        }
        assert identifier.isGroup() : "group ID error: " + identifier;
        Log.info("querying members from any station, ID: " + identifier);
        GroupManager manager = GroupManager.getInstance();
        List<ID> assistants = manager.getAssistants(identifier);
        if (assistants == null || assistants.size() == 0) {
            // group assistants not found
            Log.error("group assistants not found: " + identifier);
            return false;
        }
        // querying members from bots
        Content content = GroupCommand.query(identifier);
        for (ID bot : assistants) {
            sendContent(null, bot, content, 1);
        }
        return true;
    }

}

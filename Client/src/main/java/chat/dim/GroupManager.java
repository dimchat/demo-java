/* license: https://mit-license.org
 *
 *  DIM-SDK : Decentralized Instant Messaging Software Development Kit
 *
 *                                Written in 2019 by Moky <albert.moky@gmail.com>
 *
 * ==============================================================================
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Albert Moky
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
import java.util.List;

import chat.dim.mkm.User;
import chat.dim.port.Departure;
import chat.dim.protocol.Command;
import chat.dim.protocol.Content;
import chat.dim.protocol.Document;
import chat.dim.protocol.DocumentCommand;
import chat.dim.protocol.GroupCommand;
import chat.dim.protocol.ID;
import chat.dim.protocol.InstantMessage;
import chat.dim.protocol.Meta;
import chat.dim.protocol.MetaCommand;
import chat.dim.protocol.ReliableMessage;
import chat.dim.type.Pair;

/**
 *  This is for sending group message, or managing group members
 */
public final class GroupManager {

    private final ClientMessenger messenger;

    private final ID group;

    public GroupManager(ID gid, ClientMessenger transceiver) {
        super();
        group = gid;
        messenger = transceiver;
    }

    /**
     *  Send group message content
     *
     * @param content - message content
     * @return false on no bots found
     */
    public boolean sendContent(Content content) {
        ID gid = content.getGroup();
        if (gid == null) {
            content.setGroup(group);
        } else if (!gid.equals(group)) {
            throw new IllegalArgumentException("group ID not match: " + gid + ", " + group);
        }
        CommonFacebook facebook = messenger.getFacebook();
        List<ID> assistants = facebook.getAssistants(group);
        Pair<InstantMessage, ReliableMessage> result;
        for (ID bot : assistants) {
            // send to any bot
            result = messenger.sendContent(null, bot, content, Departure.Priority.NORMAL.value);
            if (result.second != null) {
                // only send to one bot
                return true;
            }
        }
        return false;
    }

    private void sendGroupCommand(Command content, ID receiver) {
        assert receiver != null : "receiver should not be empty";
        messenger.sendContent(null, receiver, content, Departure.Priority.NORMAL.value);
    }
    private void sendGroupCommand(Command content, List<ID> members) {
        assert members != null : "receivers should not be empty";
        for (ID receiver : members) {
            sendGroupCommand(content, receiver);
        }
    }

    /**
     *  Invite new members to this group
     *  (only existed member/assistant can do this)
     *
     * @param newMembers - new members ID list
     * @return true on success
     */
    public boolean invite(List<ID> newMembers) {
        CommonFacebook facebook = messenger.getFacebook();
        List<ID> bots = facebook.getAssistants(group);
        List<ID> members = facebook.getMembers(group);
        if (members == null) {
            members = new ArrayList<>();
        }
        int count = members.size();

        // 0. build 'meta/document' command
        Meta meta = facebook.getMeta(group);
        if (meta == null) {
            throw new NullPointerException("failed to get meta for group: " + group);
        }
        Document doc = facebook.getDocument(group, "*");
        Command command;
        if (doc == null) {
            // empty document
            command = MetaCommand.response(group, meta);
        } else {
            command = DocumentCommand.response(group, meta, doc);
        }
        sendGroupCommand(command, bots);            // to group assistants
        if (count <= 2) { // new group?
            // 1. send 'meta/document' to station and bots
            // 2. update local storage
            members = addMembers(newMembers, group);
            sendGroupCommand(command, members);     // to all members
            // 3. send 'invite' command with all members to all members
            command = GroupCommand.invite(group, members);
            sendGroupCommand(command, bots);        // to group assistants
            sendGroupCommand(command, members);     // to all members
        } else {
            // 1. send 'meta/document' to station, bots and all members
            //sendGroupCommand(command, members);     // to old members
            sendGroupCommand(command, newMembers);  // to new members
            // 2. send 'invite' command with new members to old members
            command = GroupCommand.invite(group, newMembers);
            sendGroupCommand(command, bots);        // to group assistants
            sendGroupCommand(command, members);     // to old members
            // 3. update local storage
            members = addMembers(newMembers, group);
            // 4. send 'invite' command with all members to new members
            command = GroupCommand.invite(group, members);
            sendGroupCommand(command, newMembers);  // to new members
        }

        return true;
    }

    /**
     *  Expel members from this group
     *  (only group owner/assistant can do this)
     *
     * @param outMembers - existed member ID list
     * @return true on success
     */
    public boolean expel(List<ID> outMembers) {
        CommonFacebook facebook = messenger.getFacebook();
        ID owner = facebook.getOwner(group);
        List<ID> bots = facebook.getAssistants(group);
        List<ID> members = facebook.getMembers(group);
        if (members == null) {
            members = new ArrayList<>();
        }

        // 0. check members list
        for (ID assistant : bots) {
            if (outMembers.contains(assistant)) {
                throw new RuntimeException("Cannot expel group assistant: " + assistant);
            }
        }
        if (outMembers.contains(owner)) {
            throw new RuntimeException("Cannot expel group owner: " + owner);
        }

        // 1. send 'expel' command to all members
        Command command = GroupCommand.expel(group, outMembers);
        sendGroupCommand(command, bots);        // to assistants
        sendGroupCommand(command, members);     // to existed members
        if (owner != null && !members.contains(owner)) {
            sendGroupCommand(command, owner);   // to owner
        }

        // 2. update local storage
        return removeMembers(outMembers, group);
    }

    /**
     *  Quit from this group
     *  (only group member can do this)
     *
     * @return true on success
     */
    public boolean quit() {
        CommonFacebook facebook = messenger.getFacebook();
        User user = facebook.getCurrentUser();
        if (user == null) {
            throw new NullPointerException("failed to get current user");
        }

        ID owner = facebook.getOwner(group);
        List<ID> bots = facebook.getAssistants(group);
        List<ID> members = facebook.getMembers(group);

        // 0. check permission
        ID uid = user.getIdentifier();
        if (bots.contains(uid)) {
            throw new RuntimeException("Group assistant cannot quit: " + uid);
        } else if (uid.equals(owner)) {
            throw new RuntimeException("Group owner cannot quit: " + owner);
        }

        // 1. send 'quit' command to group bots
        Command command = GroupCommand.quit(group);
        sendGroupCommand(command, bots);        // to assistants

        // 2. remove from members
        if (members == null) {
            //throw new NullPointerException("Group members not found: " + group);
            return true;
        }
        int pos = members.indexOf(uid);
        if (pos >= 0) {
            members.remove(pos);
        //} else {
        //    //throw new IndexOutOfBoundsException("Not a group member: " + uid + ", members: " + ID.revert(members));
        //    return true;
        }

        // 3. send 'quit' command to other members
        sendGroupCommand(command, members);     // to existed members
        if (owner != null && !members.contains(owner)) {
            sendGroupCommand(command, owner);   // to owner
        }

        // 4. update local storage
        return facebook.saveMembers(members, group);
    }

    /**
     *  Query group info
     *
     * @return false on error
     */
    public boolean query() {
        return messenger.queryMembers(group);
    }

    //-------- local storage

    private List<ID> addMembers(List<ID> newMembers, ID group) {
        CommonFacebook facebook = messenger.getFacebook();
        List<ID> members = facebook.getMembers(group);
        assert members != null : "failed to get members for group: " + group;
        int count = 0;
        for (ID member : newMembers) {
            if (members.contains(member)) {
                continue;
            }
            members.add(member);
            ++count;
        }
        if (count > 0) {
            facebook.saveMembers(members, group);
        }
        return members;
    }
    private boolean removeMembers(List<ID> outMembers, ID group) {
        CommonFacebook facebook = messenger.getFacebook();
        List<ID> members = facebook.getMembers(group);
        assert members != null : "failed to get members for group: " + group;
        int count = 0;
        for (ID member : outMembers) {
            if (!members.contains(member)) {
                continue;
            }
            members.remove(member);
            ++count;
        }
        if (count == 0) {
            return false;
        }
        return facebook.saveMembers(members, group);
    }
}
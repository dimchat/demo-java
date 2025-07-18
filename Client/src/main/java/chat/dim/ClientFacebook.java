/* license: https://mit-license.org
 *
 *  DIM-SDK : Decentralized Instant Messaging Software Development Kit
 *
 *                                Written in 2023 by Moky <albert.moky@gmail.com>
 *
 * ==============================================================================
 * The MIT License (MIT)
 *
 * Copyright (c) 2023 Albert Moky
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

import chat.dim.dbi.AccountDBI;
import chat.dim.protocol.BroadcastUtils;
import chat.dim.protocol.Bulletin;
import chat.dim.protocol.EntityType;
import chat.dim.protocol.ID;

/**
 *  Client Facebook with Address Name Service
 */
public abstract class ClientFacebook extends CommonFacebook {

    public ClientFacebook(AccountDBI database) {
        super(database);
    }

    //
    //  GroupDataSource
    //

    @Override
    public ID getFounder(ID group) {
        assert group.isGroup() : "group ID error: " + group;
        // check broadcast group
        if (group.isBroadcast()) {
            // founder of broadcast group
            return BroadcastUtils.getBroadcastFounder(group);
        }
        // check bulletin document
        Bulletin doc = getBulletin(group);
        if (doc == null) {
            // the owner(founder) should be set in the bulletin document of group
            return null;
        }
        // check local storage
        ID user = database.getFounder(group);
        if (user != null) {
            // got from local storage
            return user;
        }
        // get from bulletin document
        user = doc.getFounder();
        assert user != null : "founder not designated for group: " + group;
        return user;
    }

    @Override
    public ID getOwner(ID group) {
        assert group.isGroup() : "group ID error: " + group;
        // check broadcast group
        if (group.isBroadcast()) {
            // owner of broadcast group
            return BroadcastUtils.getBroadcastOwner(group);
        }
        // check bulletin document
        Bulletin doc = getBulletin(group);
        if (doc == null) {
            // the owner(founder) should be set in the bulletin document of group
            return null;
        }
        // check local storage
        ID user = database.getOwner(group);
        if (user != null) {
            // got from local storage
            return user;
        }
        // check group type
        if (EntityType.GROUP.equals(group.getType())) {
            // Polylogue owner is its founder
            user = getFounder(group);
            if (user == null) {
                user = doc.getFounder();
            }
        }
        assert user != null : "owner not found for group: " + group;
        return user;
    }

    @Override
    public List<ID> getMembers(ID group) {
        assert group.isGroup() : "group ID error: " + group;
        // check broadcast group
        if (group.isBroadcast()) {
            // members of broadcast group
            return BroadcastUtils.getBroadcastMembers(group);
        }
        // check group owner
        ID owner = getOwner(group);
        if (owner == null) {
            // assert false : "group owner not found: " + group;
            return null;
        }
        EntityChecker checker = getEntityChecker();
        // check local storage
        List<ID> members = database.getMembers(group);
        checker.checkMembers(group, members);
        if (members == null || members.isEmpty()) {
            members = new ArrayList<>();
            members.add(owner);
        } else {
            assert members.get(0).equals(owner) : "group owner must be the first member: " + group;
        }
        return members;
    }

    @Override
    public List<ID> getAssistants(ID group) {
        assert group.isGroup() : "group ID error: " + group;
        // check bulletin document
        Bulletin doc = getBulletin(group);
        if (doc == null) {
            // the assistants should be set in the bulletin document of group
            return null;
        }
        // check local storage
        List<ID> bots = database.getAssistants(group);
        if (bots != null && !bots.isEmpty()) {
            // got from local storage
            return bots;
        }
        // get from bulletin document
        return doc.getAssistants();
    }

    //
    //  Organizational Structure
    //

    @Override
    public List<ID> getAdministrators(ID group) {
        assert group.isGroup() : "group ID error: " + group;
        // check bulletin document
        Bulletin doc = getBulletin(group);
        if (doc == null) {
            // the administrators should be set in the bulletin document
            return null;
        }
        // the 'administrators' should be saved into local storage
        // when the newest bulletin document received,
        // so we must get them from the local storage only,
        // not from the bulletin document.
        return database.getAdministrators(group);
    }

    @Override
    public boolean saveAdministrators(List<ID> members, ID group) {
        return database.saveAdministrators(members, group);
    }

    @Override
    public boolean saveMembers(List<ID> newMembers, ID group) {
        return database.saveMembers(newMembers, group);
    }

    //
    //  Address Name Service
    //
    public static AddressNameServer ans = null;

}

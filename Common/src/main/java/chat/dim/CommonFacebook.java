/* license: https://mit-license.org
 *
 *  DIM-SDK : Decentralized Instant Messaging Software Development Kit
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
import java.util.Date;
import java.util.List;

import chat.dim.core.Barrack;
import chat.dim.crypto.DecryptKey;
import chat.dim.crypto.EncryptKey;
import chat.dim.crypto.SignKey;
import chat.dim.crypto.VerifyKey;
import chat.dim.dbi.AccountDBI;
import chat.dim.mkm.DocumentUtils;
import chat.dim.mkm.MetaUtils;
import chat.dim.mkm.User;
import chat.dim.protocol.Bulletin;
import chat.dim.protocol.Document;
import chat.dim.protocol.DocumentType;
import chat.dim.protocol.ID;
import chat.dim.protocol.Meta;
import chat.dim.protocol.Visa;
import chat.dim.type.Duration;
import chat.dim.utils.Log;

/**
 *  Common Facebook with Database
 */
public abstract class CommonFacebook extends Facebook {

    protected final AccountDBI database;

    private CommonArchivist barrack;
    private EntityChecker checker;

    private User current;

    public CommonFacebook(AccountDBI db) {
        super();
        database = db;
        barrack = null;
        checker = null;
        // current user
        current = null;
    }

    public AccountDBI getDatabase() {
        return database;
    }

    public EntityChecker getEntityChecker() {
        return checker;
    }
    public void setEntityChecker(EntityChecker checker) {
        this.checker = checker;
    }

    @Override
    protected CommonArchivist getBarrack() {
        return barrack;
    }
    public void setBarrack(CommonArchivist archivist) {
        barrack = archivist;
    }

    //
    //  Current User
    //

    public User getCurrentUser() {
        // Get current user (for signing and sending message)
        User user = current;
        if (user != null) {
            return user;
        }
        Barrack barrack = getBarrack();
        List<User> localUsers = barrack.getLocalUsers();
        if (localUsers == null || localUsers.isEmpty()) {
            return null;
        }
        user = localUsers.get(0);
        current = user;
        return user;
    }

    public void setCurrentUser(User user) {
        if (user.getDataSource() == null) {
            user.setDataSource(this);
        }
        current = user;
    }

    @Override
    public User selectLocalUser(ID receiver) {
        List<User> localUsers = barrack.getLocalUsers();
        if (localUsers == null) {
            localUsers = new ArrayList<>();
        }
        User user = current;
        if (user != null/* && !localUsers.contains(user)*/) {
            localUsers.add(0, user);
        }
        //
        //  1.
        //
        if (localUsers.isEmpty()) {
            assert false : "local users should not be empty";
            return null;
        } else if (receiver.isBroadcast()) {
            // broadcast message can be decrypted by anyone, so
            // just return current user here
            return localUsers.get(0);
        }
        //
        //  2.
        //
        if (receiver.isUser()) {
            // personal message
            for (User item : localUsers) {
                if (receiver.equals(item.getIdentifier())) {
                    // DISCUSS: set this item to be current user?
                    return item;
                }
            }
        } else {
            // group message (recipient not designated)
            assert receiver.isGroup() : "receiver error: " + receiver;
            // the messenger will check group info before decrypting message,
            // so we can trust that the group's meta & members MUST exist here.
            List<ID> members = getMembers(receiver);
            assert !members.isEmpty() : "members not found: " + receiver;
            for (User item : localUsers) {
                if (members.contains(item.getIdentifier())) {
                    // DISCUSS: set this item to be current user?
                    return item;
                }
            }
        }
        // not me?
        return null;
    }

    //
    //  Documents
    //

    public Document getDocument(ID identifier, String type) {
        List<Document> documents = getDocuments(identifier);
        Document doc = DocumentUtils.lastDocument(documents, type);
        // compatible for document type
        if (doc == null && DocumentType.VISA.equals(type)) {
            doc = DocumentUtils.lastDocument(documents, DocumentType.PROFILE);
        }
        return doc;
    }

    public Visa getVisa(ID user) {
        // assert user.isUser() : "user ID error: " + user;
        List<Document> documents = getDocuments(user);
        return DocumentUtils.lastVisa(documents);
    }


    public Bulletin getBulletin(ID group) {
        // assert group.isGroup() : "group ID error: " + group;
        List<Document> documents = getDocuments(group);
        return DocumentUtils.lastBulletin(documents);
    }

    public String getName(ID identifier) {
        String type;
        if (identifier.isUser()) {
            type = DocumentType.VISA;
        } else if (identifier.isGroup()) {
            type = DocumentType.BULLETIN;
        } else {
            type = "*";
        }
        // get name from document
        Document doc = getDocument(identifier, type);
        if (doc != null) {
            String name = doc.getName();
            if (name != null && name.length() > 0) {
                return name;
            }
        }
        // get name from ID
        return Anonymous.getName(identifier);
    }

    // -------- Storage

    @Override
    public boolean saveMeta(Meta meta, ID identifier) {
        //
        //  1. check valid
        //
        if (!checkMeta(meta, identifier)) {
            assert false : "meta not valid: " + identifier;
            Log.warning("meta not valid: " + identifier);
            return false;
        }
        //
        //  2. check duplicated
        //
        Meta old = getMeta(identifier);
        if (old != null) {
            Log.debug("meta duplicated: " + identifier);
            return true;
        }
        //
        //  3. save into database
        //
        return database.saveMeta(meta, identifier);
    }

    protected boolean checkMeta(Meta meta, ID identifier) {
        return meta.isValid() && MetaUtils.matches(identifier, meta);
    }

    @Override
    public boolean saveDocument(Document doc) {
        //
        //  1. check valid
        //
        boolean valid = checkDocumentValid(doc);
        if (!valid) {
            assert false : "document not valid: " + doc.getIdentifier();
            Log.warning("document not valid: " + doc.getIdentifier());
            return false;
        }
        //
        //  2. check expired
        //
        if (checkDocumentExpired(doc)) {
            Log.info("drop expired document: " + doc.getIdentifier());
            return false;
        }
        //
        //  3. save into database
        //
        return database.saveDocument(doc);
    }

    protected boolean checkDocumentValid(Document doc) {
        ID identifier = doc.getIdentifier();
        Date docTime = doc.getTime();
        // check document time
        if (docTime == null) {
            //assert false : "document error: " + doc;
            Log.warning("document without time: " + identifier);
        } else {
            // calibrate the clock
            // make sure the document time is not in the far future
            Date nearFuture = Duration.ofMinutes(30).addTo(new Date());
            if (docTime.after(nearFuture)) {
                assert false : "document time error: " + docTime + ", " + doc;
                Log.error("document time error: " + docTime + ", " + identifier);
                return false;
            }
        }
        // check valid
        return doc.isValid() || verifyDocument(doc);
    }

    protected boolean verifyDocument(Document doc) {
        ID identifier = doc.getIdentifier();
        Meta meta = getMeta(identifier);
        if (meta == null) {
            Log.warning("failed to get meta: " + identifier);
            return false;
        }
        return doc.verify(meta.getPublicKey());
    }

    protected boolean checkDocumentExpired(Document doc) {
        ID identifier = doc.getIdentifier();
        String type = DocumentUtils.getDocumentType(doc);
        // check old documents with type
        List<Document> documents = getDocuments(identifier);
        Document old = DocumentUtils.lastDocument(documents, type);
        return old != null && DocumentUtils.isExpired(doc, old);
    }

    @Override
    protected VerifyKey getMetaKey(ID user) {
        Meta meta = getMeta(user);
        if (meta != null/* && meta.isValid()*/) {
            return meta.getPublicKey();
        }
        //throw new NullPointerException("failed to get meta for ID: " + user);
        return null;
    }

    @Override
    protected EncryptKey getVisaKey(ID user) {
        List<Document> documents = getDocuments(user);
        Visa doc = DocumentUtils.lastVisa(documents);
        if (doc != null/* && doc.isValid()*/) {
            return doc.getPublicKey();
        }
        return null;
    }

    //
    //  Entity DataSource
    //

    @Override
    public Meta getMeta(ID entity) {
        Meta meta = database.getMeta(entity);
        EntityChecker checker = getEntityChecker();
        checker.checkMeta(entity, meta);
        return meta;
    }

    @Override
    public List<Document> getDocuments(ID entity) {
        List<Document> docs = database.getDocuments(entity);
        EntityChecker checker = getEntityChecker();
        checker.checkDocuments(entity, docs);
        return docs;
    }

    //
    //  User DataSource
    //

    @Override
    public List<ID> getContacts(ID user) {
        return database.getContacts(user);
    }

    @Override
    public List<DecryptKey> getPrivateKeysForDecryption(ID user) {
        return database.getPrivateKeysForDecryption(user);
    }

    @Override
    public SignKey getPrivateKeyForSignature(ID user) {
        return database.getPrivateKeyForSignature(user);
    }

    @Override
    public SignKey getPrivateKeyForVisaSignature(ID user) {
        return database.getPrivateKeyForVisaSignature(user);
    }

    //
    //  Organizational Structure
    //

    public abstract List<ID> getAdministrators(ID group);
    public abstract boolean saveAdministrators(List<ID> members, ID group);

    public abstract boolean saveMembers(List<ID> newMembers, ID group);

}

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
package chat.dim.sqlite.account;

import java.util.List;

import chat.dim.dbi.DocumentDBI;
import chat.dim.format.TransportableData;
import chat.dim.protocol.Document;
import chat.dim.protocol.ID;
import chat.dim.sql.SQLConditions;
import chat.dim.sqlite.DataRowExtractor;
import chat.dim.sqlite.DataTableHandler;
import chat.dim.sqlite.DatabaseConnector;

public class DocumentTable extends DataTableHandler<Document> implements DocumentDBI {

    private DataRowExtractor<Document> extractor;

    public DocumentTable(DatabaseConnector connector) {
        super(connector);
        // lazy load
        extractor = null;
    }

    @Override
    protected DataRowExtractor<Document> getDataRowExtractor() {
        return extractor;
    }

    private boolean prepare() {
        if (extractor == null) {
            // create table if not exists
            String[] fields = {
                    "id INTEGER PRIMARY KEY AUTOINCREMENT",
                    "did VARCHAR(64)",
                    "type VARCHAR(8)",
                    "data TEXT",
                    "signature VARCHAR(88)",
            };
            if (!createTable(T_DOCUMENT, fields)) {
                // db error
                return false;
            }
            // prepare data row extractor
            extractor = (resultSet, index) -> {
                String did = resultSet.getString("did");
                String type = resultSet.getString("type");
                String data = resultSet.getString("data");
                String signature = resultSet.getString("signature");
                ID identifier = ID.parse(did);
                assert identifier != null : "did error: " + did;
                if (type == null || type.length() == 0) {
                    type = "*";
                }
                Document doc;
                if (data == null || signature == null) {
                    doc = Document.create(type, identifier);
                } else {
                    TransportableData ted = TransportableData.parse(signature);
                    assert ted != null : "signature error: " + signature;
                    doc = Document.create(type, identifier, data, ted);
                }
                if (type.equals("*")) {
                    if (identifier.isGroup()) {
                        type = Document.BULLETIN;
                    } else {
                        type = Document.VISA;
                    }
                }
                doc.put("type", type);
                return doc;
            };
        }
        return true;
    }
    private static final String[] SELECT_COLUMNS = {"did", "type", "data", "signature"};
    private static final String[] INSERT_COLUMNS = {"did", "type", "data", "signature"};
    private static final String T_DOCUMENT = "t_document";

    @Override
    public List<Document> getDocuments(ID entity) {
        if (!prepare()) {
            // db error
            return null;
        }
        SQLConditions conditions = new SQLConditions();
        conditions.addCondition(null, "did", "=", entity.toString());
        return select(T_DOCUMENT, SELECT_COLUMNS, conditions,
                null, null, "id DESC", -1, 0);
    }

    @Override
    public boolean saveDocument(Document doc) {
        if (!prepare()) {
            // db error
            return false;
        }
        ID identifier = doc.getIdentifier();
        String type = doc.getType();
        String data = doc.getString("data", "");
        String signature = doc.getString("signature", "");
        Object[] values = {identifier.toString(), type, data, signature};
        return insert(T_DOCUMENT, INSERT_COLUMNS, values) > 0;
    }

    @Override
    public boolean clearDocuments(ID entity, String type) {
        if (!prepare()) {
            // db error
            return false;
        }
        SQLConditions conditions = new SQLConditions();
        conditions.addCondition(null, "did", "=", entity.toString());
        conditions.addCondition(SQLConditions.Relation.AND, "type", "=", type);
        return delete(T_DOCUMENT, conditions) >= 0;
    }

}

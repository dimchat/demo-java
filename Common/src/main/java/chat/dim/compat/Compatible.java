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
package chat.dim.compat;

import java.util.HashMap;
import java.util.Map;

import chat.dim.protocol.Command;
import chat.dim.protocol.Content;
import chat.dim.protocol.LoginCommand;
import chat.dim.protocol.MetaCommand;
import chat.dim.protocol.MetaVersion;
import chat.dim.protocol.NameCard;
import chat.dim.protocol.ReceiptCommand;
import chat.dim.protocol.ReliableMessage;
import chat.dim.type.Converter;
import chat.dim.utils.Log;

// TODO: remove after all server/client upgraded
public interface Compatible {

    @SuppressWarnings("unchecked")
    static void fixMetaAttachment(ReliableMessage rMsg) {
        Object meta = rMsg.get("meta");
        if (meta != null) {
            fixMetaVersion((Map<String, Object>) meta);
        }
    }
    static void fixMetaVersion(Map<String, Object> meta) {
        Object type = meta.get("type");
        if (type == null) {
            type = meta.get("version");
        } else if (type instanceof String && !meta.containsKey("algorithm")) {
            // TODO: check number
            if (((String) type).length() > 2) {
                meta.put("algorithm", type);
            }
        }
        int version = MetaVersion.parseInt(type, 0);
        if (version > 0) {
            meta.put("type", version);
            meta.put("version", version);
        }
    }

    @SuppressWarnings("unchecked")
    static void fixVisaAttachment(ReliableMessage rMsg) {
        Object visa = rMsg.get("visa");
        if (visa != null) {
            fixID((Map<String, Object>) visa);
        }
    }
    static void fixID(Map<String, Object> doc) {
        Object did = doc.get("did");
        if (did != null) {
            doc.put("ID", did);
        } else {
            did = doc.get("ID");
            if (did != null) {
                doc.put("did", did);
            }
        }
    }

    static void fixType(Content content) {
        Object type = content.get("type");
        if (type instanceof String) {
            try {
                int num = Converter.getInt(type, -1);
                if (num >= 0) {
                    content.put("type", num);
                }
            } catch (Exception error) {
                Log.warning("failed to convert content type: " + type);
            }
        }
    }

    static Content fixContent(Content content) {
        // 0. fix 'type'
        fixType(content);
        // 1. fix 'ID'
        if (content instanceof NameCard) {
            // ID <-> did
            fixID(content);
        }
        return content;
    }

    @SuppressWarnings("unchecked")
    static Command fixCommand(Command content) {
        // 0. fix 'type'
        fixType(content);
        // 1. fix 'cmd'
        content = fixCmd(content);
        // 2. fix other commands
        if (content instanceof ReceiptCommand) {
            fixReceiptCommand((ReceiptCommand) content);
        } else if (content instanceof MetaCommand) {
            Object meta = content.get("meta");
            if (meta != null) {
                fixMetaVersion((Map<String, Object>) meta);
            }
            Object doc = content.get("document");
            if (doc != null) {
                fixID((Map<String, Object>) doc);
            }
            // ID <-> did
            fixID(content);
        } else if (content instanceof LoginCommand) {
            // ID <-> did
            fixID(content);
        }
        // OK
        //return Command.parse(content.toMap());
        return content;
    }
    static Command fixCmd(Command content) {
        Object cmd = content.get("cmd");
        if (cmd == null) {
            cmd = content.get("command");
            content.put("cmd", cmd);
        } else if (!content.containsKey("command")) {
            content.put("command", cmd);
            content = Command.parse(content.toMap());
        }
        return content;
    }

    @SuppressWarnings("unchecked")
    static void fixReceiptCommand(ReceiptCommand content) {
        // check for v2.0
        Object origin = content.get("origin");
        if (origin == null) {
            // check for v1.0
            Object envelope = content.get("envelope");
            if (envelope == null) {
                // check for older version
                if (!content.containsKey("sender")) {
                    // this receipt contains no envelope info,
                    // no need to fix it.
                    return;
                }
                // older version
                Map<String, Object> env = new HashMap<>();
                env.put("sender",    content.get("sender"));
                env.put("receiver",  content.get("receiver"));
                env.put("time",      content.get("time"));
                env.put("sn",        content.get("sn"));
                env.put("signature", content.get("signature"));
                content.put("origin", env);
                content.put("envelope", env);
            } else {
                // (v1.0)
                // compatible with v2.0
                content.put("origin", envelope);
                // compatible with older version
                copyReceiptValues((Map<String, Object>) envelope, content);
            }
        } else {
            // (v2.0)
            // compatible with v1.0
            content.put("envelope", origin);
            // compatible with older version
            copyReceiptValues((Map<String, Object>) origin, content);
        }
    }
    static void copyReceiptValues(Map<String, Object> fromOrigin, ReceiptCommand toContent) {
        String name;
        for (Map.Entry<String, Object> entry : fromOrigin.entrySet()) {
            name = entry.getKey();
            if (name == null) {
                // should not happen
                continue;
            } else if (name.equals("type")) {
                continue;
            } else if (name.equals("time")) {
                continue;
            }
            toContent.put(name, entry.getValue());
        }
    }
}

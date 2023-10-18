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
package chat.dim.cpu;

import java.util.List;

import chat.dim.CommonFacebook;
import chat.dim.CommonMessenger;
import chat.dim.Facebook;
import chat.dim.Messenger;
import chat.dim.group.GroupDelegate;
import chat.dim.group.GroupHelper;
import chat.dim.group.GroupHistoryBuilder;
import chat.dim.protocol.Content;
import chat.dim.protocol.HistoryCommand;
import chat.dim.protocol.ReliableMessage;

public class HistoryCommandProcessor extends BaseCommandProcessor {

    protected final GroupDelegate delegate;
    protected final GroupHelper helper;
    protected final GroupHistoryBuilder builder;

    public HistoryCommandProcessor(Facebook facebook, Messenger messenger) {
        super(facebook, messenger);
        delegate = createDelegate();
        helper = createHelper();
        builder = createBuilder();
    }

    // override for customized data source
    protected GroupDelegate createDelegate() {
        return new GroupDelegate(getFacebook(), getMessenger());
    }

    // override for customized helper
    protected GroupHelper createHelper() {
        return new GroupHelper(delegate);
    }
    // override for customized builder
    protected GroupHistoryBuilder createBuilder() {
        return new GroupHistoryBuilder(delegate);
    }

    @Override
    protected CommonFacebook getFacebook() {
        Facebook facebook = super.getFacebook();
        assert facebook instanceof CommonFacebook : "facebook error: " + facebook;
        return (CommonFacebook) facebook;
    }

    @Override
    protected CommonMessenger getMessenger() {
        Messenger messenger = super.getMessenger();
        assert messenger instanceof CommonMessenger : "messenger error: " + messenger;
        return (CommonMessenger) messenger;
    }

    @Override
    public List<Content> process(Content content, ReliableMessage rMsg) {
        assert content instanceof HistoryCommand : "history command error: " + content;
        HistoryCommand command = (HistoryCommand) content;
        return respondReceipt("Command not support.", rMsg.getEnvelope(), command, newMap(
                "template", "History command (name: ${command}) not support yet!",
                "replacements", newMap(
                        "command", command.getCmd()
                )
        ));
    }

}

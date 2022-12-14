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

import chat.dim.cpu.BaseContentProcessor;
import chat.dim.cpu.ContentProcessor;
import chat.dim.cpu.ContentProcessorCreator;
import chat.dim.cpu.HandshakeCommandProcessor;
import chat.dim.cpu.LoginCommandProcessor;
import chat.dim.cpu.ReceiptCommandProcessor;
import chat.dim.protocol.HandshakeCommand;
import chat.dim.protocol.LoginCommand;
import chat.dim.protocol.ReceiptCommand;

public class ClientContentProcessorCreator extends ContentProcessorCreator {

    public ClientContentProcessorCreator(Facebook facebook, Messenger messenger) {
        super(facebook, messenger);
    }

    @Override
    public ContentProcessor createContentProcessor(int type) {
        // default
        if (type == 0) {
            return new BaseContentProcessor(getFacebook(), getMessenger());
        }
        // others
        return super.createContentProcessor(type);
    }

    @Override
    public ContentProcessor createCommandProcessor(int type, String name) {
        // handshake
        if (name.equals(HandshakeCommand.HANDSHAKE)) {
            return new HandshakeCommandProcessor(getFacebook(), getMessenger());
        }
        // login
        if (name.equals(LoginCommand.LOGIN)) {
            return new LoginCommandProcessor(getFacebook(), getMessenger());
        }
        // receipt
        if (name.equals(ReceiptCommand.RECEIPT)) {
            return new ReceiptCommandProcessor(getFacebook(), getMessenger());
        }
        // others
        return super.createCommandProcessor(type, name);
    }
}

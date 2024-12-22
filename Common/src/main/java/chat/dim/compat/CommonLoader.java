/* license: https://mit-license.org
 * ==============================================================================
 * The MIT License (MIT)
 *
 * Copyright (c) 2024 Albert Moky
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

import chat.dim.PluginLoader;
import chat.dim.core.CoreLoader;
import chat.dim.protocol.AnsCommand;
import chat.dim.protocol.BlockCommand;
import chat.dim.protocol.Command;
import chat.dim.protocol.HandshakeCommand;
import chat.dim.protocol.LoginCommand;
import chat.dim.protocol.MuteCommand;
import chat.dim.protocol.ReportCommand;

public class CommonLoader extends CoreLoader {

    private final PluginLoader pluginLoader;

    public CommonLoader(PluginLoader pluginLoader) {
        super();
        this.pluginLoader = pluginLoader;
    }

    @Override
    public void run() {
        super.run();
        pluginLoader.run();
    }

    /**
     *  Command factories
     */
    @Override
    protected void registerCommandFactories() {
        super.registerCommandFactories();

        // Handshake
        Command.setFactory(HandshakeCommand.HANDSHAKE, HandshakeCommand::new);
        // Login
        Command.setFactory(LoginCommand.LOGIN, LoginCommand::new);
        // Report
        Command.setFactory(ReportCommand.REPORT, ReportCommand::new);
        // Mute
        Command.setFactory(MuteCommand.MUTE, MuteCommand::new);
        // Block
        Command.setFactory(BlockCommand.BLOCK, BlockCommand::new);
        // ANS
        Command.setFactory(AnsCommand.ANS, AnsCommand::new);
    }

}
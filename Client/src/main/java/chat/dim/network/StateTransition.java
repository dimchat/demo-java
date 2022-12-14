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
package chat.dim.network;

import java.util.Date;

import chat.dim.fsm.BaseTransition;

/**
 *  Session States
 *  ~~~~~~~~~~~~~~
 *
 *      +--------------+                +------------------+
 *      |  0.Default   | .............> |   1.Connecting   |
 *      +--------------+                +------------------+
 *          A       A       ................:       :
 *          :       :       :                       :
 *          :       :       V                       V
 *          :   +--------------+        +------------------+
 *          :   |   5.Error    | <..... |   2.Connected    |
 *          :   +--------------+        +------------------+
 *          :       A       A                   A   :
 *          :       :       :................   :   :
 *          :       :                       :   :   V
 *      +--------------+                +------------------+
 *      |  4.Running   | <............. |  3.Handshaking   |
 *      +--------------+                +------------------+
 *
 *  Transitions
 *  ~~~~~~~~~~~
 *
 *      0.1 - when session ID was set, change state 'default' to 'connecting';
 *
 *      1.2 - when connection built, change state 'connecting' to 'connected';
 *      1.5 - if connection failed, change state 'connecting' to 'error';
 *
 *      2.3 - if no error occurs, change state 'connected' to 'handshaking';
 *      2.5 - if connection lost, change state 'connected' to 'error';
 *
 *      3.2 - if handshaking expired, change state 'handshaking' to 'connected';
 *      3.4 - when session key was set, change state 'handshaking' to 'running';
 *      3.5 - if connection lost, change state 'handshaking' to 'error';
 *
 *      4.0 - when session ID/key erased, change state 'running' to 'default';
 *      4.5 - when connection lost, change state 'running' to 'error';
 *
 *      5.0 - when connection reset, change state 'error' to 'default'.
 */
public abstract class StateTransition extends BaseTransition<StateMachine> {

    StateTransition(String target) {
        super(target);
    }

    boolean isExpired(SessionState state) {
        long now = new Date().getTime();
        return 0 < state.timestamp && state.timestamp < (now - 30000);
    }
}

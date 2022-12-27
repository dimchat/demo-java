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

import java.util.Date;
import java.util.Locale;

import chat.dim.core.Packer;
import chat.dim.core.Processor;
import chat.dim.dbi.SessionDBI;
import chat.dim.fsm.Delegate;
import chat.dim.mkm.Station;
import chat.dim.mkm.User;
import chat.dim.network.ClientSession;
import chat.dim.network.SessionState;
import chat.dim.network.StateMachine;
import chat.dim.network.StateTransition;
import chat.dim.protocol.EntityType;
import chat.dim.protocol.ID;
import chat.dim.skywalker.Runner;

public abstract class Terminal extends Runner implements Delegate<StateMachine, StateTransition, SessionState> {

    private final CommonFacebook facebook;
    private final SessionDBI database;

    private ClientMessenger messenger;
    private StateMachine fsm;  // session state
    private long lastTime;

    public Terminal(CommonFacebook barrack, SessionDBI sdb) {
        super();
        facebook = barrack;
        database = sdb;
        messenger = null;
        fsm = null;
        // last online time
        lastTime = new Date().getTime();
    }

    // "zh-CN"
    public String getLanguage() {
        return Locale.getDefault().getLanguage();
    }

    // "DIM"
    public abstract String getDisplayName();

    // "1.0.1"
    public abstract String getVersionName();

    // "4.0"
    public abstract String getSystemVersion();

    // "HMS"
    public abstract String getSystemModel();

    // "hammerhead"
    public abstract String getSystemDevice();

    // "HUAWEI"
    public abstract String getDeviceBrand();

    // "hammerhead"
    public abstract String getDeviceBoard();

    // "HUAWEI"
    public abstract String getDeviceManufacturer();

    /**
     *  format: "DIMP/1.0 (Linux; U; Android 4.1; zh-CN) DIMCoreKit/1.0 (Terminal, like WeChat) DIM-by-GSP/1.0.1"
     */
    public String getUserAgent() {
        String model = getSystemModel();
        String device = getSystemDevice();
        String sysVersion = getSystemVersion();
        String lang = getLanguage();

        String appName = getDisplayName();
        String appVersion = getVersionName();

        return String.format("DIMP/1.0 (%s; U; %s %s; %s)" +
                        " DIMCoreKit/1.0 (Terminal, like WeChat) %s-by-MOKY/%s",
                model, device, sysVersion, lang, appName, appVersion);
    }

    public SessionDBI getDatabase() {
        return database;
    }

    public CommonFacebook getFacebook() {
        return facebook;
    }

    public ClientMessenger getMessenger() {
        return messenger;
    }

    public ClientSession getSession() {
        ClientMessenger transceiver = messenger;
        if (transceiver == null) {
            return null;
        }
        return transceiver.getSession();
    }

    public SessionState getState() {
        StateMachine machine = fsm;
        if (machine == null) {
            return null;
        }
        return machine.getCurrentState();
    }

    public boolean isAlive() {
        // if more than 10 minutes no online command sent
        // means this terminal is dead
        Date now = new Date();
        return now.getTime() < (lastTime + 600 * 1000);
    }

    public ClientMessenger connect(String host, int port) {
        ClientMessenger old = messenger;
        if (old != null) {
            ClientSession session = old.getSession();
            if (session.isActive()) {
                // current session is active
                Station station = session.getStation();
                if (station.getHost().equals(host) && station.getPort() == port) {
                    // same target
                    return old;
                }
            }
        }
        // stop the machine & remove old messenger
        StateMachine machine = fsm;
        if (machine != null) {
            machine.stop();
            fsm = null;
        }
        // create new messenger with session
        Station station = createStation(host, port);
        ClientSession session = createSession(station);
        messenger = createMessenger(session, facebook);
        // create packer, processor for messenger
        // they have weak references to facebook & messenger
        messenger.setPacker(createPacker(facebook, messenger));
        messenger.setProcessor(createProcessor(facebook, messenger));
        // set weak reference to messenger
        session.setMessenger(messenger);
        // create & start state machine
        machine = new StateMachine(session);
        machine.setDelegate(this);
        machine.start();
        fsm = machine;
        return messenger;
    }
    protected Station createStation(String host, int port) {
        Station station = new Station(host, port);
        station.setDataSource(facebook);
        return station;
    }
    protected ClientSession createSession(Station station) {
        ClientSession session = new ClientSession(station, database);
        // set current user for handshaking
        User user = facebook.getCurrentUser();
        if (user != null) {
            session.setIdentifier(user.getIdentifier());
        }
        session.start();
        return session;
    }
    protected Packer createPacker(CommonFacebook facebook, ClientMessenger messenger) {
        return new ClientMessagePacker(facebook, messenger);
    }
    protected Processor createProcessor(CommonFacebook facebook, ClientMessenger messenger) {
        return new ClientMessageProcessor(facebook, messenger);
    }
    protected abstract ClientMessenger createMessenger(ClientSession session, CommonFacebook facebook);

    public boolean login(ID current) {
        ClientSession session = getSession();
        if (session == null) {
            return false;
        } else {
            session.setIdentifier(current);
            return true;
        }
    }

    public void enterBackground() {
        ClientMessenger transceiver = messenger;
        StateMachine machine = fsm;
        if (transceiver == null || machine == null) {
            // not connect
            return;
        }
        // check signed in user
        ClientSession session = transceiver.getSession();
        ID uid = session.getIdentifier();
        if (uid != null) {
            // already signed in, check session state
            SessionState state = machine.getCurrentState();
            if (state.equals(SessionState.RUNNING)) {
                // report client state
                transceiver.reportOffline(uid);
                idle(512);
            }
        }
        // pause the session
        machine.pause();
    }
    public void enterForeground() {
        ClientMessenger transceiver = messenger;
        StateMachine machine = fsm;
        if (transceiver == null || machine == null) {
            // not connect
            return;
        }
        // resume the session
        machine.resume();
        // check signed in user
        ClientSession session = transceiver.getSession();
        ID uid = session.getIdentifier();
        if (uid != null) {
            // already signed in, wait a while to check session state
            idle(512);
            if (getState().equals(SessionState.RUNNING)) {
                // report client state
                transceiver.reportOnline(uid);
            }
        }
    }

    public void start() {
        Thread thread = new Thread(this);
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void finish() {
        // stop state machine
        StateMachine machine = fsm;
        if (machine != null) {
            machine.stop();
            fsm = null;
        }
        // stop session in messenger
        Messenger transceiver = messenger;
        if (transceiver != null) {
            ClientSession session = getSession();
            session.stop();
            messenger = null;
        }
        super.finish();
    }

    @Override
    protected void idle() {
        idle(60 * 1000);
    }

    @Override
    public boolean process() {
        long now = new Date().getTime();
        if (now < (lastTime + 300 * 1000)) {
            // last sent within 5 minutes
            return false;
        }
        // check session state
        ClientMessenger messenger = getMessenger();
        if (messenger == null) {
            // not connect
            return false;
        }
        ClientSession session = messenger.getSession();
        ID uid = session.getIdentifier();
        if (uid == null || !getState().equals(SessionState.RUNNING)) {
            // handshake not accepted
            return false;
        }
        // report every 5 minutes to keep user online
        if (EntityType.STATION.equals(uid.getType())) {
            // a station won't login to another station, if here is a station,
            // it must be a station bridge for roaming messages, we just send
            // report command to the target station to keep session online.
            messenger.reportOnline(uid);
        } else {
            // send login command to everyone to provide more information.
            // this command can keep the user online too.
            messenger.broadcastLogin(uid, getUserAgent());
        }
        // update last online time
        lastTime = now;
        return false;
    }

    //
    //  FSM Delegate
    //

    @Override
    public void enterState(SessionState next, StateMachine ctx) {
        // called before state changed
    }

    @Override
    public void exitState(SessionState previous, StateMachine ctx) {
        // called after state changed
        ClientMessenger messenger = getMessenger();
        SessionState current = ctx.getCurrentState();
        if (current == null) {
            return;
        }
        if (current.equals(SessionState.HANDSHAKING)) {
            // start handshake
            messenger.handshake(null);
        } else if (current.equals(SessionState.RUNNING)) {
            // broadcast current meta & visa document to all stations
            messenger.handshakeSuccess();
            // update last online time
            lastTime = new Date().getTime();
        }
    }

    @Override
    public void pauseState(SessionState current, StateMachine ctx) {

    }

    @Override
    public void resumeState(SessionState current, StateMachine ctx) {
        // TODO: clear session key for re-login?
    }
}

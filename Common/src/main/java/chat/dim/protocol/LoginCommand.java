/* license: https://mit-license.org
 * ==============================================================================
 * The MIT License (MIT)
 *
 * Copyright (c) 2020 Albert Moky
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
package chat.dim.protocol;

import java.util.HashMap;
import java.util.Map;

import chat.dim.dkd.cmd.BaseCommand;
import chat.dim.mkm.ServiceProvider;
import chat.dim.mkm.Station;

/**
 *  Login Command
 *
 *  <blockquote><pre>
 *  data format: {
 *      type : 0x88,
 *      sn   : 123,
 *
 *      command  : "login",
 *      time     : 0,
 *      //---- client info ----
 *      did      : "{UserID}",
 *      device   : "DeviceID",  // (optional)
 *      agent    : "UserAgent", // (optional)
 *      //---- server info ----
 *      station  : {
 *          did  : "{StationID}",
 *          host : "{IP}",
 *          port : 9394
 *      },
 *      provider : {
 *          did  : "{SP_ID}"
 *      }
 *  }
 *  </pre></blockquote>
 */
public class LoginCommand extends BaseCommand {

    public static final String LOGIN     = "login";

    public LoginCommand(Map<String, Object> content) {
        super(content);
    }

    public LoginCommand(ID identifier) {
        super(LOGIN);
        assert identifier != null : "login ID should not empty";
        put("did", identifier.toString());
    }

    //
    //  Client Info
    //

    // user ID
    public ID getIdentifier() {
        return ID.parse(get("did"));
    }

    // device ID
    public String getDevice() {
        return getString("device", null);
    }
    public void setDevice(String device) {
        put("device", device);
    }

    // user-agent
    public String getAgent() {
        return getString("agent", null);
    }
    public void setAgent(String agent) {
        put("agent", agent);
    }

    //
    //  Server Info
    //

    // station
    @SuppressWarnings("unchecked")
    public Map<String, Object> getStation() {
        return (Map<String, Object>) get("station");
    }
    @SuppressWarnings("rawtypes")
    public void setStation(Map station) {
        put("station", station);
    }
    public void setStation(Station station) {
        Map<String, Object> info = new HashMap<>();
        ID sid = station.getIdentifier();
        if (!sid.isBroadcast()) {
            info.put("ID", sid.toString());
            info.put("did", sid.toString());
        }
        info.put("host", station.getHost());
        info.put("port", station.getPort());
        //put("station", info);
        setStation(info);
    }

    // service provider
    @SuppressWarnings("unchecked")
    public Map<String, Object> getProvider() {
        return (Map<String, Object>) get("provider");
    }
    public void setProvider(Map<?, ?> provider) {
        put("provider", provider);
    }
    public void setProvider(ID provider) {
        Map<String, Object> info = new HashMap<>();
        info.put("ID", provider.toString());
        info.put("did", provider.toString());
        //put("provider", info);
        setProvider(info);
    }
    public void setProvider(ServiceProvider provider) {
        setProvider(provider.getIdentifier());
    }
}

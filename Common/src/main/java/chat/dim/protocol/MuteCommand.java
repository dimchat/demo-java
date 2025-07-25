/* license: https://mit-license.org
 *
 *  DIMP : Decentralized Instant Messaging Protocol
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
package chat.dim.protocol;

import java.util.List;
import java.util.Map;

import chat.dim.dkd.cmd.BaseCommand;

/**
 *  Mute Command
 *
 *  <blockquote><pre>
 *  data format: {
 *      type : 0x88,
 *      sn   : 123,
 *
 *      command : "mute",
 *      list    : []      // mute-list
 *  }
 *  </pre></blockquote>
 */
public class MuteCommand extends BaseCommand {

    public static final String MUTE   = "mute";

    // mute-list
    private List<ID> muteList = null;

    public MuteCommand(Map<String, Object> content) {
        super(content);
    }

    /**
     *  Send mute-list
     *
     * @param list - mute list
     */
    public MuteCommand(List<ID> list) {
        super(MUTE);
        setMuteList(list);
    }

    /**
     *  Query mute-list
     */
    public MuteCommand() {
        super(MUTE);
    }

    //-------- setters/getters --------

    @SuppressWarnings("unchecked")
    public List<ID> getMuteList() {
        if (muteList == null) {
            Object list = get("list");
            if (list != null) {
                muteList = ID.convert((List<String>) list);
            }
        }
        return muteList;
    }

    public void setMuteList(List<ID> list) {
        if (list == null) {
            remove("list");
        } else {
            put("list", ID.revert(list));
        }
        muteList = list;
    }
}

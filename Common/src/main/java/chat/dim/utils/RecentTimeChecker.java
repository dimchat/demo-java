/* license: https://mit-license.org
 *
 *  DIM-SDK : Decentralized Instant Messaging Software Development Kit
 *
 *                                Written in 2023 by Moky <albert.moky@gmail.com>
 *
 * ==============================================================================
 * The MIT License (MIT)
 *
 * Copyright (c) 2023 Albert Moky
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
package chat.dim.utils;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class RecentTimeChecker <K> {

    private final Map<K, Date> times = new HashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public boolean setLastTime(K key, Date now) {
        if (now == null) {
            assert false : "recent time empty: " + key;
            return false;
        }
        // TODO: calibration clock

        boolean changed = false;
        Lock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            Date last = times.get(key);
            if (last == null || last.before(now)) {
                times.put(key, now);
                changed = true;
            }
        } finally {
            writeLock.unlock();
        }
        return changed;
    }

    public boolean isExpired(K key, Date now) {
        if (now == null) {
            // assert false : "recent time empty: " + key;
            return true;
        }
        Date last = times.get(key);
        return last != null && last.after(now);
    }

}

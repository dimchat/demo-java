/* license: https://mit-license.org
 *
 *  HTTP
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
package chat.dim.http;

import java.lang.ref.WeakReference;
import java.net.URL;

/**
 *  Download Request
 *  ~~~~~~~~~~~~~~~~
 *  waiting task
 *
 *  properties:
 *      url      - remote URL
 *      path     - temporary file path
 *      delegate - callback
 */
public class DownloadRequest extends AbstractTask {

    private final WeakReference<DownloadDelegate> delegateRef;

    public DownloadRequest(URL url, String path, DownloadDelegate delegate) {
        super(url, path);
        delegateRef = new WeakReference<>(delegate);
    }

    public DownloadDelegate getDelegate() {
        return delegateRef.get();
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof DownloadTask) {
            if (super.equals(other)) {
                // same object
                return true;
            }
            DownloadTask task = (DownloadTask) other;
            return url.equals(task.url);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return url.hashCode();
    }

    @Override
    public String toString() {
        return String.format("<%s url=\"%s\" path=\"%s\" />",
                this.getClass().getName(), url, path);
    }
}

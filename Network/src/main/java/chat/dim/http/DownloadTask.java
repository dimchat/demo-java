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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import chat.dim.filesys.Paths;
import chat.dim.utils.Log;

/**
 *  Download Task
 *  ~~~~~~~~~~~~~
 *  running task
 *
 *  properties:
 *      url      - remote URL
 *      path     - temporary file path
 *      delegate - HTTP client
 */
public class DownloadTask extends DownloadRequest implements Runnable {

    public DownloadTask(URL url, String path, DownloadDelegate delegate) {
        super(url, path, delegate);
    }

    private static IOError download(URL url, String filePath) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(false);
        connection.setDoInput(true);
        connection.setRequestMethod("GET");
        connection.setUseCaches(true);
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(5000);
        //connection.connect();

        IOError error;

        int code = connection.getResponseCode();
        if (code == 200) {
            try (InputStream inputStream = connection.getInputStream()) {
                File file = new File(filePath);
                try (FileOutputStream outputStream = new FileOutputStream(file)) {
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, len);
                    }
                    outputStream.flush();
                    // OK
                    error = null;
                }
            }
        } else {
            // TODO: fetch error response
            error = new IOError(null);
        }
        //connection.disconnect();

        return error;
    }

    @Override
    public void run() {
        DownloadDelegate delegate = getDelegate();
        touch();
        IOError error;
        try {
            // 1. prepare directory
            String dir = Paths.parent(path);
            assert dir != null : "download file path error: " + path;
            if (!Paths.mkdirs(dir)) {
                onError();
                error = new IOError(new IOException("failed to create dir: " + dir));
                delegate.onDownloadError(this, error);
            }
            // 2. start download
            error = download(url, path);
            if (error == null) {
                onSuccess();
                delegate.onDownloadSuccess(this, path);
            } else {
                onError();
                delegate.onDownloadError(this, error);
            }
        } catch (IOException e) {
            //e.printStackTrace();
            Log.error("failed to download: " + url);
            onError();
            delegate.onDownloadFailed(this, e);
        } finally {
            onFinished();
        }
    }
}

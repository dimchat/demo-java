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
package chat.dim.sqlite;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import chat.dim.filesys.Paths;

public class DatabaseConnector {

    private final String dbFilePath;
    private Connection connection;

    public DatabaseConnector(String sqliteFilePath) {
        super();
        dbFilePath = sqliteFilePath;
        // lazy load
        connection = null;
    }

    @Override
    protected void finalize() throws Throwable {
        destroy();
        super.finalize();
    }

    public void destroy() throws SQLException {
        Connection conn = connection;
        if (conn != null) {
            connection = null;
            conn.close();
        }
    }

    public Connection getConnection() throws SQLException {
        Connection conn = connection;
        if (conn == null) {
            if (!Paths.exists(dbFilePath)) {
                // make sure parent directory exists
                String dir = Paths.parent(dbFilePath);
                Paths.mkdirs(dir);
            }
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbFilePath);
            connection = conn;
        }
        return conn;
    }
}

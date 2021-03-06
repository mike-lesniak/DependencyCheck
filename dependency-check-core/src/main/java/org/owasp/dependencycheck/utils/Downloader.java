/*
 * This file is part of dependency-check-core.
 *
 * Dependency-check-core is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * Dependency-check-core is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * dependency-check-core. If not, see http://www.gnu.org/licenses/.
 *
 * Copyright (c) 2012 Jeremy Long. All Rights Reserved.
 */
package org.owasp.dependencycheck.utils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * A utility to download files from the Internet.
 *
 * @author Jeremy Long (jeremy.long@owasp.org)
 */
public final class Downloader {

    /**
     * Private constructor for utility class.
     */
    private Downloader() {
    }

    /**
     * Retrieves a file from a given URL and saves it to the outputPath.
     *
     * @param url the URL of the file to download.
     * @param outputPath the path to the save the file to.
     * @throws DownloadFailedException is thrown if there is an error
     * downloading the file.
     */
    public static void fetchFile(URL url, String outputPath) throws DownloadFailedException {
        fetchFile(url, outputPath, false);
    }

    /**
     * Retrieves a file from a given URL and saves it to the outputPath.
     *
     * @param url the URL of the file to download.
     * @param outputPath the path to the save the file to.
     * @param unzip true/false indicating that the file being retrieved is
     * gzipped and if true, should be uncompressed before writing to the file.
     * @throws DownloadFailedException is thrown if there is an error
     * downloading the file.
     */
    public static void fetchFile(URL url, String outputPath, boolean unzip) throws DownloadFailedException {
        final File f = new File(outputPath);
        fetchFile(url, f, unzip);
    }

    /**
     * Retrieves a file from a given URL and saves it to the outputPath.
     *
     * @param url the URL of the file to download.
     * @param outputPath the path to the save the file to.
     * @throws DownloadFailedException is thrown if there is an error
     * downloading the file.
     */
    public static void fetchFile(URL url, File outputPath) throws DownloadFailedException {
        fetchFile(url, outputPath, false);
    }

    /**
     * Retrieves a file from a given URL and saves it to the outputPath.
     *
     * @param url the URL of the file to download.
     * @param outputPath the path to the save the file to.
     * @param unzip true/false indicating that the file being retrieved is
     * gzipped and if true, should be uncompressed before writing to the file.
     * @throws DownloadFailedException is thrown if there is an error
     * downloading the file.
     */
    public static void fetchFile(URL url, File outputPath, boolean unzip) throws DownloadFailedException {
        HttpURLConnection conn = null;
        try {
            conn = Downloader.getConnection(url);
            conn.setRequestProperty("Accept-Encoding", "gzip, deflate");
            conn.connect();
        } catch (IOException ex) {
            try {
                if (conn != null) {
                    conn.disconnect();
                }
            } finally {
                conn = null;
            }
            throw new DownloadFailedException("Error downloading file.", ex);
        }
        final String encoding = conn.getContentEncoding();

        BufferedOutputStream writer = null;
        InputStream reader = null;
        try {
            if (unzip || (encoding != null && "gzip".equalsIgnoreCase(encoding))) {
                reader = new GZIPInputStream(conn.getInputStream());
            } else if (encoding != null && "deflate".equalsIgnoreCase(encoding)) {
                reader = new InflaterInputStream(conn.getInputStream());
            } else {
                reader = conn.getInputStream();
            }

            writer = new BufferedOutputStream(new FileOutputStream(outputPath));
            final byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = reader.read(buffer)) > 0) {
                writer.write(buffer, 0, bytesRead);
            }
        } catch (Exception ex) {
            throw new DownloadFailedException("Error saving downloaded file.", ex);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                    writer = null;
                } catch (Exception ex) {
                    Logger.getLogger(Downloader.class.getName()).log(Level.FINEST,
                            "Error closing the writer in Downloader.", ex);
                }
            }
            if (reader != null) {
                try {
                    reader.close();
                    reader = null;
                } catch (Exception ex) {

                    Logger.getLogger(Downloader.class.getName()).log(Level.FINEST,
                            "Error closing the reader in Downloader.", ex);
                }
            }
            try {
                conn.disconnect();
            } finally {
                conn = null;
            }
        }
    }

    /**
     * Makes an HTTP Head request to retrieve the last modified date of the
     * given URL.
     *
     * @param url the URL to retrieve the timestamp from
     * @return an epoch timestamp
     * @throws DownloadFailedException is thrown if an exception occurs making
     * the HTTP request
     */
    public static long getLastModified(URL url) throws DownloadFailedException {
        HttpURLConnection conn = null;
        long timestamp = 0;
        try {
            conn = Downloader.getConnection(url);
            conn.setRequestMethod("HEAD");
            conn.connect();
            timestamp = conn.getLastModified();
        } catch (Exception ex) {
            throw new DownloadFailedException("Error making HTTP HEAD request.", ex);
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } finally {
                    conn = null;
                }
            }
        }
        return timestamp;
    }

    /**
     * Utility method to get an HttpURLConnection. If the app is configured to
     * use a proxy this method will retrieve the proxy settings and use them
     * when setting up the connection.
     *
     * @param url the url to connect to
     * @return an HttpURLConnection
     * @throws DownloadFailedException thrown if there is an exception
     */
    private static HttpURLConnection getConnection(URL url) throws DownloadFailedException {
        HttpURLConnection conn = null;
        Proxy proxy = null;
        final String proxyUrl = Settings.getString(Settings.KEYS.PROXY_URL);
        try {
            if (proxyUrl != null) {
                final int proxyPort = Settings.getInt(Settings.KEYS.PROXY_PORT);
                final SocketAddress addr = new InetSocketAddress(proxyUrl, proxyPort);
                proxy = new Proxy(Proxy.Type.HTTP, addr);
                conn = (HttpURLConnection) url.openConnection(proxy);
            } else {
                conn = (HttpURLConnection) url.openConnection();
            }
            //added a default timeout of 20000
            //if (Settings.getString(Settings.KEYS.CONNECTION_TIMEOUT) != null) {
            final int timeout = Settings.getInt(Settings.KEYS.CONNECTION_TIMEOUT, 60000);
            conn.setConnectTimeout(timeout);
            //}
        } catch (IOException ex) {
            if (conn != null) {
                try {
                    conn.disconnect();
                } finally {
                    conn = null;
                }
            }
            throw new DownloadFailedException("Error getting connection.", ex);
        }
        return conn;
    }
}

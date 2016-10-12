/*
 * Licensed to Diennea S.r.l. under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Diennea S.r.l. licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package docet;

import docet.model.DocetPackageDescriptor;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Whitelist;
import org.jsoup.select.Elements;


/**
 * Class containing general-purpose utility (lib) methods.
 *
 * @author matteo.casadei
 *
 */
public final class DocetUtils {

    private static final Logger LOGGER = Logger.getLogger(DocetUtils.class.getName());
    /**
     *
     */
    private DocetUtils() {
    }

    public static DocetPackageDescriptor generatePackageDescriptor(final File pathToPackage)
            throws IOException {
        final DocetPackageDescriptor packageDesc = new DocetPackageDescriptor();
        final Document descriptor = Jsoup.parseBodyFragment(
            new String(DocetUtils.fastReadFile(pathToPackage.toPath().resolve("descriptor.html")), "UTF-8"));
        final Elements divDescriptor = descriptor.select("[lang]");
        for (final Element divForlang : divDescriptor) {
            final String lang = divForlang.attr("lang");
            final String title = divForlang.select("h1").get(0).text();
            final String desc = divForlang.select("p").get(0).text();
            packageDesc.addLabelForLang(lang, title);
            packageDesc.addAbstractForLang(lang, desc);
        }
        return packageDesc;
    }

    private static final boolean USE_DIRECT_BUFFER = true;

    public static byte[] fastReadFile(Path f) throws IOException {
        try (SeekableByteChannel c = Files.newByteChannel(f, StandardOpenOption.READ)) {
            int len = (int) Files.size(f);
            if (USE_DIRECT_BUFFER) {
                ByteBuffer buffer = ByteBuffer.allocateDirect(len);
                try {
                    long res = c.read(buffer);
                    if (res != len) {
                        throw new IOException("not all file " + f.toAbsolutePath() + " was read with NIO len=" + len + " writeen=" + res);
                    }
                    buffer.flip();
                    byte[] result = new byte[len];
                    buffer.get(result);
                    return result;
                } finally {
                    forceReleaseBuffer(buffer);
                }
            } else {
                ByteBuffer buffer = ByteBuffer.allocate(len);
                long res = c.read(buffer);
                if (res != len) {
                    throw new IOException("not all file " + f.toAbsolutePath() + " was read with NIO len=" + len + " read=" + res);
                }
                byte[] result = buffer.array();
                return result;
            }
        }
    }

    private static final Class<? extends ByteBuffer> SUN_DIRECT_BUFFER;
    private static final Method SUN_BUFFER_CLEANER;
    private static final Method SUN_CLEANER_CLEAN;

    static {
        if (!USE_DIRECT_BUFFER) {
            SUN_DIRECT_BUFFER = null;
            SUN_BUFFER_CLEANER = null;
            SUN_CLEANER_CLEAN = null;
        } else {
            Method bufferCleaner = null;
            Method cleanerClean = null;
            Class<? extends ByteBuffer> BUF_CLASS = null;
            try {
                BUF_CLASS = (Class<? extends ByteBuffer>) Class.forName("sun.nio.ch.DirectBuffer", true, Thread.currentThread().getContextClassLoader());
                if (BUF_CLASS != null) {
                    bufferCleaner = BUF_CLASS.getMethod("cleaner", (Class[]) null);
                    Class<?> cleanClazz = Class.forName("sun.misc.Cleaner", true, Thread.currentThread().getContextClassLoader());
                    cleanerClean = cleanClazz.getMethod("clean", (Class[]) null);
                }
            } catch (Throwable t) {
                LOGGER.log(Level.SEVERE, "Fast reading file error", t);
            }
            SUN_DIRECT_BUFFER = BUF_CLASS;
            SUN_BUFFER_CLEANER = bufferCleaner;
            SUN_CLEANER_CLEAN = cleanerClean;
        }
    }

    private static void forceReleaseBuffer(ByteBuffer buffer) {
        if (buffer == null || !buffer.isDirect()) {
            return;
        }
        if (SUN_DIRECT_BUFFER != null && SUN_DIRECT_BUFFER.isAssignableFrom(buffer.getClass())) {
            try {
                Object cleaner = SUN_BUFFER_CLEANER.invoke(buffer, (Object[]) null);
                SUN_CLEANER_CLEAN.invoke(cleaner, (Object[]) null);
            } catch (Throwable t) {
                LOGGER.log(Level.SEVERE, "Error on releasing buffer", t);
            }
        }
    }

    /**
     * Given a search text for searching a specific page on a given package (in
     * the form "packageid:pageid"), returns the array [packageid, pageid].
     * 
     * @param searchText
     *            a searchText in the form packageid:pageid
     * 
     * @return an array of two elements (packageid, pageid) or an empty array in
     *         case the search text does not comply to the format
     *         (packageid:pageid).
     */
    public static String[] parsePageIdSearchToTokens(final String searchText) {
        final String[] res = searchText.trim().split(":");
        if (res.length == 2) {
            return res;
        }
        return new String[]{};
    }

    public static String cleanPageText(final String dirtyPageText) {
        final Whitelist whiteList = Whitelist.relaxed();
        whiteList.addAttributes(":all", "class", "id", "href", "docetref", "title", "package", "src");
        whiteList.removeProtocols("a", "href", "ftp", "http", "https", "mailto");
        whiteList.removeProtocols("img", "src", "http", "https");
        whiteList.preserveRelativeLinks(true);
        return Jsoup.clean(dirtyPageText, whiteList);
    }
}

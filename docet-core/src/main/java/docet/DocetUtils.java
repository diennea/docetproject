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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.logging.Logger;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Whitelist;
import org.jsoup.select.Elements;

import docet.model.DocetPackageDescriptor;
import io.netty.util.internal.PlatformDependent;
import java.util.ArrayList;
import java.util.List;


/**
 * Class containing general-purpose utility (lib) methods.
 *
 * @author matteo.casadei
 *
 */
public final class DocetUtils {

    private static final Logger LOGGER = Logger.getLogger(DocetUtils.class.getName());

    private static final boolean USE_DIRECT_BUFFER = true;


    /**
     *
     */
    private DocetUtils() {
    }

    public static DocetPackageDescriptor generatePackageDescriptor(final Path pathToPackage)
            throws IOException {
        final DocetPackageDescriptor packageDesc = new DocetPackageDescriptor();
        final Document descriptor = Jsoup.parseBodyFragment(
            new String(DocetUtils.fastReadFile(pathToPackage.resolve("descriptor.html")), "UTF-8"));
        final Elements divDescriptor = descriptor.select("[lang]");
        for (final Element divForlang : divDescriptor) {
            final String lang = divForlang.attr("lang");
            final String title = divForlang.select("h1").get(0).text();
            final String desc = divForlang.select("p").get(0).text();
            packageDesc.addLabelForLang(lang, title);
            packageDesc.addAbstractForLang(lang, desc);
            final String fallbackLang = divForlang.attr("reference-language");
            if (!fallbackLang.isEmpty()) {
                packageDesc.addfallbackLangForLang(lang, fallbackLang);
            }
        }
        return packageDesc;
    }

    public static byte[] readStream(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int nRead;
        byte[] data = new byte[1024];

        while ((nRead = in.read(data, 0, data.length)) != -1) {
          buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return buffer.toByteArray();
    }

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
                    ((Buffer)buffer).flip();
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
                return buffer.array();
            }
        }
    }

    private static void forceReleaseBuffer(ByteBuffer buffer) {
        if (buffer == null || !buffer.isDirect()) {
            return;
        }
        PlatformDependent.freeDirectBuffer(buffer);
    }

    public static Path resolve(Path base, String one, String... more) {
        if (more == null || more.length < 1) {
            return base.resolve(one);
        }
        StringBuilder builder = new StringBuilder(one);
        for(String block : more) {
            builder.append('/').append(block);
        }
        return base.resolve(builder.toString());
    }


    public static String cleanPageText(final String dirtyPageText, boolean enableIframe) {
        final Whitelist whiteList = Whitelist.relaxed();
        if (enableIframe) {
            whiteList.addTags("iframe");
            whiteList.addAttributes("iframe", "src", "frameborder", "width", "height", "allowfullscreen", "allow");
        }
        whiteList.addAttributes(":all", "class", "id", "href", "docetref", "title", "package", "src");
        whiteList.removeProtocols("a", "href", "ftp", "http", "https", "mailto");
        whiteList.removeProtocols("img", "src", "http", "https");
        whiteList.preserveRelativeLinks(true);
        return Jsoup.clean(dirtyPageText, whiteList);//.replaceAll("<img ([^</]+)>", "<img $1 />");
    }

    /**
     * Convert a String hex color in #FFF or #FFFFFF format, returning an array of 3 integers representing the
     * corresponding RGB color (in this order).
     *
     * @param hex
     * @return
     */
    public static Integer[] convertHexColorToRgb(final String hex) {
        if (!hex.matches("^#[abcdefABCDEF0-9]{6}|#[abcdefABCDEF0-9]{3}")) {
            return new Integer[]{};
        }
        final String hexCode;
        if (hex.length() == 4) {
            char[] chars = hex.toCharArray();
            char[] normalizedChars = new char[] { '#', chars[1], chars[1], chars[2], chars[2], chars[3], chars[3]};
            hexCode = new String(normalizedChars);
        } else {
            hexCode = hex;
        }
        return new Integer[] {
            Integer.valueOf( hexCode.substring( 1, 3 ), 16 ),
            Integer.valueOf( hexCode.substring( 3, 5 ), 16 ),
            Integer.valueOf( hexCode.substring( 5, 7 ), 16 )
        };
    }
    
    public static boolean extensionAllowed(String extension) {
        List<String> forbiddenExtensions = new ArrayList<>();
        
        for (ForbiddenExtensions fe : ForbiddenExtensions.values()) {
            forbiddenExtensions.add(fe.extension());
        }
        
        return forbiddenExtensions.contains(extension) == false;
    }
   
    private enum ForbiddenExtensions {
        JPEG("jpeg"),
        JPG("jpg");

        private final String extension;

        private ForbiddenExtensions(final String extension) {
            this.extension = extension;
        }

        public String extension() {
            return extension;
        }
    }
    
}

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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import docet.model.DocetPackageDescriptor;
import docet.model.PackageDescriptionResult;


/**
 * Class containing general-purpose utility (lib) methods.
 *
 * @author matteo.casadei
 *
 */
public final class DocetUtils {

    /**
     *
     */
    private DocetUtils() {
    }

    public static DocetPackageDescriptor generatePackageDescriptor(final File pathToPackage)
            throws UnsupportedEncodingException, IOException {
        final DocetPackageDescriptor packageDesc = new DocetPackageDescriptor();
        final Document descriptor = Jsoup.parseBodyFragment(new String(DocetUtils.fastReadFile(pathToPackage.toPath().resolve("descriptor.html")), "UTF-8"));
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

    /**
     *
     * @param zipFile
     * @param dataDir
     */
    public static void unzipDocetData(final File zipFile, final File dataDir) throws Exception {
        if (!dataDir.isDirectory()) {
            throw new Exception("Expected directory '" + dataDir + "' does not exist. Please create it!");
        }

        if (!zipFile.isFile()) {
            throw new Exception("Expected Docet archive '" + zipFile + "' not found!");
        }

        final File safeFile = dataDir.toPath().resolve("docetdata.unzipping").toFile();

        // checking up if a previous unzipping activity was left in progress
        // (aka in an non-consistent state)
        System.out.println("Checking file: " + safeFile.getAbsolutePath());
        if (safeFile.isFile()) {
            System.out.println("File exists (probably) due to a previous installation. Cleaning up " + dataDir.getAbsolutePath());
            deleteDirectory(dataDir.toPath());
            System.out.println("Deleteting " + safeFile.getAbsolutePath());
            safeFile.delete();
        }
        safeFile.createNewFile();
        long startTS = System.currentTimeMillis();
        System.out.println("Unzipping " + zipFile.getAbsolutePath() + " to " + dataDir.getAbsolutePath());
        unzip(zipFile, dataDir);
        long stopTS = System.currentTimeMillis();
        System.out.println("Deleteting " + safeFile.getAbsolutePath());
        safeFile.delete();
    }

    private static void unzip(File f, File destination) throws IOException {
        try (ZipFile zipFile = new ZipFile(f)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = (ZipEntry) entries.nextElement();

                if (entry.isDirectory()) {
                    continue;
                }

                File outFile = new File(destination, entry.getName()).getAbsoluteFile();
                File parentFile = outFile.getParentFile();
                if (!parentFile.isDirectory()) {
                    parentFile.mkdirs();
                }

                try (FileOutputStream rawOut = new FileOutputStream(outFile); BufferedOutputStream bOut = new BufferedOutputStream(rawOut)) {
                    IOUtils.copyLarge(zipFile.getInputStream(entry), bOut);
                }
            }
        }
    }

    private static void deleteDirectory(Path f) throws IOException {
        if (Files.isDirectory(f)) {
            Files.walkFileTree(f, new FileDeleter());
            Files.deleteIfExists(f);
        } else if (Files.isRegularFile(f)) {
            throw new IOException("name " + f.toAbsolutePath() + " is not a directory");
        }
    }

    private static class FileDeleter extends SimpleFileVisitor<Path> {

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
//            println("delete file " + file.toAbsolutePath());
            Files.delete(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
//            println("delete directory " + dir);
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
        }
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
                t.printStackTrace();
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
                t.printStackTrace();
            }
        }
    }
}

package io.github.bovinemagnet.adocfoundry.tool;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.gradle.api.GradleException;

/**
 * The entry names of a release archive, exactly as stored, so that an unsafe name is refused
 * before anything is extracted.
 */
final class ArchiveEntries {

    private static final int BLOCK = 512;

    private ArchiveEntries() {}

    /**
     * Fails when any entry has an absolute path or a {@code ..} segment.
     *
     * @param archive the archive
     * @param zip whether it is a zip rather than a gzipped tar
     * @param asset the archive's release name, for the message
     */
    static void requireSafe(Path archive, boolean zip, String asset) throws IOException {
        for (String name : zip ? zipNames(archive) : tarNames(archive)) {
            if (isUnsafe(name)) {
                throw new GradleException(asset + " holds the entry '" + name + "', which would be written outside"
                        + " the folder it is extracted to, so nothing was extracted. Report this to"
                        + " https://github.com/bovinemagnet/AdocFoundry/issues.");
            }
        }
    }

    static boolean isUnsafe(String name) {
        if (name.startsWith("/") || name.startsWith("\\") || name.matches("^[A-Za-z]:.*")) {
            return true;
        }
        for (String segment : name.split("[/\\\\]")) {
            if (segment.equals("..")) {
                return true;
            }
        }
        return false;
    }

    private static List<String> zipNames(Path archive) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            return zip.stream().map(ZipEntry::getName).toList();
        }
    }

    /** Reads ustar headers, with GNU long names and pax {@code path} records. */
    private static List<String> tarNames(Path archive) throws IOException {
        List<String> names = new ArrayList<>();
        try (InputStream in = new BufferedInputStream(new GZIPInputStream(Files.newInputStream(archive)))) {
            byte[] header = new byte[BLOCK];
            String longName = null;
            while (readBlock(in, header)) {
                if (isZero(header)) {
                    break;
                }
                long size = size(header);
                char type = (char) header[156];
                if (type == 'L' || type == 'x' || type == 'g') {
                    String data = new String(readData(in, size), StandardCharsets.UTF_8);
                    if (type == 'L') {
                        longName = trimNul(data);
                    } else if (type == 'x') {
                        String path = paxPath(data);
                        if (path != null) {
                            longName = path;
                        }
                    }
                    continue;
                }
                String name = text(header, 0, 100);
                if (text(header, 257, 5).equals("ustar")) {
                    String prefix = text(header, 345, 155);
                    if (!prefix.isEmpty()) {
                        name = prefix + "/" + name;
                    }
                }
                names.add(longName != null ? longName : name);
                longName = null;
                skip(in, padded(size));
            }
        }
        return names;
    }

    private static String paxPath(String records) {
        // Each record is "<length> <key>=<value>\n".
        for (String record : records.split("\n")) {
            int space = record.indexOf(' ');
            String keyValue = space < 0 ? record : record.substring(space + 1);
            if (keyValue.startsWith("path=")) {
                return keyValue.substring("path=".length());
            }
        }
        return null;
    }

    private static long size(byte[] header) {
        if ((header[124] & 0x80) != 0) {
            long value = 0;
            for (int i = 125; i < 136; i++) {
                value = (value << 8) | (header[i] & 0xff);
            }
            return value;
        }
        String octal = text(header, 124, 12).strip();
        return octal.isEmpty() ? 0 : Long.parseLong(octal, 8);
    }

    private static String text(byte[] header, int offset, int length) {
        int end = offset;
        while (end < offset + length && header[end] != 0) {
            end++;
        }
        return new String(header, offset, end - offset, StandardCharsets.UTF_8);
    }

    private static String trimNul(String text) {
        int nul = text.indexOf('\0');
        return nul < 0 ? text : text.substring(0, nul);
    }

    private static boolean isZero(byte[] block) {
        for (byte b : block) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    private static long padded(long size) {
        return (size + BLOCK - 1) / BLOCK * BLOCK;
    }

    private static boolean readBlock(InputStream in, byte[] block) throws IOException {
        int read = in.readNBytes(block, 0, BLOCK);
        if (read == 0) {
            return false;
        }
        if (read < BLOCK) {
            throw new IOException("The archive ends inside a header");
        }
        return true;
    }

    private static byte[] readData(InputStream in, long size) throws IOException {
        if (size > 1 << 20) {
            throw new IOException("A tar extended header of " + size + " bytes is too large");
        }
        byte[] data = in.readNBytes((int) size);
        skip(in, padded(size) - size);
        return data;
    }

    private static void skip(InputStream in, long count) throws IOException {
        in.skipNBytes(count);
    }
}

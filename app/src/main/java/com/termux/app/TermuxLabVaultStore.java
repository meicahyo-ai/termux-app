package com.termux.app;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Standalone Termux Lab Markdown vault. No Obsidian APIs, services, or account. */
public final class TermuxLabVaultStore {
    private static final int MAX_NOTE_BYTES = 1024 * 1024;
    private static final Pattern WIKILINK = Pattern.compile("\\[\\[([^]\\r\\n]+)\\]\\]");
    private final Path root;

    public TermuxLabVaultStore(File termuxHome) throws IOException {
        root = new File(termuxHome, "TermuxLabVault").toPath();
        if (Files.isSymbolicLink(root)) throw new IOException("Vault symlink tidak diizinkan");
        Files.createDirectories(root);
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Vault bukan direktori");
    }

    public File folder() { return root.toFile(); }

    private static String cleanPath(String value, boolean markdown) throws IOException {
        if (value == null) throw new IOException("Nama kosong");
        String name = Normalizer.normalize(value.trim().replace('\\', '/'), Normalizer.Form.NFC);
        if (name.isEmpty() || name.length() > 200 || name.startsWith("/") || name.indexOf('\0') >= 0)
            throw new IOException("Nama tidak valid");
        String[] parts = name.split("/", -1);
        if (parts.length > 5) throw new IOException("Folder terlalu dalam");
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".") || part.equals("..") || part.startsWith(".") || part.length() > 100 || part.contains(":"))
                throw new IOException("Path tidak diperbolehkan: " + part);
        }
        if (markdown && !name.toLowerCase(Locale.ROOT).endsWith(".md")) name += ".md";
        return name;
    }

    private Path resolve(String input, boolean note) throws IOException {
        Path path = root.resolve(cleanPath(input, note)).normalize();
        if (!path.startsWith(root) || path.equals(root)) throw new IOException("Path keluar dari vault");
        Path walk = root;
        for (Path part : root.relativize(path)) {
            walk = walk.resolve(part);
            if (Files.isSymbolicLink(walk)) throw new IOException("Symlink tidak diizinkan");
        }
        return path;
    }

    public synchronized void createFolder(String folderName) throws IOException {
        Path path = resolve(folderName, false);
        Files.createDirectories(path);
    }

    public synchronized String create(String title) throws IOException {
        Path path = resolve(title, true);
        if (!Files.isDirectory(path.getParent(), LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Buat foldernya terlebih dahulu");
        Files.createFile(path); // Atomic: never overwrites an existing note.
        return root.relativize(path).toString().replace(File.separatorChar, '/');
    }

    public synchronized String read(String name) throws IOException {
        Path path = resolve(name, true);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > MAX_NOTE_BYTES)
            throw new IOException("Catatan tidak tersedia atau terlalu besar");
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    public synchronized String fingerprint(String name) throws IOException { return sha256(read(name)); }

    public static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) builder.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Refuse stale writes; replace using a same-folder temporary file. */
    public synchronized String save(String name, String markdown, String expectedFingerprint) throws IOException {
        if (markdown == null || markdown.getBytes(StandardCharsets.UTF_8).length > MAX_NOTE_BYTES)
            throw new IOException("Catatan melebihi batas 1 MiB");
        Path target = resolve(name, true);
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Catatan tidak ditemukan");
        if (expectedFingerprint == null || !fingerprint(name).equals(expectedFingerprint))
            throw new IOException("Catatan berubah di luar editor. Simpan dibatalkan agar tidak menimpa perubahan.");
        Path temp = Files.createTempFile(target.getParent(), ".tl-note-", ".tmp");
        try {
            Files.write(temp, markdown.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return sha256(markdown);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** Deletion is explicit: moved to a private trash folder for recovery. */
    public synchronized void trash(String name) throws IOException {
        Path path = resolve(name, true);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Catatan tidak ditemukan");
        Path trash = root.resolve(".trash");
        Files.createDirectories(trash);
        String targetName = System.currentTimeMillis() + "-" + path.getFileName();
        Files.move(path, trash.resolve(targetName));
    }

    public synchronized List<String> list() throws IOException {
        ArrayList<String> names = new ArrayList<>();
        collect(root.toFile(), names, 0);
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private void collect(File folder, List<String> names, int depth) throws IOException {
        if (depth > 4 || names.size() >= 2000) return;
        File[] files = folder.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.getName().startsWith(".") || Files.isSymbolicLink(file.toPath())) continue;
            if (file.isDirectory()) collect(file, names, depth + 1);
            else if (file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".md")) {
                names.add(root.relativize(file.toPath()).toString().replace(File.separatorChar, '/'));
                if (names.size() >= 2000) break;
            }
        }
    }

    public synchronized List<String> search(String query) throws IOException {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return list();
        ArrayList<String> result = new ArrayList<>();
        for (String name : list()) {
            if (name.toLowerCase(Locale.ROOT).contains(needle)) { result.add(name); continue; }
            try {
                if (read(name).toLowerCase(Locale.ROOT).contains(needle)) result.add(name);
            } catch (IOException ignored) { /* Ignore inaccessible/oversized notes in search. */ }
        }
        return result;
    }

    public static List<String> outgoing(String markdown) {
        ArrayList<String> links = new ArrayList<>();
        Matcher matcher = WIKILINK.matcher(markdown);
        while (matcher.find()) {
            String title = matcher.group(1).split("[|#]", 2)[0].trim();
            if (!title.isEmpty() && !links.contains(title)) links.add(title);
        }
        return links;
    }

    public synchronized List<String> backlinks(String name) throws IOException {
        String target = name.replaceAll("(?i)\\.md$", "");
        int slash = target.lastIndexOf('/');
        String base = slash >= 0 ? target.substring(slash + 1) : target;
        ArrayList<String> result = new ArrayList<>();
        for (String candidate : list()) {
            if (candidate.equals(name)) continue;
            try {
                for (String link : outgoing(read(candidate))) {
                    if (link.equalsIgnoreCase(target) || link.equalsIgnoreCase(base)) {
                        result.add(candidate);
                        break;
                    }
                }
            } catch (IOException ignored) { }
        }
        return result;
    }
}

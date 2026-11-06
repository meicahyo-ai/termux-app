package com.termux.app;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Deliberately restricted extension runtime: installable declarative Markdown
 * template packs only. NEVER executes plugin-provided Java, JavaScript, or Shell.
 */
public final class TermuxLabPluginStore {
    public static final class Pack {
        public final String id, name, version;
        public final boolean enabled;
        Pack(String id, String name, String version, boolean enabled) {
            this.id = id; this.name = name; this.version = version; this.enabled = enabled;
        }
    }
    public static final class Template {
        public final String pluginId, title, markdown;
        Template(String pluginId, String title, String markdown) {
            this.pluginId = pluginId; this.title = title; this.markdown = markdown;
        }
    }

    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9._-]{2,63}");
    private static final Pattern NAME = Pattern.compile("[a-zA-Z0-9_-]{1,70}\\.md");
    private static final int MAX_ARCHIVE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_UNPACKED_BYTES = 4 * 1024 * 1024;
    private static final int MAX_ENTRIES = 36;
    private final Path root;

    public TermuxLabPluginStore(File home) throws IOException {
        root = new File(home, ".termux-lab/extensions").toPath();
        if (Files.isSymbolicLink(root)) throw new IOException("Folder ekstensi symlink tidak diizinkan");
        Files.createDirectories(root);
    }

    private Path packPath(String id) throws IOException {
        if (id == null || !ID.matcher(id).matches()) throw new IOException("Plugin ID tidak valid");
        Path path = root.resolve(id);
        if (Files.isSymbolicLink(path)) throw new IOException("Plugin symlink tidak diizinkan");
        return path;
    }

    private static byte[] readLimited(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) {
            if (out.size() + n > maxBytes) throw new IOException("Ukuran plugin melebihi batas");
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    public synchronized Pack install(InputStream input) throws IOException {
        if (input == null) throw new IOException("File plugin tidak ditemukan");
        // Bound compressed bytes, entry count, and total uncompressed bytes.
        byte[] archive = readLimited(input, MAX_ARCHIVE_BYTES);
        Map<String, byte[]> files = new HashMap<>();
        int count = 0, total = 0;
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (!name.equals("plugin.properties") &&
                    !(name.startsWith("templates/") && NAME.matcher(name.substring("templates/".length())).matches()))
                    throw new IOException("Isi ZIP tidak diizinkan: " + name);
                if (++count > MAX_ENTRIES || files.containsKey(name)) throw new IOException("ZIP terlalu banyak/duplikat");
                byte[] bytes = readLimited(zip, 256 * 1024);
                total += bytes.length;
                if (total > MAX_UNPACKED_BYTES) throw new IOException("ZIP terlalu besar");
                files.put(name, bytes);
                zip.closeEntry();
            }
        }
        if (!files.containsKey("plugin.properties") || count < 2) throw new IOException("Manifest/template tidak ditemukan");
        Properties manifest = new Properties();
        try (java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(files.get("plugin.properties"))) {
            manifest.load(in);
        }
        String id = manifest.getProperty("id", "").trim();
        String name = manifest.getProperty("name", "").trim();
        String version = manifest.getProperty("version", "").trim();
        String type = manifest.getProperty("type", "").trim();
        if (!ID.matcher(id).matches() || name.isEmpty() || name.length() > 80 ||
            version.length() > 30 || !version.matches("[0-9]+\\.[0-9]+(\\.[0-9]+)?") ||
            !"template-pack".equals(type)) throw new IOException("Manifest plugin tidak sesuai");
        Path dest = packPath(id);
        if (Files.exists(dest, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Plugin sudah terpasang: " + id);
        Path stage = Files.createTempDirectory(root, ".staging-");
        boolean installed = false;
        try {
            for (Map.Entry<String, byte[]> f : files.entrySet()) {
                Path file = stage.resolve(f.getKey()).normalize();
                if (!file.startsWith(stage)) throw new IOException("Path plugin keluar folder");
                Files.createDirectories(file.getParent());
                Files.write(file, f.getValue());
            }
            Files.createFile(stage.resolve("enabled.flag"));
            Files.move(stage, dest);
            installed = true;
        } finally {
            if (!installed) deleteTree(stage);
        }
        return new Pack(id, name, version, true);
    }

    private Pack inspect(Path dir) throws IOException {
        Path manifestPath = dir.resolve("plugin.properties");
        if (Files.isSymbolicLink(dir) || !Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Manifest ekstensi tidak valid");
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(manifestPath)) { p.load(in); }
        String id = p.getProperty("id", "");
        if (!ID.matcher(id).matches() || !dir.getFileName().toString().equals(id) ||
            !"template-pack".equals(p.getProperty("type", ""))) throw new IOException("Ekstensi tidak sesuai");
        return new Pack(id, p.getProperty("name", id), p.getProperty("version", "?"),
            Files.exists(dir.resolve("enabled.flag"), LinkOption.NOFOLLOW_LINKS));
    }

    public synchronized List<Pack> list() {
        List<Pack> packs = new ArrayList<>();
        File[] dirs = root.toFile().listFiles();
        if (dirs == null) return packs;
        for (File dir : dirs) {
            if (!dir.getName().startsWith(".") && dir.isDirectory()) {
                try { packs.add(inspect(dir.toPath())); } catch (IOException ignored) { }
            }
        }
        Collections.sort(packs, (a,b) -> a.id.compareTo(b.id));
        return packs;
    }

    public synchronized void setEnabled(String id, boolean enabled) throws IOException {
        Path dir = packPath(id);
        inspect(dir);
        Path flag = dir.resolve("enabled.flag");
        if (enabled) { if (!Files.exists(flag)) Files.createFile(flag); }
        else Files.deleteIfExists(flag);
    }

    public synchronized List<Template> templates() throws IOException {
        List<Template> items = new ArrayList<>();
        for (Pack pack : list()) {
            if (!pack.enabled) continue;
            Path dir = packPath(pack.id).resolve("templates");
            File[] files = dir.toFile().listFiles();
            if (files == null) continue;
            for (File f : files) {
                if (!NAME.matcher(f.getName()).matches() || !f.isFile() || Files.isSymbolicLink(f.toPath()) || f.length() > 256 * 1024) continue;
                String title = f.getName().substring(0, f.getName().length() - 3).replace('_', ' ');
                items.add(new Template(pack.id, title, new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)));
            }
        }
        return items;
    }

    public synchronized void uninstall(String id) throws IOException {
        Path dir = packPath(id);
        inspect(dir);
        // Fail closed if anything unexpected was added since installation.
        File[] top = dir.toFile().listFiles();
        if (top == null) throw new IOException("Folder plugin tidak dapat diperiksa");
        for (File file : top) {
            if (!file.getName().equals("plugin.properties") && !file.getName().equals("enabled.flag") && !file.getName().equals("templates"))
                throw new IOException("Plugin punya file tambahan; pembatalan penghapusan");
            if (Files.isSymbolicLink(file.toPath())) throw new IOException("Symlink tidak diizinkan");
        }
        Path templates = dir.resolve("templates");
        if (Files.exists(templates)) {
            File[] files = templates.toFile().listFiles();
            if (files == null) throw new IOException("Folder templates tidak dapat dibaca");
            for (File file : files) {
                if (!NAME.matcher(file.getName()).matches() || !file.isFile() || Files.isSymbolicLink(file.toPath()))
                    throw new IOException("Ada file plugin yang tidak dikenal");
            }
        }
        deleteTree(dir);
    }

    private static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
        if (Files.isSymbolicLink(path)) throw new IOException("Symlink tidak boleh dihapus");
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            File[] children = path.toFile().listFiles();
            if (children == null) throw new IOException("Folder tidak dapat dibaca");
            for (File child : children) deleteTree(child.toPath());
        }
        Files.deleteIfExists(path);
    }
}

package com.jarvis.backup;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Base64;

import com.jarvis.audit.AuditService;
import com.jarvis.error.Exceptions.NotFoundException;
import com.jarvis.error.Exceptions.PathBlockedException;
import com.jarvis.explorer.FileSystemService;
import com.jarvis.secrets.VaultCrypto;

import lombok.RequiredArgsConstructor;

/**
 * Backup / Restore for the Jarvis Explorer (spec §8 Backup & Sync). Snapshots the
 * Explorer into a timestamped ZIP under {@code .backups/} and restores from one.
 * Real, local, no dependencies (java.util.zip). The transient {@code .backups} and
 * {@code .sandbox} folders are excluded; restore is zip-slip guarded.
 */
@Service
@RequiredArgsConstructor
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final String BACKUPS = ".backups";

    private final FileSystemService fs;
    private final AuditService audit;
    private final VaultCrypto crypto;
    private final JarvisBackupProperties props;

    public record BackupInfo(String name, long sizeBytes, String createdAt) {}

    /** Snapshot the whole Explorer into a new ZIP; returns its info. */
    public BackupInfo create() {
        return info(createZip());
    }

    /**
     * Snapshot, then AES-encrypt the archive (Secrets-Vault key) into a {@code .zip.enc} written to the
     * cloud-sync folder if configured (else the local backups dir) — so an off-box copy is never plaintext.
     * The plaintext zip is removed after encryption.
     */
    public BackupInfo createEncrypted() {
        Path zip = createZip();
        try {
            String cipher = crypto.encrypt(Base64.getEncoder().encodeToString(Files.readAllBytes(zip)));
            Path dest = destinationDir().resolve(zip.getFileName().toString() + ".enc");
            Files.writeString(dest, cipher);
            Files.deleteIfExists(zip);
            audit.record("BACKUP", "backup:encrypt", dest.getFileName().toString(), "OK", null);
            log.info("Encrypted backup written: {}", dest);
            return info(dest);
        } catch (IOException e) {
            throw new UncheckedIOException("Encrypted backup failed", e);
        }
    }

    private Path createZip() {
        Path root = fs.getRoot();
        Path zip = uniqueBackupPath(backupsDir());
        int files = 0;
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip));
             Stream<Path> walk = Files.walk(root)) {
            List<Path> entries = walk.filter(Files::isRegularFile).filter(p -> !excluded(root, p)).toList();
            for (Path p : entries) {
                String rel = root.relativize(p).toString();
                zos.putNextEntry(new ZipEntry(rel));
                Files.copy(p, zos);
                zos.closeEntry();
                files++;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Backup failed", e);
        }
        String name = zip.getFileName().toString();
        audit.record("BACKUP", "backup:create", name, "OK", files + " files");
        log.info("Backup created: {} ({} files)", name, files);
        return zip;
    }

    /** Where encrypted backups land — the configured cloud-sync folder, or the local backups dir. */
    private Path destinationDir() {
        String cloud = props.getCloudDir();
        if (cloud == null || cloud.isBlank()) {
            return backupsDir();
        }
        Path dir = cloud.startsWith("~")
                ? Path.of(System.getProperty("user.home") + cloud.substring(1)).toAbsolutePath().normalize()
                : Path.of(cloud).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create cloud backup folder", e);
        }
        return dir;
    }

    public List<BackupInfo> list() {
        Path dir = backupsDir();
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".zip"))
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .map(this::info).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Restore files from a backup ZIP into the Explorer (overwrites; never deletes). */
    public String restore(String name) {
        Path root = fs.getRoot();
        Path zip = backupsDir().resolve(name).normalize();
        if (!zip.startsWith(backupsDir()) || !Files.exists(zip)) {
            throw new NotFoundException("No backup named '" + name + "'.");
        }
        int restored = 0;
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                Path target = root.resolve(entry.getName()).normalize();
                if (!target.startsWith(root)) {
                    throw new PathBlockedException("Backup entry escapes the Explorer root: " + entry.getName());
                }
                Files.createDirectories(target.getParent());
                Files.copy(zis, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                restored++;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Restore failed", e);
        }
        audit.record("BACKUP", "backup:restore", name, "OK", restored + " files");
        log.info("Restored {} files from {}", restored, name);
        return "Restored " + restored + " file(s) from " + name + ".";
    }

    /** A backup-&lt;epoch&gt;.zip name, with a -N suffix if several land in the same second. */
    private Path uniqueBackupPath(Path dir) {
        long epoch = Instant.now().getEpochSecond();
        Path zip = dir.resolve("backup-" + epoch + ".zip");
        for (int n = 2; Files.exists(zip); n++) {
            zip = dir.resolve("backup-" + epoch + "-" + n + ".zip");
        }
        return zip;
    }

    private Path backupsDir() {
        Path dir = fs.getRoot().resolve(BACKUPS);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create backups folder", e);
        }
        return dir;
    }

    private boolean excluded(Path root, Path file) {
        String rel = root.relativize(file).toString();
        return rel.startsWith(BACKUPS) || rel.startsWith(".sandbox");
    }

    private BackupInfo info(Path zip) {
        try {
            return new BackupInfo(zip.getFileName().toString(), Files.size(zip),
                    Files.getLastModifiedTime(zip).toInstant().toString());
        } catch (IOException e) {
            return new BackupInfo(zip.getFileName().toString(), 0, Instant.now().toString());
        }
    }
}

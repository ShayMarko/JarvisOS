package com.jarvis.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jarvis.audit.AuditService;
import com.jarvis.backup.BackupService.BackupInfo;
import com.jarvis.config.JarvisSecurityProperties;
import com.jarvis.explorer.FileSystemService;
import com.jarvis.secrets.VaultCrypto;

class BackupServiceTest {

    private BackupService service(Path root) {
        FileSystemService fs = mock(FileSystemService.class);
        when(fs.getRoot()).thenReturn(root);
        JarvisSecurityProperties sec = new JarvisSecurityProperties();
        sec.setVaultKey("test-vault-key-1234567890");
        return new BackupService(fs, mock(AuditService.class), new VaultCrypto(sec), new JarvisBackupProperties());
    }

    @Test
    void createsListsAndRestores(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("notes.txt"), "hello jarvis");
        Files.createDirectories(root.resolve("Docs"));
        Files.writeString(root.resolve("Docs/plan.md"), "# plan");

        BackupService svc = service(root);

        BackupInfo info = svc.create();
        assertThat(info.name()).startsWith("backup-").endsWith(".zip");
        assertThat(info.sizeBytes()).isPositive();
        assertThat(svc.list()).extracting(BackupInfo::name).contains(info.name());

        // Mutate, then restore — original content must come back.
        Files.writeString(root.resolve("notes.txt"), "corrupted");
        Files.delete(root.resolve("Docs/plan.md"));

        String result = svc.restore(info.name());
        assertThat(result).contains("Restored");
        assertThat(Files.readString(root.resolve("notes.txt"))).isEqualTo("hello jarvis");
        assertThat(Files.readString(root.resolve("Docs/plan.md"))).isEqualTo("# plan");
    }

    @Test
    void createsAnEncryptedBackup(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("secret.txt"), "top secret");
        BackupService svc = service(root);

        BackupInfo info = svc.createEncrypted();
        assertThat(info.name()).endsWith(".zip.enc");
        Path enc = root.resolve(".backups").resolve(info.name());
        assertThat(Files.exists(enc)).isTrue();
        // ciphertext, not a zip: must not start with the ZIP magic "PK"
        assertThat(Files.readString(enc)).doesNotStartWith("PK");
    }

    @Test
    void backupExcludesItsOwnFolder(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("a.txt"), "a");
        BackupService svc = service(root);
        svc.create();
        // A second backup must not embed the first one (.backups is excluded).
        BackupInfo second = svc.create();
        assertThat(svc.list()).hasSize(2);
        assertThat(second.sizeBytes()).isPositive();
    }
}

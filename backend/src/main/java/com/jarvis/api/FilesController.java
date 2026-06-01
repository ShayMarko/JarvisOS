package com.jarvis.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jarvis.explorer.FileContent;
import com.jarvis.explorer.FileNode;
import com.jarvis.explorer.FileSystemService;
import com.jarvis.local.MacActions;

import jakarta.validation.constraints.NotNull;

/**
 * Jarvis Explorer endpoints. Mutating operations are authorised by the
 * PermissionGuard inside the service; failures surface via the global error
 * handler as a consistent {@code ApiError}.
 */
@RestController
@RequestMapping("/api/files")
public class FilesController {

    private final FileSystemService fileSystem;
    private final MacActions macActions;

    public FilesController(FileSystemService fileSystem, MacActions macActions) {
        this.fileSystem = fileSystem;
        this.macActions = macActions;
    }

    public record WriteRequest(@NotNull String path, @NotNull String content) {}

    public record PathRequest(@NotNull String path) {}

    public record MessageResponse(String result) {}

    @GetMapping
    public List<FileNode> list(@RequestParam(name = "path", defaultValue = "") String path) {
        return fileSystem.list(path);
    }

    @GetMapping("/content")
    public FileContent content(@RequestParam("path") String path) {
        return fileSystem.readText(path);
    }

    @PutMapping("/content")
    public FileNode write(@RequestBody WriteRequest request) {
        return fileSystem.writeText(request.path(), request.content());
    }

    @PostMapping("/dir")
    public FileNode createDir(@RequestBody PathRequest request) {
        return fileSystem.createDirectory(request.path());
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@RequestParam("path") String path,
                                       @RequestParam(name = "confirm", defaultValue = "false") boolean confirm) {
        fileSystem.delete(path, confirm);
        return ResponseEntity.noContent().build();
    }

    /** Reveal a file in Finder (macOS) — backs the "open file location" button. */
    @PostMapping("/reveal")
    public MessageResponse reveal(@RequestBody PathRequest request) {
        return new MessageResponse(macActions.reveal(fileSystem.resolveExisting(request.path())));
    }
}

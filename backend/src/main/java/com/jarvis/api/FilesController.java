package com.jarvis.api;

import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
@RequiredArgsConstructor
public class FilesController {

    private final FileSystemService fileSystem;
    private final MacActions macActions;


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

    /**
     * Raw file bytes (guarded), for in-app preview of images, PDFs, audio and video.
     * Supports HTTP Range requests so {@code <video>}/{@code <audio>} can seek and the
     * server streams in ~1&nbsp;MB chunks instead of buffering the whole file in memory.
     */
    @GetMapping("/raw")
    public ResponseEntity<org.springframework.core.io.support.ResourceRegion> raw(
            @RequestParam("path") String path,
            @RequestHeader(value = org.springframework.http.HttpHeaders.RANGE, required = false) String rangeHeader) {
        java.nio.file.Path p = fileSystem.resolveExisting(path);
        org.springframework.core.io.FileSystemResource res = new org.springframework.core.io.FileSystemResource(p);
        long length;
        try {
            length = res.contentLength();
        } catch (java.io.IOException e) {
            return ResponseEntity.notFound().build();
        }
        long chunk = 1024 * 1024;   // serve up to 1 MB per response
        org.springframework.core.io.support.ResourceRegion region;
        org.springframework.http.HttpStatus status;
        if (rangeHeader != null && !rangeHeader.isBlank()) {
            org.springframework.http.HttpRange range = org.springframework.http.HttpRange.parseRanges(rangeHeader).get(0);
            long start = range.getRangeStart(length);
            long end = range.getRangeEnd(length);
            region = new org.springframework.core.io.support.ResourceRegion(res, start, Math.min(chunk, end - start + 1));
            status = org.springframework.http.HttpStatus.PARTIAL_CONTENT;
        } else {
            region = new org.springframework.core.io.support.ResourceRegion(res, 0, Math.min(chunk, length));
            status = length > chunk ? org.springframework.http.HttpStatus.PARTIAL_CONTENT : org.springframework.http.HttpStatus.OK;
        }
        String ct = probeType(p);
        return ResponseEntity.status(status)
                .header(org.springframework.http.HttpHeaders.ACCEPT_RANGES, "bytes")
                .contentType(org.springframework.http.MediaType.parseMediaType(ct))
                .body(region);
    }

    private static String probeType(java.nio.file.Path p) {
        try {
            String ct = java.nio.file.Files.probeContentType(p);
            return ct != null ? ct : "application/octet-stream";
        } catch (java.io.IOException e) {
            return "application/octet-stream";
        }
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

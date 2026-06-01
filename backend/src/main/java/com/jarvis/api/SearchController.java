package com.jarvis.api;

import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jarvis.explorer.FileNode;
import com.jarvis.explorer.FileSystemService;

/** Scoped global search across the Jarvis Explorer (spec §5.2 /searchall). */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final FileSystemService fileSystem;


    @GetMapping
    public List<FileNode> search(@RequestParam("q") String query,
                                 @RequestParam(name = "path", defaultValue = "") String path) {
        return fileSystem.search(query, path);
    }
}

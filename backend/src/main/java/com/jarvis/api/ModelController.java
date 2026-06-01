package com.jarvis.api;

import lombok.RequiredArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jarvis.model.ModelCatalog;
import com.jarvis.model.ModelRouter;

/** Model Manager / Router info (spec §6). */
@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
public class ModelController {

    private final ModelCatalog catalog;
    private final ModelRouter router;


    @GetMapping
    public Map<String, Object> models() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("models", catalog.all());
        out.put("active", catalog.active().id());
        out.put("preference", router.preference().name());
        return out;
    }
}

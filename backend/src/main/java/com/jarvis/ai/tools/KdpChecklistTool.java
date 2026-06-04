package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;

import lombok.RequiredArgsConstructor;

/**
 * The authoritative Amazon KDP readiness checklist — deterministic, no AI cost. The Author uses it as
 * the target to hit before publishing; the Book Critic uses it as the bar to judge an ebook against.
 */
@Component
@RequiredArgsConstructor
public class KdpChecklistTool implements Tool {

    private final ObjectMapper mapper;

    private static final String CHECKLIST = """
            📦 Amazon KDP readiness checklist
            Manuscript / structure
            - [ ] Title page + copyright page + (optional) dedication
            - [ ] Table of contents with working links
            - [ ] Each chapter starts on a NEW page (page break)
            - [ ] Consistent heading styles, font, and spacing throughout
            - [ ] No orphaned placeholders / TODOs / broken references
            - [ ] Front matter (intro/preface) and back matter (about the author, also-by, CTA)
            Quality
            - [ ] Reading flow: each chapter balances story and information, with clear zones
            - [ ] Consistency pass done (names, tense, terminology, voice)
            - [ ] Spelling/grammar pass done
            Compliance
            - [ ] AI-content disclosure considered (KDP asks whether AI was used)
            - [ ] Copyright / rights are clear; no infringing material
            Formats
            - [ ] eBook export (reflowable) validated
            - [ ] Print PDF (if print): correct trim size, margins, bleed, embedded fonts
            Listing / metadata
            - [ ] Title + subtitle, 200–4000 char book description (blurb)
            - [ ] 7 keywords, 2 categories chosen
            - [ ] Cover meets KDP specs (eBook 1.6:1; print = trim + bleed + spine)
            - [ ] Price set; territories selected
            """;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("kdp_checklist",
                "Return the authoritative Amazon KDP readiness checklist (formatting, structure, compliance, "
                + "formats, listing/metadata). Use it as the target before publishing an ebook, or to audit one. "
                + "Optional 'book' just labels which book it's for.",
                "{\"type\":\"object\",\"properties\":{\"book\":{\"type\":\"string\"}}}");
    }

    @Override
    public String execute(String args) {
        String book = ToolArgs.firstStr(mapper, args, "book", "title", "name");
        return (book.isBlank() ? "" : "For: " + book + "\n") + CHECKLIST;
    }
}

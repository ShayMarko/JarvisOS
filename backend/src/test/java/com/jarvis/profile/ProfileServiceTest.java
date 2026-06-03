package com.jarvis.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jarvis.audit.AuditService;
import com.jarvis.explorer.FileContent;
import com.jarvis.explorer.FileSystemService;

class ProfileServiceTest {

    private FileSystemService fs;
    private ProfileService profile;

    private static final String SAMPLE = """
            # About Me — Shay Marko

            ## Snapshot
            Shay Marko — Israeli Java/Spring backend developer and builder-founder.

            ## Identity
            - Name: Shay Marko
            - Location: Israel
            - Languages: English and Hebrew

            ## Work, military & life context
            - Sensitive: salary 99999 and reserve-duty details that must stay private.
            """;

    @BeforeEach
    void setUp() {
        fs = mock(FileSystemService.class);
        profile = new ProfileService(fs, mock(AuditService.class));
    }

    @Test
    void compactIdentityIncludesNameButNotTheSensitiveSections() {
        when(fs.readText("about-me.md")).thenReturn(new FileContent("about-me.md", SAMPLE));

        String identity = profile.compactIdentity();

        assertThat(identity).contains("Shay Marko");        // the answer to "what's my name"
        assertThat(identity).contains("Israel").contains("Hebrew");
        assertThat(identity).doesNotContain("salary 99999"); // sensitive section stays out of the always-injected block
        assertThat(identity.length()).isLessThan(901);       // stays lean
    }

    @Test
    void compactIdentityIsBlankWhenProfileIsEmpty() {
        when(fs.readText("about-me.md")).thenReturn(new FileContent("about-me.md", "   "));
        assertThat(profile.compactIdentity()).isEmpty();
    }
}

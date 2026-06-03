package com.jarvis.profile;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Binds {@code jarvis.preference-learning} — the loop that quietly notices durable facts/preferences
 * about the user during conversation and OFFERS to remember them. It never writes the profile on its
 * own (consent via the Approval Center) and does its noticing on the LOCAL model, so personal content
 * is never shipped to a cloud AI just to decide what's worth remembering.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jarvis.preference-learning")
public class JarvisPreferenceProperties {

    private boolean enabled = true;
    /** Don't bother examining very short messages. */
    private int minMessageChars = 12;
    /**
     * Cheap pre-filter: only spend a local-model call when the user's message actually sounds like it
     * states a preference/identity fact. Keeps this from running on every "what's the weather".
     */
    private List<String> hints = List.of(
            "i prefer", "i like", "i love", "i hate", "i don't like", "i dont like", "i use", "i'm using",
            "i always", "i never", "call me", "my name is", "i am a", "i'm a", "i work", "my role",
            "remember that", "from now on", "please always", "please never", "i'd rather", "i would rather");
}

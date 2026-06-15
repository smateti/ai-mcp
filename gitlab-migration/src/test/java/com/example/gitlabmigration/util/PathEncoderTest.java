package com.example.gitlabmigration.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PathEncoderTest {

    @Test
    void encodesSlashesToPercent2F() {
        assertEquals("group%2Fsub%2Frepo", PathEncoder.encode("group/sub/repo"));
    }

    @Test
    void singleSegmentUnchanged() {
        assertEquals("myproject", PathEncoder.encode("myproject"));
    }

    @Test
    void encodesSpaces() {
        assertEquals("my+group%2Fmy+repo", PathEncoder.encode("my group/my repo"));
    }

    @Test
    void emptyStringReturnsEmpty() {
        assertEquals("", PathEncoder.encode(""));
    }

    @Test
    void deeplyNestedPath() {
        assertEquals("a%2Fb%2Fc%2Fd%2Fe", PathEncoder.encode("a/b/c/d/e"));
    }
}

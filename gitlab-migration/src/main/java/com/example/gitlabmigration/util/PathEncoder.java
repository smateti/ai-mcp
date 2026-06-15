package com.example.gitlabmigration.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * URL-encodes GitLab project/group full paths for use as API path ids.
 * e.g. "group/sub/repo" -> "group%2Fsub%2Frepo"
 */
public final class PathEncoder {

    private PathEncoder() {}

    public static String encode(String fullPath) {
        return URLEncoder.encode(fullPath, StandardCharsets.UTF_8);
    }
}

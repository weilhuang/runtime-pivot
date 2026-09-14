package com.runtime.pivot.protocol;

import java.io.File;
import java.io.IOException;

public final class PathSafety {
    private PathSafety() {
    }

    public static File resolveUnder(File root, String relativeOrAbsolute) throws IOException {
        if (root == null) {
            throw new IllegalArgumentException("root directory is required");
        }
        File canonicalRoot = root.getCanonicalFile();
        File candidate = new File(relativeOrAbsolute);
        if (!candidate.isAbsolute()) {
            candidate = new File(canonicalRoot, relativeOrAbsolute);
        }
        File canonical = candidate.getCanonicalFile();
        String rootPath = canonicalRoot.getPath();
        String targetPath = canonical.getPath();
        if (!targetPath.equals(rootPath) && !targetPath.startsWith(rootPath + File.separator)) {
            throw new IOException("Path escapes output directory: " + relativeOrAbsolute);
        }
        return canonical;
    }
}

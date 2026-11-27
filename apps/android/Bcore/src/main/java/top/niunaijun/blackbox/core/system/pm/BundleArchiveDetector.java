package top.niunaijun.blackbox.core.system.pm;

import java.io.File;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class BundleArchiveDetector {
    private BundleArchiveDetector() {
    }

    static boolean isApksBundle(File file) {
        if (file == null) return false;
        String name = file.getName();
        if (name != null && name.toLowerCase().endsWith(".apks")) {
            return true;
        }

        // Regular APKs always have a root manifest. Nested APK assets must not make
        // the host package look like a split APK bundle.
        try (ZipFile zipFile = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            boolean hasRootManifest = false;
            boolean hasBundleMetadata = false;
            int splitApkCount = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry == null || entry.isDirectory()) continue;
                String entryName = entry.getName();
                if (entryName == null) continue;
                String lower = entryName.toLowerCase();
                if ("androidmanifest.xml".equals(lower)) {
                    hasRootManifest = true;
                }
                if ("toc.pb".equals(lower) || "bundleconfig.pb".equals(lower)) {
                    hasBundleMetadata = true;
                }
                if (lower.endsWith(".apk") &&
                        (lower.startsWith("splits/") ||
                            lower.startsWith("standalones/") ||
                            lower.startsWith("instant/"))) {
                    splitApkCount++;
                }
            }
            return !hasRootManifest && (hasBundleMetadata || splitApkCount >= 2);
        } catch (Throwable ignored) {
            return false;
        }
    }
}

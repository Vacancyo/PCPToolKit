package top.niunaijun.blackbox.core.system.pm;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class BundleArchiveDetectionTest {
    @Test
    public void regularApkWithNestedPluginApksIsNotASplitBundle() throws Exception {
        File apk = File.createTempFile("game-", ".apk");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(apk))) {
            addEntry(zip, "AndroidManifest.xml");
            addEntry(zip, "classes.dex");
            addEntry(zip, "assets/gbsdk.plugin.common.apk");
            addEntry(zip, "assets/gbsdk.plugin.datalink.apk");
        }

        assertFalse(BundleArchiveDetector.isApksBundle(apk));
        apk.delete();
    }

    @Test
    public void splitBundleIsDetectedByBundleMetadata() throws Exception {
        File apks = File.createTempFile("game-", ".apks");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(apks))) {
            addEntry(zip, "BundleConfig.pb");
            addEntry(zip, "splits/base-master.apk");
            addEntry(zip, "splits/base-arm64_v8a.apk");
        }

        assertTrue(BundleArchiveDetector.isApksBundle(apks));
        apks.delete();
    }

    private static void addEntry(ZipOutputStream zip, String name) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(1);
        zip.closeEntry();
    }
}

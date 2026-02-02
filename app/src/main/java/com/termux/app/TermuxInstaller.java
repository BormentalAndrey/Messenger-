package com.termux.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.system.Os;
import android.util.Pair;

import com.kakdela.p2p.R;
import com.termux.shared.file.FileUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.errors.Error;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.file.TermuxFileUtils;
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.termux.shared.termux.TermuxConstants.TERMUX_PREFIX_DIR;
import static com.termux.shared.termux.TermuxConstants.TERMUX_PREFIX_DIR_PATH;
import static com.termux.shared.termux.TermuxConstants.TERMUX_STAGING_PREFIX_DIR;
import static com.termux.shared.termux.TermuxConstants.TERMUX_STAGING_PREFIX_DIR_PATH;

public final class TermuxInstaller {

    private static final String LOG_TAG = "TermuxInstaller";

    /*
     * ВАЖНО:
     * Используем %2B вместо '+' в имени тега.
     *
     * Assets (как на скрине GitHub):
     *  - bootstrap-aarch64.zip
     *  - bootstrap-arm.zip
     *  - bootstrap-i686.zip
     *  - bootstrap-x86_64.zip
     */
    private static final String BOOTSTRAP_BASE_URL =
        "https://github.com/termux/termux-packages/releases/download/bootstrap-2024.12.18-r1%2Bapt-android-7";

    public static void setupBootstrapIfNeeded(final Activity activity, final Runnable whenDone) {

        if (FileUtils.directoryFileExists(TERMUX_PREFIX_DIR_PATH, true)
            && !TermuxFileUtils.isTermuxPrefixDirectoryEmpty()) {
            whenDone.run();
            return;
        }

        final ProgressDialog progress = new ProgressDialog(activity);
        progress.setTitle("System Setup");
        progress.setMessage(activity.getString(R.string.bootstrap_installer_body));
        progress.setCancelable(false);
        progress.setIndeterminate(false);
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setMax(100);
        progress.show();

        new Thread(() -> {

            File tempZip = new File(activity.getCacheDir(), "bootstrap_download.zip");

            try {
                String downloadUrl = getDownloadUrl();
                Logger.logInfo(LOG_TAG, "Starting download: " + downloadUrl);

                downloadFileWithRedirects(downloadUrl, tempZip, progress, activity);

                prepareDirectories();

                Logger.logInfo(LOG_TAG, "Extracting bootstrap...");
                extractZip(tempZip);

                Logger.logInfo(LOG_TAG, "Finalizing installation...");

                if (!TERMUX_STAGING_PREFIX_DIR.renameTo(TERMUX_PREFIX_DIR)) {
                    throw new RuntimeException("Failed to move staging directory to prefix");
                }

                TermuxShellEnvironment.writeEnvironmentToFile(activity);

                Logger.logInfo(LOG_TAG, "Bootstrap installed successfully.");

                activity.runOnUiThread(whenDone);

            } catch (Exception e) {
                Logger.logError(LOG_TAG, "Installation failed", e);
                showBootstrapErrorDialog(activity, whenDone,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
                sendBootstrapCrashReportNotification(activity, e.getMessage());
            } finally {
                try {
                    if (tempZip.exists()) tempZip.delete();
                } catch (Throwable ignored) {}

                activity.runOnUiThread(() -> {
                    try {
                        progress.dismiss();
                    } catch (Throwable ignored) {}
                });
            }

        }).start();
    }

    private static String getDownloadUrl() {

        String abi = Build.SUPPORTED_ABIS[0];
        String arch;

        if (abi.contains("arm64-v8a")) {
            arch = "aarch64";
        } else if (abi.contains("armeabi-v7a")) {
            arch = "arm";
        } else if (abi.contains("x86_64")) {
            arch = "x86_64";
        } else if (abi.contains("x86")) {
            arch = "i686";
        } else {
            arch = "aarch64";
        }

        return BOOTSTRAP_BASE_URL + "/bootstrap-" + arch + ".zip";
    }

    private static void downloadFileWithRedirects(
        String urlStr,
        File dest,
        ProgressDialog progress,
        Activity activity
    ) throws Exception {

        URL url = new URL(urlStr);

        HttpURLConnection conn;
        int redirects = 0;

        while (true) {

            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent", "Termux-P2P-Installer");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);

            int status = conn.getResponseCode();

            if (status == HttpURLConnection.HTTP_MOVED_TEMP
                || status == HttpURLConnection.HTTP_MOVED_PERM
                || status == HttpURLConnection.HTTP_SEE_OTHER
                || status == 307
                || status == 308) {

                String newUrl = conn.getHeaderField("Location");

                if (newUrl == null)
                    throw new RuntimeException("Redirect without Location header");

                url = new URL(newUrl);
                redirects++;

                if (redirects > 10)
                    throw new RuntimeException("Too many redirects");

                continue;
            }

            if (status != HttpURLConnection.HTTP_OK) {
                throw new RuntimeException("HTTP " + status + " at " + url);
            }

            break;
        }

        int fileLength = conn.getContentLength();

        try (InputStream input = new BufferedInputStream(conn.getInputStream());
             FileOutputStream output = new FileOutputStream(dest)) {

            byte[] data = new byte[8192];
            long total = 0;
            int count;

            while ((count = input.read(data)) != -1) {

                output.write(data, 0, count);

                if (fileLength > 0) {
                    total += count;
                    final int percent = (int) (total * 100 / fileLength);
                    activity.runOnUiThread(() -> progress.setProgress(percent));
                }
            }

            output.flush();
        }
    }

    private static void prepareDirectories() throws Exception {

        FileUtils.deleteFile("staging", TERMUX_STAGING_PREFIX_DIR_PATH, true);
        FileUtils.deleteFile("prefix", TERMUX_PREFIX_DIR_PATH, true);

        Error error = TermuxFileUtils.isTermuxPrefixStagingDirectoryAccessible(true, true);
        if (error != null)
            throw new Exception(error.getMessage());

        error = TermuxFileUtils.isTermuxPrefixDirectoryAccessible(true, true);
        if (error != null)
            throw new Exception(error.getMessage());
    }

    private static void extractZip(File zipFile) throws Exception {

        final byte[] buffer = new byte[8192];
        final List<Pair<String, String>> symlinks = new ArrayList<>();

        File stagingRoot = new File(TERMUX_STAGING_PREFIX_DIR_PATH);
        String stagingCanonical = stagingRoot.getCanonicalPath();

        try (ZipInputStream zipInput =
                 new ZipInputStream(new FileInputStream(zipFile))) {

            ZipEntry entry;

            while ((entry = zipInput.getNextEntry()) != null) {

                String name = entry.getName();

                if (name == null || name.isEmpty()) {
                    zipInput.closeEntry();
                    continue;
                }

                if ("SYMLINKS.txt".equals(name)) {

                    BufferedReader reader =
                        new BufferedReader(new InputStreamReader(zipInput));

                    String line;
                    while ((line = reader.readLine()) != null) {

                        String[] parts = line.split("←");

                        if (parts.length == 2) {
                            symlinks.add(
                                Pair.create(
                                    parts[0],
                                    TERMUX_STAGING_PREFIX_DIR_PATH + "/" + parts[1]
                                )
                            );
                        }
                    }

                    zipInput.closeEntry();
                    continue;
                }

                File target = new File(stagingRoot, name);

                // zip-slip защита
                String canonical = target.getCanonicalPath();
                if (!canonical.startsWith(stagingCanonical + File.separator)) {
                    throw new SecurityException("Zip path traversal: " + name);
                }

                if (entry.isDirectory()) {

                    if (!target.exists() && !target.mkdirs()) {
                        throw new RuntimeException("mkdir failed: " + target);
                    }

                } else {

                    File parent = target.getParentFile();
                    if (!parent.exists() && !parent.mkdirs()) {
                        throw new RuntimeException("parent mkdir failed: " + parent);
                    }

                    try (FileOutputStream out = new FileOutputStream(target)) {

                        int read;
                        while ((read = zipInput.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                        }
                    }

                    // Исполняемые файлы
                    if (name.startsWith("bin/")
                        || name.startsWith("libexec/")
                        || name.startsWith("libexec/apt/")
                        || name.contains("lib/apt/methods")) {

                        try {
                            Os.chmod(target.getAbsolutePath(), 0700);
                        } catch (Throwable t) {
                            Logger.logWarn(LOG_TAG,
                                "chmod failed: " + target.getAbsolutePath());
                        }
                    }
                }

                zipInput.closeEntry();
            }
        }

        for (Pair<String, String> symlink : symlinks) {

            File linkFile = new File(symlink.second);
            File parent = linkFile.getParentFile();

            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            try {
                Os.symlink(symlink.first, symlink.second);
            } catch (Exception e) {
                Logger.logWarn(LOG_TAG,
                    "Symlink failed: " + symlink.second);
            }
        }
    }

    public static void showBootstrapErrorDialog(
        Activity activity,
        Runnable whenDone,
        String message
    ) {

        activity.runOnUiThread(() ->

            new AlertDialog.Builder(activity)
                .setTitle("Installation Failure")
                .setMessage("Error detail:\n" + message)
                .setCancelable(false)
                .setNegativeButton("Exit",
                    (d, w) -> activity.finish())
                .setPositiveButton("Retry",
                    (d, w) -> {
                        FileUtils.deleteFile(
                            "prefix",
                            TERMUX_PREFIX_DIR_PATH,
                            true
                        );
                        setupBootstrapIfNeeded(activity, whenDone);
                    })
                .show()
        );
    }

    private static void sendBootstrapCrashReportNotification(
        Activity activity,
        String message
    ) {
        // Без крашей на Android 12+, но с реальным логированием
        Logger.logError(LOG_TAG,
            "Bootstrap installation error: " + message);
    }

    public static void setupStorageSymlinks(final Context context) {

        new Thread(() -> {

            try {
                File storageDir = TermuxConstants.TERMUX_STORAGE_HOME_DIR;

                FileUtils.clearDirectory(
                    "~/storage",
                    storageDir.getAbsolutePath()
                );

                File sharedDir = Environment.getExternalStorageDirectory();
                Os.symlink(
                    sharedDir.getAbsolutePath(),
                    new File(storageDir, "shared").getAbsolutePath()
                );

                File dcimDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DCIM);
                Os.symlink(
                    dcimDir.getAbsolutePath(),
                    new File(storageDir, "dcim").getAbsolutePath()
                );

                File downloadsDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);
                Os.symlink(
                    downloadsDir.getAbsolutePath(),
                    new File(storageDir, "downloads").getAbsolutePath()
                );

                Logger.logInfo(LOG_TAG, "Storage symlinks created.");

            } catch (Exception e) {
                Logger.logError(LOG_TAG,
                    "Storage setup error: " + e.getMessage());
            }

        }).start();
    }
                             }

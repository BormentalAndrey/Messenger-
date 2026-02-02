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

    // Ссылка на Bootstrap с поддержкой '+' (актуально для GitHub релизов)
    private static final String BOOTSTRAP_BASE_URL =
        "https://github.com/termux/termux-packages/releases/download/bootstrap-2024.12.18-r1+apt-android-7";

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
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setMax(100);
        progress.show();

        new Thread(() -> {
            File tempZip = new File(activity.getCacheDir(), "bootstrap_download.zip");
            try {
                String downloadUrl = getDownloadUrl();
                Logger.logInfo(LOG_TAG, "Starting download: " + downloadUrl);

                downloadFile(downloadUrl, tempZip, progress, activity);
                prepareDirectories();
                extractZip(tempZip);

                if (!TERMUX_STAGING_PREFIX_DIR.renameTo(TERMUX_PREFIX_DIR)) {
                    throw new RuntimeException("Failed to finalize installation: rename failed");
                }

                TermuxShellEnvironment.writeEnvironmentToFile(activity);
                activity.runOnUiThread(whenDone);

            } catch (Exception e) {
                Logger.logError(LOG_TAG, "Bootstrap error: " + e.getMessage());
                showBootstrapErrorDialog(activity, whenDone, e.getMessage());
            } finally {
                if (tempZip.exists()) tempZip.delete();
                activity.runOnUiThread(() -> {
                    try { progress.dismiss(); } catch (Throwable ignored) { }
                });
            }
        }).start();
    }

    private static String getDownloadUrl() {
        String abi = Build.SUPPORTED_ABIS[0];
        String arch = abi.contains("arm64") ? "aarch64" : (abi.contains("armeabi") ? "arm" : (abi.contains("x86_64") ? "x86_64" : "i686"));
        return BOOTSTRAP_BASE_URL + "/bootstrap-" + arch + ".zip";
    }

    private static void downloadFile(String urlStr, File dest, ProgressDialog progress, Activity activity) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("User-Agent", "Termux-App-Installer");

        if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new Exception("Server returned HTTP " + conn.getResponseCode());
        }

        int fileLength = conn.getContentLength();
        try (InputStream input = new BufferedInputStream(conn.getInputStream());
             FileOutputStream output = new FileOutputStream(dest)) {
            byte[] data = new byte[8192];
            long total = 0;
            int count;
            while ((count = input.read(data)) != -1) {
                total += count;
                if (fileLength > 0) {
                    int percent = (int) (total * 100 / fileLength);
                    activity.runOnUiThread(() -> progress.setProgress(percent));
                }
                output.write(data, 0, count);
            }
        }
    }

    private static void prepareDirectories() throws Exception {
        deleteRecursively(new File(TERMUX_STAGING_PREFIX_DIR_PATH));
        deleteRecursively(new File(TERMUX_PREFIX_DIR_PATH));

        Error error = TermuxFileUtils.isTermuxPrefixStagingDirectoryAccessible(true, true);
        if (error != null) throw new Exception(error.getMessage());

        error = TermuxFileUtils.isTermuxPrefixDirectoryAccessible(true, true);
        if (error != null) throw new Exception(error.getMessage());
    }

    private static void extractZip(File zipFile) throws Exception {
        final byte[] buffer = new byte[8192];
        final List<Pair<String, String>> symlinks = new ArrayList<>();
        try (ZipInputStream zipInput = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                String name = entry.getName();
                if ("SYMLINKS.txt".equals(name)) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(zipInput));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] parts = line.split("←");
                        if (parts.length == 2) {
                            symlinks.add(Pair.create(parts[0], TERMUX_STAGING_PREFIX_DIR_PATH + "/" + parts[1]));
                        }
                    }
                } else {
                    File target = new File(TERMUX_STAGING_PREFIX_DIR_PATH, name);
                    if (entry.isDirectory()) {
                        target.mkdirs();
                    } else {
                        if (!target.getParentFile().exists()) target.getParentFile().mkdirs();
                        try (FileOutputStream out = new FileOutputStream(target)) {
                            int read;
                            while ((read = zipInput.read(buffer)) != -1) out.write(buffer, 0, read);
                        }
                        // Установка Unix-прав на исполнение
                        if (name.startsWith("bin/") || name.startsWith("libexec/")) {
                            Os.chmod(target.getAbsolutePath(), 0700);
                        }
                    }
                }
            }
        }
        for (Pair<String, String> symlink : symlinks) {
            try {
                File linkFile = new File(symlink.second);
                if (!linkFile.getParentFile().exists()) linkFile.getParentFile().mkdirs();
                Os.symlink(symlink.first, symlink.second);
            } catch (Exception ignored) {}
        }
    }

    public static void showBootstrapErrorDialog(Activity activity, Runnable whenDone, String message) {
        activity.runOnUiThread(() ->
            new AlertDialog.Builder(activity)
                .setTitle("Installation Failure")
                .setMessage("Error detail:\n" + message)
                .setCancelable(false)
                .setNegativeButton("Exit", (d, w) -> activity.finish())
                .setPositiveButton("Retry", (d, w) -> {
                    deleteRecursively(new File(TERMUX_PREFIX_DIR_PATH));
                    setupBootstrapIfNeeded(activity, whenDone);
                })
                .show()
        );
    }

    /**
     * Создание симлинков для доступа к хранилищу Android.
     * Необходим для вызова из TerminalActivity.kt.
     */
    public static void setupStorageSymlinks(Context context) {
        new Thread(() -> {
            try {
                File storageDir = TermuxConstants.TERMUX_STORAGE_HOME_DIR;
                // Очистка старых ссылок
                deleteRecursively(storageDir);
                if (!storageDir.mkdirs()) return;

                // Основная память
                File sharedDir = Environment.getExternalStorageDirectory();
                Os.symlink(sharedDir.getAbsolutePath(), new File(storageDir, "shared").getAbsolutePath());

                // Стандартные папки
                String[] dirs = {
                    Environment.DIRECTORY_DCIM,
                    Environment.DIRECTORY_DOWNLOADS,
                    Environment.DIRECTORY_PICTURES,
                    Environment.DIRECTORY_MUSIC,
                    Environment.DIRECTORY_MOVIES
                };

                for (String dirType : dirs) {
                    File path = Environment.getExternalStoragePublicDirectory(dirType);
                    if (path.exists()) {
                        Os.symlink(path.getAbsolutePath(), new File(storageDir, dirType.toLowerCase()).getAbsolutePath());
                    }
                }

                Logger.logInfo(LOG_TAG, "Storage symlinks created successfully.");
            } catch (Exception e) {
                Logger.logError(LOG_TAG, "Storage setup error: " + e.getMessage());
            }
        }).start();
    }

    private static void sendBootstrapCrashReportNotification(Activity activity, String message) {
        // Уведомление подавлено для стабильности на API 31+
        Logger.logError(LOG_TAG, "Bootstrap Notification suppressed. Error: " + message);
    }

    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File c : children) deleteRecursively(c);
            }
        }
        file.delete();
    }
                            }

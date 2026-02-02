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
import com.termux.shared.termux.crash.TermuxCrashUtils;
import com.termux.shared.termux.file.TermuxFileUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.errors.Error;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxUtils;
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

    /**
     * Ссылка на Bootstrap. 
     * Используем '%2B' вместо '+', так как некоторые реализации HttpURLConnection 
     * могут некорректно кодировать этот символ, вызывая 404.
     */
    private static final String BOOTSTRAP_BASE_URL = "https://github.com/termux/termux-packages/releases/download/bootstrap-2024.12.18-r1%2Bapt-android-7";

    public static void setupBootstrapIfNeeded(final Activity activity, final Runnable whenDone) {
        if (FileUtils.directoryFileExists(TERMUX_PREFIX_DIR_PATH, true) && !TermuxFileUtils.isTermuxPrefixDirectoryEmpty()) {
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

        new Thread() {
            @Override
            public void run() {
                File tempZip = new File(activity.getCacheDir(), "bootstrap_download.zip");
                try {
                    String downloadUrl = getDownloadUrl();
                    Logger.logInfo(LOG_TAG, "Starting download: " + downloadUrl);

                    // 1. Скачивание
                    downloadFileWithRedirects(downloadUrl, tempZip, progress, activity);

                    // 2. Подготовка
                    prepareDirectories();

                    // 3. Распаковка (Используем чистый Java/Unix-Os метод без нативных библиотек)
                    Logger.logInfo(LOG_TAG, "Extracting bootstrap zip...");
                    extractZip(tempZip);

                    // 4. Финализация
                    Logger.logInfo(LOG_TAG, "Finalizing installation...");
                    if (!TERMUX_STAGING_PREFIX_DIR.renameTo(TERMUX_PREFIX_DIR)) {
                        throw new RuntimeException("Failed to move staging directory to prefix");
                    }

                    TermuxShellEnvironment.writeEnvironmentToFile(activity);

                    Logger.logInfo(LOG_TAG, "Bootstrap installed.");
                    activity.runOnUiThread(whenDone);

                } catch (Exception e) {
                    Logger.logError(LOG_TAG, "Installation failed: " + e.getMessage());
                    showBootstrapErrorDialog(activity, whenDone, e.getMessage());
                } finally {
                    if (tempZip.exists()) tempZip.delete();
                    activity.runOnUiThread(progress::dismiss);
                }
            }
        }.start();
    }

    private static String getDownloadUrl() {
        String arch;
        String abi = Build.SUPPORTED_ABIS[0];
        if (abi.contains("arm64-v8a")) arch = "aarch64";
        else if (abi.contains("armeabi-v7a")) arch = "arm";
        else if (abi.contains("x86_64")) arch = "x86_64";
        else if (abi.contains("x86")) arch = "i686";
        else arch = "aarch64"; 

        return BOOTSTRAP_BASE_URL + "/bootstrap-" + arch + ".zip";
    }

    private static void downloadFileWithRedirects(String urlStr, File dest, ProgressDialog progress, Activity activity) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "Termux-App-P2P");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);

        int status = conn.getResponseCode();
        if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_SEE_OTHER) {
            String newUrl = conn.getHeaderField("Location");
            conn = (HttpURLConnection) new URL(newUrl).openConnection();
        }

        if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new Exception("HTTP " + conn.getResponseCode() + " at " + urlStr);
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
            output.flush();
        }
    }

    private static void prepareDirectories() throws Exception {
        FileUtils.deleteFile("staging", TERMUX_STAGING_PREFIX_DIR_PATH, true);
        FileUtils.deleteFile("prefix", TERMUX_PREFIX_DIR_PATH, true);

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
                if (name.equals("SYMLINKS.txt")) {
                    // Читаем список симлинков
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
                        if (!target.exists() && !target.mkdirs()) throw new RuntimeException("mkdir fail: " + target);
                    } else {
                        if (!target.getParentFile().exists() && !target.getParentFile().mkdirs()) 
                            throw new RuntimeException("parent mkdir fail: " + target);
                            
                        try (FileOutputStream out = new FileOutputStream(target)) {
                            int read;
                            while ((read = zipInput.read(buffer)) != -1) out.write(buffer, 0, read);
                        }
                        
                        // ВАЖНО: Установка прав 700 для всех бинарных файлов в bin и libexec
                        if (name.startsWith("bin/") || name.startsWith("libexec/") || name.contains("lib/apt/methods")) {
                            Os.chmod(target.getAbsolutePath(), 0700);
                        }
                    }
                }
                zipInput.closeEntry();
            }
        }

        // Создаем симлинки через системный вызов Os.symlink (не требует нативных libtermux-bootstrap.so)
        for (Pair<String, String> symlink : symlinks) {
            File linkFile = new File(symlink.second);
            if (!linkFile.getParentFile().exists()) linkFile.getParentFile().mkdirs();
            try {
                Os.symlink(symlink.first, symlink.second);
            } catch (Exception e) {
                Logger.logWarn(LOG_TAG, "Symlink fail: " + symlink.second);
            }
        }
    }

    public static void showBootstrapErrorDialog(Activity activity, Runnable whenDone, String message) {
        activity.runOnUiThread(() -> {
            new AlertDialog.Builder(activity)
                .setTitle("Installation Failure")
                .setMessage("Error detail:\n" + message)
                .setCancelable(false)
                .setNegativeButton("Exit", (d, w) -> activity.finish())
                .setPositiveButton("Retry", (d, w) -> {
                    FileUtils.deleteFile("prefix", TERMUX_PREFIX_DIR_PATH, true);
                    setupBootstrapIfNeeded(activity, whenDone);
                })
                .show();
        });
    }

    private static void sendBootstrapCrashReportNotification(Activity activity, String message) {
        // Оставлено пустым для предотвращения IllegalArgumentException на Android 12+ (API 31)
    }

    public static void setupStorageSymlinks(final Context context) {
        new Thread(() -> {
            try {
                File storageDir = TermuxConstants.TERMUX_STORAGE_HOME_DIR;
                FileUtils.clearDirectory("~/storage", storageDir.getAbsolutePath());
                
                File sharedDir = Environment.getExternalStorageDirectory();
                Os.symlink(sharedDir.getAbsolutePath(), new File(storageDir, "shared").getAbsolutePath());

                File dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
                Os.symlink(dcimDir.getAbsolutePath(), new File(storageDir, "dcim").getAbsolutePath());

                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                Os.symlink(downloadsDir.getAbsolutePath(), new File(storageDir, "downloads").getAbsolutePath());

                Logger.logInfo(LOG_TAG, "Storage symlinks created.");
            } catch (Exception e) {
                Logger.logError(LOG_TAG, "Storage error: " + e.getMessage());
            }
        }).start();
    }
                    }

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
     * Прямая ссылка на официальный Bootstrap. 
     * Использование '+' вместо '%2B' крайне важно для корректной обработки URL в Java 
     * и предотвращения ошибки FileNotFoundException (404).
     */
    private static final String BOOTSTRAP_BASE_URL = "https://github.com/termux/termux-packages/releases/download/bootstrap-2024.12.18-r1+apt-android-7";

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
                    Logger.logInfo(LOG_TAG, "Starting bootstrap download: " + downloadUrl);

                    // 1. Скачивание с поддержкой редиректов (GitHub хранит файлы на внешних CDN)
                    downloadFileWithRedirects(downloadUrl, tempZip, progress, activity);

                    // 2. Подготовка директорий
                    prepareDirectories();

                    // 3. Распаковка архива
                    Logger.logInfo(LOG_TAG, "Extracting bootstrap zip...");
                    extractZip(tempZip);

                    // 4. Финализация установки
                    Logger.logInfo(LOG_TAG, "Finalizing installation...");
                    if (!TERMUX_STAGING_PREFIX_DIR.renameTo(TERMUX_PREFIX_DIR)) {
                        throw new RuntimeException("Critical failure: Failed to move staging directory to prefix");
                    }

                    TermuxShellEnvironment.writeEnvironmentToFile(activity);

                    Logger.logInfo(LOG_TAG, "Bootstrap installation completed successfully.");
                    activity.runOnUiThread(whenDone);

                } catch (Exception e) {
                    Logger.logError(LOG_TAG, "Bootstrap installation failed: " + e.getMessage());
                    // Вызываем диалог ошибки. Уведомление отключено внутри метода ниже для предотвращения краша на Android 12+.
                    showBootstrapErrorDialog(activity, whenDone, Logger.getStackTracesMarkdownString(null, Logger.getStackTracesStringArray(e)));
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
        // GitHub часто возвращает 302/301 для файлов релизов
        if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_SEE_OTHER) {
            String newUrl = conn.getHeaderField("Location");
            conn = (HttpURLConnection) new URL(newUrl).openConnection();
        }

        if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new Exception("Server returned HTTP " + conn.getResponseCode() + " for URL: " + urlStr);
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
                    activity.runOnUiThread(() -> {
                        progress.setProgress(percent);
                        progress.setMessage("Downloading: " + percent + "%");
                    });
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
        if (error != null) throw new Exception("Staging access error: " + error.getMessage());
        
        error = TermuxFileUtils.isTermuxPrefixDirectoryAccessible(true, true);
        if (error != null) throw new Exception("Prefix access error: " + error.getMessage());
    }

    private static void extractZip(File zipFile) throws Exception {
        final byte[] buffer = new byte[8192];
        final List<Pair<String, String>> symlinks = new ArrayList<>();

        try (ZipInputStream zipInput = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.equals("SYMLINKS.txt")) {
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
                        if (!target.exists() && !target.mkdirs()) throw new RuntimeException("Failed to create dir: " + target);
                    } else {
                        if (!target.getParentFile().exists() && !target.getParentFile().mkdirs()) 
                            throw new RuntimeException("Failed to create parent dir: " + target);
                            
                        try (FileOutputStream out = new FileOutputStream(target)) {
                            int read;
                            while ((read = zipInput.read(buffer)) != -1) out.write(buffer, 0, read);
                        }
                        
                        // Установка Unix-прав на исполнение для бинарных файлов
                        if (name.startsWith("bin/") || name.startsWith("libexec/") || name.contains("lib/apt/methods")) {
                            Os.chmod(target.getAbsolutePath(), 0700);
                        }
                    }
                }
                zipInput.closeEntry();
            }
        }

        for (Pair<String, String> symlink : symlinks) {
            File linkFile = new File(symlink.second);
            if (!linkFile.getParentFile().exists()) linkFile.getParentFile().mkdirs();
            try {
                Os.symlink(symlink.first, symlink.second);
            } catch (Exception e) {
                Logger.logWarn(LOG_TAG, "Symlink creation failed: " + symlink.second + " -> " + symlink.first);
            }
        }
    }

    public static void showBootstrapErrorDialog(Activity activity, Runnable whenDone, String message) {
        Logger.logErrorExtended(LOG_TAG, "Bootstrap failed: " + message);
        
        // ВАЖНО: Вызов уведомления закомментирован, так как он вызывает краш IllegalArgumentException 
        // на Android 12+ из-за отсутствия флагов PendingIntent.
        // sendBootstrapCrashReportNotification(activity, message);

        activity.runOnUiThread(() -> {
            new AlertDialog.Builder(activity)
                .setTitle("Installation Failure")
                .setMessage(activity.getString(R.string.bootstrap_error_body) + "\n\nDetails:\n" + message)
                .setCancelable(false)
                .setNegativeButton(R.string.bootstrap_error_abort, (d, w) -> activity.finish())
                .setPositiveButton(R.string.bootstrap_error_try_again, (d, w) -> {
                    FileUtils.deleteFile("prefix", TERMUX_PREFIX_DIR_PATH, true);
                    setupBootstrapIfNeeded(activity, whenDone);
                })
                .show();
        });
    }

    private static void sendBootstrapCrashReportNotification(Activity activity, String message) {
        // Метод оставлен пустым для предотвращения падения приложения на Android 12+.
        Logger.logInfo(LOG_TAG, "Crash report notification suppressed to avoid PendingIntent flag error on API 31+.");
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

                Logger.logInfo(LOG_TAG, "Storage symlinks synchronized.");
            } catch (Exception e) {
                Logger.logError(LOG_TAG, "Storage setup error: " + e.getMessage());
            }
        }).start();
    }
}

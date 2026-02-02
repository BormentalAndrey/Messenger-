package com.termux.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.system.Os;
import android.util.Log;
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

/**
 * Исправленный установщик Termux.
 * Решает проблемы:
 * 1. HTTP 404 при загрузке (исправлен URL и редиректы).
 * 2. Crash on Android 12+ (удален PendingIntent notification).
 * 3. Права доступа (добавлен chmod 0700).
 */
public final class TermuxInstaller {

    private static final String LOG_TAG = "TermuxInstaller";

    // Точный тег релиза как на вашем скриншоте GitHub
    private static final String BOOTSTRAP_TAG = "bootstrap-2024.12.18-r1+apt-android-7";
    
    // Базовый URL. Важно: символ '+' здесь обрабатывается корректно как часть пути.
    private static final String BOOTSTRAP_BASE_URL = 
        "https://github.com/termux/termux-packages/releases/download/" + BOOTSTRAP_TAG;

    public static void setupBootstrapIfNeeded(final Activity activity, final Runnable whenDone) {
        // Проверяем, установлен ли уже Termux (наличие папки /usr и файлов внутри)
        if (FileUtils.directoryFileExists(TERMUX_PREFIX_DIR_PATH, true)
            && !TermuxFileUtils.isTermuxPrefixDirectoryEmpty()) {
            activity.runOnUiThread(whenDone);
            return;
        }

        final ProgressDialog progress = new ProgressDialog(activity);
        progress.setTitle("Инициализация Termux");
        progress.setMessage("Загрузка базовой системы...");
        progress.setCancelable(false);
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setMax(100);
        progress.show();

        new Thread(() -> {
            File tempZip = new File(activity.getCacheDir(), "bootstrap.zip");
            try {
                // 1. Формирование ссылки
                String downloadUrl = getDownloadUrl();
                Logger.logInfo(LOG_TAG, "Downloading from: " + downloadUrl);

                // 2. Скачивание (с поддержкой редиректов GitHub -> Amazon S3)
                downloadFile(downloadUrl, tempZip, progress, activity);
                
                // 3. Очистка и подготовка папок
                activity.runOnUiThread(() -> progress.setMessage("Распаковка..."));
                prepareDirectories();
                
                // 4. Распаковка (с установкой прав chmod!)
                extractZip(tempZip);

                // 5. Финализация (переименование staging -> usr)
                if (!TERMUX_STAGING_PREFIX_DIR.renameTo(TERMUX_PREFIX_DIR)) {
                    throw new RuntimeException("Failed to rename staging directory.");
                }

                // 6. Создание файлов окружения
                TermuxShellEnvironment.writeEnvironmentToFile(activity);
                
                // Успех -> запускаем колбэк
                activity.runOnUiThread(() -> {
                    if (progress.isShowing()) progress.dismiss();
                    whenDone.run();
                });

            } catch (Exception e) {
                Logger.logError(LOG_TAG, "Bootstrap error: " + e.getMessage());
                // ВАЖНО: Вместо уведомления (которое вызывало краш PendingIntent), показываем диалог
                showBootstrapErrorDialog(activity, whenDone, e.getMessage());
                
                activity.runOnUiThread(() -> {
                    if (progress.isShowing()) progress.dismiss();
                });
            } finally {
                if (tempZip.exists()) tempZip.delete();
            }
        }).start();
    }

    /**
     * Выбирает правильный ZIP файл в зависимости от архитектуры CPU устройства.
     * Соответствует именам файлов на скриншоте.
     */
    private static String getDownloadUrl() {
        String abi = Build.SUPPORTED_ABIS[0];
        String arch;
        
        if (abi.startsWith("arm64")) {
            arch = "aarch64";
        } else if (abi.startsWith("armeabi")) {
            arch = "arm";
        } else if (abi.equals("x86_64")) {
            arch = "x86_64";
        } else if (abi.equals("x86")) {
            arch = "i686";
        } else {
            arch = "aarch64"; // Default fallback
        }
        
        return BOOTSTRAP_BASE_URL + "/bootstrap-" + arch + ".zip";
    }

    /**
     * Скачивает файл, корректно обрабатывая редиректы (HTTP 302).
     */
    private static void downloadFile(String urlStr, File dest, ProgressDialog progress, Activity activity) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(30000);
        conn.setRequestProperty("User-Agent", "Termux-Installer"); // GitHub иногда блокирует пустые UA

        int responseCode = conn.getResponseCode();
        
        // Ручная обработка редиректов, если автомат не сработал
        if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || 
            responseCode == HttpURLConnection.HTTP_MOVED_PERM || 
            responseCode == 302 || responseCode == 307) {
            String newUrl = conn.getHeaderField("Location");
            Logger.logInfo(LOG_TAG, "Redirected to: " + newUrl);
            url = new URL(newUrl);
            conn = (HttpURLConnection) url.openConnection();
        }

        if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new Exception("HTTP Error " + conn.getResponseCode() + " for URL: " + urlStr);
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
        // Удаляем старые/битые версии
        deleteRecursively(new File(TERMUX_STAGING_PREFIX_DIR_PATH));
        deleteRecursively(new File(TERMUX_PREFIX_DIR_PATH));

        Error error = TermuxFileUtils.isTermuxPrefixStagingDirectoryAccessible(true, true);
        if (error != null) throw new Exception("Staging dir setup failed: " + error.getMessage());

        error = TermuxFileUtils.isTermuxPrefixDirectoryAccessible(true, true);
        if (error != null) throw new Exception("Prefix dir setup failed: " + error.getMessage());
    }

    private static void extractZip(File zipFile) throws Exception {
        final byte[] buffer = new byte[8192];
        final List<Pair<String, String>> symlinks = new ArrayList<>();
        
        try (ZipInputStream zipInput = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                String name = entry.getName();
                
                // Обработка файла симлинков внутри архива
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
                        if (target.getParentFile() != null) target.getParentFile().mkdirs();
                        try (FileOutputStream out = new FileOutputStream(target)) {
                            int read;
                            while ((read = zipInput.read(buffer)) != -1) out.write(buffer, 0, read);
                        }
                        
                        // ВАЖНО: Делаем бинарники исполняемыми
                        // Без этого вы получите "Permission denied" при запуске bash
                        if (name.startsWith("bin/") || name.contains("/bin/") || name.startsWith("libexec/")) {
                            Os.chmod(target.getAbsolutePath(), 0700);
                        }
                    }
                }
            }
        }

        // Восстанавливаем симлинки
        for (Pair<String, String> symlink : symlinks) {
            try {
                File linkFile = new File(symlink.second);
                if (linkFile.getParentFile() != null) linkFile.getParentFile().mkdirs();
                Os.symlink(symlink.first, symlink.second);
            } catch (Exception e) {
                // Игнорируем некритичные ошибки симлинков
            }
        }
    }

    // Безопасный показ ошибки вместо вылетающего Notification
    public static void showBootstrapErrorDialog(Activity activity, Runnable whenDone, String message) {
        activity.runOnUiThread(() ->
            new AlertDialog.Builder(activity)
                .setTitle("Ошибка установки")
                .setMessage("Не удалось загрузить компоненты Termux.\n\nДетали: " + message + "\n\nПроверьте интернет.")
                .setCancelable(false)
                .setNegativeButton("Выход", (d, w) -> activity.finish())
                .setPositiveButton("Повторить", (d, w) -> {
                    // Очищаем и пробуем снова
                    setupBootstrapIfNeeded(activity, whenDone);
                })
                .show()
        );
    }

    /**
     * Создает симлинки на хранилище (/sdcard) в папке home/storage
     */
    public static void setupStorageSymlinks(Context context) {
        new Thread(() -> {
            try {
                File storageDir = TermuxConstants.TERMUX_STORAGE_HOME_DIR;
                if (storageDir.exists()) deleteRecursively(storageDir);
                storageDir.mkdirs();

                File sharedDir = Environment.getExternalStorageDirectory();
                try {
                    Os.symlink(sharedDir.getAbsolutePath(), new File(storageDir, "shared").getAbsolutePath());
                } catch (Exception ignored) {}

                String[] dirs = {
                    Environment.DIRECTORY_DCIM, Environment.DIRECTORY_DOWNLOADS,
                    Environment.DIRECTORY_PICTURES, Environment.DIRECTORY_MUSIC,
                    Environment.DIRECTORY_MOVIES
                };

                for (String dirType : dirs) {
                    File path = Environment.getExternalStoragePublicDirectory(dirType);
                    if (path != null && path.exists()) {
                        try {
                            Os.symlink(path.getAbsolutePath(), new File(storageDir, dirType.toLowerCase()).getAbsolutePath());
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception e) {
                Logger.logError(LOG_TAG, "Storage symlink error: " + e.getMessage());
            }
        }).start();
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

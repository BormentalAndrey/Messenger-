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

public final class TermuxInstaller {

    private static final String LOG_TAG = "TermuxInstaller";

    // Актуальный релиз bootstrap
    private static final String BOOTSTRAP_TAG =
            "bootstrap-2025.11.30-r1+apt.android-7";

    private static final String BOOTSTRAP_BASE_URL =
            "https://github.com/termux/termux-packages/releases/download/" + BOOTSTRAP_TAG;

    public static void setupBootstrapIfNeeded(final Activity activity, final Runnable whenDone) {

        // Переопределяем prefix в безопасное место
        TermuxConstants.overridePrefixDir(new File(activity.getDataDir(), "usr"));

        if (FileUtils.directoryFileExists(TermuxConstants.TERMUX_PREFIX_DIR_PATH, true)
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
                String downloadUrl = getDownloadUrl();
                Logger.logInfo(LOG_TAG, "Downloading from: " + downloadUrl);

                downloadFile(downloadUrl, tempZip, progress, activity);

                activity.runOnUiThread(() -> progress.setMessage("Распаковка..."));

                prepareDirectories();
                extractZip(tempZip);

                if (!TermuxConstants.TERMUX_STAGING_PREFIX_DIR.renameTo(TermuxConstants.TERMUX_PREFIX_DIR)) {
                    throw new RuntimeException("Failed to rename staging directory.");
                }

                // chmod +x для всех бинарников
                fixBinaryPermissions(TermuxConstants.TERMUX_PREFIX_DIR);

                TermuxShellEnvironment.writeEnvironmentToFile(activity);

                activity.runOnUiThread(() -> {
                    if (progress.isShowing()) progress.dismiss();
                    whenDone.run();
                });

            } catch (Exception e) {
                Logger.logError(LOG_TAG,
                        "Bootstrap error:\n" + Log.getStackTraceString(e));

                activity.runOnUiThread(() -> {
                    if (progress.isShowing()) progress.dismiss();
                    showBootstrapErrorDialog(activity, whenDone, e.getMessage());
                });

            } finally {
                if (tempZip.exists()) tempZip.delete();
            }
        }).start();
    }

    private static String getDownloadUrl() {
        String abi = Build.SUPPORTED_ABIS[0];
        String arch;
        if (abi.startsWith("arm64")) arch = "aarch64";
        else if (abi.startsWith("armeabi")) arch = "arm";
        else if (abi.equals("x86_64")) arch = "x86_64";
        else if (abi.equals("x86")) arch = "i686";
        else arch = "aarch64";

        return BOOTSTRAP_BASE_URL + "/bootstrap-" + arch + ".zip";
    }

    private static void downloadFile(String urlStr, File dest, ProgressDialog progress, Activity activity) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("User-Agent", "Termux-Installer");

        int code = conn.getResponseCode();
        if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP ||
            code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
            String newUrl = conn.getHeaderField("Location");
            conn.disconnect();
            url = new URL(newUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Termux-Installer");
        }

        if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new Exception("HTTP error " + conn.getResponseCode());
        }

        int fileLength = conn.getContentLength();

        try (InputStream input = new BufferedInputStream(conn.getInputStream());
             FileOutputStream output = new FileOutputStream(dest)) {

            byte[] buffer = new byte[8192];
            long total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (fileLength > 0) {
                    int percent = (int) (total * 100 / fileLength);
                    int finalPercent = percent;
                    activity.runOnUiThread(() -> progress.setProgress(finalPercent));
                }
                output.write(buffer, 0, count);
            }
        }
    }

    private static void prepareDirectories() throws Exception {
        deleteRecursively(new File(TermuxConstants.TERMUX_STAGING_PREFIX_DIR_PATH));
        deleteRecursively(new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH));

        Error error;
        error = TermuxFileUtils.isTermuxPrefixStagingDirectoryAccessible(true, true);
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
                if ("SYMLINKS.txt".equals(name)) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(zipInput));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] parts = line.split("←");
                        if (parts.length == 2) {
                            symlinks.add(Pair.create(parts[0], TermuxConstants.TERMUX_STAGING_PREFIX_DIR_PATH + "/" + parts[1]));
                        }
                    }
                } else {
                    File target = new File(TermuxConstants.TERMUX_STAGING_PREFIX_DIR_PATH, name);
                    if (entry.isDirectory()) {
                        target.mkdirs();
                        try { Os.chmod(target.getAbsolutePath(), 0700); } catch (Throwable ignored) {}
                    } else {
                        if (target.getParentFile() != null) target.getParentFile().mkdirs();
                        try (FileOutputStream out = new FileOutputStream(target)) {
                            int read;
                            while ((read = zipInput.read(buffer)) != -1) out.write(buffer, 0, read);
                        }
                        if (name.startsWith("bin/") || name.contains("/bin/") || name.startsWith("libexec/")) {
                            try { Os.chmod(target.getAbsolutePath(), 0700); } catch (Throwable ignored) {}
                        }
                    }
                }
            }
        }

        for (Pair<String, String> symlink : symlinks) {
            try {
                File linkFile = new File(symlink.second);
                if (linkFile.getParentFile() != null) linkFile.getParentFile().mkdirs();
                Os.symlink(symlink.first, symlink.second);
            } catch (Throwable ignored) {}
        }
    }

    private static void fixBinaryPermissions(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    fixBinaryPermissions(f);
                    if (f.isFile() && (f.getName().equals("bash") || f.getName().endsWith(".so") || f.canExecute())) {
                        try { Os.chmod(f.getAbsolutePath(), 0700); } catch (Throwable ignored) {}
                    }
                }
            }
        }
    }

    public static void showBootstrapErrorDialog(Activity activity, Runnable whenDone, String message) {
        activity.runOnUiThread(() -> new AlertDialog.Builder(activity)
                .setTitle("Ошибка установки")
                .setMessage("Не удалось загрузить компоненты Termux.\n\nДетали: " + message + "\n\nПроверьте интернет.")
                .setCancelable(false)
                .setNegativeButton("Выход", (d, w) -> activity.finish())
                .setPositiveButton("Повторить", (d, w) -> setupBootstrapIfNeeded(activity, whenDone))
                .show());
    }

    public static void setupStorageSymlinks(Context context) {
        new Thread(() -> {
            try {
                File storageDir = TermuxConstants.TERMUX_STORAGE_HOME_DIR;
                if (storageDir.exists()) deleteRecursively(storageDir);
                storageDir.mkdirs();

                File sharedDir = Environment.getExternalStorageDirectory();
                try { Os.symlink(sharedDir.getAbsolutePath(), new File(storageDir, "shared").getAbsolutePath()); } catch (Throwable ignored) {}

                String[] dirs = {
                        Environment.DIRECTORY_DCIM,
                        Environment.DIRECTORY_DOWNLOADS,
                        Environment.DIRECTORY_PICTURES,
                        Environment.DIRECTORY_MUSIC,
                        Environment.DIRECTORY_MOVIES
                };
                for (String dirType : dirs) {
                    File path = Environment.getExternalStoragePublicDirectory(dirType);
                    if (path != null && path.exists()) {
                        try { Os.symlink(path.getAbsolutePath(), new File(storageDir, dirType.toLowerCase()).getAbsolutePath()); } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable e) {
                Logger.logError(LOG_TAG, "Storage symlink error:\n" + Log.getStackTraceString(e));
            }
        }).start();
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File c : children) deleteRecursively(c);
        }
        file.delete();
    }
}

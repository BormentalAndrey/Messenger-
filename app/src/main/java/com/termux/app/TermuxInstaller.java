package com.termux.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.system.Os;
import android.util.Pair;
import android.view.WindowManager;

import com.kakdela.p2p.R;
import com.termux.shared.file.FileUtils;
import com.termux.shared.termux.crash.TermuxCrashUtils;
import com.termux.shared.termux.file.TermuxFileUtils;
import com.termux.shared.interact.MessageDialogUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.markdown.MarkdownUtils;
import com.termux.shared.errors.Error;
import com.termux.shared.android.PackageUtils;
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

    // Ссылка на официальный репозиторий с бутстрапами
    private static final String BOOTSTRAP_BASE_URL = "https://github.com/termux/termux-packages/releases/download/bootstrap-2024.12.18-r1%2Bapt-android-7";

    public static void setupBootstrapIfNeeded(final Activity activity, final Runnable whenDone) {
        if (FileUtils.directoryFileExists(TERMUX_PREFIX_DIR_PATH, true) && !TermuxFileUtils.isTermuxPrefixDirectoryEmpty()) {
            whenDone.run();
            return;
        }

        final ProgressDialog progress = new ProgressDialog(activity);
        progress.setMessage(activity.getString(R.string.bootstrap_installer_body)); // "Installing..."
        progress.setCancelable(false);
        progress.setIndeterminate(false);
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setMax(100);
        progress.show();

        new Thread() {
            @Override
            public void run() {
                File tempZip = new File(activity.getCacheDir(), "bootstrap.zip");
                try {
                    // 1. Определяем URL для архитектуры устройства
                    String downloadUrl = getDownloadUrl();
                    Logger.logInfo(LOG_TAG, "Downloading bootstrap from: " + downloadUrl);

                    // 2. Скачиваем файл
                    downloadFile(downloadUrl, tempZip, progress, activity);

                    // 3. Подготовка папок (стандартная логика Termux)
                    prepareDirectories(activity);

                    // 4. Распаковка
                    Logger.logInfo(LOG_TAG, "Extracting bootstrap...");
                    extractZip(tempZip, activity);

                    // 5. Финализация
                    if (!TERMUX_STAGING_PREFIX_DIR.renameTo(TERMUX_PREFIX_DIR)) {
                        throw new RuntimeException("Moving staging to prefix failed");
                    }

                    TermuxShellEnvironment.writeEnvironmentToFile(activity);
                    activity.runOnUiThread(whenDone);

                } catch (Exception e) {
                    Logger.logError(LOG_TAG, "Bootstrap failed: " + e.getMessage());
                    showBootstrapErrorDialog(activity, whenDone, Logger.getStackTracesMarkdownString(null, Logger.getStackTracesStringArray(e)));
                } finally {
                    if (tempZip.exists()) tempZip.delete();
                    activity.runOnUiThread(progress::dismiss);
                }
            }
        }.start();
    }

    private static String getDownloadUrl() {
        String arch = "aarch64";
        String abi = Build.SUPPORTED_ABIS[0];
        if (abi.contains("arm64")) arch = "aarch64";
        else if (abi.contains("armeabi")) arch = "arm";
        else if (abi.contains("x86_64")) arch = "x86_64";
        else if (abi.contains("x86")) arch = "i686";
        return BOOTSTRAP_BASE_URL + "/bootstrap-" + arch + ".zip";
    }

    private static void downloadFile(String urlStr, File dest, ProgressDialog progress, Activity activity) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.connect();

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
                        progress.setMessage("Downloading system (" + percent + "%)");
                        progress.setProgress(percent);
                    });
                }
                output.write(data, 0, count);
            }
        }
    }

    private static void prepareDirectories(Activity activity) throws Exception {
        FileUtils.deleteFile("staging", TERMUX_STAGING_PREFIX_DIR_PATH, true);
        FileUtils.deleteFile("prefix", TERMUX_PREFIX_DIR_PATH, true);
        TermuxFileUtils.isTermuxPrefixStagingDirectoryAccessible(true, true);
        TermuxFileUtils.isTermuxPrefixDirectoryAccessible(true, true);
    }

    private static void extractZip(File zipFile, Activity activity) throws Exception {
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
                        symlinks.add(Pair.create(parts[0], TERMUX_STAGING_PREFIX_DIR_PATH + "/" + parts[1]));
                    }
                } else {
                    File target = new File(TERMUX_STAGING_PREFIX_DIR_PATH, name);
                    if (entry.isDirectory()) {
                        target.mkdirs();
                    } else {
                        target.getParentFile().mkdirs();
                        try (FileOutputStream out = new FileOutputStream(target)) {
                            int read;
                            while ((read = zipInput.read(buffer)) != -1) out.write(buffer, 0, read);
                        }
                        if (name.startsWith("bin/") || name.startsWith("libexec/")) {
                            Os.chmod(target.getAbsolutePath(), 0700);
                        }
                    }
                }
            }
        }
        for (Pair<String, String> symlink : symlinks) {
            new File(symlink.second).getParentFile().mkdirs();
            Os.symlink(symlink.first, symlink.second);
        }
    }

    // Остальные методы (showBootstrapErrorDialog и т.д.) остаются как в оригинале,
    // но убедитесь, что они используют актуальные строки из ресурсов.
    
    public static void showBootstrapErrorDialog(Activity activity, Runnable whenDone, String message) {
        activity.runOnUiThread(() -> {
            new AlertDialog.Builder(activity)
                .setTitle("Installation Error")
                .setMessage("Failed to download or install system files. Check your internet connection.\n\n" + message)
                .setNegativeButton("Exit", (d, w) -> activity.finish())
                .setPositiveButton("Retry", (d, w) -> setupBootstrapIfNeeded(activity, whenDone))
                .show();
        });
    }

    public static void setupStorageSymlinks(final Context context) {
        // Оставьте вашу реализацию здесь, она рабочая
    }
}

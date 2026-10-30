package rkr.simplekeyboard.inputmethod.latin.utils;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;

public class AutoUpdater {
    // এখানে আপনার GitHub Username এবং Repo Name বসান:
    private static final String GITHUB_REPO = "YOUR_GITHUB_USERNAME/YOUR_REPO_NAME";

    public static void checkForUpdate(final Context context) {
        Toast.makeText(context, "Checking for updates...", Toast.LENGTH_SHORT).show();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                URL url = new URL("https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");

                if (conn.getResponseCode() != 200) {
                    showToast(context, "No release found or repo is private!");
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder res = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) res.append(line);
                reader.close();

                JSONObject json = new JSONObject(res.toString());
                String latestTag = json.getString("tag_name");
                String notes = json.optString("body", "New update available!");
                JSONArray assets = json.getJSONArray("assets");

                if (assets.length() > 0) {
                    String apkUrl = assets.getJSONObject(0).getString("browser_download_url");
                    new Handler(Looper.getMainLooper()).post(() ->
                        showUpdateDialog(context, latestTag, notes, apkUrl)
                    );
                }
            } catch (Exception e) {
                showToast(context, "Update check failed: " + e.getMessage());
            }
        });
    }

    private static void showUpdateDialog(Context context, String version, String notes, String apkUrl) {
        new AlertDialog.Builder(context)
                .setTitle("New Update: " + version)
                .setMessage(notes)
                .setPositiveButton("Download & Install", (d, w) -> downloadAndInstall(context, apkUrl, version))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void downloadAndInstall(Context context, String apkUrl, String version) {
        Toast.makeText(context, "Downloading update...", Toast.LENGTH_LONG).show();
        String fileName = "RidmikClone_" + version + ".apk";
        File oldFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName);
        if (oldFile.exists()) oldFile.delete();

        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(apkUrl));
        req.setTitle("Downloading Update " + version);
        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        req.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName);

        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        long downloadId = dm.enqueue(req);

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id == downloadId) {
                    ctx.unregisterReceiver(this);
                    Uri uri = dm.getUriForDownloadedFile(downloadId);
                    if (uri != null) {
                        Intent install = new Intent(Intent.ACTION_VIEW);
                        install.setDataAndType(uri, "application/vnd.android.package-archive");
                        install.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        ctx.startActivity(install);
                    }
                }
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        }
    }

    private static void showToast(Context context, String msg) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show());
    }
   
}

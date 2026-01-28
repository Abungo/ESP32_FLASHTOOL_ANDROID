package com.abungo.usbserial.Flash;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.abungo.usbserial.R;
import com.google.android.material.appbar.MaterialToolbar;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ManageFirmwareActivity extends AppCompatActivity {

    private RecyclerView rvFirmwares;
    private View tvNoFirmwares;
    private FirmwareAdapter adapter;
    private final List<File> firmwareFiles = new ArrayList<>();
    private File firmwaresDir;

    private final ActivityResultLauncher<String> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            this::onFilePicked);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_firmware);

        firmwaresDir = new File(getFilesDir(), "uploaded_firmwares");
        if (!firmwaresDir.exists()) {
            firmwaresDir.mkdirs();
        }

        setupUI();
        loadFirmwares();
    }

    private void setupUI() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        rvFirmwares = findViewById(R.id.rvFirmwares);
        tvNoFirmwares = findViewById(R.id.tvNoFirmwares);
        rvFirmwares.setLayoutManager(new LinearLayoutManager(this));

        adapter = new FirmwareAdapter(firmwareFiles, new FirmwareAdapter.OnFirmwareActionListener() {
            @Override
            public void onDelete(File file) {
                confirmDelete(file);
            }

            @Override
            public void onItemClick(File file) {
                showFirmwareDetails(file);
            }
        });
        rvFirmwares.setAdapter(adapter);

        findViewById(R.id.fabAdd).setOnClickListener(v -> filePickerLauncher.launch("*/*"));
    }

    private void showFirmwareDetails(File file) {
        long sizeBytes = file.length();
        String size = sizeBytes < 1024 * 1024
                ? String.format(java.util.Locale.getDefault(), "%.1f KB", sizeBytes / 1024f)
                : String.format(java.util.Locale.getDefault(), "%.1f MB", sizeBytes / (1024f * 1024f));

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                java.util.Locale.getDefault());
        String date = sdf.format(new java.util.Date(file.lastModified()));

        String technicalDetails = parseFirmwareHeader(file);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Firmware Details")
                .setMessage("Name: " + file.getName() + "\n\n" +
                        "Path: " + file.getAbsolutePath() + "\n\n" +
                        "File Size: " + size + " (" + sizeBytes + " bytes)\n" +
                        "Modified: " + date + "\n\n" +
                        "--- Technical Header ---\n" +
                        technicalDetails)
                .setPositiveButton("OK", null)
                .show();
    }

    private String parseFirmwareHeader(File file) {
        try (InputStream is = new FileInputStream(file)) {
            byte[] header = new byte[8];
            if (is.read(header) < 8)
                return "Could not read header";

            int segments = header[1] & 0xFF;
            int flashMode = header[2] & 0xFF;
            int flashSizeFreq = header[3] & 0xFF;

            // Entry point is 32-bit little endian
            long entryPoint = ((header[7] & 0xFFL) << 24) |
                    ((header[6] & 0xFFL) << 16) |
                    ((header[5] & 0xFFL) << 8) |
                    (header[4] & 0xFFL);

            String modeStr = "Unknown";
            switch (flashMode) {
                case 0:
                    modeStr = "QIO";
                    break;
                case 1:
                    modeStr = "QOUT";
                    break;
                case 2:
                    modeStr = "DIO";
                    break;
                case 3:
                    modeStr = "DOUT";
                    break;
            }

            String freqStr = "Unknown";
            int freq = flashSizeFreq & 0x0F;
            switch (freq) {
                case 0:
                    freqStr = "40MHz";
                    break;
                case 1:
                    freqStr = "26MHz";
                    break;
                case 2:
                    freqStr = "20MHz";
                    break;
                case 0xF:
                    freqStr = "80MHz";
                    break;
            }

            String sizeStr = "Unknown";
            int flashSize = (flashSizeFreq >> 4) & 0x0F;
            switch (flashSize) {
                case 0:
                    sizeStr = "1MB";
                    break;
                case 1:
                    sizeStr = "2MB";
                    break;
                case 2:
                    sizeStr = "4MB";
                    break;
                case 3:
                    sizeStr = "8MB";
                    break;
                case 4:
                    sizeStr = "16MB";
                    break;
            }

            return "Magic: 0x" + Integer.toHexString(header[0] & 0xFF).toUpperCase() + "\n" +
                    "Segments: " + segments + "\n" +
                    "SPI Mode: " + modeStr + "\n" +
                    "Flash Freq: " + freqStr + "\n" +
                    "Flash Size: " + sizeStr + "\n" +
                    "Entry Point: 0x" + Long.toHexString(entryPoint).toUpperCase();

        } catch (Exception e) {
            return "Error parsing header: " + e.getMessage();
        }
    }

    private void confirmDelete(File file) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Delete Firmware")
                .setMessage("Are you sure you want to delete " + file.getName() + "?")
                .setPositiveButton("Delete", (dialog, which) -> deleteFirmware(file))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadFirmwares() {
        firmwareFiles.clear();
        File[] files = firmwaresDir.listFiles();
        if (files != null) {
            Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            firmwareFiles.addAll(Arrays.asList(files));
        }
        adapter.notifyDataSetChanged();
        tvNoFirmwares.setVisibility(firmwareFiles.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void onFilePicked(Uri uri) {
        if (uri == null)
            return;

        try {
            String fileName = getFileName(uri);
            if (fileName == null)
                fileName = "unknown_" + System.currentTimeMillis() + ".bin";

            // Validate the file before saving
            if (!isValidFirmware(uri)) {
                Toast.makeText(this, "Invalid firmware file. Must be an ESP32 .bin file.", Toast.LENGTH_LONG).show();
                return;
            }

            File destFile = new File(firmwaresDir, fileName);
            try (InputStream is = getContentResolver().openInputStream(uri);
                    OutputStream os = new FileOutputStream(destFile)) {
                byte[] buffer = new byte[4096];
                int length;
                while ((length = is.read(buffer)) > 0) {
                    os.write(buffer, 0, length);
                }
            }
            Toast.makeText(this, "Firmware uploaded: " + fileName, Toast.LENGTH_SHORT).show();
            loadFirmwares();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to upload firmware: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private boolean isValidFirmware(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null)
                return false;

            // Check magic byte (0xE9 is the standard ESP image magic)
            int magic = is.read();
            return magic == 0xE9;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) {
                        result = cursor.getString(index);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private void deleteFirmware(File file) {
        if (file.delete()) {
            Toast.makeText(this, "Deleted: " + file.getName(), Toast.LENGTH_SHORT).show();
            loadFirmwares();
        } else {
            Toast.makeText(this, "Failed to delete file", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

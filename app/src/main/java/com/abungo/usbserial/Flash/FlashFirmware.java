package com.abungo.usbserial.Flash;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.abungo.usbserial.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.physicaloid.lib.Boards;
import com.physicaloid.lib.Physicaloid;
import com.physicaloid.lib.usb.driver.uart.UartConfig;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.content.Intent;

public class FlashFirmware extends AppCompatActivity {

  // USB Serial
  private Physicaloid mPhysicaloid;
  private boolean deviceConnected = false;

  // UI Components
  private AutoCompleteTextView spinnerFirmware;
  private AutoCompleteTextView spinnerBaud;
  private MaterialButton btFlash;
  private MaterialButton btDetect;
  private MaterialButton btClearLog;
  private TextView tvRead;
  private TextView tvDeviceStatus;
  private TextView tvChipInfo;
  private TextView tvProgressStatus;
  private TextView tvProgressPercent;
  private View statusIndicator;
  private MaterialCardView progressCard;
  private LinearProgressIndicator progressBar;

  // Configuration
  private static final String DEFAULT_FIRMWARE_NAME = "Built-in ESP32 Firmware";
  private static final String ASSET_FILE_NAME_ESP32 = "firmwares/ESP32/20240222-v1.22.2.bin";
  private String[] itemsBaudRate;
  private List<String> firmwareList = new ArrayList<>();
  private UartConfig uartConfig;
  private File firmwaresDir;

  // Threading
  private final ExecutorService executorService = Executors.newSingleThreadExecutor();
  private final Handler mainHandler = new Handler(Looper.getMainLooper());

  // State
  private boolean isFlashing = false;
  private String detectedChip = null;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    initializeViews();
    setupToolbar();
    setupDropdowns();
    setupListeners();
    initializeUSB();
  }

  private void initializeViews() {
    // Toolbar
    MaterialToolbar toolbar = findViewById(R.id.topAppBar);
    setSupportActionBar(toolbar);

    // Input fields
    spinnerFirmware = findViewById(R.id.spinnerFirmware);
    spinnerBaud = findViewById(R.id.spinnerBaud);

    // Buttons
    btFlash = findViewById(R.id.btFlash);
    btDetect = findViewById(R.id.btDetect);
    btClearLog = findViewById(R.id.btClearLog);

    // Status views
    tvDeviceStatus = findViewById(R.id.tvDeviceStatus);
    tvChipInfo = findViewById(R.id.tvChipInfo);
    statusIndicator = findViewById(R.id.statusIndicator);

    // Log
    tvRead = findViewById(R.id.tvRead);

    // Progress views
    progressCard = findViewById(R.id.progressCard);
    progressBar = findViewById(R.id.progressBar);
    tvProgressStatus = findViewById(R.id.tvProgressStatus);
    tvProgressPercent = findViewById(R.id.tvProgressPercent);
  }

  private void setupToolbar() {
    MaterialToolbar toolbar = findViewById(R.id.topAppBar);
    setSupportActionBar(toolbar);
  }

  private void setupDropdowns() {
    refreshFirmwareList();

    // Baud rate dropdown
    itemsBaudRate = new String[] {
        "9600", "19200", "38400", "57600", "115200", "230400", "460800", "921600"
    };
    ArrayAdapter<String> baudAdapter = new ArrayAdapter<>(
        this,
        android.R.layout.simple_dropdown_item_1line,
        itemsBaudRate);
    spinnerBaud.setAdapter(baudAdapter);
    spinnerBaud.setText("115200", false);
  }

  private void refreshFirmwareList() {
    firmwaresDir = new File(getFilesDir(), "uploaded_firmwares");
    if (!firmwaresDir.exists()) {
      firmwaresDir.mkdirs();
    }

    firmwareList.clear();
    firmwareList.add(DEFAULT_FIRMWARE_NAME);

    File[] files = firmwaresDir.listFiles();
    if (files != null) {
      Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
      for (File f : files) {
        firmwareList.add(f.getName());
      }
    }

    ArrayAdapter<String> firmwareAdapter = new ArrayAdapter<>(
        this,
        android.R.layout.simple_dropdown_item_1line,
        firmwareList);
    spinnerFirmware.setAdapter(firmwareAdapter);

    // Maintain selection if possible, otherwise default to first
    String current = spinnerFirmware.getText().toString();
    if (firmwareList.contains(current)) {
      spinnerFirmware.setText(current, false);
    } else {
      spinnerFirmware.setText(firmwareList.get(0), false);
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    refreshFirmwareList();
  }

  private void setupListeners() {
    btClearLog.setOnClickListener(v -> {
      tvRead.setText(R.string.ready);
    });
  }

  private void initializeUSB() {
    mPhysicaloid = new Physicaloid(this);
    uartConfig = new UartConfig(
        115200,
        UartConfig.DATA_BITS8,
        UartConfig.STOP_BITS1,
        UartConfig.PARITY_NONE,
        false,
        false);

    // Try to open device
    executorService.execute(() -> {
      boolean opened = mPhysicaloid.open();
      mainHandler.post(() -> {
        if (opened) {
          mPhysicaloid.setConfig(uartConfig);
          updateDeviceStatus(true, null);
          appendLog("✓ Device connected and ready");
        } else {
          updateDeviceStatus(false, null);
          appendLog("⚠ No USB device detected\nPlease connect ESP32 and grant USB permissions");
        }
      });
    });
  }

  private void updateDeviceStatus(boolean connected, String chipType) {
    deviceConnected = connected;
    detectedChip = chipType;

    if (connected) {
      tvDeviceStatus.setText(R.string.device_connected);
      statusIndicator.setBackgroundTintList(
          ColorStateList.valueOf(ContextCompat.getColor(this, R.color.md_theme_light_tertiary)));

      if (chipType != null) {
        tvChipInfo.setText(getString(R.string.chip_detected, chipType));
        tvChipInfo.setVisibility(View.VISIBLE);
      } else {
        tvChipInfo.setVisibility(View.GONE);
      }

      btFlash.setEnabled(!isFlashing);
      btDetect.setEnabled(!isFlashing);
    } else {
      tvDeviceStatus.setText(R.string.device_disconnected);
      statusIndicator.setBackgroundTintList(
          ColorStateList.valueOf(ContextCompat.getColor(this, R.color.md_theme_light_error)));
      tvChipInfo.setVisibility(View.GONE);

      btFlash.setEnabled(false);
      btDetect.setEnabled(false);
    }
  }

  private void appendLog(String message) {
    mainHandler.post(() -> {
      String currentText = tvRead.getText().toString();
      if (currentText.equals(getString(R.string.ready))) {
        tvRead.setText(message);
      } else {
        tvRead.append("\n" + message);
      }
    });
  }

  private void updateProgress(int percent, String status) {
    mainHandler.post(() -> {
      if (percent >= 0) {
        progressCard.setVisibility(View.VISIBLE);
        progressBar.setProgress(percent);
        tvProgressPercent.setText(getString(R.string.progress_percent, percent));
        if (status != null) {
          tvProgressStatus.setText(status);
        }
      } else {
        progressCard.setVisibility(View.GONE);
      }
    });
  }

  public void onClickFlash(View v) {
    if (isFlashing) {
      Toast.makeText(this, "Flash operation already in progress", Toast.LENGTH_SHORT).show();
      return;
    }

    if (!deviceConnected) {
      Toast.makeText(this, R.string.no_device, Toast.LENGTH_SHORT).show();
      return;
    }

    new AlertDialog.Builder(this)
        .setTitle("Flash Firmware")
        .setMessage(
            "This will flash the firmware to your ESP32. Make sure the device is in bootloader mode.\n\nContinue?")
        .setPositiveButton("Flash", (dialog, which) -> startFlashing())
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void startFlashing() {
    isFlashing = true;
    btFlash.setEnabled(false);
    btDetect.setEnabled(false);

    appendLog("\n━━━━━━━━━━━━━━━━━━━━━");
    appendLog("Starting flash operation...");
    updateProgress(0, getString(R.string.flashing));

    executorService.execute(() -> {
      try {
        CommandInterfaceESP32 cmd = new CommandInterfaceESP32(mUploadCallback, mPhysicaloid);

        appendLog("Initializing chip...");
        boolean initSuccess = cmd.initChip();

        if (!initSuccess) {
          mainHandler.post(() -> {
            appendLog("✗ Failed to initialize chip");
            Toast.makeText(this, R.string.flash_failed, Toast.LENGTH_LONG).show();
            finishFlashing(false);
          });
          return;
        }

        appendLog("✓ Chip initialized");

        appendLog("Detecting chip type...");
        int chip = cmd.detectChip();
        String chipName = (chip == cmd.ESP32) ? "ESP32" : "Unknown";
        mainHandler.post(() -> updateDeviceStatus(true, chipName));
        appendLog("✓ Detected: " + chipName);

        appendLog("Changing baud rate to 921600...");
        cmd.changeBaudeRate();
        appendLog("✓ Baud rate changed");

        cmd.init();

        appendLog("Reading firmware file...");
        String selectedFirmware = spinnerFirmware.getText().toString();
        InputStream file1;
        if (selectedFirmware.equals(DEFAULT_FIRMWARE_NAME)) {
          file1 = getAssets().open(ASSET_FILE_NAME_ESP32);
        } else {
          file1 = new FileInputStream(new File(firmwaresDir, selectedFirmware));
        }
        byte[] data = readFile(file1);

        if (data.length == 0) {
          mainHandler.post(() -> {
            appendLog("✗ Firmware file is empty or could not be read");
            Toast.makeText(this, R.string.flash_failed, Toast.LENGTH_LONG).show();
            finishFlashing(false);
          });
          return;
        }

        appendLog("✓ Firmware loaded (" + (data.length / 1024) + " KB)");
        appendLog("Flashing to address 0x1000...");

        cmd.flashData(data, 0x1000, 0);

        appendLog("Resetting chip...");
        cmd.reset();

        mainHandler.post(() -> {
          appendLog("✓ Flash completed successfully!");
          appendLog("━━━━━━━━━━━━━━━━━━━━━\n");
          Toast.makeText(this, R.string.flash_success, Toast.LENGTH_LONG).show();
          finishFlashing(true);
        });

      } catch (IOException e) {
        mainHandler.post(() -> {
          appendLog("✗ Error: " + e.getMessage());
          Toast.makeText(this, R.string.flash_failed, Toast.LENGTH_LONG).show();
          finishFlashing(false);
        });
      }
    });
  }

  private void finishFlashing(boolean success) {
    isFlashing = false;
    updateProgress(-1, null);
    btFlash.setEnabled(deviceConnected);
    btDetect.setEnabled(deviceConnected);
  }

  public void onClickDetect(View v) {
    if (isFlashing) {
      return;
    }

    if (!deviceConnected) {
      Toast.makeText(this, R.string.no_device, Toast.LENGTH_SHORT).show();
      return;
    }

    btDetect.setEnabled(false);
    appendLog("\nDetecting firmware...");

    executorService.execute(() -> {
      FirmwareInfo firm = new FirmwareInfo(mPhysicaloid);
      String version = "";

      if (firm.open(38400)) {
        version = firm.getFirmwarVersion();
        firm.close();
      }

      String finalVersion = version;
      mainHandler.post(() -> {
        if (finalVersion != null && !finalVersion.isEmpty()) {
          appendLog("✓ Detected firmware: " + finalVersion);
        } else {
          appendLog("✗ Could not detect firmware version");
        }
        btDetect.setEnabled(true);
      });
    });
  }

  private byte[] readFile(InputStream inputStream) {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    int length;
    try {
      while ((length = inputStream.read(buffer)) != -1) {
        byteArrayOutputStream.write(buffer, 0, length);
      }
      inputStream.close();
    } catch (IOException e) {
      e.printStackTrace();
      return new byte[0];
    }
    return byteArrayOutputStream.toByteArray();
  }

  private final UploadSTM32CallBack mUploadCallback = new UploadSTM32CallBack() {

    @Override
    public void onUploading(int value) {
      updateProgress(value, getString(R.string.writing));
    }

    @Override
    public void onInfo(String value) {
      // Suppress verbose info messages
    }

    @Override
    public void onPreUpload() {
      updateProgress(0, getString(R.string.erasing));
    }

    @Override
    public void onPostUpload(boolean success) {
      // Handled in main flow
    }

    @Override
    public void onCancel() {
      appendLog("✗ Operation cancelled");
    }

    @Override
    public void onError(UploadSTM32Errors err) {
      appendLog("✗ Error: " + err.toString());
    }
  };

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.main_menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int id = item.getItemId();

    if (id == R.id.action_help) {
      showHelpDialog();
      return true;
    } else if (id == R.id.action_about) {
      showAboutDialog();
      return true;
    } else if (id == R.id.action_manage_firmwares) {
      startActivity(new Intent(this, ManageFirmwareActivity.class));
      return true;
    }

    return super.onOptionsItemSelected(item);
  }

  private void showHelpDialog() {
    new AlertDialog.Builder(this)
        .setTitle(R.string.help_title)
        .setMessage(R.string.help_content)
        .setPositiveButton(R.string.ok, null)
        .show();
  }

  private void showAboutDialog() {
    new AlertDialog.Builder(this)
        .setTitle(R.string.about_title)
        .setMessage(R.string.about_content)
        .setPositiveButton(R.string.ok, null)
        .show();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (mPhysicaloid != null) {
      mPhysicaloid.close();
    }
    executorService.shutdown();
  }
}

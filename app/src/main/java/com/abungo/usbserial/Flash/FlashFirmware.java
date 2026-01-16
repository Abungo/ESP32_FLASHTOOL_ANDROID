package com.abungo.usbserial.Flash;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.abungo.usbserial.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.physicaloid.lib.Boards;
import com.physicaloid.lib.Physicaloid;
import com.physicaloid.lib.programmer.avr.UploadErrors;
import com.physicaloid.lib.usb.driver.uart.UartConfig;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FlashFirmware extends AppCompatActivity {
  Physicaloid mPhysicaloid;

  boolean recoverFirmware = false;
  Boards mSelectedBoard;
  Button btFlash;

  // UI Components
  public AutoCompleteTextView spinnerFirmware;
  public AutoCompleteTextView dropdownBaudRate;
  TextView tvRead;
  private AlertDialog.Builder builder = null;
  private AlertDialog alert;

  private ArrayList<Boards> mBoardList;
  private UartConfig uartConfig;

  // ESP32
  private static final String ASSET_FILE_NAME_ESP32 = "firmwares/ESP32/20240222-v1.22.2.bin";
  
  private String[] itemsBaudRate;
  private String[] itemsFirmwares;

  // Executor for background tasks
  private final ExecutorService executorService = Executors.newSingleThreadExecutor();
  private final Handler mainHandler = new Handler(Looper.getMainLooper());

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    // Toolbar setup
    MaterialToolbar toolbar = findViewById(R.id.topAppBar);
    setSupportActionBar(toolbar);

    // UI Init
    spinnerFirmware = findViewById(R.id.spinnerFirmware);
    dropdownBaudRate = findViewById(R.id.spinnerBaud);
    btFlash = findViewById(R.id.btFlash);
    tvRead = findViewById(R.id.tvRead);

    // Firmware Setup
    itemsFirmwares = new String[] {"ESP32"}; // Removed ESP8266 as it was unimplemented
    ArrayAdapter<String> adapterFirmware =
        new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, itemsFirmwares);
    spinnerFirmware.setAdapter(adapterFirmware);
    spinnerFirmware.setText(itemsFirmwares[0], false); // Default selection

    // Baud Rate Setup
    itemsBaudRate =
        new String[] {
          "1200", "2400", "4800", "9600", "14400", "19200", "28800", "38400", "57600", "115200",
          "230400", "921600"
        };
    ArrayAdapter<String> adapterBaudRate =
        new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, itemsBaudRate);
    dropdownBaudRate.setAdapter(adapterBaudRate);
    dropdownBaudRate.setText("115200", false); // Default selection

    // Physicaloid Init
    mPhysicaloid = new Physicaloid(this);
    mBoardList = new ArrayList<>();
    for (Boards board : Boards.values()) {
      if (board.support > 0) {
        mBoardList.add(board);
      }
    }

    if (!mBoardList.isEmpty()) {
        mSelectedBoard = mBoardList.get(0);
    }

    uartConfig =
        new UartConfig(
            115200,
            UartConfig.DATA_BITS8,
            UartConfig.STOP_BITS1,
            UartConfig.PARITY_NONE,
            false,
            false);

    btFlash.setEnabled(true);
    if (mPhysicaloid.open()) {
      mPhysicaloid.setConfig(uartConfig);
      tvAppend(tvRead, "Device Opened.\n");
    } else {
      tvAppend(tvRead, "Cannot Open Device. Check connection or permissions.\n");
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    close();
    executorService.shutdown();
  }

  public void onClickDismiss(View v) {
    close();
    finish();
  }

  public void onClickRecover(View v) {
    tvRead.setText("Recovering Firmware...\n");
    String selected = spinnerFirmware.getText().toString();

    if (selected.equals("ESP32")) {
      recoverFirmware = true;
      executeUploadESP32();
    } else {
        tvAppend(tvRead, "Recovery not implemented for this selection.\n");
    }
  }

  public void onClickDetect(View v) {
    executeDetect();
  }

  public void onClickFirmwareInfo(View v) {
    tvRead.setText("The following firmwares are available:\n");
    tvRead.append(ASSET_FILE_NAME_ESP32 + "\n");
  }

  public void onClickFlash(View v) {
    String selected = spinnerFirmware.getText().toString();
    if (selected.equals("ESP32")) {
      tvRead.setText("Loading ESP32 firmware...\n");
      recoverFirmware = false;
      executeUploadESP32();
    } else {
        tvAppend(tvRead, "Flashing not implemented for " + selected + "\n");
    }
  }

  // --- Background Tasks ---

  private void executeDetect() {
    showProgressDialog("Detect Firmware", "Attempting to detect firmware...");

    executorService.execute(() -> {
        String version = "";
        FirmwareInfo firm = new FirmwareInfo(mPhysicaloid);
        if(firm.open(38400)) {
             version = firm.getFirmwarVersion();
        } else {
            // Try to reopen if needed or log error
        }

        String finalVersion = version;

        mainHandler.post(() -> {
            if (finalVersion == null || finalVersion.isEmpty()) {
                tvAppend(tvRead, "Firmware Version Not Detected.\n");
            } else {
                tvAppend(tvRead, "Detected Firmware Version: " + finalVersion + "\n");
                // Selection logic based on version could go here if mapped to spinner items
            }
            dismissProgressDialog();
        });
    });
  }

  private byte[] readFile(InputStream inputStream) throws IOException {
    try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        InputStream is = inputStream) {
      byte[] buffer = new byte[1024];
      int len;
      while ((len = is.read(buffer)) != -1) {
        byteArrayOutputStream.write(buffer, 0, len);
      }
      return byteArrayOutputStream.toByteArray();
    }
  private void executeUploadESP32() {
    showProgressDialog("Flashing Firmware", "Flashing Your Firmware...");

    executorService.execute(() -> {
        String[] firmwareFileName = new String[4];

        if (!recoverFirmware) {
            firmwareFileName[0] = ASSET_FILE_NAME_ESP32;
            uploadESP32(firmwareFileName, mUploadSTM32Callback);
        }

        mainHandler.post(this::dismissProgressDialog);
    });
  }


  private void showProgressDialog(String title, String message) {
      builder = new AlertDialog.Builder(FlashFirmware.this);
      builder.setMessage(message)
          .setTitle(title)
          .setCancelable(false)
          .setNegativeButton("Cancel", (dialog, id) -> dialog.cancel());
      alert = builder.create();
      alert.show();
  }

  private void dismissProgressDialog() {
      if (alert != null && alert.isShowing()) {
          alert.dismiss();
      }
  }

  // --- Helper Methods ---

  private byte[] readFile(InputStream inputStream) {
    // Optimization: Use a 4KB buffer to reduce I/O overhead compared to byte-by-byte reading.
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

  public void uploadESP32(String fileName[], UploadSTM32CallBack UpCallback) {
    boolean failed = false;
    InputStream file1 = null;
    CommandInterfaceESP32 cmd;

    cmd = new CommandInterfaceESP32(UpCallback, mPhysicaloid);

    try {
      file1 = getAssets().open(fileName[0]);
    } catch (IOException e) {
      tvAppend(tvRead, "Error: File not found " + fileName[0] + "\n");
      return;
    } catch (Exception e) {
      e.printStackTrace();
      tvAppend(tvRead, "Error opening file: " + e.getMessage() + "\n");
      return;
    }

    dialogAppend("Starting ...");
    boolean ret = cmd.initChip();
    if (!ret) {
       dialogAppend("Chip has not been initiated.");
       failed = true;
    } else {
        dialogAppend("Chip Initiated.");
    }

    if (!failed) {
      int chip = cmd.detectChip();
      if (chip == cmd.ESP32) {
        tvAppend(tvRead, "Chip is ESP32\n");
      }

      dialogAppend("Changing baudrate to 921600");
      cmd.changeBaudeRate();
      cmd.init();

      // Those are the files you want to flush
      try {
        dialogAppend("Flashing file 1 0x1000");
        cmd.flashData(readFile(file1), 0x1000, 0);
        /*
        dialogAppend("Flashing file 2 0x1000");
        cmd.flashData(readFile(file2), 0x1000, 0);

        dialogAppend("Flashing file 3 0x10000");
        cmd.flashData(readFile(file3), 0x10000, 0);
        dialogAppend("Flashing file 4 0x8000");
        cmd.flashData(readFile(file4), 0x8000, 0);
        */
      } catch (IOException e) {
        e.printStackTrace();
        tvAppend(tvRead, "Error reading file: " + e.getMessage() + "\n");
      }
      // we have finish flashing lets reset the board so that the program can start
      cmd.reset();

      dialogAppend("Done");
      tvAppend(tvRead, "Done\n");
    }
  }

  UploadSTM32CallBack mUploadSTM32Callback =
      new UploadSTM32CallBack() {

        @Override
        public void onUploading(int value) {
          dialogAppend("Uploading " + value + " %");
        }

        @Override
        public void onInfo(String value) {
          tvAppend(tvRead, value);
        }

        @Override
        public void onPreUpload() {
          tvAppend(tvRead, "Pre Upload...\n");
        }

        public void info(String value) {
          tvAppend(tvRead, value);
        }

        @Override
        public void onPostUpload(boolean success) {
          mainHandler.post(() -> {
              if (success) {
                tvAppend(tvRead, "Upload Successful\n");
              } else {
                tvAppend(tvRead, "Upload Failed\n");
              }
              dismissProgressDialog();
          });
        }

        @Override
        public void onCancel() {
          tvAppend(tvRead, "Upload Cancelled\n");
        }

        @Override
        public void onError(UploadSTM32Errors err) {
          tvAppend(tvRead, "Upload Error: " + err.toString() + "\n");
        }
      };


  private void tvAppend(TextView tv, CharSequence text) {
    mainHandler.post(() -> tv.append(text));
  }

  private void dialogAppend(CharSequence text) {
    mainHandler.post(() -> {
        if (alert != null && alert.isShowing()) {
            alert.setMessage(text);
        }
    });
  }

  private void close() {
    if (mPhysicaloid != null) {
        mPhysicaloid.close();
    }
  }
}

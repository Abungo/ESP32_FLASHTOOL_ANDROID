package com.abungo.usbserial.Flash;

/**
 * @description: This is used to flash the altimeter firmware from the Android device using an OTG
 *     cable so that the store Android application is compatible with altimeter. This works with the
 *     ATMega328 based altimeters as well as the STM32 based altimeters
 * @author: boris.dureau@neuf.fr
 */
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Handler;

import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;

import android.widget.ImageView;

import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.abungo.usbserial.R;
import com.physicaloid.lib.Boards;
import com.physicaloid.lib.Physicaloid;

import com.physicaloid.lib.programmer.avr.UploadErrors;
import com.physicaloid.lib.usb.driver.uart.UartConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.io.InputStream;
import java.util.ArrayList;

import static com.physicaloid.misc.Misc.toHexStr;

public class FlashFirmware extends AppCompatActivity {
  Physicaloid mPhysicaloid;

  boolean recorverFirmware = false;
  Boards mSelectedBoard;
  Button btFlash;

  public Spinner spinnerFirmware;
  public ImageView imageAlti;
  TextView tvRead;
  private AlertDialog.Builder builder = null;
  private AlertDialog alert;
  private ArrayList<Boards> mBoardList;
  private UartConfig uartConfig;

  // ESP32
  private static final String ASSET_FILE_NAME_ESP32 = "firmwares/ESP32/20240222-v1.22.2.bin";
  
  private String[] itemsBaudRate;
  private String[] itemsFirmwares;
  private Spinner dropdownBaudRate;

  // fast way to call Toast
  private void msg(String s) {
    Toast.makeText(getApplicationContext(), s, Toast.LENGTH_LONG).show();
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {

    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    spinnerFirmware = (Spinner) findViewById(R.id.spinnerFirmware);
    itemsFirmwares =
        new String[] {"ESP32", "ESP8266"};

    ArrayAdapter<String> adapterFirmware =
        new ArrayAdapter<String>(
            this, android.R.layout.simple_spinner_dropdown_item, itemsFirmwares);
    spinnerFirmware.setAdapter(adapterFirmware);
    spinnerFirmware.setSelection(0);

    btFlash = (Button) findViewById(R.id.btFlash);
    tvRead = (TextView) findViewById(R.id.tvRead);

    mPhysicaloid = new Physicaloid(this);
    mBoardList = new ArrayList<Boards>();
    for (Boards board : Boards.values()) {
      if (board.support > 0) {
        mBoardList.add(board);
      }
    }

    mSelectedBoard = mBoardList.get(0);
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

    } else {
      // cannot open
      Toast.makeText(this, "Cannot Open", Toast.LENGTH_LONG).show();
    }

    // baud rate
    dropdownBaudRate = (Spinner) findViewById(R.id.spinnerBaud);
    itemsBaudRate =
        new String[] {
          "1200", "2400", "4800", "9600", "14400", "19200", "28800", "38400", "57600", "115200",
          "230400"
        };

    ArrayAdapter<String> adapterBaudRate =
        new ArrayAdapter<String>(
            this, android.R.layout.simple_spinner_dropdown_item, itemsBaudRate);
    dropdownBaudRate.setAdapter(adapterBaudRate);
    dropdownBaudRate.setSelection(10);

    spinnerFirmware.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          @Override
          public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {}

          @Override
          public void onNothingSelected(AdapterView<?> arg0) {}
        });
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    close();
  }

  public void onClickDismiss(View v) {
    close();
    finish();
  }

  public void onClickRecover(View v) {
    String recoverFileName;
    recoverFileName = "";
    tvRead.setText("");
    tvRead.setText("After Complete Upload");
    if (itemsFirmwares[(int) spinnerFirmware.getSelectedItemId()].equals("ESP32")) {
      recorverFirmware = true;
      new UploadESP32Asyc().execute();
    }
  }

  public void onClickDetect(View v) {
    new DetectAsyc().execute();
  }

  public void onClickFirmwareInfo(View v) {
    tvRead.setText("The following firmwares are available:");
    tvRead.append("\n");
    tvRead.append(ASSET_FILE_NAME_ESP32);
  }

  public void onClickFlash(View v) {
    if (itemsFirmwares[(int) spinnerFirmware.getSelectedItemId()].equals("ESP32")) {
      tvRead.setText("Loading ESP32 firmware\n");
      recorverFirmware = false;
      new UploadESP32Asyc().execute();
    }
  }

  private class DetectAsyc extends AsyncTask<Void, Void, Void> // UI thread
  {
    @Override
    protected void onPreExecute() {
      builder = new AlertDialog.Builder(FlashFirmware.this);
      // Attempting to detect firmware...
      builder
          .setMessage("Detect Firmware")
          .setTitle("Msg Detect Firmware")
          .setCancelable(false)
          .setNegativeButton(
              "Cancel",
              new DialogInterface.OnClickListener() {
                public void onClick(final DialogInterface dialog, final int id) {
                  dialog.cancel();
                }
              });
      alert = builder.create();
      alert.show();
    }

    @Override
    protected Void doInBackground(Void... voids) {

      String version = "";

      FirmwareInfo firm = new FirmwareInfo(mPhysicaloid);
      firm.open(38400);
      version = firm.getFirmwarVersion();
      tvAppend(tvRead,"Firmware Version Not Detected" + version + "\n");

      if (version.equals("AltiMulti")) {
        spinnerFirmware.setSelection(0);
      }
      if (version.equals("AltiMultiV2")) {
        spinnerFirmware.setSelection(1);
      }
      if (version.equals("AltiServo")) {
        spinnerFirmware.setSelection(2);
      }
      if (version.equals("AltiDuo")) {
        spinnerFirmware.setSelection(3);
      }
      if (version.equals("AltiMultiSTM32")) {
        spinnerFirmware.setSelection(4);
      }
      if (version.equals("AltiGPS")) {
        spinnerFirmware.setSelection(5);
      }
      return null;
    }

    @Override
    protected void onPostExecute(
        Void result) // after the doInBackground, it checks if everything went fine
        {
      alert.dismiss();
    }
  }

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
    }

    return byteArrayOutputStream.toByteArray();
  }

  private class UploadESP32Asyc extends AsyncTask<Void, Void, Void> // UI thread
  {

    @Override
    protected void onPreExecute() {
      builder = new AlertDialog.Builder(FlashFirmware.this);
      // Flashing firmware...
      builder
          .setMessage("Flashing Firmware")
          .setTitle("Flashing Your Firmware")
          .setCancelable(false)
          .setNegativeButton(
              "Cancel",
              new DialogInterface.OnClickListener() {
                public void onClick(final DialogInterface dialog, final int id) {
                  dialog.cancel();
                }
              });
      alert = builder.create();
      alert.show();
    }

    @Override
    protected Void doInBackground(Void... voids) {
      String firmwareFileName[] = new String[4];
      if (!recorverFirmware) {
        if (itemsFirmwares[(int) spinnerFirmware.getSelectedItemId()].equals("ESP32")) {
          firmwareFileName[0] = ASSET_FILE_NAME_ESP32;
          uploadESP32(firmwareFileName, mUploadSTM32Callback);
        }
    }
      return null;
    }

    @Override
    protected void onPostExecute(
        Void result) // after the doInBackground, it checks if everything went fine
        {
      alert.dismiss();
    }
  }

  public void uploadESP32(String fileName[], UploadSTM32CallBack UpCallback) {
    boolean failed = false;
    InputStream file1 = null;
    InputStream file2 = null;
    InputStream file3 = null;
    InputStream file4 = null;
    CommandInterfaceESP32 cmd;

    cmd = new CommandInterfaceESP32(UpCallback, mPhysicaloid);

    try {
      file1 = getAssets().open(fileName[0]);

    } catch (IOException e) {
      // e.printStackTrace();
      tvAppend(tvRead, "file not found: " + ASSET_FILE_NAME_ESP32 + "\n");
    } catch (Exception e) {
      e.printStackTrace();
      tvAppend(tvRead, "gethexfile : " + ASSET_FILE_NAME_ESP32 + "\n");
    }
    dialogAppend("Starting ...");
    boolean ret = cmd.initChip();
    if (ret) dialogAppend("Chip Has Not Been Initiated" + ret);
    else {
      dialogAppend("Chip has not been initiated:" + ret);
      failed = true;
    }
    int bootversion = 0;
    if (!failed) {
      // let's detect the chip, not really required but I just want to make sure that
      // it is
      // an ESP32 because this is what the program is for
      int chip = cmd.detectChip();
      if (chip == cmd.ESP32)
        // dialogAppend("Chip is ESP32");
        tvAppend(tvRead, "Chip is ESP32\n");

      // now that we have initialized the chip we can change the baud rate to 921600
      // first we tell the chip the new baud rate
      dialogAppend("Changing baudrate to 921600");
      cmd.changeBaudeRate();
      cmd.init();

      // Those are the files you want to flush
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
      // we have finish flashing lets reset the board so that the program can start
      cmd.reset();

      dialogAppend("done ");
      tvAppend(tvRead, "done");
    }
  }

  Physicaloid.UploadCallBack mUploadCallback =
      new Physicaloid.UploadCallBack() {

        @Override
        public void onUploading(int value) {
          dialogAppend("Uploading" + value + " %");
        }

        @Override
        public void onPreUpload() {
          // Upload : Start
          tvAppend(tvRead, "Pre Upload");
        }

        public void info(String value) {
          tvAppend(tvRead, value);
        }

        @Override
        public void onPostUpload(boolean success) {
          if (success) {
            // Upload : Successful
            tvAppend(tvRead, "Upload Successful");
          } else {
            // Upload fail
            tvAppend(tvRead, "Upload Filed");
          }
          alert.dismiss();
        }

        @Override
        // Cancel uploading
        public void onCancel() {
          tvAppend(tvRead, "Upload Cancelled");
        }

        @Override
        // Error  :
        public void onError(UploadErrors err) {
          tvAppend(tvRead, "Upload Error" + err.toString() + "\n");
        }
      };

  UploadSTM32CallBack mUploadSTM32Callback =
      new UploadSTM32CallBack() {

        @Override
        public void onUploading(int value) {
          dialogAppend("Uploading" + value + " %");
        }

        @Override
        public void onInfo(String value) {
          tvAppend(tvRead, value);
        }

        @Override
        public void onPreUpload() {
          // Upload : Start
          tvAppend(tvRead, "Pre Upload");
        }

        public void info(String value) {
          tvAppend(tvRead, value);
        }

        @Override
        public void onPostUpload(boolean success) {
          if (success) {
            // Upload : Successful
            tvAppend(tvRead, "Upload Successful");
          } else {
            // Upload fail
            tvAppend(tvRead, "Upload Failed");
          }
          alert.dismiss();
        }

        @Override
        // Cancel uploading
        public void onCancel() {
          tvAppend(tvRead, "Upload Cancelled");
        }

        @Override
        // Error  :
        public void onError(UploadSTM32Errors err) {
          tvAppend(tvRead, "Upload Error" + err.toString() + "\n");
        }
      };
  Handler mHandler = new Handler();

  private void tvAppend(TextView tv, CharSequence text) {
    final TextView ftv = tv;
    final CharSequence ftext = text;
    mHandler.post(
        new Runnable() {
          @Override
          public void run() {
            ftv.append(ftext);
          }
        });
  }

  /*private void setRadioButton(RadioButton rb, boolean state) {
      final RadioButton frb = rb;
      final boolean fstate = state;
      mHandler.post(new Runnable() {
          @Override
          public void run() {
              frb.setChecked(fstate);
          }
      });
  }*/

  private void dialogAppend(CharSequence text) {
    final CharSequence ftext = text;
    mHandler.post(
        new Runnable() {
          @Override
          public void run() {
            alert.setMessage(ftext);
          }
        });
  }

  private void close() {
    if (mPhysicaloid.close()) {}
  }
}

package com.abungo.usbserial;
/**
 * @description: This class instanciate pretty much everything including the connection
 * @author: boris.dureau@neuf.fr
 **/

import android.Manifest;
import android.app.Application;

import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.abungo.usbserial.connection.UsbConnection;

import java.io.IOException;
import java.io.InputStream;

import java.util.Locale;

/**
 * @description: This is quite a major class used everywhere because it can point to your connection,
 * appconfig
 * @author: boris.dureau@neuf.fr
 **/
public class ConsoleApplication extends Application {
    private boolean isConnected = false;
    // Store number of flight
    public int NbrOfFlight = 0;
    public int currentFlightNbr = 0;

    private static boolean DataReady = false;
    public long lastReceived = 0;
    public String commandRet = "";

    private double FEET_IN_METER = 1;
    private boolean exit = false;
    private String address, moduleName;
    private String myTypeOfConnection = "bluetooth";// "USB";//"bluetooth";

    private UsbConnection UsbCon = null;

    private Handler mHandler;

    public void setHandler(Handler mHandler) {
        this.mHandler = mHandler;
    }

    public String lastReadResult;
    public String lastData;

    @Override
    public void onCreate() {

        super.onCreate();
        UsbCon = new UsbConnection();
    }

    public void setConnectionType(String TypeOfConnection) {
        myTypeOfConnection = TypeOfConnection;
    }

    public String getConnectionType() {
        return myTypeOfConnection;
    }

    public void setAddress(String bTAddress) {
        address = bTAddress;
    }

    public String getAddress() {
        return address;
    }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String bTmoduleName) {
        moduleName = bTmoduleName;
    }

    public InputStream getInputStream() {
        InputStream tmpIn = null;
        tmpIn = UsbCon.getInputStream();
        return tmpIn;
    }

    public void setConnected(boolean Connected) {
        UsbCon.setUSBConnected(Connected);
    }

    public UsbConnection getUsbCon() {
        return UsbCon;
    }

    public boolean getConnected() {
        boolean ret = false;
        ret = UsbCon.getUSBConnected();
        return ret;
    }
    // connect to the USB
    public boolean connect(UsbManager usbManager, UsbDevice device, int baudRate) {
        boolean state = false;
        if (myTypeOfConnection.equals("usb")) {
            state = UsbCon.connect(usbManager, device, baudRate);
            setConnectionType("usb");

            for (int i =0; i < 3; i++) {
                if (isConnectionValid()) {
                    state = true;
                    break;
                }
            }
            if(!state)
                Disconnect();
        }
        return state;
    }


    public boolean isConnectionValid() {
        boolean valid = false;
        //if(getConnected()) {

        setDataReady(false);
       /* try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }*/
        clearInput();
        //  send 2 commands to get rid off the module connection string on some modules
        write("h;".toString());
        clearInput();
        write("h;".toString());

        String myMessage = "";
        long timeOut = 10000;
        long startTime = System.currentTimeMillis();
        long diffTime = 0;
        //get the results
        //wait for the result to come back
        try {
            /*while (getInputStream().available() <= 0 || diffTime < timeOut) {
                diffTime = System.currentTimeMillis() - startTime;}*/
            while (getInputStream().available() <= 0) ;
        } catch (IOException e) {

        }

        myMessage = "Replacement Message";

        if (myMessage.equals("OK")) {
            lastReadResult = myMessage;
            valid = true;
        } else {
            lastReadResult = myMessage;
            valid = false;
        }
        //turn off telemetry
        clearInput();
        write("y0;".toString());

        return valid;
    }

    public void Disconnect() {
        UsbCon.Disconnect();
    }

    public void write(String data) {
        UsbCon.write(data);
    }

    public void clearInput() {
        UsbCon.clearInput();
    }

    public void setExit(boolean b) {
        this.exit = b;
    }


    public long calculateSentenceCHK(String currentSentence[]) {
        long chk = 0;
        String sentence = "";

        for (int i = 0; i < currentSentence.length - 1; i++) {
            sentence = sentence + currentSentence[i] + ",";
        }
        //Log.d("calculateSentenceCHK", sentence);
        chk = generateCheckSum(sentence);
        return chk;
    }

    public static Integer generateCheckSum(String value) {

        byte[] data = value.getBytes();
        long checksum = 0L;

        for (byte b : data) {
            checksum += b;
        }

        checksum = checksum % 256;

        return new Long(checksum).intValue();

    }
	
    public void setDataReady(boolean value) {
        DataReady = value;
    }

    public boolean getDataReady() {
        return DataReady;
    }
}


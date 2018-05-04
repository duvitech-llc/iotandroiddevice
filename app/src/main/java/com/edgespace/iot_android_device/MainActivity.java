package com.edgespace.iot_android_device;

import android.app.Application;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.gson.JsonObject;
import com.microsoft.azure.sdk.iot.device.DeviceClient;
import com.microsoft.azure.sdk.iot.device.DeviceTwin.DeviceMethodData;
import com.microsoft.azure.sdk.iot.device.IotHubClientProtocol;
import com.microsoft.azure.sdk.iot.device.IotHubEventCallback;
import com.microsoft.azure.sdk.iot.device.IotHubMessageResult;
import com.microsoft.azure.sdk.iot.device.IotHubStatusCode;
import com.microsoft.azure.sdk.iot.device.Message;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "IOT-ANDROID-DEVICE";
    public static AppCompatActivity _activity;

    private static final int METHOD_SUCCESS = 200;
    private static final int METHOD_HUNG = 300;
    private static final int METHOD_NOT_FOUND = 404;
    private static final int METHOD_NOT_DEFINED = 404;

    private final String connString = "HostName=ConnectedCarHub.azure-devices.net;DeviceId=iot-android-device;SharedAccessKey=7uzzZRTOe7VQgBN7SoT7NRR/3v4S7EHyEn9Amfcmijw=";
    private final String deviceId = "iot-android-device";
    private double temperature;
    private double humidity;
    private int sentCount = 0;

    private DeviceClient client = null;

    private static int method_command(Object data)
    {
        System.out.println("invoking command on this device");

        final String temp = new String((byte[])data, Message.DEFAULT_IOTHUB_MESSAGE_CHARSET);

        System.out.println("Data: " + temp);
        _activity.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(_activity, "DATA: " + temp, Toast.LENGTH_SHORT).show();
            }
        });
        // Insert code to invoke command here
        return METHOD_SUCCESS;
    }

    private static int method_default(Object data)
    {
        System.out.println("invoking default method for this device");
        // Insert device specific code here

        final String temp = new String((byte[])data, Message.DEFAULT_IOTHUB_MESSAGE_CHARSET);

        _activity.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(_activity, "DATA: " + temp, Toast.LENGTH_SHORT).show();
            }
        });

        return METHOD_NOT_DEFINED;
    }

    protected static class DeviceMethodStatusCallBack implements IotHubEventCallback
    {
        public void execute(IotHubStatusCode status, Object context)
        {
            System.out.println("IoT Hub responded to device method operation with status " + status.name());
        }
    }

    protected static class SampleDeviceMethodCallback implements com.microsoft.azure.sdk.iot.device.DeviceTwin.DeviceMethodCallback
    {
        @Override
        public DeviceMethodData call(String methodName, Object methodData, Object context)
        {
            DeviceMethodData deviceMethodData ;
            switch (methodName)
            {
                case "command" :
                {

                    int status = method_command(methodData);

                    deviceMethodData = new DeviceMethodData(status, "executed " + methodName);
                    break;
                }
                default:
                {
                    int status = method_default(methodData);
                    deviceMethodData = new DeviceMethodData(status, "executed " + methodName);
                }
            }

            return deviceMethodData;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        _activity = this;
    }


    @Override
    protected void onStart(){
        super.onStart();
        Log.d(TAG, "onStart Entered");

        // Comment/uncomment from lines below to use HTTPS or MQTT protocol
        //IotHubClientProtocol protocol = IotHubClientProtocol.HTTPS;
        IotHubClientProtocol protocol = IotHubClientProtocol.MQTT;


        try {
            client = new DeviceClient(connString, protocol);

            if (protocol == IotHubClientProtocol.MQTT)
            {
                MessageCallbackMqtt callback = new MessageCallbackMqtt();
                Counter counter = new Counter(0);
                client.setMessageCallback(callback, counter);
            } else
            {
                MessageCallback callback = new MessageCallback();
                Counter counter = new Counter(0);
                client.setMessageCallback(callback, counter);
            }

            client.open();

            // subscribe methods
            client.subscribeToDeviceMethod(new SampleDeviceMethodCallback(), null, new DeviceMethodStatusCallBack(), null);

            Log.d(TAG, "Client Opened");

        } catch (Exception e2)
        {
            System.out.println("Exception while opening IoTHub connection: " + e2.toString());
        }


    }

    @Override
    protected void onStop(){
        super.onStop();
        Log.d(TAG, "onStop Entered");
        try {
            if(client != null) {
                client.closeNow();
                Log.d(TAG, "Client Closed");
            }

        } catch (Exception e2)
        {
            System.out.println("Exception while opening IoTHub connection: " + e2.toString());
        }
    }

    private void SendMessage() throws URISyntaxException, IOException
    {
        if (client == null){
            Toast.makeText(this, "Client is null", Toast.LENGTH_SHORT).show();
            return;
        }

        sentCount++;
        temperature = 20.0 + Math.random() * 10;
        humidity = 30.0 + Math.random() * 20;

        String msgStr = "{\"deviceId\":\"" + deviceId + "\",\"messageId\":" + sentCount + ",\"temperature\":" + temperature + ",\"humidity\":" + humidity + "}";
        try
        {
            Message msg = new Message(msgStr);
            msg.setProperty("temperatureAlert", temperature > 28 ? "true" : "false");
            msg.setMessageId(java.util.UUID.randomUUID().toString());
            System.out.println(msgStr);
            EventCallback eventCallback = new EventCallback();
            client.sendEventAsync(msg, eventCallback, sentCount);
        } catch (Exception e)
        {
            System.err.println("Exception while sending event: " + e.getMessage());
        }

    }

    public void btnReceiveOnClick(View v) throws URISyntaxException, IOException
    {
        Button button = (Button) v;

        SendMessage();
    }

    public void fileUpload(String fullFileName){
        try
        {

            File file = new File(fullFileName);
            if(file.isDirectory())
            {
                throw new IllegalArgumentException(fullFileName + " is a directory, please provide a single file name, or use the FileUploadSample to upload directories.");
            }
            else
            {
                client.uploadToBlobAsync(file.getName(), new FileInputStream(file), file.length(), new FileUploadStatusCallBack(), null);
            }

            System.out.println("File upload started with success");

            System.out.println("Waiting for file upload callback with the status...");
        }
        catch (Exception e)
        {
            System.out.println("On exception, shutting down \n" + " Cause: " + e.getCause() + " \nERROR: " +  e.getMessage());

        }
    }

    protected static class FileUploadStatusCallBack implements IotHubEventCallback
    {
        public void execute(IotHubStatusCode status, Object context)
        {
            System.out.println("IoT Hub responded to file upload operation with status " + status.name());
        }
    }

    // Our MQTT doesn't support abandon/reject, so we will only display the messaged received
    // from IoTHub and return COMPLETE
    static class MessageCallbackMqtt implements com.microsoft.azure.sdk.iot.device.MessageCallback
    {
        public IotHubMessageResult execute(Message msg, Object context)
        {
            Counter counter = (Counter) context;
            System.out.println(
                    "Received message " + counter.toString()
                            + " with content: " + new String(msg.getBytes(), Message.DEFAULT_IOTHUB_MESSAGE_CHARSET));

            counter.increment();

            return IotHubMessageResult.COMPLETE;
        }
    }

    static class EventCallback implements IotHubEventCallback
    {
        public void execute(IotHubStatusCode status, Object context)
        {
            Integer i = (Integer) context;
            System.out.println("IoT Hub responded to message " + i.toString()
                    + " with status " + status.name());
        }
    }

    static class MessageCallback implements com.microsoft.azure.sdk.iot.device.MessageCallback
    {
        public IotHubMessageResult execute(Message msg, Object context)
        {
            Counter counter = (Counter) context;
            System.out.println(
                    "Received message " + counter.toString()
                            + " with content: " + new String(msg.getBytes(), Message.DEFAULT_IOTHUB_MESSAGE_CHARSET));

            int switchVal = counter.get() % 3;
            IotHubMessageResult res;
            switch (switchVal)
            {
                case 0:
                    res = IotHubMessageResult.COMPLETE;
                    break;
                case 1:
                    res = IotHubMessageResult.ABANDON;
                    break;
                case 2:
                    res = IotHubMessageResult.REJECT;
                    break;
                default:
                    // should never happen.
                    throw new IllegalStateException("Invalid message result specified.");
            }

            System.out.println("Responding to message " + counter.toString() + " with " + res.name());

            counter.increment();

            return res;
        }
    }

    /**
     * Used as a counter in the message callback.
     */
    static class Counter
    {
        int num;

        Counter(int num) {
            this.num = num;
        }

        int get() {
            return this.num;
        }

        void increment() {
            this.num++;
        }

        @Override
        public String toString() {
            return Integer.toString(this.num);
        }
    }

}

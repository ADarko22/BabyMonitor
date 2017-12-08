package com.example.angelo.babymonitor;

import android.annotation.TargetApi;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;


public class SensorService extends Service {

    private final static String TAG = "BABY_MONITOR_DEBUG";

    private volatile Looper mServiceLooper;
    private volatile ServiceHandler mServiceHandler;

    private static final String ACTION_START_CONNECTION = "START_CONNECTION";
    private static final String ACTION_STOP_CONNECTION = "STOP_CONNECTION";

    private static final String ACTION_DISCONNECT = "DISCONNECT";

    ServerSocket listeningSocket = null;

    Socket cmdInSocket = null;
    InputStream cmdInput = null;
    BufferedReader cmdReader = null;
    SensorCommandHandlerThread cmdThread = null;

    Socket cmdOutSocket = null;
    OutputStream cmdOutput = null;
    PrintWriter cmdWriter = null;

    Socket streamSocket = null;
    OutputStream streamOutput = null;
    AudioStreamSenderThread streamThread = null;

    /*Notification to the Sensor Activity*/
    public static final String DISCONNECT_NOTIFY = "DISCONNECT_NOTIFY";
    public static final String EXTERNAL_DISCONNECT_NOTIFY = "EXT_DISCONNECT NOTIFY";
    public static final String CONNECT_NOTIFY = "CONNECT_NOTIFY";
    Intent intent = null;


    public SensorService() {
        super();
    }

    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            onHandleIntent((Intent)msg.obj);
            // stopSelf(msg.arg1); <-- Removed
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        HandlerThread thread = new HandlerThread("IntentService[SensorService]");
        thread.start();

        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);
    }

    @Override
    public void onStart(Intent intent, int startId) {
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        msg.obj = intent;
        mServiceHandler.sendMessage(msg);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        onStart(intent, startId);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handleActionStopConnection(false);
        mServiceLooper.quit();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        return null;
    }

    /*
    *   CALLING THE REQUIRED SERVICE by sending the appropriate intent!
    * */
    public static void startActionStartConnection(Context context, InetAddress serverAddress) {
        Log.d(TAG,"START INTENT SERVICE: START_CONNECTION");
        Intent intent = new Intent(context, SensorService.class);
        intent.setAction(ACTION_START_CONNECTION);
        intent.putExtra("SERVER_ADDRESS", (Serializable)serverAddress);
        context.startService(intent);
    }

    public static void startActionStopConnection(Context context) {
        Log.d(TAG,"START INTENT SERVICE: STOP_CONNECTION");
        Intent intent = new Intent(context, SensorService.class);
        intent.setAction(ACTION_STOP_CONNECTION);
        context.startService(intent);
    }


    public static void startActionDisconnect(Context context){
        Log.d(TAG,"START INTENT SERVICE: DISCONNECT");
        Intent intent = new Intent(context, SensorService.class);
        intent.setAction(ACTION_DISCONNECT);
        context.startService(intent);
    }

    /*
    *   HANNDLING THE RECEIVED INTENTS
    * */

    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_START_CONNECTION.equals(action)) {

                Log.d(TAG, "ACTION_START_CONNECTION");
                InetAddress serverAddress = (InetAddress) intent.getSerializableExtra("SERVER_ADDRESS");

                handleActionStartConnection(serverAddress);
            }
            else if (ACTION_STOP_CONNECTION.equals(action)) {

                Log.d(TAG, "ACTION_STOP_CONNECTION");
                handleActionStopConnection(false);
            }
            else if(ACTION_DISCONNECT.equals(action)){

                if(cmdWriter != null) {
                    cmdWriter.println(ACTION_DISCONNECT);
                    cmdWriter.flush();
                }

                Log.d(TAG, "ACTION_DISCONNECT");
                handleActionStopConnection(false);
            }
        }
    }

    /**
     * INTENT HANDLERS
     */
    private void handleActionStartConnection(final InetAddress serverAddress) {

        String data;
        byte[] buf = new byte[1024];
        Log.d(TAG, "handleActionStartConnection() STARTED!");
        try {
            listeningSocket = new ServerSocket();
            listeningSocket.setReuseAddress(true);
            listeningSocket.setSoTimeout(0);
            listeningSocket.bind(new InetSocketAddress(serverAddress, 23339));

                /*Accepting the Command Socket (only receive commands)*/
            cmdInSocket = listeningSocket.accept();
            cmdInput = cmdInSocket.getInputStream();
            cmdReader = new BufferedReader(new InputStreamReader(cmdInput));

            Log.d(TAG, "cmdInput socket accepted: " + cmdInSocket.getLocalSocketAddress() + " - " + cmdInSocket.getRemoteSocketAddress());

            cmdOutSocket = listeningSocket.accept();
            cmdOutput = cmdOutSocket.getOutputStream();
            cmdWriter = new PrintWriter(new OutputStreamWriter(cmdOutput));

            Log.d(TAG, "cmdOutput socket accepted: " + cmdOutSocket.getLocalSocketAddress() + " - " + cmdOutSocket.getRemoteSocketAddress());

                /*Connecting the Streaming Socket to the client (only send the stream)*/
            streamSocket = listeningSocket.accept();
            streamOutput = streamSocket.getOutputStream();

            listeningSocket.close();

            Log.d(TAG, "stream socket accepted: " + streamSocket.getLocalSocketAddress() + " - " + streamSocket.getRemoteSocketAddress());

        } catch (IOException e) {
            e.printStackTrace();
        }

        cmdThread = new SensorCommandHandlerThread();
        cmdThread.start();

        streamThread = new AudioStreamSenderThread();
        streamThread.start();

        /*Notify Connection to the Sensor Activity*/
        intent = new Intent();
        intent.setAction(CONNECT_NOTIFY);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

        Log.d(TAG, "handleActionStartConnection() FINISHED!");
    }

    /**
     *
     */
    private void handleActionStopConnection(boolean external) {

        Log.d(TAG, "handleActionStopConnection():"+(external?"external":"internal")+" STARTED!");
        try {
            if(listeningSocket != null && !listeningSocket.isClosed())
                listeningSocket.close();
            if(cmdThread != null) {
                cmdThread.stopService();
                cmdThread.interrupt();
                cmdThread = null;
            }
            if(cmdInSocket != null && !cmdInSocket.isClosed())
                cmdInSocket.close();

            if(cmdOutSocket != null && !cmdOutSocket.isClosed())
                cmdOutSocket.close();

            if(streamThread != null){
                streamThread.disconnectService();
                streamThread.interrupt();
                streamThread = null;
            }
            if(streamSocket != null && !streamSocket.isClosed())
                streamSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        /*Notify Disconnection to the Sensor Activity*/
        intent = new Intent();
        if(external)
            intent.setAction(EXTERNAL_DISCONNECT_NOTIFY);
        else
            intent.setAction(DISCONNECT_NOTIFY);

        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

        Log.d(TAG, "handleActionStopConnection():"+(external?"external":"internal")+" FINISHED!");
    }

    /*TASK to handle client commands...*/
    public class SensorCommandHandlerThread extends Thread{

        final static String START_STREAMING = "START_STREAMING";
        final static String STOP_STREAMING = "STOP_STREAMING";
        final static String DISCONNECT = "DISCONNECT";

        String command = null;

        boolean inService = true;

        @Override
        public void run() {

            inService = true;

            Log.d(TAG, "SensorCommandHandlerThread STARTED!");

            while(inService && cmdOutSocket!= null && !cmdInSocket.isClosed()) {
                try {
                    if(cmdReader != null && cmdInSocket != null) {

                        command = cmdReader.readLine();

                        Log.d(TAG, "CMD received: " + command);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if(command != null){
                    if(command.equals(START_STREAMING)){
                        if(streamThread == null) {
                            streamThread = new AudioStreamSenderThread();
                            streamThread.start();
                        }
                    }
                    else if(command.equals(STOP_STREAMING)){
                        if(streamThread != null) {
                            streamThread.disconnectService();
                            streamThread.interrupt();
                            streamThread = null;
                        }
                    }
                    else if(command.equals(DISCONNECT)) {
                        //close all Sockets and Threads
                        handleActionStopConnection(true);
                    }
                }
            }
        }

        public void startService(){
            inService = true;
        }
        public void stopService(){

            Log.d(TAG, "SensorCommandHandlerThread STOPPED!");
            inService = false;
        }
    }

    /*TASK to handle audio recording & streaming*/
    public class AudioStreamSenderThread extends Thread{


        AudioRecord recorder = null;

        private int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        private int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        private int sampleRate = 44100;
        private int minBuffSize;

        byte buffer[];
        boolean inService = true;

        @Override
        public void run() {

            minBuffSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
            buffer = new byte[minBuffSize];

            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, minBuffSize*4);
            recorder.startRecording();

            inService = true;

            Log.d(TAG, "AudioStreamSenderThread STARTED!");

            while(inService && streamSocket != null && !streamSocket.isClosed()){

                minBuffSize = recorder.read(buffer, 0, buffer.length);

                try {
                    streamOutput.write(buffer, 0, buffer.length);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void disconnectService(){
            if(recorder != null && recorder.getState() == 1) {
                Log.d(TAG, "AudioStreamSenderThread STOPPED!");
                inService = false;
                recorder.stop();
                recorder.release();
            }
        }
    }
}

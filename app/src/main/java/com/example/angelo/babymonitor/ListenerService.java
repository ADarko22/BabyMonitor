package com.example.angelo.babymonitor;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
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
import java.net.Socket;

public class ListenerService extends Service {

    private final static  String TAG = "BABY_MONITOR_DEBUG";

    private volatile Looper mServiceLooper;
    private volatile ServiceHandler mServiceHandler;

    private static final String ACTION_START_CONNECTION = "START_CONNECTION";
    private static final String ACTION_STOP_CONNECTION = "STOP_CONNECTION";

    final static String ACTION_START_STREAMING = "START_STREAMING";
    final static String ACTION_STOP_STREAMING = "STOP_STREAMING";
    final static String ACTION_DISCONNECT = "DISCONNECT";

    Socket cmdOutSocket = null;
    OutputStream cmdOutput = null;
    PrintWriter cmdWriter = null;

    Socket cmdInSocket = null;
    InputStream cmdInput = null;
    BufferedReader cmdReader = null;
    ListenerCommandHandlerThread cmdThread = null;

    Socket streamSocket = null;
    InputStream streamInput = null;
    AudioStreamReceiverThread streamThread = null;

    /*Notification to the Listener Activity*/
    public static final String DISCONNECT_NOTIFY = "DISCONNECT_NOTIFY";
    public static final String CONNECT_NOTIFY = "CONNECT_NOTIFY";
    Intent intent = null;


    public ListenerService() {
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
        HandlerThread thread = new HandlerThread("IntentService[ListenerService]");
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
        handleActionStopConnection();
        mServiceLooper.quit();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * CALLING Service Actions with INTENTS
     */
    public static void startActionStartConnection(Context context, InetAddress serverAddress) {
        Log.d(TAG, "START INTENT SERVICE: START_CONNECTION");
        Intent intent = new Intent(context, ListenerService.class);
        intent.setAction(ACTION_START_CONNECTION);

        intent.putExtra("SERVER_ADDRESS", (Serializable)serverAddress);
        context.startService(intent);
    }


    public static void startActionStopConnection(Context context) {
        Log.d(TAG, "START INTENT SERVICE: STOP_CONNECTION");
        Intent intent = new Intent(context, ListenerService.class);
        intent.setAction(ACTION_STOP_CONNECTION);

        context.startService(intent);
    }

    public static void startActionStartStreaming(Context context){
        Log.d(TAG, "START INTENT SERVICE: START_STREAMING");
        Intent intent = new Intent(context, ListenerService.class);
        intent.setAction(ACTION_START_STREAMING);

        context.startService(intent);
    }

    public static void startActionStopStreaming(Context context){
        Log.d(TAG, "START INTENT SERVICE: STOP_STREAMING");
        Intent intent = new Intent(context, ListenerService.class);
        intent.setAction(ACTION_STOP_STREAMING);

        context.startService(intent);
    }

    public static void startActionDisconnect(Context context){
        Log.d(TAG, "START INTENT SERVICE: DISCONNECT");
        Intent intent = new Intent(context, ListenerService.class);
        intent.setAction(ACTION_DISCONNECT);

        context.startService(intent);
    }

    /*
    * HANDLING INTENT ACTIONS
    * */
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_START_CONNECTION.equals(action)) {
                Log.d(TAG, "ACTION_START_CONNECTION");
                InetAddress serverAddress = (InetAddress) intent.getSerializableExtra("SERVER_ADDRESS");
                handleActionStartConnection(serverAddress);

            } else if (ACTION_STOP_CONNECTION.equals(action)) {
                Log.d(TAG, "ACTION_STOP_CONNECTION");
                handleActionStopConnection();
            }
            else if (ACTION_START_STREAMING.equals(action)) {

                cmdWriter.println(ACTION_START_STREAMING);
                cmdWriter.flush();

                if (streamThread == null){
                    Log.d(TAG, "ACTION_START_STREAMING");
                    streamThread = new AudioStreamReceiverThread();
                    streamThread.start();
                }
            }
            else if (ACTION_STOP_STREAMING.equals(action)){

                cmdWriter.println(ACTION_STOP_STREAMING);
                cmdWriter.flush();

                if(streamThread != null) {
                    Log.d(TAG, "ACTION_STOP_STREAMING");
                    streamThread.disconnectService();
                    streamThread.interrupt();
                    streamThread = null;
                }
            }
            else if (ACTION_DISCONNECT.equals(action)){

                if(cmdWriter != null) {
                    cmdWriter.println(ACTION_DISCONNECT);
                    cmdWriter.flush();
                }

                Log.d(TAG, "ACTION_DISCONNECT");
                handleActionStopConnection();
            }
        }
    }

    /**
     *
     */
    private void handleActionStartConnection(final InetAddress serverAddress) {

        Log.d(TAG, "handleActionStartConnection() STARTED!");
        try {
            cmdOutSocket = new Socket();
            cmdOutSocket.bind(null);

            cmdOutSocket.connect(new InetSocketAddress(serverAddress, 23339));
            cmdOutput = cmdOutSocket.getOutputStream();
            cmdWriter = new PrintWriter(new OutputStreamWriter(cmdOutput));

            Log.d(TAG, "cmdOutput socket connected:" +cmdOutSocket.getLocalSocketAddress()+ " - "+ cmdOutSocket.getRemoteSocketAddress());

            cmdInSocket = new Socket();
            cmdInSocket.bind(null);

            cmdInSocket.connect(new InetSocketAddress(serverAddress, 23339));
            cmdInput = cmdInSocket.getInputStream();
            cmdReader = new BufferedReader(new InputStreamReader(cmdInput));


            Log.d(TAG, "cmdInput socket connected: "+cmdInSocket.getLocalSocketAddress()+ " - "+ cmdInSocket.getRemoteSocketAddress());

            streamSocket = new Socket();
            streamSocket.bind(null);

            streamSocket.connect(new InetSocketAddress(serverAddress, 23339));
            streamInput = streamSocket.getInputStream();

            Log.d(TAG, "stream socket connected: "+streamSocket.getLocalSocketAddress()+ " - "+ streamSocket.getRemoteSocketAddress());

        } catch (IOException e) {
            e.printStackTrace();
        }

        cmdThread = new ListenerCommandHandlerThread();
        cmdThread.start();

        streamThread = new AudioStreamReceiverThread();
        streamThread.start();

        /*Notify Connection to the Listener Activity*/
        intent = new Intent();
        intent.setAction(CONNECT_NOTIFY);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

        Log.d(TAG, "handleActionStartConnection() FINISHED!");
    }

    /**
     *
     */
    private void handleActionStopConnection() {

        Log.d(TAG, "handleActionStopConnection() STARTED!");
        try {
            if(cmdOutSocket != null && !cmdOutSocket.isClosed())
                cmdOutSocket.close();

            if(cmdThread != null){
                cmdThread.stopService();
                cmdThread.interrupt();
                cmdThread = null;
            }

            if(cmdInSocket != null && !cmdInSocket.isClosed())
                cmdInSocket.close();

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

        /*Notify Disconnection to the Listener Activity*/
        intent = new Intent();
        intent.setAction(DISCONNECT_NOTIFY);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

        Log.d(TAG, "handleActionStopConnection() FINISHED!");
    }

    /*Streaming & Playing TASK*/
    public class AudioStreamReceiverThread extends Thread{

        private AudioTrack speaker = null;

        private int sampleRate = 44100;
        private int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
        private int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int minBuffSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat);

        boolean inService = true;

        @Override
        public void run() {

            byte buffer[] = new byte[minBuffSize];

            if(speaker == null) {
                speaker = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, channelConfig, audioFormat, minBuffSize * 4, AudioTrack.MODE_STREAM);
                speaker.play();
            }
            inService = true;

            Log.d(TAG, "AudioStreamReceiverThread STARTED!");

            while(inService && streamSocket != null && !streamSocket.isClosed()){

                try {
                    streamInput.read(buffer);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if(speaker != null)
                    speaker.write(buffer, 0, minBuffSize);
            }
        }

        public void disconnectService(){
            if(speaker!= null && speaker.getState() == 1) {
                Log.d(TAG, "AudioStreamReceiverThread STOPPED!");
                inService = false;
                speaker.stop();
                speaker.release();
            }
        }
    }

    public class ListenerCommandHandlerThread extends Thread{

        final static String START_STREAMING = "START_STREAMING";
        final static String STOP_STREAMING = "STOP_STREAMING";
        final static String DISCONNECT = "DISCONNECT";

        String command = null;

        boolean inService = true;

        @Override
        public void run() {

            inService = true;

            Log.d(TAG, "ListenerCommandHandlerThread STARTED!");

            while(inService && cmdInSocket != null && !cmdInSocket.isClosed()) {
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
                            streamThread = new AudioStreamReceiverThread();
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
                        handleActionStopConnection();
                    }
                }
            }
        }

        public void startService(){
            inService = true;
        }
        public void stopService(){
            Log.d(TAG, "ListenerCommandHandlerThread STOPPED!");
            inService = false;
        }
    }
}

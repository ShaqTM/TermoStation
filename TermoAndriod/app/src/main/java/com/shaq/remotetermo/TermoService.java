package com.shaq.remotetermo;


import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;


import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.DatagramPacket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;

import androidx.annotation.NonNull;


public class TermoService extends Service {
    public static final String ACTION_UPDATE_PREFS= "com.shaq.remotetermo.action.UPDATE_PREFS";
    public static final String ACTION_UPDATE_DATA= "com.shaq.remotetermo.action.UPDATE_DATA";
    public static final String ACTION_UPDATE_WIDGET= "com.shaq.remotetermo.action.UPDATE_WIDGET";
    public static final String ACTION_NO_CONNECTION= "com.shaq.remotetermo.action.NO_CONNECTION";
    private static final int PORT = 5353;
    MulticastSocket clientSocket;
    private String mDeviceIP;
    private String mDevicemDNS;
    private boolean mSearchWiFi;
    private boolean mSearchByName;
    private String mDevicePort;
    private String mDeviceName;
    private String mDeviceAddress;
    private boolean firebaseEnable;
    private FirebaseDatabase database = FirebaseDatabase.getInstance();
    private DatabaseReference dataCounterRef = database.getReference("dataCounter");
    private DatabaseReference dataRef;

    Runnable findAndReadRunnable = new Runnable() {
        @Override
        public void run() {
            searching = true;
            if (mSearchWiFi&&!getNetworkState().equals("")){
                searching = false;
                return;
            }
            if (mSearchByName&&mDeviceAddress.equals("")){
                try {
                    clientSocket = new MulticastSocket(PORT);
                    InetAddress group = InetAddress.getByName("224.0.0.251");
                    clientSocket.setBroadcast(true);
                    clientSocket.joinGroup(group);
                    send();
                    if (!receive()){
                        searching = false;
                        clientSocket.close();
                        return;
                    };
                    clientSocket.close();
                }
                catch (IOException e){
                    mDeviceAddress = "";
                    searching = false;
                    return;
            }}
            else if (mDeviceAddress.equals("")){
                InetAddress ip;
                try {
                    ip = InetAddress.getByName(mDeviceIP);
                }
                catch(UnknownHostException e){
                    mDeviceAddress ="";
                    searching = false;
                    return;
                }
                mDeviceAddress = ip.getHostAddress()+":"+mDevicePort;
            }
            URL url;
            Bundle data;
            try {
                url = new URL("http://" + mDeviceAddress + "/tempData");
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                data = readStream(in);
                data.putString("device",mDeviceName+" : "+mDeviceAddress);
                Calendar calendar = Calendar.getInstance();
                SimpleDateFormat sd = new SimpleDateFormat("dd.MM.yyyy kk:mm:ss");
                data.putString("refreshTime",sd.format(calendar.getTime()));

                Intent intent = new Intent(ACTION_UPDATE_WIDGET);
                intent.putExtra("data", data);
                getApplicationContext().sendBroadcast(intent);

            }
            catch (IOException e){
                mSendBroadCast(getString(R.string.deviceNotFound));
                mDeviceAddress = "";
            }

            searching = false;
        }
    };
    boolean searching = false;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)){
                findAndRead();

            }
            else if(intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)||intent.getAction().equals(WifiManager.WIFI_STATE_CHANGED_ACTION)){
                String networkState = getNetworkState();
                if (networkState.equals("")){
                    updatePrefs();
                }
                else{
                    mSendBroadCast(networkState);
                }

            }
        }

    };


    public TermoService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        registerReceiver(receiver, filter);
        searching = false;
        mDeviceAddress = "";
        updatePrefs();

    }

    @Override
    public void onDestroy() {
        unregisterReceiver(receiver);
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent!=null&&intent.getAction().equals(ACTION_UPDATE_PREFS)){
            updatePrefs();
        }
        else if(intent!=null&&intent.getAction().equals(ACTION_UPDATE_DATA)){
            findAndRead();
        }

        return super.onStartCommand(intent, flags, startId);
    }

    private void updatePrefs(){
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        mDeviceName = prefs.getString(getString(R.string.device_name),"");
        mDevicemDNS = prefs.getString(getString(R.string.device_mDNS),"");
        mDeviceIP = prefs.getString(getString(R.string.device_ip),"");
        mDevicePort = prefs.getString(getString(R.string.device_port),"");
        mSearchWiFi = prefs.getBoolean(getString(R.string.searchWiFiOnly),true);
        mSearchByName = prefs.getBoolean(getString(R.string.searchByName),true);
        firebaseEnable = prefs.getBoolean(getString(R.string.firebase_enable),false);
        mDeviceAddress = "";
        findAndRead();
    }

    private void findAndRead(){

        if (firebaseEnable){
            dataCounterRef.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    // This method is called once with the initial value and again
                    // whenever data at this location is updated.
                    Integer dataCounter = dataSnapshot.getValue(Integer.class);
                    dataRef = database.getReference(dataCounter.toString());
                    dataRef.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                            Bundle data = new Bundle();


                            data.putDouble("outTemp",new BigDecimal((Double) dataSnapshot.child("outsideTemp").getValue()).setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue());
                            data.putDouble("inTemp",new BigDecimal((Double) dataSnapshot.child("insideTemp").getValue()).setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue());
                            data.putDouble("inHum",new BigDecimal((Double) dataSnapshot.child("insideHumidity").getValue()).setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue());
                            data.putDouble("inPres",new BigDecimal((Double) dataSnapshot.child("insidePressure").getValue()).setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue());
                            data.putString("device",mDeviceName);
                            Date date = new Date(((Long)dataSnapshot.child("timeStamp").getValue()-10*60*60)*1000);
                            SimpleDateFormat sd = new SimpleDateFormat("dd.MM.yyyy kk:mm:ss");
                            data.putString("refreshTime",sd.format(date));

                            Intent intent = new Intent(ACTION_UPDATE_WIDGET);
                            intent.putExtra("data", data);
                            getApplicationContext().sendBroadcast(intent);

                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError databaseError) {
                            mSendBroadCast(getString(R.string.deviceNotFound));
                        }
                    });
                }

                @Override
                public void onCancelled(DatabaseError error) {
                    mSendBroadCast(getString(R.string.deviceNotFound));
                }
            });


        } else {
            if (!searching){
                Thread thread = new Thread(findAndReadRunnable);
                thread.start();
            }
        }

    }

    private Bundle readStream(InputStream is) {
        Bundle data = new Bundle();
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            int i = is.read();
            while (i != -1) {
                bo.write(i);
                i = is.read();
            }
            JSONObject jsonObject = new JSONObject(bo.toString());
            data.putDouble("outTemp",jsonObject.getDouble("outTemp"));
            data.putDouble("inTemp",jsonObject.getDouble("inTemp"));
            data.putDouble("inHum",jsonObject.getDouble("inHum"));
            if (jsonObject.has("inPres")){
                data.putDouble("inPres",jsonObject.getDouble("inPres"));
            }

        } catch (IOException|JSONException e) {
            data.putDouble("outTemp",0);
            data.putDouble("inTemp",0);
            data.putDouble("inHum",0);
            data.putDouble("inPres",0);
        }
        return data;

    }



    private void send() throws IOException {


        ByteArrayOutputStream message = new ByteArrayOutputStream(50);
        //ID
        message.write(0);
        message.write(0);
        //Flags
        message.write(0);
        message.write(0);
        //number of questions
        message.write(0);
        message.write(1);
        //number of answers
        message.write(0);
        message.write(0);
        //number of ///
        message.write(0);
        message.write(0);
        //number of ///
        message.write(0);
        message.write(0);


        writeName(mDevicemDNS+"._http._tcp.local",message);
        //type
        message.write(0);
        message.write(33);
        //class
        message.write(0);
        message.write(1);
        byte[] result = message.toByteArray();
        try {
            message.close();
        } catch (IOException exception) {}

        DatagramPacket sendPacket = new DatagramPacket(result, result.length, InetAddress.getByName("224.0.0.251"), PORT);
        clientSocket.send(sendPacket);
    }
    private boolean receive () {
        byte try_count = 0;
        while (!clientSocket.isClosed()) {
            try {
                byte[] buf = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                clientSocket.setSoTimeout(500);
                clientSocket.receive(packet);
                ByteArrayInputStream in = new ByteArrayInputStream(packet.getData());
                //ID
                readUnsignedShort(in);
                //Flags
                readUnsignedShort(in);
                //number of request
                int reqNum = readUnsignedShort(in);
                //number of answers
                int ansNum = readUnsignedShort(in);
                //number of ...
                readUnsignedShort(in);
                //number of ...
                readUnsignedShort(in);
                ///              if (ansNum==0){
                //               continue;
                //         }
                for (int i=0;i<reqNum;i++){
                    readRequest(in);
                    //Тип и класс
                    readUnsignedShort(in);
                    readUnsignedShort(in);

                }
                if (ansNum!=0){
                    if (!readRequest(in).equals(mDeviceName+"._http._tcp.local")){
                        continue;
                    }
                }
                for (int i=0;i<ansNum;i++){

                    int ansType = readUnsignedShort(in);

                    readUnsignedShort(in);
                    readInt(in);

                    //Длина данных
                    int resLen = readUnsignedShort(in);
                    if (ansType ==33) {

                        readUnsignedShort(in);//priority
                        readUnsignedShort(in);//weight
                        readUnsignedShort(in);
                        readRequest(in);
                        readRequest(in);
                    }
                    else if (ansType ==1) {
                        byte[] inetaddr = new byte[4];
                        inetaddr[0] = (byte)readUnsignedByte(in);
                        inetaddr[1] = (byte)readUnsignedByte(in);
                        inetaddr[2] = (byte)readUnsignedByte(in);
                        inetaddr[3] = (byte)readUnsignedByte(in);
                        mDeviceAddress = InetAddress.getByAddress(inetaddr).getHostAddress();
                        return true;
                    }
                    else {
                        for (int x = 0; x < resLen; x++) {
                            readUnsignedByte(in);
                        }
                    }

                }

            } catch (IOException e) {
                e.printStackTrace();
                if (try_count>=10){
                    mDeviceAddress = "";
                    mSendBroadCast(getString(R.string.deviceNotFound));
                    return false;
                }
                try_count+=1;
            }
        }
        mSendBroadCast(getString(R.string.deviceNotFound));
        mDeviceAddress = "";
        return false;
    }

    private void writeName(String name, ByteArrayOutputStream message) {
        String aName = name;
        while (true) {
            int n = aName.indexOf('.');
            if (n < 0) {
                n = aName.length();
            }
            if (n <= 0) {
                message.write(0);
                return;
            }
            String label = aName.substring(0, n);
            writeUTF(label, 0, label.length(), message);
            aName = aName.substring(n);
            if (aName.startsWith(".")) {
                aName = aName.substring(1);
            }
        }
    }
    private void writeUTF(String str, int off, int len, ByteArrayOutputStream message) {
        // compute utf length
        int utflen = 0;
        for (int i = 0; i < len; i++) {
            int ch = str.charAt(off + i);
            if ((ch >= 0x0001) && (ch <= 0x007F)) {
                utflen += 1;
            } else {
                if (ch > 0x07FF) {
                    utflen += 3;
                } else {
                    utflen += 2;
                }
            }
        }
        // write utf length
        message.write(utflen);
        // write utf data
        for (int i = 0; i < len; i++) {
            int ch = str.charAt(off + i);
            if ((ch >= 0x0001) && (ch <= 0x007F)) {
                message.write(ch);
            } else {
                if (ch > 0x07FF) {
                    message.write(0xE0 | ((ch >> 12) & 0x0F));
                    message.write(0x80 | ((ch >> 6) & 0x3F));
                    message.write(0x80 | ((ch >> 0) & 0x3F));
                } else {
                    message.write(0xC0 | ((ch >> 6) & 0x1F));
                    message.write(0x80 | ((ch >> 0) & 0x3F));
                }
            }
        }
    }

    private String readUTF(int len,ByteArrayInputStream data) {
        StringBuilder buffer = new StringBuilder(len);
        for (int index = 0; index < len; index++) {
            int ch = this.readUnsignedByte(data);
            switch (ch >> 4) {
                case 0:
                case 1:
                case 2:
                case 3:
                case 4:
                case 5:
                case 6:
                case 7:
                    // 0xxxxxxx
                    break;
                case 12:
                case 13:
                    // 110x xxxx 10xx xxxx
                    ch = ((ch & 0x1F) << 6) | (this.readUnsignedByte(data) & 0x3F);
                    index++;
                    break;
                case 14:
                    // 1110 xxxx 10xx xxxx 10xx xxxx
                    ch = ((ch & 0x0f) << 12) | ((this.readUnsignedByte(data) & 0x3F) << 6) | (this.readUnsignedByte(data) & 0x3F);
                    index++;
                    index++;
                    break;
                default:
                    // 10xx xxxx, 1111 xxxx
                    ch = ((ch & 0x3F) << 4) | (this.readUnsignedByte(data) & 0x0f);
                    index++;
                    break;
            }
            buffer.append((char) ch);
        }
        return buffer.toString();
    }

    private String readRequest(ByteArrayInputStream data){
        int len = readUnsignedByte(data);
        String result = "";
        while (len!=0){
            if(result.equals("")) {
                result = readUTF(len, data);
            }
            else{
                result = result + "." + readUTF(len, data);
            }
            len = readUnsignedByte(data);
        }
        return result;
    }

    private int readUnsignedByte(ByteArrayInputStream data) {
        return (data.read() & 0xFF);
    }
    private int readUnsignedShort(ByteArrayInputStream data) {
        return (readUnsignedByte(data) << 8) | this.readUnsignedByte(data);
    }
    private int readInt(ByteArrayInputStream data) {
        return (this.readUnsignedShort(data) << 16) | this.readUnsignedShort(data);
    }

    private void mSendBroadCast(String state){
        Bundle data = new Bundle();
        Intent i = new Intent(ACTION_NO_CONNECTION);
        data.putString("device",state);
        Calendar calendar = Calendar.getInstance();

        i.putExtra("data", data);
        getApplicationContext().sendBroadcast(i);
    }
    private String getNetworkState(){
        WifiManager wiFiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wiFiManager.getWifiState()==WifiManager.WIFI_STATE_ENABLED){
            WifiInfo wifiInfo = wiFiManager.getConnectionInfo();
            if (wifiInfo!=null&&wifiInfo.getBSSID()!=null&&!wifiInfo.getBSSID().equals("")){
                return ""; //WiFi connected
            }
            else{
                return getString(R.string.WiFi_noConnection);
            }
        }
        return getString(R.string.WiFi_off);
    }
}



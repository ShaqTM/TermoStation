package com.shaq.remotetermo;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.collection.LongSparseArray;

import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.os.AsyncTask;


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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;


public class MainActivity extends AppCompatActivity {
    private TextView textDeviceIP;
    private TextView textInHum;
    private TextView textInTemp;
    private TextView textOutTemp;
    private TextView textPres;
    private String mDeviceMDNS;
    private Handler handler;
    private GetTempTask  getTempTask;
    private ResolveTask resolveTask;
    private static final int PORT = 5353;
    private MulticastSocket clientSocket;
    private InetAddress group;
    private FirebaseDatabase database = FirebaseDatabase.getInstance();
    private DatabaseReference dataCounterRef = database.getReference("dataCounter");
    private DatabaseReference dataRef;
    private ArrayList<Long> timeArrayList;
    private LongSparseArray<HashMap<String,Double>> dataArray;
    private ImageView graphView;
    private boolean inTemp;
    private boolean outTemp;
    private boolean hum;
    private boolean pres;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button btn_Settings = (Button) findViewById(R.id.buttonSettings);
        btn_Settings.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(getApplicationContext(), SettingsActivity.class);
                startActivity(i);
            }
        });
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        inTemp = prefs.getBoolean("inTemp",false);
        outTemp = prefs.getBoolean("outTemp",false);
        pres = prefs.getBoolean("pres",false);
        hum = prefs.getBoolean("hum",false);

        Switch swInTemp = findViewById(R.id.switchTmpIn);
        swInTemp.setChecked(inTemp);
        swInTemp.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("inTemp",isChecked);
                editor.apply();
                inTemp = isChecked;
                graphView.setBackground(new MyGraphDrawable());
            }
        });

        Switch swOutTemp = findViewById(R.id.switchTmpOut);
        swOutTemp.setChecked(outTemp);
        swOutTemp.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("outTemp",isChecked);
                editor.apply();
                outTemp = isChecked;
                graphView.setBackground(new MyGraphDrawable());
            }
        });

        Switch swPres = findViewById(R.id.switchPres);
        swPres.setChecked(pres);
        swPres.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("pres",isChecked);
                editor.apply();
                pres = isChecked;
                graphView.setBackground(new MyGraphDrawable());
            }
        });

        Switch swHum = findViewById(R.id.switchHum);
        swHum.setChecked(hum);
        swHum.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("him",isChecked);
                editor.apply();
                hum = isChecked;
                graphView.setBackground(new MyGraphDrawable());
            }
        });



        Button btn_Search = (Button) findViewById(R.id.buttonSearch);
        btn_Search.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                if (prefs.getBoolean(getString(R.string.firebase_enable),false)){
                    dataCounterRef.addValueEventListener(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            // This method is called once with the initial value and again
                            // whenever data at this location is updated.
                            Integer dataCounter = dataSnapshot.getValue(Integer.class);
                            textDeviceIP.setText(dataCounter.toString());
                            dataRef = database.getReference(dataCounter.toString());
                            dataRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                    textInHum.setText(dataSnapshot.child("insideHumidity").getValue().toString());
                                    textInTemp.setText(dataSnapshot.child("insideTemp").getValue().toString());
                                    textOutTemp.setText(dataSnapshot.child("outsideTemp").getValue().toString());
                                    textPres.setText(dataSnapshot.child("insidePressure").getValue().toString());
                                    Date date = new Date(((Long)dataSnapshot.child("timeStamp").getValue()-10*60*60)*1000);
                                    SimpleDateFormat sd = new SimpleDateFormat("dd.MM.yyyy kk:mm:ss");
                                    textDeviceIP.setText(sd.format(date));
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError databaseError) {

                                }
                            });
                        }

                        @Override
                        public void onCancelled(DatabaseError error) {
                            textDeviceIP.setText(getString(R.string.deviceNotFound));
                        }
                    });


                } else {
                    boolean searchWiFiOnly = prefs.getBoolean(getString(R.string.searchWiFiOnly), true);
                    boolean connected = false;
                    WifiManager wiFiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    if (searchWiFiOnly && wiFiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED) {
                        WifiInfo wifiInfo = wiFiManager.getConnectionInfo();
                        if (wifiInfo != null && wifiInfo.getBSSID() != null && !wifiInfo.getBSSID().equals("")) {
                            connected = true;
                        }
                        if (!connected) {
                            textDeviceIP.setText(getString(R.string.WiFi_noConnection));
                            return;
                        }
                    } else if (searchWiFiOnly) {
                        textDeviceIP.setText(getString(R.string.WiFi_off));
                        return;
                    }


                    if (prefs.getBoolean(getString(R.string.searchByName), true)) {
                        mDeviceMDNS = prefs.getString(getString(R.string.device_mDNS), "");
                        if (mDeviceMDNS.equals("")) {
                            Toast.makeText(getApplicationContext(), "Введите имя устройства!!", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (resolveTask != null && resolveTask.getStatus() == AsyncTask.Status.RUNNING) {
                            return;
                        }
                        resolveTask = new ResolveTask();
                        resolveTask.execute(mDeviceMDNS);
                    } else {
                        String deviceAddress = prefs.getString(getString(R.string.device_ip), "");
                        String devicePort = prefs.getString(getString(R.string.device_port), "");
                        if (deviceAddress.equals("")) {
                            Toast.makeText(getApplicationContext(), "Введите адрес устройства!!", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (getTempTask != null) {
                            if (getTempTask.getStatus() == AsyncTask.Status.RUNNING) {
                                getTempTask.cancel(true);
                            }
//                        try {
                            //                           Timer timer = new Timer();
                            //                           timer.wait(1000);
                            //                       }
                            //                       catch (InterruptedException e){}
                            if (getTempTask.getStatus() == AsyncTask.Status.RUNNING) {
                                Toast.makeText(getApplicationContext(), "Получение данных не закончено!!", Toast.LENGTH_SHORT).show();
                            }
                        }
                        getTempTask = new GetTempTask();
                        getTempTask.execute(deviceAddress, devicePort);
                    }
                }
            }
        });
        textDeviceIP =  findViewById(R.id.deviceIP);
        textInHum =  findViewById(R.id.inHum);
        textInTemp =  findViewById(R.id.inTemp);
        textOutTemp =  findViewById(R.id.outTemp);
        textPres =  findViewById(R.id.pres);
        handler = new Handler(){
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                Bundle msgData = msg.getData();
                textDeviceIP.setText(msgData.getString("textDeviceIP"));
            }
        };
        graphView = findViewById(R.id.graphView);


        timeArrayList = new ArrayList<>();
        dataArray = new LongSparseArray<>();
        Button btn_Graph = findViewById(R.id.buttonGraph);
        btn_Graph.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DatabaseReference dbRef = database.getReference();
                dbRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        timeArrayList.clear();
                        dataArray.clear();
                        for (DataSnapshot child:dataSnapshot.getChildren()){
                            if (child.getKey().equals("dataCounter")){
                                continue;
                            }
                            Long time = child.child("timeStamp").getValue(Long.class);
                            if (time*1000> Calendar.getInstance().getTimeInMillis()+5*24*60*60*1000 || time*1000<Calendar.getInstance().getTimeInMillis()-5*24*60*60*1000){
                                continue;
                            }
                            timeArrayList.add(time);
                            HashMap<String,Double> tempData = new HashMap<>();

                            for (DataSnapshot value:child.getChildren()){
                                if (value.getKey().equals("timeStamp")){
                                    continue;
                                }
                                tempData.put(value.getKey(), value.getValue(Double.class));
                            }
                            dataArray.put(time,tempData);
                        }
                        Collections.sort(timeArrayList);
                        graphView.setBackground(new MyGraphDrawable());
                        //graphView.invalidate();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {

                    }
                });


            }
        });

    }

    @Override
    public void onStop() {
        super.onStop();
    }

    private class GetTempTask extends AsyncTask<Object, String, Void> {

        @Override
        protected Void doInBackground(Object... params) {
            InetAddress ip;
            String address;
            if (params.length>1) {
                try {
                    ip= InetAddress.getByName((String)params[0]);
                } catch (UnknownHostException e) {
                    publishProgress("","Ошибка в адресе устройства!!");
                    return null;
                }

                address = ip.getHostAddress() + ":" + (String) params[1];
            }
            else {
                ip = (InetAddress)params[0];
                address = ip.getHostAddress();

            }
            String response;
            URL url;
            Log.d("SHAQ","http://" + address + "/tempData");
            try {
                url = new URL("http://" + address + "/tempData");
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                response = readStream(in);
                publishProgress(response,address);
                urlConnection.disconnect();

            }
            catch (IOException e){e.printStackTrace();}

            return null;
        }






        @Override
        protected void onProgressUpdate(String... param) {
            super.onProgressUpdate(param);
            try {
                if(!param[0].equals("")) {
                    JSONObject jsonObject = new JSONObject(param[0]);
                    textOutTemp.setText("Внешняя температура: " + jsonObject.getDouble("outTemp") + " " + "\u00B0" + "C");
                    textInTemp.setText("Внутренняя температура: " + jsonObject.getDouble("inTemp") + " " + "\u00B0" + "C");
                    textInHum.setText("Внутренняя влажность: " + jsonObject.getDouble("inHum") + " %");
                    if (jsonObject.has("inPres")) {
                        textPres.setText("Давление: " + jsonObject.getDouble("inPres") + " hPa");
                    }

                }
                textDeviceIP.setText(param[1]);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);

        }
        private String readStream(InputStream is) {
            try {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                int i = is.read();
                while(i != -1) {
                    bo.write(i);
                    i = is.read();
                }
                is.close();
                return bo.toString();
            } catch (IOException e) {
            return "";
            }
        }

    }
    private class ResolveTask extends AsyncTask<String,String,Void>{
        InetAddress resolvedAddress;
        @Override
        protected Void doInBackground(String... params) {
            try {
                clientSocket = new MulticastSocket(PORT);
                group = InetAddress.getByName("224.0.0.251");
                clientSocket.setBroadcast(true);
                clientSocket.joinGroup(group);
                send(params[0]);
                if(!receive(params[0])){
                    send(params[0]);
                    receive(params[0]);
                };
                clientSocket.close();
            }
            catch (IOException e){
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            textDeviceIP.setText(values[0]);

            super.onProgressUpdate(values);
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
        }
        private void send(String devName) throws IOException {


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


            writeName(devName+"._http._tcp.local",message);
            //type
            message.write(0);
            message.write(33);
            //class
            message.write(0);
            message.write(1);
            Log.d("SHAQ", "send "+message.toString());
            byte[] result = message.toByteArray();
            try {
                message.close();
            } catch (IOException exception) {}

            DatagramPacket sendPacket = new DatagramPacket(result, result.length, InetAddress.getByName("224.0.0.251"), PORT);
            clientSocket.send(sendPacket);
        }
        private boolean receive (String devName) {
            while (!clientSocket.isClosed()) {
                try {
                    byte[] buf = new byte[1024];
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    clientSocket.setSoTimeout(1000);
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
                        //Log.d("SHAQ", readRequest(in));
                        if (!readRequest(in).equals(devName+"._http._tcp.local")){
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
                            readUnsignedShort(in);//port
                            readRequest(in);
                            readRequest(in);
                        }
                        else if (ansType ==1) {
                            byte[] inetaddr = new byte[4];
                            inetaddr[0] = (byte)readUnsignedByte(in);
                            inetaddr[1] = (byte)readUnsignedByte(in);
                            inetaddr[2] = (byte)readUnsignedByte(in);
                            inetaddr[3] = (byte)readUnsignedByte(in);
                            resolvedAddress = InetAddress.getByAddress(inetaddr);
                            if (getTempTask!=null){
                                while (getTempTask.getStatus()==Status.RUNNING){
                                    getTempTask.cancel(true);
                                }
                            }
                            getTempTask = new GetTempTask();
                            getTempTask.execute(resolvedAddress);
                            publishProgress(resolvedAddress.getHostAddress());
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

                    publishProgress(getString(R.string.deviceNotFound));
                    return false;
                }
            }
            publishProgress(getString(R.string.deviceNotFound));
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

        String readRequest(ByteArrayInputStream data){
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

    }

    private class MyGraphDrawable extends Drawable {
        private Paint mPaintScale = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Path mPathScale = new Path();
        HashMap <String,MyGraphic> graphicHashMap = new HashMap<>();



        private MyGraphDrawable(){
            super();
            if (hum){
                graphicHashMap.put("insideHumidity",new MyGraphic(Color.RED,graphicHashMap.size()));
            }
            if (pres){
                graphicHashMap.put("insidePressure",new MyGraphic(Color.YELLOW,graphicHashMap.size()));
            }
            if (inTemp){
                graphicHashMap.put("insideTemp",new MyGraphic(Color.MAGENTA,graphicHashMap.size()));
            }
            if (outTemp){
                graphicHashMap.put("outsideTemp",new MyGraphic(Color.WHITE,graphicHashMap.size()));
            }

        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            canvas.drawPath(mPathScale,mPaintScale);
            for (Map.Entry<String,MyGraphic> entry:graphicHashMap.entrySet()){
                entry.getValue().draw(canvas);
            }
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public void setAlpha(int alpha) {
            for (Map.Entry<String,MyGraphic> entry:graphicHashMap.entrySet()){
                entry.getValue().paint.setAlpha(alpha);
            }
            mPaintScale.setAlpha(alpha);
        }


        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            for (Map.Entry<String,MyGraphic> entry:graphicHashMap.entrySet()){
                entry.getValue().paint.setColorFilter(colorFilter);
            }
            mPaintScale.setColorFilter(colorFilter);
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            int boundx = 150;
            super.onBoundsChange(bounds);
            if (timeArrayList ==null || timeArrayList.size() == 0){
                return;
            }
            int width = bounds.width();
            int height = bounds.height();
            mPaintScale.setColor(Color.GREEN);
            mPaintScale.setStyle(Paint.Style.STROKE);
            mPaintScale.setStrokeWidth(10);
            mPathScale.reset();
            mPathScale.moveTo(0+boundx, 0);
            mPathScale.lineTo(width ,0 );
            mPathScale.lineTo(width ,height );
            mPathScale.moveTo(0+boundx, 0);
            mPathScale.lineTo(0+boundx, height);
            mPathScale.lineTo(width, height);

            for (Map.Entry<String,MyGraphic> entry:graphicHashMap.entrySet()){
                entry.getValue().init(entry.getKey(),width,height);
            }

        }

    }

    private class MyGraphic {
        private int index;
        private Path path;
        private Paint paint;
        private double minValue;
        private double maxValue;
        private int height;
        private int width;
        public MyGraphic(int color,int ind){
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(10);
            paint.setTextSize(35.0f);
            path = new Path();
            index = ind;
        }
        public void init(String key,int mWidth, int mHeight){
            int boundx = 150;
            height = mHeight;
            width = mWidth;
            path.reset();
            minValue = 10000;
            maxValue = -100;
            for (Long time : timeArrayList) {
                HashMap<String, Double> tmpValue = dataArray.get(time);
                double value = tmpValue.get(key);
                if (value < minValue) {
                    minValue = value;
                }
                if (value > maxValue) {
                    maxValue = value;
                }
            }
            float stepX = (float) (width-boundx) / (timeArrayList.get(timeArrayList.size() - 1) - timeArrayList.get(0));
            float stepY = (height) / ((float) (maxValue - minValue));
            boolean firstPoint = true;
            for (Long time : timeArrayList) {
                if (firstPoint) {
                    path.moveTo((time - timeArrayList.get(0)) * stepX+boundx, height - (float) ((dataArray.get(time).get(key) - minValue) * stepY));
                    firstPoint = false;
                }
                path.lineTo((time - timeArrayList.get(0)) * stepX+boundx, height - (float) ((dataArray.get(time).get(key) - minValue) * stepY));
            }
        }
        public void draw(Canvas canvas){
            paint.setStrokeWidth(10);
            canvas.drawPath(path,paint);
            paint.setStrokeWidth(2);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText(Double.toString(new BigDecimal(maxValue).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue()),0,(index+1)*paint.getTextSize(),paint);
            canvas.drawText(Double.toString(new BigDecimal(minValue).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue()),0,height-paint.getTextSize()*(4-index),paint);
        }
    }


}

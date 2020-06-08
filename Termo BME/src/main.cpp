
#include <EEPROM.h>
#include <ESP8266WiFi.h>
#include <WiFiClient.h>
#include <ESP8266WebServer.h>
#include <OneWire.h>
#include <DallasTemperature.h>
#include <ESP8266mDNS.h>
#include <Wire.h>
#include <Adafruit_BME280.h>
#include <FirebaseESP8266.h>
#include <NTPClient.h>
#include "db.h"

// Data wire is plugged into port 2 on the Arduino
#define ONE_WIRE_BUS 4 //D2
#define TEMPERATURE_PRECISION 9 // Lower resolution

#define mSCL 14 //D5
//#define DHTTYPE DHT22   // DHT 22  (AM2302), AM2321
#define RESETPIN 5 //D1
#define mSDA 12 //D6

#include <WiFiUdp.h>

FirebaseData firebaseData;

Adafruit_BME280 bme; // I2C
TwoWire twoWire;
// Setup a oneWire instance to communicate with any OneWire devices (not just Maxim/Dallas temperature ICs)
OneWire oneWire(ONE_WIRE_BUS);
// Pass our oneWire reference to Dallas Temperature.
DallasTemperature sensors(&oneWire);

const char *ap_ssid = "ESPap";

int ap;
ESP8266WebServer server(80);
String mSsid;
String mPassword;
double insideTemp;
double outsideTemp;
double insideHumidity;
double insidePressure;
int status = WL_IDLE_STATUS;

int loopCounter=0;
int timeCounter=0;
int dataCounter=0;

WiFiUDP ntpUDP;
NTPClient timeClient(ntpUDP, "europe.pool.ntp.org", 36000, 60000);

void handleTempData() {
  char in_temp[8];
  char out_temp[8];
  char in_pres[8];
  char in_hum[8];
  char temp[100];

  dtostrf(outsideTemp, 7, 2, out_temp);
  dtostrf(insideHumidity, 7, 2, in_hum);
  dtostrf(insidePressure, 7, 2, in_pres);
  dtostrf(insideTemp, 7, 2, in_temp);

  snprintf ( temp, 100,
  "{\"outTemp\":%s,\"inTemp\":%s,\"inHum\":%s,\"inPres\":%s}",out_temp,in_temp,in_hum,in_pres);

  server.send ( 200, "text/html", temp );

  Serial.print("Refresh data: ");
  Serial.println(temp);

}

void handleRootTemp() {
  char temp[2000];

  snprintf ( temp, 2000,
             "<html> \
  <head>\
    <meta http-equiv=\"Content-Type\" content=\"text/html; Charset=UTF-8\">\
    <title>ESP8266 Termo</title>\
    <style>\
      body { background-color: #cccccc; font-family: Arial, Helvetica, Sans-Serif; Color: #000088; }\
    </style>\
    <script type=\"text/javascript\">\
window.onload = function(){\
setTimeout(\"startAjax();\", 1000);\
setInterval(\"startAjax();\", 10000);\
};\
function startAjax(){\
  var request;\
  if(window.XMLHttpRequest){\
      request = new XMLHttpRequest();\
  } else if(window.ActiveXObject){\
      request = new ActiveXObject(\"Microsoft.XMLHTTP\"); \
  } else {\
      return;\
  };\
   request.onreadystatechange = function(){\
        if(request.readyState==4){\
           if(request.status==200){\
              var tempData = JSON.parse(request.responseText);\
              document.getElementById(\"outTemp\").innerHTML = \"<b>\"+tempData.outTemp+\"</b>\";\
              document.getElementById(\"inTemp\").innerHTML = \"<b>\"+tempData.inTemp+\"</b>\";\
              document.getElementById(\"inHum\").innerHTML = \"<b>\"+tempData.inHum+\"</b>\";\
              document.getElementById(\"inPres\").innerHTML = \"<b>\"+tempData.inPres+\"</b>\";\
            };\
       };\
    };\
    request.open ('GET', \"tempData\", true);\
    request.send ('');\
  };\
</script>\
  </head>\
  <body>\
    <h1>Current status</h1>\
  <p>Outside temp: <span id=\"outTemp\"></span> *C</p>\
  <p>Inside temp: <span id=\"inTemp\"></span> *C</p>\
  <p>Inside humidity: <span id=\"inHum\"></span> %</p>\
  <p>Inside presure: <span id=\"inPres\"></span> hPa</p>\
  <br>\
  <br>\
  <input type=\"button\" value = \"Refresh\" onclick = \"startAjax()\"</>\
  <form action=\"/Settings\">\
    <label>Password: <br> \
    <input type=\"text\" name=\"password\" value = \"\" required size=\"20\" maxlength=\"20\">\
    </label>\
    <input type=\"submit\" value = \"Settings\"</>\
  </form>\
  </body>\
</html>");
  server.send ( 200, "text/html", temp );


}

void handleRootSetup() {
  char temp[700];
  char buf1[20];
  char buf2[20];

  if (digitalRead(RESETPIN) != 0 && ap == 1){
    if (server.args() == 0) {
      handleRootTemp();
      return;
    }
    else if (server.args() > 0 && server.arg(0) != mPassword){
      handleRootTemp();
      return;
      }
  }

  mSsid.toCharArray(buf1, mSsid.length()+1);
  mPassword.toCharArray(buf2, mPassword.length()+1);
  snprintf ( temp, 700,

             "<html>\
  <head>\
    <title>ESP8266 Termo</title>\
    <style>\
      body { background-color: #cccccc; font-family: Arial, Helvetica, Sans-Serif; Color: #000088; }\
    </style>\
  </head>\
  <body>\
    <h1>Input AP name and key</h1>\
 <br>\
  <form action = \"/SaveSettings\">\
    <label>AP name: <br>\
    <input type=\"text\" name=\"ssid\" value = \"%s\" required size=\"20\" maxlength=\"20\">\
    </label>\
  <br>\
    <label>Password: <br> \
    <input type=\"text\" name=\"password\" value = \"%s\" required size=\"20\" maxlength=\"20\">\
    </label>\
    <input type=\"hidden\" name=\"options\" value = \"fromsettings\">\
  <br>\
  <p><input type=\"submit\" value=\"Save\"></p>\
  </form>\
  </body>\
</html>",buf1,buf2
           );
  server.send ( 200, "text/html", temp );
  Serial.println("Web interface setup page");
}

void handleSaveSettings() {
  char temp[700];
  byte buf[25];
  unsigned int i;
  if (server.args() == 0) {
    handleRootSetup();
    return;
  };
  mSsid = server.arg(0);
  mPassword = server.arg(1);
  if (server.arg(2)!="fromsettings"){
    return;}
  EEPROM.begin(50);
  EEPROM.write(0, 1);
  EEPROM.write(1, mSsid.length());
  mSsid.getBytes(buf,mSsid.length()+1);
  for (i=0;i<mSsid.length();i++){
    EEPROM.write(i+2, buf[i]);
  };

  EEPROM.write(30, mPassword.length());
  mPassword.getBytes(buf,mPassword.length()+1);
  for (i=0;i<mPassword.length();i++){
    EEPROM.write(i+31, buf[i]);
  };

  EEPROM.commit();

  Serial.print("Ssid: ");
  Serial.println(mSsid);

  Serial.print("password: ");
  Serial.println(mPassword);


  snprintf ( temp, 700,
             "<html>\
  <head>\
    <title>ESP8266 Termo</title>\
    <style>\
      body { background-color: #cccccc; font-family: Arial, Helvetica, Sans-Serif; Color: #000088; }\
    </style>\
  </head>\
  <body>\
    <h1>Settings saved</h1>\
  </body>\
</html>"
           );
  server.send ( 200, "text/html", temp );
  Serial.println("Settings saved");
}




void connectToWiFi() {
  Serial.println("Attempting to connect to WiFi");
    // Connect to WPA/WPA2 network:
  //mSsid = "skynet";
  //mPassword = "pfghtnyfzpjyf";
  //const char* m_Ssid     = "skynet";
  //const char* m_Password = "pfghtnyfzpjyf";
  //WiFi.begin(m_Ssid, m_Password);
  WiFi.begin(mSsid.c_str(), mPassword.c_str());
  while (WiFi.status() != WL_CONNECTED) {
    Serial.println("Can not connect to WiFi!!!");
    Serial.println(mSsid);
    Serial.println(mPassword);

    // wait 10 seconds for connection:
    delay(500);
  }
  Serial.println("OK!!!");
  Serial.println("IP address: ");
  Serial.println(WiFi.localIP());
//  if (WiFi.getAutoConnect() != true){
//    WiFi.setAutoConnect(true);  //on power-on automatically connects to last used hwAP
    WiFi.setAutoReconnect(true);
//  }

}

bool getCurrentDataCounter(){
  if (dataCounter == 0) {
    if (Firebase.getInt(firebaseData, "/dataCounter")){
      dataCounter = firebaseData.intData();
      return true;
    }
    else if (firebaseData.httpCode()== FIREBASE_ERROR_PATH_NOT_EXIST){
      dataCounter = 1;
      return true;
    }
    else {
      return false;
    }
  }
  return true;
}


void sendToFirebase(){
  FirebaseJson json;
  timeClient.update();
  json.add("outsideTemp", outsideTemp);
  json.add("insideTemp", insideTemp);
  json.add("insideHumidity", insideHumidity);
  json.add("insidePressure", insidePressure);
  json.add("timeStamp", (int) timeClient.getEpochTime());
  int tmpDataCounter = dataCounter+1;
  if (tmpDataCounter>150){
    tmpDataCounter = 1;
  }
  if (Firebase.setJSON(firebaseData, "/"+String(tmpDataCounter), json)){
    if (Firebase.setInt(firebaseData, "/dataCounter",tmpDataCounter)){
      dataCounter=tmpDataCounter;
    }
  }

}



void setup() {
  pinMode(RESETPIN,INPUT_PULLUP);

  delay(100);
  Serial.begin(74880);
  delay(100);
  Serial.println();
  EEPROM.begin(15);
  ap = EEPROM.read(0);
/*  ap = 0;*/
  if (digitalRead(RESETPIN) == 0 || ap != 1) { /*start ap*/
    Serial.println("No AP settings. Starting AP");
    IPAddress ip(192, 168, 0, 1);
    IPAddress mask(255, 255, 255, 0);

    WiFi.config(ip, ip, mask);
    WiFi.softAP(ap_ssid);

    IPAddress myIP = WiFi.softAPIP();
    Serial.print("AP IP address: ");
    Serial.println(myIP);

    server.on("/", handleRootSetup);
    server.on("/SaveSettings", handleSaveSettings);
    server.begin();
    Serial.println("HTTP server started");

  }
  else {
    WiFi.mode(WIFI_STA);
    EEPROM.begin(50);
    mSsid = "";
    mPassword = "";
    byte i=0;
    byte ssid_length = EEPROM.read(1);
    byte password_length = EEPROM.read(30);
    for (i=0;i<ssid_length;i++){
      mSsid += char(EEPROM.read(i+2));
    }
    for (i=0;i<password_length;i++){
      mPassword += char(EEPROM.read(i+31));
    }
    //mSsid = "Shaq";
    Serial.println("AP settings found. Connecting");
    Serial.print("Ssid: ");
    Serial.println(mSsid);

    Serial.print("password: ");
    Serial.println(mPassword);
    WiFi.setOutputPower(25);
    connectToWiFi();
    server.on("/", handleRootTemp);
    server.on("/Settings", handleRootSetup);
    server.on("/SaveSettings", handleSaveSettings);
    server.on("/tempData", handleTempData);

    server.begin();
    Serial.println("HTTP server started");

  };
  if (!MDNS.begin("esp8266")) {
    Serial.println("Error setting up MDNS responder!");
  }
  else {
    Serial.println("mDNS responder started");
  }
  // Add service to MDNS-SD
  MDNS.addService("http", "tcp", 80);
  Serial.println("Start DS18B20");
  sensors.begin();
  twoWire.begin(mSDA,mSCL);
  if (!bme.begin(119,&twoWire))
  {
    Serial.print("No BME280 detected ... Check your wiring or I2C ADDR!");
  }
  else {
    Serial.println("BME280 ready.");
    bme.setSampling(Adafruit_BME280::MODE_FORCED,
                    Adafruit_BME280::SAMPLING_X1, // temperature
                    Adafruit_BME280::SAMPLING_X1, // pressure
                    Adafruit_BME280::SAMPLING_X1, // humidity
                    Adafruit_BME280::FILTER_OFF);//,
//                    Adafruit_BME280::STANDBY_MS_500   );
  }
  Serial.println("Sensors started");
  timeClient.begin();
  timeClient.update();
  Serial.println(timeClient.getFormattedTime());
  Firebase.begin(FIREBASE_HOST, FIREBASE_AUTH);
//  Firebase.setInt(firebaseData, "/"+timeClient.getFormattedTime(),150);
  loopCounter = 0;
  timeCounter = 58;
  dataCounter = 0;

}

void loop() {

    server.handleClient();
    delay(100);
    loopCounter+=1;
    if (loopCounter<100){
      return;
    }
    loopCounter = 0;
    timeCounter+=1;
    //Serial.println(ESP.getFreeHeap());
    if (timeCounter<59){
      return;
    }
    timeCounter = 0;
    sensors.requestTemperatures(); // Send the command to get temperatures
    outsideTemp = sensors.getTempCByIndex(0);
    bme.takeForcedMeasurement();

    insideTemp = bme.readTemperature();
    insideHumidity = bme.readHumidity();
    insidePressure = bme.readPressure()/100.0F;
    if (getCurrentDataCounter()){
      sendToFirebase();
    }


}

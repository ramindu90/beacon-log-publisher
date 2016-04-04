package org.wso2.united.beaconlogpublisher;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Environment;
import android.os.RemoteException;
import android.provider.Settings;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends ActionBarActivity implements BeaconConsumer, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {
    protected static final String TAG = "MonitoringActivity";
    private BeaconManager beaconManager;
    private Queue<BeaconDataRecord> queue;
    private LocationManager locationManager;
    private ScheduledExecutorService beaconPublisherScheduler;

    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 1000;
    private Location mLastLocation;
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    private boolean mRequestingLocationUpdates = false;
    private String deviceId;

    private static int UPDATE_INTERVAL = 10000;
    private static int FASTEST_INTERVAL = 5000;
    private static int DISPLACEMENT = 10;

    TextView usernameText;
    TextView user_roleText;
    Button startButton;
    Button endButton;
    Button saveUserDetailButton;
    Spinner airportCodeSpinner;

    String username = "test";;
    String user_role = "cleaning";

    final  Context context = this;


    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    //todo for gps logs
    String airportCode = "ORD";
    private String eventType = "ENTER";
    private double fenceAccuracy = 0.0d;
    private int fenceAltitude = 0;
    private double fenceBearing = 0.0d;
    private double fenceSpeed = 0.0d;
    private String fenceIdentifier = "";
    private String name = "";


    boolean receiverStarted = false;
   // boolean isStopped=true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);
            initUIFields();

            airportCodeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                 @Override
                 public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                     airportCode = airportCodeSpinner.getSelectedItem().toString();
                     TextView selectedText = (TextView) parent.getChildAt(0);
                     if (selectedText != null) {
                         selectedText.setTextColor(Color.parseColor("#80D8FF"));
                     }
                 }

                 @Override
                 public void onNothingSelected(AdapterView<?> parent) {

                 }
             });
            queue = new ConcurrentLinkedQueue<BeaconDataRecord>();
            deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);

            // location
            locationManager = (LocationManager) context.getSystemService(LOCATION_SERVICE);
            checkPlayServices();
            buildGoogleApiClient();
            createLocationRequest();
            mRequestingLocationUpdates = true;

        } catch (Throwable e) {
            Log.e("ERROR On create", e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    /**
     * Start collecting location and beacon data
     * @param view
     */
    public void startCollectingData(View view) {

        if(!"Select airport".equals(airportCodeSpinner.getSelectedItem().toString())){
            endButton.setEnabled(true);
            startButton.setEnabled(false);
            saveUserDetailButton.setEnabled(false);

            // beacon data
            beaconManager = BeaconManager.getInstanceForApplication(this);
            beaconManager.getBeaconParsers().add(new BeaconParser().
                    setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25"));
            beaconManager.getBeaconParsers().add(new BeaconParser().
                    setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));
            beaconManager.getBeaconParsers().add(new BeaconParser()
                    .setBeaconLayout("m:2-3=beac,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25"));
            beaconManager.getBeaconParsers().add(new BeaconParser()
                    .setBeaconLayout("s:0-1=feaa,m:2-2=00,p:3-3:-41,i:4-13,i:14-19"));

            beaconManager.bind(this);

            // write to file - every 5 seconds
            if(!receiverStarted){
                receiverStarted = true;
                beaconPublisherScheduler = Executors.newSingleThreadScheduledExecutor();
                beaconPublisherScheduler.scheduleWithFixedDelay(new Runnable() {
                    @Override
                    public void run() {
                        try {
                        publishBeaconData();
//                            publishSeperateBeaconData();
                        } catch (Throwable e) {
                            Log.e("Error : log beacon data", e.getMessage());
                        }
                    }
                }, 0, 15, TimeUnit.SECONDS);
            }
        } else {
            AlertDialog.Builder dlgAlert  = new AlertDialog.Builder(context);
            dlgAlert.setMessage("Please select an airport before starting process");
            dlgAlert.setTitle("Cannot start process");
            dlgAlert.setPositiveButton("OK", null);
            dlgAlert.setCancelable(true);
            dlgAlert.create().show();
        }
    }

    /**
     * Stop collecting location and beacon data
     * @param view
     */
    public void stopCollectingData(View view){
        beaconManager.unbind(this);
        beaconPublisherScheduler.shutdown();
        startButton.setEnabled(false);
        endButton.setEnabled(false);
        saveUserDetailButton.setEnabled(true);
        usernameText.setEnabled(true);
        user_roleText.setEnabled(true);
        receiverStarted = false;
        airportCodeSpinner.setEnabled(true);
    }

    /**
     * Initialize the UI fields
     */
    private void initUIFields() {
        usernameText = (TextView)findViewById(R.id.username);
        user_roleText = (TextView)findViewById(R.id.user_role);

        startButton = (Button)findViewById(R.id.startButton);
        endButton = (Button)findViewById(R.id.endButton);
        saveUserDetailButton = (Button)findViewById(R.id.save_user_detail);

        airportCodeSpinner = (Spinner)findViewById(R.id.airportCodeSpinner);
        ArrayAdapter<String> adapter;
        List<String> list;

        list = new ArrayList<String>();
        list.add("Select airport");
        list.add("ORD");
        list.add("EWR");
        adapter = new ArrayAdapter<String>(getApplicationContext(),
                android.R.layout.simple_spinner_item, list);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        airportCodeSpinner.setAdapter(adapter);

        endButton.setEnabled(false);
        startButton.setEnabled(false);
    }

    public void saveUserDetails(View view) {
        username = usernameText.getText().toString();
        user_role = user_roleText.getText().toString();

        startButton.setEnabled(true);
        endButton.setEnabled(false);
        saveUserDetailButton.setEnabled(false);

        usernameText.setEnabled(false);
        user_roleText.setEnabled(false);
        airportCodeSpinner.setEnabled(false);
    }

    @Override
    public void onBeaconServiceConnect() {
        try {
            beaconManager.setBackgroundScanPeriod(1000l);
            beaconManager.setBackgroundBetweenScanPeriod(1000l);
            beaconManager.updateScanPeriods();
        } catch (RemoteException e) {
            Log.e("Error : update scan", e.getMessage());
        }
        beaconManager.setRangeNotifier(new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
                try {
                    if (beacons.size() > 0) {
                        Log.i(TAG, "Reading from beacon");
                        Iterator<Beacon> beaconIterator = beacons.iterator();
                        while (beaconIterator.hasNext()) {
                            Beacon beacon = beaconIterator.next();
                            BeaconDataRecord beaconDataRecord = new BeaconDataRecord();
                            beaconDataRecord.setUuid(String.valueOf(beacon.getId1()));
                            beaconDataRecord.setMajor(String.valueOf(beacon.getId2()));
                            beaconDataRecord.setMinor(String.valueOf(beacon.getId3()));
                            beaconDataRecord.setDistance(beacon.getDistance());
                            beaconDataRecord.setRssi(beacon.getRssi());
                            beaconDataRecord.setBeaconType(String.valueOf(beacon.getBluetoothName()));
                            beaconDataRecord.setName(String.valueOf(beacon.getBluetoothName()));
                            beaconDataRecord.setTimestamp(System.currentTimeMillis());
                            //todo: check whats being recorded here
                            beaconDataRecord.setAllData(beacon.toString());
                            queue.add(beaconDataRecord);
                        }
                    }
                } catch (Throwable e) {
                    Log.e("Unexpected error", e.getMessage());
                }
            }
        });

        try {
            beaconManager.startRangingBeaconsInRegion(new Region("myRangingUniqueId", null, null, null));
        } catch (RemoteException e) {
            Log.e("Error : beacon ranging", e.getMessage());
        }
    }

    /**
     * Publish the received beacon data to the local file
     */
    private void publishBeaconData() {
        try {
            createLocationRequest();
            showLocation();

            JSONObject mainObject = new JSONObject();
            mainObject.put("accessCode", "AccessCode");

            JSONObject application = new JSONObject();
            application.put("id", 1);
            application.put("isProduction", false);
            application.put("name", "");

            JSONObject version = new JSONObject();
            version.put("build", "");
            version.put("displayText", "2.0.1");
            version.put("major", "");
            version.put("minor", "");

            application.put("version", version);
            mainObject.put("application", application);
            mainObject.put("deviceId", deviceId);
            mainObject.put("languageCode", "en-US");
            mainObject.put("transactionId", "0804c882-ff9d-4a2f-a1e9-b4068808af97");

            JSONArray results = new JSONArray();

            JSONObject result = new JSONObject();
            BeaconDataRecord record = queue.poll();
            if (record != null) {
                result.put("airportCode", record.getAirportCode());
                result.put("beaconDistance", record.getDistance());
                result.put("beaconMajor", record.getMajor());
                result.put("beaconMinor", record.getMinor());
                result.put("beaconRssi", record.getRssi());
                result.put("beaconUuid", record.getUuid());
                result.put("deviceId", deviceId);
                result.put("eventType", record.getEventType());
                result.put("fenceAccuracy", record.getFenceAccuracy());
                result.put("fenceAltitude", record.getFenceAltitude());
                result.put("fenceBearing", record.getFenceBearing());
                result.put("fenceIdentifier", record.getFenceIdentifier());
                result.put("fenceLatitude", record.getFenceLatitude());
                result.put("fenceLongitude", record.getFenceLongitude());
                result.put("fenceSpeed", record.getFenceSpeed());
                result.put("name", record.getName());
                result.put("timestamp", record.getTimestamp());
                results.put(result);

                while (!queue.isEmpty()) {
                    result = new JSONObject();
                    BeaconDataRecord dataRecord = queue.poll();
                    result.put("airportCode", dataRecord.getAirportCode());
                    result.put("beaconDistance", dataRecord.getDistance());
                    result.put("beaconMajor", dataRecord.getMajor());
                    result.put("beaconMinor", dataRecord.getMinor());
                    result.put("beaconRssi", dataRecord.getRssi());
                    result.put("beaconUuid", dataRecord.getUuid());
                    result.put("deviceId", deviceId);
                    result.put("eventType", dataRecord.getEventType());
                    result.put("fenceAccuracy", dataRecord.getFenceAccuracy());
                    result.put("fenceAltitude", dataRecord.getFenceAltitude());
                    result.put("fenceBearing", dataRecord.getFenceBearing());
                    result.put("fenceIdentifier", dataRecord.getFenceIdentifier());
                    result.put("fenceLatitude", dataRecord.getFenceLatitude());
                    result.put("fenceLongitude", dataRecord.getFenceLongitude());
                    result.put("fenceSpeed", dataRecord.getFenceSpeed());
                    result.put("name", dataRecord.getName());
                    result.put("timestamp", dataRecord.getTimestamp());
                    results.put(result);
                }
            //sending gps log if  no beacon logs aren't retrieved
            } else {
                if (mLastLocation != null) {
                    Double latitude = mLastLocation.getLatitude();
                    Double longitude = mLastLocation.getLongitude();
                    result = new JSONObject();
                    result.put("airportCode", airportCode);
                    result.put("beaconDistance", 0.0);
                    result.put("beaconMajor", 0);
                    result.put("beaconMinor", 0);
                    result.put("beaconRssi", 0);
                    result.put("beaconUuid", "");
                    result.put("deviceId", deviceId);
                    result.put("eventType", eventType);
                    result.put("fenceAccuracy", fenceAccuracy);
                    result.put("fenceAltitude", fenceAltitude);
                    result.put("fenceBearing", fenceBearing);
                    result.put("fenceIdentifier", fenceIdentifier);
                    result.put("fenceLatitude", latitude);
                    result.put("fenceLongitude", longitude);
                    result.put("fenceSpeed", fenceSpeed);
                    mainObject.put("name", name);
                    result.put("timestamp", simpleDateFormat.format(System.currentTimeMillis()));
                    results.put(result);
                }
            }
            mainObject.put("results", results);
            publishOrSaveData(mainObject);
        } catch (Exception e) {
            Log.e("Error : writing logs", e.getMessage());
        }
    }

    /**
     * Publish the received beacon data to the local file
     */
    private void publishSeperateBeaconData() {

        try {
            createLocationRequest();
            showLocation();

            JSONObject mainObject = new JSONObject();
            mainObject.put("accessCode", "AccessCode");

            JSONObject application = new JSONObject();
            application.put("id", 1);
            application.put("isProduction", false);
            application.put("name", "");

            JSONObject version = new JSONObject();
            version.put("build", "");
            version.put("displayText", "2.0.1");
            version.put("major", "");
            version.put("minor", "");

            application.put("version", version);
            mainObject.put("application", application);
            mainObject.put("deviceId", deviceId);
            mainObject.put("languageCode", "en-US");
            mainObject.put("transactionId", "0804c882-ff9d-4a2f-a1e9-b4068808af97");

//            JSONArray results = new JSONArray();

//            JSONObject result = new JSONObject();
            BeaconDataRecord record = queue.poll();
            if (record != null) {
                mainObject.put("airportCode", record.getAirportCode());
                mainObject.put("beaconDistance", record.getDistance());
                mainObject.put("beaconMajor", record.getMajor());
                mainObject.put("beaconMinor", record.getMinor());
                mainObject.put("beaconRssi", record.getRssi());
                mainObject.put("beaconUuid", record.getUuid());
                mainObject.put("deviceId", deviceId);
                mainObject.put("eventType", record.getEventType());
                mainObject.put("fenceAccuracy", record.getFenceAccuracy());
                mainObject.put("fenceAltitude", record.getFenceAltitude());
                mainObject.put("fenceBearing", record.getFenceBearing());
                mainObject.put("fenceLatitude", record.getFenceLatitude());
                mainObject.put("fenceLongitude", record.getFenceLongitude());
                mainObject.put("fenceSpeed", record.getFenceSpeed());
                mainObject.put("name", record.getName());
                mainObject.put("fenceIdentifier", record.getFenceIdentifier());
                mainObject.put("timestamp", record.getTimestamp());
                publishOrSaveData(mainObject);
                while (!queue.isEmpty()) {
                    BeaconDataRecord dataRecord = queue.poll();
                    mainObject.put("airportCode", dataRecord.getAirportCode());
                    mainObject.put("beaconDistance", dataRecord.getDistance());
                    mainObject.put("beaconMajor", dataRecord.getMajor());
                    mainObject.put("beaconMinor", dataRecord.getMinor());
                    mainObject.put("beaconRssi", dataRecord.getRssi());
                    mainObject.put("beaconUuid", dataRecord.getUuid());
                    mainObject.put("deviceId", deviceId);
                    mainObject.put("eventType", dataRecord.getEventType());
                    mainObject.put("fenceAccuracy", dataRecord.getFenceAccuracy());
                    mainObject.put("fenceAltitude", dataRecord.getFenceAltitude());
                    mainObject.put("fenceBearing", dataRecord.getFenceBearing());
                    mainObject.put("fenceIdentifier", dataRecord.getFenceIdentifier());
                    mainObject.put("fenceLatitude", dataRecord.getFenceLatitude());
                    mainObject.put("fenceLongitude", dataRecord.getFenceLongitude());
                    mainObject.put("fenceSpeed", dataRecord.getFenceSpeed());
                    mainObject.put("name", dataRecord.getName());
                    mainObject.put("timestamp", dataRecord.getTimestamp());
                    publishOrSaveData(mainObject);
                }
            } else {
                if (mLastLocation != null) {
                    Double latitude = mLastLocation.getLatitude();
                    Double longitude = mLastLocation.getLongitude();
                    mainObject.put("airportCode", airportCode);
                    mainObject.put("beaconDistance", 0.0);
                    mainObject.put("beaconMajor", 0);
                    mainObject.put("beaconMinor", 0);
                    mainObject.put("beaconRssi", 0);
                    mainObject.put("beaconUuid", "");
                    mainObject.put("deviceId", deviceId);
                    mainObject.put("eventType", eventType);
                    mainObject.put("fenceAccuracy", fenceAccuracy);
                    mainObject.put("fenceAltitude", fenceAltitude);
                    mainObject.put("fenceBearing", fenceBearing);
                    mainObject.put("fenceIdentifier", fenceIdentifier);
                    mainObject.put("fenceLatitude", latitude);
                    mainObject.put("fenceLongitude", longitude);
                    mainObject.put("fenceSpeed", fenceSpeed);
                    mainObject.put("name", name);
                    mainObject.put("timestamp", simpleDateFormat.format(System.currentTimeMillis()));
                    publishOrSaveData(mainObject);
                }
            }
        } catch (Exception e) {
            Log.e("Error : writing logs", e.getMessage());
        }
    }

    private void publishOrSaveData(JSONObject mainObject){
        BufferedWriter buf;
        String fileName = "beaconlog-"+ deviceId + ".log";
        try{
            if(!isOnline()){
                File logFile = new File(Environment.getExternalStorageDirectory(), fileName);
                if (!logFile.exists()) {
                    logFile.createNewFile();
                    final String logFileName = logFile.getAbsolutePath();
                    this.runOnUiThread(new Runnable() {
                        public void run() {
                            Toast toast = Toast.makeText(context, "Data logged since no connectivity: " + logFileName, Toast.LENGTH_LONG);
                            toast.getView().setBackgroundColor(Color.parseColor("#FFA726"));
                            toast.show();
                        }
                    });

                }
                buf = new BufferedWriter(new FileWriter(logFile, true));
                buf.append(mainObject.toString());
                buf.newLine();
                buf.flush();
                buf.close();
            } else {
                File logFile = new File(Environment.getExternalStorageDirectory(), fileName);
                if (logFile.exists()) {
                    InputStream ist = new FileInputStream(logFile.getAbsolutePath());
                    BufferedReader in = new BufferedReader(new InputStreamReader(ist));
                    StringBuilder response = new StringBuilder();
                    int line;
                    int jsonObj = 0;
                    int array = 0;
                    while ((line = in.read()) != -1) {
                        if ('[' == (char) line) {
                            array++;
                        }
                        if (']' == (char) line) {
                            array--;
                        }
                        if ('{' == (char) line) {
                            jsonObj++;
                        }
                        if ('}' == (char) line) {
                            jsonObj--;
                            if (jsonObj == 0) {
                                response.append((char) line);
                            }
                        }
                        if (']' != (char) line && '[' != (char) line) {
                            if (jsonObj != 0) {
                                response.append((char) line);
                            }
                        }
                        if (jsonObj == 0 && !"".equalsIgnoreCase(response.toString())) {
                            String message = response.toString();
                            response = new StringBuilder();
                            JSONObject jobj = new JSONObject(message);

                            DefaultHttpClient httpclient = new DefaultHttpClient();
//                            HttpPost httpost = new HttpPost("https://api.ual-mobile.com/LocationEvent/PublishAirportOpsLocationEvent");
                            HttpPost httpost = new HttpPost("http://192.168.1.3:9763/endpoints/deleteHttpReceiver");
                            StringEntity se = null;
                            se = new StringEntity(jobj.toString());
                            httpost.setEntity(se);
                            httpost.setHeader("Content-type", "application/json");
                            ResponseHandler<String> handler = new BasicResponseHandler();
                            HttpResponse response2 = httpclient.execute(httpost);
                        }
                    }
                    this.runOnUiThread(new Runnable() {
                        public void run() {
                            Toast toast =Toast.makeText(context, "previous data sent!", Toast.LENGTH_LONG);
                            toast.getView().setBackgroundColor(Color.parseColor("#FFF176"));
                            toast.show();
                        }
                    });
                    logFile.delete();
                }
                DefaultHttpClient httpclient = new DefaultHttpClient();
//                HttpPost httpost = new HttpPost("https://api.ual-mobile.com/LocationEvent/PublishAirportOpsLocationEvent");
                HttpPost httpost = new HttpPost("http://192.168.1.3:9763/endpoints/deleteHttpReceiver");
                StringEntity se = null;
                se = new StringEntity(mainObject.toString());
                httpost.setEntity(se);
                httpost.setHeader("Content-type", "application/json");
                ResponseHandler<String> handler = new BasicResponseHandler();
                HttpResponse response2 = httpclient.execute(httpost);
                this.runOnUiThread(new Runnable() {
                    public void run() {
                        Toast toast =Toast.makeText(context, "current data sent!", Toast.LENGTH_LONG);
                        toast.getView().setBackgroundColor(Color.parseColor("#388E3C"));
                        toast.show();
                    }
                });
            }
        }catch(Exception e){
            Log.e("Error : writing logs", e.getMessage());
        }
    }

    private boolean isOnline() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

    // Location
    private void showLocation() {
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (mLastLocation != null) {
            Double latitude = mLastLocation.getLatitude();
            Double longitude = mLastLocation.getLongitude();
            Log.d("location ", "Longitude : " + longitude + " , Latitude :" + latitude);
        } else {
            Log.d("ERROR :", "ERROR");
        }
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApiIfAvailable(LocationServices.API).build();
    }

    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, this, PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                Toast.makeText(getApplicationContext(), "This device is not supported.", Toast.LENGTH_LONG).show();
                finish();
            }
            return false;
        }
        return true;
    }

    @Override
    public void onConnected(Bundle bundle) {
        showLocation();
        startLocationUpdates();
    }

    @Override
    public void onConnectionSuspended(int i) {
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.i(TAG, "Connection failed : ConnectionResult.getErrorCode() = " + connectionResult.getErrorCode());
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mGoogleApiClient != null) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPlayServices();
    }

    /**
     * Create location request
     */
    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(UPDATE_INTERVAL);
        mLocationRequest.setFastestInterval(FASTEST_INTERVAL);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setSmallestDisplacement(DISPLACEMENT);
    }

    /**
     * Location api configurations
     */
    protected void startLocationUpdates() {
        Intent alarm = new Intent(MainActivity.this, MainActivity.class);
        PendingIntent recurringAlarm =
                PendingIntent.getBroadcast(context,
                        1,
                        alarm,
                        PendingIntent.FLAG_CANCEL_CURRENT);
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, recurringAlarm);
    }

    @Override
    public void onLocationChanged(Location location) {
        mLastLocation = location;
        showLocation();
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }
}

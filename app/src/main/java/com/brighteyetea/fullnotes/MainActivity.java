package com.brighteyetea.fullnotes;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity implements LocationListener {

    public static final String TAG = "MainActivity";

    //SQLite Database
    SQLiteDatabase myDB = null;

    //Location / GPS vars
    LocationManager lm;
    boolean drivingMonitorOn = true;
    TextView current_location_lat_lon;
    TextView current_location_speed;
    TextView current_location_course;
    TextView current_location_battery;
    int enableGPSTimesAsked = 0;
    double currentLat;
    double currentLon;
    int GPSdistanceFilterM = 1; // METERS
    //    int GPSupdateMinutes = 0;
    int GPSupdateSeconds = 5; //(60*GPSupdateMinutes)
    int GPSupdateTimeMs = 1000*GPSupdateSeconds; //MILLISECONDS //1000*60*1=60000 -> 1 MINUTE
    double currentSpeed;
    float currentBearing;
    float currentAccuracy;
    String currentProvider;
    double currentAltitude;
    float batteryPct;
//    int deviceBatteryState; // 0 unknown, 1 unplugged, 2 charging, 3 full

    //Eror Log vars
    String ErrorLogList_ErrorSource = "";
    String ErrorLogList_InsertStmt = "";
    String ErrorLogList_ErrorType = "";
    String ErrorLogList_ErrorMessage  = "";

    //User
    int globalUserListID = 99;

    //App Screens
    int globalUserScreen = 0;
    int globalUserScreen_Home = 1;
    int globalUserScreen_NewNote = 2;
    int globalUserScreen_TakePhoto = 3;
    int globalUserScreen_ViewNotes = 4;
    int globalUserScreen_ViewNoteDetails = 5;
    int globalUserScreen_Demos = 6;
    int globalUserScreen_Info = 7;

    //New Note
    EditText new_note_layout_title_editText;
    EditText new_note_layout_note_editText;
    Button new_note_layout_save_button;

    //View Notes
    ListView view_notes_layout_listView;
    List<HashMap<String, String>> notesfillMaps;
    static CustomAdapter notesCustomAdapter;

    ArrayList<Integer> noteListIDArrayList = new ArrayList<>();
    ArrayList<String> noteTitleArrayList = new ArrayList<>();
    ArrayList<String> noteTextArrayList = new ArrayList<>();
    ArrayList<String> noteCreatedArrayList = new ArrayList<>();
    ArrayList<String> noteGPSLatArrayList = new ArrayList<>();
    ArrayList<String> noteGPSLonArrayList = new ArrayList<>();

    //View Note Details
    ListView view_note_details_layout_listView;
    static CustomAdapter noteDetailsCustomAdapter;

    int globalSelectedNote_ListID;
    String globalSelectedNote_Text;
    String globalSelectedNote_Created;
    String globalSelectedNote_GPSLat;
    String globalSelectedNote_GPSLon;

    ArrayList<Integer> notePhotoListIDArrayList = new ArrayList<>();
    ArrayList<String> notePhotoArrayList = new ArrayList<>();
    String globalSelectedNote_Photo;

    /*
    SQLite
    Tables
    Dialogs
    GPS
    Photos
     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initApp();
    }

    @Override
    public void onBackPressed() {
//        super.onBackPressed();
        loadHomeScreen();
    }

    public void initApp(){
        createDatabase();
    }

    public void createDatabase() {
        // Check to see if there is already a deviceID in the database, so that it doesn't get created everytime you load the app
        //create the database and tables
        myDB = this.openOrCreateDatabase("smd00_fn", MODE_PRIVATE, null);
        myDB.execSQL("CREATE TABLE IF NOT EXISTS NoteList (NoteListID INTEGER PRIMARY KEY AUTOINCREMENT, UserListId INTEGER, NoteTitle VARCHAR(255), NoteText VARCHAR(255), Created DATETIME DEFAULT (datetime('now','localtime')), GPSLat FLOAT, GPSLon FLOAT, IsChanged INTEGER DEFAULT 0, RecordStatus INTEGER DEFAULT 1, LastUpdated DATETIME);");
        myDB.execSQL("CREATE TABLE IF NOT EXISTS ErrorLogList (ID INTEGER PRIMARY KEY AUTOINCREMENT, DeviceID VARCHAR(50), UserListID INTEGER, UserScreen INTEGER, ErrorSource VARCHAR(50), ErrorType VARCHAR(50), ErrorMessage VARCHAR(2000), DataSent DATETIME, DataSentBatchID VARCHAR(50), Created DATETIME DEFAULT (datetime('now','localtime')));");
}

    public void insertErrorLog(){
        ErrorLogList_InsertStmt = "INSERT INTO ErrorLogList (DeviceID, DriverListID, UserScreen, ErrorSource, ErrorType, ErrorMessage) " +
                "VALUES ('(SELECT TOP(1) DeviceID FROM GeneralSettings ORDER BY Created DESC)', " + globalUserListID + ", " + globalUserScreen + ", '" + ErrorLogList_ErrorSource +
                "', '" + ErrorLogList_ErrorType + "', '" + ErrorLogList_ErrorMessage + "')";
        try {
            myDB.execSQL(ErrorLogList_InsertStmt);
        } catch (SQLException e99) {
            e99.printStackTrace();
        }
    }

    public void activity_main_new_note_button_click(View view) {
        loadNewNoteScreen();
    }

    public void activity_main_view_notes_button_click(View view) {
        loadViewNotesScreen();
    }

    public void activity_main_demos_button_click(View view) {
        loadDemosScreen();
    }

    public void activity_main_info_button_click(View view) {
        loadInfoScreen();
    }

    public void loadHomeScreen(){
        setContentView(R.layout.activity_main);
        globalUserScreen = globalUserScreen_Home;
    }

    public void loadNewNoteScreen(){
        setContentView(R.layout.new_note_layout);
        globalUserScreen = globalUserScreen_NewNote;
        startGPS();

        new_note_layout_title_editText = (EditText)findViewById(R.id.new_note_layout_title_editText);
        new_note_layout_note_editText = (EditText)findViewById(R.id.new_note_layout_note_editText);
        new_note_layout_save_button = (Button)findViewById(R.id.new_note_layout_save_button);
    }

    public void loadViewNotesScreen(){
        //Load view notes layout
        setContentView(R.layout.view_notes_layout);
        //Set current screen global var
        globalUserScreen = globalUserScreen_ViewNotes;
        //Initiate notes ListView
        view_notes_layout_listView = (ListView) findViewById(R.id.view_notes_layout_listView);
        //Clear notes data arrays
        noteListIDArrayList.clear();
        noteTitleArrayList.clear();
        noteTextArrayList.clear();
        noteCreatedArrayList.clear();
        noteGPSLatArrayList.clear();
        noteGPSLonArrayList.clear();
        notePhotoListIDArrayList.clear();
        int tmpNoteListCount = 0;

        //Get notes
        try {
            String[] args = new String[]{Integer.toString(globalUserListID)};
            Cursor c = myDB.rawQuery("SELECT * FROM NoteList WHERE UserListID = ? AND RecordStatus > 0 ORDER BY Created DESC", args);
            if (c.getCount() > 0) {
                c.moveToFirst();
                if (c != null) {
                    do {
                        int tmpNoteListID = c.getInt(c.getColumnIndex("NoteListID"));
                        String tmpNoteTitle = c.getString(c.getColumnIndex("NoteTitle"));
                        String tmpNoteText = c.getString(c.getColumnIndex("NoteText"));
                        String tmpCreated = c.getString(c.getColumnIndex("Created"));
                        String tmpLat = c.getString(c.getColumnIndex("GPSLat"));
                        String tmpLon = c.getString(c.getColumnIndex("GPSLon"));
    //                    int tmpPhotoListID = c.getInt(c.getColumnIndex("PhotoListID"));

                        if (!noteListIDArrayList.contains(tmpNoteListID)) {
                            noteListIDArrayList.add(tmpNoteListID);
                            noteTitleArrayList.add(tmpNoteTitle);
                            noteTextArrayList.add(tmpNoteText);
                            noteCreatedArrayList.add(tmpCreated);
                            noteGPSLatArrayList.add(tmpLat);
                            noteGPSLonArrayList.add(tmpLon);
    //                        notePhotoListIDArrayList.add(tmpPhotoListID);
                            tmpNoteListCount = tmpNoteListCount + 1;
                        }

                    } while (c.moveToNext());
                }
            }
            c.close();

            String[] from = new String[]{"noteTitle", "noteCreated", "noteLocation", "noteText"};
            int[] to = new int[]{R.id.view_notes_list_title, R.id.view_notes_list_created, R.id.view_notes_list_location, R.id.view_notes_list_text};

            notesfillMaps = new ArrayList<>();
            notesfillMaps.clear();

            for (int i = 0; i < noteListIDArrayList.size(); i++) {
                HashMap<String, String> map = new HashMap<String, String>();

    //            int tmpNoteListID = noteListIDArrayList.get(i);
                String tmpNoteTitle = noteTitleArrayList.get(i);
                String tmpNoteText = noteTextArrayList.get(i);
                String tmpCreated = noteCreatedArrayList.get(i);
                String tmpLat = noteGPSLatArrayList.get(i);
                String tmpLon = noteGPSLonArrayList.get(i);
    //            int tmpPhotoListID = notePhotoListIDArrayList.get(i);

                map.put("noteTitle", "" + tmpNoteTitle);
                map.put("noteCreated", "" + tmpCreated);
                map.put("noteLocation", "(" + tmpLat + "," + tmpLon + ")");
                map.put("noteText", "" + tmpNoteText);
                notesfillMaps.add(map);
            }

            notesCustomAdapter = new CustomAdapter(this, notesfillMaps, R.layout.view_notes_list, from, to);
            view_notes_layout_listView.setAdapter(notesCustomAdapter);
        } catch (Exception e) {
            e.printStackTrace();
        }

        view_notes_layout_listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1,
                                    int position, long arg3) {
                loadNewNoteScreen();
            }
        });
    }

    public void loadDemosScreen(){
        setContentView(R.layout.demos_layout);
        globalUserScreen = globalUserScreen_Demos;
    }

    public void loadInfoScreen(){
//        setContentView(R.layout.info_layout);
        globalUserScreen = globalUserScreen_Info;
    }

    public void new_note_layout_save_button_click(View view) {
        String tmpNoteTitle = new_note_layout_title_editText.getText().toString();
        String tmpNoteText = new_note_layout_note_editText.getText().toString();

        if(tmpNoteTitle.equals("")){
            showMessage_emptyOK("Title cannot be empty");
            return;
        }

        Toast.makeText(this, "" + tmpNoteTitle + "\r\n" + tmpNoteText, Toast.LENGTH_SHORT).show();

        try {
            getLocation();
            String insertStmt = "INSERT INTO NoteList (NoteTitle, NoteText, GPSLat, GPSLon, UserListID) VALUES('" + tmpNoteTitle + "', '" + tmpNoteText + "', '" + currentLat + "', '" + currentLon + "', " + globalUserListID + ")";
            myDB.execSQL(insertStmt);
            Log.d(TAG, insertStmt);
        } catch (SQLException e) {
            e.printStackTrace();
//            ErrorLogList_ErrorSource = getClass().getSimpleName() + " / " + new Object(){}.getClass().getEnclosingMethod().getName();//Thread.currentThread().getStackTrace();
//            ErrorLogList_ErrorType = "SQLException";
//            ErrorLogList_ErrorMessage  = e.toString().replace("'", "''").replace(",", " | ");
//            insertErrorLog();
        }

//        try {
//            String insertStmt = "INSERT INTO GPSLocList (GPSLat, GPSlon, LogDateTime, Speed, Course, BatteryLevel) VALUES('" + currentLat + "','" + currentLon + "','" + currentDate() + "','" + currentSpeed + "','" + currentCourse + "','" + batteryPct + "')";
//            myDB.execSQL(insertStmt);
//            Log.d(TAG, insertStmt);
//        } catch (SQLException e) {
//            e.printStackTrace();
////            ErrorLogList_ErrorSource = getClass().getSimpleName() + " / " + new Object(){}.getClass().getEnclosingMethod().getName();//Thread.currentThread().getStackTrace();
////            ErrorLogList_ErrorType = "SQLException";
////            ErrorLogList_ErrorMessage  = e.toString().replace("'", "''").replace(",", " | ");
////            insertErrorLog();
//        }
    }

    public void new_note_layout_exit_button_click(View view) {
        loadHomeScreen();
    }

    private void showMessageOKCancel(String message, DialogInterface.OnClickListener okListener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("ATTENTION")
                .setMessage(message)
                .setPositiveButton("OK", okListener)
                .setNegativeButton("Cancel", null);
        final AlertDialog dialog = builder.create();
        dialog.show();
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        this.setFinishOnTouchOutside(false);
    }

    private void showMessageOK(String message, DialogInterface.OnClickListener okListener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("ATTENTION")
                .setMessage(message)
                .setPositiveButton("OK", okListener);
        final AlertDialog dialog = builder.create();
        dialog.show();
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        this.setFinishOnTouchOutside(false);
    }

    private void showMessage_emptyOK(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("ATTENTION")
                .setMessage(message)
                .setPositiveButton("OK", null);
        final AlertDialog dialog = builder.create();
        dialog.show();
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        this.setFinishOnTouchOutside(false);
    }

    public String currentDate() {
        String currentDate = null;
        try {
            currentDate = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return currentDate;
    }

    public void exitApp(){
        try {
            Log.d(TAG, "Exiting Note Full App");
//        myDB.close();
//        notificationManager.cancelAll();
            super.finish();
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //------------------------------ GPS functions

    private boolean hasGPSEnabled() {
        boolean isGPSEnabled = false;
        try {
            isGPSEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return isGPSEnabled;
    }

    //    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.d(TAG, "onStatusChanged();");
    }

    //    @Override
    public void onProviderDisabled(String provider) {
        try {
            Toast.makeText(getApplicationContext(), "Gps is turned off... ", Toast.LENGTH_SHORT).show();

            if(!hasGPSEnabled() && enableGPSTimesAsked == 0){
                enableGPSTimesAsked = 1;
                AlertDialog.Builder builder = new AlertDialog.Builder(this)
                        .setTitle("GPS TURNED OFF")
                        .setMessage("GPS Must be turned on.\r\nClick OK to turn the GPS on.")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                enableGPSTimesAsked = 1;
                                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                startActivity(intent);
                                return;
                            }
                        });
                final AlertDialog dialog = builder.create();
                dialog.show();
                dialog.setCancelable(false);
                dialog.setCanceledOnTouchOutside(false);
                this.setFinishOnTouchOutside(false);
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //    @Override
    public void onProviderEnabled(String provider) {
//        Toast.makeText(getApplicationContext(), "Gps is turned on... ", Toast.LENGTH_SHORT).show();
        enableGPSTimesAsked = 0;
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, GPSupdateTimeMs, GPSdistanceFilterM, this);
        } catch (SecurityException e) {
            e.printStackTrace();
            Log.d(TAG, "GPS Error: " + e);
        }
    }

    public void onLocationChanged(Location location) {
        try {
            currentAccuracy = location.getAccuracy();
            currentProvider = location.getProvider();
            currentAltitude = location.getAltitude();
            currentLat = location.getLatitude();
            currentLon = location.getLongitude();
            currentSpeed = (location.getSpeed() * 3.6);
            currentBearing = location.getBearing();
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = this.registerReceiver(null, ifilter);
            int level = 0;
            try {
                level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            } catch (Exception e) {
                e.printStackTrace();
                ErrorLogList_ErrorSource = getClass().getSimpleName() + " / " + new Object(){}.getClass().getEnclosingMethod().getName();//Thread.currentThread().getStackTrace();
                ErrorLogList_ErrorType = "Exception";
                ErrorLogList_ErrorMessage  = e.toString().replace("'", "''").replace(",", " | ");
                insertErrorLog();
            }
            int scale = 0;
            try {
                scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            } catch (Exception e) {
                e.printStackTrace();
            }
            batteryPct = (level / (float) scale) * 100;

            try {
                if (globalUserScreen == globalUserScreen_NewNote) {
                    ((TextView)findViewById(R.id.new_note_layout_live_location)).setText(getString(R.string.current_location) + " " + currentLat + ", " + currentLon + "");
                    if(currentSpeed>0)
                        ((TextView)findViewById(R.id.new_note_layout_live_speed)).setText(String.format("%.0f", currentSpeed) + " km/h");
                }
//                if(globalUserScreen == globalUserScreen_Info){
//                    ((TextView)findViewById(R.id.info_speed)).setText(String.format("%.0f", currentSpeed));
//                    String tmpInfoLatLon = "\r\nLat: " + String.format("%.6f", currentLat) + "\r\nLon: " + String.format("%.6f",currentLon);
//                    if(currentBearing > 0){
//                        tmpInfoLatLon = tmpInfoLatLon + "\r\n" + getResources().getString(R.string.bearing) + ": " + String.format("%f", currentBearing);
//                    }
//                    ((TextView) findViewById(R.id.info_lat_lon)).setText(tmpInfoLatLon);
//                    //                String tmpInfoOther = "";
//                    //                tmpInfoOther = getResources().getString(R.string.bearing) + ": " + String.format("%.2f", currentBearing) + "°"; //getResources() not needed
//                    //                tmpInfoOther = tmpInfoOther + "\r\n" + getResources().getString(R.string.battery) + ": " + String.format("%.0f", batteryPct) + "%";
//                    //                ((TextView) findViewById(R.id.info_other)).setText(tmpInfoOther);
//                }
            } catch (Exception e) {
                e.printStackTrace();
            }

//            if(drivingMonitorOn && (currentSpeed > 20)){
//                try {
//                    String insertStmt = "INSERT INTO GPSLocList (GPSLat, GPSlon, Speed, Course, BatteryLevel) VALUES('" + currentLat + "','" + currentLon + "','" + currentSpeed + "','" + currentBearing + "','" + batteryPct + "')";
//                    myDB.execSQL(insertStmt);
//                    Log.d(TAG, insertStmt);
//                    //            globalGPSSuccess = false;
//                    //startGPSUpdateBGProcess();
//                } catch (SQLException e) {
//                    e.printStackTrace();
//                    Log.d(TAG, "Error inserting into GPSLocList " + e);
//                    ErrorLogList_ErrorSource = getClass().getSimpleName() + " / " + new Object(){}.getClass().getEnclosingMethod().getName();//Thread.currentThread().getStackTrace();
//                    ErrorLogList_ErrorType = "SQLException";
//                    ErrorLogList_ErrorMessage  = e.toString().replace("'", "''").replace(",", " | ");
//                    insertErrorLog();
//                }
//            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    final private int REQUEST_CODE_ASK_PERMISSIONS = 123;

    public void startGPS(){
        try {
            lm = (LocationManager) getSystemService(LOCATION_SERVICE);

            //check for permissions
            if (Build.VERSION.SDK_INT >= 23) {
                //            Log.d(TAG, "SDK > 23 : Marshmallow+");

                int hasGPSPermission = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION);
                if (hasGPSPermission != PackageManager.PERMISSION_GRANTED) {
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                        showMessageOK("You need to allow access to GPS for this app to be able to track your location",
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        ActivityCompat.requestPermissions(MainActivity.this, new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CODE_ASK_PERMISSIONS);
                                    }
                                });
                        return;
                    }
                    ActivityCompat.requestPermissions(MainActivity.this, new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CODE_ASK_PERMISSIONS);
                    try {
                        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, GPSupdateTimeMs, GPSdistanceFilterM, this); // (LocationListener) this);
                    } catch (Exception e) {
                        e.printStackTrace();
                        ErrorLogList_ErrorSource = getClass().getSimpleName() + " / " + new Object(){}.getClass().getEnclosingMethod().getName();//Thread.currentThread().getStackTrace();
                        ErrorLogList_ErrorType = "Exception";
                        ErrorLogList_ErrorMessage  = e.toString().replace("'", "''").replace(",", " | ");
                        insertErrorLog();
                    }
                    return;
                }
            } else {
                //            Log.d(TAG, "SDK < 23 : Pre-Marshmallow");

                if (!hasGPSEnabled()) {
                    lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, GPSupdateTimeMs, GPSdistanceFilterM, this);
                    setTitle("Full Note - NO GPS");

                    AlertDialog.Builder builder = new AlertDialog.Builder(this)
                            .setTitle("GPS DISABLED")
                            .setMessage("GPS Must be turned on.\r\nClick OK to turn the GPS on.")
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    Intent gpsOptionsIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                    startActivity(gpsOptionsIntent);
                                    return;
                                }
                            });
                    final AlertDialog dialog = builder.create();
                    dialog.show();
                    dialog.setCancelable(false);
                    dialog.setCanceledOnTouchOutside(false);
                    this.setFinishOnTouchOutside(false);

                } else {
                    //Empty
                }
            }

            //request location updates (start listening)
            try {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, GPSupdateTimeMs, GPSdistanceFilterM, this);
            } catch (Exception e) {
                e.printStackTrace();
                ErrorLogList_ErrorSource = getClass().getSimpleName() + " / " + new Object(){}.getClass().getEnclosingMethod().getName();//Thread.currentThread().getStackTrace();
                ErrorLogList_ErrorType = "Exception";
                ErrorLogList_ErrorMessage  = e.toString().replace("'", "''").replace(",", " | ");
                insertErrorLog();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
//        Log.d(TAG, "Ready to run /startGPS/");
//        Log.d(TAG, "Last known location: " + getLocation());
    }

//    public void stopGPS(){
//        try {
//            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//                if (hasGPSEnabled()) {
//                    lm.removeUpdates(this);
//                }
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

    public void getLocation() {
//    public String getLocation(){
        Criteria criteria = new Criteria();
        String bestProvider = lm.getBestProvider(criteria, false);
        try {
            Location location = lm.getLastKnownLocation(bestProvider);
            currentLat = location.getLatitude();
            currentLon = location.getLongitude();
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    public class CustomAdapter extends SimpleAdapter {
        public CustomAdapter(Context context, List<HashMap<String, String>> items, int resource, String[] from, int[] to) {
            super(context, items, resource, from, to);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);

            if (globalUserScreen == globalUserScreen_ViewNotes) {
                view_notes_layout_listView.requestLayout();
            }

            return view;
        }
    }
}

package org.super169.findlocation;


import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMyLocationButtonClickListener;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * This demo shows how GMS Location can be used to check for changes to the users location.  The
 * "My Location" button uses GMS Location to set the blue dot representing the users location. To
 * track changes to the users location on the map, we request updates from the
 * {@link com.google.android.gms.location.FusedLocationProviderApi}.
 */
public class FindLocationActivity extends FragmentActivity
        implements
        ConnectionCallbacks,
        OnConnectionFailedListener,
        LocationListener,
        OnMyLocationButtonClickListener,
        OnMapReadyCallback {

    private GoogleMap mMap;

    private GoogleApiClient mGoogleApiClient;
    private TextView mFindLocationView;
    private TextView mMyLocationView;
    private EditText mPhone;
    private EditText mKeyword;
    private Marker mMarker = null;
    private Circle mCircle = null;

    // It is intends to use fixed date format
    @SuppressLint("SimpleDateFormat")
    private static final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static final String ACTION_SMS_SENT = "org.super169.mylocation.SMS_SENT_ACTION";
    private static final String ACTION_SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";
    private static BroadcastReceiver mSendSMSReceiver;
    private static BroadcastReceiver mReceiveSMSReceiver;

    private static final DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    // These settings are the same as the settings for the map. They will in fact give you updates
    // at the maximal rates currently possible.
    private static final LocationRequest REQUEST = LocationRequest.create()
            .setInterval(5000)         // 5 seconds
            .setFastestInterval(16)    // 16ms = 60fps
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.find_location);

        final TextView appVersion = (TextView) findViewById(R.id.app_version);
        appVersion.setText(getString(R.string.app_name) + " (" + BuildConfig.VERSION_NAME + ")");

        String sPhone = getPrefData(this, getString(R.string.pref_key_phone));
        String sKeyword = getPrefData(this, getString(R.string.pref_key_keyword), getString(R.string.pref_keyword_default));

        mFindLocationView = (TextView) findViewById(R.id.find_location_text);
        mMyLocationView = (TextView) findViewById(R.id.my_location_text);
        mFindLocationView.setText(getString(R.string.msg_find_location_guide));
        mMyLocationView.setText(getString(R.string.msg_my_location_wait));
        mPhone = (EditText) findViewById(R.id.et_phone);
        mKeyword = (EditText) findViewById(R.id.et_keyword);
        mPhone.setText(sPhone);
        mKeyword.setText(sKeyword);

        // Register broadcast receivers for SMS sent and delivered intents
        mSendSMSReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String message = null;
                boolean error = true;
                switch (getResultCode()) {
                    case Activity.RESULT_OK:
                        message = getString(R.string.msg_sms_sent);
                        error = false;
                        break;
                    case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                        message = getString(R.string.msg_sms_sent_err_generic);
                        break;
                    case SmsManager.RESULT_ERROR_NO_SERVICE:
                        message = getString(R.string.msg_sms_sent_err_no_service);
                        break;
                    case SmsManager.RESULT_ERROR_NULL_PDU:
                        message = getString(R.string.msg_sms_sent_err_null_pdu);
                        break;
                    case SmsManager.RESULT_ERROR_RADIO_OFF:
                        message = getString(R.string.msg_sms_sent_err_radio_off);
                        break;
                }

                mFindLocationView.setText(message);
                mFindLocationView.setTextColor(error ? Color.RED : Color.GREEN);
            }
        };

        mReceiveSMSReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // mFindLocationView.setText("SMS Received");

                // Bundle extras = intent.getExtras();
                String targetPhone = "+852" + mPhone.getText().toString().trim();

                SmsMessage[] msgs = android.provider.Telephony.Sms.Intents.getMessagesFromIntent(intent);
                for (SmsMessage message : msgs) {
                    String fromAddress = message.getOriginatingAddress().trim();

                    if (fromAddress.equals(targetPhone)) {

                        abortBroadcast();
                        showFindLocationMessage(R.string.msg_sms_returned, true);
                        try {
                            String msgBody = message.getMessageBody();
                            showFindLocation(msgBody);

                        } catch (Exception e) {
                            showFindLocationMessage(R.string.msg_sms_returned_err, true);
                        }
                    }
                }

            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
        setUpGoogleApiClientIfNeeded();
        mGoogleApiClient.connect();
        registerReceiver(mSendSMSReceiver, new IntentFilter(ACTION_SMS_SENT));
        registerReceiver(mReceiveSMSReceiver, new IntentFilter(ACTION_SMS_RECEIVED));
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(mReceiveSMSReceiver);
        unregisterReceiver(mSendSMSReceiver);
        if (mGoogleApiClient != null) {
            mGoogleApiClient.disconnect();
        }
    }

    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map)).getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(GoogleMap map) {
        mMap = map;
        if (mMap != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            mMap.setMyLocationEnabled(true);
            mMap.setOnMyLocationButtonClickListener(this);
            mMap.getUiSettings().setZoomControlsEnabled(false);
        }
    }

    private void setUpGoogleApiClientIfNeeded() {
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(LocationServices.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
        }
    }

    /**
     * Implementation of {@link LocationListener}.
     */
    @Override
    public void onLocationChanged(Location location) {
        // mMyLocationView.setText("Location = " + location);
        // String sDisplay = String.format(msgFormat, dateFormat.format(new Date(location.getTime())), location.getLongitude(), location.getLatitude(), location.getSpeed());
        @SuppressLint("DefaultLocale")
        String sDisplay = String.format("%s > %.2fm (%s)", dateFormat.format(new Date(location.getTime())), location.getSpeed(), location.getProvider());
        mMyLocationView.setText(sDisplay);
    }

    /**
     * Callback called when connected to GCore. Implementation of {@link ConnectionCallbacks}.
     */
    @Override
    public void onConnected(Bundle connectionHint) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient,
                REQUEST,
                this);  // LocationListener
    }

    /**
     * Callback called when disconnected from GCore. Implementation of {@link ConnectionCallbacks}.
     */
    @Override
    public void onConnectionSuspended(int cause) {
        // Do nothing
    }

    /**
     * Implementation of {@link OnConnectionFailedListener}.
     */
    @Override
    public void onConnectionFailed(@NonNull ConnectionResult result) {
        // Do nothing
    }

    @Override
    public boolean onMyLocationButtonClick() {
        Toast.makeText(this, "Show my location", Toast.LENGTH_SHORT).show();
        // Return false so that we don't consume the event and the default behavior still occurs
        // (the camera animates to the user's current position).
        return false;
    }

    private void setPrefData(Context context, String key, String value) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                getString(R.string.shared_pref_key), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(key, value);
        editor.apply();
    }

    private String getPrefData(Context context, String key) {
        return getPrefData(context, key, "");
    }

    private String getPrefData(Context context, String key, String defValue) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                getString(R.string.shared_pref_key), Context.MODE_PRIVATE);
        return sharedPref.getString(key, defValue);
    }

    private void hideKeyboard() {
        InputMethodManager inputManager = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);

        // check if no view has focus:
        View view = this.getCurrentFocus();
        if (view != null) {
            inputManager.hideSoftInputFromWindow(view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    // Parameter View is required for onClick event
    @SuppressWarnings("UnusedParameters")
    public void goFindFused(View view) {
        goFindLocation(LocationProvider.Fused);
    }

    // Parameter View is required for onClick event
    @SuppressWarnings("UnusedParameters")
    public void goFindGps(View view) {
        goFindLocation(LocationProvider.GPS);
    }

    // Parameter View is required for onClick event
    @SuppressWarnings("UnusedParameters")
    public void goFindNetwork(View view) {
        goFindLocation(LocationProvider.Network);
    }

    // Parameter View is required for onClick event
    @SuppressWarnings("UnusedParameters")
    public void goFindBest(View view) {
        goFindLocation(LocationProvider.Best);
    }


    private enum LocationProvider {
        Best,
        Fused,
        GPS,
        Network
    }

    /**
     * Button to get current Location. This demonstrates how to get the current Location as required
     * without needing to register a LocationListener.
     */
    // View is reserved for future use
    private void goFindLocation(LocationProvider findType) {
        hideKeyboard();
        String sParm = "";
        String sMsg;
        if (findType == LocationProvider.Fused) {
            sMsg = getString(R.string.get_fused_location);
            sParm = ":f:";
        } else if (findType == LocationProvider.GPS) {
            sMsg = getString(R.string.get_gps_location);
            sParm = ":g:";
        } else if (findType == LocationProvider.Network) {
            sMsg = getString(R.string.get_network_location);
            sParm = ":n:";
        } else {
            sMsg = getString(R.string.get_best_location);
        }

        Toast.makeText(this, sMsg, Toast.LENGTH_SHORT).show();
        String sPhone, sKeyword;
        try {
            sPhone = mPhone.getText().toString().trim();
            sKeyword = mKeyword.getText().toString().trim();
            //if ((sPhone != null) && (!sPhone.isEmpty()) && (sKeyword != null) && (!sKeyword.isEmpty())) {
            if (!(sPhone.isEmpty() || sKeyword.isEmpty())) {
                setPrefData(this, getString(R.string.pref_key_phone), sPhone);
                setPrefData(this, getString(R.string.pref_key_keyword), sKeyword);
                clearMaker();
                sKeyword = "#" + sKeyword + sParm;

                SmsManager sms = SmsManager.getDefault();

                sms.sendTextMessage(sPhone, null, sKeyword, PendingIntent.getBroadcast(
                        FindLocationActivity.this, 0, new Intent(ACTION_SMS_SENT), 0), null);

            } else {
                showFindLocationMessage(R.string.msg_find_location_empty, true);
            }
        } catch (Exception e) {
            showFindLocationMessage(R.string.msg_input_error, true);
        }
    }

    private void showFindLocation(String msgBody) {
        try {
            String msgInfo[] = msgBody.split("#");
            if (msgInfo.length > 2) {
                Double version = Double.parseDouble(msgInfo[2]);
                String locationInfo[] = msgInfo[3].split(";");
                if (locationInfo.length > 5) {
                    Double l1 = Double.parseDouble(locationInfo[2]);
                    Double l2 = Double.parseDouble(locationInfo[3]);
                    Double accuracy = Double.parseDouble(locationInfo[4]);
                    Double speed = Double.parseDouble(locationInfo[5]);
                    LatLng center  = new LatLng(l1, l2);
                    clearMaker();
                    mMarker = mMap.addMarker( new MarkerOptions().position(center).draggable(false));
                    int mFillColor;
                    String provider = locationInfo[0].toLowerCase();

                    // Color (Hub, Saturation, Brightness):
                    //      0,1,1 - Red
                    //      32,1,1 - Orange
                    //      100,1,1 - Green

                    switch (provider) {
                        case "gps":
                            mFillColor = Color.HSVToColor(0x3F, new float[]{100, 1, 1});
                            break;
                        case "network":
                            mFillColor = Color.HSVToColor(0x3F, new float[]{0, 1, 1});
                            break;
                        default:
                            // default fused
                            mFillColor = Color.HSVToColor(0x3F, new float[]{32, 1, 1});
                            break;
                    }

                    mCircle = mMap.addCircle( new CircleOptions().center(center).radius(accuracy).strokeWidth(5).fillColor(mFillColor));
                    CameraPosition currentCameraPosition = mMap.getCameraPosition();
                    CameraPosition cameraPosition = new CameraPosition.Builder(currentCameraPosition).target(center).zoom(15).build();
                    mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));

                    @SuppressLint("DefaultLocale")
                    String sFindLocation = String.format("%s > %.2fm (%s)", locationInfo[1], speed, locationInfo[0]);
                    mFindLocationView.setText(sFindLocation);
                } else if ((version >= 1.2) && (locationInfo.length > 2)){
                    String sFindLocation = String.format("%s > %s : %s", locationInfo[0], locationInfo[1], locationInfo[2]);
                    mFindLocationView.setText(sFindLocation);
                }
            } else {
                String sFindLocation = String.format("%s > ERR: %s", format.format(Calendar.getInstance()), msgBody);
                mFindLocationView.setText(sFindLocation);
            }
        } catch (Exception e) {
            String sFindLocation = String.format("%s > %s : ERR", format.format(Calendar.getInstance()), msgBody);
            mFindLocationView.setText(sFindLocation);
        }

    }

    // Parameter prepared for generic function
    @SuppressWarnings("SameParameterValue")
    private void showFindLocationMessage(int rsId, boolean showToast) {
        String sDisplay = getString(rsId);
        if (showToast) Toast.makeText(this, sDisplay, Toast.LENGTH_SHORT).show();
        mFindLocationView.setText(sDisplay);
    }

    private void clearMaker() {
        if (mMarker != null) mMarker.remove();
        if (mCircle != null) mCircle.remove();
        mMarker = null;
        mCircle = null;
        mFindLocationView.setText("");
    }

}

package com.alooma.example.hello;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
//import com.github.aloomaio.androidsdk.aloomametrics.AloomaAPI;
import com.alooma.android.mpmetrics.AloomaAPI;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;

/**
 * For more information about integrating Alooma with your Android application,
 * please check out:
 *
 *     https://support.alooma.com/hc/en-us/articles/214019489-Android-SDK-integration
 *
 * @author alooma
 *
 */
public class MainActivity extends Activity {

    /*
     * You will use a Alooma API token to allow your app to send data to Alooma. To get your token
     * - Log in to Alooma, and select the project you want to use for this application
     * - Click the gear icon in the lower left corner of the screen to view the settings dialog
     * - In the settings dialog, you will see the label "Token", and a string that looks something like this:
     *
     *        2ef3c08e8466df98e67ea0cfa1512e9f
     *
     *   Paste it below (where you see "YOUR API TOKEN")
     */
    public static final String ALOOMA_API_TOKEN = "NOT A REAL TOKEN";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String trackingDistinctId = getTrackingDistinctId();

        // Initialize the Alooma library for tracking.
        mAlooma = AloomaAPI.getInstance(this, ALOOMA_API_TOKEN);


        // We also identify the current user with a distinct ID.

        mAlooma.identify(trackingDistinctId); //this is the distinct_id value that
        // will be sent with events. If you choose not to set this,
        // the SDK will generate one for you

        setContentView(R.layout.activity_main);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        final long nowInHours = hoursSinceEpoch();
        final int hourOfTheDay = hourOfTheDay();

        // For our simple test app, we're interested tracking
        // when the user views our application.

        // It will be interesting to segment our data by the date that they
        // first viewed our app. We use a
        // superProperty (so the value will always be sent with the
        // remainder of our events) and register it with
        // registerSuperPropertiesOnce (so no matter how many times
        // the code below is run, the events will always be sent
        // with the value of the first ever call for this user.)
        // all the change we make below are LOCAL. No API requests are made.
        try {
            final JSONObject properties = new JSONObject();
            properties.put("first viewed on", nowInHours);
            properties.put("user domain", "(unknown)"); // default value
            mAlooma.registerSuperPropertiesOnce(properties);
        } catch (final JSONException e) {
            throw new RuntimeException("Could not encode hour first viewed as JSON");
        }

        // Now we send an event to Alooma. We want to send a new
        // "App Resumed" event every time we are resumed, and
        // we want to send a current value of "hour of the day" for every event.
        // As usual,all of the user's super properties will be appended onto this event.
        try {
            final JSONObject properties = new JSONObject();
            properties.put("hour of the day", hourOfTheDay);
            mAlooma.track("App Resumed", properties);
        } catch(final JSONException e) {
            throw new RuntimeException("Could not encode hour of the day in JSON");
        }

    }

    // Associated with the "Send to Alooma" button in activity_main.xml
    // set some persistent properties that will be sent with
    // all future track() calls using AloomaAPI.registerSuperProperties()
    public void sendToAlooma(final View view) {

        final EditText firstNameEdit = (EditText) findViewById(R.id.edit_first_name);
        final EditText lastNameEdit = (EditText) findViewById(R.id.edit_last_name);
        final EditText emailEdit = (EditText) findViewById(R.id.edit_email_address);

        final String firstName = firstNameEdit.getText().toString();
        final String lastName = lastNameEdit.getText().toString();
        final String email = emailEdit.getText().toString();

        // We use the user domain as a superProperty here, but we call registerSuperProperties
        // instead of registerSuperPropertiesOnce so we can overwrite old values
        // as we get new information.
        try {
            final JSONObject domainProperty = new JSONObject();
            domainProperty.put("first name", firstName);
            domainProperty.put("last name", lastName);
            domainProperty.put("email ", email);
            domainProperty.put("user domain", domainFromEmailAddress(email));
            mAlooma.registerSuperProperties(domainProperty);
        } catch (final JSONException e) {
            throw new RuntimeException("Cannot write user email address domain as a super property");
        }

        // In addition to viewing the updated record in alooma's UI, it might
        // be interesting to see when and how many and what types of users
        // are updating their information, so we'll send an event as well.
        // You can call track with null if you don't have any properties to add
        // to an event (remember all the established superProperties will be added
        // before the event is dispatched to Alooma)
        mAlooma.track("update info button clicked", null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // To preserve battery life, the Alooma library will store
        // events rather than send them immediately. This means it
        // is important to call flush() to send any unsent events
        // before your application is taken out of memory.
        mAlooma.flush();
    }

    ////////////////////////////////////////////////////

    public void setBackgroundImage(final View view) {
        final Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
        photoPickerIntent.setType("image/*");
        startActivityForResult(photoPickerIntent, PHOTO_WAS_PICKED);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (PHOTO_WAS_PICKED == requestCode && null != data) {
            final Uri imageUri = data.getData();
            if (null != imageUri) {
                // AsyncTask, please...
                final ContentResolver contentResolver = getContentResolver();
                try {
                    final InputStream imageStream = contentResolver.openInputStream(imageUri);
                    System.out.println("DRAWING IMAGE FROM URI " + imageUri);
                    final Bitmap background = BitmapFactory.decodeStream(imageStream);
                    getWindow().setBackgroundDrawable(new BitmapDrawable(getResources(), background));
                } catch (final FileNotFoundException e) {
                    Log.e(LOGTAG, "Image apparently has gone away", e);
                }
            }
        }
    }

    private String getTrackingDistinctId() {
        final SharedPreferences prefs = getPreferences(MODE_PRIVATE);

        String ret = prefs.getString(ALOOMA_DISTINCT_ID_NAME, null);
        if (ret == null) {
            ret = generateDistinctId();
            final SharedPreferences.Editor prefsEditor = prefs.edit();
            prefsEditor.putString(ALOOMA_DISTINCT_ID_NAME, ret);
            prefsEditor.commit();
        }

        return ret;
    }

    // These disinct ids are here for the purposes of illustration.
    // In practice, there are great advantages to using distinct ids that
    // are easily associated with user identity, either from server-side
    // sources, or user logins. A common best practice is to maintain a field
    // in your users table to store alooma distinct_id, so it is easily
    // accesible for use in attributing cross platform or server side events.
    private String generateDistinctId() {
        final Random random = new Random();
        final byte[] randomBytes = new byte[32];
        random.nextBytes(randomBytes);
        return Base64.encodeToString(randomBytes, Base64.NO_WRAP | Base64.NO_PADDING);
    }

    ///////////////////////////////////////////////////////
    // conveniences

    private int hourOfTheDay() {
        final Calendar calendar = Calendar.getInstance();
        return calendar.get(Calendar.HOUR_OF_DAY);
    }

    private long hoursSinceEpoch() {
        final Date now = new Date();
        final long nowMillis = now.getTime();
        return nowMillis / 1000 * 60 * 60;
    }

    private String domainFromEmailAddress(String email) {
        String ret = "";
        final int atSymbolIndex = email.indexOf('@');
        if ((atSymbolIndex > -1) && (email.length() > atSymbolIndex)) {
            ret = email.substring(atSymbolIndex + 1);
        }

        return ret;
    }

    private AloomaAPI mAlooma;
    private static final String ALOOMA_DISTINCT_ID_NAME = "Alooma Example $distinctid";
    private static final int PHOTO_WAS_PICKED = 2;
    private static final String LOGTAG = "Alooma Example App";
}
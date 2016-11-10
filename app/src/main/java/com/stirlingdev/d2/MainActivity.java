package com.stirlingdev.d2;

import android.app.SearchManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;

/* To import and query contacts */
import android.provider.ContactsContract;
import android.support.v4.app.ActivityCompat;

import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;

import static android.widget.Toast.makeText;
import static edu.cmu.pocketsphinx.SpeechRecognizerSetup.defaultSetup;

import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;


public class MainActivity extends AppCompatActivity implements RecognitionListener{

    // Recognizer

    /* Named searches allow to quickly reconfigure the decoder */
    private static final String KWS_SEARCH = "wakeup";
    private static final String WEB_SEARCH = "search";
    private static final String MENU_SEARCH = "menu";

    /* Keyword we are looking for to activate menu */
    private static final String KEYPHRASE = "jono wakeup";

    private SpeechRecognizer recognizer;
    private HashMap<String, Integer> captions;

    //Contact Info
    private static final String CONTACT_NAME = "name";
    private static final String CONTACT_PHONE = "phone";
    private static final String CONTACT_EMAIL = "email";

    //Debugging
    private static final String TAG = "checking";
    private static final String TAG2= "Call Detected:";
    private static final String TAG3="Contact Query:";
    private static final String TAG4="Web Query:";
    private static final String TAG5="SMS Detected:";
    private static final String TAG6="Create Name Dictionary:";
    
    //Permissions
    private static final int REQUEST_CODE_ASK_PERMISSIONS=123;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ((TextView) findViewById(R.id.caption_text))
                .setText("Preparing the recognizer");


        // Check Permissions
        requestingRecordPermissions();
        requestingCallPermissions();
        requestingContactPermissions();
        requestingSMSPermissions();

        //TOOLBAR
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // BEGIN LOADING SPEECH RECOGNIZER
        // Prepare the data for UI
        captions = new HashMap<String, Integer>();
        captions.put(KWS_SEARCH, R.string.kws_caption);
        captions.put(MENU_SEARCH, R.string.menu_caption);
        captions.put(WEB_SEARCH, R.string.WEB_caption);
        //setContentView(R.layout.splash);

        // Recognizer initialization is a time-consuming and it involves IO,
        // so we execute it in async task

        new AsyncTask<Void, Void, Exception>() {



            @Override
            protected Exception doInBackground(Void... params) {
                try {
                    Assets assets = new Assets(MainActivity.this);
                    File assetDir = assets.syncAssets();
                    setupRecognizer(assetDir);
                } catch (IOException e) {
                    return e;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Exception result) {
                if (result != null) {
                    ((TextView) findViewById(R.id.caption_text))
                            .setText("Failed to init recognizer " + result);
                } else {
                    switchSearch(KWS_SEARCH);
                }
            }
        }.execute();
        setContentView(R.layout.activity_main);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        recognizer.cancel();
        recognizer.shutdown();
    }

    /**
     * In partial result we get quick updates about current hypothesis. In
     * keyword spotting mode we can react here, in other modes we need to wait
     * for final result in onResult.
     */
    @Override
    public void onPartialResult(Hypothesis hypothesis) {

        if (hypothesis == null)
            return;

        String text = hypothesis.getHypstr();
        if (text.equals(KEYPHRASE)) {
            Log.v(TAG, "I'm Awake!");
            switchSearch(MENU_SEARCH);

        } else if (text.contains(WEB_SEARCH)) {
            Log.v(TAG, WEB_SEARCH);
            switchSearch(WEB_SEARCH);


        }else {
            ((TextView) findViewById(R.id.result_text)).setText(text);
            Log.v(TAG, text);
        }
    }

    /**
     * This callback is called when we stop the recognizer.
     */
    @Override
    public void onResult(Hypothesis hypothesis) {
        Log.v(TAG, "Result");
        ((TextView) findViewById(R.id.result_text)).setText("");
        if (hypothesis != null) {
            String text = hypothesis.getHypstr();

            if (text.contains("call ")){
                Log.v(TAG2, "Initiate");
                text.replace("call ", "");
                callContact(text);
                Log.v(TAG2, "Finished");
            }
            else if(text.contains("text ")){
                Log.v(TAG5, "Initiate");
                text.replace("text ", "");
                textContact(text);
                Log.v(TAG5, "Finished");
            }
            else if (text.contains(KEYPHRASE)) {}

            else{
                Log.v(TAG4, "Initiate");
                webSearch(text);
                Log.v(TAG4, "Finished");
            }

            makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBeginningOfSpeech() {

    }

    /**
     * We stop recognizer here to get a final result
     */
    @Override
    public void onEndOfSpeech() {
        //if (!recognizer.getSearchName().equals(KWS_SEARCH))
        //    switchSearch(KWS_SEARCH);
    }

    private void switchSearch(String searchName) {
        recognizer.stop();
        Log.v(TAG, "start switch");
        // If we are not spotting, start listening with timeout (10000 ms or 10 seconds).
        if (searchName.equals(KWS_SEARCH)) {
            Log.v(TAG, "keyword");
            recognizer.startListening(searchName);
        } else {
            Log.v(TAG, "not keyword");
            recognizer.startListening(searchName, 1000);
        }

        String caption = getResources().getString(captions.get(searchName));
        ((TextView) findViewById(R.id.caption_text)).setText(caption);
    }

    public void webSearch(String text) {
        Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
        intent.putExtra(SearchManager.QUERY, text);
        startActivity(intent);
    }


    private void setupRecognizer(File assetsDir) throws IOException {
        // The recognizer can be configured to perform multiple searches
        // of different kind and switch between them

        recognizer = defaultSetup()
                .setAcousticModel(new File(assetsDir, "en-us-ptm"))
                .setDictionary(new File(assetsDir, "cmudict-en-us.dict"))

                        // To disable logging of raw audio comment out this call (takes a lot of space on the device)
                .setRawLogDir(assetsDir)

                        // Threshold to tune for keyphrase to balance between false alarms and misses
                .setKeywordThreshold(1e-45f)

                        // Use context-independent phonetic search, context-dependent is too slow for mobile
                .setBoolean("-allphone_ci", true)

                .getRecognizer();
        recognizer.addListener(this);

        // Create keyword-activation search.
        recognizer.addKeyphraseSearch(KWS_SEARCH, KEYPHRASE);

        // Create grammar-based search for selection between demos

        createNameGrammar();

        File menuGrammar = new File(assetsDir, "menu.gram");
        recognizer.addGrammarSearch(MENU_SEARCH, menuGrammar);

        // Create language model search
        File languageModel = new File(assetsDir, "en-us.lm");
        recognizer.addNgramSearch(WEB_SEARCH, languageModel);

    }

    @Override
    public void onError(Exception error) {
        ((TextView) findViewById(R.id.caption_text)).setText(error.getMessage());
    }

    @Override
    public void onTimeout() {
        switchSearch(KWS_SEARCH);
    }


    /**
     *  This method searches for a contact by name
     *  It saves the phone numbers and emails
     *  Returns true if found
     *  False if not
    */
    public boolean searchForContact(String text, HashMap contact_info) {
        String phoneNumber = null;
        String email = null;

        Uri CONTENT_URI = ContactsContract.Contacts.CONTENT_URI;

        String _ID = ContactsContract.Contacts._ID;
        String DISPLAY_NAME = ContactsContract.Contacts.DISPLAY_NAME;
        String HAS_PHONE_NUMBER = ContactsContract.Contacts.HAS_PHONE_NUMBER;

        Uri PhoneCONTENT_URI = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        String Phone_CONTACT_ID = ContactsContract.CommonDataKinds.Phone.CONTACT_ID;
        String NUMBER = ContactsContract.CommonDataKinds.Phone.NUMBER;

        Uri EmailCONTENT_URI = ContactsContract.CommonDataKinds.Email.CONTENT_URI;
        String Email_CONTACT_ID = ContactsContract.CommonDataKinds.Email.CONTACT_ID;
        String DATA = ContactsContract.CommonDataKinds.Email.DATA;

        ContentResolver contentResolver = getContentResolver();

        Cursor cursor = contentResolver.query(CONTENT_URI, null, null, null, null);

        /*
            Here we begin to check for contacts:
                If there are contacts -> Search 'em
                If not, too bad
         */

        Log.v(TAG3, "Initiate");
        if (cursor.getCount() > 0) {
            while (cursor.moveToNext()) {

                String contact_id = cursor.getString(cursor.getColumnIndex(_ID));
                String name = cursor.getString(cursor.getColumnIndex(DISPLAY_NAME));

                int has_phone_number = Integer.parseInt(cursor.getString(cursor.getColumnIndex(HAS_PHONE_NUMBER)));

                if (text.contains(name.toLowerCase())) {
                    Log.v(TAG3, "Contact Found");
                    contact_info.put(CONTACT_NAME, name.toLowerCase());

                    if (has_phone_number > 0) {
                        Cursor phoneCursor = contentResolver.query(PhoneCONTENT_URI, null, Phone_CONTACT_ID + " = ?", new String[]{contact_id}, null);

                        //Create a place to store each phone number
                        List<String> numbers = new ArrayList<String>();

                        //cycle through phone numbers and add them to the list
                        while (phoneCursor.moveToNext()) {
                            phoneNumber = phoneCursor.getString(phoneCursor.getColumnIndex(NUMBER));
                            Log.v(TAG3, "Phone Number: "+phoneNumber);
                            numbers.add(phoneNumber);
                        }
                        //add numbers to contact info
                        contact_info.put(CONTACT_PHONE, numbers);

                        phoneCursor.close();

                        List<String> emails = new ArrayList<String>();

                        Cursor emailCursor = contentResolver.query(EmailCONTENT_URI, null, Email_CONTACT_ID + " = ?", new String[]{contact_id}, null);
                        while (emailCursor.moveToNext()) {
                            email = emailCursor.getString(emailCursor.getColumnIndex(NUMBER));
                            emails.add(email);
                        }
                        contact_info.put(CONTACT_EMAIL, emails);
                        emailCursor.close();


                    }
                    return true;
                }
            }
        }
        Log.v(TAG3, "Unable to Find Contact");
        return false;
    }

    // TODO: Must give the user the option of which mobile to call9

    public void callContact(String text) {
        HashMap<String, List<String>> contact_info = new HashMap<String, List<String>>();

        Log.v(TAG2, "Search For Contact");
        if (searchForContact(text, contact_info)) {

            Log.v(TAG2, "Contact Found");
            //We will set the first phone number for default
            List<String> numbers = contact_info.get(CONTACT_PHONE);
            Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + numbers.get(0)));

            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                startActivity(intent);
            }

        }else{

            Log.v(TAG2, "Unable to Find Contact");
        }

    }


    public void textContact(String text) {
        HashMap<String, List<String>> contact_info = new HashMap<String, List<String>>();

        Log.v(TAG5, "Search For Contact");
        if (searchForContact(text, contact_info)) {
            text.replace("Send a text to ","");
            text.replace(contact_info.get(CONTACT_NAME)+" ", "");
            text.replace("saying ","");

            Log.v(TAG5, "Contact Found");
            //We will set the first phone number for default
            List<String> numbers = contact_info.get(CONTACT_PHONE);

            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                SmsManager smsmanager = SmsManager.getDefault();
                smsmanager.sendTextMessage(numbers.get(0), null, text, null, null);
            }

        }else{
            Log.v(TAG2, "Unable to Find Contact");
        }
    }

    /**
        Here begin the methods for requesting permissions.
     */

    public void requestingCallPermissions(){
        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(MainActivity.this,
                android.Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    android.Manifest.permission.CALL_PHONE)) {

                // Show an expanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,new String[]{android.Manifest.permission.CALL_PHONE},REQUEST_CODE_ASK_PERMISSIONS);
            }
        }
    }

    public void requestingSMSPermissions(){
        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(MainActivity.this,
                android.Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    android.Manifest.permission.SEND_SMS)) {

                // Show an expanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,new String[]{android.Manifest.permission.SEND_SMS},REQUEST_CODE_ASK_PERMISSIONS);
            }
        }
    }

    public void requestingContactPermissions(){
        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(MainActivity.this,
                android.Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    android.Manifest.permission.READ_CONTACTS)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,new String[]{android.Manifest.permission.READ_CONTACTS},REQUEST_CODE_ASK_PERMISSIONS);
            }
        }
    }

    public void requestingRecordPermissions(){
        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(MainActivity.this,
                android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    android.Manifest.permission.RECORD_AUDIO)) {

                // Show an expanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,new String[]{android.Manifest.permission.RECORD_AUDIO},REQUEST_CODE_ASK_PERMISSIONS);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_PERMISSIONS:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission Granted
                    Toast.makeText(MainActivity.this, "Permission Allowed", Toast.LENGTH_SHORT)
                            .show();
                } else {
                    // Permission Denied
                    Toast.makeText(MainActivity.this, "Permission Denied", Toast.LENGTH_SHORT)
                            .show();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    // TODO: Create a name grammar

    public void createNameGrammar(){
        try{
            // create new dictionary file
            FileOutputStream fout = openFileOutput("names.gram", Context.MODE_WORLD_WRITEABLE);
            OutputStreamWriter osw = new OutputStreamWriter(fout);

            // scroll through contacts
            Uri CONTENT_URI = ContactsContract.Contacts.CONTENT_URI;
            String DISPLAY_NAME = ContactsContract.Contacts.DISPLAY_NAME;
            ContentResolver contentResolver = getContentResolver();

            Cursor cursor = contentResolver.query(CONTENT_URI, null, null, null, null);

        /*
            Here we begin to check for contacts:
                If there are contacts -> Search 'em
                If not, too bad
         */

            Log.v(TAG6, "Initiate");
            if (cursor.getCount() > 0) {

                StringBuilder sb = new StringBuilder();

                StringTokenizer tokens;

                while (cursor.moveToNext()) {

                    String name = cursor.getString(cursor.getColumnIndex(DISPLAY_NAME)).toLowerCase();
                    tokens = new StringTokenizer(name," ");
                    if(cursor.isLast())
                        //append whole name and the first name so that Eric henderson can be called eric
                        sb.append(name+" | "+tokens.nextElement().toString()+";");

                    else
                        sb.append(name+" | "+tokens.nextElement().toString() + " | ");

                    osw.write(sb.toString());
                    Log.v(TAG6, sb.toString());
                    sb.setLength(0);

                }


            }
        }catch(IOException ioe){
            ioe.printStackTrace();
        }
    }

    // TODO: update old dictionary (detect when new contact is added?)


}

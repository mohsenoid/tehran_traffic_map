package com.tehran.traffic.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask.Status;
import android.os.Bundle;
import android.provider.Settings.Secure;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.analytics.tracking.android.EasyTracker;
import com.tehran.traffic.R;
import com.tehran.traffic.models.CloudMessage;
import com.tehran.traffic.network.DataLoader;
import com.tehran.traffic.ui.NavigationView.OnNavigationListener;
import com.tehran.traffic.ui.TouchImageView.OnTileListener;
import com.tehran.traffic.util.IabHelper;
import com.tehran.traffic.util.IabHelper.QueryInventoryFinishedListener;
import com.tehran.traffic.util.IabResult;
import com.tehran.traffic.util.Inventory;
import com.tehran.traffic.util.Purchase;
import com.tehran.traffic.util.SkuDetails;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity implements OnClickListener,
        DialogInterface.OnClickListener, OnTileListener, OnNavigationListener, AdapterView.OnItemSelectedListener {
    // SKUs for our products: the premium upgrade (non-consumable)
    static final String SKU_ADS = "ads";
    // (arbitrary) request code for the purchase flow
    static final int RC_REQUEST = 10001;
    final static int[][] tiles = new int[12][12];
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 0;
    public static boolean firstRun = true;
    static ApplicationState appState = ApplicationState.Traffic;
    static int currentTile;
    static int currentRow;
    static int currentCol;
    static String condition = "0";
    final String TAG = MainActivity.class.getName();
    final Context context = this;
    // Does the user have the premium upgrade?
    boolean mIsAdsFree = false;
    IabHelper.QueryInventoryFinishedListener mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
        public void onQueryInventoryFinished(IabResult result,
                                             Inventory inventory) {
            Log.d(TAG, "Query inventory finished.");
            if (result.isFailure()) {
                Log.d(TAG, "Failed to query inventory: " + result);
                // mAdsFreeError = true;
                updateUi();
                return;
            } else {
                Log.d(TAG, "Query inventory was successful.");
                // does the user have the premium upgrade?
                mIsAdsFree = inventory.hasPurchase(SKU_ADS);

                // update UI accordingly

                Log.d(TAG, "User is "
                        + (mIsAdsFree ? "PREMIUM" : "NOT PREMIUM"));

                updateUi();
            }

            Log.d(TAG, "Initial inventory query finished; enabling main UI.");
        }
    };
    boolean mAdsFreeError = false;
    // The helper object
    IabHelper mHelper;
    IabHelper.QueryInventoryFinishedListener mQueryInventoryFinishedListener = new QueryInventoryFinishedListener() {

        @Override
        public void onQueryInventoryFinished(IabResult result, Inventory inv) {
            Log.d(TAG, "PrePurchase finished: " + result + ", Inventory: "
                    + inv);

            // if we were disposed of in the meantime, quit.
            if (mHelper == null)
                return;

            if (result.isFailure()) {
                Toast.makeText(context, "Error query: " + result,
                        Toast.LENGTH_LONG).show();
                // setWaitScreen(false);
                return;
            }

            SkuDetails skuDetails = inv.getSkuDetails(SKU_ADS);
            skuDetails.getPrice();
        }
    };
    // Callback for when a purchase is finished
    IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
        public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
            Log.d(TAG, "Purchase finished: " + result + ", purchase: "
                    + purchase);

            // if we were disposed of in the meantime, quit.
            if (mHelper == null)
                return;

            if (result.isFailure()) {
                Toast.makeText(context, "Error purchasing: " + result,
                        Toast.LENGTH_LONG).show();
                // setWaitScreen(false);
                return;
            }
            if (!verifyDeveloperPayload(purchase)) {
                Toast.makeText(context,
                        "Error purchasing. Authenticity verification failed.",
                        Toast.LENGTH_LONG).show();
                // setWaitScreen(false);
                return;
            }

            Log.d(TAG, "Purchase successful.");

            if (purchase.getSku().equals(SKU_ADS)) {
                // bought the premium upgrade!
                Log.d(TAG, "Purchase is premium upgrade. Congratulating user.");
                Toast.makeText(context, "Thank you for upgrading to premium!",
                        Toast.LENGTH_LONG).show();
                mIsAdsFree = true;
                updateUi();
                // setWaitScreen(false);
            }
        }

    };
    TouchImageView tivMap;
    ImageButton ibPrev, ibNext, ibRefresh, ibPause, ibBack;
    ImageView ivRoadsHelp;
    Spinner spState;
    NavigationView nvMap;
    TextView tvError;
    View inMap, inNews, inAbout, inContact;
    Dialog updateDialog;
    private View llAds, purchase1, purchase2;
    private boolean doubleBackToExitPressedOnce;
    private DataLoader loader;
//    static String ms;

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        setState(position);

        if (loader == null || loader.isCancelled()
                || loader.getStatus() == Status.FINISHED) {
            loader = new DataLoader(context, tivMap, tvError);
        }
        loader.loadRoad(getState(), false);

        checkLastUpdate();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    @Override
    protected void onStart() {
        super.onStart();

        EasyTracker.getInstance().activityStart(this); // Google Analytic
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (CloudMessage.checkPlayServices(this)) {
            // If this check succeeds, proceed with normal processing.
            // Otherwise, prompt user to get valid Play Services APK.
        }

        CloudMessage.startGCM(this);

        condition = getIntent().getStringExtra("alert");
        if (condition != null) {
            String ms = getIntent().getStringExtra("msg");
            alertCloudMessage(ms);
        } else {

        }

        fillTiles();

        initForm();

        loader = new DataLoader(context, tivMap, tvError);

        switchView();

        if (!isConnected())
            tvError.setVisibility(View.VISIBLE);

        if (firstRun) {
            Toast.makeText(context, R.string.msg_tile_click, Toast.LENGTH_LONG)
                    .show();
            firstRun = false;
        }

        // in app billing

        String base64EncodedPublicKey = "MIHNMA0GCSqGSIb3DQEBAQUAA4G7ADCBtwKBrwDc6DJpNhliflAPBa/8eNgOLjcfQKfr5PachBqf66cBhk32coQat6ZkEM2TtMylipvNBKrv50zfEpSkQt4NO0uWPuAlk8pJ10mlrhx77Bdz83nSBkLegJym7v4yUG9vC0AgbTm+bDTfNjCVUJEdnM/qCh/NbTOppUUE8tpa+sOgiCwv4P8fyeXGiss75y7yryt7bdWHpqXVvUETmqVfGG/6Epu0uHsi7WbhpTcv+eECAwEAAQ==";

        mHelper = new IabHelper(context, base64EncodedPublicKey);

        // mHelper.enableDebugLogging(true);

        try {
            Log.d(TAG, "Starting setup.");
            mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
                @Override
                public void onIabSetupFinished(IabResult result) {
                    Log.d(TAG, "Setup finished.");

                    if (!result.isSuccess()) {
                        // Oh noes, there was a problem.
                        Log.d(TAG, "Problem setting up In-app Billing: "
                                + result);
                    }
                    // Hooray, IAB is fully set up!
                    mHelper.queryInventoryAsync(mGotInventoryListener);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            mAdsFreeError = true;
            updateUi();
        }

    }

    private void alertCloudMessage(String ms) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setTitle(getResources().getString(R.string.app_name));
        alertDialogBuilder
                .setMessage(ms)
                .setCancelable(false)
                .setNegativeButton(getString(R.string.close), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });

        if (ms.contains("http")) {
            // extract url from message
            int start = ms.indexOf("http");

            int len = ms.length();

            int endSpace = ms.indexOf(" ", start);
            endSpace = endSpace == -1 ? len : endSpace;

            int endEnter = ms.indexOf("\n", start);
            endEnter = endEnter == -1 ? len : endEnter;

            final String url = ms.substring(start, Math.min(Math.min(endSpace, endEnter), len));

            // remove url from message
            //ms = ms.replaceFirst(url, "");

            alertDialogBuilder.setPositiveButton(getString(R.string.open), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {

                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(url));
                    startActivity(intent);
                }
            });
        }


        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    private void updateUi() {
        if (mAdsFreeError) {
            llAds.setVisibility(View.VISIBLE);
            purchase1.setVisibility(View.GONE);
            purchase2.setVisibility(View.GONE);
        } else if (mIsAdsFree) {
            llAds.setVisibility(View.GONE);
            purchase1.setVisibility(View.GONE);
            purchase2.setVisibility(View.GONE);
        } else {
            llAds.setVisibility(View.VISIBLE);
            purchase1.setVisibility(View.VISIBLE);
            purchase2.setVisibility(View.VISIBLE);
        }
    }

    // private boolean isFirstRun() {
    // boolean firstRun = PreferenceManager.getDefaultSharedPreferences(
    // context).getBoolean("firstRun", true);
    // //set first run
    // Editor editor = PreferenceManager.getDefaultSharedPreferences(context)
    // .edit();
    // editor.putBoolean("firstRun", false);
    // editor.commit();
    //
    // return firstRun;
    // }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.d(TAG, "onActivityResult(" + requestCode + "," + resultCode + ","
                + data);

        // Pass on the activity result to the helper for handling
        if (!mHelper.handleActivityResult(requestCode, resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data);
        } else {
            Log.d(TAG, "onActivityResult handled by IABUtil.");
        }
    }

    private void fillTiles() {
        // http://www.tehrantraffic.com/mapimages/web67.jpg
        tiles[0] = getResources().getIntArray(R.array.map_row1);
        tiles[1] = getResources().getIntArray(R.array.map_row2);
        tiles[2] = getResources().getIntArray(R.array.map_row3);
        tiles[3] = getResources().getIntArray(R.array.map_row4);
        tiles[4] = getResources().getIntArray(R.array.map_row5);
        tiles[5] = getResources().getIntArray(R.array.map_row6);
        tiles[6] = getResources().getIntArray(R.array.map_row7);
        tiles[7] = getResources().getIntArray(R.array.map_row8);
        tiles[8] = getResources().getIntArray(R.array.map_row9);
        tiles[9] = getResources().getIntArray(R.array.map_row10);
        tiles[10] = getResources().getIntArray(R.array.map_row11);
        tiles[11] = getResources().getIntArray(R.array.map_row12);
    }

    @Override
    protected void onResume() {
        super.onResume();
        doubleBackToExitPressedOnce = false;

        CloudMessage.checkPlayServices(this);
    }

    private void initForm() {
        inMap = findViewById(R.id.inMap);
        inNews = findViewById(R.id.inNews);
        inContact = findViewById(R.id.inContact);
        inAbout = findViewById(R.id.inAbout);

        ibPrev = (ImageButton) findViewById(R.id.ibPrev);
        ibNext = (ImageButton) findViewById(R.id.ibNext);
        ibRefresh = (ImageButton) findViewById(R.id.ibRefresh);
        ibPause = (ImageButton) findViewById(R.id.ibPause);
        ibBack = (ImageButton) findViewById(R.id.ibBack);
        ivRoadsHelp = (ImageView) findViewById(R.id.ivRoadsHelp);
        spState = (Spinner) findViewById(R.id.spState);

        nvMap = (NavigationView) findViewById(R.id.nvMap);
        nvMap.setOnNavigationListener(this);

        tivMap = (TouchImageView) findViewById(R.id.tivMap);
        tivMap.setMaxZoom(6f);
        tivMap.setOnTileListener(this);
        // tivMap.setImageDrawable(context.getResources().getDrawable(
        // R.drawable.logo));
        // tivMap.setScaleType(ScaleType.CENTER_INSIDE);

        tvError = (TextView) findViewById(R.id.tvError);

        ibRefresh.setVisibility(View.VISIBLE);
        ibNext.setVisibility(View.INVISIBLE);
        ibPause.setVisibility(View.INVISIBLE);
        ibBack.setVisibility(View.GONE);
        nvMap.setVisibility(View.GONE);
        ivRoadsHelp.setVisibility(View.GONE);
        spState.setVisibility(View.GONE);

        spState.setSelection(getState());
        spState.setOnItemSelectedListener(this);

        llAds = findViewById(R.id.llAds);
        purchase1 = findViewById(R.id.purchase1);
        purchase2 = findViewById(R.id.purchase2);
    }

    private int getState() {
        return getSharedPreferences(
                "TehranTrafficMap", 0).getInt("stateID", 7);
    }

    private void setState(int stateID) {
        SharedPreferences.Editor editor = getSharedPreferences(
                "TehranTrafficMap", 0).edit();
        editor.putInt("stateID", stateID);
        editor.commit();
    }

    private void switchView() {
        enableAllTabs();
        invisibleAllIncludes();

        switch (appState) {
            case Traffic:
                showTrafficMap();
                break;
            case Road:
                showRoadMap();
                break;
            case Zoom:
                showTrafficTile();
                break;
            case Plane:
                showTrafficPlane();
                break;
            case Metro:
                showMetroMap();
                break;
            case Brt:
                showBrtMap();
                break;
//            case News:
//                showNews();
//                break;
            case Contact:
                showContact();
                break;
            case About:
                showAbout();
                break;
        }
    }

    private void enableAllTabs() {
        findViewById(R.id.ibTabTraffic).setEnabled(true);
        findViewById(R.id.ibTabRoad).setEnabled(true);
        findViewById(R.id.ibTabPlane).setEnabled(true);
        findViewById(R.id.ibTabMetro).setEnabled(true);
        findViewById(R.id.ibTabBrt).setEnabled(true);
        //findViewById(R.id.ibTabNews).setEnabled(true);
        findViewById(R.id.ibTabContact).setEnabled(true);
        findViewById(R.id.ibTabAbout).setEnabled(true);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.ibPrev:
                ibPrev.setVisibility(Button.INVISIBLE);
                ibNext.setVisibility(Button.VISIBLE);
                loader.loadPrev();
                break;

            case R.id.ibNext:
                ibPrev.setVisibility(Button.VISIBLE);
                ibNext.setVisibility(Button.INVISIBLE);
                showTrafficMap();
                break;
            case R.id.ibRefresh:
                if (appState == ApplicationState.Traffic) {
                    if (loader == null || loader.isCancelled()
                            || loader.getStatus() == Status.FINISHED) {
                        loader = new DataLoader(MainActivity.this, tivMap, tvError);
                    }
                    loader.loadFile("newMap", "jpg", true);

                    ibPrev.setVisibility(Button.VISIBLE);
                } else if (appState == ApplicationState.Road) {
                    if (loader == null || loader.isCancelled()
                            || loader.getStatus() == Status.FINISHED) {
                        loader = new DataLoader(MainActivity.this, tivMap, tvError);
                    }
                    loader.loadRoad(getState(), true);
                }
                break;

            case R.id.ibBack:
                switchView();
                break;
            case R.id.ibTabTraffic:
                appState = ApplicationState.Traffic;
                switchView();
                break;
            case R.id.ibTabRoad:
                appState = ApplicationState.Road;
                switchView();
                break;
            case R.id.ibTabPlane:
                appState = ApplicationState.Plane;
                switchView();
                break;
            case R.id.ibTabMetro:
                appState = ApplicationState.Metro;
                switchView();
                break;
            case R.id.ibTabBrt:
                appState = ApplicationState.Brt;
                switchView();
                break;
//            case R.id.ibTabNews:
//                appState = ApplicationState.News;
//                switchView();
//                break;
            case R.id.ibTabContact:
                appState = ApplicationState.Contact;
                switchView();
                break;
            case R.id.ibTabAbout:
                appState = ApplicationState.About;
                switchView();
                break;
            case R.id.purchase1:
            case R.id.purchase2:
                // prePurchase();
                purchase();
                break;
        }
    }

    private void prePurchase() {
        List<String> skuList = new ArrayList<String>();
        skuList.add(SKU_ADS);

        try {
            Log.d(TAG, "Launching prepurchase flow for ads free app.");
            mHelper.queryInventoryAsync(true, skuList,
                    mQueryInventoryFinishedListener);
        } catch (Exception e) {
            e.printStackTrace();
            mAdsFreeError = true;
        }
    }

    private void purchase() {
        // if (!mHelper.subscriptionsSupported()) {
        // Toast.makeText(context,
        // "Subscriptions not supported on your device yet. Sorry!",
        // Toast.LENGTH_LONG).show();
        // return;
        // }

        String payload = getPayloadParam();

        // setWaitScreen(true);
        try {
            Log.d(TAG, "Launching purchase flow for ads free app.");
            mHelper.launchPurchaseFlow(this, SKU_ADS, RC_REQUEST,
                    mPurchaseFinishedListener, payload);
        } catch (Exception e) {
            e.printStackTrace();
            mAdsFreeError = true;
        }
    }

    private String getPayloadParam() {
        return Secure
                .getString(context.getContentResolver(), Secure.ANDROID_ID);
    }

    /**
     * Verifies the developer payload of a purchase.
     */
    boolean verifyDeveloperPayload(Purchase p) {
        String payload = p.getDeveloperPayload();

        return payload.equals(getPayloadParam());
    }

    @Override
    public void onTileClick(View v, int row, int col) {
        switch (v.getId()) {
            case R.id.tivMap:
                if (appState == ApplicationState.Traffic) {
                    if (row > 0 && row < 12 && col > 0 && col < 12)
                        if (tiles[row][col] != 0) {
                            currentTile = tiles[row][col];
                            currentRow = row;
                            currentCol = col;
                            appState = ApplicationState.Zoom;
                            switchView();
                            setNavigator();
                        }
                }
                break;
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case DialogInterface.BUTTON_POSITIVE:
                if (loader == null || loader.isCancelled()
                        || loader.getStatus() == Status.FINISHED) {
                    loader = new DataLoader(context, tivMap, tvError);
                }

                switch (appState) {
                    case Traffic:
                        loader.loadFile("newMap", "jpg", true);

                        if (loader.fileExist("oldMap"))
                            ibPrev.setVisibility(Button.VISIBLE);
                        break;
                    case Zoom:
                        loader.loadTile(currentTile, true);
                        break;
                    case Road:
                        loader.loadRoad(getState(), true);
                        break;
                }

                break;

            case DialogInterface.BUTTON_NEGATIVE:
                // Nothing to do!
                break;
        }
    }

    private void invisibleAllIncludes() {
        inMap.setVisibility(View.GONE);
        inNews.setVisibility(View.GONE);
        inContact.setVisibility(View.GONE);
        inAbout.setVisibility(View.GONE);
    }

    private void checkLastUpdate() {
        try {
            SharedPreferences settings = getSharedPreferences(
                    "TehranTrafficMap", 0);
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                    Locale.US);
            int interval = 5;
            Date lastUpdate = new Date();
            switch (appState) {
                case Traffic:
                    lastUpdate = df.parse(settings.getString("newMap", ""));
                    interval = 5;
                    break;
                case Zoom:
                    lastUpdate = df.parse(settings.getString("newTile"
                            + currentTile, ""));
                    interval = 5;
                    break;
                case Road:
                    lastUpdate = df.parse(settings.getString("newRoad"
                            + getState(), ""));
                    interval = 15;
                    break;
            }
            Date now = Calendar.getInstance().getTime();
            lastUpdate.setMinutes(lastUpdate.getMinutes() + interval);
            if ((long) lastUpdate.getTime() < (long) now.getTime()) {
                showUpdateDialog();
            }
        } catch (Exception e) {
            e.printStackTrace();
            showUpdateDialog();
        }
    }

    private void showUpdateDialog() {
        if (updateDialog == null || !updateDialog.isShowing()) {
            if (loader != null
                    && (loader.isCancelled() || loader.getStatus() == Status.PENDING)
                    && isConnected()) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                updateDialog = builder
                        .setMessage(getString(R.string.msg_updatemap))
                        .setPositiveButton(getString(R.string.msg_yes),
                                MainActivity.this)
                        .setNegativeButton(getString(R.string.msg_no),
                                MainActivity.this).show();
            } else {
                Log.i("test", "WTF!");
            }
        }
    }

    public void showTrafficMap() {
        appState = ApplicationState.Traffic;

        findViewById(R.id.inMap).setVisibility(View.VISIBLE);
        findViewById(R.id.tivMap).setVisibility(View.VISIBLE);


        if (loader == null || loader.isCancelled()
                || loader.getStatus() == Status.FINISHED) {
            loader = new DataLoader(MainActivity.this, tivMap, tvError);
        }
        loader.loadFile("newMap", "jpg", false);

        ibBack.setVisibility(View.GONE);
        nvMap.setVisibility(View.GONE);
        ivRoadsHelp.setVisibility(View.GONE);
        spState.setVisibility(View.GONE);

        ibRefresh.setVisibility(Button.VISIBLE);

        if (loader.fileExist("oldMap")) {
            ibPrev.setVisibility(Button.VISIBLE);
        } else {
            ibPrev.setVisibility(Button.INVISIBLE);
        }

        findViewById(R.id.ibTabTraffic).setEnabled(false);

        if (condition == null && firstRun)
            checkLastUpdate();
    }

    public void showRoadMap() {
        appState = ApplicationState.Road;

        findViewById(R.id.inMap).setVisibility(View.VISIBLE);

        ibPrev.setVisibility(View.GONE);
        ibNext.setVisibility(View.GONE);
        ibRefresh.setVisibility(View.VISIBLE);
        ibBack.setVisibility(View.GONE);
        nvMap.setVisibility(View.GONE);
        ivRoadsHelp.setVisibility(View.VISIBLE);
        spState.setVisibility(View.VISIBLE);

        if (loader == null || loader.isCancelled()
                || loader.getStatus() == Status.FINISHED) {
            loader = new DataLoader(context, tivMap, tvError);
        }
        loader.loadRoad(getState(), false);

        findViewById(R.id.ibTabRoad).setEnabled(false);

        checkLastUpdate();

    }

    public void showTrafficTile() {
        appState = ApplicationState.Zoom;

        findViewById(R.id.inMap).setVisibility(View.VISIBLE);

        ibPrev.setVisibility(View.GONE);
        ibNext.setVisibility(View.GONE);
        ibRefresh.setVisibility(View.GONE);

        ibBack.setVisibility(View.VISIBLE);
        nvMap.setVisibility(View.VISIBLE);
        ivRoadsHelp.setVisibility(View.GONE);
        spState.setVisibility(View.GONE);

        if (loader == null || loader.isCancelled()
                || loader.getStatus() == Status.FINISHED) {
            loader = new DataLoader(context, tivMap, tvError);
        }
        loader.loadTile(currentTile, false);

        findViewById(R.id.ibTabTraffic).setEnabled(false);

        checkLastUpdate();

    }

    private void setNavigator() {
        // check up
        boolean up = currentRow > 0 && tiles[currentRow - 1][currentCol] > 0;
        // check down
        boolean down = currentRow < 11 && tiles[currentRow + 1][currentCol] > 0;
        // check left
        boolean left = currentCol > 0 && tiles[currentRow][currentCol - 1] > 0;
        // check right
        boolean right = currentCol < 11
                && tiles[currentRow][currentCol + 1] > 0;

        nvMap.setButtonsEnabled(down, left, up, right);

    }

    private void showTrafficPlane() {
        appState = ApplicationState.Plane;

        findViewById(R.id.inMap).setVisibility(View.VISIBLE);

        ibPrev.setVisibility(Button.INVISIBLE);
        ibNext.setVisibility(Button.INVISIBLE);
        ibRefresh.setVisibility(Button.INVISIBLE);

        nvMap.setVisibility(Button.GONE);
        ibBack.setVisibility(Button.GONE);
        ivRoadsHelp.setVisibility(View.GONE);
        spState.setVisibility(View.GONE);

        loader.loadPlane();

        findViewById(R.id.ibTabPlane).setEnabled(false);
    }

    private void showMetroMap() {
        appState = ApplicationState.Metro;

        findViewById(R.id.inMap).setVisibility(View.VISIBLE);

        ibPrev.setVisibility(Button.INVISIBLE);
        ibNext.setVisibility(Button.INVISIBLE);
        ibRefresh.setVisibility(Button.INVISIBLE);

        nvMap.setVisibility(Button.GONE);
        ibBack.setVisibility(Button.GONE);
        ivRoadsHelp.setVisibility(View.GONE);
        spState.setVisibility(View.GONE);

        loader.loadMetro();

        findViewById(R.id.ibTabMetro).setEnabled(false);
    }

    private void showBrtMap() {
        appState = ApplicationState.Brt;

        findViewById(R.id.inMap).setVisibility(View.VISIBLE);

        ibPrev.setVisibility(Button.INVISIBLE);
        ibNext.setVisibility(Button.INVISIBLE);
        ibRefresh.setVisibility(Button.INVISIBLE);

        nvMap.setVisibility(Button.GONE);
        ibBack.setVisibility(Button.GONE);
        ivRoadsHelp.setVisibility(View.GONE);
        spState.setVisibility(View.GONE);

        loader.loadBrt();

        findViewById(R.id.ibTabBrt).setEnabled(false);
    }

    private void showContact() {

        findViewById(R.id.ibTabContact).setEnabled(false);
        findViewById(R.id.inContact).setVisibility(View.VISIBLE);
    }

    private void showAbout() {
        findViewById(R.id.ibTabAbout).setEnabled(false);
        findViewById(R.id.inAbout).setVisibility(View.VISIBLE);
    }

    @Override
    public void onBackPressed() {
        if (appState == ApplicationState.Zoom)
            showTrafficMap();
        else {

            if (doubleBackToExitPressedOnce) {
                super.onBackPressed();
                return;
            }
            doubleBackToExitPressedOnce = true;
            Toast.makeText(this, R.string.msg_exit, Toast.LENGTH_SHORT).show();

            Timer t = new Timer();
            t.schedule(new TimerTask() {

                @Override
                public void run() {
                    doubleBackToExitPressedOnce = false;
                }
            }, 2500);
        }

    }

    //private void showNews() {
    //    findViewById(R.id.ibTabNews).setEnabled(false);
    //    findViewById(R.id.inNews).setVisibility(View.VISIBLE);
    //
    //}

    @Override
    protected void onStop() {
        super.onStop();

        EasyTracker.getInstance().activityStop(this); // Google Analytic
    }

    @Override
    public void onDownClick(View v) {
        currentRow++;
        currentTile = tiles[currentRow][currentCol];
        switchView();
        setNavigator();
    }

    @Override
    public void onLeftClick(View v) {
        currentCol--;
        currentTile = tiles[currentRow][currentCol];
        switchView();
        setNavigator();
    }

    @Override
    public void onUpClick(View v) {
        currentRow--;
        currentTile = tiles[currentRow][currentCol];
        switchView();
        setNavigator();
    }

    @Override
    public void onRightClick(View v) {
        currentCol++;
        currentTile = tiles[currentRow][currentCol];
        switchView();
        setNavigator();

    }

    public boolean isConnected() {
        ConnectivityManager connectivityManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager
                .getActiveNetworkInfo();
        return activeNetworkInfo != null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mHelper != null)
            try {
                mHelper.dispose();
            } catch (Exception e) {
                e.printStackTrace();
                mAdsFreeError = true;
            }
        mHelper = null;
    }


    enum ApplicationState {
        Traffic, Road, Zoom, Plane, Metro, Brt, News, Contact, About
    }

}
package com.hokaze.exaltedroller;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.util.ArrayList;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private AdView mAdView;
    private FirebaseAnalytics mFirebaseAnalytics;

    Button bRoll, bTricks;
    EditText etDice;
    TextView tvResults, tvSuccess;
    CheckBox checkColours, checkTens, checkBotches, checkEx3;
    Random ranDice = new Random();
    ArrayList<String> trickList = new ArrayList<String>();
    boolean[] trickValues = new boolean[11]; // ugh, magic number for list size, need to declare early for save/restore
    SpannableStringBuilder builder = new SpannableStringBuilder("Results: ");
    String simple = "Results: ";
    SpannableStringBuilder successStr = new SpannableStringBuilder("Successes: ");
    int diceCount = 0, targetNumber = 7, doubleNumber = 11;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialise ads api at launch to minimise latency of requests
        //MobileAds.initialize(getApplicationContext(), "ca-app-pub-3891762784020205/7005607976");

        // Obtain the FirebaseAnalytics instance.
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        bRoll=(Button)findViewById(R.id.rollButton);
        bTricks=(Button)findViewById(R.id.tricksButton);
        etDice=(EditText)findViewById(R.id.d10s);
        tvResults=(TextView)findViewById(R.id.resultsView);
        tvResults.setMovementMethod(new ScrollingMovementMethod()); // scrollable results
        tvSuccess=(TextView)findViewById(R.id.successView);
        checkColours=(CheckBox)findViewById(R.id.colourHighlights);
        checkTens=(CheckBox)findViewById(R.id.doubleTens);
        checkBotches=(CheckBox)findViewById(R.id.botchesSubtract);
        checkEx3=(CheckBox)findViewById(R.id.enableEx3);

        AdView mAdView = (AdView) findViewById(R.id.adView);
        // Deploy test ads to emulator and test physical device, other devices will get real ads
        // (Don't want to accidentally click my own ads and get suspended!)
        AdRequest adRequest = new AdRequest.Builder()
                //.addTestDevice(AdRequest.DEVICE_ID_EMULATOR) // All emulators
                .addTestDevice("9E352341EE24936E89C7621696793500")  // My Phone as test device
                .build();
        //AdRequest adRequest = new AdRequest.Builder().build(); // Actual ads
        mAdView.loadAd(adRequest);

        trickList.add("Double 9s and above");
        trickList.add("Double 8s and above");
        trickList.add("Double 7s and above");
        trickList.add("Exploding 10s");
        trickList.add("Reroll 6s once");
        trickList.add("Reroll 5s once");
        trickList.add("Reroll 1s once");
        trickList.add("Reroll non-successes once");
        trickList.add("6s and above are successes");
        trickList.add("5s and above are successes");
        trickList.add("4s and above are successes");

        // Convert arraylist into charsequence for dialog checkbox and store the values
        final CharSequence[] dialogList = trickList.toArray(new CharSequence[trickList.size()]);
        trickValues = new boolean[dialogList.length]; // checkbox persists when dialog is closed

        // Highlights all text for fast deletion when you select the d10 text box
        etDice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                etDice.setText(etDice.getText());
                etDice.selectAll();
            }
        });

        // Roll button pressed
        bRoll.setOnClickListener(new View.OnClickListener() {

            // Roll dice in function so it can be called from onClick and the 1000+ dice alertdialog
            public void rollDice() {

                // Temporarily lock screen rotation to current orientation, as rotating during the
                //  progress dialog/thread run() can cause crashes.
                // UPDATE: should now work properly with tablets
                int rotation = getWindowManager().getDefaultDisplay().getRotation();
                int tempOrientation = getResources().getConfiguration().orientation;
                int orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                switch(tempOrientation)
                {
                    case Configuration.ORIENTATION_LANDSCAPE:
                        if(rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_90)
                            orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                        else
                            orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                        break;
                    case Configuration.ORIENTATION_PORTRAIT:
                        if(rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_270)
                            orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                        else
                            orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                }
                setRequestedOrientation(orientation);

                // Display loading spinner, mostly used for larger numbers of dice
                final ProgressDialog loading = new ProgressDialog(MainActivity.this);
                loading.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                loading.setMessage("Rolling. Please wait...");
                loading.setIndeterminate(true);
                loading.setCanceledOnTouchOutside(false);
                loading.show();


                // Sort out the strings and dice rolling in a seperate thread
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            int successes = 0, botches = 0;

                            // Prepare results view
                            if (checkColours.isChecked() == true) {
                                // Creating a new SSB and letting the garbage collector discard the old one
                                // seems to be faster than using .clear() on large data. This way fixes
                                // the speed issue where rolling 1000+ dice would then cause a delay on the next
                                // roll, causing even small rolls to require the loading screen.
                                builder = new SpannableStringBuilder("Results: ");
                                builder.clear();
                                builder.append("Results: ");
                            }
                            else {
                                simple = "Results: ";
                            }

                            // Determine which numbers have success, doubles, rerolls, etc
                            targetNumber = 7;
                            doubleNumber = 11; // cannot get double successes unless double 10s/9s/8s/7s in effect
                            ArrayList<Integer> rerollList = new ArrayList<Integer>();
                            boolean rerollAllowed = true; // Set to false for dice created by non-exploding reroll dice tricks
                            if (checkTens.isChecked()) {doubleNumber = 10;}
                            if (checkEx3.isChecked()) {
                                // Double 9s/8s/7s
                                if (trickValues[0] == true) {doubleNumber = 9;}
                                if (trickValues[1] == true) {doubleNumber = 8;}
                                if (trickValues[2] == true) {doubleNumber = 7;}
                                // Exploding 10s, reroll 6s/5s/1s
                                if (trickValues[3] == true) {rerollList.add(10);}
                                if (trickValues[4] == true) {rerollList.add(6);}
                                if (trickValues[5] == true) {rerollList.add(5);}
                                if (trickValues[6] == true) {rerollList.add(1);}
                                // TN 6s/5s/4s
                                if (trickValues[8] == true) {targetNumber = 6;}
                                if (trickValues[9] == true) {targetNumber = 5;}
                                if (trickValues[10] == true) {targetNumber = 4;}
                                // Reroll non-successes
                                if (trickValues[7] == true) {
                                    for (int i = 1; i < targetNumber; ++i) {
                                        // If any of the above rerolls are enabled, the number is added to the list
                                        // twice, but doesn't trigger 2d10 extra, only 1d10
                                        rerollList.add(i);
                                    }
                                }
                            }

                            // Loop through dice rolled
                            for (int i = 0; i < diceCount; ++i){
                                int randomInt = ranDice.nextInt(10)+1;
                                boolean bold = false;

                                // Success
                                if (randomInt >= targetNumber) {
                                    ++successes;

                                    // Check if the number has double successes
                                    if (randomInt >= doubleNumber) {
                                        ++successes;
                                    }
                                }

                                // Fail
                                else {
                                    // Botch
                                    if (randomInt == 1) {
                                        ++botches;

                                        // Some Systems have botches subtract successes
                                        if (checkBotches.isChecked()) {
                                            --successes;
                                        }
                                    }
                                }

                                // Check for exploding dice or rerolls
                                if (rerollAllowed == true && rerollList.size() > 0) {
                                    // Re-roll with enhanced for loop for slightly faster performance
                                    for (Integer d : rerollList) {
                                        if (randomInt == d) {
                                            --i;
                                            bold = true;
                                            // Only 10s may explode ad infinatum
                                            if (randomInt != 10) {
                                                rerollAllowed = false;
                                            }
                                        }
                                    }
                                }
                                else {
                                    // Even if d10 was generated by a reroll once roll, 10s can always explode if enabled
                                    if (randomInt == 10 && rerollList.contains(10)) {
                                        --i;
                                        bold = true;
                                    }
                                    rerollAllowed = true;
                                }

                                // Display results with coloured text for botch/success/doubles and bold for re-rolls
                                if (checkColours.isChecked()) {
                                    SpannableString resultStr = new SpannableString(String.valueOf(randomInt));

                                    builder.append(resultStr);
                                    if (bold == true) {
                                        builder.append("r");
                                    }
                                    builder.append(" ");

                                }
                                else {
                                    simple += String.valueOf(randomInt);
                                    if (bold == true) {
                                        simple += "r"; // indicate rerolls without bold text
                                    }
                                    simple += " ";
                                }
                            }


                            // Alternative formatting for results by regexing the spannablestringbuilder
                            if (checkColours.isChecked()) {
                                regexResults();
                            }


                            // Handle success/botch formatting
                            successStr = new SpannableStringBuilder();
                            if (successes < 1 && botches > 0) {
                                // Display number of botches in BOLD
                                successStr.append("Botches: ");
                                SpannableString botchStr = new SpannableString(String.valueOf(botches));
                                botchStr.setSpan(new StyleSpan(Typeface.BOLD), 0, botchStr.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                                // If using coloured formatting, display in RED to make botches (instead of successes) more obvious
                                if (checkColours.isChecked()) {
                                    botchStr.setSpan(new ForegroundColorSpan(Color.rgb(200, 50, 50)), 0, botchStr.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                                }
                                successStr.append(botchStr);
                            }
                            else {
                                // Display number of successes in BOLD
                                successStr.append("Successes: ");
                                SpannableString succStr = new SpannableString(String.valueOf(successes));
                                succStr.setSpan(new StyleSpan(Typeface.BOLD), 0, succStr.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                                // If using coloured formatting, display in GREEN if successes, or BLUE if many successes
                                if (checkColours.isChecked()) {
                                    // Should probably be RED if we don't botch but get 0 successes...
                                    if (successes < 1) {
                                        succStr.setSpan(new ForegroundColorSpan(Color.rgb(200, 50, 50)), 0, succStr.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                                    }
                                    else {
                                        if (successes >= diceCount / 2) {
                                            succStr.setSpan(new ForegroundColorSpan(Color.rgb(50, 50, 200)), 0, succStr.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                                        }
                                        else {
                                            succStr.setSpan(new ForegroundColorSpan(Color.rgb(50, 200, 50)), 0, succStr.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                                        }
                                    }
                                }
                                successStr.append(succStr);
                            }

                            // UPDATE UI
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    // Use colours and formatted text if enabled
                                    if (checkColours.isChecked()) {
                                        tvResults.setText(builder, TextView.BufferType.SPANNABLE);
                                    }
                                    else {
                                        tvResults.setText(simple, TextView.BufferType.SPANNABLE);
                                    }
                                    // Reset scroller to top, fixes bug where scrolling down on a large dice roll then doing
                                    // a small roll would cause the results to disappear unless you scrolled up
                                    tvResults.scrollTo(0,0);

                                    // Set the success/botch display
                                    tvSuccess.setText(successStr);
                                }
                            });

                        } catch (Exception e) {

                        }
                        // Close the loading dialog and unlock orientation, allowing rotations again
                        loading.dismiss();
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                    }
                }).start();

            }

            // Override default onClick event
            @Override
            public void onClick(View v) {
                diceCount = 0;
                final boolean[] inDialog = {false};
                // Prevent crashes in case textbox contains non-number
                try {
                    diceCount = Integer.parseInt(etDice.getText().toString());
                }
                catch (NumberFormatException nfe) {
                    // do nothing
                }

                // User is taking the piss with dice and an arbitrary dice cap is enforced
                if (diceCount > 9999) {
                    inDialog[0] = true;
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Your dice are blotting out the sun!")
                            .setMessage("Well, you're eager aren't you? Sorry, but the dice cap is set at 9,999.")
                            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    // do nothing
                                }
                            })
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .show();
                }

                // If rolling a LOT of dice, show a warning and possibly prevent the roll
                else if (diceCount > 999) {
                    inDialog[0] = true;
                    final boolean[] rolling = {false};
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("That's a LOT of d10s!")
                            .setMessage("Are you sure you want to roll 1000+ dice? This may cause lag on some devices.")
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    // Roll dice despite large number, but show a loading spinner
                                    //rollDice(true);
                                    rolling[0] = true;
                                }
                            })
                            .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    // Do nothing
                                }
                            })
                            .setOnDismissListener(new DialogInterface.OnDismissListener() {

                                @Override
                                public void onDismiss(DialogInterface dialog) {
                                    if (rolling[0]) {
                                        rollDice();
                                    }

                                }
                            })
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .show();
                }

                // Roll with <1000 dice count, so no loading display
                if (inDialog[0] == false) {
                    rollDice();
                }
            }
        });

        // Dice tricks button pressed -> open checkbox dialog
        bTricks.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final AlertDialog.Builder builderDialog = new AlertDialog.Builder(MainActivity.this);
                builderDialog.setTitle("Select Dice Tricks");

                // Setup dialog using setMutliChoiceItem method to enable checkboxes
                builderDialog.setMultiChoiceItems(dialogList, trickValues, new DialogInterface.OnMultiChoiceClickListener() {
                            // Set on click of some options to auto-enable others: no functional change but makes
                            // the UI and the results more obvious to users
                            @Override
                            public void onClick(DialogInterface dialog, int whichButton, boolean isChecked) {
                                ListView list = ((AlertDialog) dialog).getListView();
                                // Disabling double 9s will disable doubles 8s and 7s in the UI
                                if (whichButton == 0) {
                                    if (list.isItemChecked(0)) {
                                        // do nothing
                                    }
                                    else {
                                        trickValues[1] = false;
                                        list.setItemChecked(1, false);
                                        trickValues[2] = false;
                                        list.setItemChecked(2, false);
                                    }
                                }
                                // Setting double 8s and above sets double 9s in the UI
                                if (whichButton == 1) {
                                    if (list.isItemChecked(1)) {
                                        trickValues[0] = true;
                                        list.setItemChecked(0, true);
                                    }
                                    // Disabling double 8s will disable double 9s if applicable
                                    else {
                                        trickValues[2] = false;
                                        list.setItemChecked(2, false);
                                    }
                                }
                                // Setting double 7s and above sets double 8s and 9s in the UI
                                if (whichButton == 2) {
                                    if (list.isItemChecked(2)) {
                                        trickValues[0] = true;
                                        list.setItemChecked(0, true);
                                        trickValues[1] = true;
                                        list.setItemChecked(1, true);
                                    }
                                }
                                // Disabling TN 6 will disable TN 5 and TN 4 in the UI
                                if (whichButton == 8) {
                                    if (list.isItemChecked(8)) {
                                        // do nothing
                                    }
                                    else {
                                        trickValues[9] = false;
                                        list.setItemChecked(9, false);
                                        trickValues[10] = false;
                                        list.setItemChecked(10, false);
                                    }
                                }
                                // Setting TN 5s and above sets TN 6
                                if (whichButton == 9) {
                                    if (list.isItemChecked(9)) {
                                        trickValues[8] = true;
                                        list.setItemChecked(8, true);
                                    }
                                    // Disabling TN 5s will disable TN 4 if applicable
                                    else {
                                        trickValues[10] = false;
                                        list.setItemChecked(10, false);
                                    }
                                }
                                // Setting TN 4s and above sets TN 5 and TN 6
                                if (whichButton == 10) {
                                    if (list.isItemChecked(10)) {
                                        trickValues[8] = true;
                                        list.setItemChecked(8, true);
                                        trickValues[9] = true;
                                        list.setItemChecked(9, true);
                                    }
                                }
                            }
                        });

                // Confirm
                builderDialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                                ListView list = ((AlertDialog) dialog).getListView();
                                // make selected item in the comma seprated string
                                StringBuilder stringBuilder = new StringBuilder();
                                for (int i = 0; i < list.getCount(); i++) {
                                    boolean checked = list.isItemChecked(i);

                                    if (checked) {
                                        if (stringBuilder.length() > 0) stringBuilder.append(",");
                                        stringBuilder.append(list.getItemAtPosition(i));

                                    }
                                }
                            }
                        });

                // Finally create and show the dialog
                AlertDialog alert = builderDialog.create();
                alert.show();
            }
        });
    }

    // Alternative formatting for results by regexing the spannablestringbuilder
    public void regexResults() {
        // Green/Blue: successes/doubles
        String regex = String.format("[%d-9]|10", targetNumber); // matches range of TN to 9 OR 10
        Pattern ptn = Pattern.compile(regex);
        Matcher matcher = ptn.matcher(builder.toString());
        while (matcher.find()) {
            ForegroundColorSpan spanC = new ForegroundColorSpan(Color.rgb(50, 200, 50));
            if (Integer.parseInt(matcher.group(0)) >= doubleNumber) {
                spanC = new ForegroundColorSpan(Color.rgb(50, 50, 200)); // blue if 2 successes
            }
            builder.setSpan(spanC, matcher.start(), matcher.end()+1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); // +1 to include "r" if appropriate
        }
        // Red: botches
        regex = "1r|1 ";
        ptn = Pattern.compile(regex);
        matcher = ptn.matcher(builder.toString());
        while (matcher.find()) {
            ForegroundColorSpan spanC = new ForegroundColorSpan(Color.rgb(200, 50, 50));
            builder.setSpan(spanC, matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        // Italics: rerolls
        regex = "[1-9]r|10r";
        ptn = Pattern.compile(regex);
        matcher = ptn.matcher(builder.toString());
        while (matcher.find()) {
            StyleSpan spanI = new StyleSpan(Typeface.ITALIC);
            builder.setSpan(spanI, matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }


    // Need to save and restore state below so that the dice tricks list and results textview
    // are not wiped clean upon screen rotation, which kinda kills usability somewhat

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        // Save UI state changes to the savedInstanceState.
        // This bundle will be passed to onCreate if the process is
        // killed and restarted.
        savedInstanceState.putBooleanArray("DiceTrickValues", trickValues);
        savedInstanceState.putString("ResultsString", tvResults.getText().toString()); // plain string, no formatting
        savedInstanceState.putInt("TargetNumber", targetNumber);
        savedInstanceState.putInt("DoubleNumber", doubleNumber);
        savedInstanceState.putString("SuccessString", Html.toHtml(successStr));
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // Restore UI state from the savedInstanceState.
        // This bundle has also been passed to onCreate.
        trickValues = savedInstanceState.getBooleanArray("DiceTrickValues");
        targetNumber = savedInstanceState.getInt("TargetNumber");
        doubleNumber = savedInstanceState.getInt("DoubleNumber");
        // Make successes/botches display the number in bold on reload
        Spanned spanSuccess = Html.fromHtml(savedInstanceState.getString("SuccessString"));
        successStr = new SpannableStringBuilder();
        successStr.append(spanSuccess);
        // Load results in plain or formatted text
        if (checkColours.isChecked()) {
            builder = new SpannableStringBuilder();
            builder.append(savedInstanceState.getString("ResultsString"));
            regexResults();
            tvResults.setText(builder);
            tvSuccess.setText(successStr);
        }
        else {
            tvResults.setText(savedInstanceState.getString("ResultsString"));
            tvSuccess.setText(successStr.toString());
        }
    }
}
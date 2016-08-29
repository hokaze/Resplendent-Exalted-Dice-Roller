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

import java.util.ArrayList;
import java.util.Random;

public class MainActivity extends AppCompatActivity {
    Button bRoll, bTricks;
    EditText etDice;
    TextView tvResults, tvSuccess;
    CheckBox checkColours, checkTens, checkBotches, checkEx3;
    static Random ranDice = new Random();
    ArrayList<String> trickList = new ArrayList<String>();
    boolean[] trickValues = new boolean[12]; // ugh, magic number for list size, need to declare early for save/restore
    final SpannableStringBuilder builder = new SpannableStringBuilder("Results: ");
    String simple = "Results: ";
    final SpannableStringBuilder successStr = new SpannableStringBuilder("Successes: ");
    int diceCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialise ads api at launch to minimise latency of requests
        MobileAds.initialize(getApplicationContext(), "ca-app-pub-3891762784020205/7005607976");

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
                //etDice.clearFocus();
                //etDice.requestFocus();
                //etDice.clearFocus();
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
                                builder.clear();
                                builder.append("Results: ");
                            }
                            else {
                                simple = "Results: ";
                            }

                            // Determine which numbers have success, doubles, rerolls, etc
                            int targetNumber = 7;
                            int doubleNumber = 11; // cannot get double successes unless double 10s/9s/8s/7s in effect
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
                                int colour = Color.BLACK;
                                boolean bold = false;

                                // Success
                                if (randomInt >= targetNumber) {
                                    ++successes;
                                    colour = Color.rgb(50, 200, 50); // A green that isn't as painfully bright as Color.GREEN

                                    // Check if the number has double successes
                                    if (randomInt >= doubleNumber) {
                                        ++successes;
                                        colour = Color.rgb(50, 50, 200); // Blue
                                    }
                                }

                                // Fail
                                else {
                                    // Botch
                                    if (randomInt == 1) {
                                        ++botches;
                                        colour = Color.rgb(200, 50, 50); // Red, less blinding than Color.RED
                                        // Some Systems have botches subtract successes
                                        if (checkBotches.isChecked()) {
                                            --successes;
                                        }
                                    }
                                }

                                // Check for exploding dice or rerolls
                                if (rerollAllowed == true && rerollList.size() > 0) {
                                    for (int j = 0; j < rerollList.size(); ++j) {
                                        // Primitive infinite reroller
                                        if (randomInt == rerollList.get(j)) {
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

                                // Display results with new coloured text for botch/success/doubles
                                if (checkColours.isChecked()) {
                                    SpannableString resultStr = new SpannableString(String.valueOf(randomInt));
                                    resultStr.setSpan(new ForegroundColorSpan(colour), 0, resultStr.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                                    // If d10 triggered a reroll or explosion, use bold text
                                    if (bold == true) {
                                        resultStr.setSpan(new StyleSpan(Typeface.BOLD), 0, resultStr.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                                    }
                                    builder.append(resultStr);
                                    builder.append(" ");
                                }
                                else {
                                    simple += String.valueOf(randomInt);
                                    simple += " ";
                                }
                            }

                            // Handle success/botch formatting
                            successStr.clear();
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
                                    if (successes >= diceCount / 2) {
                                        succStr.setSpan(new ForegroundColorSpan(Color.rgb(50, 50, 200)), 0, succStr.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                                    }
                                    else {
                                        succStr.setSpan(new ForegroundColorSpan(Color.rgb(50, 200, 50)), 0, succStr.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
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
                    //Toast.makeText(MainActivity.this, "Please enter a valid number of d10s to roll.", Toast.LENGTH_SHORT).show();
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
                            public void onClick(DialogInterface dialog, int whichButton, boolean trickValues) {
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
        if (checkColours.isChecked()) {
            savedInstanceState.putString("SpanResultsString", Html.toHtml(builder)); // convert to html so we can save formatting
        }
        savedInstanceState.putString("SuccessString", Html.toHtml(successStr));
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // Restore UI state from the savedInstanceState.
        // This bundle has also been passed to onCreate.
        trickValues = savedInstanceState.getBooleanArray("DiceTrickValues");
        // Load results in plan or formatted text
        tvResults.setText(savedInstanceState.getString("ResultsString"));
        if (checkColours.isChecked()) {
            Spanned spanResults = Html.fromHtml(savedInstanceState.getString("SpanResultsString"));
            builder.clear();
            builder.append(spanResults); // need this or multiple coloured rotations in a row lose data
            tvResults.setText(spanResults);
        }
        // Make successes/botches display the number in bold on reload
        Spanned spanSuccess = Html.fromHtml(savedInstanceState.getString("SuccessString"));
        successStr.clear();
        successStr.append(spanSuccess);
        tvSuccess.setText(successStr);
    }
}
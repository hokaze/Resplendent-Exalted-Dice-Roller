package com.hokaze.exaltedroller;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.firebase.analytics.FirebaseAnalytics;

import java.util.ArrayList;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private FirebaseAnalytics mFirebaseAnalytics;

    Button bRoll, bTricks;
    EditText etDice;
    TextView tvResults, tvSuccess;
    CheckBox checkColours, checkTens, checkBotches, checkEx3,
            checkExplode10s, checkReroll6s, checkReroll5s, checkReroll1s, checkRerollNonSuccesses;
    Spinner tnSpinner, doublesSpinner;
    Random ranDice = new Random();
    boolean[] trickValues = new boolean[5]; // all tricks disable to false on init
    SpannableStringBuilder builder = new SpannableStringBuilder("Results: ");
    String simple = "Results: ";
    SpannableStringBuilder successStr = new SpannableStringBuilder("Successes: ");
    int diceCount = 0, defaultTargetNumber = 7, defaultDoubleNumber = 10,
            targetNumber = defaultTargetNumber, doubleNumber = defaultDoubleNumber,
            trickTargetNumber = defaultTargetNumber, trickDoubleNumber = defaultDoubleNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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

        // Highlights all text for fast deletion when you select the d10 text box
        etDice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                etDice.setText(etDice.getText());
                etDice.selectAll();
            }
        });

        // Change values when the checkboxes are toggled, whether by the user or automatically from dice trick options
        // instead of waiting to roll to change target numbers, double numbers, etc.
        // Should address issues where the app seems to "forget" the settings assigned to it via dice tricks
        checkTens.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,boolean isChecked) {
                if (checkTens.isChecked() == false) { doubleNumber = 11; trickDoubleNumber = 11; }
                else {
                    if (checkEx3.isChecked()) {
                        if (trickDoubleNumber <= defaultDoubleNumber) { doubleNumber = trickDoubleNumber; }
                        else { doubleNumber = defaultDoubleNumber; trickDoubleNumber = defaultDoubleNumber; }
                    }
                    else { doubleNumber = defaultDoubleNumber; }
                }
            }
        });

        checkEx3.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,boolean isChecked) {
                if (checkEx3.isChecked() == false) {
                    targetNumber = defaultTargetNumber;
                    if (checkTens.isChecked()) { doubleNumber = defaultDoubleNumber; }
                    else { doubleNumber = 11; }
                }
                else {
                    targetNumber = trickTargetNumber;
                    if (trickDoubleNumber <= defaultDoubleNumber) { checkTens.setChecked(true); }
                }
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
                            targetNumber = defaultTargetNumber;
                            doubleNumber = defaultDoubleNumber; // cannot get double successes unless double 10s/9s/8s/7s in effect
                            ArrayList<Integer> rerollList = new ArrayList<Integer>();
                            boolean rerollAllowed = true; // Set to false for dice created by non-exploding reroll dice tricks
                            if (!checkTens.isChecked()) {doubleNumber = 11;}
                            if (checkEx3.isChecked()) {
                                // Alternative Target Number
                                targetNumber = trickTargetNumber;
                                // Double Successes enabled
                                doubleNumber = trickDoubleNumber;
                                // Exploding 10s
                                if (trickValues[0] == true) {rerollList.add(10);}
                                // Reroll 6s/5s/1s
                                if (trickValues[1] == true) {rerollList.add(6);}
                                if (trickValues[2] == true) {rerollList.add(5);}
                                if (trickValues[3] == true) {rerollList.add(1);}
                                // Reroll non-successes
                                if (trickValues[4] == true) {
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

                // Inflate and set the custom layout for the dialog
                // Pass null as the parent view because its going in the dialog layout
                LayoutInflater inflater = getLayoutInflater();
                View dialogView = inflater.inflate(R.layout.dicetricks_dialog, null);
                builderDialog.setView(dialogView);

                // Use dialogView.findViewById so we look inside dicetricks_dialog.xml instead of activity_main.xml
                tnSpinner=(Spinner)dialogView.findViewById(R.id.targetNumberSpinner);
                doublesSpinner=(Spinner)dialogView.findViewById(R.id.doubleSuccessesSpinner);
                checkExplode10s=(CheckBox)dialogView.findViewById(R.id.explodingTensCheckbox);
                checkReroll6s=(CheckBox)dialogView.findViewById(R.id.reroll6sCheckbox);
                checkReroll5s=(CheckBox)dialogView.findViewById(R.id.reroll5sCheckbox);
                checkReroll1s=(CheckBox)dialogView.findViewById(R.id.reroll1sCheckbox);
                checkRerollNonSuccesses=(CheckBox)dialogView.findViewById(R.id.rerollNonSuccessesCheckbox);

                // Set spinners and checkboxes to the correct values
                tnSpinner.setSelection(trickTargetNumber-1);
                doublesSpinner.setSelection(trickDoubleNumber-1);
                checkExplode10s.setChecked(trickValues[0]);
                checkReroll6s.setChecked(trickValues[1]);
                checkReroll5s.setChecked(trickValues[2]);
                checkReroll1s.setChecked(trickValues[3]);
                checkRerollNonSuccesses.setChecked(trickValues[4]);

                // Confirm
                builderDialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // Keep trick numbers seperate from main, so we can restore them easily
                                // when tricks are toggled on and off
                                trickTargetNumber = tnSpinner.getSelectedItemPosition()+1;
                                trickDoubleNumber = doublesSpinner.getSelectedItemPosition()+1;

                                // Get checkbox states
                                trickValues[0] = checkExplode10s.isChecked();
                                trickValues[1] = checkReroll6s.isChecked();
                                trickValues[2] = checkReroll5s.isChecked();
                                trickValues[3] = checkReroll1s.isChecked();
                                trickValues[4] = checkRerollNonSuccesses.isChecked();

                                // UX: Toggle double 10s checkbox automatically to off if set to No doubles
                                if (trickDoubleNumber > 10) { checkTens.setChecked(false); }
                                else { checkTens.setChecked(true); }

                                // UX: If they've clicked on Dice Tricks, they probably want them enabled, so tick the checkbox for the user automatically
                                checkEx3.setChecked(true);
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
        savedInstanceState.putInt("TrickTargetNumber", trickTargetNumber);
        savedInstanceState.putInt("TrickDoubleNumber", trickDoubleNumber);
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
        trickTargetNumber = savedInstanceState.getInt("TrickTargetNumber");
        trickDoubleNumber = savedInstanceState.getInt("TrickDoubleNumber");
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
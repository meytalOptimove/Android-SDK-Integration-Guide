package com.optimove.sdk.demo;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.design.widget.BaseTransientBottomBar;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TextInputEditText;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.optimove.sdk.optimove_sdk.device_state.OptimoveDeviceRequirement;
import com.optimove.sdk.optimove_sdk.main.Optimove;
import com.optimove.sdk.optimove_sdk.main.OptimoveSuccessStateListener;
import com.optimove.sdk.optimove_sdk.optitrack.EventSentResult;
import com.optimove.sdk.optimove_sdk.optitrack.OptimoveEvent;

public class MainActivity extends AppCompatActivity {

    private static final String SP_NAME = "demo_sp";

    private View rootView;
    private MyOptimoveStateListener optimoveStateListener;
    private boolean isOptimoveAvailable = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        optimoveStateListener = new MyOptimoveStateListener();
        rootView = findViewById(R.id.rootView);
        setupCustomEvents();
        setupSetUserId();
        setupTemplateTestMode();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Optimove.getInstance().registerSuccessStateListener(optimoveStateListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        //To prevent memory leaks when device configurations change, don't forget to unregister!
        Optimove.getInstance().unregisterSuccessStateListener(optimoveStateListener);
    }

    private void setupCustomEvents() {

        Button customEventSubmit = findViewById(R.id.submitCustomEvent);
        customEventSubmit.setOnClickListener(new CustomEventSubmitter());
    }

    private void setupSetUserId() {

        Button userIdSubmit = findViewById(R.id.submitSetUserId);
        userIdSubmit.setOnClickListener(new SetUserIdSubmitter());
    }

    private void setupTemplateTestMode() {

        Button testModeToggle = findViewById(R.id.toggleTestMode);
        testModeToggle.setOnClickListener(new TestModeToggler());
    }

    /**
     * To be sure that any call to the SDK is called only after the SDK has started, we must implement a {@link OptimoveSuccessStateListener}.
     */
    private class MyOptimoveStateListener implements OptimoveSuccessStateListener {

        @Override
        public void onConfigurationSucceed(OptimoveDeviceRequirement... missingPermissions) {
            //This is where we can handle any missing permissions and ask the user to provide us with them
            isOptimoveAvailable = true;
        }
    }

    /**
     * Creates a new custom event from the provided inputs.
     * The event must conform to the declared event in the configurations
     */
    private class CustomEventSubmitter implements View.OnClickListener {

        private TextInputEditText stringInputField;
        private TextInputEditText numberInputField;

        public CustomEventSubmitter() {

            stringInputField = findViewById(R.id.stringInputField);
            numberInputField = findViewById(R.id.numberInputField);
        }

        @Override
        public void onClick(View v) {

            if (!isOptimoveAvailable) {
                Toast.makeText(MainActivity.this, "Optimove SDK is not ready yet!", Toast.LENGTH_SHORT).show();
                return;
            }
            String stringInput = stringInputField.getText().toString();
            stringInput = stringInput.isEmpty() ? null : stringInput;
            String numberInput = numberInputField.getText().toString();
            numberInput = numberInput.isEmpty() ? null : numberInput;

            OptimoveEvent event;

            if (numberInput == null) {
                event = new CustomCombinedEvent(stringInput, null);
            } else {
                try {
                    event = new CustomCombinedEvent(stringInput, Double.parseDouble(numberInput));
                } catch (Exception e) {
                    String resultMsg = String.format("Event validation failed due to %s", EventSentResult.INCORRECT_VALUE_TYPE_ERROR);
                    Snackbar.make(rootView, resultMsg, BaseTransientBottomBar.LENGTH_SHORT).show();
                    return;
                }
            }

            submitCustomEvent(event);
        }

        private void submitCustomEvent(OptimoveEvent event) {
            Optimove.getInstance().reportEvent(event);
            // The event will now be validated and reported asynchronously - check
        }
    }

    /**
     * Sets a new User ID. Note that a userId can be set only once.
     */
    private class SetUserIdSubmitter implements View.OnClickListener {

        private TextInputEditText userIdInputField;

        public SetUserIdSubmitter() {

            userIdInputField = findViewById(R.id.uidInputText);
        }

        @Override
        public void onClick(View v) {

            if (!isOptimoveAvailable) {
                Toast.makeText(MainActivity.this, "Optimove SDK is not ready yet!", Toast.LENGTH_SHORT).show();
                return;
            }
            SharedPreferences sp = getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
            boolean didSignIn = sp.getBoolean("did_sign_in", false);
            String output;
            if (didSignIn) {
                output = "You already signed in!";
            } else {
                String userId = userIdInputField.getText().toString();
                if (userId.isEmpty() || userId.contains(" ")) {
                    output = "Invalid user ID";
                } else {
                    Optimove.getInstance().setUserId(userId);
                    sp.edit().putBoolean("did_sign_in", true).apply();
                    output = "Your user ID was saved";
                }
            }
            Snackbar.make(rootView, output, Snackbar.LENGTH_SHORT).show();
        }
    }

    /**
     * It's not an error to call {@link Optimove#startTestMode()} twice but is doesn't reflect the actual state of the current occurrence of the app.
     * That's why we'll track the subscription to tests here.
     */
    private class TestModeToggler implements View.OnClickListener {

        private TextView testModeOutput;

        public TestModeToggler() {
            testModeOutput = findViewById(R.id.testModeStateOutput);
            updateTestModeOutput();
        }

        @Override
        public void onClick(View v) {

            if (!isOptimoveAvailable) {
                Toast.makeText(MainActivity.this, "Optimove SDK is not ready yet!", Toast.LENGTH_SHORT).show();
                return;
            }
            SharedPreferences sp = getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
            boolean testModeToggled = sp.getBoolean("test_mode_toggled", false);

            if (testModeToggled) {
                Optimove.getInstance().stopTestMode(success -> {
                    if (success) {
                        sp.edit().putBoolean("test_mode_toggled", false).apply();
                    } else {
                        Log.e("TEST_MODE_TOGGLE", "Failed to stop test mode");
                    }
                });
            } else {
                Optimove.getInstance().startTestMode(success -> {
                    if (success) {
                        sp.edit().putBoolean("test_mode_toggled", true).apply();
                    } else {
                        Log.e("TEST_MODE_TOGGLE", "Failed to start test mode");
                    }
                });
            }

            updateTestModeOutput();
        }

        private void updateTestModeOutput() {

            SharedPreferences sp = getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
            boolean testModeToggled = sp.getBoolean("test_mode_toggled", false);
            String output = testModeToggled ? "Template Test Mode is ON" : "Template Test Mode is OFF";
            testModeOutput.setText(output);
        }
    }
}

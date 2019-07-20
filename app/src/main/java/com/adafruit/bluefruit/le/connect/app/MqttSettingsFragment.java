package com.adafruit.bluefruit.le.connect.app;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.mqtt.MqttManager;
import com.adafruit.bluefruit.le.connect.mqtt.MqttSettings;
import com.adafruit.bluefruit.le.connect.utils.KeyboardUtils;

import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MqttSettingsFragment extends Fragment implements MqttSettingsCodeReaderFragment.OnFragmentInteractionListener, MqttManager.MqttManagerListener {
    // Log
    private final static String TAG = MqttSettingsFragment.class.getSimpleName();

    // Constants
    private static final int kNumPublishFeeds = 2;
    public static final int kPublishFeed_RX = 0;
    public static final int kPublishFeed_TX = 1;

    // UI
    private EditText mServerAddressEditText;
    private EditText mServerPortEditText;
    private EditText mSubscribeTopicEditText;
    private Button mConnectButton;
    private ProgressBar mConnectProgressBar;
    private TextView mStatusTextView;
    private Spinner mSubscribeSpinner;
    private EditText mUsernameEditText;
    private EditText mPasswordEditText;

    // Data
    private MqttManager mMqttManager;
    private String mPreviousSubscriptionTopic;

    // region Fragment Lifecycle

    @SuppressWarnings("UnnecessaryLocalVariable")
    public static MqttSettingsFragment newInstance() {
        MqttSettingsFragment fragment = new MqttSettingsFragment();
        return fragment;
    }

    public MqttSettingsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Update ActionBar
        AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity != null) {
            ActionBar actionBar = activity.getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(R.string.uart_mqtt_settings_title);
                actionBar.setDisplayHomeAsUpEnabled(true);
            }
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_mqttsettings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Context context = getContext();
        if (context != null) {
            mMqttManager = new MqttManager(context, MqttSettingsFragment.this);

            // UI - Server
            mServerAddressEditText = view.findViewById(R.id.serverAddressEditText);
            mServerAddressEditText.setText(MqttSettings.getServerAddress(context));
            mServerAddressEditText.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    final String serverAddress = mServerAddressEditText.getText().toString();
                    MqttSettings.setServerAddress(context, serverAddress);
                }
            });

            mServerPortEditText = view.findViewById(R.id.serverPortEditText);
            mServerPortEditText.setHint("" + MqttSettings.kDefaultServerPort);
            mServerPortEditText.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    MqttSettings.setServerPort(context, mServerPortEditText.getText().toString());
                    mServerPortEditText.setText(String.format(Locale.ENGLISH, "%d", MqttSettings.getServerPort(context)));
                }
            });

            // UI - Publish
            Switch publishSwitch = view.findViewById(R.id.publishSwitch);
            publishSwitch.setChecked(MqttSettings.isPublishEnabled(context));
            publishSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> MqttSettings.setPublishEnabled(context, isChecked));

            final int[] kPublishTopicEditTextsIds = {R.id.publish0TopicEditText, R.id.publish1TopicEditText};
            final int[] kPublishTopicSpinnerIds = {R.id.publish0Spinner, R.id.publish1Spinner};
            List<String> qosEntries = new ArrayList<>();
            qosEntries.add(getString(R.string.uart_mqtt_qos_atleastonce));
            qosEntries.add(getString(R.string.uart_mqtt_qos_atmostonce));
            qosEntries.add(getString(R.string.uart_mqtt_qos_exactlyonce));
            for (int i = 0; i < kNumPublishFeeds; i++) {
                final int index = i;

                final EditText publishTopicEditText = view.findViewById(kPublishTopicEditTextsIds[i]);
                publishTopicEditText.setText(MqttSettings.getPublishTopic(context, index));
                publishTopicEditText.setOnFocusChangeListener((v, hasFocus) -> {
                    if (!hasFocus) {
                        MqttSettings.setPublishTopic(context, index, publishTopicEditText.getText().toString());
                    }
                });

                Spinner publishSpinner = view.findViewById(kPublishTopicSpinnerIds[i]);
                ArrayAdapter<String> qosAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, qosEntries);
                qosAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                publishSpinner.setAdapter(qosAdapter);
                publishSpinner.setSelection(MqttSettings.getPublishQos(context, index));
                publishSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        MqttSettings.setPublishQos(context, index, position);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {

                    }
                });
            }

            // UI - Subscribe
            mSubscribeTopicEditText = view.findViewById(R.id.subscribeTopicEditText);
            mSubscribeTopicEditText.setText(MqttSettings.getSubscribeTopic(context));
            mPreviousSubscriptionTopic = MqttSettings.getSubscribeTopic(context);
            mSubscribeTopicEditText.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    String topic = mSubscribeTopicEditText.getText().toString();
                    MqttSettings.setSubscribeTopic(context, topic);
                    subscriptionChanged(topic, mSubscribeSpinner.getSelectedItemPosition());
                }
            });


            Switch subscribeSwitch = view.findViewById(R.id.subscribeSwitch);
            subscribeSwitch.setChecked(MqttSettings.isSubscribeEnabled(context));
            subscribeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                MqttSettings.setSubscribeEnabled(context, isChecked);
                subscriptionChanged(null, mSubscribeSpinner.getSelectedItemPosition());
            });

            mSubscribeSpinner = view.findViewById(R.id.subscribeSpinner);
            ArrayAdapter<String> qosAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, qosEntries);
            qosAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            mSubscribeSpinner.setAdapter(qosAdapter);

            mSubscribeSpinner.setSelection(MqttSettings.getSubscribeQos(context));
            mSubscribeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                boolean isInitializing = true;

                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (!isInitializing) {
                        MqttSettings.setSubscribeQos(context, position);
                        String topic = mSubscribeTopicEditText.getText().toString();
                        subscriptionChanged(topic, mSubscribeSpinner.getSelectedItemPosition());
                    }
                    isInitializing = false;
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {

                }
            });

            List<String> subscribeBehaviourEntries = new ArrayList<>();
            subscribeBehaviourEntries.add(getString(R.string.uart_mqtt_subscription_localonly));
            subscribeBehaviourEntries.add(getString(R.string.uart_mqtt_subscription_transmit));
            Spinner subscribeBehaviourSpinner = view.findViewById(R.id.subscribeBehaviourSpinner);
            ArrayAdapter<String> subscribeAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, subscribeBehaviourEntries);
            qosAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            subscribeBehaviourSpinner.setAdapter(subscribeAdapter);
            subscribeBehaviourSpinner.setSelection(MqttSettings.getSubscribeBehaviour(context));
            subscribeBehaviourSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                boolean isInitializing = true;

                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (!isInitializing) {
                        MqttSettings.setSubscribeBehaviour(context, position);
                    }
                    isInitializing = false;
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {

                }
            });

            // UI - Advanced
            mUsernameEditText = view.findViewById(R.id.usernameEditText);
            mUsernameEditText.setText(MqttSettings.getUsername(context));
            mUsernameEditText.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    final String username = mUsernameEditText.getText().toString();
                    MqttSettings.setUsername(context, username);
                }
            });

            mPasswordEditText = view.findViewById(R.id.passwordEditText);
            mPasswordEditText.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    final String newPassword = mPasswordEditText.getText().toString();
                    MqttSettings.setPassword(context, newPassword);
                    Log.d(TAG, "save password: " + newPassword);
                }
            });

            Switch cleanSessionSwitch = view.findViewById(R.id.cleanSessionSwitch);
            cleanSessionSwitch.setChecked(MqttSettings.isCleanSession(context));
            cleanSessionSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> MqttSettings.setCleanSession(context, isChecked));

            Switch sslConnectionSwitch = view.findViewById(R.id.sslConnectionSwitch);
            sslConnectionSwitch.setChecked(MqttSettings.isSslConnection(context));
            sslConnectionSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> MqttSettings.setSslConnection(context, isChecked));

            // UI - Connect
            mConnectButton = view.findViewById(R.id.connectButton);
            mConnectButton.setOnClickListener(v -> {
                // Force remove focus from last field to take into account the changes
                FragmentActivity activity = getActivity();
                if (activity != null) {
                    View currentFocusView = activity.getCurrentFocus();
                    if (currentFocusView != null) {
                        currentFocusView.clearFocus();
                    }
                }

                // Dismiss keyboard
                KeyboardUtils.dismissKeyboard(v);

                // Connect / Disconnect
                Context context1 = getContext();
                if (context1 != null) {
                    MqttManager.MqqtConnectionStatus status = mMqttManager.getClientStatus();
                    Log.d(TAG, "current mqtt status: " + status);
                    if (status == MqttManager.MqqtConnectionStatus.DISCONNECTED || status == MqttManager.MqqtConnectionStatus.NONE || status == MqttManager.MqqtConnectionStatus.ERROR) {
                        mMqttManager.connectFromSavedSettings();
                    } else {
                        mMqttManager.disconnect();
                        MqttSettings.setConnectedEnabled(context1, false);
                    }

                    // Update UI
                    updateStatusUI();
                }
            });
            mConnectProgressBar = view.findViewById(R.id.connectProgressBar);
            mStatusTextView = view.findViewById(R.id.statusTextView);

            // UI - QRCode
            Button qrConfigButton = view.findViewById(R.id.qrConfigButton);
            qrConfigButton.setOnClickListener(v -> {
                // Dismiss keyboard
                KeyboardUtils.dismissKeyboard(v);

                // Launch reader
                FragmentActivity activity = getActivity();
                if (activity != null) {
                    FragmentManager fragmentManager = activity.getSupportFragmentManager();
                    if (fragmentManager != null) {
                        MqttSettingsCodeReaderFragment fragment = MqttSettingsCodeReaderFragment.newInstance();
                        fragment.setTargetFragment(this, 0);
                        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction()
                                .setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right, R.anim.slide_in_right, R.anim.slide_out_left)
                                .replace(R.id.contentLayout, fragment, "MqttSettingsCodeReader");
                        fragmentTransaction.addToBackStack(null);
                        fragmentTransaction.commit();
                    }
                }
            });

            // Refresh UI
            updateStatusUI();
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        Context context = getContext();
        if (context != null) {
            // EditText setText: set it on onStart:  https://stackoverflow.com/questions/13303469/edittext-settext-not-working-with-fragment
            String password = MqttSettings.getPassword(context);
            mPasswordEditText.setText(password);
        }
    }

    // endregion

    // region
    private void subscriptionChanged(String newTopic, int qos) {
        Log.d(TAG, "subscription changed from: " + mPreviousSubscriptionTopic + " to: " + newTopic + " qos: " + qos);

        mMqttManager.unsubscribe(mPreviousSubscriptionTopic);
        mMqttManager.subscribe(newTopic, qos);
        mPreviousSubscriptionTopic = newTopic;
    }

    @MainThread
    private void updateStatusUI() {
        Context context = getContext(); // Use context for getString and check that is not null to solve a crash detected on logs
        if (context == null) {
            Log.e(TAG, "Context is null");
            return;
        }

        MqttManager.MqqtConnectionStatus status = mMqttManager.getClientStatus();

        // Update enable-disable button
        final boolean showWait = (status == MqttManager.MqqtConnectionStatus.CONNECTING || status == MqttManager.MqqtConnectionStatus.DISCONNECTING);
        mConnectButton.setVisibility(showWait ? View.INVISIBLE : View.VISIBLE);
        mConnectProgressBar.setVisibility(showWait ? View.VISIBLE : View.GONE);

        if (!showWait) {
            int stringId = R.string.uart_mqtt_action_connect;
            if (status == MqttManager.MqqtConnectionStatus.CONNECTED) {
                stringId = R.string.uart_mqtt_action_disconnect;
            }

            mConnectButton.setText(context.getString(stringId));
        }

        // Update status text
        int statusStringId;
        switch (status) {
            case CONNECTED:
                statusStringId = R.string.uart_mqtt_status_connected;
                break;
            case CONNECTING:
                statusStringId = R.string.uart_mqtt_status_connecting;
                break;
            case DISCONNECTING:
                statusStringId = R.string.uart_mqtt_status_disconnecting;
                break;
            case ERROR:
                statusStringId = R.string.uart_mqtt_status_error;
                break;
            default:
                statusStringId = R.string.uart_mqtt_status_disconnected;
                break;
        }
        mStatusTextView.setText(context.getString(statusStringId));
    }
    // endregion


    // region MqttSettingsCodeReaderFragment.OnImageCropListener
    @Override
    public void onPasswordUpdated(String password) {
        mPasswordEditText.setText(password);
        Context context = getContext();
        if (context != null) {
            MqttSettings.setPassword(context, password);
        }
        mPasswordEditText.requestFocus();
    }

    // endregion

    // region MqttManagerListener
    @Override
    public void onMqttConnected() {
        updateStatusUI();
    }

    @Override
    public void onMqttDisconnected() {
        updateStatusUI();
    }

    @Override
    public void onMqttMessageArrived(String topic, @NonNull MqttMessage mqttMessage) {

    }

    // endregion
}

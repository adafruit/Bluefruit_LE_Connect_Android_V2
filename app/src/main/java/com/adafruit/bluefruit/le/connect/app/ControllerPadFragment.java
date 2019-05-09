package com.adafruit.bluefruit.le.connect.app;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.ImageButton;

import com.adafruit.bluefruit.le.connect.R;

public class ControllerPadFragment extends Fragment {
    // Log
    @SuppressWarnings("unused")
    private final static String TAG = ControllerPadFragment.class.getSimpleName();

    // Config
    private final static float kMinAspectRatio = 1.8f;

    // UI TextBuffer (refreshing the text buffer is managed with a timer because a lot of changes can arrive really fast and could stall the main thread)
    private Handler mUIRefreshTimerHandler = new Handler();
    private Runnable mUIRefreshTimerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isUITimerRunning) {
                updateTextDataUI();
                // Log.d(TAG, "updateDataUI");
                mUIRefreshTimerHandler.postDelayed(this, 200);
            }
        }
    };
    private boolean isUITimerRunning = false;

    // UI
    private ViewGroup mContentView;
    private EditText mBufferTextView;
    private ViewGroup mRootLayout;
    private View mTopSpacerView;
    private View mBottomSpacerView;

    // Data
    private ControllerPadFragmentListener mListener;
    private volatile StringBuilder mDataBuffer = new StringBuilder();
    private volatile StringBuilder mTextSpanBuffer = new StringBuilder();
    private int maxPacketsToPaintAsText;
    View.OnTouchListener mPadButtonTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent event) {
            final int tag = Integer.valueOf((String) view.getTag());
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                view.setPressed(true);
                mListener.onSendControllerPadButtonStatus(tag, true);
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                view.setPressed(false);
                mListener.onSendControllerPadButtonStatus(tag, false);
                view.performClick();
                return true;
            }
            return false;
        }
    };

    // region Lifecycle
    @SuppressWarnings("UnnecessaryLocalVariable")
    public static ControllerPadFragment newInstance() {
        ControllerPadFragment fragment = new ControllerPadFragment();
        return fragment;
    }

    public ControllerPadFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Retain this fragment across configuration changes
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_controller_pad, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Set title
        AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity != null) {
            ActionBar actionBar = activity.getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(R.string.controlpad_title);
                actionBar.setDisplayHomeAsUpEnabled(true);
            }
        }

        // UI
        mRootLayout = view.findViewById(R.id.rootLayout);
        mTopSpacerView = view.findViewById(R.id.topSpacerView);
        mBottomSpacerView = view.findViewById(R.id.bottomSpacerView);

        mContentView = view.findViewById(R.id.contentView);
        mBufferTextView = view.findViewById(R.id.bufferTextView);
        if (mBufferTextView != null) {
            mBufferTextView.setKeyListener(null);     // make it not editable
        }

        ImageButton upArrowImageButton = view.findViewById(R.id.upArrowImageButton);
        upArrowImageButton.setOnTouchListener(mPadButtonTouchListener);
        ImageButton leftArrowImageButton = view.findViewById(R.id.leftArrowImageButton);
        leftArrowImageButton.setOnTouchListener(mPadButtonTouchListener);
        ImageButton rightArrowImageButton = view.findViewById(R.id.rightArrowImageButton);
        rightArrowImageButton.setOnTouchListener(mPadButtonTouchListener);
        ImageButton bottomArrowImageButton = view.findViewById(R.id.bottomArrowImageButton);
        bottomArrowImageButton.setOnTouchListener(mPadButtonTouchListener);

        ImageButton button1ImageButton = view.findViewById(R.id.button1ImageButton);
        button1ImageButton.setOnTouchListener(mPadButtonTouchListener);
        ImageButton button2ImageButton = view.findViewById(R.id.button2ImageButton);
        button2ImageButton.setOnTouchListener(mPadButtonTouchListener);
        ImageButton button3ImageButton = view.findViewById(R.id.button3ImageButton);
        button3ImageButton.setOnTouchListener(mPadButtonTouchListener);
        ImageButton button4ImageButton = view.findViewById(R.id.button4ImageButton);
        button4ImageButton.setOnTouchListener(mPadButtonTouchListener);

        // Read shared preferences
        maxPacketsToPaintAsText = UartBaseFragment.kDefaultMaxPacketsToPaintAsText; //PreferencesFragment.getUartTextMaxPackets(this);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof ControllerPadFragmentListener) {
            mListener = (ControllerPadFragmentListener) context;
        } else if (getTargetFragment() instanceof ControllerPadFragmentListener) {
            mListener = (ControllerPadFragmentListener) getTargetFragment();
        } else {
            throw new RuntimeException(context.toString() + " must implement ControllerPadFragmentListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onResume() {
        super.onResume();

        ViewTreeObserver observer = mRootLayout.getViewTreeObserver();
        observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                adjustAspectRatio();
                mRootLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });

        // Refresh timer
        isUITimerRunning = true;
        mUIRefreshTimerHandler.postDelayed(mUIRefreshTimerRunnable, 0);
    }

    @Override
    public void onPause() {
        isUITimerRunning = false;
        mUIRefreshTimerHandler.removeCallbacksAndMessages(mUIRefreshTimerRunnable);

        super.onPause();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_help, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        FragmentActivity activity = getActivity();

        switch (item.getItemId()) {
            case R.id.action_help:
                if (activity != null) {
                    FragmentManager fragmentManager = activity.getSupportFragmentManager();
                    if (fragmentManager != null) {
                        CommonHelpFragment helpFragment = CommonHelpFragment.newInstance(getString(R.string.controlpad_help_title), getString(R.string.controlpad_help_text));
                        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction()
                                .replace(R.id.contentLayout, helpFragment, "Help");
                        fragmentTransaction.addToBackStack(null);
                        fragmentTransaction.commit();
                    }
                }
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // endregion

    // region UI

    private void adjustAspectRatio() {
        ViewGroup rootLayout = mContentView;
        final int mainWidth = rootLayout.getWidth();

        if (mainWidth > 0) {
            final int mainHeight = rootLayout.getHeight() - mTopSpacerView.getLayoutParams().height - mBottomSpacerView.getLayoutParams().height;
            if (mainHeight > 0) {
                // Add black bars if aspect ratio is below min
                final float aspectRatio = mainWidth / (float) mainHeight;
                if (aspectRatio < kMinAspectRatio) {
                    final int spacerHeight = Math.round(mainHeight - mainWidth / kMinAspectRatio);
                    ViewGroup.LayoutParams topLayoutParams = mTopSpacerView.getLayoutParams();
                    topLayoutParams.height = spacerHeight / 2;
                    mTopSpacerView.setLayoutParams(topLayoutParams);

                    ViewGroup.LayoutParams bottomLayoutParams = mBottomSpacerView.getLayoutParams();
                    bottomLayoutParams.height = spacerHeight / 2;
                    mBottomSpacerView.setLayoutParams(bottomLayoutParams);
                }
            }
        }
    }

    public synchronized void addText(String text) {
        mDataBuffer.append(text);
    }


    private int mDataBufferLastSize = 0;

    private synchronized void updateTextDataUI() {

        final int bufferSize = mDataBuffer.length();
        if (mDataBufferLastSize != bufferSize) {

            if (bufferSize > maxPacketsToPaintAsText) {
                mDataBufferLastSize = bufferSize - maxPacketsToPaintAsText;
                mTextSpanBuffer.setLength(0);
                mTextSpanBuffer.append(getString(R.string.uart_text_dataomitted)).append("\n");
                mDataBuffer.replace(0, mDataBufferLastSize, "");
                mTextSpanBuffer.append(mDataBuffer);

            } else {
                mTextSpanBuffer.append(mDataBuffer.substring(mDataBufferLastSize, bufferSize));
            }

            mDataBufferLastSize = mDataBuffer.length();
            mBufferTextView.setText(mTextSpanBuffer);
            mBufferTextView.setSelection(0, mTextSpanBuffer.length());        // to automatically scroll to the end
        }
    }
    // endregion

    // region ControllerPadFragmentListener
    public interface ControllerPadFragmentListener {
        void onSendControllerPadButtonStatus(int tag, boolean isPressed);
    }
    // endregion
}

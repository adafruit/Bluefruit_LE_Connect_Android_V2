package com.adafruit.bluefruit.le.connect.app.imagetransfer;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.adafruit.bluefruit.le.connect.R;
import com.google.android.material.tabs.TabLayout;

public class ImageTransferFormatSelectorDialogFragment extends DialogFragment implements ImageTransferFormatSelectorPageFragment.FormatSelectorListener {// AppCompatDialogFragment {
    // Log
    private final static String TAG = ImageTransferFormatSelectorDialogFragment.class.getSimpleName();

    // Constants
    private static final String ARG_PARAM_ISEINKMODEENABLED = "isEInkModeEnabled";
    private static final String ARG_PARAM_RESOLUTION_WIDTH = "resolutionWidth";
    private static final String ARG_PARAM_RESOLUTION_HEIGHT = "resolutionHeight";

    public interface FormatSelectorListener {
        void onResolutionSelected(Size resolution, boolean isEInkMode);
    }

    // Params
    private boolean mIsEInkModeEnabled;
    private Size mResolution;
    private FormatSelectorListener mListener;

    public static ImageTransferFormatSelectorDialogFragment newInstance(boolean isEInkModeEnabled, Size resolution) {
        ImageTransferFormatSelectorDialogFragment fragment = new ImageTransferFormatSelectorDialogFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_PARAM_ISEINKMODEENABLED, isEInkModeEnabled);
        args.putInt(ARG_PARAM_RESOLUTION_WIDTH, resolution.getWidth());
        args.putInt(ARG_PARAM_RESOLUTION_HEIGHT, resolution.getHeight());
        fragment.setArguments(args);
        return fragment;
    }

    public ImageTransferFormatSelectorDialogFragment() {
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mIsEInkModeEnabled = getArguments().getBoolean(ARG_PARAM_ISEINKMODEENABLED);
            int resolutionWidth = getArguments().getInt(ARG_PARAM_RESOLUTION_WIDTH);
            int resolutionHeight = getArguments().getInt(ARG_PARAM_RESOLUTION_HEIGHT);
            mResolution = new Size(resolutionWidth, resolutionHeight);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_imagetransfer_chooseresolution, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {

        // Set animations
        Dialog dialog = getDialog();
        if (dialog != null) {
            //dialog.setTitle(R.string.imagetransfer_resolution_choose);
            //dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            Window window = dialog.getWindow();
            if (window != null) {
                window.setWindowAnimations(R.style.DialogAnimation);
            }
        }

        // Configure tabs and viewpager
        ViewPager viewPager = view.findViewById(R.id.viewpager);
        ResolutionsPageAdapter adapter = new ResolutionsPageAdapter(getChildFragmentManager(), FragmentPagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT, mResolution);
        viewPager.setAdapter(adapter);

        TabLayout tabLayout = view.findViewById(R.id.tabLayout);
        tabLayout.setupWithViewPager(viewPager);

        // Force titles
        TabLayout.Tab standardResolutions = tabLayout.getTabAt(0);
        if (standardResolutions != null) {
            standardResolutions.setText(R.string.imagetransfer_resolution_mode_standard);
        }
        TabLayout.Tab eInkResolutions = tabLayout.getTabAt(1);
        if (eInkResolutions != null) {
            eInkResolutions.setText(R.string.imagetransfer_resolution_mode_eink);
        }

        // Set initial item
        viewPager.setCurrentItem(mIsEInkModeEnabled ? 1 : 0);
    }

    @Override
    public void onResume() {
        super.onResume();

        // Set dialog size
        Dialog dialog = getDialog();
        if (dialog != null) {
            Window window = dialog.getWindow();
            if (window != null) {
                int width = getResources().getDimensionPixelSize(R.dimen.imagetransfer_resolutiondialog_width);
                int height = getResources().getDimensionPixelSize(R.dimen.imagetransfer_resolutiondialog_height);
                window.setLayout(width, height);
            }
        }
    }


    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof FormatSelectorListener) {
            mListener = (FormatSelectorListener) context;
        } else if (getTargetFragment() instanceof FormatSelectorListener) {
            mListener = (FormatSelectorListener) getTargetFragment();
        } else {
            throw new RuntimeException(context.toString() + " must implement FormatSelectorListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    // region  ResolutionsPageAdapter
    static class ResolutionsPageAdapter extends FragmentPagerAdapter {

        // Params
        private Size mResolution;
        //private Fragment mTargetFragment;

        ResolutionsPageAdapter(@NonNull FragmentManager fm, int behavior, Size resolution) {//}, Fragment targetFragment) {
            super(fm, behavior);
            mResolution = resolution;
            //mTargetFragment = targetFragment;
        }

        @Override
        public int getCount() {
            return 2;
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            return ImageTransferFormatSelectorPageFragment.newInstance(position != 0, mResolution);
        }
    }

    // enregion

    // region FormatSelectorListener
    @Override
    public void onResolutionSelected(Size resolution, boolean isEInkMode) {
        if (mListener != null) {
            mListener.onResolutionSelected(resolution, isEInkMode);
        }
        dismiss();

    }
    // endregion

}

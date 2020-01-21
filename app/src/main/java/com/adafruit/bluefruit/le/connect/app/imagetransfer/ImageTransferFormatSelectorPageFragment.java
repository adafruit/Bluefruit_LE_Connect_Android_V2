package com.adafruit.bluefruit.le.connect.app.imagetransfer;

import android.content.Context;
import android.os.Bundle;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.adafruit.bluefruit.le.connect.R;

import java.util.Locale;

public class ImageTransferFormatSelectorPageFragment extends Fragment {
    private static final String ARG_PARAM_ISEINKMODE = "isEInkMode";
    private static final String ARG_PARAM_RESOLUTION_WIDTH = "resolutionWidth";
    private static final String ARG_PARAM_RESOLUTION_HEIGHT = "resolutionHeight";

    public static Size[] kStandardResolutions = {
            new Size(4, 4),
            new Size(8, 8),
            new Size(16, 16),
            new Size(32, 32),
            new Size(64, 64),
            new Size(128, 128),
            new Size(128, 160),
            new Size(160, 80),
            new Size(168, 144),
            new Size(212, 104),
            new Size(240, 240),
            new Size(250, 122),
            new Size(256, 256),
            new Size(296, 128),
            new Size(300, 400),
            new Size(320, 240),
            new Size(480, 320),
            new Size(512, 512),
            // new Size(1024, 1024),
    };


    private static Size[] kEInkResolutions = {
            new Size(152, 152),
            new Size(168, 44),
            new Size(212, 104),
            new Size(250, 122),
            new Size(296, 128),
            new Size(300, 400),
    };

    public interface FormatSelectorListener {
        void onResolutionSelected(Size resolution, boolean isEInkMode);
    }

    // Params
    private boolean mIsEInkMode;
    private Size mResolution;

    private FormatSelectorListener mListener;

    public static ImageTransferFormatSelectorPageFragment newInstance(boolean isEInkMode, Size resolution) {
        ImageTransferFormatSelectorPageFragment fragment = new ImageTransferFormatSelectorPageFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_PARAM_ISEINKMODE, isEInkMode);
        args.putInt(ARG_PARAM_RESOLUTION_WIDTH, resolution.getWidth());
        args.putInt(ARG_PARAM_RESOLUTION_HEIGHT, resolution.getHeight());
        fragment.setArguments(args);
        return fragment;
    }

    public ImageTransferFormatSelectorPageFragment() {
        // Required empty public constructor
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mIsEInkMode = getArguments().getBoolean(ARG_PARAM_ISEINKMODE);
            int resolutionWidth = getArguments().getInt(ARG_PARAM_RESOLUTION_WIDTH);
            int resolutionHeight = getArguments().getInt(ARG_PARAM_RESOLUTION_HEIGHT);
            mResolution = new Size(resolutionWidth, resolutionHeight);
        }

        onAttachToParentFragment(getParentFragment());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mListener = null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_imagetransfer_formatselector_page, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Context context = getContext();
        if (context == null) return;

        Size[] resolutions = mIsEInkMode ? kEInkResolutions : kStandardResolutions;

        // add a radio button list
        int checkedItem = -1;
        final ArrayAdapter<String> resolutionsAdapter = new ArrayAdapter<>(context, android.R.layout.select_dialog_singlechoice);
        for (int i = 0; i < resolutions.length; i++) {
            Size size = resolutions[i];
            resolutionsAdapter.add(String.format(Locale.US, "%d x %d", size.getWidth(), size.getHeight()));

            if (size.equals(mResolution)) {
                checkedItem = i;
            }
        }

        ListView listView = view.findViewById(R.id.listView);
        listView.setAdapter(resolutionsAdapter);
        listView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
        if (checkedItem >= 0) {
            listView.setItemChecked(checkedItem, true);
        }

        listView.setOnItemClickListener((parent, view1, position, id) -> {
            if (mListener != null) {
                Size[] currentResolutions = mIsEInkMode ? kEInkResolutions : kStandardResolutions;
                mListener.onResolutionSelected(currentResolutions[position], mIsEInkMode);
            }
        });

    }


    // based on https://stackoverflow.com/questions/23142956/sending-data-from-nested-fragments-to-parent-fragment
    private void onAttachToParentFragment(Fragment fragment) {
        if (fragment instanceof FormatSelectorListener) {
            mListener = (FormatSelectorListener) fragment;
        } else {
            throw new RuntimeException(fragment + " must implement FormatSelectorListener");
        }
    }

    /*
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
    }*/
}

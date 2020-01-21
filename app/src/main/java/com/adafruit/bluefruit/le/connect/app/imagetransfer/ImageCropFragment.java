package com.adafruit.bluefruit.le.connect.app.imagetransfer;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.adafruit.bluefruit.le.connect.R;
import com.naver.android.helloyako.imagecrop.view.ImageCropView;

public class ImageCropFragment extends Fragment {
    // Log
    public final static String TAG = ImageCropFragment.class.getSimpleName();

    // Constants
    private static final String ARG_IMAGEURI = "imageUri";
    private static final String ARG_WIDTH = "width";
    private static final String ARG_HEIGHT = "height";

    // Interface
    public interface OnImageCropListener {
        void onCropFinished(Bitmap bitmap);
    }

    // Data
    private OnImageCropListener mListener;
    private ImageCropView mImageCropView;
    private String mImagePath;
    private int mImageWidth, mImageHeight;

    public ImageCropFragment() {
        // Required empty public constructor
    }

    public static ImageCropFragment newInstance(@NonNull String imageUri, int width, int height) {
        ImageCropFragment fragment = new ImageCropFragment();
        Bundle args = new Bundle();
        args.putString(ARG_IMAGEURI, imageUri);
        args.putInt(ARG_WIDTH, width);
        args.putInt(ARG_HEIGHT, height);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mImagePath = getArguments().getString(ARG_IMAGEURI);
            mImageWidth = getArguments().getInt(ARG_WIDTH);
            mImageHeight = getArguments().getInt(ARG_HEIGHT);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_imagecrop, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mImageCropView = view.findViewById(R.id.imageCropView);
        try {
            mImageCropView.setImageFilePath(mImagePath);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Error: " + e);
        }
        mImageCropView.setAspectRatio(mImageWidth, mImageHeight);

        Button cropButton = view.findViewById(R.id.cropButton);
        cropButton.setOnClickListener(view1 -> cropImage());
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnImageCropListener) {
            mListener = (OnImageCropListener) context;
        } else if (getTargetFragment() instanceof OnImageCropListener) {
            mListener = (OnImageCropListener) getTargetFragment();
        } else {
            throw new RuntimeException(context.toString() + " must implement OnImageCropListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    private void cropImage() {
        if (!mImageCropView.isChangingScale()) {
            Bitmap bitmap = mImageCropView.getCroppedImage();
            if (bitmap != null) {
                //bitmapConvertToFile(b);

                if (mListener != null) {
                    mListener.onCropFinished(bitmap);

                    FragmentManager fragmentManager = getFragmentManager();
                    if (fragmentManager != null) {
                        fragmentManager.popBackStackImmediate();
                    }
                }
            } else {
                Toast.makeText(getContext(), R.string.imagetransfer_crop_error, Toast.LENGTH_SHORT).show();
            }
        }
    }
}

package com.adafruit.bluefruit.le.connect.app.neopixel;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialog;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.utils.FileUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class NeopixelBoardSelectorFragment extends AppCompatDialogFragment {
    // Log
    private final static String TAG = NeopixelBoardSelectorFragment.class.getSimpleName();

    // Data
    private NeopixelBoardSelectorFragmentListener mListener;

    // region Lifecycle
    @SuppressWarnings("UnnecessaryLocalVariable")
    public static NeopixelBoardSelectorFragment newInstance() {
        NeopixelBoardSelectorFragment fragment = new NeopixelBoardSelectorFragment();
        return fragment;
    }

    public NeopixelBoardSelectorFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Remove title
        AppCompatDialog dialog = (AppCompatDialog) getDialog();
        if (dialog != null) {
            dialog.supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        }

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_neopixel_boardselector, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Dismiss on click outside
        AppCompatDialog dialog = (AppCompatDialog) getDialog();
        if (dialog != null) {
            dialog.setCanceledOnTouchOutside(true);
        }

        // UI
        Context context = getContext();
        if (context != null) {
            RecyclerView standardSizesRecyclerView = view.findViewById(R.id.standardSizesRecyclerView);
            standardSizesRecyclerView.setHasFixedSize(true);
            standardSizesRecyclerView.setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
            RecyclerView.Adapter standardBoardSizesAdapter = new StandardBoardSizesAdapter(context, index -> {
                mListener.onBoardIndexSelected(index);
                dismiss();
            });
            standardSizesRecyclerView.setAdapter(standardBoardSizesAdapter);

            Button lineStripButton = view.findViewById(R.id.lineStripButton);
            lineStripButton.setOnClickListener(view1 -> {
                AlertDialog.Builder alert = new AlertDialog.Builder(context);
                alert.setTitle(R.string.neopixelboardselector_linestriplength_title);
                final EditText input = new EditText(context);
                input.setHint(R.string.neopixelboardselector_linestriplength_hint);
                input.setInputType(InputType.TYPE_CLASS_NUMBER);
                input.setRawInputType(Configuration.KEYBOARD_12KEY);
                alert.setView(input);
                alert.setPositiveButton(R.string.neopixelboardselector_linestriplength_action, (alertDialog, whichButton) -> {
                    String valueString = String.valueOf(input.getText());
                    int value = 8;      // Default length
                    try {
                        int number = Integer.parseInt(valueString);
                        if (number > 0) {
                            value = number;
                        }
                    } catch (Exception e) {
                        Log.d(TAG, "Cannot parse value");
                    }

                    mListener.onLineStripSelected(value);
                });
                alert.setNegativeButton(android.R.string.cancel, (alertDialog, whichButton) -> {
                });
                alert.show();
            });
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof NeopixelBoardSelectorFragmentListener) {
            mListener = (NeopixelBoardSelectorFragmentListener) context;
        } else if (getTargetFragment() instanceof NeopixelBoardSelectorFragmentListener) {
            mListener = (NeopixelBoardSelectorFragmentListener) getTargetFragment();
        } else {
            throw new RuntimeException(context.toString() + " must implement NeopixelComponentSelectorFragmentListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    // endregion

    // region BoardSizesAdapter
    public static class StandardBoardSizesAdapter extends RecyclerView.Adapter<StandardBoardSizesAdapter.ViewHolder> {
        interface Listener {
            void onBoardSelected(int index);
        }

        // Data
        private List<String> mDefaultBoards;
        private Listener mListener;

        // ViewHolder
        class ViewHolder extends RecyclerView.ViewHolder {
            Button mItem;

            ViewHolder(ViewGroup view) {
                super(view);
                mItem = view.findViewById(R.id.itemView);
            }
        }

        StandardBoardSizesAdapter(@NonNull Context context, @NonNull Listener listener) {
            mListener = listener;

            // Read standard board size data
            String boardsJsonString = FileUtils.readAssetsFile("neopixel" + File.separator + "NeopixelBoards.json", context.getAssets());
            try {
                mDefaultBoards = new ArrayList<>();
                JSONArray boardsArray = new JSONArray(boardsJsonString);
                for (int i = 0; i < boardsArray.length(); i++) {
                    JSONObject boardJsonObject = boardsArray.getJSONObject(i);
                    String boardName = boardJsonObject.getString("name");
                    mDefaultBoards.add(boardName);
                }
            } catch (JSONException e) {
                Log.e(TAG, "Error decoding default boards");
            }
        }

        @SuppressWarnings("UnnecessaryLocalVariable")
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ViewGroup view = (ViewGroup) LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_neopixel_list_item, parent, false);
            final ViewHolder viewHolder = new ViewHolder(view);
            return viewHolder;
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

            String name = mDefaultBoards.get(position);
            holder.mItem.setText(name);

            holder.mItem.setOnClickListener(v -> {
                final int index = holder.getAdapterPosition();
                mListener.onBoardSelected(index);
            });
        }

        @Override
        public int getItemCount() {
            return mDefaultBoards.size();
        }
    }

    // endregion

    // region Listener
    public interface NeopixelBoardSelectorFragmentListener {
        void onBoardIndexSelected(int index);

        void onLineStripSelected(int length);
    }
    // endregion
}

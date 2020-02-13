package com.adafruit.bluefruit.le.connect.app.neopixel;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialog;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.adafruit.bluefruit.le.connect.R;

public class NeopixelComponentSelectorFragment extends AppCompatDialogFragment {
    // Log
    @SuppressWarnings("unused")
    private final static String TAG = NeopixelComponentSelectorFragment.class.getSimpleName();

    // Constants
    private static final String ARG_SELECTEDCOMPONENT = "selectedComponent";
    private static final String ARG_IS400HHZENABLED = "is400KhzEnabled";

    // Data
    private NeopixelComponents mSelectedComponent;
    private boolean mIs400KhzEnabled;
    private NeopixelComponentSelectorFragmentListener mListener;

    // region Lifecycle
    public static NeopixelComponentSelectorFragment newInstance(int selectedComponentType, boolean is400KhzEnabled) {
        NeopixelComponentSelectorFragment fragment = new NeopixelComponentSelectorFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_SELECTEDCOMPONENT, selectedComponentType);
        args.putBoolean(ARG_IS400HHZENABLED, is400KhzEnabled);
        fragment.setArguments(args);
        return fragment;
    }

    public NeopixelComponentSelectorFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            final int selectedComponentType = getArguments().getInt(ARG_SELECTEDCOMPONENT);
            mSelectedComponent = new NeopixelComponents(selectedComponentType);
            mIs400KhzEnabled = getArguments().getBoolean(ARG_IS400HHZENABLED);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Remove title
        AppCompatDialog dialog = (AppCompatDialog) getDialog();
        if (dialog != null) {
            dialog.supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        }

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_neopixel_componentselector, container, false);
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
            RecyclerView standardComponentsRecyclerView = view.findViewById(R.id.standardComponentsRecyclerView);
            standardComponentsRecyclerView.setHasFixedSize(true);
            standardComponentsRecyclerView.setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
            RecyclerView.Adapter standardBoardSizesAdapter = new StandardComponentsAdapter(mSelectedComponent, components -> {
                mSelectedComponent = components;
                if (mListener != null) {
                    mListener.onComponentsSelected(components, mIs400KhzEnabled);
                    dismiss();
                }
            });
            standardComponentsRecyclerView.setAdapter(standardBoardSizesAdapter);


            SwitchCompat mode400HhzSwitch = view.findViewById(R.id.mode400HhzSwitch);
            mode400HhzSwitch.setChecked(mIs400KhzEnabled);
            mode400HhzSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (buttonView.isPressed()) {
                    mIs400KhzEnabled = isChecked;
                    if (mListener != null) {
                        mListener.onComponentsSelected(mSelectedComponent, mIs400KhzEnabled);
                    }
                }
            });
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof NeopixelComponentSelectorFragmentListener) {
            mListener = (NeopixelComponentSelectorFragmentListener) context;
        } else if (getTargetFragment() instanceof NeopixelComponentSelectorFragmentListener) {
            mListener = (NeopixelComponentSelectorFragmentListener) getTargetFragment();
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

    // region StandardComponentsAdapter
    public static class StandardComponentsAdapter extends RecyclerView.Adapter<StandardComponentsAdapter.ViewHolder> {

        // Interface
        interface Listener {
            void onComponentSelected(NeopixelComponents components);
        }

        // Data
        private NeopixelComponents[] mDefaultComponents = NeopixelComponents.getAll();
        private Listener mListener;
        private int mSelectedComponentIndex;

        // ViewHolder
        class ViewHolder extends RecyclerView.ViewHolder {
            Button mItem;
            View mCheckboxView;

            ViewHolder(ViewGroup view) {
                super(view);
                mItem = view.findViewById(R.id.itemView);
                mCheckboxView = view.findViewById(R.id.itemCheckBox);
            }
        }

        StandardComponentsAdapter(NeopixelComponents selectedComponents, @NonNull Listener listener) {
            mSelectedComponentIndex = selectedComponents.getType();
            mListener = listener;
        }

        @SuppressWarnings("UnnecessaryLocalVariable")
        @NonNull
        @Override
        public StandardComponentsAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ViewGroup view = (ViewGroup) LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_neopixel_list_item, parent, false);
            final StandardComponentsAdapter.ViewHolder viewHolder = new StandardComponentsAdapter.ViewHolder(view);
            return viewHolder;
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

            NeopixelComponents components = mDefaultComponents[position];
            holder.mItem.setText(components.getComponentName());
            final boolean isCurrentType = components.getType() == mSelectedComponentIndex;
            holder.mCheckboxView.setVisibility(isCurrentType ? View.VISIBLE : View.GONE);

            holder.mItem.setOnClickListener(v -> {
                final int index = holder.getAdapterPosition();
                mSelectedComponentIndex = index;
                notifyDataSetChanged();
                if (mListener != null) {
                    mListener.onComponentSelected(mDefaultComponents[index]);
                }
            });
        }

        @Override
        public int getItemCount() {
            return mDefaultComponents.length;
        }
    }

    // endregion


    // region NeopixelComponentSelectorFragmentListener
    public interface NeopixelComponentSelectorFragmentListener {
        void onComponentsSelected(NeopixelComponents components, boolean is400KhzEnabled);
    }

    // endregion
}
package com.adafruit.bluefruit.le.connect.app;

import android.app.AlertDialog;
import android.bluetooth.le.AdvertiseCallback;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.ble.peripheral.PeripheralService;
import com.adafruit.bluefruit.le.connect.models.PeripheralModeViewModel;
import com.adafruit.bluefruit.le.connect.utils.DialogUtils;
import com.adafruit.bluefruit.le.connect.utils.LocalizationManager;

public class PeripheralModeFragment extends Fragment {
    // Constants
    private final static String TAG = PeripheralModeFragment.class.getSimpleName();
    //private final static int kRssiRefreshInterval = 300; // in milliseconds

    // UI
    private RecyclerView mRecyclerView;

    // region Fragment lifecycle
    public static PeripheralModeFragment newInstance() {
        return new PeripheralModeFragment();
    }

    public PeripheralModeFragment() {
        // Required empty public constructor
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
    }

    @Override
    public void onDetach() {
        super.onDetach();
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
        return inflater.inflate(R.layout.fragment_peripheralmode, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Context context = getContext();
        if (context != null) {

            // Peripherals recycler view
            mRecyclerView = view.findViewById(R.id.recyclerView);
            DividerItemDecoration itemDecoration = new DividerItemDecoration(context, DividerItemDecoration.VERTICAL);
            Drawable lineSeparatorDrawable = ContextCompat.getDrawable(context, R.drawable.simpledivideritemdecoration);
            assert lineSeparatorDrawable != null;
            itemDecoration.setDrawable(lineSeparatorDrawable);
            mRecyclerView.addItemDecoration(itemDecoration);

            //   recyclerView.setHasFixedSize(true);        // Improve performance
            RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getContext());
            mRecyclerView.setLayoutManager(layoutManager);
        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // ViewModel
        FragmentActivity activity = getActivity();
        if (activity != null) {
            //PeripheralModeViewModel model = new ViewModelProvider(activity).get(PeripheralModeViewModel.class);
            PeripheralModeViewModel model = new ViewModelProvider(activity, new ViewModelProvider.AndroidViewModelFactory(activity.getApplication())).get(PeripheralModeViewModel.class);

            model.getStartAdvertisingErrorCode().observe(this, errorCode -> {
                Log.d(TAG, "Advertising error: " + errorCode);

                if (errorCode != null) {
                    int messageId = errorCode == AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE ? R.string.peripheral_advertising_starterror_toolarge : R.string.peripheral_advertising_starterror_undefined;

                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    AlertDialog dialog = builder.setTitle(R.string.dialog_error).setMessage(messageId)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                    DialogUtils.keepDialogOnOrientationChanges(dialog);
                }
            });

            // RecyclerView Adapter
            PeripheralModeAdapter adapter = new PeripheralModeAdapter(activity, model, index -> {
                FragmentActivity activity2 = getActivity();
                if (activity2 != null) {
                    FragmentManager fragmentManager = activity2.getSupportFragmentManager();

                    Fragment fragment = null;
                    String fragmentTag = null;
                    switch (index) {
                        case 0:
                            fragment = DeviceInformationServiceFragment.newInstance();
                            fragmentTag = "DeviceInformationService";
                            break;
                        case 1:

                            fragment = UartServiceFragment.newInstance();
                            fragmentTag = "UartService";
                            break;
                    }

                    if (fragment != null) {
                        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction()
                                .setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right, R.anim.slide_in_right, R.anim.slide_out_left)
                                .replace(R.id.contentLayout, fragment, fragmentTag);
                        fragmentTransaction.addToBackStack(null);
                        fragmentTransaction.commit();
                    }

                }
            });
            mRecyclerView.setAdapter(adapter);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        FragmentActivity activity = getActivity();
        if (activity != null) {
            activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        }
    }

    // endregion

    private static class PeripheralModeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        // Constants
        private static final int kCellType_SectionTitle = 0;
        private static final int kCellType_AdvertisingInfoCell = 1;
        private static final int kCellType_ServicesCell = 2;

        private static final int kAdvertisingCellsStartPosition = 1;
        private static final int kServiceCellsStartPosition = 3;

        interface Listener {
            void onServiceSelected(int index);
        }

        // Structs
        class SectionViewHolder extends RecyclerView.ViewHolder {
            TextView titleTextView;

            SectionViewHolder(View view) {
                super(view);
                titleTextView = view.findViewById(R.id.titleTextView);
            }
        }

        class AdvertisingInfoViewHolder extends RecyclerView.ViewHolder {
            TextView titleTextView;
            EditText nameEditText;
            SwitchCompat enabledSwitch;

            AdvertisingInfoViewHolder(View view) {
                super(view);
                titleTextView = view.findViewById(R.id.titleTextView);
                nameEditText = view.findViewById(R.id.nameEditText);
                enabledSwitch = view.findViewById(R.id.enabledSwitch);
            }
        }

        class ServiceViewHolder extends RecyclerView.ViewHolder {
            ViewGroup mainViewGroup;
            TextView nameTextView;
            SwitchCompat enabledSwitch;

            ServiceViewHolder(View view) {
                super(view);
                mainViewGroup = view.findViewById(R.id.mainViewGroup);
                nameTextView = view.findViewById(R.id.nameTextView);
                enabledSwitch = view.findViewById(R.id.enabledSwitch);
            }
        }

        // Data
        private Context mContext;
        private PeripheralModeViewModel mModel;
        private Listener mListener;

        //
        PeripheralModeAdapter(@NonNull Context context, @NonNull PeripheralModeViewModel viewModel, @NonNull Listener listener) {
            mContext = context;
            mModel = viewModel;
            mListener = listener;
        }

        @Override
        public int getItemViewType(int position) {
            super.getItemViewType(position);

            if (position == kAdvertisingCellsStartPosition - 1 || position == kServiceCellsStartPosition - 1) {
                return kCellType_SectionTitle;
            } else if (position == kCellType_AdvertisingInfoCell) {
                return kCellType_AdvertisingInfoCell;
            } else {
                return kCellType_ServicesCell;
            }
        }

        // Create new views (invoked by the layout manager)
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            //Log.d(TAG, "onCreateViewHolder type: " + viewType);
            switch (viewType) {
                case kCellType_SectionTitle: {
                    View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_common_section_item, parent, false);
                    return new SectionViewHolder(view);
                }
                case kCellType_AdvertisingInfoCell: {
                    View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_peripheralmode_name_item, parent, false);
                    return new AdvertisingInfoViewHolder(view);
                }
                case kCellType_ServicesCell: {
                    View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_common_switch_item, parent, false);
                    return new ServiceViewHolder(view);
                }
                default: {
                    Log.e(TAG, "Unknown cell type");
                    throw new AssertionError("Unknown cell type");
                }
            }
        }

        // Replace the contents of a view (invoked by the layout manager)
        @Override
        public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder, int position) {

            LocalizationManager localizationManager = LocalizationManager.getInstance();
            final int viewType = getItemViewType(position);
            switch (viewType) {
                case kCellType_SectionTitle:
                    SectionViewHolder sectionViewHolder = (SectionViewHolder) holder;
                    sectionViewHolder.titleTextView.setText(localizationManager.getString(mContext, position == kAdvertisingCellsStartPosition - 1 ? "peripheral_advertisinginfo" : "peripheral_services"));
                    break;

                case kCellType_AdvertisingInfoCell:
                    AdvertisingInfoViewHolder advertisingInfoViewHolder = (AdvertisingInfoViewHolder) holder;
                    advertisingInfoViewHolder.titleTextView.setText(R.string.peripheral_localname);

                    final String localName = mModel.getLocalName();
                    advertisingInfoViewHolder.nameEditText.setText(localName);
                    advertisingInfoViewHolder.nameEditText.setHint(localName);
                    advertisingInfoViewHolder.nameEditText.addTextChangedListener(new TextWatcher() {

                        public void afterTextChanged(Editable s) {
                            String text = s.toString();
                            mModel.setLocalName(mContext, text);
                        }

                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                        }

                        public void onTextChanged(CharSequence s, int start, int before, int count) {
                        }
                    });

                    advertisingInfoViewHolder.enabledSwitch.setChecked(mModel.isIncludingDeviceName(mContext));
                    advertisingInfoViewHolder.enabledSwitch.setOnCheckedChangeListener((compoundButton, b) -> {
                        if (compoundButton.isPressed()) {
                            mModel.setIncludeDeviceName(mContext, b);
                        }
                    });

                    break;

                case kCellType_ServicesCell:
                    final int servicePosition = position - kServiceCellsStartPosition;
                    ServiceViewHolder serviceViewHolder = (ServiceViewHolder) holder;
                    final PeripheralService service = mModel.getPeripheralServices().get(servicePosition);
                    serviceViewHolder.enabledSwitch.setChecked(service.isEnabled());
                    serviceViewHolder.enabledSwitch.setOnCheckedChangeListener((compoundButton, b) -> {
                        if (compoundButton.isPressed()) {
                            service.setEnabled(mContext, b);
                            mModel.start(mContext);     // Force re-start advertising
                        }
                    });
                    serviceViewHolder.nameTextView.setText(service.getName());
                    serviceViewHolder.mainViewGroup.setOnClickListener(view -> mListener.onServiceSelected(servicePosition));
                    break;

                default:
                    Log.e(TAG, "Unknown cell type");
                    break;
            }
        }

        // Return the size of your dataset (invoked by the layout manager)
        @Override
        public int getItemCount() {
            return 1 + 1 + 1 + mModel.getPeripheralServices().size();
        }
    }
}
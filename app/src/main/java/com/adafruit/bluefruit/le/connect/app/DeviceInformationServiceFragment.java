package com.adafruit.bluefruit.le.connect.app;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.ble.peripheral.DeviceInformationPeripheralService;
import com.adafruit.bluefruit.le.connect.models.DeviceInformationServiceViewModel;

public class DeviceInformationServiceFragment extends Fragment {
    // Log
    private final static String TAG = DeviceInformationServiceFragment.class.getSimpleName();

    // UI
    private RecyclerView mRecyclerView;

    // region Fragment Lifecycle
    @SuppressWarnings("UnnecessaryLocalVariable")
    public static DeviceInformationServiceFragment newInstance() {
        DeviceInformationServiceFragment fragment = new DeviceInformationServiceFragment();
        return fragment;
    }

    public DeviceInformationServiceFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_deviceinformationservice, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity != null) {
            ActionBar actionBar = activity.getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(R.string.peripheral_dis_title);
                actionBar.setDisplayHomeAsUpEnabled(true);
            }
        }

        final Context context = getContext();
        if (context != null) {
            // Peripherals recycler view
            mRecyclerView = view.findViewById(R.id.recyclerView);
            DividerItemDecoration itemDecoration = new DividerItemDecoration(context, DividerItemDecoration.VERTICAL);
            Drawable lineSeparatorDrawable = ContextCompat.getDrawable(context, R.drawable.simpledivideritemdecoration);
            assert lineSeparatorDrawable != null;
            itemDecoration.setDrawable(lineSeparatorDrawable);
            mRecyclerView.addItemDecoration(itemDecoration);

            mRecyclerView.setHasFixedSize(true);        // Improves performance
            RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(context);
            mRecyclerView.setLayoutManager(layoutManager);
        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // ViewModel
        //DeviceInformationServiceViewModel model = new ViewModelProvider(this).get(DeviceInformationServiceViewModel.class);
        FragmentActivity activity = getActivity();
        final Context context = getContext();
        if (activity != null && context != null) {
            DeviceInformationServiceViewModel model = new ViewModelProvider(this, new ViewModelProvider.AndroidViewModelFactory(activity.getApplication())).get(DeviceInformationServiceViewModel.class);

            DeviceInformationServiceAdapter adapter = new DeviceInformationServiceAdapter(context, model);
            mRecyclerView.setAdapter(adapter);
        }
    }

    // endregion

    // region DeviceInformationServiceAdapter
    private static class DeviceInformationServiceAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        // Constants
        private static final int kCellType_SectionTitle = 0;
        private static final int kCellType_CharacteristicCell = 1;

        private static final int kCharacteristicsCellsStartPosition = 1;

        private final int[] kLabelStringIds = {R.string.peripheral_dis_manufacturer, R.string.peripheral_dis_modelnumber, R.string.peripheral_dis_serialnumber, R.string.peripheral_dis_hardwarenumber, R.string.peripheral_dis_firmwarerevision, R.string.peripheral_dis_softwarerevision};

        // Data Structures
        private class SectionViewHolder extends RecyclerView.ViewHolder {
            TextView titleTextView;

            SectionViewHolder(View view) {
                super(view);
                titleTextView = view.findViewById(R.id.titleTextView);
            }
        }

        private class CharacteristicViewHolder extends RecyclerView.ViewHolder {
            TextView titleTextView;
            EditText nameEditText;

            CharacteristicViewHolder(View view) {
                super(view);
                titleTextView = view.findViewById(R.id.titleTextView);
                nameEditText = view.findViewById(R.id.nameEditText);
            }
        }

        // Data
        private Context mContext;
        private DeviceInformationServiceViewModel mModel;

        // region Lifecycle
        DeviceInformationServiceAdapter(@NonNull Context context, @NonNull DeviceInformationServiceViewModel model) {
            mContext = context.getApplicationContext();
            mModel = model;
        }

        @Override
        public int getItemViewType(int position) {
            super.getItemViewType(position);

            if (position == 0) {
                return kCellType_SectionTitle;
            } else {
                return kCellType_CharacteristicCell;
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
                case kCellType_CharacteristicCell: {
                    View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_common_edittext_item, parent, false);
                    return new CharacteristicViewHolder(view);
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

            final int viewType = getItemViewType(position);
            switch (viewType) {
                case kCellType_SectionTitle:
                    SectionViewHolder sectionViewHolder = (SectionViewHolder) holder;
                    sectionViewHolder.titleTextView.setText(mContext.getString(R.string.peripheral_characteristics));
                    break;

                case kCellType_CharacteristicCell:
                    final int characteristicIndex = position - kCharacteristicsCellsStartPosition;
                    final DeviceInformationPeripheralService service = mModel.getDeviceInfomationPeripheralService();

                    CharacteristicViewHolder characteristicViewHolder = (CharacteristicViewHolder) holder;

                    String title = mContext.getString(kLabelStringIds[characteristicIndex]);
                    characteristicViewHolder.titleTextView.setText(title);
                    characteristicViewHolder.nameEditText.addTextChangedListener(new TextWatcher() {

                        public void afterTextChanged(Editable s) {
                            String text = s.toString();
                            switch (characteristicIndex) {
                                case 0:
                                    service.setManufacturer(text);
                                    break;
                                case 1:
                                    service.setModelNumber(text);
                                    break;
                                case 2:
                                    service.setSerialNumber(text);
                                    break;
                                case 3:
                                    service.setHardwareNumber(text);
                                    break;
                                case 4:
                                    service.setFirmwareNumber(text);
                                    break;
                                case 5:
                                    service.setSoftwareRevision(text);
                                    break;
                                default:
                                    break;
                            }
                        }

                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                        }

                        public void onTextChanged(CharSequence s, int start, int before, int count) {
                        }
                    });

                    String value;
                    switch (characteristicIndex) {
                        case 0:
                            value = service.getManufacturer();
                            break;
                        case 1:
                            value = service.getModelNumber();
                            break;
                        case 2:
                            value = service.getSerialNumber();
                            break;
                        case 3:
                            value = service.getHardwareNumber();
                            break;
                        case 4:
                            value = service.getFirmwareNumber();
                            break;
                        case 5:
                            value = service.getSoftwareRevision();
                            break;
                        default:
                            value = null;
                            break;
                    }

                    characteristicViewHolder.nameEditText.setText(value);
            }
        }

        @Override
        public int getItemCount() {
            return 1 + kLabelStringIds.length;
        }

        // endregion
    }

    // endregion
}

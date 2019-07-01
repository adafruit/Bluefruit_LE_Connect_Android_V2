package com.adafruit.bluefruit.le.connect.app;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.ble.BleUtils;
import com.adafruit.bluefruit.le.connect.ble.central.BlePeripheral;
import com.adafruit.bluefruit.le.connect.ble.central.BleUUIDNames;
import com.adafruit.bluefruit.le.connect.utils.LocalizationManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class InfoFragment extends ConnectedPeripheralFragment {
    // Log
    private final static String TAG = InfoFragment.class.getSimpleName();

    // Constants
    private final static UUID kDisServiceUUID = UUID.fromString("0000180A-0000-1000-8000-00805F9B34FB");

    // UI
    private InfoAdapter mInfoAdapter;
    private SwipeRefreshLayout mSwipeRefreshLayout;

    // Data
    private InfoData mInfoData = new InfoData();

    // region Fragment Lifecycle

    public static InfoFragment newInstance(@NonNull String singlePeripheralIdentifier) {
        InfoFragment fragment = new InfoFragment();
        fragment.setArguments(createFragmentArgs(singlePeripheralIdentifier));
        return fragment;
    }

    public InfoFragment() {
        // Required empty public constructor
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_info, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Update ActionBar
        setActionBarTitle(R.string.info_tab_title);

        final Context context = getContext();
        if (context != null) {
            // Peripherals recycler view
            RecyclerView infoRecyclerView = view.findViewById(R.id.infoRecyclerView);
            DividerItemDecoration itemDecoration = new DividerItemDecoration(context, DividerItemDecoration.VERTICAL);
            Drawable lineSeparatorDrawable = ContextCompat.getDrawable(context, R.drawable.simpledivideritemdecoration);
            assert lineSeparatorDrawable != null;
            itemDecoration.setDrawable(lineSeparatorDrawable);
            infoRecyclerView.addItemDecoration(itemDecoration);

            RecyclerView.LayoutManager infoLayoutManager = new LinearLayoutManager(getContext());
            infoRecyclerView.setLayoutManager(infoLayoutManager);

            // Disable update animation
            SimpleItemAnimator animator = (SimpleItemAnimator) infoRecyclerView.getItemAnimator();
            if (animator != null) {
                animator.setSupportsChangeAnimations(false);
            }

            // Adapter
            mInfoAdapter = new InfoAdapter(context, mInfoData);
            infoRecyclerView.setAdapter(mInfoAdapter);
        }

        // Swipe to refreshAll
        mSwipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        mSwipeRefreshLayout.setOnRefreshListener(() -> {
            refreshData();
            mSwipeRefreshLayout.postDelayed(() -> mSwipeRefreshLayout.setRefreshing(false), 500);
        });

        // Refresh
        refreshData();
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
                        CommonHelpFragment helpFragment = CommonHelpFragment.newInstance(getString(R.string.info_help_title), getString(R.string.info_help_text));
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
    private void updateUI() {
        mInfoAdapter.notifyDataSetChanged();

        /*
        // Show progress view if data is not ready yet
        final boolean isDataEmpty = mInfoListView.getChildCount() == 0;
        mWaitView.setVisibility(isDataEmpty ? View.VISIBLE : View.GONE);
*/
    }

    private void refreshData() {
        // Remove old data
        mInfoData.clear();

        // Services
        List<BluetoothGattService> services = mBlePeripheral == null ? null : mBlePeripheral.getServices();     // Check if mBlePeripheral is null (crash detected on Google logs)

        if (services != null) {
            // Order services so "DIS" is at the top (if present)
            Collections.sort(services, (serviceA, serviceB) -> {
                final boolean isServiceADis = serviceA.getUuid().equals(kDisServiceUUID);
                final boolean isServiceBDis = serviceB.getUuid().equals(kDisServiceUUID);
                return isServiceADis ? -1 : (isServiceBDis ? 1 : serviceA.getUuid().compareTo(serviceB.getUuid()));
            });

            final Handler mainHandler = new Handler(Looper.getMainLooper());
            // Discover characteristics and descriptors
            for (BluetoothGattService service : services) {
                // Service
                final UUID serviceUuid = service.getUuid();
                final int instanceId = service.getInstanceId();
                String serviceUUIDString = BleUtils.uuidToString(serviceUuid);
                String serviceName = BleUUIDNames.getNameForUUID(getContext(), serviceUUIDString);
                String finalServiceName = serviceName != null ? serviceName : LocalizationManager.getInstance().getString(getContext(), "info_type_service");
                ElementPath serviceElementPath = new ElementPath(serviceUuid, instanceId, null, null, finalServiceName, serviceUUIDString);
                mInfoData.mServices.add(serviceElementPath);

                // Characteristics
                List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                List<ElementPath> characteristicNamesList = new ArrayList<>(characteristics.size());
                for (final BluetoothGattCharacteristic characteristic : characteristics) {
                    final UUID characteristicUuid = characteristic.getUuid();
                    final String characteristicUuidString = BleUtils.uuidToString(characteristicUuid);
                    String characteristicName = BleUUIDNames.getNameForUUID(getContext(), characteristicUuidString);
                    String finalCharacteristicName = characteristicName != null ? characteristicName : characteristicUuidString;
                    final ElementPath characteristicElementPath = new ElementPath(serviceUuid, instanceId, characteristicUuid, null, finalCharacteristicName, characteristicUuidString);
                    characteristicNamesList.add(characteristicElementPath);

                    // Read characteristic
                    if (BlePeripheral.isCharacteristicReadable(service, characteristicUuid)) {
                        final String characteristicKey = characteristicElementPath.getKey();
                        mBlePeripheral.readCharacteristic(service, characteristicUuid, (status, data) -> {
                            if (status == BluetoothGatt.GATT_SUCCESS) {
                                mInfoData.mValuesMap.put(characteristicKey, data);

                                // Update UI
                                mainHandler.post(this::updateUI);
                            }
                        });

                        /*
                        mBlePeripheral.readCharacteristic(service, characteristicUuid, status -> {
                            if (status == BluetoothGatt.GATT_SUCCESS) {
                                mInfoData.mValuesMap.put(characteristicKey, characteristic.getValue());

                                // Update UI
                                mainHandler.post(this::updateUI);
                            }
                        });*/
                    }

                    // Descriptors
                    List<BluetoothGattDescriptor> descriptors = characteristic.getDescriptors();
                    List<ElementPath> descriptorNamesList = new ArrayList<>(descriptors.size());
                    for (final BluetoothGattDescriptor descriptor : descriptors) {
                        final UUID descriptorUuid = descriptor.getUuid();
                        final String descriptorUuidString = BleUtils.uuidToString(descriptorUuid);
                        String descriptorName = BleUUIDNames.getNameForUUID(getContext(), descriptorUuidString);
                        String finalDescriptorName = descriptorName != null ? descriptorName : descriptorUuidString;
                        final ElementPath descriptorElementPath = new ElementPath(serviceUuid, instanceId, characteristicUuid, descriptorUuid, finalDescriptorName, descriptorUuidString);
                        descriptorNamesList.add(descriptorElementPath);

                        // Read descriptor
//                    if (BlePeripheral.isDescriptorReadable(service, characteristicUuid, descriptorUuid)) {
                        final String descriptorKey = descriptorElementPath.getKey();
                        mBlePeripheral.readDescriptor(service, characteristicUuid, descriptorUuid, status -> {
                            if (status == BluetoothGatt.GATT_SUCCESS) {
                                mInfoData.mValuesMap.put(descriptorKey, descriptor.getValue());

                                // Update UI
                                mainHandler.post(this::updateUI);
                            }
                        });
//                    }
                    }

                    mInfoData.mDescriptors.put(characteristicElementPath.getKey(), descriptorNamesList);
                }
                mInfoData.mCharacteristics.put(serviceElementPath.getKey(), characteristicNamesList);
            }
        }

        updateUI();
    }

    // endregion

    // region Structures

    private class ElementPath {
        // Constants
        private final static int kDataFormat_Auto = -1;
        private final static int kDataFormat_String = 0;
        private final static int kDataFormat_Hex = 1;

        // Data
        UUID serviceUUID;
        int serviceInstance;
        UUID characteristicUUID;
        UUID descriptorUUID;
        String name;
        String uuidString;

        int dataFormat = kDataFormat_Auto;       // will try to display as string, if is not visible then display it as hex

        ElementPath(UUID serviceUUID, int serviceInstance, UUID characteristicUUID, UUID descriptorUUID, String name, String uuidString) {
            this.serviceUUID = serviceUUID;
            this.serviceInstance = serviceInstance;
            this.characteristicUUID = characteristicUUID;
            this.descriptorUUID = descriptorUUID;
            this.name = name;
            this.uuidString = uuidString;
        }

        @NonNull
        String getKey() {
            return (serviceUUID != null ? serviceUUID.toString() : "") + serviceInstance + (characteristicUUID != null ? characteristicUUID.toString() : "") + (descriptorUUID != null ? descriptorUUID.toString() : "");
        }

        @Override
        public String toString() {
            return name;
        }

        String getValueFormattedInGraphicCharacters(@Nullable byte[] value) {     // Note: take into account that this function can change the dataFormat variable
            String valueString = getValueFormatted(value, dataFormat);
            // if format is auto and the result is not visible, change the format to hex
            if (valueString != null && dataFormat == kDataFormat_Auto && !TextUtils.isGraphic(valueString)) {
                dataFormat = kDataFormat_Hex;
                valueString = getValueFormatted(value, dataFormat);
            }
            return valueString;
        }

        private String getValueFormatted(@Nullable byte[] value, int dataFormat) {
            String valueString = null;
            if (value != null) {
                if (dataFormat == kDataFormat_String || dataFormat == kDataFormat_Auto) {
                    valueString = new String(value);
                } else if (dataFormat == kDataFormat_Hex) {
                    String hexString = BleUtils.bytesToHex(value);
                    String[] hexGroups = splitStringEvery(hexString, 2);
                    valueString = TextUtils.join("-", hexGroups);
                }
            }

            return valueString;
        }

        @SuppressWarnings("SameParameterValue")
        private String[] splitStringEvery(String s, int interval) {         // based on: http://stackoverflow.com/questions/12295711/split-a-string-at-every-nth-position
            int arrayLength = (int) Math.ceil(((s.length() / (double) interval)));
            String[] result = new String[arrayLength];

            int j = 0;
            int lastIndex = result.length - 1;
            for (int i = 0; i < lastIndex; i++) {
                result[i] = s.substring(j, j + interval);
                j += interval;
            }
            if (lastIndex >= 0) {
                result[lastIndex] = s.substring(j);
            }

            return result;
        }
    }

    class InfoData {
        List<ElementPath> mServices;                            // List with service names
        Map<String, List<ElementPath>> mCharacteristics;        // Map with characteristics for service keys
        Map<String, List<ElementPath>> mDescriptors;            // Map with descriptors for characteristic keys
        Map<String, byte[]> mValuesMap;                         // Map with values for characteristic and descriptor keys

        InfoData() {
            mServices = new ArrayList<>();
            mCharacteristics = new HashMap<>();
            mDescriptors = new HashMap<>();
            mValuesMap = new HashMap<>();
        }

        void clear() {
            mServices.clear();
            mCharacteristics.clear();
            mDescriptors.clear();
            mValuesMap.clear();
        }
    }

    // endregion


    // region InfoAdapter

    static class InfoAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        // Constants
        private static final int kViewType_Service = 0;
        private static final int kViewType_Characteristic = 1;
        private static final int kViewType_Descriptor = 2;

        // ViewHolders
        class ServiceViewHolder extends RecyclerView.ViewHolder {
            TextView nameTextView;
            TextView uuidTextView;

            ServiceViewHolder(View view) {
                super(view);
                nameTextView = view.findViewById(R.id.nameTextView);
                uuidTextView = view.findViewById(R.id.uuidTextView);
            }
        }

        class CharacteristicViewHolder extends RecyclerView.ViewHolder {
            ViewGroup mainViewGroup;
            TextView nameTextView;
            TextView uuidTextView;
            TextView valueLabelTextView;
            TextView valueTextView;

            CharacteristicViewHolder(View view) {
                super(view);
                mainViewGroup = view.findViewById(R.id.mainViewGroup);
                nameTextView = view.findViewById(R.id.nameTextView);
                uuidTextView = view.findViewById(R.id.uuidTextView);
                valueLabelTextView = view.findViewById(R.id.valueLabelTextView);
                valueTextView = view.findViewById(R.id.valueTextView);
            }
        }

        class DescriptorViewHolder extends RecyclerView.ViewHolder {
            ViewGroup mainViewGroup;
            TextView nameTextView;
            TextView uuidTextView;
            TextView valueLabelTextView;
            TextView valueTextView;

            DescriptorViewHolder(View view) {
                super(view);
                mainViewGroup = view.findViewById(R.id.mainViewGroup);
                nameTextView = view.findViewById(R.id.nameTextView);
                uuidTextView = view.findViewById(R.id.uuidTextView);
                valueLabelTextView = view.findViewById(R.id.valueLabelTextView);
                valueTextView = view.findViewById(R.id.valueTextView);
            }
        }

        // Data
        private Context mContext;
        private InfoData mInfoData;

        InfoAdapter(@NonNull Context context, InfoData infoData) {
            mContext = context;
            mInfoData = infoData;
        }

        @Override
        public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);

            //mRecyclerView = recyclerView;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final int resource = viewType == kViewType_Service ? R.layout.layout_info_service_item : viewType == kViewType_Characteristic ? R.layout.layout_info_characteristic_item : R.layout.layout_info_descriptor_item;
            View view = LayoutInflater.from(parent.getContext()).inflate(resource, parent, false);
            switch (viewType) {
                case kViewType_Characteristic:
                    return new CharacteristicViewHolder(view);
                case kViewType_Descriptor:
                    return new DescriptorViewHolder(view);
                default:
                    return new ServiceViewHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            final ElementPath elementPath = getElementPath(position);
            if (elementPath != null) {
                if (holder instanceof ServiceViewHolder) {
                    ServiceViewHolder viewHolder = (ServiceViewHolder) holder;
                    viewHolder.nameTextView.setText(elementPath.name);
                    viewHolder.uuidTextView.setText(elementPath.uuidString);
                } else if (holder instanceof CharacteristicViewHolder) {
                    CharacteristicViewHolder viewHolder = (CharacteristicViewHolder) holder;
                    viewHolder.nameTextView.setText(elementPath.name);
                    viewHolder.uuidTextView.setText(elementPath.uuidString);
                    final byte[] value = mInfoData.mValuesMap.get(elementPath.getKey());
                    final String valueString = elementPath.getValueFormattedInGraphicCharacters(value);     // Note: take into account that this function can change the dataFormat variable
                    viewHolder.valueLabelTextView.setText(mContext.getString(elementPath.dataFormat == ElementPath.kDataFormat_Hex ? R.string.info_data_hex : R.string.info_data_dec));
                    viewHolder.valueTextView.setText(valueString);
                    viewHolder.mainViewGroup.setOnClickListener(view -> {
                        switch (elementPath.dataFormat) {
                            case ElementPath.kDataFormat_Hex:
                                elementPath.dataFormat = ElementPath.kDataFormat_String;
                                break;
                            default:
                                elementPath.dataFormat = ElementPath.kDataFormat_Hex;
                                break;
                        }

                        notifyItemChanged(position);
                    });
                } else if (holder instanceof DescriptorViewHolder) {
                    DescriptorViewHolder viewHolder = (DescriptorViewHolder) holder;
                    viewHolder.nameTextView.setText(elementPath.name);
                    final byte[] value = mInfoData.mValuesMap.get(elementPath.getKey());
                    final String valueString = elementPath.getValueFormattedInGraphicCharacters(value);     // Note: take into account that this function can change the dataFormat variable
                    viewHolder.valueLabelTextView.setText(mContext.getString(elementPath.dataFormat == ElementPath.kDataFormat_Hex ? R.string.info_data_hex : R.string.info_data_dec));
                    viewHolder.valueTextView.setText(valueString);
                    viewHolder.mainViewGroup.setOnClickListener(view -> {
                        switch (elementPath.dataFormat) {
                            case ElementPath.kDataFormat_Hex:
                                elementPath.dataFormat = ElementPath.kDataFormat_String;
                                break;
                            default:
                                elementPath.dataFormat = ElementPath.kDataFormat_Hex;
                                break;
                        }
                        notifyItemChanged(position);

                    });
                }
            } else {
                Log.e(TAG, "elementPath is null");
            }
        }

        @Override
        public int getItemCount() {
            final int numGroups = getGroupCount();

            /*
            if(mIsGroupExpanded.size() < numGroups) { // Hack to expand group expanded so it always has as many elements as services
                final int numItemsToAdd = numGroups - mIsGroupExpanded.size();
                for (int i =0; i< numItemsToAdd; i++) {
                    mIsGroupExpanded.add(false);
                }
            }*/

            int numChildren = 0;
            for (int i = 0; i < numGroups; i++) {
                //if (mIsGroupExpanded.get(i)) {
                numChildren += getChildrenCount(i);
                //}
            }
            return numGroups + numChildren;
        }

        @Override
        public int getItemViewType(int position) {
            int serviceId = 0;
            int index = 0;
            while (index < position) {
                //if (mIsGroupExpanded.get(index)) {
                final String serviceKey = mInfoData.mServices.get(serviceId).getKey();
                List<ElementPath> characteristics = mInfoData.mCharacteristics.get(serviceKey);
                final int numCharacteristicsInService = characteristics == null ? 0 : characteristics.size();
                for (int characteristicId = 0; characteristicId < numCharacteristicsInService; characteristicId++) {

                    index++;   // Add Characteristic
                    if (index >= position) {
                        return kViewType_Characteristic;
                    } else {
                        final String characteristicKey = characteristics.get(characteristicId).getKey();
                        List<ElementPath> descriptors = mInfoData.mDescriptors.get(characteristicKey);
                        final int numDescriptors = descriptors == null ? 0 : descriptors.size();
                        index += numDescriptors;
                        if (index >= position) {
                            return kViewType_Descriptor;
                        }
                    }
                }
                //}

                // add service
                serviceId++;
                index++;
            }

            return kViewType_Service;
        }

        private ElementPath getElementPath(int position) {
            if (mInfoData.mServices.size() == 0) {
                return null;
            }

            int serviceId = 0;
            int index = 0;
            while (index < position) {
                //if (mIsGroupExpanded.get(index)) {
                final String serviceKey = mInfoData.mServices.get(serviceId).getKey();
                final List<ElementPath> characteristics = mInfoData.mCharacteristics.get(serviceKey);
                final int numCharacteristicsInService = characteristics == null ? 0 : characteristics.size();
                for (int characteristicId = 0; characteristicId < numCharacteristicsInService; characteristicId++) {

                    index++;   // Add Characteristic
                    if (index >= position) {
                        return characteristics.get(characteristicId);
                    } else {
                        final String characteristicKey = characteristics.get(characteristicId).getKey();
                        final List<ElementPath> descriptors = mInfoData.mDescriptors.get(characteristicKey);
                        final int numDescriptors = descriptors == null ? 0 : descriptors.size();
                        if (numDescriptors > 0 && index + numDescriptors >= position) {
                            return descriptors.get(position - index - 1);
                        } else {
                            index += numDescriptors;
                        }
                    }
                }
                //}

                // add service
                serviceId++;
                index++;
            }

            return mInfoData.mServices.get(serviceId);
        }

        private int getGroupCount() {
            return mInfoData.mServices != null ? mInfoData.mServices.size() : 0;
        }

        private int getChildrenCount(int groupPosition) {
            final String serviceKey = mInfoData.mServices.get(groupPosition).getKey();
            final List<ElementPath> characteristics = mInfoData.mCharacteristics.get(serviceKey);
            final int numCharacteristicsInService = characteristics == null ? 0 : characteristics.size();
            int totalNumDescriptors = 0;
            for (int i = 0; i < numCharacteristicsInService; i++) {
                final String characteristicKey = characteristics.get(i).getKey();
                List<ElementPath> descriptors = mInfoData.mDescriptors.get(characteristicKey);
                totalNumDescriptors += descriptors == null ? 0 : descriptors.size();
            }
            return numCharacteristicsInService + totalNumDescriptors;
        }
    }

    // endregion
}
package com.adafruit.bluefruit.le.connect.app;

import android.content.Context;
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.ble.BleUtils;
import com.adafruit.bluefruit.le.connect.ble.central.BlePeripheral;
import com.adafruit.bluefruit.le.connect.ble.central.BleScanner;
import com.adafruit.bluefruit.le.connect.style.RssiUI;
import com.adafruit.bluefruit.le.connect.utils.AdapterUtils;
import com.adafruit.bluefruit.le.connect.utils.KeyboardUtils;
import com.adafruit.bluefruit.le.connect.utils.LocalizationManager;
import com.adafruit.bluefruit.le.connect.utils.UriBeaconUtils;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;

import no.nordicsemi.android.support.v18.scanner.ScanRecord;

import static com.adafruit.bluefruit.le.connect.ble.central.BleScanner.kDeviceType_Beacon;
import static com.adafruit.bluefruit.le.connect.ble.central.BleScanner.kDeviceType_Uart;
import static com.adafruit.bluefruit.le.connect.ble.central.BleScanner.kDeviceType_UriBeacon;

class BlePeripheralsAdapter extends RecyclerView.Adapter<BlePeripheralsAdapter.ViewHolder> {
    // Constants
    @SuppressWarnings("unused")
    private final static String TAG = BlePeripheralsAdapter.class.getSimpleName();

    interface Listener {
        void onAdvertisementData(@NonNull BlePeripheral blePeripheral);
    }

    // Data
    private List<BlePeripheral> mBlePeripherals;
    private Context mContext;
    private RecyclerView mRecyclerView;
    private Listener mListener;

    BlePeripheralsAdapter(@NonNull Context context, @NonNull Listener listener) {
        mContext = context.getApplicationContext();
        mListener = listener;
    }

    private static String deviceDescription(Context context, BlePeripheral blePeripheral) {
        final int deviceType = BleScanner.getDeviceType(blePeripheral);

        String description;
        switch (deviceType) {
            case kDeviceType_Uart:
                description = context.getString(R.string.scanner_uartavailable);
                break;
            case kDeviceType_Beacon:
                description = context.getString(R.string.scanner_beacon);
                break;
            case kDeviceType_UriBeacon:
                description = context.getString(R.string.scanner_uribeacon);
                break;
            default:
                description = null;
                break;
        }

        return description;
    }

    private static Spanned getAdvertisementDescription(Context context, BlePeripheral blePeripheral) {
        final int deviceType = BleScanner.getDeviceType(blePeripheral);

        String text;
        switch (deviceType) {
            case kDeviceType_Beacon:
                text = getBeaconAdvertisementDescription(context, blePeripheral);
                break;

            case kDeviceType_UriBeacon:
                text = getUriBeaconAdvertisementDescription(context, blePeripheral);
                break;

            default:
                text = getCommonAdvertisementDescription(context, blePeripheral);
                break;
        }

        Spanned result;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            result = Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY);
        } else {
            result = Html.fromHtml(text);
        }
        return result;
    }

    private static String getUriBeaconAdvertisementDescription(Context context, BlePeripheral blePeripheral) {
        StringBuilder result = new StringBuilder();

        final String name = blePeripheral.getDevice().getName();
        if (name != null) {
            result.append(context.getString(R.string.scanresult_advertisement_localname)).append(": <b>").append(name).append("</b><br>");
        }

        final String address = blePeripheral.getDevice().getAddress();
        result.append(context.getString(R.string.scanresult_advertisement_address)).append(": <b>").append(address == null ? "" : address).append("</b><br>");

        final ScanRecord scanRecord = blePeripheral.getScanRecord();
        if (scanRecord != null) {
            byte[] scanRecordBytes = scanRecord.getBytes();
            if (scanRecordBytes != null) {
                String uri = UriBeaconUtils.getUriFromAdvertisingPacket(scanRecordBytes) + "</b><br>";
                result.append(context.getString(R.string.scanresult_advertisement_uribeacon_uri)).append(": <b>").append(uri);

                final int txPower = scanRecord.getTxPowerLevel();
                if (txPower > -255) {       // is valid?
                    result.append(context.getString(R.string.scanresult_advertisement_txpower)).append(": <b>").append(txPower).append("</b>");
                }
            }
        }

        return result.toString();
    }

    private static String getCommonAdvertisementDescription(Context context, BlePeripheral blePeripheral) {
        StringBuilder result = new StringBuilder();

        final String name = blePeripheral.getDevice().getName();
        if (name != null) {
            result.append(context.getString(R.string.scanresult_advertisement_localname)).append(": <b>").append(name).append("</b><br>");
        }
        final String address = blePeripheral.getDevice().getAddress();
        result.append(context.getString(R.string.scanresult_advertisement_address)).append(": <b>").append(address == null ? "" : address).append("</b><br>");

        StringBuilder serviceText = new StringBuilder();
        ParcelUuid[] uuids = blePeripheral.getDevice().getUuids();
        if (uuids != null) {
            int i = 0;
            for (ParcelUuid uuid : uuids) {
                if (i > 0) serviceText.append(", ");
                serviceText.append(uuid.toString().toUpperCase());
                i++;
            }
        }
        if (!serviceText.toString().isEmpty()) {
            result.append(context.getString(R.string.scanresult_advertisement_servicesuuids)).append(": <b>").append(serviceText).append("</b><br>");
        }

        ScanRecord scanRecord = blePeripheral.getScanRecord();
        if (scanRecord != null) {
            final int txPower = scanRecord.getTxPowerLevel();
            if (txPower > -255) {       // is valid?
                result.append(context.getString(R.string.scanresult_advertisement_txpower)).append(": <b>").append(txPower).append("</b>");
            }
        }

        return result.toString();
    }

    // region UI Utils

    private static String getBeaconAdvertisementDescription(Context context, BlePeripheral blePeripheral) {
        StringBuilder result = new StringBuilder();

        final String name = blePeripheral.getDevice().getName();
        if (name != null) {
            result.append(context.getString(R.string.scanresult_advertisement_localname)).append(": <b>").append(name).append("</b><br>");
        }
        final String address = blePeripheral.getDevice().getAddress();
        result.append(context.getString(R.string.scanresult_advertisement_address)).append(": <b>").append(address == null ? "" : address).append("</b><br>");

        ScanRecord scanRecord = blePeripheral.getScanRecord();
        if (scanRecord != null) {
            byte[] advertisedBytes = scanRecord.getBytes();
            if (advertisedBytes != null && advertisedBytes.length > 6) {
                final byte[] manufacturerBytes = {advertisedBytes[6], advertisedBytes[5]};      // Little endian
                String manufacturerId = BleUtils.bytesToHex(manufacturerBytes);
                String manufacturer = getManufacturerName(manufacturerId);
                result.append(context.getString(R.string.scanresult_advertisement_manufacturer)).append(": <b>").append(manufacturer == null ? "" : manufacturer).append("</b><br>");
            }
        }

        StringBuilder text = new StringBuilder();
        ParcelUuid[] uuids = blePeripheral.getDevice().getUuids();
        if (uuids != null && uuids.length == 1) {
            ParcelUuid uuid = uuids[0];
            text.append(uuid.toString().toUpperCase());
        }
        result.append(context.getString(R.string.scanresult_advertisement_beacon_major)).append(": <b>").append(text).append("</b><br>");

        if (scanRecord != null) {
            byte[] advertisedBytes = scanRecord.getBytes();
            if (advertisedBytes != null && advertisedBytes.length > 26) {
                final byte[] majorBytes = {advertisedBytes[25], advertisedBytes[26]};           // Big endian
                final String major = BleUtils.bytesToHex(majorBytes);
                result.append(context.getString(R.string.scanresult_advertisement_beacon_major)).append(": <b>").append(major).append("</b><br>");
            }

            if (advertisedBytes != null && advertisedBytes.length > 28) {
                final byte[] minorBytes = {advertisedBytes[27], advertisedBytes[28]};           // Big endian
                final String minor = BleUtils.bytesToHex(minorBytes);
                result.append(context.getString(R.string.scanresult_advertisement_beacon_minor)).append(": <b>").append(minor).append("</b><br>");
            }

            final int txPower = scanRecord.getTxPowerLevel();
            if (txPower > -255) {       // is valid?
                result.append(context.getString(R.string.scanresult_advertisement_txpower)).append(": <b>").append(txPower).append("</b>");
            }
        }

        return result.toString();
    }

    private static String getManufacturerName(String manufacturerId) {
        final String[] kKnownManufacturers = {"004C", "0059", "0822"};
        final String[] kManufacturerNames = {"Apple (004C)", "Nordic (0059)", "Adafruit (0822)"};

        String result;
        int knownIndex = Arrays.asList(kKnownManufacturers).indexOf(manufacturerId);
        if (knownIndex >= 0) {
            result = kManufacturerNames[knownIndex] + "  (" + kKnownManufacturers[knownIndex] + ")";
        } else {
            result = manufacturerId;

        }
        return result;
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);

        mRecyclerView = recyclerView;
    }

    void setBlePeripherals(final List<BlePeripheral> blePeripherals) {
        if (mBlePeripherals == null) {
            mBlePeripherals = blePeripherals;
            notifyItemRangeInserted(0, mBlePeripherals != null ? mBlePeripherals.size() : 0);
        } else {
            DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                    return mBlePeripherals.size();
                }

                @Override
                public int getNewListSize() {
                    return blePeripherals.size();
                }

                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                    final String oldItemAddress = mBlePeripherals.get(oldItemPosition).getDevice().getAddress();
                    final String newItemAddress = blePeripherals.get(newItemPosition).getDevice().getAddress();

                    return oldItemAddress.equals(newItemAddress);
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    return mBlePeripherals.get(oldItemPosition).equals(blePeripherals.get(newItemPosition));
/*
                    ScanResult oldScanResult = mBlePeripherals.get(oldItemPosition);
                    ScanResult newScanResult = scanResults.get(newItemPosition);

                    return getDrawableIdForRssi(oldScanResult.getRssi()) == getDrawableIdForRssi(newScanResult.getRssi());
*/
                }
            });
            mBlePeripherals = blePeripherals;

            // Save and restore state to preserve user scroll
            // https://stackoverflow.com/questions/43458146/diffutil-in-recycleview-making-it-autoscroll-if-a-new-item-is-added
            RecyclerView.LayoutManager layoutManager = mRecyclerView.getLayoutManager();
            if (layoutManager != null) {
                Parcelable recyclerViewState = layoutManager.onSaveInstanceState();
                result.dispatchUpdatesTo(this);
                layoutManager.onRestoreInstanceState(recyclerViewState);
            }

//            result.dispatchUpdatesTo((ListUpdateCallback)this);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_scan_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, int position) {
        final BlePeripheral blePeripheral = mBlePeripherals.get(position);

        final String identifier = blePeripheral.getIdentifier();
        //   if (!identifier.equalsIgnoreCase(holder.deviceAddress)) {           // only update when changed (to avoid onclicklistener problems)
        //Log.d(TAG, "Bind View Holder for address: " + deviceAddress);

        // Main view
        String name = blePeripheral.getName();
        if (name == null) {
            name = identifier;
        }

        holder.deviceAddress = identifier;
        holder.nameTextView.setText(name);
        final String description = deviceDescription(mContext, blePeripheral);
        holder.descriptionTextView.setVisibility(description != null ? View.VISIBLE : View.GONE);
        holder.descriptionTextView.setText(description);

        final int rssiDrawableResource = RssiUI.getDrawableIdForRssi(blePeripheral.getRssi());
        holder.rssiImageView.setImageResource(rssiDrawableResource);

        holder.connectButton.setTag(position);
        final int connectionState = blePeripheral.getConnectionState();
        final String connectionAction = connectionState == BlePeripheral.STATE_DISCONNECTED ? "scanner_connect" : "scanner_disconnect";
        holder.connectButton.setText(LocalizationManager.getInstance().getString(mContext, connectionAction));

        final WeakReference<BlePeripheral> weakBlePeripheral = new WeakReference<>(blePeripheral);
        holder.connectButton.setOnClickListener(view -> {
            KeyboardUtils.dismissKeyboard(view);

            BlePeripheral selectedBlePeripheral = weakBlePeripheral.get();
            if (selectedBlePeripheral != null) {
                if (connectionState == BlePeripheral.STATE_DISCONNECTED) {
                    selectedBlePeripheral.connect(mContext);
                } else {
                    selectedBlePeripheral.disconnect();
                }
            }
        });
        holder.view.setOnClickListener(view -> holder.togleExpanded());

        // Expanded view
        Spanned text = getAdvertisementDescription(mContext, blePeripheral);
        holder.dataTextView.setText(text);

        holder.rawDataButton.setOnClickListener(v -> mListener.onAdvertisementData(blePeripheral));
    }

    @Override
    public int getItemCount() {
        return mBlePeripherals == null ? 0 : mBlePeripherals.size();
    }

    // region ViewHolder
    static class ViewHolder extends RecyclerView.ViewHolder {
        View view;
        TextView nameTextView;
        TextView descriptionTextView;
        ImageView rssiImageView;
        Button connectButton;
        Button rawDataButton;
        TextView dataTextView;
        String deviceAddress;

        private boolean isExpanded = false;
        private ViewGroup expandedViewGroup;

        ViewHolder(View view) {
            super(view);
            this.view = view;
            nameTextView = view.findViewById(R.id.nameTextView);
            descriptionTextView = view.findViewById(R.id.descriptionTextView);
            rssiImageView = view.findViewById(R.id.rssiImageView);
            connectButton = view.findViewById(R.id.connectButton);
            deviceAddress = null;
            expandedViewGroup = view.findViewById(R.id.expandedViewGroup);
            expandedViewGroup.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
            rawDataButton = view.findViewById(R.id.rawDataButton);
            dataTextView = view.findViewById(R.id.dataTextView);
        }

        void togleExpanded() {
            isExpanded = !isExpanded;
            animateExpanded();
        }

        private void animateExpanded() {
            //          expandedViewGroup.setVisibility(isExpanded ? View.VISIBLE:View.GONE);
            if (isExpanded) {
                AdapterUtils.expand(expandedViewGroup);
            } else {
                AdapterUtils.collapse(expandedViewGroup);
            }
        }
    }
    // endregion

/*
    // Trick to show items added on top
    // https://stackoverflow.com/questions/43458146/diffutil-in-recycleview-making-it-autoscroll-if-a-new-item-is-added
    private boolean mIsFirstItemModified = false;
    public boolean getAndResetFirstItemModified() {
        boolean result = mIsFirstItemModified;
        mIsFirstItemModified = false;
        return result;
    }

    // region ListUpdateCallback

    @Override
    public void onInserted(int position, int count) {
        if (position == 0) {
            mIsFirstItemModified = true;
        }
        notifyItemRangeInserted(position, count);
    }

    @Override
    public void onRemoved(int position, int count) {
        notifyItemRangeRemoved(position, count);
    }

    @Override
    public void onMoved(int fromPosition, int toPosition) {
        notifyItemMoved(fromPosition, toPosition);
    }

    @Override
    public void onChanged(int position, int count, Object payload) {
        notifyItemRangeChanged(position, count, payload);
    }


    // endregion
    */
}
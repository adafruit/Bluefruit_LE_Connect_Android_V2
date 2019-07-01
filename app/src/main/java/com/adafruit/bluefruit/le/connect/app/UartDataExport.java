package com.adafruit.bluefruit.le.connect.app;

import androidx.annotation.Nullable;

import com.adafruit.bluefruit.le.connect.ble.BleUtils;
import com.adafruit.bluefruit.le.connect.ble.UartPacket;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

class UartDataExport {

    static String packetsAsText(List<UartPacket> packets, boolean isHexFormat) {
        // Compile all data
        ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
        for (UartPacket packet : packets) {
            try {
                dataStream.write(packet.getData());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Convert to text
        byte[] data = dataStream.toByteArray();
        return isHexFormat ? BleUtils.bytesToHex2(data) : BleUtils.bytesToText(data, true);
    }

    static String packetsAsCsv(List<UartPacket> packets, boolean isHexFormat) {
        StringBuilder text = new StringBuilder("Timestamp,Mode,Data\r\n");        // csv Header

        DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss:SSS", Locale.US);

        for (UartPacket packet : packets) {
            Date date = new Date(packet.getTimestamp());
            String dateString = dateFormat.format(date).replace(",", ".");      //  comma messes with csv, so replace it by a point
            String mode = packet.getMode() == UartPacket.TRANSFERMODE_RX ? "RX" : "TX";
            String dataString = isHexFormat ? BleUtils.bytesToHex2(packet.getData()) : BleUtils.bytesToText(packet.getData(), true);

            // Remove newline characters from data (it messes with the csv format and Excel wont recognize it)
            dataString = dataString.trim();
            text.append(String.format(Locale.ENGLISH, "%s,%s,%s\r\n", dateString, mode, dataString));
        }

        return text.toString();
    }

    @Nullable
    static String packetsAsJson(List<UartPacket> packets, boolean isHexFormat) {

        JSONArray jsonItemsArray = new JSONArray();

        for (UartPacket packet : packets) {
            long unixTime = packet.getTimestamp() / 1000L;
            String mode = packet.getMode() == UartPacket.TRANSFERMODE_RX ? "RX" : "TX";
            String dataString = isHexFormat ? BleUtils.bytesToHex2(packet.getData()) : BleUtils.bytesToText(packet.getData(), true);

            JSONObject jsonItem = new JSONObject();
            try {
                jsonItem.put("timestamp", unixTime);
                jsonItem.put("mode", mode);
                jsonItem.put("data", dataString);
                jsonItemsArray.put(jsonItem);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        String result = null;
        JSONObject json = new JSONObject();
        try {
            json.put("items", jsonItemsArray);
            result = json.toString(2);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return result;
    }

    @Nullable
    static byte[] packetsAsBinary(List<UartPacket> packets) {
        if (!packets.isEmpty()) {
            ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
            for (UartPacket packet : packets) {
                try {
                    dataStream.write(packet.getData());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            return dataStream.toByteArray();
        } else {
            return null;
        }
    }
}

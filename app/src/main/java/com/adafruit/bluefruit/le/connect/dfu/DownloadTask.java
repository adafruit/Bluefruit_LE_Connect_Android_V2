package com.adafruit.bluefruit.le.connect.dfu;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;

class DownloadTask extends AsyncTask<Uri, Integer, ByteArrayOutputStream> {
    // Log
    private final static String TAG = DownloadTask.class.getSimpleName();

    // Data
    private final WeakReference<Context> mWeakContext;
    private PowerManager.WakeLock mWakeLock;
    private Listener mListener;
    private int mOperationId;
    private Uri mUri;
    private Object mTag;

    DownloadTask(@NonNull Context context, int operationId, @NonNull Listener listener) {
        mWeakContext = new WeakReference<>(context.getApplicationContext());
        mListener = listener;
        mOperationId = operationId;
    }

    void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    @Override
    protected ByteArrayOutputStream doInBackground(Uri... sUrl) {
        InputStream input = null;
        ByteArrayOutputStream output = null;
        HttpURLConnection connection = null;
        try {
            mUri = sUrl[0];

            int fileLength = 0;
            String uriScheme = mUri.getScheme();
            //Log.d(TAG, "Downloading from " + uriScheme);
            boolean shouldBeConsideredAsInputStream = uriScheme != null && (uriScheme.equalsIgnoreCase("file") || uriScheme.equalsIgnoreCase("content"));
            if (shouldBeConsideredAsInputStream) {
                input = mWeakContext.get().getContentResolver().openInputStream(mUri);
            } else {
                URL url = new URL(mUri.toString());
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                // expect HTTP 200 OK, so we don't mistakenly save error report instead of the file
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    return null;
                }

                // this will be useful to display download percentage  might be -1: server did not report the length
                fileLength = connection.getContentLength();

                // download the file
                input = connection.getInputStream();
            }
            //Log.d(TAG, "\tFile size: " + fileLength);

            if (input != null) {
                // download the file
                output = new ByteArrayOutputStream();

                byte[] data = new byte[4096];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    // allow cancelling
                    if (isCancelled()) {
                        input.close();
                        return null;
                    }
                    total += count;

                    // publishing the progress....
                    if (fileLength > 0) {   // only if total length is known
                        publishProgress((int) (total * 100 / fileLength));
                    }
                    output.write(data, 0, count);
                    //Log.d(TAG, "Downloading data: " + total);
                }
            } else {
                Log.e(TAG, "DownloadTask with null input");
            }

        } catch (Exception e) {
            Log.w(TAG, "Error DownloadTask " + e);
            return null;
        } finally {
            Log.d(TAG, "Download: Release download objects");
            try {
                if (output != null) {
                    output.close();
                }
                if (input != null) {
                    input.close();
                }
            } catch (IOException ignored) {
            }

            if (connection != null) {
                connection.disconnect();
            }
        }
        Log.d(TAG, "Download:Finished");
        return output;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        // take CPU lock to prevent CPU from going off if the user presses the power button during download
        PowerManager powerManager = (PowerManager) mWeakContext.get().getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
            mWakeLock.acquire();
        }
    }

    @Override
    protected void onProgressUpdate(Integer... progress) {
        super.onProgressUpdate(progress);

        if (mListener != null) {
            mListener.onDownloadProgress(mOperationId, progress[0]);
        }
    }

    @Override
    protected void onPostExecute(ByteArrayOutputStream result) {
        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }

        if (mListener != null) {
            mListener.onDownloadCompleted(mOperationId, mUri, result);
            mListener = null;
        }
    }

    interface Listener {
        void onDownloadProgress(int operationId, int progress);

        void onDownloadCompleted(int operationId, @NonNull Uri uri, @Nullable ByteArrayOutputStream result);
    }

    public Object getTag() {
        return mTag;
    }

    public void setTag(Object tag) {
        this.mTag = tag;
    }
}
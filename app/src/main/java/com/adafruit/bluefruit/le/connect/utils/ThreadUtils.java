package com.adafruit.bluefruit.le.connect.utils;

public class ThreadUtils {

    // https://stackoverflow.com/questions/34871580/android-how-to-group-async-tasks-together-like-in-ios
    public static class DispatchGroup {

        private int mCount;
        private Runnable mRunnable;

        public DispatchGroup() {
            super();
            mCount = 0;
        }

        public synchronized void enter() {
            mCount++;
        }

        public synchronized void leave() {
            mCount--;
            notifyGroup();
        }

        public void notify(Runnable r) {
            mRunnable = r;
            notifyGroup();
        }

        private void notifyGroup() {
            if (mCount <= 0 && mRunnable != null) {
                mRunnable.run();
            }
        }
    }
}

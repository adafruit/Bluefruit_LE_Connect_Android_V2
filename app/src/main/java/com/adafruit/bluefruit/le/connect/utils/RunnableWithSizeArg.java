package com.adafruit.bluefruit.le.connect.utils;

public abstract class RunnableWithSizeArg implements Runnable {
    private int writtenSize;
    public RunnableWithSizeArg(int writtenSize){
        this.writtenSize = writtenSize;
    }

    public int getWrittenSize(){
        return this.writtenSize;
    }
}

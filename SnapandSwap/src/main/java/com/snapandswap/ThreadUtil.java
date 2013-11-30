package com.snapandswap;

import android.os.Bundle;
import android.util.Log;

public class ThreadUtil {
	
	public static long getThreadId() {
		Thread t = Thread.currentThread();
		return t.getId();
	}

	public static String getThreadSig() {
		Thread t = Thread.currentThread();
		long l = t.getId();
		String name = t.getName();
		long p = t.getPriority();
		String gname = t.getThreadGroup().getName();
		return (name
				+ ":(id)" + l
				+ ":(priority)" + p
				+ ":(group)" + gname);
	}

	public static void logThreadSignature(){
		Log.d(CameraActivity.TAG, getThreadSig());
	}
	
	public static void sleepForInSecs(int secs){
		try{
			Thread.sleep(secs * 1000);
		} catch(InterruptedException x){
			throw new RuntimeException("interrupted",x);
		}
	}
	
	//The following two methods are used by worker threads
	//that we will introduce later.
	public static Bundle getStringAsABundle(String message){
		Bundle b = new Bundle();
		b.putString("message", message);
		return b;
	}
	public static String getStringFromABundle(Bundle b){
		return b.getString("message");
	}
}

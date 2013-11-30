package com.snapandswap;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class StorageUtil {
	
	private static File mediaStorageDir;
	
	public static void createNewJpeg(Context context, byte[] data) {
		File pictureFile = StorageUtil.getOutputMediaFile(context);
		if (pictureFile == null){
			Log.d(CameraActivity.TAG, "Error creating media file, check storage permissions: ");
			return;
		}

		try {
			FileOutputStream fos = new FileOutputStream(pictureFile);
			fos.write(data);
			fos.close();
		} catch (FileNotFoundException e) {
			Log.d(CameraActivity.TAG, "File not found: " + e.getMessage());
		} catch (IOException e) {
			Log.d(CameraActivity.TAG, "Error accessing file: " + e.getMessage());
		}
	}
	
	public static File getOutputMediaFile(Context context) {

		if (! checkExternalStorage(context)) {
			return null;
		}
		mediaStorageDir= new File(context.getExternalFilesDir(null), "pics");
		// Create the storage directory if it does not exist
		if (! mediaStorageDir.exists()){
			if (! mediaStorageDir.mkdirs()){
				Log.i("pictures", "failed to create directory");
				return null;
			}
		}

		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
		File imgFile = new File(mediaStorageDir.getPath() + File.separator +
				"IMG_"+ timeStamp + ".jpg");

		return imgFile;
	}

	private static boolean checkExternalStorage(Context context) {
		String exStorageStatus = Environment.getExternalStorageState();
		if (exStorageStatus.equals(Environment.MEDIA_MOUNTED)) {
			return true;
		} else if (exStorageStatus.equals(Environment.MEDIA_MOUNTED_READ_ONLY)) {
			Toast.makeText(context, "External Storage is Read Only!", Toast.LENGTH_SHORT).show();
		} else {
			Toast.makeText(context, "External Storage is Not Mounted", Toast.LENGTH_SHORT).show();
		}
		return false;
	}

}

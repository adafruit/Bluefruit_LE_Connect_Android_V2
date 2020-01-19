package magick.util;

import fakeawt.Dimension;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import magick.ImageInfo;
import magick.MagickException;
import magick.MagickImage;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.os.Handler;
import android.util.Log;
public class MagickBitmap {

	//////////////////////////////////////////////////////////
	//
	//	2016/04/21 D.Slamnig modified
	//
	//////////////////////////////////////////////////////////
	public static final String LOGTAG = "MagickBitmap.java";
	
	public static Bitmap ToBitmap(MagickImage img) throws MagickException {
		Log.d(LOGTAG, "ToBitmap()");
		int width = img.getWidth();
		int height = img.getHeight();
		int count = width * height * 4;
		byte pixels[] = new byte[count];
		
		/* Because Java use big edian, when calling native bytesToInts, 
		 * we must reverse the order of ARGB. */
		boolean res = img.dispatchImage(0, 0, width, height, "BGRA", pixels);
		int colors[] = bytesToInts(pixels);
		//int colors[] = toIntArray(pixels);
		
		if (res) {
			// Added catching out of memory error: 
			Bitmap bitmap = null;

			Log.d(LOGTAG, "Going to create bitmap:");
			try{
				bitmap = Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888);
			}catch(OutOfMemoryError e)
			{
				Log.w(LOGTAG, "OutOfMemoryError caught", e);
				bitmap = null;
				return null;
			}
			return bitmap;
			// return Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888);
		}
		else return null;
	}
	
	//////////////////////////////////////////////////////////
	//
	//	ToReducedBitmap
	//
	//	2016/04/21 D.Slamnig added 
	//
	// 	Limit bitmap size to max dimension
	//
	//////////////////////////////////////////////////////////
	public static Bitmap ToReducedBitmap(MagickImage img, int maxDimension) throws MagickException
	{
		MagickImage reducedImg = null;
		Bitmap bitmap = null;
		int w, h;
		int reduce = 0;
		
		Log.d(LOGTAG, "ToReducedBitmap()");
		
		// get original image size:
		w = img.getWidth();
		h = img.getHeight();
		
		// reduce image to maxDimension:
		if(w > maxDimension){
			h = (h * maxDimension) / w;
			w = maxDimension;
			reduce = 1;
		}
		
		if(h > maxDimension){
			w = (w * maxDimension) / h;
			h = maxDimension;
			reduce = 1;
		}
		
		if(reduce == 1){ // generate reduced size image
			if((reducedImg = img.sampleImage(w, h)) != null)
				bitmap = ToBitmap(reducedImg);
		}
		else // use original image
			bitmap = ToBitmap(img);
		
		return bitmap;
	}
	////////////////////////////////////////////////////////////////////////////
	
	public static MagickImage fromBitmap(Bitmap bmp) throws MagickException {
		/*
		int width = bmp.getWidth();
		int height = bmp.getHeight();
		int count = width * height;
		int pixels[] = new int[count];
		bmp.getPixels(pixels, 0, width, 0, 0, width, height);*/
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		bmp.compress(CompressFormat.JPEG, 80, bos);
		ImageInfo info = new ImageInfo();
		info.setMagick("jpeg");
		MagickImage image = new MagickImage(info, bos.toByteArray());
		return image;
	}
	
	public static int[] toIntArray(byte[] barr) { 
        //Pad the size to multiple of 4 
        int size = (barr.length / 4) + ((barr.length % 4 == 0) ? 0 : 1);      

        ByteBuffer bb = ByteBuffer.allocate(size *4); 
        bb.put(barr); 

        //Java uses Big Endian. Network program uses Little Endian. 
        //bb.order(ByteOrder.LITTLE_ENDIAN); 
       

        int[] result = new int[size]; 
        bb.rewind(); 
        while (bb.remaining() > 0) { 
            result[bb.position()/4] =bb.getInt(); 
        } 

        return result; 
	}
	
	public native static int[] bytesToInts(byte[] bytes);
	
	static {
        System.loadLibrary("imagemagick");
	}
}

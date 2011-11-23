/*
 * PhoneGap is available under *either* the terms of the modified BSD license *or* the
 * MIT License (2008). See http://opensource.org/licenses/alphabetical for full text.
 * 
 * Copyright (c) 2005-2010, Nitobi Software Inc.
 * Copyright (c) 2010, IBM Corporation
 */
package com.phonegap;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.codec.binary.Base64;
import org.json.JSONArray;
import org.json.JSONException;

import com.phonegap.api.Plugin;
import com.phonegap.api.PluginResult;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.net.Uri;
import android.util.Log;

/**
 * This class launches the camera view, allows the user to take a picture, closes the camera view,
 * and returns the captured image.  When the camera view is closed, the screen displayed before 
 * the camera view was shown is redisplayed.
 */
public class CameraLauncher extends Plugin {

	private static final int DATA_URL = 0;				// Return base64 encoded string
	private static final int FILE_URI = 1;				// Return file uri (content://media/external/images/media/2 for Android)
	
	private static final int PHOTOLIBRARY = 0;			// Choose image from picture library (same as SAVEDPHOTOALBUM for Android)
	private static final int CAMERA = 1;				// Take picture from camera
	private static final int SAVEDPHOTOALBUM = 2;		// Choose image from picture library (same as PHOTOLIBRARY for Android)
	
	private static final int JPEG = 0;                  // Take a picture of type JPEG
	private static final int PNG = 1;                   // Take a picture of type PNG
	
	private int mQuality;					// Compression quality hint (0-100: 0=low quality & high compression, 100=compress of max quality)
	private int targetWidth;                // desired width of the image
	private int targetHeight;               // desired height of the image
	private Uri imageUri;					// Uri of captured image 
	public String callbackId;
	
    /**
     * Constructor.
     */
	public CameraLauncher() {
	}

	/**
	 * Executes the request and returns PluginResult.
	 * 
	 * @param action 		The action to execute.
	 * @param args 			JSONArry of arguments for the plugin.
	 * @param callbackId	The callback id used when calling back into JavaScript.
	 * @return 				A PluginResult object with a status and message.
	 */
	public PluginResult execute(String action, JSONArray args, String callbackId) {
		PluginResult.Status status = PluginResult.Status.OK;
		String result = "";		
		this.callbackId = callbackId;
		
		try {
			if (action.equals("takePicture")) {
				int destType = DATA_URL;
				if (args.length() > 1) {
					destType = args.getInt(1);
				}
				int srcType = CAMERA;
				if (args.length() > 2) {
					srcType = args.getInt(2);
				}
                if (args.length() > 3) {
                    this.targetWidth = args.getInt(3); 
                }
                if (args.length() > 4) {
                    this.targetHeight = args.getInt(4); 
                }
                int encodingType = JPEG;
                if (args.length() > 5) {
                    encodingType = args.getInt(5); 
                }
				if (srcType == CAMERA) {
					this.takePicture(args.getInt(0), destType, encodingType);
				}
				else if ((srcType == PHOTOLIBRARY) || (srcType == SAVEDPHOTOALBUM)) {
					this.getImage(args.getInt(0), srcType, destType);
				}
				PluginResult r = new PluginResult(PluginResult.Status.NO_RESULT);
				r.setKeepCallback(true);
				return r;
			}
			return new PluginResult(status, result);
		} catch (JSONException e) {
			e.printStackTrace();
			return new PluginResult(PluginResult.Status.JSON_EXCEPTION);
		}
	}
	
    //--------------------------------------------------------------------------
    // LOCAL METHODS
    //--------------------------------------------------------------------------
    
	/**
	 * Take a picture with the camera.
	 * When an image is captured or the camera view is cancelled, the result is returned
	 * in PhonegapActivity.onActivityResult, which forwards the result to this.onActivityResult.
	 * 
	 * The image can either be returned as a base64 string or a URI that points to the file.
	 * To display base64 string in an img tag, set the source to:
	 * 		img.src="data:image/jpeg;base64,"+result;
	 * or to display URI in an img tag
	 * 		img.src=result;
	 * 
	 * @param quality			Compression quality hint (0-100: 0=low quality & high compression, 100=compress of max quality)
	 * @param returnType		Set the type of image to return. 
	 */
	public void takePicture(int quality, int returnType, int encodingType) {
		this.mQuality = quality;
		
		// Display camera
        Intent intent = new Intent("android.media.action.IMAGE_CAPTURE");
        
        // Specify file so that large image is captured and returned
        // TODO: What if there isn't any external storage?
        File photo = createCaptureFile(encodingType);
        intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, Uri.fromFile(photo));
        this.imageUri = Uri.fromFile(photo);

        this.ctx.startActivityForResult((Plugin) this, intent, (CAMERA+1)*16 + returnType+1);
	}

	/**
	 * Create a file in the applications temporary directory based upon the supplied encoding.
	 * 
	 * @param encodingType of the image to be taken
	 * @return a File object pointing to the temporary picture
	 */
    private File createCaptureFile(int encodingType) {
        File photo = null;
        if (encodingType == JPEG) {
            photo = new File(DirectoryManager.getTempDirectoryPath(ctx),  "Pic.jpg");
        } else {
            photo = new File(DirectoryManager.getTempDirectoryPath(ctx),  "Pic.png");            
        }
        return photo;
    }

    /**
	 * Get image from photo library.
	 * 
	 * @param quality			Compression quality hint (0-100: 0=low quality & high compression, 100=compress of max quality)
	 * @param srcType			The album to get image from.
	 * @param returnType		Set the type of image to return. 
	 */
	// TODO: Images selected from SDCARD don't display correctly, but from CAMERA ALBUM do!
	public void getImage(int quality, int srcType, int returnType) {
		this.mQuality = quality;

		Intent intent = new Intent();
		intent.setType("image/*");
		intent.setAction(Intent.ACTION_GET_CONTENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		this.ctx.startActivityForResult((Plugin) this, Intent.createChooser(intent,
				new String("Get Picture")), (srcType+1)*16 + returnType + 1);
	}

	/**
	 * Scales the bitmap according to the requested size.
	 * 
	 * @param bitmap		The bitmap to scale.
	 * @return Bitmap		A new Bitmap object of the same bitmap after scaling. 
	 */
	public Bitmap scaleBitmap(Bitmap bitmap) {
        int newWidth = this.targetWidth;
        int newHeight = this.targetHeight;
        int origWidth = bitmap.getWidth();
        int origHeight = bitmap.getHeight();

        // If no new width or height were specified return the original bitmap
    	if (newWidth <= 0 && newHeight <= 0) {
    	    return bitmap;
    	}
    	// Only the width was specified
    	else if (newWidth > 0 && newHeight <= 0) {
            newHeight = (newWidth * origHeight) / origWidth;
        }
    	// only the height was specified
    	else if (newWidth <= 0 && newHeight > 0) {
            newWidth = (newHeight * origWidth) / origHeight;
        }
        // If the user specified both a positive width and height
        // (potentially different aspect ratio) then the width or height is
        // scaled so that the image fits while maintaining aspect ratio.
        // Alternatively, the specified width and height could have been
        // kept and Bitmap.SCALE_TO_FIT specified when scaling, but this
        // would result in whitespace in the new image.
    	else {
            double newRatio = newWidth / (double)newHeight;
            double origRatio = origWidth / (double)origHeight;

            if (origRatio > newRatio) {
                newHeight = (newWidth * origHeight) / origWidth;
            } else if (origRatio < newRatio) {
                newWidth = (newHeight * origWidth) / origHeight;
            }
        }
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
	}
	
    /**
     * Called when the camera view exits. 
     * 
     * @param requestCode		The request code originally supplied to startActivityForResult(), 
     * 							allowing you to identify who this result came from.
     * @param resultCode		The integer result code returned by the child activity through its setResult().
     * @param intent			An Intent, which can return result data to the caller (various data can be attached to Intent "extras").
     */
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		
		// Get src and dest types from request code
		int srcType = (requestCode/16) - 1;
		int destType = (requestCode % 16) - 1;

		// If CAMERA
		if (srcType == CAMERA) {
			// If image available
			if (resultCode == Activity.RESULT_OK) {
				try {
					// Read in bitmap of captured image
          Bitmap bitmap;
          try {
            bitmap = android.provider.MediaStore.Images.Media.getBitmap(this.ctx.getContentResolver(), imageUri);
          } catch (FileNotFoundException e) {
            Uri uri = intent.getData();
            android.content.ContentResolver resolver = this.ctx.getContentResolver();
            bitmap = android.graphics.BitmapFactory.decodeStream(resolver.openInputStream(uri));
          }

					bitmap = scaleBitmap(bitmap);
					
					// If sending base64 image back
					if (destType == DATA_URL) {
						this.processPicture(bitmap);
					}

					// If sending filename back
					else if (destType == FILE_URI){
						// Create entry in media store for image
						// (Don't use insertImage() because it uses default compression setting of 50 - no way to change it)
						ContentValues values = new ContentValues();
						values.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
						Uri uri = null;
						try {
							uri = this.ctx.getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
						} catch (UnsupportedOperationException e) {
							System.out.println("Can't write to external media storage.");
							try {
								uri = this.ctx.getContentResolver().insert(android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI, values);
							} catch (UnsupportedOperationException ex) {
								System.out.println("Can't write to internal media storage.");							
								this.failPicture("Error capturing image - no media storage found.");
								return;
							}
						}

						// Add compressed version of captured image to returned media store Uri
						OutputStream os = this.ctx.getContentResolver().openOutputStream(uri);
						bitmap.compress(Bitmap.CompressFormat.JPEG, this.mQuality, os);
						os.close();

						// Send Uri back to JavaScript for viewing image
						this.success(new PluginResult(PluginResult.Status.OK, uri.toString()), this.callbackId);
					}
					bitmap.recycle();
					bitmap = null;
					System.gc();
				} catch (IOException e) {
					e.printStackTrace();
					this.failPicture("Error capturing image.");
				}		
			}

			// If cancelled
			else if (resultCode == Activity.RESULT_CANCELED) {
				this.failPicture("Camera cancelled.");
			}

			// If something else
			else {
				this.failPicture("Did not complete!");
			}
		}
		
		// If retrieving photo from library
		else if ((srcType == PHOTOLIBRARY) || (srcType == SAVEDPHOTOALBUM)) {
			if (resultCode == Activity.RESULT_OK) {
				Uri uri = intent.getData();
				android.content.ContentResolver resolver = this.ctx.getContentResolver();
				// If sending base64 image back
				if (destType == DATA_URL) {
					try {
						Bitmap bitmap =	android.graphics.BitmapFactory.decodeStream(resolver.openInputStream(uri));
						bitmap = scaleBitmap(bitmap);
						this.processPicture(bitmap);
						bitmap.recycle();
						bitmap = null;
						System.gc();
					} catch (FileNotFoundException e) {
						e.printStackTrace();
						this.failPicture("Error retrieving image.");
					}
				}
				
				// If sending filename back
				else if (destType == FILE_URI) {
					this.success(new PluginResult(PluginResult.Status.OK, uri.toString()), this.callbackId);
				}
			}
			else if (resultCode == Activity.RESULT_CANCELED) {
				this.failPicture("Selection cancelled.");				
			}
			else {
				this.failPicture("Selection did not complete!");				
			}
		}
	}
	
	/**
	 * Compress bitmap using jpeg, convert to Base64 encoded string, and return to JavaScript.
	 *
	 * @param bitmap
	 */
	public void processPicture(Bitmap bitmap) {		
		ByteArrayOutputStream jpeg_data = new ByteArrayOutputStream();
		try {
			if (bitmap.compress(CompressFormat.JPEG, mQuality, jpeg_data)) {
				byte[] code  = jpeg_data.toByteArray();
				byte[] output = Base64.encodeBase64(code);
				String js_out = new String(output);
				this.success(new PluginResult(PluginResult.Status.OK, js_out), this.callbackId);
				js_out = null;
				output = null;
				code = null;
			}	
		}
		catch(Exception e) {
			this.failPicture("Error compressing image.");
		}		
		jpeg_data = null;
	}
	
	/**
	 * Send error message to JavaScript.
	 * 
	 * @param err
	 */
	public void failPicture(String err) {
		this.error(new PluginResult(PluginResult.Status.ERROR, err), this.callbackId);
	}
}

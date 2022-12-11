package edu.upi.cs.yudiwbs.cobaqr;
/*
QRcode dengan ML-Kit  (Camera2, Java)

Sumber:
https://medium.com/@gauravpandey_34933/how-to-camera2-api-in-android-576fd23650ea

Barcode:
https://developers.google.com/ml-kit/vision/barcode-scanning/android

ide untuk real time processing
https://github.com/EzequielAdrianM/Camera2Vision/blob/master/Camera2/app/src/main/java/com/randolabs/ezequiel/camera2/camera/Camera2Source.java

todo:
overlay dengan preview (misal teks):
https://github.com/googlesamples/mlkit/blob/master/android/vision-quickstart/app/src/main/java/com/google/mlkit/vision/demo/GraphicOverlay.java?utm_source=developers.google.com&utm_medium=referral
dan https://github.com/googlesamples/mlkit/blob/master/android/vision-quickstart/app/src/main/java/com/google/mlkit/vision/demo/CameraSourcePreview.java?utm_source=developers.google.com&utm_medium=referral

*/

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.os.Bundle;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private String TAG = "debug_yudi";
    private Button takePictureButton;
    private TextureView textureView;
    private TextView tvHasil;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private String cameraId;
    protected CameraDevice cameraDevice;
    //protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest captureRequest;
    protected CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private ImageReader imageReader;
    private File file;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private boolean mFlashSupported;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;



    //dikeluarkan dari scanbarcode biar cepat biar cepat?
    private BarcodeScannerOptions options =
            new BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(
                            Barcode.FORMAT_QR_CODE
                            ) //Barcode.FORMAT_AZTEC
                    .build();
    private BarcodeScanner scanner = BarcodeScanning.getClient();

    //proses barcode
    private void scanBarcodes(InputImage image) {
        Task<List<Barcode>> result = scanner.process(image)
                .addOnSuccessListener(new OnSuccessListener<List<Barcode>>() {
                    @Override
                    public void onSuccess(List<Barcode> barcodes) {
                        // Task completed successfully
                        // dapat barcode
                        for (Barcode barcode: barcodes) {
                            Rect bounds = barcode.getBoundingBox();
                            Point[] corners = barcode.getCornerPoints();
                            String rawValue = barcode.getRawValue();
                            int valueType = barcode.getValueType();
                            // See API reference for complete list of supported types
                            switch (valueType) {
                                case Barcode.TYPE_WIFI:
                                    String ssid = barcode.getWifi().getSsid();
                                    String password = barcode.getWifi().getPassword();
                                    int type = barcode.getWifi().getEncryptionType();
                                    break;
                                case Barcode.TYPE_URL:
                                    String title = barcode.getUrl().getTitle();
                                    String url = barcode.getUrl().getUrl();
                                    Log.d(TAG,title+url);
                                    tvHasil.setText(title+url);
                                    break;
                            }
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        // Task failed with an exception
                        Log.e("debug_yudi","GAGAL proses barcode: "+e.getMessage());
                    }
                }).addOnCompleteListener(new OnCompleteListener<List<Barcode>>() {
                    @Override
                    public void onComplete(@NonNull Task<List<Barcode>> task) {
                        //ini penting, karena async, harus diclose disini
                        mImage.close();
                    }
                });;
        // [END run_detector]
    }


    private long currTimestamp = 0;
    private long oldTimeStamp = 0;
    private long mulaiTimeStamp = 0;

    private Image mImage;

    private class MyImageAvailableListener implements ImageReader.OnImageAvailableListener {
        @Override
        public void onImageAvailable(ImageReader reader) {

            //memperlambat frame yang diproses
            //dari sekitar 20 frame per detik (50 ms antar frame) jadi 5 frame per detik (200)
            //berdasarkan eksperimen jika frame/sec terlalu banyak mulai error di barcode scan (belum selesai sudah masuk frame baru)
            //todo: mungkin perlu dibuat agar dinamik nilainya?

            final long min_delay_ms = 200; //minimum delay antara frame yang diterima dalam ms, jika 200 artinya 5 frame/det

            currTimestamp = System.currentTimeMillis();
            long selisih = currTimestamp - mulaiTimeStamp;  //selisih saat ini dgn waktu eksekusi terakhir
            if (selisih < min_delay_ms) {
                return; //terlalu cepat, skip
            } else {
                Log.d(TAG, "mulai, delay dengan proses terakhir (ms): "+String.valueOf(selisih));  //kira 46 sd 54 milidetik antar frame
            }

            int rotation = getWindowManager().getDefaultDisplay().getRotation();

            // lihat mImageReaderPreview = ImageReader.newInstance(...  bagian nilai maximage
            // jika nilainya dinaikkan kinerja barscan akan semakin bagus
            // karena mImage bisa diambil tanpa perlu diclose?

            mImage = reader.acquireLatestImage();
            if(mImage == null) {
                    return;
            }

            // bisa error maximage terlewati
            // tapi kalau ditangani dgn try-catch malah jadi lambat.. entah kenapa
            // paling ideal dengan pembatasan frame

            try {
                //kadang terjadi image dikatakan sudah diclose (mungkin diclose oleh barcodescan)
                //ditangkap agar app tidak crash
                InputImage inImage = InputImage.fromMediaImage(mImage, rotation);
                mulaiTimeStamp = System.currentTimeMillis();   //timestamp eksekusi terakhir
                scanBarcodes(inImage);
            } catch (Exception e) {
                Log.e(TAG,"Image is already close, skip");
            }

            //mImage.close(); <-- jangan close disini, karena async bisa saja sedang diproses barscan, akan buat error

            //dari dokumentasi: Please wait for the detector to finish processing this InputImage before you close the underlying Image.
            // jadi diclose harus dari barcodscan nya

        }
    }

    //ini dipanggil berulang2 dan digunakan untuk men-scan realtime
    //diset di: createCameraPreview()
    private final ImageReader.OnImageAvailableListener mOnPreviewAvailableListener = new MyImageAvailableListener();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //preview camera
        textureView = (TextureView) findViewById(R.id.textureCamera);
        textureView.setSurfaceTextureListener(textureListener);
        tvHasil = findViewById(R.id.tvHasil);
        mulaiTimeStamp = System.currentTimeMillis();
    }

    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }


    protected void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private ImageReader mImageReaderPreview;

    /**
     * This is a callback object for the ImageReader. "onImageAvailable" will be called when a
     * preview frame is ready to be processed.
     */

    protected void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());

            //pemrosesan live
            //maxImage adalah property yang PENTING! disini diset 30
            //semakin sedikit kinerja barcodescan semakin buruk, bisa patah2
            //semakin besar, inisialiasi kamera jadi lebih lama
            //YUV_420_888 katanya lebih cepat

            mImageReaderPreview = ImageReader.newInstance(imageDimension.getWidth(), imageDimension.getHeight(), ImageFormat.YUV_420_888, 30);
            mImageReaderPreview.setOnImageAvailableListener(mOnPreviewAvailableListener, mBackgroundHandler);

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);   //untuk preview
            captureRequestBuilder.addTarget(mImageReaderPreview.getSurface());   //untuk live process

            //capturesession
            //perhatikan selain surface, mImageReaderPreview juga masuk ke list
            cameraDevice.createCaptureSession(Arrays.asList(surface,mImageReaderPreview.getSurface()),
               new CameraCaptureSession.StateCallback(){
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    //The camera is already closed
                    if (null == cameraDevice) {
                        Log.e(TAG, "camdevice null, return");
                        return;
                    }
                    // When the session is ready, we start displaying the preview.

                    captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                    try {
                        //keapa catpurecallback null?
                        cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        Log.e(TAG, "Error setrepetingreq:"+e.getMessage());
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(getApplicationContext(), "Configuration change failed", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //camera dibuka
    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.e(TAG, "is camera open");
        try {
            cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;

            //ukuran surfacetexture
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];

            // Add permission for camera and let user grant the permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
                return;
            }
            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        Log.e(TAG, "openCamera X");
    }


    private void closeCamera() {
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (null != imageReader) {
            imageReader.close();
            imageReader = null;
        }
        if (null != mImageReaderPreview) {
            mImageReaderPreview.close();
            mImageReaderPreview = null;
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                Toast.makeText(this, "Sorry!!!, you can't use this app without granting permission", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.e(TAG, "onResume");
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
    protected void onPause() {
        Log.e(TAG, "onPause");
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    //listener untuk view berisi preview camera
    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            //open your camera here
            openCamera();
        }
        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            // Transform you image captured size according to the surface width and height
        }
        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    //callback untuk berbagai kondisi camera saat di OPEN
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            //This is called when the camera is open
            Log.e(TAG, "onOpened");
            cameraDevice = camera;
            createCameraPreview();
        }
        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraDevice.close();
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

}

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.File;


public class BlockyActivity extends Activity implements View.OnTouchListener {

    static final int REQUEST_TAKE_PHOTO = 1234;
    final static int ROTATION = 1;
    Toast t; // inf. bublina
    File photoFile;
    Button takePhoto; // talacidlo odfot
    Button spracuj; // dacasne tlacidlo pre test filtrov
    ImageButton left, right; // tlacidla rotacie obrazu
    ImageView photoPrev, bigPrev;
    Bitmap photo; // odfoteny obraz
    Bitmap bmp; //pomocna bitmapa
    Matrix matrix; // matica pomocou kt sa transformuje obraz
    Mat rgbMat, grayMat, tmpMat;
    Point C;
    int d; // uhol otocenia

    static {
        if (!OpenCVLoader.initDebug()) {
            // Handle initialization error
        } else {
            System.loadLibrary("opencv_info");
            System.loadLibrary("opencv_java");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        setContentView(R.layout.activity_main);
        init();

    }

    // inicializacia premennych
    private void init() {

        d = 0;
        t = Toast.makeText(this, "Prosím, pootočte obraz tak, aby bol text zarovnaný vodorovne.", 15000);
        t.setGravity(Gravity.TOP, 0, 0);
        rgbMat = new Mat();
        grayMat = new Mat();
        tmpMat = new Mat();
        matrix = new Matrix();
        C = new Point(0, 0);
        takePhoto = (Button) findViewById(R.id.photoButton);
        spracuj = (Button) findViewById(R.id.spracuj);
        left = (ImageButton) findViewById(R.id.left);
        right = (ImageButton) findViewById(R.id.right);
        photoPrev = (ImageView) findViewById(R.id.photoPreview);
        bigPrev = (ImageView) findViewById(R.id.bigPreview);
        bigPrev.setOnTouchListener(this);
        photoPrev.setOnTouchListener(this);
        takePhoto.setOnTouchListener(this);
        spracuj.setOnTouchListener(this);
        left.setOnTouchListener(this);
        right.setOnTouchListener(this);
    }

    private void dispatchTakePictureIntent() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            photoFile = null;
            try {
                photoFile = new File(Environment.getExternalStorageDirectory() + File.separator + "image.jpg");
            } catch (Exception ex) {
                // Chyba pri vytvarani File
            }
            if (photoFile != null) {
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile));
                startActivityForResult(cameraIntent, REQUEST_TAKE_PHOTO);
            }
        }
    }

    /**
     * Metoda, ktora vytvori zmensenu kopiu odfoteneho obrazu.
     *
     * @param photoPath: Cesta k odfotenemu obrazu
     * @return Zmensenu bitmapu
     */
    private Bitmap getScaledBitmap(String photoPath) {

        int targetW = photoPrev.getWidth() - (photoPrev.getWidth() / 4 + 64);
        int targetH = photoPrev.getHeight() - (photoPrev.getHeight() / 4 + 64);

        final BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(photoPath, bmOptions);
        int photoW = bmOptions.outWidth;
        int photoH = bmOptions.outHeight;

        int scaleFactor = Math.min(photoW / targetW, photoH / targetH);

        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;
        bmOptions.inPurgeable = true;

        Bitmap bmp = BitmapFactory.decodeFile(photoPath, bmOptions);
        return bmp;
    }

    // spracovanie dat pre foto
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == REQUEST_TAKE_PHOTO) {
            photo = getScaledBitmap(photoFile.getAbsolutePath());
            photoPrev.setImageBitmap(photo);
            t.show();
        }
    }

    /**
     * Metoda, ktora sluzi na rotaciu obrazu.
     *
     * @param rotation: uhol o aky sa ma obraz pootocit
     */
    private void rotateBmp(int rotation) {
        int width = photo.getWidth();
        int height = photo.getHeight();
        matrix.reset();
        matrix.postRotate(rotation);
        bmp = Bitmap.createBitmap(photo, 0, 0, width, height, matrix, true);
        photoPrev.setImageBitmap(bmp);
    }

    /**
     * Metoda, ktora pripravi obraz pre rozpoznavanie znakov.
     */
    private void preprocess() {

        int maxValue = 255;
        int blockSize = 43;
        int c = 17;
        Bitmap rgb = photo.copy(Bitmap.Config.ARGB_8888, true);
        Utils.bitmapToMat(rgb, rgbMat); //rgb
        Imgproc.cvtColor(rgbMat, tmpMat, Imgproc.COLOR_RGB2GRAY); //gray
        Imgproc.GaussianBlur(tmpMat, grayMat, new Size(3, 3), 0);

        Imgproc.adaptiveThreshold(grayMat, tmpMat, maxValue, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, blockSize, c);
        Imgproc.cvtColor(tmpMat, rgbMat, Imgproc.COLOR_GRAY2RGBA, 4);
        Imgproc.dilate(rgbMat, grayMat, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2.9, 2.9)));
        Imgproc.erode(grayMat, rgbMat, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2.5, 2.5)));
        Utils.matToBitmap(rgbMat, photo);
        bmp = Bitmap.createBitmap(photo, 0, 0, photo.getWidth(), photo.getHeight(), matrix, true);
        bigPrev.setImageBitmap(bmp);
        bigPrev.setVisibility(View.VISIBLE);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {

        Point A = new Point((int) photoPrev.getX() + (photoPrev.getWidth() / 2), (int) photoPrev.getY() + (photoPrev.getHeight() / 3));

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                switch (v.getId()) {
                    case R.id.photoButton:
                        dispatchTakePictureIntent();
                        break;
                    case R.id.spracuj:
                        preprocess();
                        break;
                    case R.id.right:
                        if (photo != null) {
                            d += ROTATION;
                            rotateBmp(d);
                        }
                        break;
                    case R.id.left:
                        if (photo != null) {
                            d -= ROTATION;
                            rotateBmp(d);
                        }
                        break;
                    case R.id.photoPreview:
                        if (photo != null) {
                            C.set((int) event.getX(0), (int) event.getY(0));
                        }
                        break;
                    case R.id.bigPreview:
                        if (bigPrev.getVisibility() == View.VISIBLE) {
                            bigPrev.setVisibility(View.INVISIBLE);
                        }
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (v.getId() == R.id.photoPreview) {
                    if (photo != null) {
                        d = (int) Math.toDegrees(Math.atan2(event.getY(0) - A.y, event.getX(0) - A.x) - Math.atan2(C.y - A.y, C.x - A.x));
                        rotateBmp(d);
                    }
                    break;
                }
        }
        return true;
    }

}

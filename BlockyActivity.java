import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.AssetManager;
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
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class BlockyActivity extends Activity implements View.OnTouchListener {

    final static int REQUEST_TAKE_PHOTO = 1234;
    final static int ROTATION = 1;
    Toast t; // inf. bublina
    File photoFile;
    Button takePhoto; // talacidlo odfot
    Button spracuj; // dacasne tlacidlo pre test filtrov
    ImageButton left, right; // tlacidla rotacie obrazu
    ImageView photoPrev;
    ArrayList<Integer> output;
    Bitmap photo, fp; // odfoteny obraz
    Bitmap bmp; //pomocna bitmapa
    Matrix matrix; // matica pomocou kt sa transformuje obraz
    HashMap<Integer, Mat> mImgs;
    Mat rgbMat, grayMat, tmpMat;
    Point C;
    StringBuilder info;
    int d; // uhol otocenia
    boolean dkpFlag, dateFlag, priceFlag;

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
        t = Toast.makeText(this, "Prosím, pootočte obraz tak, aby bol text zarovnaný vodorovne.", 20000);
        t.setGravity(Gravity.TOP, 0, 0);
        output = new ArrayList<Integer>();
        rgbMat = new Mat();
        grayMat = new Mat();
        tmpMat = new Mat();
        info = new StringBuilder();
        AssetManager manager = getAssets();
        mImgs = new HashMap<Integer, Mat>();
        for (int i = 0; i < 19; i++) {
            try {
                Mat m = new Mat();
                Utils.bitmapToMat(BitmapFactory.decodeStream(manager.open("characters/" + i + ".png")), m);
                Imgproc.cvtColor(m, m, Imgproc.COLOR_RGB2GRAY);
                if (i < 16 && m != null) {
                    mImgs.put(i, m);
                } else {
                    switch (i) {
                        case 16:
                            mImgs.put(68, m);//D
                            break;
                        case 17:
                            mImgs.put(75, m);//K
                            break;
                        case 18:
                            mImgs.put(80, m);//P
                            break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        dkpFlag = dateFlag = priceFlag = false;
        C = new Point(0, 0);
        matrix = new Matrix();
        takePhoto = (Button) findViewById(R.id.photoButton);
        spracuj = (Button) findViewById(R.id.process);
        left = (ImageButton) findViewById(R.id.left);
        right = (ImageButton) findViewById(R.id.right);
        photoPrev = (ImageView) findViewById(R.id.photoPreview);
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
                // Chyba pri vytvarani File-u
            }
            if (photoFile != null) {
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile));
                startActivityForResult(cameraIntent, REQUEST_TAKE_PHOTO);
            }
        }
    }

    // spracovanie dat pre foto
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == REQUEST_TAKE_PHOTO) {
            fp = BitmapFactory.decodeFile(photoFile.getAbsolutePath());// full size***
            photo = getScaledBitmap(photoFile.getAbsolutePath());
            photoFile.delete();
            photoPrev.setImageBitmap(photo);
            t.show();
        }
    }

    /**
     * Metoda, ktora vytvori zmensenu kopiu odfoteneho obrazu.
     *
     * @param photoPath: Cesta k odfotenemu obrazu
     * @return Zmensenu bitmapu
     */
    private Bitmap getScaledBitmap(String photoPath) {

        int targetW = photoPrev.getWidth() - (photoPrev.getWidth() / 4);
        int targetH = photoPrev.getHeight() - (photoPrev.getHeight() / 4);

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
        bmp = Bitmap.createBitmap(photo, 0, 0, width, height, matrix, false);
        photoPrev.setImageBitmap(bmp);
    }

    /**
     * Metoda, ktora pripravi obraz pre rozpoznavanie znakov.
     */
    public String preprocess() {

        int maxValue = 255;
        int blockSize = 43;
        int c = 12;
        Utils.bitmapToMat(fp, rgbMat);

        if (d != 0) {
            Mat t = new Mat();
            Utils.bitmapToMat(fp, t);
            int len = Math.max(t.cols(), t.rows());
            org.opencv.core.Point pt = new org.opencv.core.Point(len / 2., len / 2.);
            Mat r = Imgproc.getRotationMatrix2D(pt, -d, 1.0);
            d = 0;
            Imgproc.warpAffine(t, rgbMat, r, new Size(len, len));
            t.release();
        }

        //gray
        Imgproc.cvtColor(rgbMat, tmpMat, Imgproc.COLOR_RGB2GRAY);
        //treshold
        Imgproc.adaptiveThreshold(tmpMat, grayMat, maxValue, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, blockSize, c);
        //dilate and erode
        Imgproc.cvtColor(grayMat, rgbMat, Imgproc.COLOR_GRAY2RGBA, 4);
        Imgproc.dilate(rgbMat, grayMat, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(4.7, 4.7)));
        Imgproc.erode(grayMat, rgbMat, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2.5, 2.5)));
        //finding contours
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>(); // obrysy blocku
        Imgproc.cvtColor(rgbMat, tmpMat, Imgproc.COLOR_RGB2GRAY, 1); // do metody find contours musi vstupovat iba single-channel mat
        Imgproc.findContours(tmpMat, contours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_NONE);
        org.opencv.core.Point p1, p2;
        for (MatOfPoint contour : contours) { // vykreslenie plnych obdlznikov
            org.opencv.core.Rect currentRect = Imgproc.boundingRect(contour);
            if (contour.width() > tmpMat.width() / 4 || contour.height() > tmpMat.height() / 4) {
                continue;
            } else {
                p1 = new org.opencv.core.Point(currentRect.tl().x + 2, currentRect.tl().y - 5);
                p2 = new org.opencv.core.Point(currentRect.br().x - 2, currentRect.br().y + 5);
                if (currentRect.height < 10) {
                    Core.rectangle(tmpMat, currentRect.tl(), currentRect.br(), new Scalar(0, 0, 0), -1);
                } else {
                    Core.rectangle(tmpMat, p1, p2, new Scalar(255, 255, 255), -1);
                }
            }
        }
        contours.clear();

        Imgproc.findContours(tmpMat, contours, new Mat(),
                Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_NONE); // najdenie kontur tento raz uz bez tych nechcenych

        ArrayList<List<Rect>> letters = sortText(contours);

        int match;
        int outputIndex = 0;
        for (int i = 0; i < letters.size(); i++) {
            for (int j = 0; j < letters.get(i).size(); j++) {

                // matching algo
                match = getMatch(rgbMat.submat(new Rect(new org.opencv.core.Point(letters.get(i).get(j).tl().x, letters.get(i).get(j).tl().y + 5),
                                                        new org.opencv.core.Point(letters.get(i).get(j).br().x, letters.get(i).get(j).br().y - 5))));

                if (output.size() <= 21) {
                    output.add(match);
                } else {
                    output.add(match);
                    outputIndex++;
                }

                if (output.size() > 21) {
                    //DNF a KNF nevyhovuju koli poziadavkam na znak kt. sa musi = 100(nie je rozpoznany)
                    if (((output.get(outputIndex) == 68 && output.get(outputIndex + 1) == 75 && output.get(outputIndex + 2) == 80)
                            || (output.get(outputIndex) == 100 && output.get(outputIndex + 1) == 75 && output.get(outputIndex + 2) == 80)
                            || (output.get(outputIndex) == 68 && output.get(outputIndex + 1) == 100 && output.get(outputIndex + 2) == 80)
                            || (output.get(outputIndex) == 68 && output.get(outputIndex + 1) == 75 && output.get(outputIndex + 2) == 100)) && !dkpFlag) {

                        String m = "";
                        for (int k = 0; k < 18; k++) {
                            if (output.get(outputIndex + k + 3) != 100) {
                                m += output.get(outputIndex + k + 3);
                            }
                        }
                        if (m.length() == 16 || m.length() == 17) {
                            m += "\n";
                            info.append("DKP: " + m);
                            dkpFlag = true;
                        }
                    }

                    if (output.get(outputIndex) < 4 && output.get(outputIndex + 1) < 10 && output.get(outputIndex + 2) == 100 && output.get(outputIndex + 3) < 2
                            && output.get(outputIndex + 4) < 10 && output.get(outputIndex + 5) == 100 && output.get(outputIndex + 6) == 2 && output.get(outputIndex + 7) == 0
                            && !dateFlag) {
                        dateFlag = true;

                        String m = "";
                        for (int k = 0; k < 16; k++) {
                            if (output.get(outputIndex + k) != 100 && m.length() < 17) {
                                if(output.get(outputIndex + k) == 67 || output.get(outputIndex + k) == 68){
                                    m += 0;
                                }else{
                                    m += output.get(outputIndex + k);
                                }
                            }
                            if (k == 1 || k == 4 || k == 9 || k == 11) {
                                m += " ";
                            }
                        }
                        if(m.length() == 16){
                            m += "\n";
                            info.append("Date: " + m);
                        }
                    }
                }
            }
            if (dkpFlag && dateFlag) {
                break;
            }
        }

        output.clear();
        rgbMat.release();
        tmpMat.release();
        grayMat.release();

        return info.toString();
    }

    public int getMatch(Mat character) {

        if (character.width() < 9) {
            return 100; //nerozpoznatelny znak
        }
        int maxFail = 38;
        int fail = 1000;
        int index = 100;
        Imgproc.resize(character, tmpMat, new Size(mImgs.get(0).width(), mImgs.get(0).height()));
        int rows = tmpMat.rows();
        int cols = tmpMat.cols();
        for (Map.Entry<Integer, Mat> entry : mImgs.entrySet()) {
            int currentFail = 0;
            for (int j = 0; j < rows; j++) {
                if(currentFail > maxFail){
                    break;
                }
                for (int k = 0; k < cols; k++) {
                    if (Math.abs(entry.getValue().get(j, k)[0] - tmpMat.get(j, k)[0]) > 80) {
                        currentFail++;
                    }
                }
            }
            if (fail > currentFail) {
                index = entry.getKey();
                fail = currentFail;
                if (entry.getKey().equals(6)) {
                    if (tmpMat.get(rows - 7, 0)[0] < 80) { // 5-6 test
                        index = 5;
                    }
                }
                if (entry.getKey().equals(11)) {
                    if (tmpMat.get(1, 1)[0] > 180) { // 1-I test
                        index = 100;
                    }
                }
                if (entry.getKey().equals(8) || entry.getKey().equals(3)) {
                    if (tmpMat.get(rows - 7, 1)[0] < 80) { // 3-8 test
                        if (tmpMat.get(6, 1)[0] < 80) {
                            index = 3;
                        } else {
                            index = 83; //je to S
                        }
                    } else {
                        index = 8;
                    }
                }
                if (entry.getKey().equals(68) || entry.getKey().equals(0)) {
                    if (tmpMat.get(1, 0)[0] > 180 || tmpMat.get(rows - 2, 0)[0] > 180) { // D-O-C test
                        index = 68;
                    } else {
                        if (tmpMat.get(rows / 2, cols - 1)[0] < 80) {
                            index = 67; // C
                        } else {
                            index = 0;
                        }
                    }
                }
            }
        }
        if (fail < maxFail) {
            if (index < 20) {
                return index % 10; // cisla
            } else {
                return index; // znaky
            }
        } else {
            return 100;
        }
    }

    public ArrayList<List<Rect>> sortText(List<MatOfPoint> contours) {

        HashMap<Range<Integer, Integer>, List<Rect>> sortedLetters = new HashMap<Range<Integer, Integer>, List<Rect>>();
        boolean added;
        for (int i = 0; i < contours.size(); i++) {
            Rect rect = Imgproc.boundingRect(contours.get(i));
            if (rect.height < 15 && rect.width < 8 || rect.width > tmpMat.width() / 4 || rect.height > tmpMat.height() / 4) {
                continue;
            }
            //y-ovy priemet
            added = false;
            if (i > 0) {
                for (Map.Entry<Range<Integer, Integer>, List<Rect>> entry : sortedLetters.entrySet()) {
                    if ((rect.y > entry.getKey().x1 && rect.y < entry.getKey().x2)
                            || ((rect.y + rect.height) > entry.getKey().x1 && (rect.y + rect.height) < entry.getKey().x2)
                            || (rect.y <= entry.getKey().x1 && (rect.y + rect.height) >= entry.getKey().x2)) {
                        if ((rect.y <= entry.getKey().x1 && (rect.y + rect.height) >= entry.getKey().x2)) {

                        }
                        sortedLetters.get(entry.getKey()).add(rect);
                        added = true;
                    }
                }
                if (!added) {
                    ArrayList<Rect> al = new ArrayList<Rect>();
                    al.add(rect);
                    sortedLetters.put(new Range(rect.y, rect.y + rect.height), al);
                }
            } else {
                ArrayList<Rect> al = new ArrayList<Rect>();
                al.add(rect);
                sortedLetters.put(new Range(rect.y, rect.y + rect.height), al);
            }
        }

        ArrayList<List<Rect>> letters = new ArrayList<List<Rect>>();
        // x-ovy priemet
        for (Map.Entry<Range<Integer, Integer>, List<Rect>> entry : sortedLetters.entrySet()) {
            if (entry.getValue().size() > 8 && entry.getValue().size() < 42) {
                List<Rect> al = entry.getValue();
                radixsort(al);
                letters.add(al);
            }
        }

        return letters;
    }

    public void radixsort(List<Rect> input) {

        final int RADIX = 10;
        List<Rect>[] bucket = new ArrayList[RADIX];
        for (int i = 0; i < bucket.length; i++) {
            bucket[i] = new ArrayList<Rect>();
        }

        // sort
        boolean maxLength = false;
        int tmp;
        int placement = 1;
        while (!maxLength) {
            maxLength = true;

            for (int i = 0; i < input.size(); i++) {
                tmp = input.get(i).x / placement;
                bucket[tmp % RADIX].add(input.get(i));
                if (maxLength && tmp > 0) {
                    maxLength = false;
                }
            }

            int counter = 0;
            for (int b = 0; b < RADIX; b++) {
                for (int i = 0; i < bucket[b].size(); i++) {
                    input.set(counter++, bucket[b].get(i));
                }
                bucket[b].clear();
            }

            placement *= RADIX;
        }
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
                    case R.id.process:
                        new PreprocessTask(this).execute();
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

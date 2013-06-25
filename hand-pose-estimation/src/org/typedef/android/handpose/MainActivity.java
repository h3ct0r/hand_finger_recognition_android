package org.typedef.android.handpose;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfInt4;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;
import org.typedef.android.handpose.imageProcessing.ColorBlobDetector;

import android.app.Activity;
import android.graphics.SumPathEffect;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.core.Size;

public class MainActivity extends Activity implements OnTouchListener, CvCameraViewListener2 {

    private static final String    TAG                 = "HandPose::MainActivity";
    public static final int        JAVA_DETECTOR       = 0;
    public static final int        NATIVE_DETECTOR     = 1;

    private Mat                    mRgba;
    private Mat                    mGray;
    private Mat 					mIntermediateMat;

    private int                    mDetectorType       = JAVA_DETECTOR;

    private CustomSufaceView   mOpenCvCameraView;
    private List<Size> mResolutionList;
    
    private SeekBar minTresholdSeekbar = null;
    private SeekBar maxTresholdSeekbar = null;
    private TextView minTresholdSeekbarText = null;
    private TextView numberOfFingersText = null;
    
    double iThreshold = 0;
    
    private Scalar               	mBlobColorHsv;
    private Scalar               	mBlobColorRgba;
    private ColorBlobDetector    	mDetector;
    private Mat                  	mSpectrum;
    private boolean				mIsColorSelected = false;

    private Size                 	SPECTRUM_SIZE;
    private Scalar               	CONTOUR_COLOR;
    private Scalar               	CONTOUR_COLOR_WHITE;
    
    final Handler mHandler = new Handler();
    int numberOfFingers = 0;
    
    final Runnable mUpdateFingerCountResults = new Runnable() {
        public void run() {
        	updateNumberOfFingers();
        }
    };
    
    private BaseLoaderCallback  mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                    mOpenCvCameraView.setOnTouchListener(MainActivity.this);
                    // 640x480
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    public MainActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.main_surface_view);

        mOpenCvCameraView = (CustomSufaceView) findViewById(R.id.main_surface_view);
        mOpenCvCameraView.setCvCameraViewListener(this);
        
        minTresholdSeekbarText = (TextView) findViewById(R.id.textView3);
        
        
        numberOfFingersText = (TextView) findViewById(R.id.numberOfFingers);
        
        minTresholdSeekbar = (SeekBar)findViewById(R.id.seekBar1);        
        minTresholdSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
	        	int progressChanged = 0;
	        	 
	            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser){
	                progressChanged = progress;
	                minTresholdSeekbarText.setText(String.valueOf(progressChanged));
	            }
	 
	            public void onStartTrackingTouch(SeekBar seekBar) {
	                // TODO Auto-generated method stub
	            }
	 
	            public void onStopTrackingTouch(SeekBar seekBar) {
	            	minTresholdSeekbarText.setText(String.valueOf(progressChanged));
	            }
		});
        minTresholdSeekbar.setProgress(8700);
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null){
            mOpenCvCameraView.disableView();
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, mLoaderCallback);
    }

    public void onDestroy() {
        super.onDestroy();
        mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
        mGray = new Mat();
        mRgba = new Mat();
        mIntermediateMat = new Mat();
        
        /*
        mResolutionList = mOpenCvCameraView.getResolutionList();
        ListIterator<Size> resolutionItr = mResolutionList.listIterator();
        while(resolutionItr.hasNext()) {
            Size element = resolutionItr.next();
            Log.i(TAG, "Resolution Option ["+Integer.valueOf(element.width).toString() + "x" + Integer.valueOf(element.height).toString()+"]");
        }
        
        Size resolution = mResolutionList.get(7);
        mOpenCvCameraView.setResolution(resolution);
        resolution = mOpenCvCameraView.getResolution();
        String caption = "Resolution "+ Integer.valueOf(resolution.width).toString() + "x" + Integer.valueOf(resolution.height).toString();
        Toast.makeText(this, caption, Toast.LENGTH_SHORT).show();
        */
        Camera.Size resolution = mOpenCvCameraView.getResolution();
        String caption = "Resolution "+ Integer.valueOf(resolution.width).toString() + "x" + Integer.valueOf(resolution.height).toString();
        Toast.makeText(this, caption, Toast.LENGTH_SHORT).show();
        
        Camera.Parameters cParams = mOpenCvCameraView.getParameters();
        cParams.setFocusMode(Camera.Parameters.FOCUS_MODE_INFINITY);
        mOpenCvCameraView.setParameters(cParams);
        Toast.makeText(this, "Focus mode : "+cParams.getFocusMode(), Toast.LENGTH_SHORT).show();
        
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mDetector = new ColorBlobDetector();
        mSpectrum = new Mat();
        mBlobColorRgba = new Scalar(255);
        mBlobColorHsv = new Scalar(255);
        SPECTRUM_SIZE = new Size(200, 64);
        CONTOUR_COLOR = new Scalar(255,0,0,255);
        CONTOUR_COLOR_WHITE = new Scalar(255,255,255,255);
        
    }

    public void onCameraViewStopped() {
        mGray.release();
        mRgba.release();
    }

    public boolean onTouch(View v, MotionEvent event) {
        int cols = mRgba.cols();
        int rows = mRgba.rows();

        int xOffset = (mOpenCvCameraView.getWidth() - cols) / 2;
        int yOffset = (mOpenCvCameraView.getHeight() - rows) / 2;

        int x = (int)event.getX() - xOffset;
        int y = (int)event.getY() - yOffset;

        Log.i(TAG, "Touch image coordinates: (" + x + ", " + y + ")");

        if ((x < 0) || (y < 0) || (x > cols) || (y > rows)) return false;

        Rect touchedRect = new Rect();

        touchedRect.x = (x>5) ? x-5 : 0;
        touchedRect.y = (y>5) ? y-5 : 0;

        touchedRect.width = (x+5 < cols) ? x + 5 - touchedRect.x : cols - touchedRect.x;
        touchedRect.height = (y+5 < rows) ? y + 5 - touchedRect.y : rows - touchedRect.y;

        Mat touchedRegionRgba = mRgba.submat(touchedRect);

        Mat touchedRegionHsv = new Mat();
        Imgproc.cvtColor(touchedRegionRgba, touchedRegionHsv, Imgproc.COLOR_RGB2HSV_FULL);

        // Calculate average color of touched region
        mBlobColorHsv = Core.sumElems(touchedRegionHsv);
        int pointCount = touchedRect.width*touchedRect.height;
        for (int i = 0; i < mBlobColorHsv.val.length; i++)
            mBlobColorHsv.val[i] /= pointCount;

        mBlobColorRgba = converScalarHsv2Rgba(mBlobColorHsv);

        Log.i(TAG, "Touched rgba color: (" + mBlobColorRgba.val[0] + ", " + mBlobColorRgba.val[1] +
                ", " + mBlobColorRgba.val[2] + ", " + mBlobColorRgba.val[3] + ")");

        mDetector.setHsvColor(mBlobColorHsv);

        Imgproc.resize(mDetector.getSpectrum(), mSpectrum, SPECTRUM_SIZE);

        mIsColorSelected = true;

        touchedRegionRgba.release();
        touchedRegionHsv.release();

        return false; // don't need subsequent touch events
    }
    
    private Scalar converScalarHsv2Rgba(Scalar hsvColor) {
        Mat pointMatRgba = new Mat();
        Mat pointMatHsv = new Mat(1, 1, CvType.CV_8UC3, hsvColor);
        Imgproc.cvtColor(pointMatHsv, pointMatRgba, Imgproc.COLOR_HSV2RGB_FULL, 4);

        return new Scalar(pointMatRgba.get(0, 0));
    }
    
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();

        iThreshold = minTresholdSeekbar.getProgress();  
        
        //Imgproc.blur(mRgba, mRgba, new Size(5,5));
        Imgproc.GaussianBlur(mRgba, mRgba, new org.opencv.core.Size(3, 3), 1, 1);
        //Imgproc.medianBlur(mRgba, mRgba, 3);
        
        if (!mIsColorSelected) return mRgba;

		List<MatOfPoint> contours = mDetector.getContours();
		mDetector.process(mRgba);

		Log.d(TAG, "Contours count: " + contours.size());

		if (contours.size() <= 0) {
			return mRgba;
		}

		RotatedRect rect = Imgproc.minAreaRect(new MatOfPoint2f(contours.get(0)	.toArray()));
		
		double boundWidth = rect.size.width;
		double boundHeight = rect.size.height;
		int boundPos = 0;

		for (int i = 1; i < contours.size(); i++) {
			rect = Imgproc.minAreaRect(new MatOfPoint2f(contours.get(i).toArray()));
			if (rect.size.width * rect.size.height > boundWidth * boundHeight) {
				boundWidth = rect.size.width;
				boundHeight = rect.size.height;
				boundPos = i;
			}
		}

		Rect boundRect = Imgproc.boundingRect(new MatOfPoint(contours.get(boundPos).toArray()));
		Core.rectangle( mRgba, boundRect.tl(), boundRect.br(), CONTOUR_COLOR_WHITE, 2, 8, 0 );
		
		Log.d(TAG, 
    			" Row start ["+ 
    			(int) boundRect.tl().y + "] row end ["+
    			(int) boundRect.br().y+"] Col start ["+
    			(int) boundRect.tl().x+"] Col end ["+
    			(int) boundRect.br().x+"]");
		
		int rectHeightThresh = 0;
		double a = boundRect.br().y - boundRect.tl().y;
		a = a * 0.7;
		a = boundRect.tl().y + a;
		
		Log.d(TAG, 
    			" A ["+a+"] br y - tl y = ["+(boundRect.br().y - boundRect.tl().y)+"]");
		
		//Core.rectangle( mRgba, boundRect.tl(), boundRect.br(), CONTOUR_COLOR, 2, 8, 0 );
		Core.rectangle( mRgba, boundRect.tl(), new Point(boundRect.br().x, a), CONTOUR_COLOR, 2, 8, 0 );
		
		MatOfPoint2f pointMat = new MatOfPoint2f();
		Imgproc.approxPolyDP(new MatOfPoint2f(contours.get(boundPos).toArray()), pointMat, 3, true);
		contours.set(boundPos, new MatOfPoint(pointMat.toArray()));
		
		MatOfInt hull = new MatOfInt();
		MatOfInt4 convexDefect = new MatOfInt4();
		Imgproc.convexHull(new MatOfPoint(contours.get(boundPos).toArray()), hull);
		
		if(hull.toArray().length < 3) return mRgba;
		
		Imgproc.convexityDefects(new MatOfPoint(contours.get(boundPos)	.toArray()), hull, convexDefect);

		List<MatOfPoint> hullPoints = new LinkedList<MatOfPoint>();
		List<Point> listPo = new LinkedList<Point>();
		for (int j = 0; j < hull.toList().size(); j++) {
			listPo.add(contours.get(boundPos).toList().get(hull.toList().get(j)));
		}

		MatOfPoint e = new MatOfPoint();
		e.fromList(listPo);
		hullPoints.add(e);

		List<MatOfPoint> defectPoints = new LinkedList<MatOfPoint>();
		List<Point> listPoDefect = new LinkedList<Point>();
		for (int j = 0; j < convexDefect.toList().size(); j = j+4) {
			Point farPoint = contours.get(boundPos).toList().get(convexDefect.toList().get(j+2));
			Integer depth = convexDefect.toList().get(j+3);
			if(depth > iThreshold && farPoint.y < a){
				listPoDefect.add(contours.get(boundPos).toList().get(convexDefect.toList().get(j+2)));
			}
			Log.d(TAG, "defects ["+j+"] " + convexDefect.toList().get(j+3));
		}

		MatOfPoint e2 = new MatOfPoint();
		e2.fromList(listPo);
		defectPoints.add(e2);

		Log.d(TAG, "hull: " + hull.toList());
		Log.d(TAG, "defects: " + convexDefect.toList());

		Imgproc.drawContours(mRgba, hullPoints, -1, CONTOUR_COLOR, 3);

		int defectsTotal = (int) convexDefect.total();
		Log.d(TAG, "Defect total " + defectsTotal);
		
		this.numberOfFingers = listPoDefect.size();
		if(this.numberOfFingers > 5) this.numberOfFingers = 5;
		
		mHandler.post(mUpdateFingerCountResults);
		
		for(Point p : listPoDefect){
			Core.circle(mRgba, p, 6, new Scalar(255,0,255));
		}
        
        return mRgba;
    }
    
    public void updateNumberOfFingers(){
    	numberOfFingersText.setText(String.valueOf(this.numberOfFingers));
    }
}

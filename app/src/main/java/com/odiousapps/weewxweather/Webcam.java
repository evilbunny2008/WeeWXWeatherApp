package com.odiousapps.weewxweather;

import android.graphics.Bitmap;
import java.io.File;
import java.text.DateFormat;
import java.util.Date;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.checkbox.MaterialCheckBox;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import static com.odiousapps.weewxweather.MainActivity.WEBCAM_IMAGE_ZOOM;
import static com.odiousapps.weewxweather.WeeWXApp.getAndroidString;
import static com.odiousapps.weewxweather.WeeWXApp.getColours;
import static com.odiousapps.weewxweather.WeeWXApp.getHeight;
import static com.odiousapps.weewxweather.WeeWXApp.getWidth;
import static com.odiousapps.weewxweather.WeeWXApp.textToBitmap;
import static com.odiousapps.weewxweather.WeeWXApp.webcamFilename;
import static com.odiousapps.weewxweather.WeeWXApp.webcamInterval_default;
import static com.odiousapps.weewxweather.WeeWXApp.webcamRefreshOptions;
import static com.odiousapps.weewxweather.WeeWXAppCommon.LogMessage;
import static com.odiousapps.weewxweather.WeeWXAppCommon.getImage;
import static com.odiousapps.weewxweather.WeeWXAppCommon.processUpdateInBG;
import static com.odiousapps.weewxweather.WeeWXNotificationManager.observeNotifications;
import static com.odiousapps.weewxweather.WeeWXNotificationManager.removeNotificationObserver;

public class Webcam extends Fragment implements Refreshable
{
	private RotateLayout rl;
	private ImageView iv;
	private TextView snapshotTime;
	private SwipeRefreshLayout swipeLayout;
	private MaterialCheckBox floatingCheckBox;
	private MainActivity activity;
	private int updateInterval = 0;
	private float zoomScale = 1f;
	private float lastTouchX, lastTouchY;
	private boolean dragging;
	private ScaleGestureDetector scaleDetector;
	private final Handler handler = new Handler(Looper.getMainLooper());
	private final Runnable updateRunnable = new Runnable()
	{
		@Override
		public void run()
		{
			processUpdateInBG(false, false, false, false, true);
			LogMessage("Webcam.java processUpdateInBG(true, false, false, true, " +
					"false, false, false, true);");

			if(updateInterval > 0)
				handler.postDelayed(this, updateInterval);
		}
	};

	@Override
	public void refresh()
	{
		swipeLayout.setRefreshing(true);
		processUpdateInBG(false, false, false, false, true);
	}

	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		super.onCreateView(inflater, container, savedInstanceState);

		activity = (MainActivity)getActivity();

		View rootView = inflater.inflate(R.layout.fragment_webcam, container, false);
		rl = rootView.findViewById(R.id.rotateLayout);
		iv = rootView.findViewById(R.id.webcam);
		snapshotTime = rootView.findViewById(R.id.webcamSnapshotTime);
		swipeLayout = rootView.findViewById(R.id.swipeToRefresh);
		swipeLayout.setBackgroundColor(getColours().bgColour);

		floatingCheckBox = rootView.findViewById(R.id.floatingCheckBox);
		floatingCheckBox.setChecked((boolean)KeyValue.readVar(WEBCAM_IMAGE_ZOOM, false));
		floatingCheckBox.setOnClickListener(v ->
		{
			KeyValue.putVar(WEBCAM_IMAGE_ZOOM, floatingCheckBox.isChecked());
			updateSwipe();
		});
		DraggableLock.attach(floatingCheckBox, "webcam");

		configureZoom();

		swipeLayout.setRefreshing(true);
		swipeLayout.setOnRefreshListener(this::refresh);

//		processUpdateInBG(true, false, false, true,
//				false, false, false, true);
//		LogMessage("Webcam.java processUpdateInBG(true, false, false, true, " +
//				"false, false, false, true);");

		loadWebcamImage();

		observeNotifications(getViewLifecycleOwner(), notificationObserver);

		return rootView;
	}

	@SuppressWarnings({"ClickableViewAccessibility", "unused"})
	private void configureZoom()
	{
		scaleDetector = new ScaleGestureDetector(requireContext(),
				new ScaleGestureDetector.SimpleOnScaleGestureListener()
				{
					@Override
					public boolean onScale(@NonNull ScaleGestureDetector detector)
					{
						zoomScale = Math.clamp(zoomScale * detector.getScaleFactor(), 1f, 5f);
						applyZoom();
						return true;
					}
				});

		GestureDetector tapDetector = new GestureDetector(requireContext(), new GestureDetector.SimpleOnGestureListener()
		{
			@Override
			public boolean onDoubleTap(@NonNull MotionEvent event)
			{
				zoomScale = 1f;
				iv.setTranslationX(0f);
				iv.setTranslationY(0f);
				applyZoom();
				return true;
			}
		});

		iv.setOnTouchListener((view, event) ->
		{
			// Pinch-to-zoom only works while swiping is locked
			if(!floatingCheckBox.isChecked())
				return false;

			scaleDetector.onTouchEvent(event);
			//boolean tapped = tapDetector.onTouchEvent(event);
			int action = event.getActionMasked();
			if(action == MotionEvent.ACTION_DOWN)
			{
				lastTouchX = event.getX();
				lastTouchY = event.getY();
				dragging = true;
			}

			if(action == MotionEvent.ACTION_MOVE && event.getPointerCount() == 1 &&
					zoomScale > 1f && dragging && !scaleDetector.isInProgress())
			{
				float limitX = (zoomScale - 1f) * iv.getWidth() / 2f;
				float limitY = (zoomScale - 1f) * iv.getHeight() / 2f;
				iv.setTranslationX(Math.clamp(iv.getTranslationX() + event.getX() - lastTouchX, -limitX, limitX));
				iv.setTranslationY(Math.clamp(iv.getTranslationY() + event.getY() - lastTouchY, -limitY, limitY));
			}

			if(action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_POINTER_DOWN)
				 dragging = false;

			if(action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
				dragging = false;

			lastTouchX = event.getX();
			lastTouchY = event.getY();

			return true;
		});
	}

	private void applyZoom()
	{
		iv.setScaleX(zoomScale);
		iv.setScaleY(zoomScale);
		if(zoomScale == 1f)
		{
			iv.setTranslationX(0f);
			iv.setTranslationY(0f);
		}
	}

	private void updateSwipe()
	{
		boolean locked = floatingCheckBox.isChecked();
		if(!locked && zoomScale != 1f)
		{
			zoomScale = 1f;
			applyZoom();
		}

		swipeLayout.setEnabled(!locked);

		if(isResumed())
		{
			LogMessage((locked ? "Disabling" : "Enabling") + " swipe between screens...");
			activity.setUserInputPager(!locked);
		}
	}

	@Override
	public void onDestroyView()
	{
		LogMessage("Webcam.onDestroyView()");
		super.onDestroyView();

		removeNotificationObserver(notificationObserver);
	}

	public void onResume()
	{
		super.onResume();
		updateSwipe();
		setLoopInterval();
	}

	public void onPause()
	{
		super.onPause();
		handler.removeCallbacks(updateRunnable);

		LogMessage("Enabling swipe between screens...");
		activity.setUserInputPager(true);
	}

	void setLoopInterval()
	{
		handler.removeCallbacks(updateRunnable);
		updateInterval = 0;
		if(!KeyValue.isPrefSet("webcamInterval"))
			return;

		int webcamRefreshInterval = (int)KeyValue.readVar("webcamInterval", webcamInterval_default);
		if(webcamRefreshInterval < 0 || webcamRefreshInterval >= webcamRefreshOptions.length)
			webcamRefreshInterval = webcamInterval_default;

		switch(webcamRefreshInterval)
		{
			case 0 -> updateInterval = 0;
			case 1 -> updateInterval = 10_000;
			case 2 -> updateInterval = 30_000;
			case 3 -> updateInterval = 60_000;
			default -> updateInterval = 300_000;
		}

		if(updateInterval > 0)
			handler.postDelayed(updateRunnable, updateInterval);
	}

	void stopRefreshing()
	{
		if(swipeLayout.isRefreshing())
			swipeLayout.post(() -> swipeLayout.setRefreshing(false));
	}

	private void showWebcamImage(Bitmap bm)
	{
		if(iv == null)
			return;

		if(bm == null)
		{
			noImageToShow(getAndroidString(R.string.webcam_still_downloading));
			stopRefreshing();
			return;
		}

		LogMessage("rl.getAngle()=" + rl.getAngle());
		LogMessage("screenHeightDp=" + getHeight());
		LogMessage("screenWidthDp=" + getWidth());
		LogMessage("bm.getWidth()=" + bm.getWidth());
		LogMessage("bm.getHeight()=" + bm.getHeight());

		if(getHeight() > getWidth() && bm.getWidth() > bm.getHeight())
		{
			if(rl.getAngle() != -90)
				rl.post(() -> rl.setAngle(-90));
		} else {
			if(rl.getAngle() != 0)
				rl.post(() -> rl.setAngle(0));
		}

		iv.post(() ->
		{
			iv.setImageBitmap(bm);
			updateSnapshotTime();
			stopRefreshing();
		});

		LogMessage("Finished reading webcam.jpg into memory and iv should have updated...");
	}

	// The snapshot file's time is the server's Last-Modified time when it sent one,
	// otherwise it's when the snapshot was saved locally.
	private void updateSnapshotTime()
	{
		if(snapshotTime == null)
			return;
		File file = new File(WeeWXAppCommon.getDataDir(), webcamFilename);
		if(file.exists() && file.lastModified() > 0)
		{
			String time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
					.format(new Date(file.lastModified()));
			boolean hasLastModified = (boolean)KeyValue.readVar(WeeWXAppCommon.WEBCAM_HAS_LAST_MODIFIED, false);
			snapshotTime.setText(getString(hasLastModified ? R.string.webcam_last_modified_at :
					R.string.webcam_snapshot_saved_at, time));
		} else {
			snapshotTime.setText("");
		}
	}

	private void loadWebcamImage()
	{
		LogMessage("loadWebcamImage...");

		try
		{
			Bitmap bm = getImage(webcamFilename);
			if(bm != null)
				showWebcamImage(bm);
			else
				noImageToShow(getAndroidString(R.string.webcam_still_downloading));
		} catch(Exception e) {
			LogMessage("loadWebcamImage() Error! e: " + e, true, KeyValue.e);
			noImageToShow("Error: " + e);
		}

		stopRefreshing();
	}

	private final Observer<String> notificationObserver = str ->
	{
		LogMessage("Webcam.notificationObserver notificationObserver: " + str);

		if(str.equals(WeeWXAppCommon.REFRESH_WEBCAM_INTENT))
			loadWebcamImage();

		if(str.equals(WeeWXAppCommon.STOP_WEBCAM_INTENT))
			stopRefreshing();

		if(str.equals(WeeWXAppCommon.EXIT_INTENT))
			onPause();
	};

	private void noImageToShow(String text)
	{
		Bitmap bm = textToBitmap(text);
		iv.post(() ->
		{
			iv.setImageBitmap(bm);
			iv.invalidate();
		});
	}
}
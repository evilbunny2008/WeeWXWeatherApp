package com.odiousapps.weewxweather;

import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewManager;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import com.google.android.material.checkbox.MaterialCheckBox;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;


import static com.odiousapps.weewxweather.MainActivity.FORCE_DARK_MODE;
import static com.odiousapps.weewxweather.MainActivity.CUSTOM_PAGE_ZOOM;
import static com.odiousapps.weewxweather.WeeWXApp.CUSTOM_URL;
import static com.odiousapps.weewxweather.WeeWXApp.WARNING_BODY;
import static com.odiousapps.weewxweather.WeeWXApp.custom_url;
import static com.odiousapps.weewxweather.WeeWXApp.force_dark_mode_default;
import static com.odiousapps.weewxweather.WeeWXApp.getAndroidString;
import static com.odiousapps.weewxweather.WeeWXApp.getInstance;
import static com.odiousapps.weewxweather.WeeWXAppCommon.NPWSLL;
import static com.odiousapps.weewxweather.WeeWXAppCommon.is_valid_url;
import static com.odiousapps.weewxweather.WeeWXAppCommon.doStackOutput;
import static com.odiousapps.weewxweather.WeeWXAppCommon.LogMessage;
import static com.odiousapps.weewxweather.WeeWXAppCommon.getNPWSLL;
import static com.odiousapps.weewxweather.WeeWXNotificationManager.observeNotifications;
import static com.odiousapps.weewxweather.WeeWXNotificationManager.removeNotificationObserver;

@SuppressWarnings({"deprecation", "RequiresFeature"})
public class Custom extends Fragment implements Refreshable
{
	private SafeWebView wv;
	private SwipeRefreshLayout swipeLayout;
	private MaterialCheckBox floatingCheckBox;
	private MainActivity activity;
	private final ViewTreeObserver.OnScrollChangedListener scl = () ->
			swipeLayout.setEnabled(!floatingCheckBox.isChecked() && wv.getScrollY() == 0);

	@Override
	public void refresh()
	{
		swipeLayout.setRefreshing(true);
		LogMessage("onRefresh();");
		loadCustom(true);
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		LogMessage("Custom.onCreateView()");
		super.onCreateView(inflater, container, savedInstanceState);

		activity = (MainActivity)getActivity();

		View view = inflater.inflate(R.layout.fragment_custom, container, false);

		swipeLayout = view.findViewById(R.id.swipeToRefresh);
		swipeLayout.post(() ->
		{
			if(swipeLayout == null)
				return;

			swipeLayout.setRefreshing(true);
		});
		swipeLayout.setOnRefreshListener(this::refresh);

		if(wv == null)
			wv = new SafeWebView(getInstance());

		floatingCheckBox = view.findViewById(R.id.floatingCheckBox);
		floatingCheckBox.setChecked((boolean)KeyValue.readVar(CUSTOM_PAGE_ZOOM, false));
		floatingCheckBox.setOnClickListener(v ->
		{
			KeyValue.putVar(CUSTOM_PAGE_ZOOM, floatingCheckBox.isChecked());

			// Zoom back out to the page's minimum scale before zooming is disabled,
			// zoomBy() is clamped to the minimum scale and throws if the factor is <= 0.01
			if(!floatingCheckBox.isChecked() && wv != null)
				wv.zoomBy(0.02f);

			updateSwipe();
		});
		DraggableLock.attach(floatingCheckBox, "custom");

		wv.getSettings().setDisplayZoomControls(false);
		updateSwipe();

		FrameLayout fl = view.findViewById(R.id.custom);
		fl.removeAllViews();
		fl.addView(wv);

		wv.getViewTreeObserver().addOnScrollChangedListener(scl);

		wv.setOnCustomPageFinishedListener((v, url) -> swipeLayout.setRefreshing(false), true);

		wv.setOnKeyListener((v, keyCode, event) ->
		{
			if(wv != null && wv.canGoBack() && event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK)
			{
				wv.goBack();
				return true;
			}

			return false;
		});

		setMode();

		loadCustom(false);

		observeNotifications(getViewLifecycleOwner(), notificationObserver);

		return view;
	}

	// Pinch-to-zoom only works while swiping is locked.
	// Apply only to the Custom tab; leave SafeWebView defaults unchanged.
	private void updateSwipe()
	{
		if(wv == null)
			return;

		boolean locked = floatingCheckBox.isChecked();
		wv.getSettings().setSupportZoom(locked);
		wv.getSettings().setBuiltInZoomControls(locked);

		swipeLayout.setEnabled(!locked && wv.getScrollY() == 0);

		if(isResumed())
		{
			LogMessage((locked ? "Disabling" : "Enabling") + " swipe between screens...");
			activity.setUserInputPager(!locked);
		}
	}

	@Override
	public void onResume()
	{
		super.onResume();
		updateSwipe();
	}

	@Override
	public void onPause()
	{
		super.onPause();

		LogMessage("Enabling swipe between screens...");
		activity.setUserInputPager(true);
	}

	private void setMode()
	{
		boolean fdm = (boolean)KeyValue.readVar(FORCE_DARK_MODE, force_dark_mode_default);
		boolean darkmode = (int)KeyValue.readVar("mode", WeeWXApp.mode_default) == 1;

		if(darkmode && !fdm)
			darkmode = false;

		if(WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK) && fdm && wv != null)
		{
			if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2)
			{
				if(WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING))
				{
					try
					{
						WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.getSettings(), darkmode);
					} catch(Exception e) {
						doStackOutput(e);
					}
				}
			} else {
				if(WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY))
					WebSettingsCompat.setForceDarkStrategy(wv.getSettings(),
							WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY);

				if(WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING))
					WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.getSettings(), darkmode);

				int mode = WebSettingsCompat.FORCE_DARK_OFF;
				if(darkmode)
					mode = WebSettingsCompat.FORCE_DARK_ON;

				WebSettingsCompat.setForceDark(wv.getSettings(), mode);
			}
		}
	}

	@Override
	public void onDestroyView()
	{
		LogMessage("Custom.onDestroyView()");
		super.onDestroyView();

		removeNotificationObserver(notificationObserver);

		if(wv != null)
		{
			ViewParent parent = wv.getParent();
			if(parent instanceof ViewGroup)
				((ViewManager)parent).removeView(wv);

			wv.getViewTreeObserver().removeOnScrollChangedListener(scl);

			wv.destroy();

			wv = null;

			LogMessage("Custom.onDestroyView() wv destroyed...");
		}
	}

	private void loadCustom(boolean forced)
	{
		if(wv == null)
			return;

		NPWSLL npwsll = getNPWSLL();
		if(!forced && npwsll.periodTime() <= 0L)
		{
			String tmpStr = WeeWXApp.current_dialog_html
					.replace(WARNING_BODY, getAndroidString(R.string.manual_update_set_refresh_screen_to_load));

			wv.post(() ->
			{
				if(wv == null)
					return;

				wv.loadDataWithBaseURL(null, tmpStr,
					"text/html", "utf-8", null);
			});
			return;
		}

		String custom = (String)KeyValue.readVar(CUSTOM_URL, "");
		String customUrl = (String)KeyValue.readVar(custom_url, "");

		LogMessage("loadCustom() custom: " + custom);
		LogMessage("loadCustom() customUrl: " + customUrl);

		wv.stopLoading();

		if(is_valid_url(customUrl))
			wv.post(() ->
			{
				if(wv == null)
					return;

				wv.loadUrl(customUrl);
			});
		else if(is_valid_url(custom))
			wv.post(() ->
			{
				if(wv == null)
					return;

				wv.loadUrl(custom);
			});
	}

	private final Observer<String> notificationObserver = str ->
	{
		LogMessage("Custom.java notificationObserver: " + str);

		if(str.equals(WeeWXAppCommon.REFRESH_DARKMODE_INTENT))
			setMode();
	};
}
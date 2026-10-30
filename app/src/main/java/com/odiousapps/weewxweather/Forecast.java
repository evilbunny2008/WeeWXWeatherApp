package com.odiousapps.weewxweather;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.checkbox.MaterialCheckBox;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import static com.odiousapps.weewxweather.WeeWXApp.getAndroidString;
import static com.odiousapps.weewxweather.WeeWXApp.getInstance;
import static com.odiousapps.weewxweather.WeeWXAppCommon.bitmapToBytes;
import static com.odiousapps.weewxweather.WeeWXAppCommon.doStackOutput;
import static com.odiousapps.weewxweather.WeeWXAppCommon.LogMessage;
import static com.odiousapps.weewxweather.WeeWXAppCommon.getFileNameFromURL;
import static com.odiousapps.weewxweather.WeeWXAppCommon.getGsonContent;
import static com.odiousapps.weewxweather.WeeWXAppCommon.getImage;
import static com.odiousapps.weewxweather.WeeWXAppCommon.getImageBounds;
import static com.odiousapps.weewxweather.WeeWXAppCommon.is_blank;
import static com.odiousapps.weewxweather.WeeWXAppCommon.NPWSLL;
import static com.odiousapps.weewxweather.WeeWXAppCommon.getNPWSLL;
import static com.odiousapps.weewxweather.WeeWXAppCommon.is_valid_url;
import static com.odiousapps.weewxweather.WeeWXAppCommon.processUpdateInBG;
import static com.odiousapps.weewxweather.WeeWXAppCommon.toBase64;
import static com.odiousapps.weewxweather.WeeWXNotificationManager.observeNotifications;
import static com.odiousapps.weewxweather.WeeWXNotificationManager.removeNotificationObserver;

@SuppressWarnings("deprecation")
public class Forecast extends Fragment implements View.OnClickListener, Refreshable
{
	private View rootView;
	private SafeWebView forecastWebView, radarWebView;
	private SwipeRefreshLayout swipeLayout1, swipeLayout2;
	private ImageView im;
	private FrameLayout rfl;
	private RotateLayout rl;
	private MaterialCheckBox floatingCheckBox;
	private boolean isVisible = false;
	private MainActivity activity;

	private final ExecutorService radarExecutor = Executors.newSingleThreadExecutor();
	private final Handler mainHandler = new Handler(Looper.getMainLooper());

	private final ViewTreeObserver.OnScrollChangedListener forecastScrollListener = () ->
						swipeLayout1.setEnabled(floatingCheckBox.getVisibility() != View.VISIBLE &&
						forecastWebView.getScrollY() == 0);

	private final ViewTreeObserver.OnScrollChangedListener radarScrollListener = () ->
						swipeLayout2.setEnabled(floatingCheckBox.getVisibility() == View.VISIBLE &&
						!floatingCheckBox.isChecked() && radarWebView.getScrollY() == 0);

	private void refreshForecast()
	{
		swipeLayout1.setRefreshing(true);
		LogMessage("swipeLayout1.onRefresh();");
		processUpdateInBG(false, false, true, false, false);
	}

	private void refreshRadar()
	{
		swipeLayout2.setRefreshing(true);
		LogMessage("swipeLayout2.onRefresh();");

		String radtype = (String)KeyValue.readVar("radtype", WeeWXApp.radtype_default);
		if(radtype != null && radtype.equals("image"))
			processUpdateInBG(false, false, false, true, false);
		else
			loadRadar(true);
	}

	// Refresh whichever of the forecast or radar is showing in this tab
	@Override
	public void refresh()
	{
		if(swipeLayout1.isShown())
			refreshForecast();
		else
			refreshRadar();
	}

	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		super.onCreateView(inflater, container, savedInstanceState);

		activity = (MainActivity)getActivity();

		rootView = inflater.inflate(R.layout.fragment_forecast, container, false);

		rfl = rootView.findViewById(R.id.radar_layout);
		rl = rootView.findViewById(R.id.rotateLayout);

		swipeLayout1 = rootView.findViewById(R.id.swipeToRefresh1);
		swipeLayout1.setOnRefreshListener(this::refreshForecast);

		swipeLayout2 = rootView.findViewById(R.id.swipeToRefresh2);
		swipeLayout2.setOnRefreshListener(this::refreshRadar);

		im = rootView.findViewById(R.id.logo);

		floatingCheckBox = rootView.findViewById(R.id.floatingCheckBox);
		floatingCheckBox.setOnClickListener(this);
		DraggableLock.attach(floatingCheckBox, "radar");

		if(forecastWebView == null)
		{
			forecastWebView = new SafeWebView(getInstance());
			forecastWebView.getViewTreeObserver().addOnScrollChangedListener(forecastScrollListener);
		}

		FrameLayout forecastFL = rootView.findViewById(R.id.forecastWebView);
		forecastFL.addView(forecastWebView);

		if(radarWebView == null)
		{
			radarWebView = new SafeWebView(getInstance());
			radarWebView.getViewTreeObserver().addOnScrollChangedListener(radarScrollListener);
		}

		FrameLayout fl = rootView.findViewById(R.id.radarFrameLayout);
		fl.addView(radarWebView);

		forecastWebView.setOnCustomPageFinishedListener((v, url) ->
		{
			LogMessage("forecastWebView.onPageFinished()");
			stopRefreshing();
		}, false);

		radarWebView.setOnCustomPageFinishedListener((v, url) ->
		{
			LogMessage("radarWebView.onPageFinished()");
			stopRefreshing();
		}, false);

		if(KeyValue.isPrefSet("radarforecast"))
		{
			boolean disableSwipeOnRadar = (boolean)KeyValue.readVar("disableSwipeOnRadar", WeeWXApp.disableSwipeOnRadar_default);
			floatingCheckBox.setChecked(disableSwipeOnRadar);

			updateSwipe();
			updateScreen();

			if((boolean)KeyValue.readVar("radarforecast", WeeWXApp.radarforecast_default) == WeeWXApp.RadarOnForecastScreen)
				loadRadar(false);
			else
				getForecast();
		}

		observeNotifications(getViewLifecycleOwner(), notificationObserver);

		return rootView;
	}

	@Override
	public void onDestroyView()
	{
		super.onDestroyView();

		removeNotificationObserver(notificationObserver);

		if(forecastWebView != null)
		{
			ViewParent parent = forecastWebView.getParent();
			if(parent instanceof ViewGroup)
				((ViewGroup)parent).removeView(forecastWebView);

			forecastWebView.getViewTreeObserver().removeOnScrollChangedListener(forecastScrollListener);

			forecastWebView.destroy();

			forecastWebView = null;

			LogMessage("Forecast.onDestroyView() forecastWebView destroyed...");
		}

		if(radarWebView != null)
		{
			ViewParent parent = radarWebView.getParent();
			if(parent instanceof ViewGroup)
				((ViewGroup)parent).removeView(radarWebView);

			radarWebView.getViewTreeObserver().removeOnScrollChangedListener(radarScrollListener);

			radarWebView.destroy();

			radarWebView = null;

			LogMessage("Forecast.onDestroyView() radarWebView destroyed...");
		}
	}

	private void setMode()
	{
		boolean fdm = (boolean)KeyValue.readVar(MainActivity.FORCE_DARK_MODE, WeeWXApp.force_dark_mode_default);
		boolean darkmode = (int)KeyValue.readVar("mode", WeeWXApp.mode_default) == 1;

		if(darkmode && !fdm)
			darkmode = false;

		if(WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK))
		{
			if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2)
			{
				if(WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING))
				{
					try
					{
						WebSettingsCompat.setAlgorithmicDarkeningAllowed(radarWebView.getSettings(), darkmode);
					} catch(Exception e) {
						doStackOutput(e);
					}
				}
			} else {
				if(WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY))
				{
					WebSettingsCompat.setForceDarkStrategy(radarWebView.getSettings(),
							WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY);

					if(WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING))
						WebSettingsCompat.setAlgorithmicDarkeningAllowed(radarWebView.getSettings(), darkmode);
				}

				int mode = WebSettingsCompat.FORCE_DARK_OFF;
				if(darkmode)
					mode = WebSettingsCompat.FORCE_DARK_ON;

				WebSettingsCompat.setForceDark(radarWebView.getSettings(), mode);
			}
		}
	}

	private void stopRefreshing()
	{
		if(swipeLayout1.isRefreshing())
			swipeLayout1.post(() ->
			{
				if(swipeLayout1 == null)
					return;

				swipeLayout1.setRefreshing(false);
			});

		if(swipeLayout2.isRefreshing())
			swipeLayout2.post(() ->
			{
				if(swipeLayout2 == null)
					return;

				swipeLayout2.setRefreshing(false);
			});
	}

	@WorkerThread
	private void loadRadarInBackground(String radarURL)
	{
		Bitmap bm = getImage(getFileNameFromURL(radarURL));
		if(bm == null)
		{
			mainHandler.post(() ->
			{
				failedRadarWebViewDownload(R.string.radar_download_failed);
				stopRefreshing();
			});

			return;
		}

		String contentType = "image/jpeg";
		String url = radarURL.toLowerCase(Locale.ENGLISH);
		if(!url.isBlank())
			if(url.endsWith(".png"))
				contentType = "image/png";
			else if(url.endsWith(".gif"))
				contentType = "image/gif";

		LogMessage("Loading radar image... url: " + radarURL);
		String radar = "data:" + contentType + ";base64," + toBase64(bitmapToBytes(bm));

		String html = WeeWXApp.current_html_headers +
					  WeeWXApp.html_header_rest +
					  "\n\t<img class='radarImage' alt='Radar Image' src='" + radar + "'>\n" +
					  WeeWXApp.html_footer;

		mainHandler.post(() ->
		{
			if(radarWebView == null)
				return;

			radarWebView.loadDataWithBaseURL(null, html,
				"text/html", "utf-8", null);
			stopRefreshing();
		});
	}

	@MainThread
	private void loadRadar(boolean forced)
	{
		LogMessage("Forecast.java loadRadar()");

		if((boolean)KeyValue.readVar("radarforecast", WeeWXApp.radarforecast_default) != WeeWXApp.RadarOnForecastScreen)
			return;

		String radtype = (String)KeyValue.readVar("radtype", WeeWXApp.radtype_default);
		if(is_blank(radtype))
		{
			String tmp = getAndroidString(R.string.radar_type_is_invalid, radtype);
			failedRadarWebViewDownload(tmp);
			return;
		}

		String radarURL = (String)KeyValue.readVar("RADAR_URL", "");
		if(is_blank(radarURL))
		{
			mainHandler.post(() ->
				failedRadarWebViewDownload(R.string.radar_url_not_set));
			return;
		}

		if(radtype.equals("image"))
		{
			radarExecutor.execute(() -> loadRadarInBackground(radarURL));
			return;
		}

		NPWSLL npwsll = getNPWSLL();
		if(!forced && npwsll.periodTime() <= 0)
		{
			LogMessage("Manual updating set, don't autoload the radar webpage...", KeyValue.w);
			failedRadarWebViewDownload(R.string.manual_update_set_refresh_screen_to_load);
			stopRefreshing();
			return;
		}

		LogMessage("Loading radar page... url: " + radarURL);

		// Pages like windy.com size themselves to the viewport when they load, so wait
		// until the WebView has been laid out, otherwise they render at the wrong size
		SafeWebView.runWhenSized(radarWebView, () ->
		{
			if(radarWebView == null)
				return;

			radarWebView.loadUrl(radarURL);
		});

		stopRefreshing();
	}

	private void failedRadarWebViewDownload(int resId)
	{
		String html = WeeWXApp.current_dialog_html.replace(WeeWXApp.WARNING_BODY, getAndroidString(resId));

		radarWebView.post(() ->
		{
			if(radarWebView == null)
				return;

			radarWebView.loadDataWithBaseURL(null, html,
				"text/html", "utf-8", null);
		});

		stopRefreshing();
	}

	private void failedRadarWebViewDownload(String str)
	{
		final String html = WeeWXApp.current_html_headers + WeeWXApp.html_header_rest + str + WeeWXApp.html_footer;

		radarWebView.post(() ->
		{
			if(radarWebView == null)
				return;

			radarWebView.loadDataWithBaseURL(null, html,
				"text/html", "utf-8", null);
		});

		stopRefreshing();
	}

	public void onResume()
	{
		super.onResume();

		LogMessage("Forecast.onResume()");

		if(isVisible)
			return;

		isVisible = true;

		if(KeyValue.isPrefSet("radarforecast"))
		{
			boolean disableSwipeOnRadar = (boolean)KeyValue.readVar("disableSwipeOnRadar", WeeWXApp.disableSwipeOnRadar_default);
			floatingCheckBox.setChecked(disableSwipeOnRadar);

			updateSwipe();
			updateScreen();
		}
	}

	public void onPause()
	{
		super.onPause();

		LogMessage("Forecast.java onPause()");

		if(!isVisible)
			return;

		isVisible = false;

		LogMessage("Enabling swipe between screens...");
		if(!swipeLayout1.isEnabled())
			swipeLayout1.post(() ->
			{
				if(swipeLayout1 == null)
					return;

				swipeLayout1.setEnabled(true);
			});

		if(!swipeLayout2.isEnabled())
			swipeLayout2.post(() ->
			{
				if(swipeLayout2 == null)
					return;

				swipeLayout2.setEnabled(true);
			});

		activity.setUserInputPager(true);

		stopRefreshing();
	}

	private final Observer<String> notificationObserver = str ->
	{
		LogMessage("Forecast.java notificationObserver: " + str);

		String radtype = (String)KeyValue.readVar("radtype", WeeWXApp.radtype_default);
		if(radtype == null)
			radtype = "";

		if(str.equals(WeeWXAppCommon.REFRESH_FORECAST_INTENT))
			getForecast();

		if(str.equals(WeeWXAppCommon.REFRESH_RADAR_INTENT))
			loadRadar(false);

		if(str.equals(WeeWXAppCommon.REFRESH_DARKMODE_INTENT))
			setMode();

		if((boolean)KeyValue.readVar("radarforecast", WeeWXApp.radarforecast_default) == WeeWXApp.RadarOnForecastScreen &&
		   str.equals(WeeWXAppCommon.STOP_RADAR_INTENT) && radtype.equals("image"))
			stopRefreshing();

		if((boolean)KeyValue.readVar("radarforecast", WeeWXApp.radarforecast_default) == WeeWXApp.ForecastOnForecastScreen &&
		   str.equals(WeeWXAppCommon.STOP_FORECAST_INTENT))
			stopRefreshing();

		if(str.equals(WeeWXAppCommon.EXIT_INTENT))
			onPause();
	};

	private void updateScreen()
	{
		LogMessage("Forecast.java updateScreen()");

		if((boolean)KeyValue.readVar("radarforecast", WeeWXApp.radarforecast_default) == WeeWXApp.ForecastOnForecastScreen)
		{
			LogMessage("Displaying forecastWebView...");
			if(rfl.getVisibility() != View.GONE)
				rfl.post(() ->
				{
					if(rfl == null)
						return;

					rfl.setVisibility(View.GONE);
				});

			if(swipeLayout1.getVisibility() != View.VISIBLE)
				swipeLayout1.post(() -> swipeLayout1.setVisibility(View.VISIBLE));

			swipeLayout2.post(() ->
			{
				if(swipeLayout2.isRefreshing())
					swipeLayout2.setRefreshing(false);
				swipeLayout2.setEnabled(false);
			});

			swipeLayout1.post(() ->
			{
				swipeLayout1.setBackgroundColor(WeeWXApp.getColours().bgColour);
				if(swipeLayout1.isRefreshing())
					swipeLayout1.setRefreshing(false);
				swipeLayout1.setEnabled(true);
			});
		} else {
			LogMessage("Displaying radar framelayout");
			if(swipeLayout1.getVisibility() != View.GONE)
				swipeLayout1.post(() -> swipeLayout1.setVisibility(View.GONE));

			if(rfl.getVisibility() != View.VISIBLE)
				rfl.post(() -> rfl.setVisibility(View.VISIBLE));

			swipeLayout1.post(() ->
			{
				if(swipeLayout1.isRefreshing())
					swipeLayout1.setRefreshing(false);
				swipeLayout1.setEnabled(false);
			});

			swipeLayout2.post(() ->
			{
				if(swipeLayout2.isRefreshing())
					swipeLayout2.setRefreshing(false);
				swipeLayout2.setEnabled(!floatingCheckBox.isChecked());
			});

			boolean rotate = false;

			String radtype = (String)KeyValue.readVar("radtype", WeeWXApp.radtype_default);
			if(is_blank(radtype))
			{
				if(rl.getAngle() != 0)
					rl.post(() -> rl.setAngle(0));

				if(floatingCheckBox.getVisibility() != View.GONE)
					floatingCheckBox.post(() -> floatingCheckBox.setVisibility(View.GONE));

				String tmp = getAndroidString(R.string.radar_type_is_invalid, radtype);
				failedRadarWebViewDownload(tmp);
				return;
			}

			if(radtype.equals("image"))
			{
				String radar_URL = (String)KeyValue.readVar("RADAR_URL", "");
				if(!is_valid_url(radar_URL))
				{
					LogMessage("RADAR_URL is null or blank");
					String tmp = getAndroidString(R.string.radar_url_not_set);
					failedRadarWebViewDownload(tmp);
					return;
				}

				LogMessage("Hiding the floating checkbox...");
				if(floatingCheckBox.getVisibility() != View.GONE)
					floatingCheckBox.post(() -> floatingCheckBox.setVisibility(View.GONE));

				BitmapFactory.Options bounds = getImageBounds(getFileNameFromURL(radar_URL));
				if(bounds != null && bounds.outWidth > bounds.outHeight &&
				   WeeWXApp.getHeight() > WeeWXApp.getWidth())
				{
					LogMessage("Hide radarWebView, show rotated layout...");
					rotate = true;
					if(rl.getAngle() != -90)
						rl.post(() -> rl.setAngle(-90));
				}
			} else if(radtype.equals("webpage")) {
				LogMessage("Showing the floating checkbox...");
				if(floatingCheckBox.getVisibility() != View.VISIBLE)
					floatingCheckBox.post(() -> floatingCheckBox.setVisibility(View.VISIBLE));
			} else {
				if(rl.getAngle() != 0)
					rl.post(() -> rl.setAngle(0));

				if(floatingCheckBox.getVisibility() != View.GONE)
					floatingCheckBox.post(() -> floatingCheckBox.setVisibility(View.GONE));

				String tmp = getAndroidString(R.string.radar_type_is_invalid, radtype);
				failedRadarWebViewDownload(tmp);
				return;
			}

			if(!rotate)
			{
				LogMessage("Set RotatedLayout angle to 0...");
				if(rl.getAngle() != 0)
					rl.post(() -> rl.setAngle(0));
			}
		}

		LogMessage("Forecast.updateScreen() finished...");
	}

	private void getForecast()
	{
		if((boolean)KeyValue.readVar("radarforecast", WeeWXApp.radarforecast_default) != WeeWXApp.ForecastOnForecastScreen)
			return;

		LogMessage("Forecast.getForecast() getForecast returned some content...");
		String forecastGson = (String)KeyValue.readVar("forecastGsonEncoded", "");
		boolean hasForecastGson = forecastGson != null && forecastGson.length() > 128;
		if(hasForecastGson)
			LogMessage("forecastGson.length(): " + forecastGson.length());

		if(!hasForecastGson)
		{
			showTextFC(getAndroidString(R.string.still_downloading_forecast_data));
			stopRefreshing();
			return;
		}

		String fctype = (String)KeyValue.readVar(WeeWXApp.FCTYPE, "");
		if(is_blank(fctype))
		{
			String finalErrorStr = getAndroidString(R.string.forecast_type_is_invalid, fctype);
			showTextFC(finalErrorStr);
			stopRefreshing();
			return;
		}

		String[] content = getGsonContent(forecastGson, false);
		if(content[0] != null && content[0].equals("error"))
		{
			if(!is_blank(content[1]))
			{
				showTextFC(content[1]);
			} else {
				showTextFC(getAndroidString(R.string.still_downloading_forecast_data));
			}

			stopRefreshing();
			return;
		}

		updateForecast(fctype, content[1], content.length > 2 ? content[2] : "");
	}

	private void showTextFC(String text)
	{
		if(is_blank(text))
			text = getAndroidString(R.string.forecast_url_not_set, "inigo-settings.txt");

		String html = WeeWXApp.current_html_headers +
					  WeeWXApp.html_header_rest + text +
					  WeeWXApp.html_footer;

		forecastWebView.post(() -> forecastWebView.loadDataWithBaseURL(null, html,
				"text/html", "utf-8", null));
	}

	private void updateForecast(String fctype, String bits, String desc)
	{
		if(is_blank(fctype) || is_blank(bits))
			return;

		String fc = WeeWXApp.current_html_headers +
					WeeWXApp.script_header +
					WeeWXApp.html_header_rest +
					WeeWXApp.inline_arrow +
					bits +
					WeeWXApp.arrow_spacer;

		if(WeeWXAppCommon.web_debug_on)
			fc += WeeWXApp.debug_html;

		fc += WeeWXApp.html_footer;

//		if(WeeWXAppCommon.debug_html)
//			CustomDebug.writeOutput(requireContext(), "forecast", fc, isVisible(), requireActivity());

		String finalfc = fc;

		forecastWebView.post(() -> forecastWebView.loadDataWithBaseURL("file:///android_asset/", finalfc,
					"text/html", "utf-8", null));

		TextView tv1 = rootView.findViewById(R.id.forecast);
		tv1.post(() ->
		{
			tv1.setBackgroundColor(WeeWXApp.getColours().bgColour);
			tv1.setTextColor(WeeWXApp.getColours().fgColour);
			tv1.setText(desc);
		});

		String extSVG = "_light.svg";
		String extPNG = "_light.png";
		if((int)KeyValue.readVar("theme", WeeWXApp.theme_default) != R.style.AppTheme_WeeWXApp_Dark_Common)
		{
			extSVG = "_dark.svg";
			extPNG = "_dark.png";
		}

		String finalextSVG = extSVG;
		String finalextPNG = extPNG;

		boolean isDarkTheme = (int)KeyValue.readVar("theme", WeeWXApp.theme_default) == R.style.AppTheme_WeeWXApp_Dark_Common;

		switch(fctype.toLowerCase(Locale.ENGLISH).strip())
		{
			case "yahoo" -> im.post(() -> im.setImageDrawable(WeeWXApp.loadSVGFromAssets("logos/yahoo" + finalextSVG)));
			case "weatherzone", "weatherzone2" -> im.post(() -> im.setImageDrawable(WeeWXApp.loadSVGFromAssets("logos/wz" + finalextSVG)));
			case "met.no" -> im.post(() -> {
				im.setImageBitmap(WeeWXApp.loadBitmapFromAssets("logos/met_no.png"));
				if(isDarkTheme)
					im.setColorFilter(android.graphics.Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
				else
					im.clearColorFilter();
			});
			case "wmo.int" -> im.post(() -> im.setImageBitmap(WeeWXApp.loadBitmapFromAssets("logos/wmo.png")));
			case "weather.gov" -> im.post(() -> im.setImageBitmap(WeeWXApp.loadBitmapFromAssets("logos/wgov.png")));
			case "weather.gc.ca", "weather.gc.ca-fr" -> im.post(() -> im.setImageBitmap(WeeWXApp.loadBitmapFromAssets("logos/wca.png")));
			case "metoffice.gov.uk" -> im.post(() -> im.setImageBitmap(WeeWXApp.loadBitmapFromAssets("logos/met.png")));
			case "bom2", "bom3daily", "bom3hourly" -> im.post(() -> im.setImageDrawable(WeeWXApp.loadSVGFromAssets("logos/bom" + finalextSVG)));
			case "aemet.es" -> im.post(() -> im.setImageBitmap(WeeWXApp.loadBitmapFromAssets("logos/aemet.png")));
			case "dwd.de" -> im.post(() -> im.setImageBitmap(WeeWXApp.loadBitmapFromAssets("logos/dwd.png")));
			case "metservice.com" -> im.post(() -> im.setImageDrawable(WeeWXApp.loadSVGFromAssets("logos/metservice" + finalextSVG)));
			case "meteofrance.com" -> im.post(() -> im.setImageBitmap(WeeWXApp.loadBitmapFromAssets("logos/mf.png")));
			case "openweathermap.org" -> im.post(() -> im.setImageBitmap(WeeWXApp.loadBitmapFromAssets("logos/owm.png")));
			case "apixu.com" -> im.post(() -> im.setImageBitmap(WeeWXApp.loadBitmapFromAssets("logos/apixu.png")));
			case "weather.com" -> im.post(() -> im.setImageBitmap(WeeWXApp.loadBitmapFromAssets("logos/weather_com.svg")));
			case "met.ie" -> im.post(() -> im.setImageBitmap(WeeWXApp.loadBitmapFromAssets("logos/met_ie" + finalextPNG)));
			case "ilmeteo.it" -> im.post(() -> im.setImageBitmap(WeeWXApp.loadBitmapFromAssets("logos/ilmeteo_it.png")));
			case "tempoitalia.it" -> im.post(() -> im.setImageBitmap(WeeWXApp.loadBitmapFromAssets("logos/tempoitalia_it.png")));
		}
	}

	public void updateSwipe()
	{
		if(rfl.getVisibility() == View.VISIBLE && floatingCheckBox.isChecked() && isVisible())
		{
			LogMessage("Disabling swipe between screens...");
			activity.setUserInputPager(false);
			swipeLayout2.setEnabled(false);
			return;
		}

		LogMessage("Enabling swipe between screens...");
		activity.setUserInputPager(true);
		swipeLayout2.setEnabled(true);
	}

	@Override
	public void onClick(View view)
	{
		KeyValue.putVar("disableSwipeOnRadar", floatingCheckBox.isChecked());
		updateSwipe();
		LogMessage("Forecast.onClick() finished...");
	}
}
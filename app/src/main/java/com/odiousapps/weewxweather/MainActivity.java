package com.odiousapps.weewxweather;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.method.LinkMovementMethod;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.window.OnBackInvokedDispatcher;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.slider.Slider;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textview.MaterialTextView;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.text.HtmlCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import okhttp3.HttpUrl;

import com.github.evilbunny2008.colourpicker.CPEditText;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5ClientBuilder;

import org.json.JSONException;
import org.json.JSONObject;

import static com.github.evilbunny2008.colourpicker.ColourPickerCommon.parseHexToColour;
import static com.github.evilbunny2008.colourpicker.ColourPickerCommon.to_ARGB_hex;

import static com.odiousapps.weewxweather.WidgetProvider.updateAppWidget;
import static com.odiousapps.weewxweather.WeeWXApp.CUSTOM_URL;
import static com.odiousapps.weewxweather.WeeWXApp.ENABLE_MQTT;
import static com.odiousapps.weewxweather.WeeWXApp.MQTT_TOPIC;
import static com.odiousapps.weewxweather.WeeWXApp.MQTT_URL;
import static com.odiousapps.weewxweather.WeeWXApp.RAINRATE_ALERT_SEVERE;
import static com.odiousapps.weewxweather.WeeWXApp.RAINRATE_ALERT_WARNING;
import static com.odiousapps.weewxweather.WeeWXApp.RAINRATE_ALERT_WATCH;
import static com.odiousapps.weewxweather.WeeWXApp.SAVE_APP_DEBUG_LOGS;
import static com.odiousapps.weewxweather.WeeWXApp.SETUP_FINISHED;
import static com.odiousapps.weewxweather.WeeWXApp.custom_url;
import static com.odiousapps.weewxweather.WeeWXApp.enable_mqtt_default;
import static com.odiousapps.weewxweather.WeeWXApp.getAndroidString;
import static com.odiousapps.weewxweather.WeeWXApp.getEnglishAndroidString;
import static com.odiousapps.weewxweather.WeeWXApp.getEnglishPlural;
import static com.odiousapps.weewxweather.WeeWXApp.getPlural;
import static com.odiousapps.weewxweather.WeeWXAppCommon.C2F;
import static com.odiousapps.weewxweather.WeeWXAppCommon.F2C;
import static com.odiousapps.weewxweather.WeeWXAppCommon.FAILED_TO_MERGE;
import static com.odiousapps.weewxweather.WeeWXAppCommon.INIGO_INTENT;
import static com.odiousapps.weewxweather.WeeWXAppCommon.PROCESSING_ERRORS;
import static com.odiousapps.weewxweather.WeeWXAppCommon.UPDATE_ERRORS;
import static com.odiousapps.weewxweather.WeeWXAppCommon.WIDGET_THEME_MODE;
import static com.odiousapps.weewxweather.WeeWXAppCommon.checkRainfallAlert;
import static com.odiousapps.weewxweather.WeeWXAppCommon.checkRainrateAlert;
import static com.odiousapps.weewxweather.WeeWXAppCommon.checkTempAlerts;
import static com.odiousapps.weewxweather.WeeWXAppCommon.doStackOutput;
import static com.odiousapps.weewxweather.WeeWXAppCommon.LogMessage;
import static com.odiousapps.weewxweather.WeeWXAppCommon.getDateTimeStr;
import static com.odiousapps.weewxweather.WeeWXAppCommon.getFileNameFromURL;
import static com.odiousapps.weewxweather.WeeWXAppCommon.headingTime;
import static com.odiousapps.weewxweather.WeeWXAppCommon.in2mm;
import static com.odiousapps.weewxweather.WeeWXAppCommon.is_blank;
import static com.odiousapps.weewxweather.WeeWXAppCommon.getFile;
import static com.odiousapps.weewxweather.WeeWXAppCommon.getIntervalTime;
import static com.odiousapps.weewxweather.WeeWXAppCommon.getJSONerrors;
import static com.odiousapps.weewxweather.WeeWXAppCommon.is_valid_url;
import static com.odiousapps.weewxweather.WeeWXAppCommon.json_keys;
import static com.odiousapps.weewxweather.WeeWXAppCommon.json_labels;
import static com.odiousapps.weewxweather.WeeWXAppCommon.mm2in;
import static com.odiousapps.weewxweather.WeeWXAppCommon.notMergeJsonObjects;
import static com.odiousapps.weewxweather.WeeWXAppCommon.processForecast;
import static com.odiousapps.weewxweather.WeeWXAppCommon.processWeather;
import static com.odiousapps.weewxweather.WeeWXAppCommon.saveJSONerrors;
import static com.odiousapps.weewxweather.WeeWXAppCommon.str2Float;
import static com.odiousapps.weewxweather.WeeWXAppCommon.Result3;
import static com.odiousapps.weewxweather.WeeWXNotificationManager.observeNotifications;
import static com.odiousapps.weewxweather.WeeWXNotificationManager.removeNotificationObserver;

@SuppressWarnings({"SequencedCollectionMethodCanBeUsed", "DataFlowIssue", "SourceLockedOrientationActivity", "Convert2MethodRef", "unused"})
public class MainActivity extends FragmentActivity
{
	private final ExecutorService bmExecutor = Executors.newSingleThreadExecutor();
	private final Handler mainHandler = new Handler(Looper.getMainLooper());

	static final String FORCE_DARK_MODE = "force_dark_mode";
	static final String CUSTOM_PAGE_ZOOM = "custom_page_zoom";
	static final String WEBCAM_IMAGE_ZOOM = "webcam_image_zoom";
	private static MainActivity instance;
	private boolean hasStarted = false;

	private static final int NOTIFICATION_PERMISSION_CODE = 1001;
	private static final int LOCAL_NETWORK_PERMISSION_CODE = 1002;
	private static final String LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK";
	private boolean pendingLocalSettingsSave = false;
	private boolean localNetworkPermissionRequested = false;

	private DrawerLayout mDrawerLayout;
	private TextInputLayout fgtil, bgtil;
	private TextInputEditText settingsURL, customURL;
	private CPEditText widgetBG, widgetFG;
	private MaterialButton b1;
	private MaterialButton b2;
	private MaterialSwitch wifi_only, show_indoor, metric_forecasts, rain_in_inches,
					use_exact_alarm, save_app_debug_logs, next_moon, force_dark_mode,
					morning_temp_alert, afternoon_temp_alert, rainfall_alert,
					rainrate_alert_watch, rainrate_alert_warning, rainrate_alert_severe,
					enable_mqtt;
	private MaterialRadioButton showRadar;
	private ViewPager2 mViewPager;
	private Slider sliderMorningTemp, sliderAfternoonTemp, sliderRainfall;

	private LinearLayout settingLayout, aboutLayout, morning_temp_setting, afternoon_temp_setting, rainfall_setting;

	private ScrollView scrollView;

	private SectionsStateAdapter mSectionsPagerAdapter;

	private final String utf8 = "UTF-8";

	private int UpdateFrequency, DayNightMode, widget_theme_mode, UpdateInterval, webcamInterval;

	private int appInitialLeft, appInitialRight, appInitialTop, appInitialBottom;
	private int cdInitialLeft, cdInitialRight, cdInitialTop, cdInitialBottom;
	private int dlInitialLeft, dlInitialRight, dlInitialTop, dlInitialBottom;
	private int rvInitialLeft, rvInitialRight, rvInitialTop, rvInitialBottom;

	private ImageButton hamburger;
	private boolean gestureNav = false;

	private int extraPx;

	private final ExecutorService executor = Executors.newSingleThreadExecutor();

	private Future<?> backgroundTask;

	private long bgStart = 0;

	private int theme;

	private record Setting(String name, int ResId) {}

	private final List<Setting> screen_elements = new ArrayList<>();

	ColorStateList strokeColors;

	private AlertDialog dialog;

	private int MorningTemp = 230;
	private int AfternoonTemp = 260;

	private int RainfallLimit = 2500;

	private Weather weather = null;
	private Stats stats = null;

	private Mqtt5AsyncClient mqttClient;
	private boolean mqttConnected = false;

	private JSONObject mqttOutput = null, mqttOutput2 = null, pendingJSON = null;

	private final String[] keys = {"barometer", "dewpoint", "ET", "inHumidity", "inTemp", "outHumidity", "outTemp", "radiation", "rain", "UV"};
	private final String[] formatting = {"%.1f", "%.1f", "%.1f", "%.0f", "%.1f", "%.0f", "%.1f", "%.0f", "%.1f", "%.1f"};

	private String lastTopic = null;

	private String rainFormat = "%.1f";

	static boolean mqttEnabled = false;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		instance = this;

		super.onCreate(savedInstanceState);

		if(KeyValue.webview_major_version < 83)
		{
			Intent intent = new Intent(this, WebViewTooOldActivity.class);
			startActivity(intent);
			finish();
			return;
		}

		theme = (int)KeyValue.readVar("theme", WeeWXApp.theme_default);

		setTheme(theme);

		if(WeeWXApp.theme_default == R.style.AppTheme_WeeWXApp_Light_Common)
			LogMessage("MainActivity.onCreate() WeeWXApp.theme_default: R.style.AppTheme_WeeWXApp_Light_Common");
		else if(WeeWXApp.theme_default == R.style.AppTheme_WeeWXApp_Dark_Common)
			LogMessage("MainActivity.onCreate() WeeWXApp.theme_default: R.style.AppTheme_WeeWXApp_Dark_Common");
		else if(WeeWXApp.theme_default == R.style.AppTheme_WeeWXApp_Common)
			LogMessage("MainActivity.onCreate() WeeWXApp.theme_default: R.style.AppTheme_WeeWXApp_Common");
		else
			LogMessage("MainActivity.onCreate() theme: " + WeeWXApp.theme_default);

		if(theme == R.style.AppTheme_WeeWXApp_Light_Common)
			LogMessage("MainActivity.onCreate() theme: R.style.AppTheme_WeeWXApp_Light_Common");
		else if(theme == R.style.AppTheme_WeeWXApp_Dark_Common)
			LogMessage("MainActivity.onCreate() theme: R.style.AppTheme_WeeWXApp_Dark_Common");
		else if(theme == R.style.AppTheme_WeeWXApp_Common)
			LogMessage("MainActivity.onCreate() theme: R.style.AppTheme_WeeWXApp_Common");
		else
			LogMessage("MainActivity.onCreate() theme: " + theme);

		screen_elements.add(new Setting("about_the_app", R.id.about_the_app));
		screen_elements.add(new Setting("aboutText", R.id.aboutText));
		screen_elements.add(new Setting("bgTextInputLayout", R.id.bgTextInputLayout));
		screen_elements.add(new Setting("customURL", R.id.customURL));
		screen_elements.add(new Setting("fgTextInputLayout", R.id.fgTextInputLayout));
		screen_elements.add(new Setting(FORCE_DARK_MODE, R.id.force_dark_mode));
		screen_elements.add(new Setting("metric_forecasts", R.id.metric_forecasts));
		screen_elements.add(new Setting("mtv1", R.id.mtv1));
		screen_elements.add(new Setting("mtv2", R.id.mtv2));
		screen_elements.add(new Setting("next_moon", R.id.next_moon));
		screen_elements.add(new Setting("rain_in_inches", R.id.rain_in_inches));
		screen_elements.add(new Setting(SAVE_APP_DEBUG_LOGS, R.id.save_app_debug_logs));
		screen_elements.add(new Setting("settings", R.id.settings));
		screen_elements.add(new Setting("showRadar", R.id.showRadar));
		screen_elements.add(new Setting("showForecast", R.id.showForecast));
		screen_elements.add(new Setting("show_indoor", R.id.show_indoor));
		screen_elements.add(new Setting("spinner1", R.id.spinner1));
		screen_elements.add(new Setting("spinner2", R.id.spinner2));
		screen_elements.add(new Setting("spinner3", R.id.spinner3));
		screen_elements.add(new Setting("spinner4", R.id.spinner4));
		screen_elements.add(new Setting("spinner5", R.id.spinner5));
		screen_elements.add(new Setting("til1", R.id.til1));
		screen_elements.add(new Setting("til2", R.id.til2));
		screen_elements.add(new Setting("til3", R.id.til3));
		screen_elements.add(new Setting("til4", R.id.til4));
		screen_elements.add(new Setting("til5", R.id.til5));
		screen_elements.add(new Setting("til6", R.id.til6));
		screen_elements.add(new Setting("til7", R.id.til7));
		screen_elements.add(new Setting("use_exact_alarm", R.id.use_exact_alarm));
		screen_elements.add(new Setting("widgetBG", R.id.widgetBG));
		screen_elements.add(new Setting("widgetFG", R.id.widgetFG));
		screen_elements.add(new Setting("wifi_only", R.id.wifi_only));

		screen_elements.add(new Setting("enable_mqtt", R.id.enable_mqtt));

		screen_elements.add(new Setting("morning_temp_alert", R.id.morning_temp_alert));
		screen_elements.add(new Setting("morning_temp_setting", R.id.morning_temp_setting));

		screen_elements.add(new Setting("tvMorningTempLabel", R.id.tvMorningTempLabel));
		screen_elements.add(new Setting("tvMorningTempValue", R.id.tvMorningTempValue));
		screen_elements.add(new Setting("sliderMorningTemp", R.id.sliderMorningTemp));
		screen_elements.add(new Setting("minMorningTemp", R.id.minMorningTemp));
		screen_elements.add(new Setting("maxMorningTemp", R.id.maxMorningTemp));

		screen_elements.add(new Setting("afternoon_temp_alert", R.id.afternoon_temp_alert));
		screen_elements.add(new Setting("afternoon_temp_setting", R.id.afternoon_temp_setting));

		screen_elements.add(new Setting("tvAfternoonTempLabel", R.id.tvAfternoonTempLabel));
		screen_elements.add(new Setting("tvAfternoonTempValue", R.id.tvAfternoonTempValue));
		screen_elements.add(new Setting("sliderAfternoonTemp", R.id.sliderAfternoonTemp));
		screen_elements.add(new Setting("minAfternoonTemp", R.id.minAfternoonTemp));
		screen_elements.add(new Setting("maxAfternoonTemp", R.id.maxAfternoonTemp));

		screen_elements.add(new Setting("rainfall_alert", R.id.rainfall_alert));
		screen_elements.add(new Setting("rainfall_setting", R.id.rainfall_setting));

		screen_elements.add(new Setting("tvRainfallLimitLabel", R.id.tvRainfallLimitLabel));
		screen_elements.add(new Setting("tvRainfallLimitValue", R.id.tvRainfallLimitValue));
		screen_elements.add(new Setting("sliderRainfallLimit", R.id.sliderRainfallLimit));
		screen_elements.add(new Setting("minRainfallLimit", R.id.minRainfallLimit));
		screen_elements.add(new Setting("maxRainfallLimit", R.id.maxRainfallLimit));

		screen_elements.add(new Setting(RAINRATE_ALERT_WATCH, R.id.rainrate_alert_watch));
		screen_elements.add(new Setting(RAINRATE_ALERT_WARNING, R.id.rainrate_alert_warning));
		screen_elements.add(new Setting(RAINRATE_ALERT_SEVERE, R.id.rainrate_alert_severe));

		LogMessage("MainActivity.onCreate() started...");

		WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

		LogMessage("MainActivity.onCreate() smallestScreenWidthDp: " + WeeWXApp.smallestScreenWidth());
		LogMessage("MainActivity.onCreate() minWidth=" + WeeWXApp.getWidth() +
								  ", minHeight=" + WeeWXApp.getHeight());

		extraPx = (int)(75 * WeeWXApp.getInstance().getResources().getDisplayMetrics().density + 0.5f);

		if(!WeeWXApp.isTablet())
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

		setContentView(R.layout.main_activity);

		mDrawerLayout = findViewById(R.id.drawer_layout);

		mDrawerLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.MyAppNavBarColour));

		mDrawerLayout.addDrawerListener(handleDrawerListener);

		hamburger = findViewById(R.id.hamburger);
		hamburger.setOnClickListener(arg0 ->
		{
			if(mDrawerLayout.isDrawerOpen(GravityCompat.START))
				mDrawerLayout.closeDrawer(GravityCompat.START);
			else
				mDrawerLayout.openDrawer(GravityCompat.START);
		});

		if(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
		{
			// Legacy back handling for Android < 13
			LogMessage("MainActivity.onCreate() setupBackHandling() setting getOnBackPressedDispatcher() SDK < TIRAMISU");
			getOnBackPressedDispatcher().addCallback(this, obpc);
		} else {
			// Android 13+ predictive back gestures
			// Only intercept the back if keyboard is visible or drawer is open
			LogMessage("MainActivity.onCreate() setupBackHandling() setting getOnBackInvokedDispatcher() SDK >= TIRAMISU");
			getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
		}

		myLinearLayout cd = findViewById(R.id.custom_drawer);
		cd.setBackgroundColor(WeeWXApp.getColours().bgColour);
		cd.setOnTouchedListener(() ->
		{
			//LogMessage("MainActivity.onCreate() cd.TouchedListener()");
			handleTouch();
		});

		AppBarLayout abl = findViewById(R.id.appbar);
		FrameLayout rv = findViewById(R.id.root_view);
		scrollView = findViewById(R.id.sv1);

		appInitialLeft = abl.getPaddingLeft();
		appInitialTop = abl.getPaddingTop();
		appInitialRight = abl.getPaddingRight();
		appInitialBottom = abl.getPaddingBottom();

		cdInitialLeft = cd.getPaddingLeft();
		cdInitialTop = cd.getPaddingTop();
		cdInitialRight = cd.getPaddingRight();
		cdInitialBottom = cd.getPaddingBottom();

		dlInitialLeft = mDrawerLayout.getPaddingLeft();
		dlInitialTop = mDrawerLayout.getPaddingTop();
		dlInitialRight = mDrawerLayout.getPaddingRight();
		dlInitialBottom = mDrawerLayout.getPaddingBottom();

		rvInitialLeft = rv.getPaddingLeft();
		rvInitialTop = rv.getPaddingTop();
		rvInitialRight = rv.getPaddingRight();
		rvInitialBottom = rv.getPaddingBottom();

		try {
			gestureNav = Settings.Secure.getInt(getContentResolver(),
				"navigation_mode", 0) == 2;
		} catch (Exception e) {
			LogMessage("MainActivity.onCreate() Error! e: " + e.getMessage(), true, KeyValue.e);
		}

		ViewCompat.setOnApplyWindowInsetsListener(abl, (v, insets) ->
		{
			Insets sb = insets.getInsets(WindowInsetsCompat.Type.statusBars());
			int top = appInitialTop + sb.top;
			v.setPadding(appInitialLeft, top, appInitialRight, appInitialBottom);
			return insets;
		});

		ViewCompat.setOnApplyWindowInsetsListener(cd, (v, insets) ->
		{
			Insets sb = insets.getInsets(WindowInsetsCompat.Type.statusBars());
			Insets nb = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
			Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
			int top = cdInitialTop + sb.top;
			int bottom = cdInitialBottom + Math.max(nb.bottom, ime.bottom);
			v.setPadding(cdInitialLeft, top, cdInitialRight, bottom);
			return insets;
		});

		ViewCompat.setOnApplyWindowInsetsListener(mDrawerLayout, (view, insets) ->
		{
			Insets sb = insets.getInsets(WindowInsetsCompat.Type.statusBars());
			Insets nb = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
			Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());

			int top = dlInitialTop + sb.top;
			int bottom = dlInitialBottom + Math.max(nb.bottom, ime.bottom);
			view.setBackgroundColor(WeeWXApp.getColour(R.color.MyAppNavBarColour));
			view.setPadding(dlInitialLeft, top, dlInitialRight, bottom);
			return insets;
		});

		ViewCompat.setOnApplyWindowInsetsListener(rv, (view, insets) ->
		{
			Insets nb = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
			Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
			int bottom = rvInitialBottom + Math.max(nb.bottom, ime.bottom);
			view.setBackgroundColor(WeeWXApp.getColour(R.color.MyAppNavBarColour));
			view.setPadding(rvInitialLeft, rvInitialTop, rvInitialRight, bottom);

			Insets gestureInsets = insets.getInsets(WindowInsetsCompat.Type.systemGestures());

			gestureNav = gestureInsets.left > 0 || gestureInsets.right > 0;

			updateHamburger();

			return insets;
		});

		TabLayout tabLayout = findViewById(R.id.tabs);

		mSectionsPagerAdapter = new SectionsStateAdapter(getSupportFragmentManager(), getLifecycle());
		mSectionsPagerAdapter.addFragment(new Weather());
		mSectionsPagerAdapter.addFragment(new Stats());
		mSectionsPagerAdapter.addFragment(new Forecast());
		mSectionsPagerAdapter.addFragment(new Webcam());
		mSectionsPagerAdapter.addFragment(new Custom());

		mViewPager = findViewById(R.id.container);
		mViewPager.setOffscreenPageLimit(mSectionsPagerAdapter.getItemCount());
		mViewPager.setAdapter(mSectionsPagerAdapter);
		reduceViewPagerSwipeSensitivity(mViewPager);

		String[] tabTitles;
		if(KeyValue.isPrefSet("radarforecast") &&
		   (boolean)KeyValue.readVar("radarforecast", WeeWXApp.radarforecast_default) == WeeWXApp.RadarOnHomeScreen)
			tabTitles = new String[]{getAndroidString(R.string.weather2),
									 getAndroidString(R.string.stats2),
									 getAndroidString(R.string.forecast2),
									 getAndroidString(R.string.webcam2),
									 getAndroidString(R.string.custom2)};
		else
			tabTitles = new String[]{getAndroidString(R.string.weather2),
									 getAndroidString(R.string.stats2),
									 getAndroidString(R.string.radar),
									 getAndroidString(R.string.webcam2),
									 getAndroidString(R.string.custom2)};

		new TabLayoutMediator(tabLayout, mViewPager,
				((tab, position) -> tab.setText(tabTitles[position]))).attach();

		if(savedInstanceState != null)
		{
			int page = savedInstanceState.getInt("page", 0);
			mViewPager.setCurrentItem(page, false);
		}

		TextInputLayout til1 = findViewById(R.id.til1);
		til1.setHint(getAndroidString(R.string.fileURL, "inigo-settings.txt"));

		if(!KeyValue.isPrefSet(json_keys[0] + "_url"))
			mDrawerLayout.openDrawer(GravityCompat.START);

		settingLayout = findViewById(R.id.settingLayout);
		aboutLayout = findViewById(R.id.aboutLayout);

		settingsURL = findViewById(R.id.settings);
		customURL = findViewById(R.id.customURL);

		metric_forecasts = findViewById(R.id.metric_forecasts);
		metric_forecasts.setOnCheckedChangeListener((buttonView, isChecked) ->
		{
			if(hasStarted)
			{
				LogMessage("RainfallLimit: " + RainfallLimit);

				if(isChecked)
				{
					MorningTemp = Math.round(F2C(MorningTemp / 10f) * 10);
					AfternoonTemp = Math.round(F2C(AfternoonTemp / 10f) * 10f);
					RainfallLimit = Math.round(in2mm(RainfallLimit));
				} else {
					MorningTemp = Math.round(C2F(MorningTemp / 10f) * 10);
					AfternoonTemp = Math.round(C2F(AfternoonTemp / 10f) * 10);
					RainfallLimit = Math.round(mm2in(RainfallLimit));
				}

				LogMessage("RainfallLimit: " + RainfallLimit);

				updateSliders(isChecked, MorningTemp, AfternoonTemp);
				updateSliders2(isChecked, RainfallLimit);
			}
		});

		rain_in_inches = findViewById(R.id.rain_in_inches);
		show_indoor = findViewById(R.id.show_indoor);

		use_exact_alarm = findViewById(R.id.use_exact_alarm);
		use_exact_alarm.setOnCheckedChangeListener((buttonView, isChecked) ->
		{
			if(isChecked)
				UpdateCheck.promptForExact(this);
		});

		save_app_debug_logs = findViewById(R.id.save_app_debug_logs);
		next_moon = findViewById(R.id.next_moon);
		force_dark_mode = findViewById(R.id.force_dark_mode);

		b1 = findViewById(R.id.saveButton);
		b2 = findViewById(R.id.deleteData);
		MaterialButton b3 = findViewById(R.id.aboutButton);
		MaterialButton b4 = findViewById(R.id.settingsButton);

		MaterialAutoCompleteTextView s1 = findViewById(R.id.spinner1);
		s1.setOnItemClickListener((parent, view, position, id) ->
		{
			UpdateFrequency = position;
			LogMessage("MainActivity.onCreate() New UpdateFrequency: " + UpdateFrequency);
		});

		MaterialAutoCompleteTextView s2 = findViewById(R.id.spinner2);
		s2.setOnItemClickListener((parent, view, position, id) ->
		{
			DayNightMode = position;
			LogMessage("MainActivity.onCreate() New DayNightMode: " + DayNightMode);
		});

		MaterialAutoCompleteTextView s3 = findViewById(R.id.spinner3);
		s3.setOnItemClickListener((parent, view, position, id) ->
		{
			widget_theme_mode = position;
			if(widget_theme_mode == 4)
			{
				fgtil.post(() -> fgtil.setVisibility(View.VISIBLE));
				bgtil.post(() -> bgtil.setVisibility(View.VISIBLE));
			} else {
				fgtil.post(() -> fgtil.setVisibility(View.GONE));
				bgtil.post(() -> bgtil.setVisibility(View.GONE));
			}

			LogMessage("MainActivity.onCreate() New widget_theme_mode: " + widget_theme_mode);
		});

		MaterialAutoCompleteTextView s4 = findViewById(R.id.spinner4);
		s4.setOnItemClickListener((parent, view, position, id) ->
		{
			UpdateInterval = position;
			LogMessage("MainActivity.onCreate() New UpdateInterval: " + UpdateInterval);
		});

		MaterialAutoCompleteTextView s5 = findViewById(R.id.spinner5);
		s5.setOnItemClickListener((parent, view, position, id) ->
		{
			webcamInterval = position;
			LogMessage("MainActivity.onCreate() New webcamInterval: " + webcamInterval);
		});

		widgetBG = findViewById(R.id.widgetBG);
		widgetFG = findViewById(R.id.widgetFG);

		wifi_only = findViewById(R.id.wifi_only);

		enable_mqtt = findViewById(R.id.enable_mqtt);

		showRadar = findViewById(R.id.showRadar);
		MaterialRadioButton showForecast = findViewById(R.id.showForecast);

		sliderMorningTemp = findViewById(R.id.sliderMorningTemp);
		TextView tvMorningTempValue = findViewById(R.id.tvMorningTempValue);
		sliderMorningTemp.addOnChangeListener((s, value, fromUser) ->
		{
			String tempUnit = "C";
			if(!metric_forecasts.isChecked())
				tempUnit = "F";

			MorningTemp = Math.round(value);

			String formatting = "%.1f°" + tempUnit;
			tvMorningTempValue.setText(String.format(Locale.ENGLISH, formatting, (value / 10f)));
		});

		sliderAfternoonTemp = findViewById(R.id.sliderAfternoonTemp);
		TextView tvAfternoonTempValue = findViewById(R.id.tvAfternoonTempValue);
		sliderAfternoonTemp.addOnChangeListener((s, value, fromUser) ->
		{
			String tempUnit = "C";
			if(!metric_forecasts.isChecked())
				tempUnit = "F";

			AfternoonTemp = Math.round(value);

			String formatting = "%.1f°" + tempUnit;
			tvAfternoonTempValue.setText(String.format(Locale.ENGLISH, formatting, (value / 10f)));
		});

		sliderRainfall = findViewById(R.id.sliderRainfallLimit);
		TextView tvRainfallValue = findViewById(R.id.tvRainfallLimitValue);

		sliderRainfall.addOnChangeListener((s, value, fromUser) ->
		{
			RainfallLimit = Math.round(value);

			if(metric_forecasts.isChecked() && !rain_in_inches.isChecked())
				tvRainfallValue.setText(String.format(Locale.ENGLISH, "%.1fmm", (value / 100)));
			else
				tvRainfallValue.setText(String.format(Locale.ENGLISH, "%.2fin", (value / 100)));
		});

		morning_temp_setting = findViewById(R.id.morning_temp_setting);
		morning_temp_alert = findViewById(R.id.morning_temp_alert);
		morning_temp_alert.setOnCheckedChangeListener((buttonView, isChecked) ->
		{
			if(isChecked)
			{
				requestNotificationPermission();
				morning_temp_setting.setVisibility(View.VISIBLE);
			} else {
				morning_temp_setting.setVisibility(View.GONE);
			}
		});

		afternoon_temp_setting = findViewById(R.id.afternoon_temp_setting);
		afternoon_temp_alert = findViewById(R.id.afternoon_temp_alert);
		afternoon_temp_alert.setOnCheckedChangeListener((buttonView, isChecked) ->
		{
			if(isChecked)
			{
				requestNotificationPermission();
				afternoon_temp_setting.setVisibility(View.VISIBLE);
			} else {
				afternoon_temp_setting.setVisibility(View.GONE);
			}
		});

		rainfall_setting = findViewById(R.id.rainfall_setting);
		rainfall_alert = findViewById(R.id.rainfall_alert);
		rainfall_alert.setOnCheckedChangeListener((buttonView, isChecked) ->
		{
			if(isChecked)
			{
				requestNotificationPermission();
				rainfall_setting.setVisibility(View.VISIBLE);
			} else {
				rainfall_setting.setVisibility(View.GONE);
			}
		});

		rainrate_alert_watch = findViewById(R.id.rainrate_alert_watch);
		rainrate_alert_watch.setOnCheckedChangeListener((buttonView, isChecked) ->
		{
			if(isChecked)
				requestNotificationPermission();
		});

		rainrate_alert_warning = findViewById(R.id.rainrate_alert_warning);
		rainrate_alert_warning.setOnCheckedChangeListener((buttonView, isChecked) ->
		{
			if(isChecked)
				requestNotificationPermission();
		});

		rainrate_alert_severe = findViewById(R.id.rainrate_alert_severe);
		rainrate_alert_severe.setOnCheckedChangeListener((buttonView, isChecked) ->
		{
			if(isChecked)
				requestNotificationPermission();
		});

		fgtil = findViewById(R.id.fgTextInputLayout);
		bgtil = findViewById(R.id.bgTextInputLayout);

		int fg, bg;
		boolean wo, met, rii, si, sr, uea, sadl, nm, fdm, mta, ata, rfa, rra_watch,
			rra_warning, rra_severe, em;
		int mt, at, rfl;

		boolean newDataUrlSet = KeyValue.isPrefSet(json_keys[0] + "_url");
		if(newDataUrlSet)
		{
			String dataURL = (String)KeyValue.readVar(json_keys[0] + "_url", "");
			if(!is_valid_url(dataURL))
				newDataUrlSet = false;
		}

		boolean lastDownloadSet = KeyValue.isPrefSet("LastDownload");
		if(lastDownloadSet)
		{
			String lastDownload = (String)KeyValue.readVar("LastDownload", "");
			if(is_blank(lastDownload))
				lastDownloadSet = false;
		}

		if(lastDownloadSet)
		{
			if(!newDataUrlSet)
			{
				LogMessage("MainActivity.onCreate() showUpdateAvailable2() triggered because no JSON URL detected.", KeyValue.e);
				showUpdateAvailable2();
			} else {
				KeyValue.putVar("LastDownload", null);
			}
		}

		UpdateFrequency = (int)KeyValue.readVar(WeeWXApp.UPDATE_FREQUENCY, WeeWXApp.UpdateFrequency_default);
		UpdateInterval = (int)KeyValue.readVar("UpdateInterval", WeeWXApp.UpdateInterval_default);
		webcamInterval = (int)KeyValue.readVar("webcamInterval", WeeWXApp.webcamInterval_default);
		DayNightMode = (int)KeyValue.readVar("DayNightMode", WeeWXApp.DayNightMode_default);
		widget_theme_mode =	(int)KeyValue.readVar(WIDGET_THEME_MODE, WeeWXApp.widget_theme_mode_default);

		LogMessage("MainActivity.onCreate() DayNightMode: " + DayNightMode);

		bg = (int)KeyValue.readVar("widgetBG", WeeWXApp.widgetBG_default);
		fg = (int)KeyValue.readVar("widgetFG", WeeWXApp.widgetFG_default);

		wo = (boolean)KeyValue.readVar("onlyWIFI", WeeWXApp.onlyWIFI_default);
		met = (boolean)KeyValue.readVar("metric", WeeWXApp.metric_default);
		rii = (boolean)KeyValue.readVar("rainInInches", WeeWXApp.rain_in_inches_default);
		si = (boolean)KeyValue.readVar("showIndoor", WeeWXApp.showIndoor_default);
		sr = (boolean)KeyValue.readVar("radarforecast", WeeWXApp.radarforecast_default);

		uea = (boolean)KeyValue.readVar("use_exact_alarm", WeeWXApp.use_exact_alarm_default);
		sadl = (boolean)KeyValue.readVar(SAVE_APP_DEBUG_LOGS, WeeWXApp.save_app_debug_logs_default);
		nm = (boolean)KeyValue.readVar("next_moon", WeeWXApp.next_moon_default);
		fdm = (boolean)KeyValue.readVar(FORCE_DARK_MODE, WeeWXApp.force_dark_mode_default);

		mta = (boolean)KeyValue.readVar("morning_temp_alert", WeeWXApp.morning_temp_alert_default);
		mt = (int)KeyValue.readVar("MorningTemp", WeeWXApp.MorningTemp_default);

		ata = (boolean)KeyValue.readVar("afternoon_temp_alert", WeeWXApp.afternoon_temp_alert_default);
		at = (int)KeyValue.readVar("AfternoonTemp", WeeWXApp.AfternoonTemp_default);

		rfa = (boolean)KeyValue.readVar("rainfall_alert", WeeWXApp.rainfall_alert_default);
		rfl = (int)KeyValue.readVar("RainfallLimit", WeeWXApp.RainfallLimit_default);

		rra_watch = (boolean)KeyValue.readVar(RAINRATE_ALERT_WATCH, WeeWXApp.rainrate_alert_watch_default);
		rra_warning = (boolean)KeyValue.readVar(RAINRATE_ALERT_WARNING, WeeWXApp.rainrate_alert_warning_default);
		rra_severe = (boolean)KeyValue.readVar(RAINRATE_ALERT_SEVERE, WeeWXApp.rainrate_alert_severe_default);

		em = (boolean)KeyValue.readVar(ENABLE_MQTT, enable_mqtt_default);

		if(savedInstanceState != null)
		{
			LogMessage("MainActivity.onCreate() Reading current settings that were saved in a bundle....");
			UpdateFrequency = savedInstanceState.getInt(WeeWXApp.UPDATE_FREQUENCY, UpdateFrequency);
			UpdateInterval = savedInstanceState.getInt("UpdateInterval", UpdateInterval);
			webcamInterval = savedInstanceState.getInt("webcamInterval", webcamInterval);
			DayNightMode = savedInstanceState.getInt("DayNightMode", DayNightMode);
			widget_theme_mode = savedInstanceState.getInt(WIDGET_THEME_MODE, widget_theme_mode);

			LogMessage("MainActivity.onCreate() UpdateFrequency: " + UpdateFrequency);
			LogMessage("MainActivity.onCreate() UpdateInterval: " + UpdateInterval);
			LogMessage("MainActivity.onCreate() webcamInterval: " + webcamInterval);
			LogMessage("MainActivity.onCreate() DayNightMode: " + DayNightMode);
			LogMessage("MainActivity.onCreate() widget_theme_mode: " + widget_theme_mode);

			bg = savedInstanceState.getInt("bg", bg);
			fg = savedInstanceState.getInt("fg", fg);

			wo = savedInstanceState.getBoolean("wo", wo);
			met = savedInstanceState.getBoolean("met", met);
			rii = savedInstanceState.getBoolean("rii", rii);
			si = savedInstanceState.getBoolean("si", si);
			sr = savedInstanceState.getBoolean("sr", sr);
			uea = savedInstanceState.getBoolean("uea", uea);
			sadl = savedInstanceState.getBoolean("sadl", sadl);
			nm = savedInstanceState.getBoolean("nm", nm);
			fdm = savedInstanceState.getBoolean("fdm", fdm);

			mta = savedInstanceState.getBoolean("mta", mta);
			mt = savedInstanceState.getInt("mt", mt);

			ata = savedInstanceState.getBoolean("ata", ata);
			at = savedInstanceState.getInt("at", at);

			rfa = savedInstanceState.getBoolean("rfa", rfa);
			rfl = savedInstanceState.getInt("rfl", rfl);

			rra_watch = savedInstanceState.getBoolean("rra_watch", rra_watch);
			rra_warning = savedInstanceState.getBoolean("rra_warning", rra_warning);
			rra_severe = savedInstanceState.getBoolean("rra_severe", rra_severe);

			em = savedInstanceState.getBoolean(ENABLE_MQTT, em);

			LogMessage("MainActivity.onCreate() DayNightMode: " + DayNightMode);
		}

		if(UpdateFrequency < 0 || UpdateFrequency >= WeeWXApp.updateOptions.length)
			UpdateFrequency = WeeWXApp.UpdateFrequency_default;

		if(UpdateInterval < 0 || UpdateInterval >= WeeWXApp.updateInterval.length)
			UpdateInterval = WeeWXApp.UpdateInterval_default;

		if(webcamInterval < 0 || webcamInterval >= WeeWXApp.webcamRefreshOptions.length)
			webcamInterval = WeeWXApp.webcamInterval_default;

		// https://github.com/Pes8/android-material-color-picker-dialog
		String hex = CPEditText.getFixedChar() + String.format("%08X", bg).toUpperCase();
		LogMessage("MainActivity.onCreate() Setting widgetBG to " + to_ARGB_hex(hex));
		final String bghex = hex;
		widgetBG.setText(bghex);
		widgetBG.setOnTouchListener((v, event) ->
		{
			v.performClick();

			if(event.getAction() == MotionEvent.ACTION_UP)
				handleTouch();

			return false;
		});

		hex = CPEditText.getFixedChar() + String.format("%08X", fg).toUpperCase();
		LogMessage("MainActivity.onCreate() Setting widgetFG to " + to_ARGB_hex(hex));
		final String fghex = hex;
		widgetFG.setText(fghex);
		widgetFG.setOnTouchListener((v, event) ->
		{
			v.performClick();

			if(event.getAction() == MotionEvent.ACTION_UP)
				handleTouch();

			return false;
		});

		wifi_only.setChecked(wo);
		metric_forecasts.setChecked(met);
		rain_in_inches.setChecked(rii);
		show_indoor.setChecked(si);
		use_exact_alarm.setChecked(uea);
		save_app_debug_logs.setChecked(sadl);
		next_moon.setChecked(nm);
		force_dark_mode.setChecked(fdm);

		morning_temp_alert.setChecked(mta);
		MorningTemp = mt;

		afternoon_temp_alert.setChecked(ata);
		AfternoonTemp = at;

		updateSliders(met, mt, at);

		rainfall_alert.setChecked(rfa);
		RainfallLimit = rfl;

		rainrate_alert_watch.setChecked(rra_watch);
		rainrate_alert_warning.setChecked(rra_warning);
		rainrate_alert_severe.setChecked(rra_severe);

		enable_mqtt.setChecked(em);

		updateSliders2(met, rfl);

		LogMessage("MainActivity.onCreate() DayNightMode: " + DayNightMode);

		showRadar.setChecked(sr);
		showForecast.setChecked(!sr);

		settingLayout.setVisibility(View.VISIBLE);
		aboutLayout.setVisibility(View.GONE);

		b1.setOnClickListener(arg0 ->
		{
			LogMessage("MainActivity.onCreate() Starting save settings!");
			String candidate = settingsURL.getText() == null ? "" : settingsURL.getText().toString().strip();
			if(isLocalNetworkUrl(candidate) && !hasLocalNetworkPermission())
			{
				pendingLocalSettingsSave = true;
				requestLocalNetworkPermission(true);
				return;
			}
			b1.setEnabled(false);
			b2.setEnabled(false);
			closeKeyboard();

			int layout = R.layout.layout_loading_dialog_dark;
			if(KeyValue.theme == R.style.AppTheme_WeeWXApp_Light_Common)
				layout = R.layout.layout_loading_dialog_light;

			LogMessage("MainActivity.onCreate() show dialog");
			dialog = new AlertDialog.Builder(this)
					.setCancelable(false)
					.setView(layout)
					.create();

			dialog.show();

			LogMessage("MainActivity.onCreate() Process settings!");
			processSettings();
		});

		b2.setOnClickListener(arg0 -> checkReally());

		b3.setOnClickListener(arg0 ->
		{
			loadAboutText();
			settingLayout.setVisibility(View.GONE);
			aboutLayout.setVisibility(View.VISIBLE);
			scrollView.smoothScrollTo(0, 0);
		});

		b4.setOnClickListener(arg0 ->
		{
			aboutLayout.setVisibility(View.GONE);
			settingLayout.setVisibility(View.VISIBLE);
			scrollView.smoothScrollTo(0, 0);
		});

		settingsURL.setText((String)KeyValue.readVar("SETTINGS_URL", WeeWXApp.SETTINGS_URL_default));
		settingsURL.setOnFocusChangeListener((v, hasFocus) ->
		{
			if(!hasFocus)
				closeKeyboard(v);
		});

		customURL.setText((String)KeyValue.readVar("custom_url", ""));
		customURL.setOnFocusChangeListener((v, hasFocus) ->
		{
			LogMessage("MainActivity.onCreate() CustomURL has a focus change event...");

			if(!hasFocus)
			{
				LogMessage("MainActivity.onCreate() CustomURL lost focus change event...");
				closeKeyboard(v);
				return;
			}

			LogMessage("MainActivity.onCreate() CustomURL gained focus change event...");

			final View root = getWindow().getDecorView().getRootView();
			final ViewTreeObserver.OnGlobalLayoutListener listener = new ViewTreeObserver.OnGlobalLayoutListener()
			{
				@Override
				public void onGlobalLayout()
				{
					// Remove listener immediately — only want to run once
					root.getViewTreeObserver().removeOnGlobalLayoutListener(this);

					// Now the layout has resized, scroll to the EditText
					scrollView.postDelayed(() ->
					{
						LogMessage("MainActivity.onCreate() Old scrollY: " + scrollView.getScrollY());
						int scrollY = scrollView.getScrollY() + extraPx;
						LogMessage("MainActivity.onCreate() New scrollY: " + scrollY);
						scrollView.smoothScrollTo(0, Math.max(scrollY, 0));
					}, 200);
				}
			};

			root.getViewTreeObserver().addOnGlobalLayoutListener(listener);
		});

		ArrayAdapter<String> adapter1 = newArrayAdapter(R.layout.spinner_layout, WeeWXApp.updateOptions);
		ArrayAdapter<String> adapter2 = newArrayAdapter(R.layout.spinner_layout, WeeWXApp.themeOptions);
		ArrayAdapter<String> adapter3 = newArrayAdapter(R.layout.spinner_layout, WeeWXApp.widgetThemeOptions);
		ArrayAdapter<String> adapter4 = newArrayAdapter(R.layout.spinner_layout, WeeWXApp.updateInterval);
		ArrayAdapter<String> adapter5 = newArrayAdapter(R.layout.spinner_layout, WeeWXApp.webcamRefreshOptions);

		s1.setAdapter(adapter1);
		s1.setText(WeeWXApp.updateOptions[UpdateFrequency], false);

		s2.setAdapter(adapter2);
		s2.setText(WeeWXApp.themeOptions[DayNightMode], false);

		if(widget_theme_mode == 4)
		{
			fgtil.setVisibility(View.VISIBLE);
			bgtil.setVisibility(View.VISIBLE);
		} else {
			fgtil.setVisibility(View.GONE);
			bgtil.setVisibility(View.GONE);
		}

		s3.setAdapter(adapter3);
		s3.setText(WeeWXApp.widgetThemeOptions[widget_theme_mode], false);

		s4.setAdapter(adapter4);
		s4.setText(WeeWXApp.updateInterval[UpdateInterval], false);

		s5.setAdapter(adapter5);
		s5.setText(WeeWXApp.webcamRefreshOptions[webcamInterval], false);

		EdgeToEdge.enable(this);

		setStrings();
		updateHamburger();
		updateColours();
		updateAppWidget();

		if(!(boolean)KeyValue.readVar("metric", WeeWXApp.metric_default) ||
			(boolean)KeyValue.readVar("rainInInches", WeeWXApp.rain_in_inches_default))
			rainFormat = "%.2f";

		LogMessage("MainActivity.onCreate() loading observeNotifications()..");
		observeNotifications(this, notificationObserver);

		if(!screen_elements.isEmpty())
		{
			Setting s = screen_elements.get(0);
			WeeWXAppCommon.LogColour(findViewById(s.ResId()), s.name());
		}

		LogMessage("MainActivity.onCreate() has finished...", KeyValue.d);
		hasStarted = true;
		// Ask only when a configured endpoint uses the LAN. Public HTTPS needs no permission.
		if(hasLocalNetworkEndpoint())
			requestLocalNetworkPermission(false);

		if((boolean)KeyValue.readVar(ENABLE_MQTT, enable_mqtt_default))
			mqttEnabled = true;
	}

	private void updateSliders(boolean met, float morning, float afternoon)
	{
		LogMessage("met: " + met);
		LogMessage("morning: " + morning);
		LogMessage("afternoon: " + afternoon);

		MaterialTextView minMorningTemp = findViewById(R.id.minMorningTemp);
		MaterialTextView maxMorningTemp = findViewById(R.id.maxMorningTemp);
		MaterialTextView minAfternoonTemp = findViewById(R.id.minAfternoonTemp);
		MaterialTextView maxAfternoonTemp = findViewById(R.id.maxAfternoonTemp);

		int minTemp = 160;
		int maxTemp = 300;
		String tempUnit = "°C";

		if(!met)
		{
			minTemp = Math.round(C2F(minTemp / 10f) * 10);
			maxTemp = Math.round(C2F(maxTemp / 10f) * 10);
			tempUnit = "°F";
		}

		sliderMorningTemp.setValueFrom(-999);
		sliderMorningTemp.setValueTo(999);

		morning = Math.round(morning);

		if(morning < minTemp || morning > maxTemp)
			morning = WeeWXApp.MorningTemp_default;

		sliderMorningTemp.setValue(morning);
		sliderMorningTemp.setValueFrom(minTemp);
		sliderMorningTemp.setValueTo(maxTemp);

		sliderAfternoonTemp.setValueFrom(-999);
		sliderAfternoonTemp.setValueTo(999);

		afternoon = Math.round(afternoon);

		if(afternoon < minTemp || afternoon > maxTemp)
			afternoon = WeeWXApp.AfternoonTemp_default;

		sliderAfternoonTemp.setValue(afternoon);
		sliderAfternoonTemp.setValueFrom(minTemp);
		sliderAfternoonTemp.setValueTo(maxTemp);

		String str = (minTemp / 10) + tempUnit;
		minMorningTemp.setText(str);
		minAfternoonTemp.setText(str);

		str = (maxTemp / 10) + tempUnit;
		maxMorningTemp.setText(str);
		maxAfternoonTemp.setText(str);
	}

	private void updateSliders2(boolean met, float rainfall)
	{
		LogMessage("met: " + met);
		LogMessage("rainfall: " + rainfall);

		MaterialTextView minRainfallLimit = findViewById(R.id.minRainfallLimit);
		MaterialTextView maxRainfallLimit = findViewById(R.id.maxRainfallLimit);

		int step = 20;
		int lowerlimit = step;
		int upperLimit = 25000;

		if(!met)
		{
			step = 1;
			lowerlimit = step;
			upperLimit = 10000;
		}

		sliderRainfall.setStepSize(step);
		sliderRainfall.setValueTo(99999);

		rainfall = Math.round(rainfall);

		if(met)
			rainfall = Math.round(rainfall / step) * step;

		if(rainfall < lowerlimit || rainfall > upperLimit)
			rainfall = WeeWXApp.RainfallLimit_default;

		sliderRainfall.setValue(rainfall);
		sliderRainfall.setValueFrom(lowerlimit);
		sliderRainfall.setValueTo(upperLimit);

		String str = String.format(Locale.ENGLISH, "%.1fmm", (lowerlimit / 100f));
		if(!met)
			str = String.format(Locale.ENGLISH, "%.2fin", (lowerlimit / 100f));

		minRainfallLimit.setText(str);

		str = String.format(Locale.ENGLISH, "%.1fmm", (upperLimit / 100f));
		if(!met)
			str = String.format(Locale.ENGLISH, "%.2fin", (upperLimit / 100f));

		maxRainfallLimit.setText(str);
	}

	private void reduceViewPagerSwipeSensitivity(ViewPager2 viewPager)
	{
		try
		{
			Field recyclerViewField = ViewPager2.class.getDeclaredField("mRecyclerView");
			recyclerViewField.setAccessible(true);
			RecyclerView recyclerView = (RecyclerView) recyclerViewField.get(viewPager);

			Field touchSlopField = RecyclerView.class.getDeclaredField("mTouchSlop");
			touchSlopField.setAccessible(true);
			int touchSlop = (int) touchSlopField.get(recyclerView);
			touchSlopField.set(recyclerView, touchSlop * 3);
		} catch(Exception e) {
			doStackOutput(e);
		}
	}

	@Override
	public void onDestroy()
	{
		LogMessage("MainActivity.onDestroy()");
		super.onDestroy();

		removeNotificationObserver(notificationObserver);

		UpdateCheck.cancelAlarm();

		UpdateCheck.setNextAlarm();
	}

	private void loadAboutText()
	{
		ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
		ActivityManager am = (ActivityManager)getSystemService(ACTIVITY_SERVICE);
		am.getMemoryInfo(memInfo);

		// App's own memory usage
		Runtime runtime = Runtime.getRuntime();
		long usedMemory = runtime.totalMemory() - runtime.freeMemory();
		long maxMemory = runtime.maxMemory();

		float USEDMEMORY = usedMemory / 1_048_576f;
		float MAXMEMORY = maxMemory / 1_048_576f;

		MaterialTextView tv1 = findViewById(R.id.aboutText);
		String about_blurb = WeeWXApp.current_about_blurb
				.replace("USEDMEMORY", String.format(Locale.ENGLISH, "%.1f MB", USEDMEMORY))
				.replace("MAXMEMORY", String.format(Locale.ENGLISH, "%.1f MB", MAXMEMORY));

		tv1.setText(HtmlCompat.fromHtml(about_blurb, HtmlCompat.FROM_HTML_MODE_COMPACT));
		tv1.setMovementMethod(LinkMovementMethod.getInstance());
	}

	@Override
	protected void onResume()
	{
		super.onResume();

		Window window = getWindow();

		WindowInsetsControllerCompat controller =
		    new WindowInsetsControllerCompat(window, window.getDecorView());

		controller.setAppearanceLightStatusBars(false);
		controller.setAppearanceLightNavigationBars(false);

		// Show the status bar...
		controller.show(WindowInsetsCompat.Type.systemBars());

		if(KeyValue.isVisible)
			return;

		KeyValue.isVisible = true;

		if(!UpdateCheck.canSetExact(this) && use_exact_alarm.isChecked())
			use_exact_alarm.setChecked(false);

		if(ActivityCompat.checkSelfPermission(instance, Manifest.permission.POST_NOTIFICATIONS)
				   != PackageManager.PERMISSION_GRANTED && !KeyValue.hasNotificationPerm)
		{
			if(morning_temp_alert.isChecked())
				morning_temp_alert.setChecked(false);

			if(afternoon_temp_alert.isChecked())
				afternoon_temp_alert.setChecked(false);

			if(rainfall_alert.isChecked())
				rainfall_alert.setChecked(false);

			if(rainrate_alert_watch.isChecked())
				rainrate_alert_watch.setChecked(false);

			if(rainrate_alert_warning.isChecked())
				rainrate_alert_warning.setChecked(false);

			if(rainrate_alert_severe.isChecked())
				rainrate_alert_severe.setChecked(false);
		}

		rainFormat = "%.1f";
		if(!(boolean)KeyValue.readVar("metric", WeeWXApp.metric_default) ||
			(boolean)KeyValue.readVar("rainInInches", WeeWXApp.rain_in_inches_default))
			rainFormat = "%.2f";

		if((boolean)KeyValue.readVar(ENABLE_MQTT, enable_mqtt_default))
		{
			String mqttURL = (String)KeyValue.readVar(MQTT_URL, "");
			if(is_valid_url(mqttURL))
			{
				LogMessage("MQTT is enabled");
				HttpUrl MQTTURL = HttpUrl.parse(mqttURL
					.replace("ws://", "http://")
					.replace("wss://", "https://")
					.replace("mqtt://", "http://")
					.replace("mqtts://", "https://"));

				LogMessage("MQTTURL: " + MQTTURL);

				Mqtt5ClientBuilder mqttClientBuilder = MqttClient.builder()
					.useMqttVersion5()
					.identifier("WeeWXApp-" + WeeWXApp.VERSION_NAME + "-" + UUID.randomUUID().toString())
					.serverHost(MQTTURL.host())
					.automaticReconnectWithDefaultConfig();

				LogMessage("Setting MQTT host to " + MQTTURL.host());

				if(MQTTURL.port() > 0)
				{
					LogMessage("Setting port to: " + MQTTURL.port());
					mqttClientBuilder = mqttClientBuilder.serverPort(MQTTURL.port());
				}

				if(mqttURL.startsWith("ws"))
				{
					LogMessage("Enabling websocket connection");
					mqttClientBuilder = mqttClientBuilder.webSocketConfig()
											.serverPath(MQTTURL.encodedPath())
											.applyWebSocketConfig();
				}
				if(!MQTTURL.username().isBlank() && !MQTTURL.password().isBlank())
				{
					LogMessage("Applying username and password");
					mqttClientBuilder = mqttClientBuilder.simpleAuth()
					.username(MQTTURL.username().strip())
					.password(MQTTURL.password().strip().getBytes(StandardCharsets.UTF_8))
					.applySimpleAuth();
				}

				if(MQTTURL.isHttps())
				{
					LogMessage("Enabling encryption");
					mqttClientBuilder = mqttClientBuilder.sslWithDefaultConfig();
				}

				mqttClient = mqttClientBuilder.buildAsync();

				LogMessage("mqttClientBuilder.buildAsync()");

				mqttClient.connectWith()
					.send()
					.whenComplete((connAck, throwable) ->
				{
					if(throwable != null)
			            LogMessage("Connection failed: " + throwable.getMessage(), KeyValue.e);
			        else
						LogMessage("Connected");
			    });

				String mqttTopic = (String)KeyValue.readVar(MQTT_TOPIC, "");
				LogMessage("Subscribing to " + mqttTopic);

				String[] topics = {mqttTopic};

				if(mqttTopic.contains(","))
					topics = mqttTopic.split(",");

				for(String topicName : topics)
				{
					String cleanTopic = topicName.strip();
					if(cleanTopic.isBlank())
						continue;

					LogMessage("MQTT cleanTopic: " + cleanTopic);

					try
					{
					    mqttClient.subscribeWith()
					        .topicFilter(cleanTopic)
					        .qos(MqttQos.AT_LEAST_ONCE)
					        .callback(publish ->
					        {
					            String payload = new String(publish.getPayloadAsBytes());
					            String topic = publish.getTopic().toString();
					            if(topic.isBlank())
					                return;

					            try
					            {
					                processPacket(topic, payload);
					            } catch(Exception e) {
					                doStackOutput(e);
					            }
					        })
					        .send()
					        .whenComplete((subAck, throwable) ->
					        {
					            if(throwable != null)
					            {
					                LogMessage("MQTT Subscribe to `" + cleanTopic + "` failed: " + throwable.getMessage());
					                mqttConnected = false;
					            } else {
					                LogMessage("MQTT Successfully subscribed to `" + cleanTopic + "`");
					                mqttConnected = true;
					            }
					        });
					}
					catch(Exception e)
					{
					    LogMessage("MQTT Failed to build subscribe request for `" + cleanTopic + "`: " + e.getMessage());
					}
				}
			}
		}

		UpdateCheck.cancelAlarm();

		UpdateCheck.setNextAlarm();

		UpdateCheck.runInTheBackground();
	}

	@Override
	protected void onPause()
	{
		KeyValue.isVisible = false;

		if(mqttClient != null && mqttConnected)
		{
			try
			{
				mqttClient.disconnect()
					.whenComplete((ignored, throwable) ->
					{
						mqttConnected = false;
				        if(throwable != null)
				            LogMessage("Disconnect failed", throwable);
				    });
			} catch (Exception ignore) {}

			mqttConnected = false;
			mqttClient = null;
		}

		super.onPause();
	}

	private void updateColours()
	{
		int fgColour = WeeWXApp.getColours().fgColour;

		LogMessage("MainActivity.onCreate() fgColour: #" + Integer.toHexString(fgColour));

		for(Setting s : screen_elements)
		{
			View view = findViewById(s.ResId());
			switch(view)
			{
				case TextInputLayout v ->
				{
					v.setBoxStrokeColorStateList(strokeColors);
					v.setDefaultHintTextColor(strokeColors);
					v.setPlaceholderTextColor(strokeColors);
					v.setHintTextColor(strokeColors);
					v.setHelperTextColor(strokeColors);
				}
				case TextView v ->
				{
					v.setTextColor(fgColour);
					v.setHintTextColor(fgColour);
				}
				case Slider ignored -> LogMessage("MainActivity.updateColours(): Slider detected");
				case LinearLayout ignored -> LogMessage("MainActivity.updateColours(): LinearLayout detected");
				default -> LogMessage("MainActivity.updateColours() Uncaught view type: " + view, KeyValue.w);
			}
		}
	}

	private void updateHamburger()
	{
		if(gestureNav)
		{
			LogMessage("MainActivity.updateHamburger() gestureNav == true, show the hamburger menu...");
			if(hamburger.getVisibility() != View.VISIBLE)
				hamburger.setVisibility(View.VISIBLE);
		} else {
			LogMessage("MainActivity.updateHamburger() gestureNav == false, hide the hamburger menu...");
			if(hamburger.getVisibility() != View.GONE)
				hamburger.setVisibility(View.GONE);
		}
	}

	final DrawerLayout.SimpleDrawerListener handleDrawerListener = new DrawerLayout.SimpleDrawerListener()
	{
		@Override
		public void onDrawerOpened(@NonNull View drawerView)
		{
			//LogMessage("MainActivity.handleDrawerListener() Detected a back press in the DrawerLayout...");

			InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
			View focus = getCurrentFocus();
			if(imm != null && focus != null && imm.isAcceptingText())
				closeKeyboard(drawerView, imm);
		}
	};

	final OnBackPressedCallback obpc = new OnBackPressedCallback(true)
	{
		@Override
		public void handleOnBackPressed()
		{
			//LogMessage("MainActivity.handleOnBackPressed()");
			handleBack();
		}
	};

	void handleBack()
	{
		//LogMessage("MainActivity.handleBack() Detected an application back press...");
		View focus = getCurrentFocus();
		InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
		if(focus != null && imm != null && imm.isAcceptingText())
		{
			//LogMessage("MainActivity.handleBack() Let's hide the on screen keyboard and clearFocus()...");
			closeKeyboard(focus, imm);
			return;
		}

		if(mDrawerLayout.isDrawerOpen(GravityCompat.START))
		{
			//LogMessage("MainActivity.handleBack() Let's shut the drawer...");
			closeDrawer();
			return;
		}

		if(mViewPager.getCurrentItem() > 0)
		{
			LogMessage("MainActivity.handleBack() Cycle through tabs until we hit tab 0");
			mViewPager.post(() -> mViewPager.setCurrentItem(mViewPager.getCurrentItem() - 1));
			return;
		}

		if(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
		{
			LogMessage("MainActivity.handleBack() Let's end now... SDK < TIRAMISU");
			obpc.setEnabled(false);
			finish();
		} else {
			LogMessage("MainActivity.handleBack() SDK >= TIRAMISU... Let the system do it's thing...");
			getOnBackPressedDispatcher().onBackPressed();
		}
	}

	void handleTouch()
	{
		if(settingsURL != null && settingsURL.isFocused())
		{
			closeKeyboard();
			if(settingsURL.isFocused())
				settingsURL.clearFocus();
		}

		if(customURL != null && customURL.isFocused())
		{
			closeKeyboard();
			if(customURL.isFocused())
				customURL.clearFocus();
		}
	}

	@Override
	protected void onSaveInstanceState(@NonNull Bundle outState)
	{
		super.onSaveInstanceState(outState);

		LogMessage("MainActivity.onSaveInstanceState() Stashing current settings into a bundle....");
		outState.putInt("page", mViewPager.getCurrentItem());
		outState.putInt(WeeWXApp.UPDATE_FREQUENCY, UpdateFrequency);
		outState.putInt("UpdateInterval", UpdateInterval);
		outState.putInt("webcamInterval", webcamInterval);
		outState.putInt("DayNightMode", DayNightMode);
		outState.putInt(WIDGET_THEME_MODE, widget_theme_mode);

		LogMessage("MainActivity.onSaveInstanceState() UpdateFrequency: " + UpdateFrequency);
		LogMessage("MainActivity.onSaveInstanceState() UpdateInterval: " + UpdateInterval);
		LogMessage("MainActivity.onSaveInstanceState() webcamInterval: " + webcamInterval);
		LogMessage("MainActivity.onSaveInstanceState() DayNightMode: " + DayNightMode);
		LogMessage("MainActivity.onSaveInstanceState() widget_theme_mode: " + widget_theme_mode);

		Editable edit = widgetBG.getText();
		if(edit != null && edit.length() > 0)
		{
			int bg = parseHexToColour(edit.toString());
			outState.putInt("bg", bg);
		} else {
			outState.putInt("bg", WeeWXApp.widgetBG_default);
		}

		edit = widgetFG.getText();
		if(edit != null && edit.length() > 0)
		{
			int fg = parseHexToColour(edit.toString());
			outState.putInt("fg", fg);
		} else {
			outState.putInt("fg", WeeWXApp.widgetFG_default);
		}

		outState.putBoolean("wo", wifi_only.isChecked());
		outState.putBoolean("met", metric_forecasts.isChecked());
		outState.putBoolean("rii", rain_in_inches.isChecked());
		outState.putBoolean("si", show_indoor.isChecked());
		outState.putBoolean("sr", showRadar.isChecked());
		outState.putBoolean("uea", use_exact_alarm.isChecked());
		outState.putBoolean("sadl", save_app_debug_logs.isChecked());
		outState.putBoolean("nm", next_moon.isChecked());
		outState.putBoolean("fdm", force_dark_mode.isChecked());

		outState.putBoolean("mta", morning_temp_alert.isChecked());
		outState.putInt("mt", (int)sliderMorningTemp.getValue());

		outState.putBoolean("ata", afternoon_temp_alert.isChecked());
		outState.putInt("at", (int)sliderAfternoonTemp.getValue());

		outState.putBoolean("rfa", rainfall_alert.isChecked());
		outState.putInt("rfl", RainfallLimit);

		outState.putBoolean("rra_watch", rainrate_alert_watch.isChecked());
		outState.putBoolean("rra_warning", rainrate_alert_warning.isChecked());
		outState.putBoolean("rra_severe", rainrate_alert_severe.isChecked());

		outState.putBoolean(ENABLE_MQTT, enable_mqtt.isChecked());
	}

	private void setStrings()
	{
		int fgColour = WeeWXApp.getColours().fgColour;

		int disabled = WeeWXApp.getColours().LightGray;
		if(theme == R.style.AppTheme_WeeWXApp_Dark_Common)
			disabled = WeeWXApp.getColours().DarkGray;

		strokeColors = new ColorStateList(new int[][]{new int[]{android.R.attr.state_enabled},	  // default enabled
													  new int[]{-android.R.attr.state_enabled},	 // disabled
													  new int[]{android.R.attr.state_focused},	  // focused
													  new int[]{-android.R.attr.state_focused},	 // not focused
		}, new int[]{fgColour,  // default
					 disabled,  // disabled
					 fgColour,  // focused
					 fgColour   // unfocused
		});
	}

	private ArrayAdapter<String> newArrayAdapter(int resId, String[] strings)
	{
		return new ArrayAdapter<>(this, resId, strings)
		{
			@NonNull
			@Override
			public View getView(int position, View convertView, @NonNull ViewGroup parent)
			{
				MaterialTextView view = (MaterialTextView)super.getView(position, convertView, parent);
				view.setBackgroundColor(WeeWXApp.getColours().bgColour);
				view.setTextColor(WeeWXApp.getColours().fgColour);
				return view;
			}

			@Override
			public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent)
			{
				MaterialTextView view = (MaterialTextView)super.getDropDownView(position, convertView, parent);
				view.setBackgroundColor(WeeWXApp.getColours().bgColour);
				view.setTextColor(WeeWXApp.getColours().fgColour);
				return view;
			}
		};
	}

	void closeKeyboard()
	{
		View focus = getCurrentFocus();
		closeKeyboard(focus);
	}

	void closeKeyboard(View view)
	{
		InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
		if(view != null && imm != null && imm.isAcceptingText())
			closeKeyboard(view, imm);
	}

	void closeKeyboard(View focus, InputMethodManager imm)
	{
		if(focus != null && imm != null && imm.isAcceptingText())
		{
			LogMessage("MainActivity.closeKeyboard() Let's hide the on screen keyboard...");
			imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
			focus.clearFocus();
		}
	}

	void closeDrawer()
	{
		if(mDrawerLayout.isDrawerOpen(GravityCompat.START))
		{
			LogMessage("MainActivity.closeDrawer() Let's shut the drawer...");
			mDrawerLayout.closeDrawer(GravityCompat.START);
		}
	}

	private void showProcessingErrors(int json_id, String json_url)
	{
		int errorCount = (int)KeyValue.readVar("ProcessingErrorCount", -1);
		if(errorCount < 1)
			return;

		KeyValue.putVar("ProcessingErrorCount", null);
		KeyValue.putVar("ProcessingErrorID", null);

		String errorStr = getPlural(R.plurals.processing_errors, errorCount, errorCount, json_labels[json_id], json_url, getEnglishAndroidString(R.string.app_name));
		String logStr = getEnglishPlural(R.plurals.processing_errors, errorCount, errorCount, json_labels[json_id], json_url, getEnglishAndroidString(R.string.app_name));

		showAlertDialog(errorStr, logStr);
	}

	private void showUpdateErrors()
	{
		LogMessage("MainActivity.showUpdateErrors()...");

		JSONObject jsonObject = getJSONerrors();
		if(jsonObject.length() < 1)
		{
			LogMessage("MainActivity.showUpdateErrors() jsonArray.length() < 1: " + jsonObject.length());
			return;
		}

		try
		{
			String lastError = "";
			long now = System.currentTimeMillis();
			int errorCount = 0;
			double lastErrorTime = 0;
			for(Iterator<String> it = jsonObject.keys(); it.hasNext(); )
			{
				String key = it.next();
				long when = Long.parseLong(key);
				lastErrorTime = (now - when) / 6_000D;
				lastErrorTime /= 10;
				if(lastErrorTime > 30)
				{
					LogMessage("MainActivity.showUpdateErrors() lastError > 30: " + lastError + " minutes ago...");
					continue;
				}

				lastError = jsonObject.optString(key);
				errorCount++;
			}

			if(errorCount > 0 && lastErrorTime <= 30)
			{
				if(errorCount > 1)
				{
					String errorStr = getPlural(R.plurals.processing_errors2, errorCount,
						errorCount, getEnglishAndroidString(R.string.app_name), (int)Math.round(lastErrorTime));
					LogMessage("MainActivity.showUpdateErrors() errorStr: " + errorStr);
					showAlertDialog(errorStr);
				} else {
					LogMessage("MainActivity.showUpdateErrors() errorStr: " + lastError);
					showAlertDialog(lastError);
				}
			}
		} catch(Exception e) {
			LogMessage("MainActivity.showUpdateErrors() e: " + e.getMessage());
			doStackOutput(e);
		}

		jsonObject = new JSONObject();
		saveJSONerrors(jsonObject);
	}

	private void showMergeError()
	{
		String errorStr = getAndroidString(R.string.failed_to_merge_weather_data, json_labels[0], json_labels[2]);
		String logStr = getEnglishAndroidString(R.string.failed_to_merge_weather_data, json_labels[0], json_labels[2]);

		showAlertDialog(errorStr, logStr);
	}
	private void showUpdateAvailable()
	{
		int updateVer = WeeWXApp.minimum_inigo_version;

		if((boolean)KeyValue.readVar("shownUpdate_" + updateVer, false))
			return;

		KeyValue.putVar("shownUpdate_" + updateVer, true);

		String errorStr = getAndroidString(R.string.weewx_extensions_need_updating, "WeeWX");
		String logStr = getEnglishAndroidString(R.string.weewx_extensions_need_updating, "WeeWX");
		showAlertDialog(errorStr, logStr);
	}

	private void showAlertDialog(String errorStr, String logStr)
	{
		LogMessage("showAlertDialog(), logStr: " + logStr);
		showAlertDialog(errorStr);
	}

	private void showAlertDialog(String errorStr)
	{
		new AlertDialog.Builder(this)
				.setCancelable(false)
				.setIcon(R.drawable.ic_launcher_fg)
				.setTitle(getEnglishAndroidString(R.string.app_name))
				.setMessage(errorStr)
				.setPositiveButton(getAndroidString(R.string.ok), null)
				.create()
				.show();
	}

	private void showUpdateAvailable2()
	{
		LogMessage("Will now show update2 dialog now...");
		String str = getAndroidString(R.string.json_formatted_data, "WeeWX App", "JSON formatted", WeeWXApp.WEEWX_DIR, "Inigo Plugin", "inigo-settings.txt", "GitHub.com");
		new AlertDialog.Builder(this)
				.setIcon(R.drawable.ic_launcher_fg)
				.setTitle(getEnglishAndroidString(R.string.app_name))
				.setMessage(str)
				.setPositiveButton(getAndroidString(R.string.ok), (dialog_interface, i) ->
				{
					String url = "https://github.com/evilbunny2008/WeeWXWeatherApp/wiki/JSON-formatted-data-file";
					Intent urlIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
					startActivity(urlIntent);
				})
				.setNegativeButton(getAndroidString(R.string.skip), null)
				.create()
				.show();

		LogMessage("update2 dialog should now be visible...");
	}

	private void checkReally()
	{
		new AlertDialog.Builder(this)
				.setMessage(getAndroidString(R.string.remove_all_data)).setCancelable(false)
				.setPositiveButton(getAndroidString(R.string.ok), (dialog_interface, i) ->
				{
					String settings_url = settingsURL.getText() != null &&
							!is_blank(settingsURL.getText().toString()) ?
							settingsURL.getText().toString().strip() : "";

					LogMessage("MainActivity.checkReally() Reset any widgets...");
					WidgetProvider.resetAppWidget();

					LogMessage("MainActivity.checkReally() trash all data and exit cleanly...");
					((ActivityManager)getSystemService(ACTIVITY_SERVICE)).clearApplicationUserData();

					if(is_blank(settings_url))
					{
						LogMessage("MainActivity.checkReally() Save the settings URL before exitting...");
						KeyValue.putVar("SETTINGS_URL", settings_url);
					}
				})
				.setNegativeButton(getAndroidString(R.string.no), (dialog_interface, i) -> dialog_interface.cancel())
				.create()
				.show();
	}

	private void resetScreen()
	{
		LogMessage("MainActivity.resetScreen() Do some stuff here...");

		closeKeyboard();
		closeDrawer();

		scrollView.scrollTo(0, 0);
		dialog.dismiss();
	}

	private void processSettings()
	{
		WeeWXAppCommon.doingSaveSettings = true;
		WeeWXAppCommon.saveSettingsLog = new StringBuilder();

		LogMessage("MainActivity.java processSettings() running the background updates...");

		long now = System.currentTimeMillis();

		UpdateCheck.cancelAlarm();

		long dur = (now - bgStart) / 1000;
		if(backgroundTask != null && !backgroundTask.isDone())
		{
			if(dur < 30)
			{
				LogMessage("processSettings() executor is still running and is less than 30s old (" +
						   dur + WeeWXApp.SKIPPING_S,	true, KeyValue.w);
				return;
			}

			LogMessage("processSettings() Cancelling the current background executor...");
			backgroundTask.cancel(true);
			backgroundTask = null;
		}

		// Snapshot UI state on the main thread, views must not be touched from the executor
		final String uiSettingsURL = settingsURL.getText() != null ? settingsURL.getText().toString().strip() : "";
		final String uiCustomURL = customURL.getText() != null ? customURL.getText().toString().strip() : "";
		final String uiWidgetBG = widgetBG.getText() != null ? widgetBG.getText().toString() : null;
		final String uiWidgetFG = widgetFG.getText() != null ? widgetFG.getText().toString() : null;
		final boolean uiEnableMqtt = enable_mqtt.isChecked();
		final boolean uiMetric = metric_forecasts.isChecked();
		final boolean uiMorningTempAlert = morning_temp_alert.isChecked();
		final int uiMorningTemp = (int)sliderMorningTemp.getValue();
		final boolean uiAfternoonTempAlert = afternoon_temp_alert.isChecked();
		final int uiAfternoonTemp = (int)sliderAfternoonTemp.getValue();
		final boolean uiRainfallAlert = rainfall_alert.isChecked();
		final int uiRainfallLimit = (int)sliderRainfall.getValue();
		final boolean uiRainrateWatch = rainrate_alert_watch.isChecked();
		final boolean uiRainrateWarning = rainrate_alert_warning.isChecked();
		final boolean uiRainrateSevere = rainrate_alert_severe.isChecked();
		final boolean uiRainInInches = rain_in_inches.isChecked();
		final boolean uiShowIndoor = show_indoor.isChecked();
		final boolean uiWifiOnly = wifi_only.isChecked();
		final boolean uiShowRadar = showRadar.isChecked();
		final boolean uiUseExactAlarm = use_exact_alarm.isChecked();
		final boolean uiSaveAppDebugLogs = save_app_debug_logs.isChecked();
		final boolean uiNextMoon = next_moon.isChecked();
		final boolean uiForceDarkMode = force_dark_mode.isChecked();

		bgStart = now;
		backgroundTask = executor.submit(() ->
		{
			LogMessage("processSettings() bg executor started...");

			String forecastLocationName = KeyValue.countyName = null;

			String oldData = null, jsonData = null, radtype = "", radarURL = "", forecastURL = "", webcamURL = "",
					CustomURL = "", appCustomURL, fctype = "", mqttURL = "", mqttTopic = "";
			String[] json_urls = new String[json_keys.length];

			LogMessage("processSettings() settings_url: " + uiSettingsURL);

			if(!is_valid_url(uiSettingsURL) || uiSettingsURL.equals(WeeWXApp.SETTINGS_URL_default))
			{
				errorDialog(R.string.url_was_default_or_empty);
				return;
			}

			try
			{
				String settingsData = WeeWXAppCommon.downloadSettings(uiSettingsURL).strip();
				//LogMessage("processSettings() settingsData: " + settingsData);

				if(is_blank(settingsData) || settingsData.length() < 128)
				{
					errorDialog(R.string.wasnt_able_to_connect_settings);
					return;
				}

				String[] bits = settingsData.split("\\n");
				if(bits.length > 1)
				{
					for(String bit : bits)
					{
						String line = bit.strip();
						if(is_blank(line) || line.startsWith("#") || !line.contains("="))
							continue;

						String[] mb = line.split("=", 2);
						mb[0] = mb[0].toLowerCase(Locale.ENGLISH).strip();
						mb[1] = mb[1].strip();

						LogMessage("processSettings() mb[0]: " + mb[0], KeyValue.d);
						LogMessage("processSettings() mb[1]: " + mb[1], KeyValue.d);

						switch(mb[0])
						{
							case "data" -> oldData = mb[1].strip();
							case "jsondata" -> jsonData = mb[1].strip();
							case "json-data" -> json_urls[0] = mb[1].strip();
							case "json-dicts" -> json_urls[1] = mb[1].strip();
							case "json-last" -> json_urls[2] = mb[1].strip();
							case "radar" -> radarURL = mb[1].strip();
							case "radtype" -> radtype = mb[1].toLowerCase(Locale.ENGLISH).strip();
							case "forecast" -> forecastURL = mb[1].strip();
							case WeeWXApp.FCTYPE -> fctype = mb[1].toLowerCase(Locale.ENGLISH).strip();
							case "webcam" -> webcamURL = mb[1].strip();
							case "custom" -> CustomURL = mb[1].strip();
							case "mqtt-url" -> mqttURL = mb[1].strip();
							case "mqtt-topic" -> mqttTopic = mb[1].strip();
							default -> LogMessage("processSettings() Invalid setting: " + mb[0] +
												  "=" + mb[1] + WeeWXApp.SKIPPING, true, KeyValue.w);
						}
					}
				}

				LogMessage("processSettings() here0!");
			} catch(Exception e) {
				LogMessage("processSettings() Error! e: " + e.getMessage(), true, KeyValue.e);
				doStackOutput(e);
				errorDialog(e);
				return;
			}

			if(!is_valid_url(json_urls[0]) || !is_valid_url(json_urls[1]) || !is_valid_url(json_urls[2]))
			{
				if(is_valid_url(json_urls[0]))
				{
					json_urls[1] = json_urls[0].replace("inigo-data", "inigo-dicts");
					json_urls[2] = json_urls[0].replace("inigo-data", "inigo-last");
				} else if(is_valid_url(jsonData)) {
					json_urls[0] = jsonData;
					json_urls[1] = jsonData.replace("inigo-data", "inigo-dicts");
					json_urls[2] = jsonData.replace("inigo-data", "inigo-last");
				} else if(is_valid_url(oldData)) {
					oldData = oldData.replace("inigo-data.txt", "inigo-data.json");
					json_urls[0] = oldData;
					json_urls[1] = oldData.replace("inigo-data", "inigo-dicts");
					json_urls[2] = oldData.replace("inigo-data", "inigo-last");
				}
			}

			if(is_blank(radtype))
			{
				errorDialog(R.string.radar_type_is_invalid, new Object[]{radtype});
				return;
			}

			if(!is_valid_url(radarURL))
			{
				errorDialog(R.string.radar_url_not_set);
				return;
			}

			if(uiEnableMqtt && !is_valid_url(mqttURL))
			{
				errorDialog(R.string.mqtt_url_is_not_valid, new Object[]{mqttURL, "Inigo Settings"});
				return;
			}

			if(uiEnableMqtt && is_blank(mqttTopic))
			{
				errorDialog(R.string.mqtt_topic_is_blank, new Object[]{"Inigo Settings"});
				return;
			}

			if(is_blank(fctype))
			{
				errorDialog(R.string.forecast_type_is_invalid, new Object[]{fctype});
				return;
			}

			if(is_blank(forecastURL))
			{
				errorDialog(R.string.wasnt_able_to_connect_forecast);
				return;
			}

			for(int i = 0; i < json_urls.length; i++)
				LogMessage("processSettings() " + json_labels[i] + ": " + json_urls[i]);

			LogMessage("processSettings() radarURL: " + radarURL);
			LogMessage("processSettings() radtype: " + radtype);
			LogMessage("processSettings() forecastURL: " + forecastURL);
			LogMessage("processSettings() fctype: " + fctype);
			LogMessage("processSettings() webcamURL: " + webcamURL);
			LogMessage("processSettings() CustomURL: " + CustomURL);
			LogMessage("processSettings() forecastURL: " + forecastURL);

			try
			{
				if(fctype.toLowerCase(Locale.ENGLISH).strip().equals("bom3"))
				{
					forecastURL = forecastURL.strip();

					if(forecastURL.length() > 6)
						forecastURL = forecastURL.substring(0, 6);

					if(UpdateInterval > 0)
						fctype = "bom3hourly";
					else
						fctype = "bom3daily";
				}

				switch(fctype.toLowerCase(Locale.ENGLISH).strip())
				{
					case "weatherzone3" ->
					{
						LogMessage("processSettings() fctype: " + fctype);

						String[] URLs;

						if(forecastURL.contains(","))
							URLs = forecastURL.split(",");
						else
							URLs = new String[]{forecastURL};

						if(URLs != null && URLs.length > 1)
						{
							URLs = Arrays.stream(URLs)
									.distinct()
									.toArray(String[]::new);
						} else {
							runOnUiThread(() ->
							{
								b1.setEnabled(true);
								b2.setEnabled(true);
								dialog.dismiss();
							});

							bgStart = 0;
							return;
						}

						String[] allURLs = new String[URLs.length * 7];
						int rc = 0;
						for(String url : URLs)
						{
							for(int i = 0; i < 7; i++)
							{
								long futureWhen = now + 86_400_000 * i;
								Calendar calendar = Calendar.getInstance();
								calendar.setTimeInMillis(futureWhen);

								allURLs[rc] = url;
								if(i > 0)
								{
									allURLs[rc] += String.format(Locale.ENGLISH, "?date=%04d", calendar.get(Calendar.YEAR));
									allURLs[rc] += String.format(Locale.ENGLISH, "-%02d", calendar.get(Calendar.MONTH));
									allURLs[rc] += String.format(Locale.ENGLISH, "-%02d", calendar.get(Calendar.DAY_OF_MONTH));
								}

								rc++;
							}
						}

						Collections.shuffle(Arrays.asList(allURLs));

						int loops = 1;
						for(String url : allURLs)
						{
							forecastURL = url;

							LogMessage("processSettings() URL #" + loops + "/" + allURLs.length);
							LogMessage("processSettings() forecastURL: " + forecastURL);

							String wzHTML;
							boolean addDelay = false;
							int attempt = 1;
							do
							{
								wzHTML = WeeWXAppCommon.downloadWZHTML(forecastURL, addDelay, true);
								addDelay = true;
							} while((wzHTML == null || wzHTML.length() < 10_000) && attempt++ <= 3);

							if(wzHTML == null || wzHTML.length() < 10_000)
								continue;

							LogMessage("processSettings() wzHTML.length(): " + wzHTML.length());
							JsoupHelper.processWZhtml(url, wzHTML);

							if(++loops <= allURLs.length)
							{
								int random = WeeWXAppCommon.getNextRandom(5_000, 10_000);
								LogMessage("processSettings() Sleeping for " + random + "ms...");
								Thread.sleep(random);
							}
						}

						runOnUiThread(() ->
						{
							b1.setEnabled(true);
							b2.setEnabled(true);
							dialog.dismiss();
						});

						bgStart = 0;
						return;
					}
					case "metservice.com2" ->
					{
						LogMessage("processSettings() fctype: " + fctype);
						LogMessage("processSettings() forecastURL:" + forecastURL);
/*
						String metService = WeeWXApp.wvpl.getHTML(forecastURL);
						if(metService != null && metService.length() > 1024)
						{
							String pretty = Jsoup.parse(metService).outputSettings(
									new Document.OutputSettings().indentAmount(2).prettyPrint(true)
							).outerHtml();

							//CustomDebug.writeDebug("WeeWX", "metservice.com.7day.html", pretty);
							//LogMessage("processSettings() wrote content to metservice.com.7day.html");
						}
*/
						runOnUiThread(() ->
						{
							b1.setEnabled(true);
							b2.setEnabled(true);
							dialog.dismiss();
							closeDrawer();
						});

						bgStart = 0;
						return;
					}
					case "met.no" ->
					{
						LogMessage("processSettings() forecastURL: " + forecastURL);
						LogMessage("processSettings() fctype: " + fctype);

						float lat = 0, lon = 0;

						if(forecastURL.startsWith("http"))
						{
							// Full URL provided, extract lat/lon from query params
							URI uri = URI.create(forecastURL);

							if(uri.getQuery() != null)
							{
								for(String pair : uri.getQuery().split("&"))
								{
									int idx = pair.indexOf('=');
									String key = URLDecoder.decode(pair.substring(0, idx), utf8);
									String val = URLDecoder.decode(pair.substring(idx + 1), utf8);

									if(key.equals("lat"))
										lat = str2Float(val);

									if(key.equals("lon"))
										lon = str2Float(val);
								}
							}
						} else if(forecastURL.contains(",")) {
							// Coordinates format: lat,lon
							String[] llbits = forecastURL.split(",", 2);
							lat = str2Float(llbits[0].strip());
							lon = str2Float(llbits[1].strip());
						}

						if(lat != 0 && lon != 0)
						{
							forecastURL = "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=" + lat + "&lon=" + lon;

							String url = "https://odiousapps.com/get-location-name-by-ll.php";

							forecastLocationName = WeeWXAppCommon.downloadString(url, Map.of(
									"lat", "" + lat,
									"lon", "" + lon));

							LogMessage("processSettings() forecastLocationName: " + forecastLocationName);
						}

						LogMessage("processSettings() met.no forecastURL: " + forecastURL);
					}
					case "yahoo", "weather.gc.ca", "weather.gc.ca-fr", "metoffice.gov.uk",
						 "bom2", "aemet.es", "dwd.de", "tempoitalia.it", "weatherzone2" ->
					{
						LogMessage("processSettings() forecastURL: " + forecastURL);
						LogMessage("processSettings() fctype: " + fctype);
					}
					case "wmo.int" ->
					{
						if(!forecastURL.startsWith("http"))
							forecastURL = "https://worldweather.wmo.int/en/json/" + forecastURL.strip() + "_en.xml";
						LogMessage("processSettings() forecastURL: " + forecastURL);
						LogMessage("processSettings() fctype: " + fctype);
					}
					case "weather.gov" ->
					{
						String lat = "", lon = "";
						if(forecastURL.contains("?"))
							forecastURL = forecastURL.split("\\?", 2)[1].strip();
						if(forecastURL.contains("lat") && forecastURL.contains("lon"))
						{
							String[] tmp = forecastURL.split("&");
							for (String line : tmp)
							{
								if(line.split("=", 2)[0].equals("lat"))
									lat = line.split("=", 2)[1].strip();
								if(line.split("=", 2)[0].equals("lon"))
									lon = line.split("=", 2)[1].strip();
							}
						} else {
							lat = forecastURL.split(",")[0].strip();
							lon = forecastURL.split(",")[1].strip();
						}

						forecastURL = "https://forecast.weather.gov/MapClick.php?lat=" + lat + "&lon=" + lon + "&unit=0&lg=english&FcstType=json";
						LogMessage("processSettings() forecastURL: " + forecastURL);
						LogMessage("processSettings() fctype: " + fctype);
					}
					case "bom3hourly" ->
					{
						boolean needUpdate = is_blank(KeyValue.bomGeohash) || !KeyValue.bomGeohash.equals(forecastURL.strip())
								|| is_blank(KeyValue.bomLocation);

						if(needUpdate)
						{
							KeyValue.bomGeohash = forecastURL.strip();
							String newurl = "https://api.weather.bom.gov.au/v1/locations/" + forecastURL.strip();
							String text = WeeWXAppCommon.downloadString(newurl);
							LogMessage("processSettings(): text: " + text);

							JSONObject jobj = new JSONObject(text);
							if(jobj.has("data"))
							{
								jobj = jobj.getJSONObject("data");
								if(jobj.has("name") && jobj.has("state"))
								{
									KeyValue.bomLocation = jobj.getString("name") + ", " + jobj.getString("state");
									LogMessage("processSettings() BoM Location: " + KeyValue.bomLocation);
								}
							}
						}

						forecastURL = "https://api.weather.bom.gov.au/v1/locations/" + forecastURL.strip() + "/forecasts/hourly";
						LogMessage("processSettings() forecastURL: " + forecastURL);
						LogMessage("processSettings() fctype: " + fctype);
					}
					case "bom3daily" ->
					{
						forecastURL = "https://api.weather.bom.gov.au/v1/locations/" + forecastURL.strip() + "/forecasts/daily";
						LogMessage("processSettings() forecastURL: " + forecastURL);
						LogMessage("processSettings() fctype: " + fctype);
					}
					case "metservice.com" ->
					{
						if(!forecastURL.startsWith("http"))
							forecastURL = "https://www.metservice.com/publicData/localForecast" + forecastURL.strip();
						LogMessage("processSettings() forecastURL: " + forecastURL);
						LogMessage("processSettings() fctype: " + fctype);
					}
					case "openweathermap.org" ->
					{
						if(uiMetric)
							forecastURL += "&units=metric";
						else
							forecastURL += "&units=imperial";
						forecastURL += "&lang=" + Locale.getDefault().getLanguage();
						forecastURL += "&mode=json&cnt=16";

						LogMessage("processSettings() forecastURL: " + forecastURL);
						LogMessage("processSettings() fctype: " + fctype);
					}
					case "weather.com" ->
					{
						forecastURL = "https://api.weather.com/v3/wx/forecast/daily/10day?geocode=" + forecastURL + "&format=json&apiKey" +
									  "=71f92ea9dd2f4790b92ea9dd2f779061";
						if(uiMetric)
							forecastURL += "&units=m";
						else
							forecastURL += "&units=e";
						forecastURL += "&language=" + Locale.getDefault().getLanguage() + "-" + Locale.getDefault().getCountry();
						LogMessage("processSettings() forecastURL: " + forecastURL);
						LogMessage("processSettings() fctype: " + fctype);
					}
					case "met.ie" ->
					{
						String[] llbits = forecastURL.split(",", 2);

						float lat = str2Float(llbits[0]);
						float lon = str2Float(llbits[1]);

						if(lat != 0 && lon != 0)
						{
							forecastURL = "http://openaccess.pf.api.met.ie/metno-wdb2ts/locationforecast?" +
										  "lat=" + lat + ";long=" + lon;

							String url = "https://odiousapps.com/get-location-name-by-ll.php";

							forecastLocationName = WeeWXAppCommon.downloadString(url, Map.of(
									"lat", "" + lat,
									"lon", "" + lon));

							LogMessage("forecastLocationName: " + forecastLocationName);

							Object[] o = KeyValue.closestCounty(lat, lon);

							if(o != null && o.length == 2)
							{
								KeyValue.countyName = ((County)o[0]).name;
								LogMessage("processSettings() County Name: " + KeyValue.countyName);
								LogMessage("processSettings() Distance: " + (float)o[1]);
							}
						}

						LogMessage("processSettings() forecastURL: " + forecastURL);
						LogMessage("processSettings() fctype: " + fctype);
					}
					default ->
					{
						LogMessage("processSettings() No forecast information...", KeyValue.w);
						errorDialog(R.string.forecast_type_is_invalid, new Object[]{fctype});
						return;
					}
				}
			} catch(Exception e) {
				doStackOutput(e);
				errorDialog(e);
				return;
			}

			LogMessage("processSettings() forecast checking: " + forecastURL);
			LogMessage("processSettings() fctype: " + fctype);
			LogMessage("processSettings() UpdateInterval: " + UpdateInterval);
			LogMessage("processSettings() webcamInterval: " + webcamInterval);

			if(is_blank(forecastURL))
			{
				errorDialog(R.string.forecast_url_not_set, new Object[]{"inigo-settings.txt"});
				return;
			}

			appCustomURL = uiCustomURL;
			if(!is_valid_url(appCustomURL))
				appCustomURL = null;

			if(!is_valid_url(CustomURL))
				CustomURL = null;

			List<Object> PossibleErrors = Arrays.asList(
					new Object[]{R.string.wasnt_able_to_connect_or_download, new Object[]{json_labels[0], json_urls[0]}},
					new Object[]{R.string.wasnt_able_to_connect_or_download, new Object[]{json_labels[1], json_urls[1]}},
					new Object[]{R.string.wasnt_able_to_connect_or_download, new Object[]{json_labels[2], json_urls[2]}},
					R.string.wasnt_able_to_connect_forecast,
					new Object[]{R.string.wasnt_able_to_connect_radar_image, new Object[]{radarURL}},
					R.string.wasnt_able_to_connect_webcam_url,
					R.string.wasnt_able_to_connect_custom_url
			);

			String tmpForecastURL = forecastURL;
			if(fctype.equals("wetherzone2"))
				tmpForecastURL = null;

			String tmpCustomURL = appCustomURL;
			if(appCustomURL == null)
				tmpCustomURL = CustomURL;

			if(!is_valid_url(tmpCustomURL))
				tmpCustomURL = null;

			List<String> urls = Arrays.asList(
					json_urls[0], json_urls[1], json_urls[2],
					tmpForecastURL,
					radarURL,
					webcamURL,
					tmpCustomURL
			);

			String tmpRadType = radtype.equals("image") ? "IMAGE" : "HTML";
			if(radtype.equals("image") &&
			   (radarURL.toLowerCase(Locale.ENGLISH).strip().endsWith(".mjpeg") ||
			   radarURL.toLowerCase(Locale.ENGLISH).strip().endsWith(".mjpg")))
				tmpRadType = "MJPEG";

			List<String> contentTypes = Arrays.asList(
					"JSON", "JSON", "JSON",
					"HTML",
					tmpRadType,
					"IMAGE",
					"HTML"
			);

			List<Integer> idtype = Arrays.asList(
					0, 1, 2,
					3,
					4,
					5,
					6
			);

			ParallelDownloader downloader = new ParallelDownloader(urls.size(), "processSettings");
			List<ParallelDownloader.DownloadResult> results = downloader.downloadAll(idtype, urls, contentTypes);

			boolean allOk = results.stream().allMatch(ParallelDownloader.DownloadResult::success);
			//long totalBytes = results.stream().mapToLong(ParallelDownloader.DownloadResult::length).sum();

			List<ParallelDownloader.DownloadResult> failed = results.stream().filter(r -> !r.success()).toList();

			if(!allOk)
			{
				for(ParallelDownloader.DownloadResult r : failed)
				{
					LogMessage("MainActivity.processSettings(" + r.id() + ") Error! " + r.error(), KeyValue.e);

					Object obj = PossibleErrors.get(r.id());
					if(obj instanceof Object[] multiobj)
					{
						errorDialog((int)multiobj[0], (Object[])multiobj[1]);
						return;
					}

					errorDialog((int)obj);
					return;
				}
			}

			int modhour = getIntervalTime(UpdateInterval)[1];

			List<ParallelDownloader.DownloadResult> succeeded = results.stream().toList();
			for(ParallelDownloader.DownloadResult r : succeeded)
			{
				if(0 <= r.id() && r.id() <= 2)
				{
					try
					{
						Boolean ret = processWeather(r.id(), r.string());
						if(ret == null)
						{
							bgStart = 0;
							runOnUiThread(() ->
							{
								b1.setEnabled(true);
								b2.setEnabled(true);
								dialog.dismiss();
								showProcessingErrors(r.id(), r.url());
							});

							return;
						}
					} catch(Exception e) {
						errorDialog(e);
						return;
					}

					if(r.id() == 1 && KeyValue.parseDicts())
					{
						errorDialog(R.string.failed_to_process_weather_data, new Object[]{json_labels[1]});
						return;
					}
				}

				if(r.id() == 3)
				{
					try
					{
						Result3 r3 = processForecast(modhour, fctype, r.string(), forecastURL);

						if(!r3.succeeded())
						{
							errorDialog(r3.error(), r3.error());
							return;
						}
					} catch(Exception e) {
						errorDialog(e);
						return;
					}
				}

				if(idtype.get(r.id()) == 4 && r.contentType().equals("IMAGE"))
				{
					File file = getFile(getDataDir(), getFileNameFromURL(r.url()));
					try(FileOutputStream out = new FileOutputStream(file))
					{
						LogMessage("Attempting to save to " + file.getAbsoluteFile());
						out.write(r.bytes());
						LogMessage("3Got past the save... ");
					} catch(Exception e) {
						LogMessage(WeeWXApp.ERROR_E + e, KeyValue.e);
						doStackOutput(e);
						errorDialog(e);
						return;
					}
				}

				if(r.id() == 5 && r.bytes() != null)
				{
					File file = getFile(getDataDir(), WeeWXApp.webcamFilename);
					try(FileOutputStream out = new FileOutputStream(file))
					{
						LogMessage("Attempting to save to " + file.getAbsoluteFile());
						out.write(r.bytes());
						LogMessage("4Got past the save... ");
					} catch(Exception e) {
						LogMessage(WeeWXApp.ERROR_E + e, KeyValue.e);
						doStackOutput(e);
						errorDialog(e);
						return;
					}

					WeeWXAppCommon.setWebcamFileTime(file, r.lastModified());

					LogMessage("Here1!");
				}
			}

			LogMessage("Here2!");

			if(notMergeJsonObjects())
			{
				saveSetupLog();

				bgStart = 0;
				runOnUiThread(() ->
				{
					b1.setEnabled(true);
					b2.setEnabled(true);
					dialog.dismiss();
					showMergeError();
				});

				return;
			}

			LogMessage("Here3!");

			if(KeyValue.countyName != null && is_blank(KeyValue.countyName))
				KeyValue.countyName = null;

			LogMessage("Here3a!");

			KeyValue.putVar("CountyName", KeyValue.countyName);

			LogMessage("Here3b!");

			if(KeyValue.bomLocation != null && is_blank(KeyValue.bomLocation))
				KeyValue.bomLocation = null;

			LogMessage("Here3c!");

			if(KeyValue.bomGeohash != null && is_blank(KeyValue.bomGeohash))
				KeyValue.bomGeohash = null;

			LogMessage("Here4!");

			KeyValue.putVar("bomLocation", KeyValue.bomLocation);
			KeyValue.putVar("bomGeohash", KeyValue.bomGeohash);

			KeyValue.putVar("SETTINGS_URL", uiSettingsURL);
			KeyValue.putVar(WeeWXApp.UPDATE_FREQUENCY, UpdateFrequency);
			KeyValue.putVar("UpdateInterval", UpdateInterval);
			KeyValue.putVar("webcamInterval", webcamInterval);

			LogMessage("Here5!");

			for(int i = 0; i < json_urls.length; i++)
				KeyValue.putVar(json_keys[i] + "_url", json_urls[i]);

			KeyValue.putVar("FORECAST_URL", forecastURL);
			KeyValue.putVar(WeeWXApp.FCTYPE, fctype);

			if(is_blank(forecastLocationName))
				forecastLocationName = null;

			LogMessage("Here6!");

			KeyValue.putVar("forecastLocationName", forecastLocationName);

			KeyValue.putVar("radtype", radtype);
			KeyValue.putVar("RADAR_URL", radarURL);

			if(!is_valid_url(webcamURL))
				webcamURL = null;

			KeyValue.putVar("WEBCAM_URL", webcamURL);

			if(!is_valid_url(CustomURL))
				CustomURL = null;

			LogMessage("Here7!");

			KeyValue.putVar(CUSTOM_URL, CustomURL);

			if(!is_valid_url(appCustomURL))
				appCustomURL = null;

			KeyValue.putVar(custom_url, appCustomURL);

			if(!uiEnableMqtt || !is_valid_url(mqttURL) || is_blank(mqttTopic))
			{
				LogMessage("Here8!");

				KeyValue.putVar(ENABLE_MQTT, false);
				KeyValue.putVar(MQTT_URL, null);
				KeyValue.putVar(MQTT_TOPIC, null);
			} else {
				LogMessage("Here9!");

				KeyValue.putVar(ENABLE_MQTT, uiEnableMqtt);
				KeyValue.putVar(MQTT_URL, mqttURL);
				KeyValue.putVar(MQTT_TOPIC, mqttTopic);
			}

			KeyValue.putVar("morning_temp_alert", uiMorningTempAlert);
			KeyValue.putVar("MorningTemp", uiMorningTemp);

			KeyValue.putVar("afternoon_temp_alert", uiAfternoonTempAlert);
			KeyValue.putVar("AfternoonTemp", uiAfternoonTemp);

			KeyValue.putVar("rainfall_alert", uiRainfallAlert);
			KeyValue.putVar("RainfallLimit", uiRainfallLimit);

			KeyValue.putVar(RAINRATE_ALERT_WATCH, uiRainrateWatch);
			KeyValue.putVar(RAINRATE_ALERT_WARNING, uiRainrateWarning);
			KeyValue.putVar(RAINRATE_ALERT_SEVERE, uiRainrateSevere);

			KeyValue.putVar("metric", uiMetric);
			KeyValue.putVar("rainInInches", uiRainInInches);
			KeyValue.putVar("showIndoor", uiShowIndoor);
			KeyValue.putVar("DayNightMode", DayNightMode);
			KeyValue.putVar("onlyWIFI", uiWifiOnly);
			KeyValue.putVar("radarforecast", uiShowRadar);
			KeyValue.putVar("use_exact_alarm", uiUseExactAlarm);
			KeyValue.putVar(SAVE_APP_DEBUG_LOGS, uiSaveAppDebugLogs);
			KeyValue.putVar("next_moon", uiNextMoon);
			KeyValue.putVar(FORCE_DARK_MODE, uiForceDarkMode);

			KeyValue.putVar(WIDGET_THEME_MODE, widget_theme_mode);

			LogMessage("Here10!");

			String edit = uiWidgetBG;
			if(edit != null && edit.length() > 0)
			{
				int bg = parseHexToColour(edit);
				KeyValue.putVar("widgetBG", bg);
				LogMessage("processSettings() Saved widget bg colour: " + to_ARGB_hex(bg));
			} else {
				KeyValue.putVar("widgetBG", null);
			}

			LogMessage("Here12!");

			edit = uiWidgetFG;
			if(edit != null && edit.length() > 0)
			{
				int fg = parseHexToColour(edit);
				KeyValue.putVar("widgetFG", fg);
				LogMessage("processSettings() Saved widget fg colour: " + to_ARGB_hex(fg));
			} else {
				KeyValue.putVar("widgetFG", null);
			}

			LogMessage("processSettings() Refresh widgets if at least one exists...");
			updateAppWidget();

			LogMessage("processSettings() Set the alarm...");
			UpdateCheck.setNextAlarm();

			KeyValue.putVar(SETUP_FINISHED, true);

			LogMessage("Here13!");

			//if(!saveSetupLog())
			//	return;

			runOnUiThread(() ->
			{
				resetScreen();

				onResume();

				LogMessage("processSettings() Apply the new theme");
				setTheme((int)KeyValue.readVar("theme", WeeWXApp.theme_default));
				WeeWXApp.applyTheme(false);

				if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
				{
					overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0);
					overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0);
				}

				LogMessage("processSettings() Recreate the activity...");
				finish();
				startActivity(new Intent(MainActivity.this, MainActivity.class));
			});
		});
	}

	@SuppressWarnings("UnusedReturnValue")
	boolean saveSetupLog()
	{
		String saveLogReturn = WeeWXAppCommon.outputSaveSettings();
		if(saveLogReturn != null)
		{
			runOnUiThread(() ->
			{
				b1.setEnabled(true);
				b2.setEnabled(true);
				dialog.dismiss();
				new AlertDialog.Builder(this)
						.setTitle(getAndroidString(R.string.error))
						.setMessage(saveLogReturn)
						.setPositiveButton(getAndroidString(R.string.ill_fix_and_try_again), null)
						.create()
						.show();
			});

			return false;
		}

		return true;
	}

	void errorDialog(Exception e)
	{
		errorDialog(e.getLocalizedMessage(), e.getMessage());
	}

	void errorDialog(String errorStr, String logStr)
	{
		LogMessage("errorDialog(): logStr: " + logStr, KeyValue.e);

		saveSetupLog();

		bgStart = 0;
		runOnUiThread(() ->
		{
			b1.setEnabled(true);
			b2.setEnabled(true);
			dialog.dismiss();
			new AlertDialog.Builder(this)
					.setTitle(getAndroidString(R.string.error))
					.setMessage(errorStr)
					.setPositiveButton(getAndroidString(R.string.ill_fix_and_try_again), null)
					.create()
					.show();
		});
	}

	void errorDialog(int strid, Object[] vars)
	{
		String errorStr = getAndroidString(strid, vars);
		String logStr = getEnglishAndroidString(strid, vars);
		errorDialog(errorStr, logStr);
	}

	void errorDialog(int strid)
	{
		String errorStr = getAndroidString(strid);
		String logStr = getEnglishAndroidString(strid);
		errorDialog(errorStr, logStr);
	}

	private final Observer<String> notificationObserver = str ->
	{
		if(str == null)
			return;

		LogMessage("MainActivity.notificationObserver: " + str);

		if(str.equals(INIGO_INTENT))
			showUpdateAvailable();

		if(str.equals(PROCESSING_ERRORS))
		{
			int errorCount = (int)KeyValue.readVar("ProcessingErrorCount", -1);
			if(errorCount < 1)
				return;

			int json_id = (int)KeyValue.readVar("ProcessingErrorID", -1);
			if(json_id < 0 || json_id >= json_keys.length)
				return;

			String json_url = (String)KeyValue.readVar(json_keys[json_id] + "_url", "");
			if(is_blank(json_url))
				return;

			showProcessingErrors(json_id, json_url);
		}

		if(str.equals(FAILED_TO_MERGE))
			showMergeError();

		if(str.equals(UPDATE_ERRORS))
		{
			LogMessage("MainActivity.notificationObserver: showUpdateErrors();");
			showUpdateErrors();
		}
	};

	public boolean isViewPagerNull()
	{
		return mViewPager == null;
	}

	public void setUserInputPager(boolean b)
	{
		LogMessage("MainActivity.setUserInputPager()");
		if(isViewPagerNull())
			return;

		mViewPager.post(() -> mViewPager.setUserInputEnabled(b));
	}

	static MainActivity getInstance()
	{
		return instance;
	}

	private boolean hasLocalNetworkPermission()
	{
		return Build.VERSION.SDK_INT < 37 ||
			ActivityCompat.checkSelfPermission(this, LOCAL_NETWORK_PERMISSION) == PackageManager.PERMISSION_GRANTED;
	}

	private static boolean isLocalNetworkUrl(String url)
	{
		try
		{
			URI uri = URI.create(url.strip());
			if(!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()))
				return false;
			String host = uri.getHost();
			if(host == null)
				return false;
			host = host.toLowerCase(Locale.ROOT);
			if(host.equals("localhost") || host.endsWith(".local") || host.equals("::1") ||
				host.startsWith("fc") || host.startsWith("fd") || host.startsWith("fe80:"))
				return true;
			String[] octets = host.split("\\.");
			if(octets.length != 4)
				return false;
			int[] ip = new int[4];
			for(int i = 0; i < 4; i++)
			{
				ip[i] = Integer.parseInt(octets[i]);
				if(ip[i] < 0 || ip[i] > 255) return false;
			}
			return ip[0] == 10 || ip[0] == 127 ||
				(ip[0] == 172 && ip[1] >= 16 && ip[1] <= 31) ||
				(ip[0] == 192 && ip[1] == 168) ||
				(ip[0] == 169 && ip[1] == 254);
		}
		catch(IllegalArgumentException e)
		{
			return false;
		}
	}

	private boolean hasLocalNetworkEndpoint()
	{
		if(isLocalNetworkUrl((String)KeyValue.readVar("SETTINGS_URL", "")))
			return true;
		for(String key : json_keys)
			if(isLocalNetworkUrl((String)KeyValue.readVar(key + "_url", "")))
				return true;
		return isLocalNetworkUrl((String)KeyValue.readVar("WEBCAM_URL", "")) ||
			isLocalNetworkUrl((String)KeyValue.readVar("FORECAST_URL", ""));
	}

	private void requestLocalNetworkPermission(boolean userInitiated)
	{
		if(hasLocalNetworkPermission() || localNetworkPermissionRequested)
			return;
		// Avoid repeatedly prompting on every launch after a denial.
		if(!userInitiated && (boolean)KeyValue.readVar("localNetworkPermissionAsked", false))
			return;
		new AlertDialog.Builder(this)
			.setTitle(R.string.local_network_permission_title)
			.setMessage(getString(R.string.local_network_permission_explanation, getString(R.string.app_name)))
			.setPositiveButton(R.string.ok, (dialog, which) -> {
				localNetworkPermissionRequested = true;
				KeyValue.putVar("localNetworkPermissionAsked", true);
				ActivityCompat.requestPermissions(this,
					new String[]{LOCAL_NETWORK_PERMISSION}, LOCAL_NETWORK_PERMISSION_CODE);
			})
			.setNegativeButton(R.string.no, (dialog, which) -> pendingLocalSettingsSave = false)
			.show();
	}

	private void requestNotificationPermission()
	{
		LogMessage("requestNotificationPermission()");

		if(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
		   ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
		{
			KeyValue.hasNotificationPerm = true;
			return;
		}

		LogMessage("requestNotificationPermission() Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU");

		boolean hasAsked = (boolean)KeyValue.readVar("hasAsked", false);

		LogMessage("requestNotificationPermission() Permission not granted");

		if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS))
		{
			LogMessage("requestNotificationPermission() User previously denied, show rational pop-up");

			KeyValue.putVar("hasAsked", true);

			// show explanation dialog first
			new AlertDialog.Builder(this)
					.setCancelable(false)
					.setTitle(getAndroidString(R.string.notification_permission))
					.setMessage(getAndroidString(R.string.notifications_needed))
					.setPositiveButton(getAndroidString(R.string.ok), (dialog, which) ->
					{
						LogMessage("requestNotificationPermission() User choose to retry notification permission");
						ActivityCompat.requestPermissions(this,
							new String[]{Manifest.permission.POST_NOTIFICATIONS},
							NOTIFICATION_PERMISSION_CODE);
					})
					.setNegativeButton(getAndroidString(R.string.no), (dialog, which) ->
					{
						LogMessage("requestNotificationPermission() User choose not to retry notification permission");
						disableAlerts();
					})
					.create()
					.show();

			return;
		}

		if(!hasAsked)
		{
			// first time
			LogMessage("requestNotificationPermission() Showing user notification permission");
			ActivityCompat.requestPermissions(this,
				new String[]{Manifest.permission.POST_NOTIFICATIONS},
				NOTIFICATION_PERMISSION_CODE);

			return;
		}

		LogMessage("requestNotificationPermission() Permission not granted, but already asked twice.");

		new AlertDialog.Builder(this)
				.setTitle(getAndroidString(R.string.notification_permission))
				.setMessage(getAndroidString(R.string.notifications_needed2))
				.setPositiveButton(getAndroidString(R.string.ok), (dialog, which) ->
				{
					LogMessage("requestNotificationPermission() User choose to open settings");
					Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
					intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
					startActivity(intent);
				})
				.setNegativeButton(getAndroidString(R.string.no), (dialog, which) ->
				{
					LogMessage("requestNotificationPermission() User choose to decline opening settings");
					disableAlerts();
				})
				.create()
				.show();
	}

	void disableAlerts()
	{
		morning_temp_alert.setChecked(false);
		afternoon_temp_alert.setChecked(false);
		rainfall_alert.setChecked(false);
		rainrate_alert_watch.setChecked(false);
		rainrate_alert_warning.setChecked(false);
		rainrate_alert_severe.setChecked(false);
		KeyValue.hasNotificationPerm = false;
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
	{
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);

		if(requestCode == NOTIFICATION_PERMISSION_CODE)
		{
			KeyValue.hasNotificationPerm = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
		}
		else if(requestCode == LOCAL_NETWORK_PERMISSION_CODE)
		{
			localNetworkPermissionRequested = false;
			boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
			LogMessage("Local network permission granted: " + granted);
			if(granted)
			{
				if(pendingLocalSettingsSave)
				{
					pendingLocalSettingsSave = false;
					b1.performClick();
				}
				else
					processSettings();
			}
			else
			{
				pendingLocalSettingsSave = false;
				new AlertDialog.Builder(this)
					.setMessage(R.string.local_network_permission_denied)
					.setPositiveButton(R.string.ok, null)
					.show();
			}
		}
	}

	private String statsFormat(int idx)
	{
		// ET is expressed in the same units as rain
		if(keys[idx].equals("rain") || keys[idx].equals("ET"))
			return rainFormat;

		return formatting[idx];
	}

	private void putOutput(JSONObject output, String key, Object value)
	{
		try
		{
			output.put(key, value);
		} catch (JSONException e) {
			doStackOutput(e);
		}
	}

	private void queueJSON(String key, Object value)
	{
		try
		{
			pendingJSON.put(key, value);
		} catch (JSONException e) {
			doStackOutput(e);
		}
	}

	private float getCachedFloat(String key)
	{
		if(KeyValue.values.get(key) instanceof Float val)
			return val;

		return (float)WeeWXAppCommon.getJson(key, 0f);
	}

	private void processStatsValue(String key, Float f, String format)
	{
		if(f == null || Objects.equals(KeyValue.values.get(key), f))
			return;

		KeyValue.values.put(key, f);

		String value = String.format(Locale.ENGLISH, format, f);
		putOutput(mqttOutput2, key, value);

		if(key.equals("day_rain_sum"))
			putOutput(mqttOutput, "rain", value);

		queueJSON(key, f);
	}

	private void processStatsTime(String key, Long l, int timeMode)
	{
		if(l == null || l == 0)
			return;

		String dateTime = getDateTimeStr(l, timeMode);

		if(Objects.equals(KeyValue.values.get(key), dateTime))
			return;

		KeyValue.values.put(key, dateTime);
		putOutput(mqttOutput2, key, dateTime);
		queueJSON(key, l / 1_000D);
	}

	private void processStatsWindDir(String key, Integer i, Float degrees)
	{
		if(i == null)
			return;

		Number numericDirection = degrees != null ? degrees : i;
		if(Objects.equals(KeyValue.values.get(key), numericDirection))
			return;

		KeyValue.values.put(key, numericDirection);
		putOutput(mqttOutput2, key, WeeWXAppCommon.cssToSVG("wi-wind-deg", i));
		queueJSON(key, numericDirection);

		// The numeric MQTT update also refreshes the visible label.
		String labelKey = key + "_str";
		processCompass(mqttOutput2, labelKey, labelKey, labelKey, null);
	}

	private Double pendingOrCachedNumber(String key)
	{
		if(pendingJSON != null && pendingJSON.has(key) && !pendingJSON.isNull(key))
		{
			double number = pendingJSON.optDouble(key, Double.NaN);
			if(Double.isFinite(number))
				return number;
		}
		if(WeeWXAppCommon.hasElement(key))
		{
			Object value = WeeWXAppCommon.getJson(key, Double.NaN);
			if(value instanceof Number)
				return ((Number)value).doubleValue();
		}
		return null;
	}

	private void processCompass(JSONObject output, String key, String elementId, String jsonKey, String s)
	{
		if(jsonKey.endsWith("_str") && KeyValue.ordinateNames.size() == 17)
		{
			String directionKey = jsonKey.substring(0, jsonKey.length() - 4);
			Double degrees = pendingOrCachedNumber(directionKey);
			if(degrees != null)
			{
				String mapped = WeeWXAppCommon.compassFromDegrees(degrees);
				if(mapped != null)
				{
					String speedKey = null;
					if(directionKey.equals("current_windGustDir"))
						speedKey = "current_windGust";
					else if(directionKey.endsWith("wind_vecdir"))
						speedKey = directionKey.substring(0, directionKey.length() - "wind_vecdir".length()) + "wind_avg";
					else if(directionKey.endsWith("wind_gustdir"))
						speedKey = directionKey.substring(0, directionKey.length() - "wind_gustdir".length()) + "wind_max";

					Double speed = speedKey == null ? null : pendingOrCachedNumber(speedKey);
					if(speed != null && Double.isFinite(speed) && speed <= 0)
						mapped = WeeWXAppCommon.compassUnavailable();
					s = mapped;
				}
			}
		}

		if(s == null || s.isBlank() || Objects.equals(KeyValue.values.get(key), s))
			return;

		KeyValue.values.put(key, s);
		putOutput(output, elementId, s);
		queueJSON(jsonKey, s);
	}

	void processStatsPacket(String key, Float f, Long l, Integer i, String s)
	{
		String timeperiod = null;
		for(String tp : new String[]{"day_", "yesterday_", "month_", "last_month_", "year_", "last_year_", "alltime_"})
		{
			if(key.startsWith(tp))
			{
				timeperiod = tp;
				break;
			}
		}

		if(timeperiod == null)
			return;

		int timeMode = 0;
		if(timeperiod.contains("month_"))
			timeMode = 1;
		else if(timeperiod.contains("year_"))
			timeMode = 2;
		else if(timeperiod.equals("alltime_"))
			timeMode = 3;

		String varname = key.substring(timeperiod.length());

		switch(varname)
		{
			case "wind_max", "wind_avg" ->
			{
				processStatsValue(key, f, "%.1f");
				String directionKey = timeperiod + (varname.equals("wind_avg") ? "wind_vecdir" : "wind_gustdir");
				String labelKey = directionKey + "_str";
				processCompass(mqttOutput2, labelKey, labelKey, labelKey, null);
				return;
			}
			case "wind_maxtime" ->
			{
				processStatsTime(key, l, timeMode);
				return;
			}
			case "wind_vecdir", "wind_gustdir" ->
			{
				processStatsWindDir(key, i, f);
				return;
			}
			case "wind_vecdir_str", "wind_gustdir_str" ->
			{
				processCompass(mqttOutput2, key, key, key, s);
				return;
			}
		}

		for(int i2 = 0; i2 < keys.length; i2++)
		{
			if(varname.equals(keys[i2] + "_min") || varname.equals(keys[i2] + "_max") ||
			   varname.equals(keys[i2] + "_sum"))
			{
				processStatsValue(key, f, statsFormat(i2));
				return;
			}

			if(varname.equals(keys[i2] + "_mintime") || varname.equals(keys[i2] + "_maxtime"))
			{
				processStatsTime(key, l, timeMode);
				return;
			}
		}
	}

	private void processCurrentValue(String key, Float f, Integer i, String s)
	{
		switch(key)
		{
			case "appTemp", "barometer", "dewpoint", "inTemp", "outTemp", "UV", "windGust" ->
			{
				if(f == null || Objects.equals(KeyValue.values.get(key), f))
					return;

				KeyValue.values.put(key, f);
				putOutput(mqttOutput, key, String.format(Locale.ENGLISH, "%.1f", f));
				queueJSON("current_" + key, f);
				if(key.equals("windGust"))
					processCompass(mqttOutput, "windGustDir_str", "gustDirStr", "current_windGustDir_str", null);
			}
			case "inHumidity", "outHumidity", "radiation" ->
			{
				if(i == null || Objects.equals(KeyValue.values.get(key), i))
					return;

				KeyValue.values.put(key, i);
				putOutput(mqttOutput, key, String.format(Locale.ENGLISH, "%d", i));
				queueJSON("current_" + key, i);
			}
			case "windGustDir" ->
			{
				if(i == null || Objects.equals(KeyValue.values.get(key), i))
					return;

				KeyValue.values.put(key, i);
				putOutput(mqttOutput, "gustDirComp", WeeWXAppCommon.cssToSVG("wi-wind-deg", i));
				queueJSON("current_windGustDir", f != null ? f : i);
				processCompass(mqttOutput, "windGustDir_str", "gustDirStr", "current_windGustDir_str", null);
			}
			case "windGustDir_str" -> processCompass(mqttOutput, key, "gustDirStr", "current_windGustDir_str", s);
		}
	}

	void processArchivePacket(String key, Float f, Long l, Integer i, String s)
	{
		if(key.equals("dateTime"))
		{
			if(l == null)
				return;

			//LogMessage("New dateTime (" + l + ")");
			String newTime = headingTime(l);
			LogMessage("New dateTime_archive (" + newTime + ")");
			// Keep report_time current so checkRainfallAlert() knows the data is from this rain day
			queueJSON("report_time", l / 1_000D);
			if(stats != null && stats.pageReady)
				new Handler(Looper.getMainLooper()).post(() -> stats.updateTimeStr(newTime));
			return;
		}

		processCurrentValue(key, f, i, s);
	}

	void processLoopPacket(String key, Float f, Long l, Integer i, String s)
	{
		switch(key)
		{
			case "dateTime" ->
			{
				if(l == null)
					return;

				//LogMessage("New dateTime (" + l + ")");
				String newTime = headingTime(l);
				//LogMessage("New dateTime_loop (" + newTime + ")");
				if(weather != null && weather.pageReady)
					new Handler(Looper.getMainLooper()).post(() -> weather.updateTimeStr(newTime));
				if(stats != null && stats.pageReady)
					new Handler(Looper.getMainLooper()).post(() -> stats.updateTimeStr(newTime));
			}
			case "ET_since_last_archive", "rain_since_last_archive" ->
			{
				if(f == null)
					return;

				if(Objects.equals(KeyValue.values.get(key), f))
					return;

				KeyValue.values.put(key, f);

				String obs = key.split("_")[0];
				String statsKey = "day_" + obs + "_sum";

				String runningTotal = String.format(Locale.ENGLISH, rainFormat, f + getCachedFloat(statsKey));
				putOutput(mqttOutput2, statsKey, runningTotal);

				if(obs.equals("rain"))
					putOutput(mqttOutput, "rain", runningTotal);
			}
			default -> processCurrentValue(key, f, i, s);
		}
	}

	// MQTT callbacks for each topic can run concurrently on different Rx threads
	synchronized void processPacket(String topic, String packet)
	{
		JSONObject jsonObject;
		try
		{
			jsonObject = new JSONObject(packet);
		} catch (Exception e) {
			doStackOutput(e);
			return;
		}

		if(weather == null || stats == null)
		{
			for(int i = 0; i < mSectionsPagerAdapter.getItemCount(); i++)
			{
				Fragment fragment = getSupportFragmentManager().findFragmentByTag("f" + mSectionsPagerAdapter.getItemId(i));

				if(fragment instanceof Weather)
					weather = (Weather) fragment;
				else if(fragment instanceof Stats)
					stats = (Stats)fragment;
			}
		}

		if(mqttOutput == null)
			mqttOutput = new JSONObject();

		if(mqttOutput2 == null)
			mqttOutput2 = new JSONObject();

		pendingJSON = new JSONObject();

		if(!Objects.equals(lastTopic, topic))
		{
			lastTopic = topic;
			//LogMessage("New Topic: " + topic);
		}

		//LogMessage("New MQTT packet: " + jsonObject, KeyValue.d);

		for(Iterator<String> it = jsonObject.keys(); it.hasNext();)
		{
			String key = it.next();
			if(key == null)
				continue;

			key = key.strip();
			if(key.isBlank())
				continue;

			if(!jsonObject.has(key) || jsonObject.isNull(key))
				continue;

			Object value = jsonObject.opt(key);
			if(value == null)
				continue;

			Double d = null;
			Float f = null;
			Long l = null;
			Integer i = null;
			String s = null;

			if(value instanceof Number)
			{
				try
				{
					d = ((Number)value).doubleValue();
				} catch (Exception ignore) {}

				try
				{
					f = ((Number)value).floatValue();
					i = Math.round(f);
				} catch (Exception e) {
					i = 0;
				}

				if(f == null)
					f = (float)i;

				if(d == null)
					d = (double)f;

				l = Math.round(d * 1_000);
			} else if(value instanceof String) {
				s = (String)value;
			}

			if(topic.endsWith("/loop"))
				processLoopPacket(key, f, l, i, s);

			if(topic.endsWith("/archive"))
				processArchivePacket(key, f, l, i, s);

			if(topic.endsWith("/stats"))
				processStatsPacket(key, f, l, i, s);
		}

		WeeWXAppCommon.updateJSON(pendingJSON);

		if(weather != null && weather.pageReady && mqttOutput != null && mqttOutput.length() > 0)
		{
			//LogMessage("MainActivity.java mqttOutput: " + mqttOutput, KeyValue.d);
			mqttOutput = weather.updateField(mqttOutput);
		}

		if(stats != null && stats.pageReady && mqttOutput2 != null && mqttOutput2.length() > 0)
		{
			//LogMessage("MainActivity.java mqttOutput2: " + mqttOutput2, KeyValue.d);
			mqttOutput2 = stats.updateField(mqttOutput2);
		}

		checkTempAlerts();
		checkRainfallAlert();
		checkRainrateAlert();
		updateAppWidget();
	}
}
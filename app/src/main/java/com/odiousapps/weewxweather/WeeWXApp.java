package com.odiousapps.weewxweather;

import android.Manifest;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PictureDrawable;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;

import com.caverock.androidsvg.PreserveAspectRatio;
import com.caverock.androidsvg.SVG;
import com.github.evilbunny2008.colourpicker.CPEditText;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.os.ConfigurationCompat;
import androidx.core.os.LocaleListCompat;

import static com.odiousapps.weewxweather.WeeWXAppCommon.LOGTAG;
import static com.odiousapps.weewxweather.WeeWXAppCommon.is_blank;
import static com.odiousapps.weewxweather.WeeWXAppCommon.WIDGET_THEME_MODE;
import static com.odiousapps.weewxweather.WeeWXAppCommon.doStackOutput;
import static com.odiousapps.weewxweather.WeeWXAppCommon.LogMessage;
import static com.odiousapps.weewxweather.WeeWXAppCommon.str2Int;
import static com.odiousapps.weewxweather.WeeWXNotificationManager.updateNotificationMessage;

@SuppressWarnings({"deprecation", "SameParameterValue", "RedundantSuppression", "unused"})
public class WeeWXApp extends Application
{
	private static final String html_header =   """
												<!DOCTYPE html>
												<html lang='CURRENT_LANG'>
												<head>
													<meta charset='utf-8'>
													<meta name='viewport' content='width=device-width, initial-scale=1.0, user-scalable=no'>
													<meta name='color-scheme' content='light dark'>
												""";

	static final String script_header =	 """
												<script>
													document.addEventListener("DOMContentLoaded", function()
													{
														const btn = document.getElementById("scrollToTop");
										
														window.addEventListener("scroll", () =>
														{
															// At the top? Hide the button
															if(document.documentElement.scrollTop > 100)
																btn.classList.add("show");
															else
																btn.classList.remove("show");
														});
										
														btn.addEventListener("click", () =>
														{
															window.scrollTo(
															{
																top: 0,
																behavior: "smooth"
															});
														});
													});
												</script>
										""";

	static final String html_header_rest =	"""
											</head>
											<body>
											""";

	private static final String inline_arrow_light =	"""
															<!-- Floating scroll-to-top button -->
															<div id="scrollToTop">
																<svg viewBox="0 0 24 24">
																	<path fill="#fff" d="M12 4l-7 8h4v8h6v-8h4z"/>
																</svg>
															</div>
														""";

	private static final String inline_arrow_dark =	"""
														<!-- Floating scroll-to-top button -->
														<div id="scrollToTop">
															<svg viewBox="0 0 24 24">
																<path fill="#000" d="M12 4l-7 8h4v8h6v-8h4z"/>
															</svg>
														</div>
													""";

	static String inline_arrow = inline_arrow_light;

	// Leave room for the scroll-to-top button (bottom 20px + 48px high)
	static final String arrow_spacer = "\n<div style='height:76px'></div>\n";

	static final String html_footer =	"""
											<script>
												try
												{
													window.addEventListener('message', (event) =>
													{
														const data = JSON.parse(event.data);
													    for(const key in data)
													    {
															const el = document.getElementById(key);
															if(el)
																el.innerHTML = data[key];
														}
													});
	
													if(window.WeeWXApp && typeof window.WeeWXApp.onReady === 'function')
														window.WeeWXApp.onReady();
												} catch(e) {
													console.log('error: ' + e.message);
												}
											</script>
										</body>
										</html>
										""";

	private static final String about_blurb =   """
												Big thanks to the <a href='https://weewx.com'>WeeWX project</a>, as this app
												wouldn't be possible otherwise.<br><br>
												Weather Icons from <a href='https://www.flaticon.com/'>FlatIcon</a> and
												is licensed under <a href='https://creativecommons.org/licenses/by/3.0/'>CC 3.0 BY</a> and
												<a href="https://www.vecteezy.com/free-vector/">Vectors by Vecteezy</a> and
												<a href="https://mixkit.co/">Alert sound from Mixkit.co</a>
												<br><br>
												Current WebView Library Version: WEBVIEWVER
												<br/><br/>
												WeeWX Weather App vAPPVERSION is by <a href='https://odiousapps.com'>OdiousApps</a>.
												<br/>
												<b>Memory Statistics:</b>
												<br/>
												USEDMEMORY / MAXMEMORY
												""";

	final static String debug_html =	"""
											<div id='widthDisplay'
												style='position: fixed; top: 10px; right: 10px;
												background: rgba(0,0,0,0.7); color: #fff;
												padding: 5px 10px; border-radius: 5px;
												font-family: monospace; z-index: 9999;'>
											</div>
											<script>
											const display = document.getElementById('widthDisplay');
											function updateWidth()
											{
												display.textContent = 'Width: ' + window.innerWidth + 'px ' +
												'x Height: ' + window.innerHeight + 'px';
											}
											// Update immediately
											updateWidth();
											// Update on resize
											window.addEventListener('resize', updateWidth);
											</script>
										""";

	private static final String dialog_html_header =	"""
														<!doctype html>
														<html lang="REPLACE_WITH_LANG">
														<head>
															<meta charset="utf-8" />
															<meta name="viewport" content="width=device-width,initial-scale=1" />
															<title>Warning UI Mock</title>
														""";

	private static final String dialog_html_header_rest =	"""
															</head>
															<body>
																<!-- Example: full "modal-like" warning -->
																<div class="warn-wrap" role="alertdialog" aria-labelledby="w-title" aria-describedby="w-desc" tabindex="0">
																	<div class="warn-accent" aria-hidden="true"></div>
																	<div class="warn-icon" aria-hidden="true">
																		<!-- inline SVG warning icon -->
																		<svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">
																			<path d="M11.03 3.5c.37-.9 1.64-.9 2.01 0l7.04 17.06A1.5 1.5 0 0 1 19.67 22H4.33a1.5 1.5 0 0 1-1.41-1.44L10 3.5z" fill="currentColor" opacity="0.12"/>
																			<path d="M12 8.25c-.41 0-.75.34-.75.75v4.5c0 .41.34.75.75.75s.75-.34.75-.75v-4.5c0-.41-.34-.75-.75-.75zm0 8.5a.9.9 0 1 1 0 1.8.9.9 0 0 1 0-1.8z" fill="currentColor"/>
																		</svg>
																	</div>
																	<div class="warn-body">
																	<p id="w-desc" class="warn-desc">
																		WARNING_BODY
																	</p>
																	</div>
																</div>
															</body>
															</html>
														\t""";

	record Setting(String Key, Object Val) {}

	static final String WARNING_BODY = "WARNING_BODY";
	static final String CUSTOM_URL = "CUSTOM_URL";
	static final String custom_url = "custom_url";
	static final String UPDATE_FREQUENCY = "UpdateFrequency";
	static final String SKIPPING = ", skipping...";
	static final String SKIPPING_S = "s), skipping...";
	static final String FCTYPE = "fctype";
	static final String RAINRATE_ALERT_WATCH = "rainrate_alert_watch";
	static final String RAINRATE_ALERT_WARNING = "rainrate_alert_warning";
	static final String RAINRATE_ALERT_SEVERE = "rainrate_alert_severe";
	static final String ERROR_E = "Error! e: ";
	static final String SAVE_APP_DEBUG_LOGS = "save_app_debug_logs";
	static final String FAILED_TO_CREATE_LOG_FILE_IN_MEDIA_STORE_FILES = "Failed to create log file in MediaStore.Files";
	static final String CONTENT_TYPE = "text/plain";
	static final String WEEWX_DIR = "WeeWX";
	static final String RSS_CHECK = "rssCheck";
	static final String TIME_EXT = "_time";

	static final String ENABLE_MQTT = "ENABLE_MQTT";
	static final String MQTT_URL = "MQTT_URL";
	static final String MQTT_TOPIC = "MQTT_TOPIC";

	static final String SETUP_FINISHED = "SETUP_FINISHED";

	static String current_html_headers;

	static String current_dialog_html;

	static String current_about_blurb = about_blurb;

	final static String emptyField = "<div style='span:3;'>\u00A0</div>\n";
	final static String currentSpacer = "\t\t\t<div class='currentSpacer'>\u00A0</div>\n";

	private static WeeWXApp instance = null;
	private static Colours colours;
	private static int lastNightMode = -1;

	final static int minimum_inigo_version = 2001011;

	final static boolean radarforecast_default = false;
	final static boolean disableSwipeOnRadar_default = false;
	final static boolean onlyWIFI_default = false;
	final static boolean metric_default = true;
	final static boolean rain_in_inches_default = false;
	final static boolean showIndoor_default = true;
	final static boolean use_exact_alarm_default = false;
	final static boolean next_moon_default = false;
	final static boolean force_dark_mode_default = false;
	final static boolean save_app_debug_logs_default = false;
	final static boolean enable_mqtt_default = false;

	final static int bgColour_default = 0xFFFFFFFF;
	final static int fgColour_default = 0xFF000000;

	final static int widgetBG_default = 0x00000000;
	final static int widgetFG_default = 0xFFFFFFFF;
	final static int widgetBG_default2 = 0x00000000;
	final static int widgetFG_default2 = 0xFF000000;

	final static int UpdateFrequency_default = 1;
	final static int UpdateInterval_default = 0;
	final static int webcamInterval_default = 4;
	final static int mySlider_default = 100;
	final static int DayNightMode_default = 2;
	final static int widget_theme_mode_default = 4;
	final static int theme_default = R.style.AppTheme_WeeWXApp_Light_Common;
	final static int mode_default = AppCompatDelegate.MODE_NIGHT_NO;

	final static boolean morning_temp_alert_default = false;
	final static int MorningTemp_default = 230;

	final static boolean afternoon_temp_alert_default = false;
	final static int AfternoonTemp_default = 260;

	final static boolean rainfall_alert_default = false;
	final static int RainfallLimit_default = 2500;

	final static boolean rainrate_alert_watch_default = false;
	final static boolean rainrate_alert_warning_default = false;
	final static boolean rainrate_alert_severe_default = false;

	final static String radtype_default = "image";
	final static String SETTINGS_URL_default = "https://example.com/weewx/inigo-settings.txt";
	final static String missingIconURL = "https://odiousapps.com/weewxweatherapp-icon-missing.php";

	final static String webcamFilename = "webcam.jpg";
	final static String debug_filename = "WeeWXApp_Debug.txt.gz";

	final static boolean RadarOnHomeScreen = true;
	final static boolean RadarOnForecastScreen = !RadarOnHomeScreen;
	final static boolean ForecastOnForecastScreen = RadarOnHomeScreen;

	static String[] updateOptions, themeOptions, widgetThemeOptions, updateInterval, webcamRefreshOptions;

	static boolean hasBootedFully = false;

	private static Integer currentTheme = null;

	private final static String charset = StandardCharsets.UTF_8.toString();

	//final static boolean DEBUG = com.odiousapps.weewxweather.BuildConfig.DEBUG;
	final static String VERSION_NAME = com.odiousapps.weewxweather.BuildConfig.VERSION_NAME;
	final static String APPLICATION_ID = com.odiousapps.weewxweather.BuildConfig.APPLICATION_ID;

	static CustomDns customDns = null;

	static File cacheDir = null;

	private final static List<ForecastDefaults> fc_defaults = new ArrayList<>();

	static final int max_alarms = 5;

	private static final String[] alert_channels =
	{
		RAINRATE_ALERT_WATCH,
		RAINRATE_ALERT_WARNING,
		RAINRATE_ALERT_SEVERE,
	};

	static PendingIntent showWeeWXApp;

	ForecastDefaults fcDef = null;

	//AudioAttributes audioAttributes;

	Uri soundUri;

	NotificationManager notificationManager;

	private Context englishContext;

	final SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault());
	SimpleDateFormat sdf2 = new SimpleDateFormat("EEEE d", Locale.getDefault());
	SimpleDateFormat sdf3 = new SimpleDateFormat("h:mm aa d MMMM yyyy", Locale.getDefault());
	final SimpleDateFormat sdf4 = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
	SimpleDateFormat sdf5 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
	final SimpleDateFormat sdf6  = sdf3;
	SimpleDateFormat sdf8 = new SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.getDefault());
	final SimpleDateFormat sdf9 = new SimpleDateFormat("HH:mm d MMMM yyyy", Locale.CANADA_FRENCH);
	SimpleDateFormat sdf10 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
	final SimpleDateFormat sdf11 = new SimpleDateFormat("dd.MM.yyyy' 'HH", Locale.getDefault());
	final SimpleDateFormat sdf12 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
	SimpleDateFormat sdf13 = new SimpleDateFormat("dd MMM yyyy HH:mm:ss.SSS", Locale.getDefault());
	SimpleDateFormat sdf14 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS XXX", Locale.getDefault());
	final SimpleDateFormat sdf15 = sdf10;
	SimpleDateFormat sdf19 = new SimpleDateFormat("h:mm a", Locale.getDefault());
	SimpleDateFormat sdf20 = new SimpleDateFormat("h:mma", Locale.getDefault());
	SimpleDateFormat sdf21 = new SimpleDateFormat("EEEE", Locale.getDefault());
	SimpleDateFormat sdf22 = new SimpleDateFormat("EEE", Locale.getDefault());
	SimpleDateFormat sdf23 = new SimpleDateFormat("MMM yyyy h:mm:ss a", Locale.getDefault());

	@Override
	public void onCreate()
	{
		super.onCreate();

		instance = this;

		Intent intent = new Intent();
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
		showWeeWXApp = PendingIntent.getActivity(this, 0, intent,
			PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

		Log.i("WeeWXApp", "onCreate()");

		cacheDir = getCacheDir();
		customDns = new CustomDns();
		NetworkClient.generate();

		Configuration config = new Configuration(getResources().getConfiguration());
		LocaleListCompat localeList = LocaleListCompat.create(Locale.ENGLISH);
		ConfigurationCompat.setLocales(config, localeList);
		englishContext = createConfigurationContext(config);

		Log.d(LOGTAG, "Attempting to load JSON data from shared prefs...");
		if(KeyValue.parseDicts())
			Log.e(LOGTAG, "Failed to load JSON data from shared prefs...");

		// Create the channel with the custom sound
//		AudioAttributes audioAttributes = new AudioAttributes.Builder()
//				.setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
//				.setUsage(AudioAttributes.USAGE_NOTIFICATION)
//				.build();

		soundUri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + getPackageName() + "/raw/alert.ogg");

		LogMessage("onCreate() soundUri: " + soundUri);

		notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.createNotificationChannelGroup(
			new NotificationChannelGroup(getPackageName(),
				getEnglishAndroidString(R.string.app_name)));

		createNotificationChannel("temperature_alerts",
				getAndroidString(R.string.temperature_alert_str),
				getAndroidString(R.string.temperature_alert_desc),
				NotificationManager.IMPORTANCE_HIGH
		);

		createNotificationChannel("rainfall_alert",
				getAndroidString(R.string.rainfall_alert_str),
				getAndroidString(R.string.rainfall_alert_desc),
				NotificationManager.IMPORTANCE_HIGH
		);

		createNotificationChannel(alert_channels[0],
				getAndroidString(R.string.rainrate_alert_watch_str),
				getAndroidString(R.string.rainrate_alert_watch_desc),
				NotificationManager.IMPORTANCE_HIGH);

		createNotificationChannel(alert_channels[1],
				getAndroidString(R.string.rainrate_alert_warning_str),
				getAndroidString(R.string.rainrate_alert_warning_desc),
				NotificationManager.IMPORTANCE_MAX);

		createNotificationChannel(alert_channels[2],
				getAndroidString(R.string.rainrate_alert_severe_str),
				getAndroidString(R.string.rainrate_alert_severe_desc),
				NotificationManager.IMPORTANCE_MAX
		);

		ForecastDefaults fcdef = new ForecastDefaults();
		fcdef.fctype = "weatherzone2";
		fcdef.default_wait_before_killing_executor = 180;

		fc_defaults.add(fcdef);

		PackageManager pm = getInstance().getPackageManager();

		try
		{
			String[] possibleWebViews = {
					"com.google.android.webview",
					"com.android.webview",
					"com.android.chrome"
			};

			for(String pkg: possibleWebViews)
			{
				try
				{
					LogMessage("Checking for " + pkg);
					PackageInfo info = pm.getPackageInfo(pkg, 0);
					KeyValue.currWebViewVer = info.versionName;
					LogMessage(" version: " + KeyValue.currWebViewVer);
					break;
				} catch(PackageManager.NameNotFoundException ignored) {}
			}

			if(is_blank(KeyValue.currWebViewVer))
			{
				String[] parts = KeyValue.currWebViewVer.split("\\.");
				KeyValue.webview_major_version = str2Int(parts[0]);
			}
		} catch(Exception e) {
			Log.e(WeeWXAppCommon.LOGTAG, ERROR_E + e.getMessage(), e);
			//noinspection CallToPrintStackTrace
			e.printStackTrace();
		}

		// Let's assume no value is actually OK and the package name has changed or something similar...
		if(KeyValue.webview_major_version <= 0)
			KeyValue.webview_major_version = 83;

		if(KeyValue.webview_major_version < 83)
			return;

		updateOptions = new String[]
				{
						getAndroidString(R.string.manual_update),
						getAndroidString(R.string.every_5_minutes),
						getAndroidString(R.string.every_10_minutes),
						getAndroidString(R.string.every_15_minutes),
						getAndroidString(R.string.every_30_minutes),
						getAndroidString(R.string.every_hour),
						getAndroidString(R.string.update_while_running),
						};

		themeOptions = new String[]
				{
						getAndroidString(R.string.light_theme),
						getAndroidString(R.string.dark_theme),
						getAndroidString(R.string.system_default)
				};

		widgetThemeOptions = new String[]
				{
						getAndroidString(R.string.system_default),
						getAndroidString(R.string.match_app),
						getAndroidString(R.string.light_theme),
						getAndroidString(R.string.dark_theme),
						getAndroidString(R.string.custom_setting)
				};

		updateInterval = new String[]
				{
						getAndroidString(R.string.interval_daily),
						getAndroidString(R.string.interval_12h),
						getAndroidString(R.string.interval_6h),
						getAndroidString(R.string.interval_3h),
						getAndroidString(R.string.interval_hourly),
						};

		webcamRefreshOptions = new String[]
				{
						getAndroidString(R.string.manual_update),
						getAndroidString(R.string.every_10_seconds),
						getAndroidString(R.string.every_30_seconds),
						getAndroidString(R.string.every_60_seconds),
						getAndroidString(R.string.every_5_minutes),
						};

		if((boolean)KeyValue.readVar(SAVE_APP_DEBUG_LOGS, save_app_debug_logs_default))
			LogMessage("Debug logging enabled...", true, KeyValue.i);
		else
			LogMessage("Debug logging disabled...", true, KeyValue.i);

		LogMessage("java app_version: " + VERSION_NAME + " starting...", KeyValue.i);

		if(WeeWXAppCommon.fixtypes())
			LogMessage("java successfully converted preference object types...", KeyValue.d);
		else
			LogMessage("java didn't need to convert preference object types...", KeyValue.d);

		colours = new Colours();

		KeyValue.loadYahooRGB2SVGTable();
		KeyValue.fillCounties();

		applyTheme(false);

		LogMessage("java UpdateCheck.runInTheBackground(false, true)");
		UpdateCheck.runInTheBackground();

		updateAboutBlurb();

		KeyValue.countyName = (String)KeyValue.readVar("CountyName", "");
		KeyValue.bomLocation = (String)KeyValue.readVar("bomLocation", "");
		KeyValue.bomGeohash = (String)KeyValue.readVar("bomGeohash", "");

		sdf2  = AppDateFormats.display(instance, "EEEEd");
		sdf3  = AppDateFormats.display(instance, "jmmdMMMMyyyy");
		sdf5 = AppDateFormats.display(instance, "yyyyMMddjmmssSSS");
		sdf8  = AppDateFormats.display(instance, "ddMMMyyyyjmmss");
		sdf10 = AppDateFormats.display(instance, "yyyyMMddjmmss");
		sdf13 = AppDateFormats.display(instance, "ddMMMyyyyjmmssSSS");
		sdf14 = AppDateFormats.displayWithOffset(instance, "yyyyMMddjmmssSSS");
		sdf19 = AppDateFormats.display(instance, "jmm");
		sdf20 = AppDateFormats.display(instance, "jmm");
		sdf21 = AppDateFormats.display(instance, "EEEE");
		sdf22 = AppDateFormats.display(instance, "EEE");
		sdf23 = AppDateFormats.display(instance, "MMMyyyyjmmss");
	}

	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig)
	{
		super.onConfigurationChanged(newConfig);

		int newNightMode = newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK;
		boolean isModeChanged = (lastNightMode != -1 && lastNightMode != newNightMode);

		lastNightMode = newNightMode;

		if(isModeChanged)
		{
			LogMessage("newConfig.uiMode changed, update the theme and mode!", KeyValue.d);
			applyTheme(true);
		}

		instance.fcDef = null;
	}

	static void updateAboutBlurb()
	{
		if(KeyValue.currWebViewVer != null)
		{
			current_about_blurb = about_blurb.replace("WEBVIEWVER", KeyValue.currWebViewVer)
					.replace("APPVERSION", VERSION_NAME);
		}
	}

	static Drawable loadSVGFromAssets(String filename)
	{
		String svgStr = loadFileFromAssets(filename);

		if(is_blank(svgStr))
			return null;

		LogMessage("svgStr: " + svgStr);

		try
		{
			SVG svg = SVG.getFromString(svgStr);
			svg.setDocumentPreserveAspectRatio(PreserveAspectRatio.FULLSCREEN);
			return new PictureDrawable(svg.renderToPicture());
		} catch(Exception e)
		{
			doStackOutput(e);
		}

		return null;
	}

	static Bitmap loadBitmapFromAssets(String filename)
	{
		byte[] imgBytes = loadBinaryFromAssets(filename);

		if(imgBytes == null)
			return null;

		return BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.length);
	}

	static byte[] loadBinaryFromAssets(String filename)
	{
		try
		{
			InputStream is = instance.getAssets().open(filename);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();

			byte[] buffer = new byte[1024];
			int len;

			while((len = is.read(buffer)) != -1)
				baos.write(buffer, 0, len);

			is.close();

			return baos.toByteArray();
		} catch(Exception e)
		{
			doStackOutput(e);
			return null;
		}
	}

	static String loadFileFromAssets(String filename)
	{
		String path = "", nameonly;

		try
		{
			AssetManager assetManager = instance.getAssets();

			if(filename.lastIndexOf('/') >= 0)
			{
				path = filename.substring(0, filename.lastIndexOf('/'));
				nameonly = filename.substring(filename.lastIndexOf('/') + 1);
			} else {
				nameonly = filename;
			}


			String[] files = assetManager.list(path);
			if(files == null || files.length == 0)
				return null;

			boolean exists = false;
			for(String f : files)
			{
				if(f.equals(nameonly))
				{
					exists = true;
					break;
				}
			}

			if(!exists)
			{
				LogMessage("File not found: " + filename, KeyValue.d);
				return null;
			}

			InputStream is = instance.getAssets().open(filename);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();

			byte[] buffer = new byte[1024];
			int len;

			while((len = is.read(buffer)) != -1)
				baos.write(buffer, 0, len);

			is.close();

			return baos.toString(charset);
		} catch(Exception e) {
			doStackOutput(e);
		}

		return null;
	}

	static void applyTheme(boolean forced)
	{
		int theme = (int)KeyValue.readVar("theme", theme_default);
		int mode = mode_default;

		//if(DynamicColors.isDynamicColorAvailable())
		//	DynamicColors.applyToActivitiesIfAvailable(this);

		colours.initOrReinit();

		List<Setting> settings = getDayNightMode();

		for(Setting s: settings)
		{
			if(s.Key.equals("theme"))
				theme = (int)s.Val();

			if(s.Key.equals("mode"))
				mode = (int)s.Val();
		}

		if(theme == R.style.AppTheme_WeeWXApp_Light_Common)
			LogMessage("onCreate() theme: R.style.AppTheme_WeeWXApp_Light_Common");
		else if(theme == R.style.AppTheme_WeeWXApp_Dark_Common)
			LogMessage("onCreate() theme: R.style.AppTheme_WeeWXApp_Dark_Common");
		else if(theme == R.style.AppTheme_WeeWXApp_Common)
			LogMessage("onCreate() theme: R.style.AppTheme_WeeWXApp_Common");
		else
			LogMessage("onCreate() theme: " + theme);

		LogMessage("Setting mode to: " + mode);

		if(AppCompatDelegate.getDefaultNightMode() != mode)
			AppCompatDelegate.setDefaultNightMode(mode);

		if(currentTheme == null || currentTheme != theme)
		{
			currentTheme = theme;
			instance.setTheme(theme);
		}

		String main_css = loadFileFromAssets("main.css");
		if(!is_blank(main_css))
		{
			if(theme == R.style.AppTheme_WeeWXApp_Light_Common)
				main_css = main_css.replace("ARROW_COLOUR", "#fff")
						.replace("ARROW_BG_COLOUR", "#333");
			else
				main_css = main_css.replace("ARROW_COLOUR", "#000000")
						.replace("ARROW_BG_COLOUR", "#CCCCCC");
		}

		current_html_headers = html_header +
							   "<style>\n" +
							   main_css +
							   "\n</style>\n";

		current_dialog_html = dialog_html_header +
							  "<style>\n" +
							  loadFileFromAssets("secondary.css") +
							  "\n</style>\n" +
							  dialog_html_header_rest;

		if(theme == R.style.AppTheme_WeeWXApp_Light_Common)
			inline_arrow = inline_arrow_light;
		else
			inline_arrow = inline_arrow_dark;

		replaceHex6String("BG_HEX", colours.bgColour);
		replaceHex6String("FG_HEX", colours.fgColour);
		if((int)KeyValue.readVar("theme", theme_default) == R.style.AppTheme_WeeWXApp_Dark_Common)
		{
			replaceHex6String("ACCENT_HEX", colours.DarkBlueAccent);
			replaceHex6String("GRAY_HEX", colours.DarkGray);
		} else
		{
			replaceHex6String("ACCENT_HEX", colours.LightBlueAccent);
			replaceHex6String("GRAY_HEX", colours.LightGray);
		}

		String lang = Locale.getDefault().getLanguage();
		if(lang.isBlank())
			lang = "en";

		current_html_headers = replaceHTMLString(current_html_headers, "CURRENT_LANG", lang);
		current_dialog_html = replaceHTMLString(current_dialog_html, "CURRENT_LANG", lang);

		LogMessage("Current app language: " + lang, KeyValue.d);

		if(forced)
		{
			updateNotificationMessage(WeeWXAppCommon.STOP_FORECAST_INTENT);
			updateNotificationMessage(WeeWXAppCommon.STOP_RADAR_INTENT);
			updateNotificationMessage(WeeWXAppCommon.STOP_WEATHER_INTENT);
			updateNotificationMessage(WeeWXAppCommon.STOP_WEBCAM_INTENT);
		}

		LogMessage("Theme should have updated!");

		LogMessage("DayNightMode == " + AppCompatDelegate.getDefaultNightMode());

		updateNotificationMessage(WeeWXAppCommon.REFRESH_DARKMODE_INTENT);
	}

	static void replaceHex6String(String html_tag, int colour)
	{
		String formatting = CPEditText.getFixedChar() + "%06X";
		String hex = String.format(Locale.ENGLISH, formatting, 0xFFFFFF & colour);
		current_html_headers = current_html_headers.replace(html_tag, hex);
	}

	@SuppressWarnings("unused")
	static void replaceHex8String(String html_tag, int colour)
	{
		String hex = CPEditText.getFixedChar() + String.format(Locale.ENGLISH, "%08X", colour);
		current_html_headers = current_html_headers.replace(html_tag, hex);
	}

	static String replaceHTMLString(String base_html, String html_tag, String replacement)
	{
		return base_html.replace(html_tag, replacement);
	}

	static WeeWXApp getInstance()
	{
		return instance;
	}

	static int getHeight()
	{
		return instance.getResources().getConfiguration().screenHeightDp;
	}

	static int getWidth()
	{
		return instance.getResources().getConfiguration().screenWidthDp;
	}

	static boolean isTablet()
	{
		return instance.getResources().getConfiguration().smallestScreenWidthDp >= 600;
	}

	static InputStream openRawResource(int resId)
	{
		return instance.getResources().openRawResource(resId);
	}

	static String getAndroidString(int resId)
	{
		return instance.getString(resId);
	}

	static String getAndroidString(int resId, Object... obj)
	{
		return instance.getString(resId, obj);
	}

	static String getPlural(int resId, int count, Object... obj)
	{
		return instance.getResources().getQuantityString(resId, count, obj);
	}

	static String getEnglishAndroidString(int resId)
	{
		return instance.englishContext.getString(resId);
	}

	static String getEnglishAndroidString(int resId, Object... obj)
	{
		return instance.englishContext.getString(resId, obj);
	}

	static String getEnglishPlural(int resId, int count, Object... obj)
	{
		return instance.englishContext.getResources().getQuantityString(resId, count, obj);
	}

	static int smallestScreenWidth()
	{
		return instance.getResources().getConfiguration().smallestScreenWidthDp;
	}

	static int getUImode()
	{
		return instance.getResources().getConfiguration().uiMode;
	}

	static float getDensity()
	{
		return instance.getResources().getDisplayMetrics().density;
	}

	static int getColour(int resId)
	{
		return instance.getColor(resId);
	}

	static Drawable getAndroidDrawable(int resId)
	{
		return AppCompatResources.getDrawable(instance, resId);
	}

	private static StaticLayout newStaticLayout(String text, TextPaint paint, int width)
	{
		return StaticLayout.Builder
				.obtain(text, 0, text.length(), paint, width)
				.setAlignment(Layout.Alignment.ALIGN_CENTER)
				.setLineSpacing(0f, 1f)
				.setIncludePad(true)
				.build();
	}

	static Bitmap textToBitmap(int resId)
	{
		return textToBitmap(getAndroidString(resId));
	}

	static Bitmap textToBitmap(String text)
	{
		// 1️⃣ Load the reference drawable
		Drawable drawable = getAndroidDrawable(R.drawable.nowebcam);
		if(drawable == null)
			return null;

		int width = drawable.getIntrinsicWidth();
		int height = drawable.getIntrinsicHeight();

		// 2️⃣ Create a new bitmap with same size
		Bitmap bitmap;

		if(!is_blank(text))
		{
			float textSize = height * 0.8f;

			bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
			Canvas canvas = new Canvas(bitmap);
			canvas.drawColor(WeeWXApp.getColours().bgColour);

			// 3️⃣ Prepare the paint
			TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
			paint.setColor(WeeWXApp.getColours().fgColour);
			paint.setTextAlign(Paint.Align.LEFT);
			paint.setTextSize(textSize);

			StaticLayout staticLayout = newStaticLayout(text, paint, width);

			while(textSize > 5f)
			{
				paint.setTextSize(textSize);

				staticLayout = newStaticLayout(text, paint, width);

				if(staticLayout.getHeight() <= height)
					break;

				textSize -= 1f;
			}

			float textY = (height - staticLayout.getHeight()) / 2f;

			// 5️⃣ Draw the text centered
			canvas.save();
			canvas.translate(0, textY);
			staticLayout.draw(canvas);
			canvas.restore();
		} else {
			bitmap = ((BitmapDrawable)drawable).getBitmap();
		}

		return bitmap;
	}

	static Colours getColours()
	{
		return colours;
	}

	static List<Setting> getDayNightMode()
	{
		List<Setting> settings = new ArrayList<>();

		int Black = ContextCompat.getColor(instance, R.color.Black);
		int White = ContextCompat.getColor(instance, R.color.White);

		boolean prefSet = KeyValue.isPrefSet("DayNightMode");

		int current_theme = (int)KeyValue.readVar("theme", theme_default);
		int current_mode = (int)KeyValue.readVar("mode", mode_default);
		int widget_theme_mode = (int)KeyValue.readVar(WIDGET_THEME_MODE, widget_theme_mode_default);
		int current_bgColour = (int)KeyValue.readVar("bgColour", bgColour_default);
		int current_fgColour = (int)KeyValue.readVar("fgColour", fgColour_default);
		int current_widgetBG = (int)KeyValue.readVar("widgetBG", widgetBG_default);
		int current_widgetFG = (int)KeyValue.readVar("widgetFG", widgetFG_default);

		int nightDaySetting = (int)KeyValue.readVar("DayNightMode", DayNightMode_default);
		int nightModeFlags = getUImode() & Configuration.UI_MODE_NIGHT_MASK;

		int theme = current_theme;
		int mode = current_mode;
		int bgColour = current_bgColour;
		int fgColour = current_fgColour;
		int widgetBG = current_widgetBG;
		int widgetFG = current_widgetFG;

		if(prefSet && nightDaySetting == 0)
		{
			LogMessage("Set to Light...");
			theme = R.style.AppTheme_WeeWXApp_Light_Common;
			mode = AppCompatDelegate.MODE_NIGHT_NO;
			bgColour = White;
			fgColour = Black;
		}

		if(prefSet && nightDaySetting == 1)
		{
			LogMessage("Set to Dark...");
			theme = R.style.AppTheme_WeeWXApp_Dark_Common;
			mode = AppCompatDelegate.MODE_NIGHT_NO;
			bgColour = Black;
			fgColour = White;
		}

		if(!prefSet || nightDaySetting == 2)
		{
			if(nightDaySetting == 2 && nightModeFlags == Configuration.UI_MODE_NIGHT_NO)
			{
				LogMessage("Pref not set or set to follow system and dark mode off...");
				theme = R.style.AppTheme_WeeWXApp_Light_Common;
				mode = AppCompatDelegate.MODE_NIGHT_NO;
				bgColour = White;
				fgColour = Black;
			} else
			{
				LogMessage("Pref not set or set to follow system and dark mode on...");
				theme = R.style.AppTheme_WeeWXApp_Dark_Common;
				mode = AppCompatDelegate.MODE_NIGHT_NO;
				bgColour = Black;
				fgColour = White;
			}
		}

		if(!prefSet || widget_theme_mode == 0)
		{
			if(nightModeFlags == Configuration.UI_MODE_NIGHT_NO)
			{
				LogMessage("Pref set to follow system and dark mode isn't on...");
				widgetBG = widgetBG_default;
				widgetFG = widgetFG_default;
			} else
			{
				LogMessage("Pref set to follow system and dark mode is on...");
				widgetBG = widgetBG_default2;
				widgetFG = widgetFG_default2;
			}
		}

		if(prefSet && widget_theme_mode == 1)
		{
			LogMessage("Pref set to follow the app...");
			widgetBG = bgColour;
			widgetFG = fgColour;
		}

		if(prefSet && widget_theme_mode == 2)
		{
			LogMessage("Pref set to Light...");
			widgetBG = widgetBG_default2;
			widgetFG = widgetFG_default2;
		}

		if(prefSet && widget_theme_mode == 3)
		{
			LogMessage("Pref set to Dark...");
			widgetBG = widgetBG_default;
			widgetFG = widgetFG_default;
		}

		if(prefSet && widget_theme_mode == 4)
		{
			LogMessage("Pref set to Custom...");
		}

		KeyValue.theme = theme;
		KeyValue.mode = mode;
		colours.bgColour = bgColour;
		colours.fgColour = fgColour;
		colours.widgetBG = widgetBG;
		colours.widgetFG = widgetFG;

		if(prefSet)
		{
			if(current_theme != theme)
				KeyValue.putVar("theme", theme);

			if(current_mode != mode)
				KeyValue.putVar("mode", mode);

			if(current_bgColour != bgColour)
				KeyValue.putVar("bgColour", bgColour);

			if(current_fgColour != fgColour)
				KeyValue.putVar("fgColour", fgColour);

			if(current_widgetBG != widgetBG)
				KeyValue.putVar("widgetBG", widgetBG);

			if(current_widgetFG != widgetFG)
				KeyValue.putVar("widgetFG", widgetFG);
		}

		settings.add(new Setting("theme", theme));
		settings.add(new Setting("mode", mode));
		settings.add(new Setting(WIDGET_THEME_MODE, widget_theme_mode));

		settings.add(new Setting("bgColour", bgColour));
		settings.add(new Setting("fgColour", fgColour));
		settings.add(new Setting("widgetBG", widgetBG));
		settings.add(new Setting("widgetFG", widgetFG));

		LogMessage("WeeWXApp.getDayNightMode() bgColour: #" + Integer.toHexString(bgColour));
		LogMessage("WeeWXApp.getDayNightMode() fgColour: #" + Integer.toHexString(fgColour));
		LogMessage("WeeWXApp.getDayNightMode() widgetBG: #" + Integer.toHexString(widgetBG));
		LogMessage("WeeWXApp.getDayNightMode() widgetFG: #" + Integer.toHexString(widgetFG));

		return settings;
	}

	static ForecastDefaults getFCdefs(String fctype)
	{
		if(is_blank(fctype))
			return null;

		if(instance.fcDef != null && instance.fcDef.fctype.equalsIgnoreCase(fctype))
			return instance.fcDef;

		for(ForecastDefaults fcdef: fc_defaults)
		{
			if(fcdef.fctype.equalsIgnoreCase(fctype))
			{
				instance.fcDef = fcdef;
				return fcdef;
			}
		}

		ForecastDefaults fcDef = new ForecastDefaults();
		fcDef.fctype = fctype;
		instance.fcDef = fcDef;
		return fcDef;
	}

	@SuppressWarnings("unused")
	static void updateFCdefs(ForecastDefaults newfcDef)
	{
		for(int i = 0; i < fc_defaults.size(); i++)
		{
			ForecastDefaults fcdef = fc_defaults.get(i);
			if(fcdef.fctype.equalsIgnoreCase(newfcDef.fctype))
			{
				instance.fcDef = newfcDef;
				fc_defaults.set(i, newfcDef);
				return;
			}
		}
	}

	private void createNotificationChannel(String id, String name, String description, int importance)
	{

		NotificationChannel channel = notificationManager.getNotificationChannel(name);
		if(channel != null)
			notificationManager.deleteNotificationChannel(id);

		channel = new NotificationChannel(id, name, importance);
		channel.setDescription(description);
		channel.setGroup(getPackageName());
		channel.setSound(null, null);
		channel.enableVibration(false);

		notificationManager.createNotificationChannel(channel);
	}

	static void sendTemperatureAlert(float temperature, float limit, boolean isAfternoon)
	{
		if(ActivityCompat.checkSelfPermission(instance, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && !KeyValue.hasNotificationPerm)
			return;

		boolean metric = (boolean)KeyValue.readVar("metric", WeeWXApp.metric_default);

		String temp = String.format(Locale.ENGLISH, "%.1f", temperature);
		String templimit = String.format(Locale.ENGLISH, "%.1f", limit);

		String unit = metric ? "°C" : "°F";

		int iconID = R.drawable.hot;
		String str = getAndroidString(R.string.morning_alert, temp + unit, templimit + unit);
		if(isAfternoon)
		{
			iconID = R.drawable.cold;
			str = getAndroidString(R.string.afternoon_alert, temp + unit, templimit + unit);
		}

		NotificationCompat.Builder builder = new NotificationCompat.Builder(instance, "temperature_alerts")
			.setContentIntent(showWeeWXApp)
			.setAutoCancel(true)
			.setContentText(str)
			.setContentTitle(getAndroidString(R.string.temperature_alert_str))
			.setPriority(NotificationCompat.PRIORITY_HIGH)
			.setSmallIcon(iconID)
			.setSilent(true);

		instance.notificationManager.notify(1001, builder.build());

		playSound(instance.soundUri);
		vibrate(500);
	}

	static void sendRainfallAlert(float rainfall, float rainfalllimit)
	{
		if(ActivityCompat.checkSelfPermission(instance, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && !KeyValue.hasNotificationPerm)
			return;

		boolean metric = (boolean)KeyValue.readVar("metric", WeeWXApp.metric_default);
		boolean rainInInches = (boolean)KeyValue.readVar("rainInInches", WeeWXApp.rain_in_inches_default);

		String rf = String.format(Locale.ENGLISH, "%.1f", rainfall) + "mm";
		String rfl = String.format(Locale.ENGLISH, "%.1f", rainfalllimit) + "mm";
		if(!metric || rainInInches)
		{
			rf = String.format(Locale.ENGLISH, "%.2f", rainfall) + "in";
			rfl = String.format(Locale.ENGLISH, "%.2f", rainfalllimit) + "in";
		}

		String str = getAndroidString(R.string.rainfall_alert_notification, rf, rfl);
		NotificationCompat.Builder builder = new NotificationCompat.Builder(instance, "rainfall_alert")
			.setContentIntent(showWeeWXApp)
			.setAutoCancel(true)
			.setContentText(str)
			.setContentTitle(getAndroidString(R.string.rainfall_alert_str))
			.setPriority(NotificationCompat.PRIORITY_HIGH)
			.setSmallIcon(R.drawable.rain);

		instance.notificationManager.notify(1002, builder.build());

		playSound(instance.soundUri);
		vibrate(500);
	}

	static void sendRainrateAlert(String rainfall, int level, int timelen, String timelen_unit)
	{
		if(ActivityCompat.checkSelfPermission(instance, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && !KeyValue.hasNotificationPerm)
			return;

		int strid = R.string.rainrate_alert_watch_notification;
		if(level == 1)
			strid = R.string.rainrate_alert_warning_notification;
		else if(level == 2)
			strid = R.string.rainrate_alert_severe_notification;

		String str = getAndroidString(strid, rainfall, timelen, timelen_unit);

		int priority = NotificationCompat.PRIORITY_HIGH;
		if(level > 0)
			priority = NotificationCompat.PRIORITY_MAX;

		NotificationCompat.Builder builder = new NotificationCompat.Builder(instance, alert_channels[level])
			.setContentIntent(showWeeWXApp)
			.setAutoCancel(true)
			.setContentText(str)
			.setContentTitle(getAndroidString(R.string.rainrate_alert_str))
			.setPriority(priority)
			.setSmallIcon(R.drawable.rain);

		instance.notificationManager.notify(1003, builder.build());

		playSound(instance.soundUri);
		vibrate(500);
	}

	private static void vibrate(long milliseconds)
	{
	    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
		{
	        // API 31+ - use VibratorManager
	        VibratorManager vibratorManager = (VibratorManager)instance.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
	        Vibrator vibrator = vibratorManager.getDefaultVibrator();
	        vibrator.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE));

	    } else {
	        // API 26-30 - use VibrationEffect
	        Vibrator vibrator = (Vibrator)instance.getSystemService(Context.VIBRATOR_SERVICE);
	        vibrator.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE));
		}
	}

	private static void playSound(Uri soundUri)
	{
	    Ringtone r = RingtoneManager.getRingtone(instance, soundUri);
	    r.play();
}
}
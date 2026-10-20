package com.odiousapps.weewxweather;

import android.app.Activity;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.icu.text.MessageFormat;
import android.media.MediaScannerConnection;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Xml;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.github.evilbunny2008.xmltojson.XmlToJson;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;

import java.math.BigDecimal;
import java.math.RoundingMode;

import java.nio.charset.StandardCharsets;

import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;
import androidx.core.text.HtmlCompat;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static com.odiousapps.weewxweather.NetworkClient.getInstance;
import static com.odiousapps.weewxweather.WidgetProvider.updateAppWidget;
import static com.odiousapps.weewxweather.WeeWXApp.CONTENT_TYPE;
import static com.odiousapps.weewxweather.WeeWXApp.ENABLE_MQTT;
import static com.odiousapps.weewxweather.WeeWXApp.ERROR_E;
import static com.odiousapps.weewxweather.WeeWXApp.FAILED_TO_CREATE_LOG_FILE_IN_MEDIA_STORE_FILES;
import static com.odiousapps.weewxweather.WeeWXApp.RSS_CHECK;
import static com.odiousapps.weewxweather.WeeWXApp.SAVE_APP_DEBUG_LOGS;
import static com.odiousapps.weewxweather.WeeWXApp.TIME_EXT;
import static com.odiousapps.weewxweather.WeeWXApp.enable_mqtt_default;
import static com.odiousapps.weewxweather.WeeWXApp.getAndroidString;
import static com.odiousapps.weewxweather.WeeWXApp.getEnglishAndroidString;
import static com.odiousapps.weewxweather.WeeWXNotificationManager.updateNotificationMessage;

@SuppressWarnings({"CallToPrintStackTrace", "SequencedCollectionMethodCanBeUsed", "unused"})
class WeeWXAppCommon
{
	private final static String PREFS_NAME = "WeeWxWeatherPrefs";
	final static String LOGTAG = "WeeWXApp";
	static final String MESSAGE = "message='";
	static final int debug_level = KeyValue.i;
	static final boolean debug_html = false;
	final static boolean web_debug_on = false;
	private final static int maxLogLength = 5_000;

	static final String UTF8_BOM = "\uFEFF";

	static final long default_timeout = 15_000L;
	static final long default_webview_timeout = 90_000L;
	static final int maximum_retries = 3;
	static final long retry_sleep_time = 1_000L;
	static Uri logFileUri;
	static final String EXIT_INTENT = "com.odiousapps.weewxweather.EXIT_INTENT";
	static final String INIGO_INTENT = "com.odiousapps.weewxweather.INIGO_UPDATE";
	static final String PROCESSING_ERRORS = "com.odiousapps.weewxweather.PROCESSING_ERRORS";
	static final String FAILED_TO_MERGE = "com.odiousapps.weewxweather.FAILED_TO_MERGE";
	static final String UPDATE_ERRORS = "com.odiousapps.weewxweather.UPDATE_ERRORS";

	static final String UPDATECHECK = "com.odiousapps.weewxweather.UPDATECHECK";

	static final String REFRESH_DARKMODE_INTENT = "com.odiousapps.weewxweather.REFRESH_DARKMODE_INTENT";
	static final String REFRESH_FORECAST_INTENT = "com.odiousapps.weewxweather.REFRESH_FORECAST_INTENT";
	static final String REFRESH_RADAR_INTENT = "com.odiousapps.weewxweather.REFRESH_RADAR_INTENT";
	static final String REFRESH_WEATHER_INTENT = "com.odiousapps.weewxweather.REFRESH_WEATHER_INTENT";
	static final String REFRESH_WEBCAM_INTENT = "com.odiousapps.weewxweather.REFRESH_WEBCAM_INTENT";

	static final String STOP_FORECAST_INTENT = "com.odiousapps.weewxweather.STOP_FORECAST_INTENT";
	static final String STOP_RADAR_INTENT = "com.odiousapps.weewxweather.STOP_RADAR_INTENT";
	static final String STOP_WEATHER_INTENT = "com.odiousapps.weewxweather.STOP_WEATHER_INTENT";
	static final String STOP_WEBCAM_INTENT = "com.odiousapps.weewxweather.STOP_WEBCAM_INTENT";

	static final String WIDGET_THEME_MODE = "widget_theme_mode";

	final static String[] json_labels = {"JSON Data", "JSON Dicts", "JSON Last"};
	final static String[] json_keys = {"json_data", "json_dicts", "json_last"};

	private static final int[] emptyIntArr = new int[]{};
	private static final long[] emptyLongArr = new long[]{};

	private static final BitmapFactory.Options options = new BitmapFactory.Options();

	private static JSONObject nws;

	private static Typeface tf_bold;

	private static final ExecutorService executor = Executors.newFixedThreadPool(5);

	private static final Collection<String> processedMissingIcons;

	record Result(List<Day> days, String desc, long timestamp, boolean isDaily) {}
	record Result2(String[] forecast_text, String desc, int rc) {}
	record Result3(boolean succeeded, String error, Result result) {}
	record TempResult(float CurrTemp, float minObservedTemp, float maxObservedTemp, int[] outTemp_trend_count,
					  int[] outTemp_trend_signal, long[] outTemp_trend_ts) {}
	record NPWSLL(long nowTime, long periodTime, long waitTime, long startTime, long lastStart, long report_time) {}

	static ParallelDownloader downloader;

	static boolean doingSaveSettings = false;
	static StringBuilder saveSettingsLog = new StringBuilder();

	// Period indices
	private static final int PERIOD_10MIN = 0;
	private static final int PERIOD_30MIN = 1;
	private static final int PERIOD_1HR   = 2;
	private static final int PERIOD_6HR   = 3;
	private static final int PERIOD_24HR  = 4;

	// Alert level indices
	private static final int LEVEL_WATCH   = 0;
	private static final int LEVEL_WARNING = 1;
	private static final int LEVEL_SEVERE  = 2;

	private static final int[] levels = {
			LEVEL_SEVERE, LEVEL_WARNING, LEVEL_WATCH
	};

	private static final int[] periods = {
			PERIOD_10MIN, PERIOD_30MIN, PERIOD_1HR, PERIOD_6HR, PERIOD_24HR
	};

	// Thresholds in mm
	private static final int[][] FLOOD_THRESHOLDS_MM = {
		{ 600,   1000,  2000  },  // 10 min
		{ 1500,  2500,  5000  },  // 30 min
		{ 2500,  5000,  10000 },  // 1 hr
		{ 6000,  11000, 20000 },  // 6 hr
		{ 10000, 20000, 30000 }   // 24 hr
	};

	// Thresholds in 1/100ths inch
	private static final int[][] FLOOD_THRESHOLDS_IN = {
		{ 24,  39,  79   },  // 10 min
		{ 59,  98,  197  },  // 30 min
		{ 98,  197, 394  },  // 1 hr
		{ 236, 433, 787  },  // 6 hr
		{ 394, 787, 1181 }   // 24 hr
	};

	private static final int[] warning_delays = {
		600, 1800, 3600, 21600, 86400
	};

	static
	{
		processedMissingIcons = new HashSet<>();
//		if(DEBUG)
//			debug_level = KeyValue.v;

		try
		{
			System.setProperty("http.agent", NetworkClient.UA);
		} catch(Exception e) {
			LogMessage(ERROR_E + e, KeyValue.e);
			doStackOutput(e);
		}

		try
		{
			tf_bold = Typeface.create("sans-serif", Typeface.BOLD);
		} catch(Exception e) {
			LogMessage(ERROR_E + e, KeyValue.e);
			doStackOutput(e);
		}

		options.inJustDecodeBounds = false;
		options.inPreferredConfig = Bitmap.Config.ARGB_8888;
	}

	static void doStackOutput(Throwable t)
	{
		t.printStackTrace();

		for(StackTraceElement element : t.getStackTrace())
		{
			try {
				appendWithMediaStore(element.toString(), KeyValue.e);
			} catch (Exception ignored) {}
		}
	}

	static void LogColour(View view, String name)
	{
		Integer colour = null;

		switch(view)
		{
			case TextInputLayout v ->
			{
				EditText editText = v.getEditText();
				if(editText != null)
					colour = editText.getCurrentTextColor();
			}
			case TextView v -> colour = v.getCurrentTextColor();
			default -> LogMessage("Uncaught view type: " + view, KeyValue.w);
		}

		if(colour != null)
			LogMessage("MainActivity.onCreate() colorSurface of " + name + ": #" + Integer.toHexString(colour));
	}

	static void LogMessage(String text)
	{
		LogMessage(text, false, KeyValue.v);
	}

	static void LogMessage(String text, int level)
	{
		LogMessage(text, false, level);
	}

	static void LogMessage(String text, boolean showAnyway)
	{
		LogMessage(text, showAnyway, KeyValue.i);
	}

	@SuppressWarnings("SameParameterValue")
	static void LogMessage(String text, Throwable t)
	{
		LogMessage(text, KeyValue.e);
		t.printStackTrace();

		for(StackTraceElement element : t.getStackTrace())
		{
			if(doingSaveSettings)
				saveSettingsLog.append(element.toString());

			try {
				appendWithMediaStore(element.toString(), KeyValue.e);
			} catch (Exception ignored) {}
		}
	}

	static void LogMessage(String text, boolean showAnyway, int level)
	{
		Context context = WeeWXApp.getInstance();
		if(context == null)
			return;

		if(is_blank(text))
			return;

		if(doingSaveSettings)
		{
			String timestamp = WeeWXApp.getInstance().sdf13.format(System.currentTimeMillis());
			String tmpStr = timestamp + " " + levelToName(level) + ": " + text.strip() + "\n";
			saveSettingsLog.append(tmpStr);
		}

		if((boolean)KeyValue.readVar(SAVE_APP_DEBUG_LOGS, WeeWXApp.save_app_debug_logs_default))
		{
			try {
				appendWithMediaStore(text, level);
			} catch (Exception ignored) {}
		}

		if(level <= debug_level || showAnyway)
		{
			String textStr = removeWS(text);
			if(textStr.length() > maxLogLength)
				textStr = "[String truncated to " + maxLogLength + " bytes] " + text.substring(0, maxLogLength);

			switch(level)
			{
				case KeyValue.e -> Log.e(LOGTAG, MESSAGE + textStr + "'");
				case KeyValue.w -> Log.w(LOGTAG, MESSAGE + textStr + "'");
				case KeyValue.i -> Log.i(LOGTAG, MESSAGE + textStr + "'");
				case KeyValue.d -> Log.d(LOGTAG, MESSAGE + textStr + "'");
				default -> Log.v(LOGTAG, MESSAGE + textStr + "'");
			}
		} else
			Log.v(LOGTAG, "hidden message='" + text + "'");
	}

	static byte[] gzipToBytes(String text) throws IOException
	{
		if(is_blank(text))
			return null;

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		GZIPOutputStream gzip = new GZIPOutputStream(baos);
		gzip.write(text.getBytes(StandardCharsets.UTF_8));
		gzip.close(); // flush + finish

		return baos.toByteArray();
	}

	static String levelToName(int level)
	{
		return switch(level)
		{
			case KeyValue.e -> "ERROR";
			case KeyValue.w -> "WARN";
			case KeyValue.i -> "INFO";
			case KeyValue.d -> "DEBUG";
			case KeyValue.v -> "VERBOSE";
			default -> "INVALID";
		};
	}

	static String outputSaveSettings()
	{
		if(saveSettingsLog.toString().isBlank() || !doingSaveSettings)
			return null;

		return outputSaveSettingsWithMediaStore();
	}

	private static String outputSaveSettingsWithMediaStore()
	{
		Context context = WeeWXApp.getInstance();
		if(context == null)
			return "Context is null";

		String folderName = Environment.DIRECTORY_DOWNLOADS + "/WeeWX/";

		Uri filesCollection = MediaStore.Files.getContentUri("external");

		String filename = "SaveSettings_" + System.currentTimeMillis() + ".txt.gz";
		ContentValues values = new ContentValues();
		values.put(MediaStore.Files.FileColumns.DISPLAY_NAME, filename);
		values.put(MediaStore.Files.FileColumns.MIME_TYPE, "application/gzip");
		values.put(MediaStore.Files.FileColumns.RELATIVE_PATH, folderName);

		Uri fileUri = context.getContentResolver().insert(filesCollection, values);
		if(fileUri == null)
		{
			doingSaveSettings = false;
			return "Unable to resolve fileUri";
		}

		try(OutputStream os = context.getContentResolver().openOutputStream(fileUri, "w"))
		{
			if(os == null)
				return "OutputStream is null";

			os.write(gzipToBytes(saveSettingsLog.toString()));
			doingSaveSettings = false;
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			return "Failed to save Settings log";
		}
	}

	private static String outputLegacySaveSettings()
	{
		try
		{
			String filename = "SaveSettings_" + System.currentTimeMillis() + ".txt.gz";
			File file = getExtFile(filename);
			FileOutputStream fos = new FileOutputStream(file, true);
			fos.write(gzipToBytes(saveSettingsLog.toString()));
			fos.close();

			doingSaveSettings = false;

			publish(file);

			return null;
		} catch (IOException e) {
			e.printStackTrace();
			return "Failed to save Settings log";
		}
	}

	@SuppressWarnings("ConstantValue")
	private static void appendLegacy(String text)
	{
		try
		{
			File file = getExtFile(WeeWXApp.debug_filename);
			boolean needsPublishing = !file.exists();
			FileOutputStream fos = new FileOutputStream(file, true);

			if(WeeWXApp.debug_filename.endsWith(".gz"))
				fos.write(gzipToBytes(text));
			else if(WeeWXApp.debug_filename.endsWith(".txt"))
				fos.write(text.getBytes(StandardCharsets.UTF_8));

			fos.close();

			if(needsPublishing)
				publish(file);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings("ConstantValue")
	private static void appendWithMediaStore(String text, int level) throws IOException
	{
		Context context = WeeWXApp.getInstance();
		if(context == null)
			return;

		String folderName = Environment.DIRECTORY_DOWNLOADS + "/WeeWX/";

		// ================
		// 1. Ensure WeeWX folder exists
		// ================
		Uri filesCollection = MediaStore.Files.getContentUri("external");

		// --- 1. Verify that cached URI still exists ---
		if(logFileUri != null)
		{
			try(Cursor cursor = context.getContentResolver().query(
					logFileUri,
					new String[]{ MediaStore.Files.FileColumns._ID },
					null, null, null))
			{
				if(cursor == null || !cursor.moveToFirst())
				{
					// The file was deleted -> reset cached URI
					logFileUri = null;
				}
			} catch (Exception e) {
				// If any error occurs, assume file is gone
				logFileUri = null;
			}
		}

		// --- 2. If logFileUri is null, attempt to find or create it ---
		if(logFileUri == null)
		{
			// First try to find it
			String[] projection = { MediaStore.Files.FileColumns._ID };
			String selection = MediaStore.Files.FileColumns.DISPLAY_NAME + "=? AND " +
							   MediaStore.Files.FileColumns.RELATIVE_PATH + "=?";
			String[] args = { WeeWXApp.debug_filename, folderName };

			try(Cursor cursor = context.getContentResolver().query(filesCollection, projection, selection, args, null))
			{
				if(cursor != null && cursor.moveToFirst())
				{
					long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID));
					logFileUri = ContentUris.withAppendedId(filesCollection, id);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

			// If not found, create it
			if(logFileUri == null && WeeWXApp.debug_filename.endsWith(".gz"))
			{
				ContentValues values = new ContentValues();
				values.put(MediaStore.Files.FileColumns.DISPLAY_NAME, WeeWXApp.debug_filename);
				values.put(MediaStore.Files.FileColumns.MIME_TYPE, "application/gzip");
				values.put(MediaStore.Files.FileColumns.RELATIVE_PATH, folderName);

				logFileUri = context.getContentResolver().insert(filesCollection, values);
				if(logFileUri == null)
					throw new IOException(FAILED_TO_CREATE_LOG_FILE_IN_MEDIA_STORE_FILES);
			} else if(logFileUri == null && WeeWXApp.debug_filename.endsWith(".txt")) {
				ContentValues values = new ContentValues();
				values.put(MediaStore.Files.FileColumns.DISPLAY_NAME, WeeWXApp.debug_filename);
				values.put(MediaStore.Files.FileColumns.MIME_TYPE, CONTENT_TYPE);
				values.put(MediaStore.Files.FileColumns.RELATIVE_PATH, folderName);

				logFileUri = context.getContentResolver().insert(filesCollection, values);
				if(logFileUri == null)
				{
					String warning = FAILED_TO_CREATE_LOG_FILE_IN_MEDIA_STORE_FILES;
					Log.w(LOGTAG, warning);
					throw new IOException(warning);
				}
			}
		}

		// ================
		// 3. Append text to the log file
		// ================
		String timestamp = WeeWXApp.getInstance().sdf13.format(System.currentTimeMillis());

		String tmpStr = timestamp + " " + levelToName(level) + ": " + text.strip() + "\n";

		try(OutputStream os = context.getContentResolver().openOutputStream(logFileUri, "wa"))
		{
			if(os == null)
				return;

			if(WeeWXApp.debug_filename.endsWith(".gz"))
				os.write(gzipToBytes(tmpStr));
			else
				os.write(tmpStr.getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static SharedPreferences getPrefSettings()
	{
		return WeeWXApp.getInstance().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
	}

	static void setVar(String string, Object val)
	{
		if(val == null)
		{
			if(isPrefSet(string))
				RemovePref(string);

			return;
		}

		switch(val)
		{
			case Boolean aBoolean ->
			{
				try
				{
					getPrefSettings().edit().putBoolean(string, aBoolean).apply();
				} catch(Exception ignored) {}
			}
			case Float f ->
			{
				try
				{
					getPrefSettings().edit().putFloat(string, f).apply();
				} catch(Exception ignored) {}
			}
			case Integer i ->
			{
				try
				{
					getPrefSettings().edit().putInt(string, i).apply();
				} catch(Exception ignored) {}
			}
			case Long l ->
			{
				try
				{
					getPrefSettings().edit().putLong(string, l).apply();
				} catch(Exception ignored) {}
			}
			case String s ->
			{
				try
				{
					getPrefSettings().edit().putString(string, s).apply();
				} catch(Exception ignored) {}
			}
			default ->
			{
			}
		}
	}

	@Nullable
	static Object readVar(String string, Object defVal)
	{
		if(is_blank(string))
			return null;

		if(!isPrefSet(string))
			return defVal;

		if(defVal == null)
			defVal = "";

		if(defVal instanceof Boolean)
		{
			try
			{
				return getPrefSettings().getBoolean(string, (boolean)defVal);
			} catch(Exception ignored) {}
		}

		if(defVal instanceof Integer)
		{
			try
			{
				return getPrefSettings().getInt(string, (int)defVal);
			} catch(Exception ignored) {}
		}

		if(defVal instanceof Float)
		{
			try
			{
				return getPrefSettings().getFloat(string, (float)defVal);
			} catch(Exception ignored) {}
		}

		if(defVal instanceof Long)
		{
			try
			{
				return getPrefSettings().getLong(string, (long)defVal);
			} catch(Exception ignored) {}
		}

		if(defVal instanceof String)
		{
			try
			{
				return getPrefSettings().getString(string, (String)defVal);
			} catch(Exception ignored) {}
		}

		return null;
	}

	static void RemovePref(String name)
	{
		SharedPreferences.Editor editor = getPrefSettings().edit();
		editor.putString(name, "");
		editor.remove(name);
		editor.apply();
	}

	static boolean isPrefSet(String name)
	{
		return getPrefSettings().contains(name);
	}

	static boolean fixtypes()
	{
		SharedPreferences prefs = getPrefSettings();
		if(prefs == null)
			return false;

		Map<String, ?> all = prefs.getAll();
		SharedPreferences.Editor editor = prefs.edit();
		boolean changed = false;

		for(Map.Entry<String, ?> entry: all.entrySet())
		{
			String key = entry.getKey();
			Object value = entry.getValue();

			if(!(value instanceof String))
				continue; // only convert strings

			String s = ((String)value).strip();
			Number parsed = parseNumber(s);

			if(parsed == null)
				continue; // not numeric

			// Choose the best numeric type
			Object newValue = compressNumber(parsed);

			// If re-writing as exact same type/string, skip
			if(value.equals(newValue))
				continue;

			// Persist as correct numeric type
			switch(newValue)
			{
				case Integer i -> editor.putInt(key, i);
				case Long l -> editor.putLong(key, l);
				case Float v -> editor.putFloat(key, v);
				case Double v -> editor.putString(key, Double.toString(v));
				case null, default ->
				{
					continue;
				}
			}

			changed = true;
		}

		if(changed)
			editor.apply();

		return changed;
	}

	@Nullable
	static Number parseNumber(String s)
	{
		if(s == null || s.isEmpty())
			return null;

		try
		{
			if(s.contains("."))
				return Double.parseDouble(s);
			else
				return Long.parseLong(s);
		} catch (Exception e) {
			return null;
		}
	}

	static Object compressNumber(Number number)
	{
		if(number instanceof Long l)
		{
			if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE)
				return l.intValue();

			return l;
		} else if(number instanceof Double d) {
			float f = d.floatValue();

			// If float is lossless, use float
			if(f == d)
				return f;

			// Otherwise keep double, stored as string
			return d;
		}

		return number;
	}

	static long getRSSms()
	{
		LogMessage("Checking for var named 'rssCheck'");

		if(!KeyValue.isPrefSet(RSS_CHECK))
			return 0L;

		long rssTime = (long)KeyValue.readVar(RSS_CHECK, 0L);
		if(rssTime == 0)
			return 0L;

		LogMessage("getRSSms() Before: rssTime: " + rssTime);

		while(rssTime < 10_000_000_000L)
			rssTime *= 1_000L;

		LogMessage("getRSSms() After: rssTime: " + rssTime);

		return rssTime;
	}

	static long getLDTms()
	{
		LogMessage("Checking for var named '" + json_keys[0] + TIME_EXT + "'");

		if(!isPrefSet(json_keys[0] + TIME_EXT))
			return 0L;

		long lastDownloadTime = (long)KeyValue.readVar(json_keys[0] + TIME_EXT, 0L);
		if(lastDownloadTime == 0)
			return 0L;

		LogMessage("getLDTms() Before: lastDownloadTime: " + lastDownloadTime);

		while(lastDownloadTime < 10_000_000_000L)
			lastDownloadTime *= 1_000L;

		LogMessage("getLDTms() After: lastDownloadTime: " + lastDownloadTime);

		return lastDownloadTime;
	}

	static long getLAFDms()
	{
		//LogMessage("Checking for var named 'lastAttemptedForecastDownloadTime'");

		if(!isPrefSet("lastAttemptedForecastDownloadTime"))
			return 0L;

		long lastAttemptedForecastDownloadTime = (long)KeyValue.readVar("lastAttemptedForecastDownloadTime", 0L);
		while(lastAttemptedForecastDownloadTime < 10_000_000_000L)
			lastAttemptedForecastDownloadTime *= 1_000L;

		return lastAttemptedForecastDownloadTime;
	}

	private static Day getFirstDay(List<Day> days)
	{
		Day first = null;
		if(days != null && !days.isEmpty())
		{
			Iterator<Day> it = days.iterator();
			if(it.hasNext())
				first = it.next();
		}

		return first;
	}

	static String headingTime(long when)
	{
		return WeeWXApp.getInstance().sdf22.format(when) + " " + getTimeMonth(when) +
				" " + WeeWXApp.getInstance().sdf23.format(when);
	}

	static String generateForecast(List<Day> days, long timestamp, boolean showHeader, boolean daily)
	{
		LogMessage("Starting generateForecast()");
//		LogMessage("days: " + days);

		if(days == null || days.isEmpty())
		{
			LogMessage("generateForecast() Was sent an empty days variable, skipping...", true, KeyValue.w);
			return null;
		}

		Calendar cal = Calendar.getInstance();

		StringBuilder sb = new StringBuilder();
		int start = 0;

		boolean noaa = !is_blank(days.get(0).periodName);
		sb.append("\n<div class='header'>")
				.append(noaa ? new SimpleDateFormat("EEE MMM d, h:mm a", Locale.getDefault()).format(new Date(timestamp)) : headingTime(timestamp))
				.append("</div>\n\n");

		if(showHeader)
		{
			start = 1;
			Day first = getFirstDay(days);

			sb.append(noaa ? "<div class='today noaaToday'>\n\t<div class='topRow'>\n" : "<div class='today'>\n\t<div class='topRow'>\n");

			if(!is_blank(first.icon))
			{
				sb.append("\t\t<div class='iconBig'>\n");

				if(!first.icon.startsWith("file:///") && !first.icon.startsWith("data:image"))
					sb.append("\t\t\t").append(cssToSVG(first.icon)).append("\n");
				else
					sb.append("\t\t\t<img style='height:100px;width:auto;' alt='weather icon' src='").append(removeWS(first.icon)).append("' />\n");

				sb.append("\t\t</div>\n");
			} else {
				sb.append(WeeWXApp.currentSpacer);
			}

			sb.append("\t\t<div class='wordToday'>").append(noaa && !is_blank(first.periodName) ? first.periodName : getAndroidString(R.string.today)).append("</div>\n");

			sb.append("\t\t<div class='bigTemp'>\n");

			sb.append("\t\t\t<div class='maxTemp'>");
			if(is_blank(first.max) || first.max.equals("&deg;C") || first.max.equals("&deg;F"))
				sb.append(WeeWXApp.emptyField);
			else
				sb.append(first.max);
			sb.append("</div>\n");

			sb.append("\t\t\t<div class='minTemp'>");
			if(is_blank(first.min) || first.min.equals("&deg;C") || first.min.equals("&deg;F"))
				sb.append(WeeWXApp.emptyField);
			else
				sb.append(first.min);
			sb.append("</div>\n\t\t</div>\n\t</div>\n");

			sb.append("\t<div class='bigForecastText'>\n\t\t");
			sb.append(first.text);
			sb.append("\n\t</div>\n</div>\n\n");

			sb.append("<hr />\n\n");
		}

		sb.append(noaa ? "<div class='forecast noaaForecast'>\n\n" : "<div class='forecast'>\n\n");

		for(int i = start; i < days.size(); i++)
		{
			Day day = days.get(i);

			sb.append(noaa ? "\t<div class='day noaaDay'>\n" : "\t<div class='day'>\n");

			// The icon is floated ahead of the top row so the title, temps and
			// description sit beside it and longer text wraps underneath it
			if(!is_blank(day.icon))
			{
				sb.append("\t\t<div class='iconSmall'>\n");

				if(!day.icon.startsWith("file:///") && !day.icon.startsWith("data:image"))
					sb.append("\t\t\t").append(cssToSVG(day.icon)).append("\n");
				else
					sb.append("\t\t\t<img style='height:75px;width:auto;' alt='weather icon' src='")
							.append(removeWS(day.icon))
							.append("' />\n");

				sb.append("\t\t</div>\n");
			}

			sb.append("\t\t<div class='dayTopRow'>\n");

			if(is_blank(day.icon))
				sb.append(WeeWXApp.currentSpacer);

			sb.append("\t\t\t<div class='dayTitle'>");

			if(noaa && !is_blank(day.periodName))
				sb.append(day.periodName);
			else if(daily)
			{
				if(i == 0)
					sb.append(getAndroidString(R.string.today));
				else
					sb.append(WeeWXApp.getInstance().sdf21.format(day.timestamp))
							.append(" ").append(getTimeMonth(day.timestamp));
			} else {
				cal.setTimeInMillis(day.timestamp);
				int hour = cal.get(Calendar.HOUR_OF_DAY);
				// A midnight entry straight after its daily summary shows its time
				if(day.isSummary || (hour == 0 && (i == 0 || !days.get(i - 1).isSummary)))
					sb.append(WeeWXApp.getInstance().sdf21.format(day.timestamp))
							.append(" ").append(getTimeMonth(day.timestamp));
				else
					sb.append(WeeWXApp.getInstance().sdf22.format(day.timestamp))
							.append(" ").append(getTimeMonth(day.timestamp))
							.append(", ").append(WeeWXApp.getInstance().sdf19.format(day.timestamp));
			}

			sb.append("</div>\n");

			sb.append("\t\t\t<div class='smallTemp'>\n");
			sb.append("\t\t\t\t<div class='forecastMax'>");
			if(day.max.equals("&deg;C") || day.max.equals("&deg;F"))
				sb.append("N/A");
			else
				sb.append(day.max);
			sb.append("</div>\n");

			sb.append("\t\t\t\t<div class='forecastMin'>");
			if(day.min.equals("&deg;C") || day.min.equals("&deg;F"))
				sb.append("N/A");
			else
				sb.append(day.min);
			sb.append("</div>\n\t\t\t</div>\n\t\t</div>\n");

			sb.append("\t\t<div class='desc'>\n\t\t\t");
			sb.append(day.text).append("\n\t\t</div>\n");

			if(i < days.size() - 1)
				sb.append("\t\t<hr />\n");

			sb.append("\t</div>\n\n");
		}

		if((days.size() - start) % 2 == 1)
			sb.append("<div class='day'></div>\n\n");

		sb.append("</div>\n");

		return sb.toString();
	}

	static void updateCacheTime(long timestamp)
	{
		LogMessage("WeeWXAppCommon.updateCacheTime(" + timestamp + ")");

		if(timestamp <= 0)
			return;

		while(timestamp < 10_000_000_000L)
			timestamp *= 1_000L;

		long rssCheck = getRSSms();

		LogMessage("rssCheck: " + rssCheck);
		LogMessage("timestamp: " + timestamp);

		if(timestamp != rssCheck)
			KeyValue.putVar(RSS_CHECK, timestamp);
	}

	static Day getDayByDate(List<Day> days, long timestamp)
	{
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(timestamp);

		Calendar cal1 = Calendar.getInstance();

		for(int i = 0; i < days.size(); i++)
		{
			Day day = days.get(i);

			cal1.setTimeInMillis(day.timestamp);

			if(cal.get(Calendar.YEAR) == cal1.get(Calendar.YEAR) &&
			   cal.get(Calendar.DAY_OF_YEAR) == cal1.get(Calendar.DAY_OF_YEAR))
				return day;
		}

		return null;
	}

	static Result processBoM3(int modhour, String data, String url) throws IOException
	{
		LogMessage("processBoM3()");

		long now = System.currentTimeMillis();

		Result r1 = null;
		long bomDailyLastUpdate = (long)KeyValue.readVar("bomDailyLastUpdate", 0L);
		if(now - bomDailyLastUpdate < 10_800_000L)
		{
			String bomDailyGson = (String)KeyValue.readVar("bomDailyGson", "");
			GsonHelper gh = new GsonHelper(bomDailyGson);
			if(gh.days != null && !gh.days.isEmpty())
				r1 = new Result(gh.days, gh.desc, gh.timestamp, gh.isDaily);
		}

		if(r1 == null)
		{
			String url2 = url.replace("/hourly", "/daily");

			String fcdata = "";
			if(!is_blank(url2) && !url2.equals(url))
			{
				LogMessage("processBoM3(): Getting BoM daily");
				fcdata = downloadString(url2);
			}

			if(!is_blank(fcdata))
			{
				LogMessage("processBoM3(): Processing BoM daily");
				r1 = processBoM3Daily(fcdata, false);
				if(r1 != null)
				{
					GsonHelper gh = new GsonHelper(r1.days(), r1.desc(),
						r1.timestamp() > 0 ? r1.timestamp() : System.currentTimeMillis(), r1.isDaily());
					String forecastGson = gh.toString();

					KeyValue.putVar("bomDailyGson", forecastGson);
					KeyValue.putVar("bomDailyLastUpdate", gh.timestamp);
				}
			}
		}

		Calendar cal = Calendar.getInstance();
		Calendar today = Calendar.getInstance();

		LogMessage("processBoM3(): Processing BoM hourly");
		Result r2 = processBoM3Hourly(data);

		if(r1 == null || r1.days.isEmpty())
			return r2;

		if(r2 == null)
			return null;

		LogMessage("processBoM3(): Merging BoM daily + hourly");

		long lasttimestamp = 0;
		boolean not_set_text_yet = false;
		List<Day> merged = new ArrayList<>();
		for(Day day : r2.days)
		{
			merged.add(day);
			cal.setTimeInMillis(day.timestamp);
			int hour = cal.get(Calendar.HOUR_OF_DAY);

			lasttimestamp = day.timestamp;

			boolean isToday = cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
						   cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);

			Day r1day = getDayByDate(r1.days, day.timestamp);
			if(r1day == null)
				continue;

			boolean firstToday = isToday && hour % modhour == 0 && !not_set_text_yet;
			if(firstToday || hour == 0)
			{
				if(isToday)
					not_set_text_yet = true;

				// Insert a separate summary entry ahead of the hourly one
				Day summary = new Day();
				summary.timestamp = day.timestamp;
				summary.icon = is_blank(r1day.icon) ? day.icon : r1day.icon;
				summary.text = r1day.text;
				summary.min = r1day.min;
				summary.max = r1day.max;
				summary.isSummary = true;
				merged.add(merged.size() - 1, summary);
			}
		}

		r2.days.clear();
		r2.days.addAll(merged);

		for(int i = 0; i < r1.days.size(); i++)
		{
			Day r1day = r1.days.get(i);
			if(r1day.timestamp < lasttimestamp)
				continue;

			r2.days.add(r1day);
		}

		LogMessage("processBoM3(): Successfully merged BoM daily + hourly");

		return r2;
	}

	// Appends BoM's forecast rain amount and chance to day.text.
	// Dry periods come through as min 0 and max null, nothing is added for those.
	static void addBoMRain(Day day, JSONObject entry, boolean mmRain)
	{
		JSONObject rain = entry.optJSONObject("rain");
		if(rain == null)
			return;

		JSONObject amount = rain.optJSONObject("amount");
		if(amount != null && !amount.isNull("min"))
		{
			double minRain = amount.optDouble("min", 0);
			double maxRain = amount.isNull("max") ? minRain : amount.optDouble("max", minRain);
			if(maxRain <= 0)
				return;

			day.minRain = formatBoMRain(minRain, mmRain);
			day.maxRain = formatBoMRain(maxRain, mmRain);
		}

		String extraText = "";
		if(!is_blank(day.minRain))
		{
			extraText = "Rain: " + day.minRain;
			if(!day.maxRain.equals(day.minRain))
				extraText += "-" + day.maxRain;
		}

		if(!rain.isNull("chance"))
		{
			if(!extraText.isEmpty())
				extraText += ", ";

			extraText += "Chance: " + rain.optInt("chance") + "%";
		}

		if(extraText.isEmpty())
			return;

		if(is_blank(day.text))
			day.text = extraText;
		else
			day.text += "<br>\n<br>\n" + extraText;
	}

	private static String formatBoMRain(double mm, boolean mmRain)
	{
		if(mmRain)
			return (mm == Math.rint(mm) ? String.valueOf((long)mm) : String.format(Locale.ENGLISH, "%.1f", mm)) + "mm";

		return String.format(Locale.ENGLISH, "%.2f", mm2in((float)mm)) + "in";
	}

	static Result processBoM3Hourly(String data)
	{
		String missing = null;

		LogMessage("processBoM3Hourly(): Starting processBoM3Hourly()");

		if(is_blank(data))
		{
			LogMessage("processBoM3Hourly(): data is blank, skipping...", true, KeyValue.w);
			return null;
		}

		//LogMessage("data: " + data);

		boolean metric = (boolean)KeyValue.readVar("metric", WeeWXApp.metric_default);
		boolean rainInInches = (boolean)KeyValue.readVar("rainInInches", WeeWXApp.rain_in_inches_default);

		String desc = KeyValue.bomLocation;
		LogMessage("processBoM3Hourly(): desc: " + desc);

		List<Day> days = new ArrayList<>();
		long timestamp = 0;

		try
		{
			JSONObject jobj = new JSONObject(data);

			String tmp = jobj.getJSONObject("metadata").getString("issue_time");

			Date df = WeeWXApp.getInstance().sdf1.parse(tmp);
			if(df != null)
				timestamp = df.getTime();

			if(timestamp <= 0)
				timestamp = System.currentTimeMillis();

			if(timestamp > 0)
				updateCacheTime(timestamp);

			Date date = new Date(timestamp);
			LogMessage("Last updated forecast: " + WeeWXApp.getInstance().sdf5.format(date));

			JSONArray myhours = jobj.getJSONArray("data");
			for(int i = 0; i < myhours.length(); i++)
			{
				Day day = new Day();

				day.timestamp = 0;
				df = WeeWXApp.getInstance().sdf1.parse(myhours.getJSONObject(i).getString("time"));
				if(df != null)
					day.timestamp = df.getTime();

				if(metric)
				{
					try
					{
						day.max = myhours.getJSONObject(i).getInt("temp") + "&deg;C";
					} catch(Exception ignored) {}

					try
					{
						day.min = "AT: " + myhours.getJSONObject(i).getInt("temp_feels_like") + "&deg;C";
					} catch(Exception ignored) {}
				} else {
					try
					{
						day.max = C2Fdeground(myhours.getJSONObject(i).getInt("temp"));
					} catch(Exception ignored) {}

					try
					{
						day.min = "AT: " + C2Fdeground(myhours.getJSONObject(i).getInt("temp_feels_like"));
					} catch(Exception ignored) {}
				}

				addBoMRain(day, myhours.getJSONObject(i), metric && !rainInInches);

				String fileName = bomlookup(myhours.getJSONObject(i).getString("icon_descriptor"));
				if(!is_blank(fileName))
				{
					day.icon = "icons/bom/" + fileName + ".svg";
					if(!day.icon.equals("icons/bom/null.svg"))
					{
						String content = WeeWXApp.loadFileFromAssets(day.icon);
						if(!is_blank(content))
						{
							missing = null;
							day.icon = "file:///android_asset/" + day.icon;
						} else {
							missing = day.icon;
							day.icon = null;
						}
					}
				}

				if(missing != null && !missing.equals("icons/bom/null.svg"))
				{
					String forecaseURL = (String)KeyValue.readVar("FORECAST_URL", "");

					LogMessage("Unable to locate SVG: " + missing, KeyValue.d);

					uploadMissingIcon(Map.of(
							"svgName", missing,
							"svgURL", forecaseURL)
					);
				}

				days.add(day);
			}
		} catch(Exception e) {
			LogMessage(ERROR_E + e, KeyValue.e);
			doStackOutput(e);
			Activity act = getActivity();
			if(act == null)
				return null;

			act.finish();
		}

		return new Result(days, desc, timestamp, false);
	}

	static Result processBoM3Daily(String data, boolean updateCTime)
	{
		String missing = null;

		LogMessage("Starting processBoM3Daily()");

		if(is_blank(data))
		{
			LogMessage("processBoM3Daily() data is blank, skipping...", true, KeyValue.w);
			return null;
		}

		//LogMessage("data: " + data);

		boolean metric = (boolean)KeyValue.readVar("metric", WeeWXApp.metric_default);
		boolean rainInInches = (boolean)KeyValue.readVar("rainInInches", WeeWXApp.rain_in_inches_default);

		String desc = KeyValue.bomLocation;
		List<Day> days = new ArrayList<>();
		long timestamp = 0;

		try
		{
			JSONObject jobj = new JSONObject(data);

			//desc = jobj.getJSONObject("metadata").getString("forecast_region");
			String tmp = jobj.getJSONObject("metadata").getString("issue_time");

			Date df = WeeWXApp.getInstance().sdf1.parse(tmp);
			if(df != null)
				timestamp = df.getTime();

			if(timestamp <= 0)
				timestamp = System.currentTimeMillis();

			if(timestamp > 0 && updateCTime)
				updateCacheTime(timestamp);

			Date date = new Date(timestamp);
			LogMessage("Last updated forecast: " + WeeWXApp.getInstance().sdf5.format(date));

			JSONArray mydays = jobj.getJSONArray("data");
			for(int i = 0; i < mydays.length(); i++)
			{
				Day day = new Day();

				day.timestamp = 0;
				df = WeeWXApp.getInstance().sdf1.parse(mydays.getJSONObject(i).getString("date"));
				if(df != null)
					day.timestamp = df.getTime();

				if(metric)
				{
					try
					{
						day.max = mydays.getJSONObject(i).getInt("temp_max") + "&deg;C";
					} catch(Exception ignored) {}

					try
					{
						day.min = mydays.getJSONObject(i).getInt("temp_min") + "&deg;C";
					} catch(Exception ignored) {}
				} else {
					try
					{
						day.max = C2Fdeground(mydays.getJSONObject(i).getInt("temp_max"));
					} catch(Exception ignored) {}

					try
					{
						day.min = C2Fdeground(mydays.getJSONObject(i).getInt("temp_min"));
					} catch(Exception ignored) {}
				}

				if(!mydays.getJSONObject(i).isNull("extended_text"))
					day.text = mydays.getJSONObject(i).getString("extended_text");

				addBoMRain(day, mydays.getJSONObject(i), metric && !rainInInches);

				String fileName = bomlookup(mydays.getJSONObject(i).getString("icon_descriptor"));
				if(!is_blank(fileName))
				{
					day.icon = "icons/bom/" + fileName + ".svg";

					String content = WeeWXApp.loadFileFromAssets(day.icon);
					if(!is_blank(content))
					{
						missing = null;
						day.icon = "file:///android_asset/" + day.icon;
					} else {
						missing = day.icon;
						day.icon = null;
					}
				}

				if(missing != null)
				{
					String forecaseURL = (String)KeyValue.readVar("FORECAST_URL", "");

					LogMessage("Unable to locate SVG: " + missing, KeyValue.d);

					uploadMissingIcon(Map.of(
							"svgName", missing,
							"svgURL", forecaseURL)
					);
				}

				if(!is_blank(day.icon))
					days.add(day);
			}
		} catch(Exception e) {
			LogMessage(ERROR_E + e, KeyValue.e);
			doStackOutput(e);
			Activity act = getActivity();
			if(act == null)
				return null;

			act.finish();
		}

		return new Result(days, desc, timestamp, true);
	}

	private static String bomlookup(String icon)
	{
		icon = icon.replace("-", "_");

		return switch(icon)
		{
			case "dusty" -> "dust";
			case "hazy" -> "haze";
			case "light_shower" -> "light_showers";
			case "mostly_sunny" -> "partly_cloudy";
			case "shower" -> "showers";
			case "storm" -> "storms";
			case "windy" -> "wind";
			default -> icon;
		};

//		LogMessage("BoM Old Icon: " + icon, KeyValue.d);
//		LogMessage("BoM New Icon: " + newicon, KeyValue.d);
//		return newicon;
	}

	static Result processMET(String data)
	{
		if(is_blank(data))
			return null;

		boolean metric = (boolean)KeyValue.readVar("metric", WeeWXApp.metric_default);
		long timestamp = getRSSms();

		String desc;
		List<Day> days = new ArrayList<>();

		desc = data.split("<title>", 2)[1].split(" weather - Met Office</title>",2)[0].strip();

		String[] forecasts = data.split("<ul id='dayNav'", 2)[1].split("</ul>", 2)[0].split("<li");
		for(int i = 1; i < forecasts.length; i++)
		{
			Day day = new Day();
			String date = forecasts[i].split("data-tab-id='", 2)[1].split("'")[0].strip();

			day.timestamp = 0;
			try
			{
				Date df = WeeWXApp.getInstance().sdf4.parse(date);
				if(df != null)
					day.timestamp = df.getTime();
			} catch(Exception ignored) {}

			String icon = "https://beta.metoffice.gov.uk" + forecasts[i].split("<img class='icon'")[1].split("src='")[1].split("'>")[0].strip();
			StringBuilder fileName = new StringBuilder(icon.substring(icon.lastIndexOf("/") + 1).replaceAll("\\.svg$", ".png"));
			day.min = forecasts[i].split("<span class='tab-temp-low'", 2)[1].split("'>")[1].split("</span>")[0].strip();
			day.max = forecasts[i].split("<span class='tab-temp-high'", 2)[1].split("'>")[1].split("</span>")[0].strip();
			day.text = forecasts[i].split("<div class='summary-text", 2)[1].split("'>", 3)[2]
					.split("</div>", 2)[0].replace("</span>", "").replace("<span>", "");

			day.min = day.min.substring(0, day.min.length() - 5) + "&deg;C";
			day.max = day.max.substring(0, day.max.length() - 5) + "&deg;C";

			fileName.insert(0, "met");

			day.icon = "file:///android_asset/icons/met/" + fileName;

			if(!metric)
			{
				day.max = C2Fdeground(str2Float(day.max));
				day.min = C2Fdeground(str2Float(day.min));
			}

			days.add(day);
		}

		return new Result(days, desc, timestamp, true);
	}

	// Thanks goes to the https://saratoga-weather.org folk for the base NOAA icons and code for dualimage.php
	static Result processWGOV(String data)
	{
		if(is_blank(data))
			return null;

		boolean metric = (boolean)KeyValue.readVar("metric", WeeWXApp.metric_default);
		List<Day> days = new ArrayList<>();
		long timestamp;
		String desc;

		try
		{
			JSONObject jobj = new JSONObject(data);

			desc = jobj.getJSONObject("currentobservation").getString("name");
			String tmp = jobj.getString("creationDate");

			timestamp = 0;
			Date df = WeeWXApp.getInstance().sdf1.parse(tmp);
			if(df != null)
				timestamp = df.getTime();

			if(timestamp > 0)
				updateCacheTime(timestamp);

			JSONArray periodName = jobj.getJSONObject("time").getJSONArray("startPeriodName");
			JSONArray validTime = jobj.getJSONObject("time").getJSONArray("startValidTime");
			JSONArray weather = jobj.getJSONObject("data").getJSONArray("weather");
			final JSONArray iconLink = jobj.getJSONObject("data").getJSONArray("iconLink");
			JSONArray temperature = jobj.getJSONObject("data").getJSONArray("temperature");
			for(int i = 0; i < periodName.length(); i++)
			{
				Day day = new Day();
				day.periodName = periodName.optString(i, "").strip();
				String url = iconLink.optString(i, "").replace("http://", "https://");
				String iconCode = url.substring(url.lastIndexOf('/') + 1).split("\\?", 2)[0]
						.replace(".png", "").replace(".jpg", "");
				String fallback = iconCode.startsWith("n") ? "wgovncloudy.jpg" : "wgovcloudy.jpg";
				day.icon = noaaAssetUrl(fallback);

				if(iconCode.equalsIgnoreCase("DualImage.php"))
				{
					String firstCode = "", secondCode = "", firstPercent = "", secondPercent = "";
					if(url.contains("?"))
					{
						for(String part : url.split("\\?", 2)[1].split("&"))
						{
							String[] pair = part.split("=", 2);
							if(pair.length != 2) continue;
							switch(pair[0])
							{
								case "i" -> firstCode = pair[1];
								case "j" -> secondCode = pair[1];
								case "ip" -> firstPercent = pair[1];
								case "jp" -> secondPercent = pair[1];
							}
						}
					}
					String firstFile = "wgov" + firstCode + ".jpg";
					String secondFile = "wgov" + secondCode + ".jpg";
					Bitmap first = loadNoaaAsset(firstFile);
					Bitmap second = loadNoaaAsset(secondFile);
					if(first != null)
						day.icon = noaaAssetUrl(firstFile);

					if(first != null && second != null)
					{
						Bitmap combined = firstCode.equals(secondCode)
							? combineImage(first, firstPercent + "%", secondPercent + "%")
							: combineImages(first, second, firstCode, secondCode, firstPercent +
							"%", secondPercent + "%");
						if(combined != null)
							day.icon = "data:image/jpeg;base64," + Base64.encodeToString(bitmapToBytes(combined), Base64.NO_WRAP);
					}

					if(first != null && !first.isRecycled())
						first.recycle();

					if(second != null && !second.isRecycled())
						second.recycle();
				} else {
					// NWS icons may end in a 2- or 3-digit precipitation probability.
					Matcher chance = Pattern.compile("^(.*?)(\\d{2,3})$").matcher(iconCode);
					String baseCode = iconCode;
					String percent = "";
					if(chance.matches())
					{
						baseCode = chance.group(1);
						percent = chance.group(2);
					}

					String baseFile = "wgov" + baseCode + ".jpg";
					if(noaaAssetExists(baseFile))
					{
						day.icon = noaaAssetUrl(baseFile);
						if(percent != null && !percent.isEmpty())
						{
							Bitmap base = loadNoaaAsset(baseFile);
							Bitmap overlayBitmap = loadNoaaAsset("wgovoverlay.jpg");
							if(base != null && overlayBitmap != null)
							{
								Bitmap combined = overlay(base, overlayBitmap, percent + "%");
								day.icon = "data:image/jpeg;base64," + Base64.encodeToString(bitmapToBytes(combined), Base64.NO_WRAP);
							}

							if(base != null && !base.isRecycled())
								base.recycle();

							if(overlayBitmap != null && !overlayBitmap.isRecycled())
								overlayBitmap.recycle();
						}
					}
				}

				day.timestamp = 0;
				df = WeeWXApp.getInstance().sdf1.parse(validTime.getString(i));
				if(df != null)
					day.timestamp = df.getTime();

				day.max = temperature.getString(i) + "&deg;F";

				if(metric)
					day.max = F2Cdeg(str2Int(temperature.getString(i)));

				day.text = weather.getString(i);
				days.add(day);
			}
		} catch(Exception e) {
			doStackOutput(e);
			return null;
		}

		return new Result(days, desc, timestamp, true);
	}


	private static String noaaAssetUrl(String name)
	{
		return "file:///android_asset/icons/wgov/" + name;
	}

	private static boolean noaaAssetExists(String name)
	{
		try(InputStream in = WeeWXApp.getInstance().getAssets().open("icons/wgov/" + name))
		{
			return true;
		} catch(IOException e) {
			return false;
		}
	}

	private static Bitmap loadNoaaAsset(String name)
	{
		try(InputStream in = WeeWXApp.getInstance().getAssets().open("icons/wgov/" + name))
		{
			return BitmapFactory.decodeStream(in);
		} catch(IOException e) {
			return null;
		}
	}

	static Result processWMO(String data)
	{
		if(is_blank(data))
			return null;

		boolean metric = (boolean)KeyValue.readVar("metric", WeeWXApp.metric_default);
		String desc;
		long timestamp;
		List<Day> days = new ArrayList<>();

		try
		{
			JSONObject jobj = new JSONObject(data);

			desc = jobj.getJSONObject("city").getString("cityName") + ", " + jobj.getJSONObject("city").getJSONObject("member").getString("memName");
			String tmp = jobj.getJSONObject("city").getJSONObject("forecast").getString("issueDate");


			timestamp = 0;
			Date df = WeeWXApp.getInstance().sdf15.parse(tmp);
			if(df != null)
				timestamp = df.getTime();

			if(timestamp > 0)
				updateCacheTime(timestamp);

			JSONArray jarr = jobj.getJSONObject("city").getJSONObject("forecast").getJSONArray("forecastDay");
			for(int i = 0; i < jarr.length(); i++)
			{
				Day day = new Day();
				JSONObject j = jarr.getJSONObject(i);

				String date = j.getString("forecastDate").strip();

				day.timestamp = 0;
				df = WeeWXApp.getInstance().sdf4.parse(date);
				if(df != null)
					day.timestamp = df.getTime();

				day.text = j.getString("weather");
				day.max = j.getString("maxTemp") + "&deg;C";
				day.min = j.getString("minTemp") + "&deg;C";
				if(!metric)
				{
					day.max = j.getString("maxTempF") + "&deg;F";
					day.min = j.getString("minTempF") + "&deg;F";
				}

				String code = j.getString("weatherIcon");
				code = code.substring(0, code.length() - 2);

				if((Integer.parseInt(code) >= 1 && Integer.parseInt(code) <= 27) || code.equals("31") || code.equals("35"))
					day.icon = cssToSVG("wi-wmo-" + code);
				else if(code.equals("28"))
					day.icon = fiToSVG("flaticon-cactus");
				else if(code.equals("29") || code.equals("30"))
					day.icon = fiToSVG("flaticon-thermometer");
				else if(code.equals("32"))
					day.icon = fiToSVG("flaticon-cold");
				else if(code.equals("33"))
					day.icon = fiToSVG("flaticon-warm");
				else if(code.equals("34"))
					day.icon = fiToSVG("flaticon-cool");

				days.add(day);
			}
		} catch(Exception e) {
			doStackOutput(e);
			return null;
		}

		return new Result(days, desc, timestamp, true);
	}

	static Result processMetService(String data)
	{
		if(is_blank(data))
			return null;

		boolean metric = (boolean)KeyValue.readVar("metric", WeeWXApp.metric_default);
		List<Day> days = new ArrayList<>();
		String desc;
		long timestamp;
		long tmp_timestamp = 0;

		try
		{
			LogMessage("processMetService() data: " + data);
			JSONObject jobj = new JSONObject(data);
			JSONArray loop = jobj.getJSONArray("days");
			timestamp = loop.getJSONObject(0).getLong("issuedAtRaw");
			desc = loop.getJSONObject(0).getJSONObject("riseSet").getString("location") + ", New Zealand";

			if(timestamp > 0)
				updateCacheTime(timestamp);

			for(int i = 0; i < loop.length(); i++)
			{
				Day day = new Day();
				JSONObject jtmp = loop.getJSONObject(i);

				Date df = WeeWXApp.getInstance().sdf1.parse(jtmp.getString("dateISO"));
				if(df != null)
					tmp_timestamp = df.getTime();

				day.timestamp = tmp_timestamp;
				day.text = jtmp.getString("forecast");
				day.max = jtmp.getString("max") + "&deg;C";
				day.min = jtmp.getString("min") + "&deg;C";
				if(jtmp.has("partDayData"))
					day.icon = jtmp.getJSONObject("partDayData").getJSONObject("afternoon").getString("forecastWord");
				else
					day.icon = jtmp.getString("forecastWord");

				day.icon = day.icon.toLowerCase(Locale.ENGLISH).strip()
						.replaceAll("[ -]", "_");

				if(day.icon.equals("wind_and_rain"))
					day.icon = "wind_rain";

				if(day.icon.equals("windy"))
					day.icon = "wind";

				day.icon = "icons/metservice/condition_" + day.icon + ".svg";

				String content = WeeWXApp.loadFileFromAssets(day.icon);
				if(!is_blank(content))
					day.icon = "file:///android_asset/" + day.icon;
				else
					day.icon = null;

				LogMessage("day.icon: " + day.icon);

				if(!metric)
				{
					day.max = C2Fdeground(str2Float(day.max));
					day.min = C2Fdeground(str2Float(day.min));
				}

				days.add(day);
			}
		} catch(Exception e) {
			doStackOutput(e);
			return null;
		}

		return new Result(days, desc, timestamp, true);
	}

	static Result processDWD(String data)
	{
		if(is_blank(data))
			return null;

		boolean metric = (boolean)KeyValue.readVar("metric", WeeWXApp.metric_default);
		List<Day> days = new ArrayList<>();
		String desc = "";
		long timestamp = 0;
		long lastTS = 0;

		try
		{
			String[] bits = data.split("<title>");
			if(bits.length >= 2)
				desc = bits[1].split("</title>")[0];
			desc = desc.substring(desc.lastIndexOf(" - ") + 3).strip();
			String string_time = data.split("<tr class='headRow'>", 2)[1].split("</tr>", 2)[0].strip();
			String date = string_time.split("<td width='30%' class='stattime'>", 2)[1].split("</td>", 2)[0].strip();
			string_time = date + " " + string_time.split("<td width='40%' class='stattime'>", 2)[1].split(" Uhr</td>", 2)[0].strip();

			Date df = WeeWXApp.getInstance().sdf11.parse(string_time);
			if(df != null)
				lastTS = timestamp = df.getTime();

			if(timestamp > 0)
				updateCacheTime(timestamp);

			data = data.split("<td width='40%' class='statwert'>Vorhersage</td>", 2)[1].split("</table>", 2)[0].strip();
			bits = data.split("<tr");
			for(int i = 1; i < bits.length; i++)
			{
				Day day = new Day();
				String bit = bits[i];
				String icon, temp, dayName;
				if(bit.split("<td ><b>", 2).length > 1)
					dayName = bit.split("<td ><b>", 2)[1].split("</b></td>", 2)[0].strip();
				else
					dayName = bit.split("<td><b>", 2)[1].split("</b></td>", 2)[0].strip();

				Locale locale = new Locale.Builder().setLanguage("de").setRegion("DE").build();
				day.timestamp = convertDaytoTS(dayName, locale, lastTS);

				if(bit.split("<td ><img name='piktogramm' src='", 2).length > 1)
					icon = bit.split("<td ><img name='piktogramm' src='", 2)[1].split("' width='50' alt='", 2)[0].strip();
				else
					icon = bit.split("<td><img name='piktogramm' src='", 2)[1].split("' width='50' alt='", 2)[0].strip();

				if(bit.split("'></td>\r\n<td >", 2).length > 1)
					temp = bit.split("'></td>\r\n<td >", 2)[1].split("Grad <abbr title='Celsius'>C</abbr></td>\r\n", 2)[0].strip();
				else
					temp = bit.split("'></td>\r\n<td>", 2)[1].split("Grad <abbr title='Celsius'>C</abbr></td>\r\n", 2)[0].strip();

				icon = icon.replace("/DE/wetter/_functions/piktos/vhs_", "").replace("?__blob=normal", "").strip();

				String fileName = "dwd_" + icon.replaceAll("[ -]", "_");

				day.icon = "file:///android_asset/icons/dwd/" + fileName;

				day.max = temp + "&deg;C";
				day.min = "&deg;C";
				if(!metric)
					day.max = C2Fdeground(str2Float(temp));

				days.add(day);
				lastTS = day.timestamp;
			}
		} catch(Exception e) {
			doStackOutput(e);
			return null;
		}

		return new Result(days, desc, timestamp, true);
	}

	static Result processAEMET(String data)
	{
		if(is_blank(data))
			return null;

		boolean metric = (boolean)KeyValue.readVar("metric", WeeWXApp.metric_default);
		List<Day> days = new ArrayList<>();
		String desc;
		long timestamp = 0;

		try
		{
			JSONObject jobj = new XmlToJson.Builder(data).build().toJson();
			if(jobj == null)
				return null;

			jobj = jobj.getJSONObject("root");
			desc = jobj.getString("nombre") + ", " + jobj.getString("provincia");

			String elaborado = jobj.getString("elaborado");

			Date df = WeeWXApp.getInstance().sdf12.parse(elaborado);
			if(df != null)
				timestamp = df.getTime();

			if(timestamp > 0)
				updateCacheTime(timestamp);

			JSONArray dates = jobj.getJSONObject("prediccion").getJSONArray("dia");
			for(int i = 0; i < dates.length(); i++)
			{
				Day day = new Day();
				JSONObject jtmp = dates.getJSONObject(i);
				String fecha = jtmp.getString("fecha");

				day.timestamp = 0;
				df = WeeWXApp.getInstance().sdf4.parse(fecha);
				if(df != null)
					day.timestamp = df.getTime();

				JSONObject estado_cielo = null;

				Object v = jtmp.get("estado_cielo");
				if(v instanceof JSONObject)
				{
					estado_cielo = jtmp.getJSONObject("estado_cielo");
				} else if(v instanceof JSONArray) {
					JSONArray jarr = jtmp.getJSONArray("estado_cielo");
					for (int j = 0; j < jarr.length(); j++)
					{
						if(!is_blank(jarr.getJSONObject(j).getString("descripcion")))
						{
							estado_cielo = jarr.getJSONObject(j);
							break;
						}
					}
				}

				if(estado_cielo == null)
					return null;

				JSONObject temperatura = jtmp.getJSONObject("temperatura");

				String code = estado_cielo.getString("content");
				String fileName = "aemet_" + code + "_g.png";

				day.icon = "file:///android_asset/icons/aemet/" + fileName;

				day.max = temperatura.getString("maxima") + "&deg;C";
				day.min = temperatura.getString("minima") + "&deg;C";

				if(!metric)
				{
					day.max = C2Fdeground(str2Float(day.max));
					day.min = C2Fdeground(str2Float(day.min));
				}

				day.text = estado_cielo.getString("descripcion");
				days.add(day);
			}
		} catch(Exception e) {
			doStackOutput(e);
			return null;
		}

		return new Result(days, desc, timestamp, true);
	}

	static Result processWCOM(String data)
	{
		if(is_blank(data))
			return null;

		boolean metric = (boolean)KeyValue.readVar("metric", WeeWXApp.metric_default);
		long timestamp = getRSSms();
		List<Day> days = new ArrayList<>();
		String desc;

		try
		{
			JSONObject jobj = new JSONObject(data);
			desc = "TODO";

			JSONArray validDate = jobj.getJSONArray("dayOfWeek");
			JSONArray timestamps = jobj.getJSONArray("validTimeUtc");
			JSONObject daypart = jobj.getJSONArray("daypart").getJSONObject(0);
			JSONArray icons = daypart.getJSONArray("iconCode");
			JSONArray phrase = daypart.getJSONArray("wxPhraseLong");
			JSONArray day_temp = jobj.getJSONArray("temperatureMax");
			JSONArray night_temp = jobj.getJSONArray("temperatureMin");

			for(int i = 0; i < validDate.length(); i++)
			{
				if(icons.getString(i) == null || icons.getString(i).equals("null"))
					continue;

				Day day = new Day();
				day.timestamp = timestamps.getLong(i);
				day.text = phrase.getString(i);
				day.icon = icons.getString(i);
				day.max = day_temp.getString(i);
				day.min = night_temp.getString(i);

				day.icon = String.format(Locale.ENGLISH, "%02d", Integer.parseInt(day.icon));
				day.icon = "icons/wcom/" + day.icon + ".svg";
				String content = WeeWXApp.loadFileFromAssets(day.icon);
				if(!is_blank(content))
					day.icon = "file:///android_asset/" + day.icon;
				else
					day.icon = null;

				if(metric)
				{
					day.max += "&deg;C";
					day.min += "&deg;C";
				} else {
					day.max += "&deg;F";
					day.min += "&deg;F";
				}

				days.add(day);
			}
		} catch(Exception e) {
			doStackOutput(e);
			return null;
		}

		return new Result(days, desc, timestamp, true);
	}

	static Result processMETIE(String data)
	{
		if(is_blank(data))
			return null;

		boolean metric = (boolean)KeyValue.readVar("metric", WeeWXApp.metric_default);
		long timestamp = getRSSms();
		List<Day> days = new ArrayList<>();
		String desc;

		Date firstTimeFrom = null, secondTimeFrom = null;
		Date secondTimeTo = null;

		String temp = null, rain = null, symbolNo = null;

		float maxTemp = -999.9f;

		String today = null, tonight = null, tomorrow = null, outlook = null;

		boolean doneToday = false, doneTomorrow = false, doneOutlook = false;

		Calendar calendar = Calendar.getInstance();
		Calendar UTCcal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

		long now = System.currentTimeMillis();
		String fcText = (String)KeyValue.readVar("textFC", "");
		if(!is_blank(fcText))
		{
			try
			{
				JSONArray jarr = new JSONObject(fcText)
						.getJSONArray("forecasts")
						.getJSONObject(0)
						.getJSONArray("regions");

				for(int i = 0;  i < jarr.length(); i++)
				{
					JSONObject jobj = jarr.getJSONObject(i);

					if(jobj.has("today"))
						today = jobj.getString("today");

					if(jobj.has("tonight"))
						tonight = jobj.getString("tonight");

					if(jobj.has("tomorrow"))
						tomorrow = jobj.getString("tomorrow");

					if(jobj.has("outlook"))
						outlook = jobj.getString("outlook");
				}

				LogMessage("jarr: " + jarr, true, KeyValue.i);
			} catch(JSONException ignored) {}
		}

		try
		{
			desc = (String)KeyValue.readVar("forecastLocationName", "");

			XmlPullParser parser = Xml.newPullParser();
			parser.setInput(new StringReader(data));

			int eventType = parser.getEventType();
			while(eventType != XmlPullParser.END_DOCUMENT)
			{
				String tag = parser.getName();
				if(!is_blank(tag) && tag.equals("product") && eventType == XmlPullParser.START_TAG)
					break;

				eventType = parser.next();
			}

			while(eventType != XmlPullParser.END_DOCUMENT)
			{
				eventType = parser.next();

				if(eventType == XmlPullParser.END_TAG)
					continue;

				String tag = parser.getName();
				if(!is_blank(tag))
				{
					if(tag.equals("location"))
						continue;

					if(tag.equals("time"))
					{
						if(firstTimeFrom == null)
						{
							firstTimeFrom = WeeWXApp.getInstance().sdf1.parse(parser.getAttributeValue(null, "from"));
						} else {
							secondTimeFrom = WeeWXApp.getInstance().sdf1.parse(parser.getAttributeValue(null, "from"));
							secondTimeTo = WeeWXApp.getInstance().sdf1.parse(parser.getAttributeValue(null, "to"));
						}
					}

					if(tag.equals("temperature"))
						temp = parser.getAttributeValue(null, "value");

					if(tag.equals("precipitation"))
						rain = parser.getAttributeValue(null, "value");

					if(tag.equals("symbol"))
						symbolNo = parser.getAttributeValue(null, "number");

					if(symbolNo == null)
						continue;

					if(secondTimeFrom != null && secondTimeFrom.getTime() < now)
					{
						firstTimeFrom = secondTimeFrom = secondTimeTo = null;
						temp = rain = symbolNo = null;

						continue;
					}

					if(secondTimeFrom != null)
					{
						calendar.setTime(secondTimeFrom);
						UTCcal.setTime(secondTimeFrom);
					}

					int UTChourOfDay = UTCcal.get(Calendar.HOUR_OF_DAY);

					float tmpTemp = str2Float(temp);
					if(maxTemp < tmpTemp)
						maxTemp = tmpTemp;

					if(UTChourOfDay != 0)
					{
						LogMessage(UTChourOfDay + " != 0, skipping...");

						firstTimeFrom = null;
						if(secondTimeFrom != null)
							secondTimeFrom = null;

						if(secondTimeTo != null)
							secondTimeTo = null;

						temp = rain = symbolNo = null;

						continue;
					}

					LogMessage(UTChourOfDay + " == 0, processing....");

					Day day = new Day();

					if(secondTimeFrom != null)
						LogMessage("secondTimeFrom: " + WeeWXApp.getInstance().sdf8.format(secondTimeFrom));

					if(secondTimeTo != null)
						LogMessage("secondTimeTo: " + WeeWXApp.getInstance().sdf8.format(secondTimeTo));

					if(days.isEmpty())
						day.timestamp = now;
					else if(secondTimeFrom != null)
						day.timestamp = secondTimeFrom.getTime() - 86_400_000L;

					if(!doneToday)
					{
						doneToday = true;
						if(UTCcal.get(Calendar.HOUR_OF_DAY) >= 17)
							day.text = tonight;
						else
							day.text = today;
						LogMessage("day.text: " + day.text, true, KeyValue.i);
					} else if(!doneTomorrow) {
						doneTomorrow = true;
						day.text = tomorrow;
						LogMessage("Tomorrow: " + tomorrow, true, KeyValue.i);
					} else if(!doneOutlook) {
						doneOutlook = true;
						day.text = outlook;
						LogMessage("Outlook: " + outlook, true, KeyValue.i);
					}

					symbolNo = String.format(Locale.ENGLISH, "%02d", Integer.parseInt(symbolNo));

					day.icon = "icons/metie/y" + symbolNo + "d.png";
					String content = WeeWXApp.loadFileFromAssets(day.icon);
					if(!is_blank(content))
						day.icon = "file:///android_asset/" + day.icon;
					else
						day.icon = null;

					day.max = maxTemp + "&deg;C";
					if(!metric)
						day.max = C2Fdeg(maxTemp);

					boolean rainInInches = (boolean)KeyValue.readVar("rainInInches", WeeWXApp.rain_in_inches_default);
					if(!metric || rainInInches)
						day.min = mm2in(str2Float(rain)) + "in";
					else
						day.min = rain + "mm";

					days.add(day);

					maxTemp = -999.9f;
					firstTimeFrom = null;

					if(secondTimeFrom != null)
						secondTimeFrom = null;

					if(secondTimeTo != null)
						secondTimeTo = null;

					temp = rain = symbolNo = null;
				}
			}
		} catch(Exception e) {
			doStackOutput(e);
			return null;
		}

		return new Result(days, desc, timestamp, true);
	}

	static Result processOWM(String data)
	{
		if(is_blank(data))
			return null;

		List<Day> days = new ArrayList<>();
		String desc;

		boolean metric = (boolean)KeyValue.readVar("metric", WeeWXApp.metric_default);
		long timestamp = getRSSms();

		try
		{
			JSONObject jobj = new JSONObject(data);
			desc = jobj.getJSONObject("city").getString("name") + ", " + jobj.getJSONObject("city").getString("country");
			JSONArray jarr = jobj.getJSONArray("list");
			for(int i = 0; i < jarr.length(); i++)
			{
				Day day = new Day();
				JSONObject j = jarr.getJSONObject(i);
				day.timestamp = j.getLong("dt") * 1_000L;

				JSONObject temp = j.getJSONObject("temp");
				int min = (int)Math.round(Double.parseDouble(temp.getString("min")));
				int max = (int)Math.round(Double.parseDouble(temp.getString("max")));
				JSONObject weather = j.getJSONArray("weather").getJSONObject(0);

				String text = weather.getString("description");
				String icon = weather.getString("icon");

				LogMessage("icon: " + icon);

				day.icon = "icons/owm/" + icon + "_t@4x.png";
				String content = WeeWXApp.loadFileFromAssets(day.icon);
				if(!is_blank(content))
					day.icon = "file:///android_asset/" + day.icon;
				else
					day.icon = null;

				LogMessage("day.icon: " + day.icon);

				if(metric)
					day.max = max + "&deg;C";
				else
					day.max = max + "&deg;F";
				day.text = text;

				if(metric)
					day.min = min + "&deg;C";
				else
					day.min = min + "&deg;F";
				days.add(day);
			}
		} catch(Exception e) {
			doStackOutput(e);
			return null;
		}

		return new Result(days, desc, timestamp, true);
	}

	static Result processMetNO(String data)
	{
		if(is_blank(data))
			return null;

		boolean metric = (boolean)KeyValue.readVar("metric", WeeWXApp.metric_default);
		boolean rainInInches = (boolean)KeyValue.readVar("rainInInches", WeeWXApp.rain_in_inches_default);
		long timestamp = 0;
		List<Day> days = new ArrayList<>();

		String desc = (String)KeyValue.readVar("forecastLocationName", "");
		if(is_blank(desc))
			desc = "";

		int modhour = getIntervalTime()[1];

		try
		{
			JSONObject jobj = new JSONObject(data);
			jobj = jobj.getJSONObject("properties");

			JSONObject meta = jobj.getJSONObject("meta");
			String updated_at = meta.getString("updated_at");
			JSONArray timeseries = jobj.getJSONArray("timeseries");

			Date df = WeeWXApp.getInstance().sdf1.parse(updated_at);
			if(df != null)
			{
				timestamp = df.getTime();
				if(timestamp > 0)
					updateCacheTime(timestamp);
			}

			for(int i = 0; i < timeseries.length(); i++)
			{
				JSONObject jobj2 = timeseries.getJSONObject(i);
				Day day = buildMetNODay(jobj2, metric, rainInInches, modhour);
				days.add(day);
			}
		} catch(Exception e) {
			doStackOutput(e);
			return null;
		}

		return new Result(days, desc, timestamp, false);
	}

	private static Day buildMetNODay(JSONObject jobj2, boolean metric, boolean rainInInches, int modhour)
	{
		try
		{
			Day day = new Day();
			JSONObject tsdata = jobj2.getJSONObject("data");

			String icon = "";
			if(modhour < 24)
			{
				// Prefer shorter interval icons for hourly/sub-daily views
				if(tsdata.has("next_1_hours"))
					icon = tsdata.getJSONObject("next_1_hours").getJSONObject("summary").getString("symbol_code");
				else if(tsdata.has("next_6_hours"))
					icon = tsdata.getJSONObject("next_6_hours").getJSONObject("summary").getString("symbol_code");
				else if(tsdata.has("next_12_hours"))
					icon = tsdata.getJSONObject("next_12_hours").getJSONObject("summary").getString("symbol_code");
			} else {
				// Prefer longer interval icons for daily view
				if(tsdata.has("next_12_hours"))
					icon = tsdata.getJSONObject("next_12_hours").getJSONObject("summary").getString("symbol_code");
				else if(tsdata.has("next_6_hours"))
					icon = tsdata.getJSONObject("next_6_hours").getJSONObject("summary").getString("symbol_code");
				else if(tsdata.has("next_1_hours"))
					icon = tsdata.getJSONObject("next_1_hours").getJSONObject("summary").getString("symbol_code");
			}

			// Temperature from instant data (compact API doesn't have air_temperature_max)
			JSONObject instant = tsdata.getJSONObject("instant").getJSONObject("details");
			if(instant.has("air_temperature"))
			{
				day.max = instant.getDouble("air_temperature") + "&deg;C";
				if(!metric)
					day.max = C2Fdeg(str2Float(day.max));
			}

			// Rain - prefer next_1_hours for short intervals, next_6_hours for daily
			boolean gotRain = false;
			if(modhour < 24 && tsdata.has("next_1_hours"))
			{
				try
				{
					JSONObject details = tsdata.getJSONObject("next_1_hours").getJSONObject("details");
					double precip = details.getDouble("precipitation_amount");
					if(!metric || rainInInches)
						day.min = round(precip / 25.4) + "in";
					else
						day.min = precip + "mm";
					gotRain = true;
				} catch(Exception ignored) {}
			}

			if(!gotRain && tsdata.has("next_6_hours"))
			{
				JSONObject details = tsdata.getJSONObject("next_6_hours")
						.getJSONObject("details");

				try
				{
					double precip = details.getDouble("precipitation_amount");
					if(!metric || rainInInches)
						day.min = round(precip / 25.4) + "in";
					else
						day.min = precip + "mm";
				} catch(Exception ignored) {}
			}

			JSONObject details2 = tsdata.getJSONObject("instant")
					.getJSONObject("details");
			if(details2.has("wind_speed"))
			{
				double windSpeed = details2.getDouble("wind_speed");
				double windDir = details2.getDouble("wind_from_direction");

				if(metric)
					day.text = Math.round(windSpeed * 3.6) + "km/h from the " + degtoname(windDir);
				else
					day.text = Math.round(windSpeed * 2.236936) + "mph from the " + degtoname(windDir);
			}

			Date df = WeeWXApp.getInstance().sdf1.parse(jobj2.getString("time"));
			if(df != null)
				day.timestamp = df.getTime();
			else
				day.timestamp = System.currentTimeMillis();

			day.icon = "icons/metno/" + icon + ".svg";
			String content = WeeWXApp.loadFileFromAssets(day.icon);
			if(!is_blank(content))
				day.icon = "file:///android_asset/" + day.icon;
			else
				day.icon = null;

			return day;
		} catch(Exception e) {
			doStackOutput(e);
			return null;
		}
	}

	private static String degtoname(double deg)
	{
		if(deg <= 22.5)
			return "North";
		else if(deg <= 67.5)
			return "North East";
		else if(deg <= 112.5)
			return "East";
		else if(deg <= 157.5)
			return "South East";
		else if(deg <= 202.5)
			return "South";
		else if(deg <= 247.5)
			return "South West";
		else if(deg <= 292.5)
			return "West";
		else if(deg <= 337.5)
			return "North West";
		return "North";
	}

	static long convertDaytoTS(String dayName, Locale locale, long lastTS)
	{
		long startTS = lastTS;

		dayName = HtmlCompat.fromHtml(dayName, HtmlCompat.FROM_HTML_MODE_LEGACY).toString();

		while(true)
		{
			SimpleDateFormat sdf = new SimpleDateFormat("EEEE", locale);
			String string_time = sdf.format(lastTS);
			//LogMessage("string_time == " + string_time + ", dayName == " + dayName);
			if(dayName.toLowerCase(Locale.ENGLISH).strip().startsWith(string_time.toLowerCase(Locale.ENGLISH)))
				return lastTS;

			lastTS += 86_400_000L;

			if(lastTS > startTS + 86_400_000L)
				return 0;
		}
	}

	static String convertRGB2Hex(String svg)
	{
		// rgb(var(--uds-spectrum-color-purple-4))
		for(KV kv : KeyValue.yahoo)
		{
			LogMessage("Checking for rgb(var(" + kv.Key + ")) -> " + kv.Val);
			svg = svg.replace("rgb(var(" + kv.Key + "))", kv.Val);
		}

		Pattern p = Pattern.compile("rgb\\(var\\((.*?)\\)\\)");
		Matcher m = p.matcher(svg);
		while(m.find())
			LogMessage("Found unconverted RGB in SVG: " + m.group(1), KeyValue.d);

		return svg;
	}

	@SuppressWarnings("SameParameterValue")
	static Boolean passesRegularCheck(boolean forced, boolean has_json_combined)
	{
		if(!forced && !KeyValue.isPrefSet(WeeWXApp.UPDATE_FREQUENCY))
		{
			if(has_json_combined)
			{
				LogMessage("passesRegularCheck() lastJsonDownload is null or blank...", KeyValue.d);
				KeyValue.putVar("LastWeatherError", getAndroidString(R.string.update_set_to_manual_but_no_content_cached));
				return false;
			}

			LogMessage("passesRegularCheck() Not forced and set to manual updates...");
			return false;
		}

		int pos = (int)KeyValue.readVar(WeeWXApp.UPDATE_FREQUENCY, WeeWXApp.UpdateFrequency_default);
		if(pos < 0 || pos >= WeeWXApp.updateOptions.length)
			pos = WeeWXApp.UpdateFrequency_default;

		LogMessage("passesRegularCheck() pos: " + pos + ", update interval set to: " +
				   WeeWXApp.updateOptions[pos] + ", forced set to: " + forced);

		if(!forced && pos == 0)
		{
			if(has_json_combined)
			{
				LogMessage("passesRegularCheck() lastJsonDownload is null or blank...", KeyValue.d);
				KeyValue.putVar("LastWeatherError", getAndroidString(R.string.update_set_to_manual_but_no_content_cached));
				return false;
			}

			LogMessage("passesRegularCheck() Not forced and set to manual updates...");
			return false;
		}

		NPWSLL npwsll = getNPWSLL();
		if(!forced && npwsll.periodTime <= 0)
		{
			LogMessage("passesRegularCheck() Skipping, period is invalid or set to manual refresh only...", KeyValue.d);

			if(has_json_combined)
			{
				KeyValue.putVar("LastWeatherError", getAndroidString(R.string.update_set_to_manual_but_no_content_cached));
				return false;
			}

			return false;
		}

		if(!forced && npwsll.report_time == 0)
		{
			LogMessage("passesRegularCheck() Skipping, report_time == 0, app hasn't been setup...", KeyValue.d);

			if(has_json_combined)
			{
				KeyValue.putVar("LastWeatherError", getAndroidString(R.string.no_download_or_app_not_setup));
				return false;
			}

			return false;
		}

		LogMessage("passesRegularCheck() Last updated check, is " + npwsll.report_time + " < " + npwsll.lastStart + "?");
		if(!forced && npwsll.report_time > npwsll.lastStart)
		{
			LogMessage("passesRegularCheck() !forced && " + npwsll.report_time + " < " + npwsll.lastStart + "...");
			if(has_json_combined)
			{
				LogMessage("passesRegularCheck() lastJsonDownload != null && !isBlank()... Skipping...", KeyValue.d);
				return false;
			}
		}

		return true;
	}

	static boolean notMergeJsonObjects()
	{
		JSONObject json_data, json_last, json_combined;

		//LogMessage("mergeJsonObjects() Loading " + json_keys[0] + "_str from SharedPrefs");
		String json_data_str = (String)KeyValue.readVar(json_keys[0] + "_str", "");
		if(is_blank(json_data_str))
		{
			LogMessage("mergeJsonObjects() json_data_str == null or isBlank(), key: " +
				json_keys[0] + "_str, value: " + json_data_str);
			return true;
		}

		try
		{
			json_data = new JSONObject(json_data_str);
		} catch(JSONException je) {
			LogMessage("mergeJsonObjects() Failed turing json_data_str into json_data, je: " +
				je.getMessage());
			return true;
		}

		if(json_data.length() == 0)
		{
			LogMessage("mergeJsonObjects() json_data.length() == 0");
			return true;
		}

		//LogMessage("mergeJsonObjects() Loading " + json_keys[2] + "_str from SharedPrefs");
		String json_last_str = (String)KeyValue.readVar(json_keys[2] + "_str", "");
		if(is_blank(json_last_str))
		{
			LogMessage("mergeJsonObjects() json_last_str == null or isBlank(), key: " +
							json_keys[2] + "_str, value: " + json_last_str);
			return true;
		}

		try
		{
			json_last = new JSONObject(json_last_str);
		} catch(JSONException je) {
			LogMessage("mergeJsonObjects() Failed turing json_last_str into json_last, je: " +
				je.getMessage());
			return true;
		}

		if(json_last.length() == 0)
		{
			LogMessage("mergeJsonObjects() json_last.length() == 0");
			return true;
		}

		try
		{
			json_combined = json_last;
			Iterator<String> keys = json_data.keys();
			while(keys.hasNext())
			{
				String key = keys.next();
				Object value = json_data.get(key);
				//LogMessage("mergeJsonObjects() putting key: " + key + ", value: " + value);
				json_combined.put(key, value);
			}
		} catch(JSONException je) {
			LogMessage("mergeJsonObjects() Failed to merge json_data with json_last, je: + " +
				je.getMessage());
			return true;
		}

		if(json_combined.length() == 0)
		{
			LogMessage("mergeJsonObjects() json_combined.length() == 0");
			return true;
		}

		String json_combined_str = json_combined.toString();

		//LogMessage("mergeJsonObjects() json_combined_str: " + json_combined_str);
		KeyValue.putVar("json_combined_str", json_combined_str);

		return false;
	}

	// The rain day starts at since_hour today, or at since_hour yesterday if that hour hasn't
	// been reached yet. With since_hour unset or 0 this is simply midnight today.
	static Calendar getRainDayStart()
	{
		int since_hour = (int)getJson("since_hour", 0);
		if(since_hour < 0 || since_hour > 23)
			since_hour = 0;

		KeyValue.since_hour = since_hour;

		Calendar now = Calendar.getInstance();
		Calendar rainDayStart = (Calendar)now.clone();
		rainDayStart.set(Calendar.HOUR_OF_DAY, since_hour);
		rainDayStart.set(Calendar.MINUTE, 0);
		rainDayStart.set(Calendar.SECOND, 0);
		rainDayStart.set(Calendar.MILLISECOND, 0);
		if(rainDayStart.after(now))
			rainDayStart.add(Calendar.DAY_OF_YEAR, -1);

		return rainDayStart;
	}

	// Set on a saved inigo-last when its rain sums were copied from the previous one
	static final String RAIN_CARRIED_OVER = "app_rain_carried_over";

	// inigo-last is generated at midnight and again at since_hour. The midnight run skips
	// the *_rain_sum values because the rain day isn't over until since_hour, the
	// since_hour run includes them, or a "no value" warning for them if there's no rain data.
	static boolean hasRainSums(JSONObject jsonObject)
	{
		if(jsonObject.optBoolean(RAIN_CARRIED_OVER, false))
			return false;

		for(Iterator<String> it = jsonObject.keys(); it.hasNext();)
			if(it.next().endsWith("_rain_sum"))
				return true;

		JSONArray errors = jsonObject.optJSONArray("processingErrors");
		if(errors != null)
			for(int i = 0; i < errors.length(); i++)
			{
				JSONObject error = errors.optJSONObject(i);
				if(error != null && error.optString("msg").contains("_rain_sum"))
					return true;
			}

		return false;
	}

	// Until since_hour the last completed rain day is still the one in the previous
	// inigo-last, so keep showing its rain sums rather than none at all.
	static void carryOverRainSums(JSONObject jsonObject)
	{
		if(hasRainSums(jsonObject))
			return;

		String json_last_str = (String)KeyValue.readVar(json_keys[2] + "_str", "");
		if(is_blank(json_last_str))
			return;

		try
		{
			JSONObject previous = new JSONObject(json_last_str);
			for(Iterator<String> it = previous.keys(); it.hasNext();)
			{
				String key = it.next();
				if(key.endsWith("_rain_sum"))
					jsonObject.put(key, previous.get(key));
			}

			jsonObject.put(RAIN_CARRIED_OVER, true);
			LogMessage("carryOverRainSums() " + json_labels[2] + " has no rain sums yet, kept the previous ones");
		} catch(JSONException je) {
			doStackOutput(je);
		}
	}

	// report_time in inigo-last is midnight at the start of the day it was generated on,
	// so it needs downloading again if it's from before today, or if it's after since_hour
	// and the copy we have is from the midnight run, which doesn't have the rain sums yet.
	static boolean isJsonLastCurrent()
	{
		String json_last_str = (String)KeyValue.readVar(json_keys[2] + "_str", "");
		if(is_blank(json_last_str))
			return false;

		JSONObject json_last;
		try
		{
			json_last = new JSONObject(json_last_str);
		} catch(JSONException je) {
			return false;
		}

		long report_time = Math.round(json_last.optDouble("report_time", 0D) * 1_000L);

		Calendar midnight = Calendar.getInstance();
		midnight.set(Calendar.HOUR_OF_DAY, 0);
		midnight.set(Calendar.MINUTE, 0);
		midnight.set(Calendar.SECOND, 0);
		midnight.set(Calendar.MILLISECOND, 0);

		if(report_time < midnight.getTimeInMillis())
		{
			LogMessage("isJsonLastCurrent() " + json_labels[2] + " is from before today, downloading it again");
			return false;
		}

		// getRainDayStart() is today once since_hour has been reached
		if(getRainDayStart().before(midnight) || hasRainSums(json_last))
			return true;

		LogMessage("isJsonLastCurrent() " + json_labels[2] + " is from the midnight run, downloading the since_hour run");
		return false;
	}

	static void checkRainfallAlert()
	{
		boolean rainfall_alert = (boolean)KeyValue.readVar("rainfall_alert", WeeWXApp.rainfall_alert_default);

		if(!rainfall_alert)
		{
			if(!MainActivity.mqttEnabled)
				LogMessage("checkRainfallAlert() rainfall_alert set to false");
			return;
		}

		if(!MainActivity.mqttEnabled)
			LogMessage("checkRainfallAlert() rainfall_alert set to true");

		// day_rain_sum resets at since_hour, not midnight
		Calendar rainDayStart = getRainDayStart();

		long last_rainfall_alert = (long)KeyValue.readVar("LastRainfallAlert", 0L);
		if(last_rainfall_alert >= rainDayStart.getTimeInMillis())
		{
			if(!MainActivity.mqttEnabled)
				LogMessage("checkRainfallAlert() Rainfall notification already triggered this rain day");
			return;
		}

		// Don't alert on a total left over from the previous rain day
		long report_time = Math.round((double)getJson("report_time", 0D) * 1_000L);
		if(report_time < rainDayStart.getTimeInMillis())
		{
			if(!MainActivity.mqttEnabled)
				LogMessage("checkRainfallAlert() report_time is from before the current rain day started");
			return;
		}

		float rainfall = (float)getJson("day_rain_sum", 0f);

		float rainfall_limit = (int)KeyValue.readVar("RainfallLimit", WeeWXApp.RainfallLimit_default) / 100f;

		if(rainfall >= rainfall_limit)
		{
			KeyValue.putVar("LastRainfallAlert", System.currentTimeMillis());
			WeeWXApp.sendRainfallAlert(rainfall, rainfall_limit);
			LogMessage("checkRainfallAlert() rainfall (" + rainfall + ") >= rainfall_limit (" + rainfall_limit + ") notification triggered");
		} else {
			if(!MainActivity.mqttEnabled)
				LogMessage("checkRainfallAlert() rainfall (" + rainfall + ") < rainfall_limit (" + rainfall_limit + ") no notification triggered");
		}
	}

	static void checkRainrateAlert()
	{
		boolean[] rainrate_alerts = {
			(boolean)KeyValue.readVar(WeeWXApp.RAINRATE_ALERT_WATCH, WeeWXApp.rainrate_alert_watch_default),
			(boolean)KeyValue.readVar(WeeWXApp.RAINRATE_ALERT_WARNING, WeeWXApp.rainrate_alert_warning_default),
			(boolean)KeyValue.readVar(WeeWXApp.RAINRATE_ALERT_SEVERE, WeeWXApp.rainrate_alert_severe_default),
		};

		if(!rainrate_alerts[0] && !rainrate_alerts[1] && !rainrate_alerts[2])
		{
			if(!MainActivity.mqttEnabled)
				LogMessage("checkRainrateAlert() rainrate_alert set to false");
			return;
		}

		if(!MainActivity.mqttEnabled)
			LogMessage("checkRainrateAlert() At least one rainrate alert is true");

		boolean metric = (boolean)KeyValue.readVar("metric", WeeWXApp.metric_default) &&
						 !(boolean)KeyValue.readVar("rainInInches", WeeWXApp.rain_in_inches_default);

		int[] totals = {
			Math.round((float)getJson("rain_600", 0f) * 100),
			Math.round((float)getJson("rain_1800", 0f) * 100),
			Math.round((float)getJson("rain_3600", 0f) * 100),
			Math.round((float)getJson("rain_21600", 0f) * 100),
			Math.round((float)getJson("rain_86400", 0f) * 100),
		};

		long now = System.currentTimeMillis();
		long last_rainrate_alert = (long)KeyValue.readVar("LastRainrateAlert", 0L);
		int last_rainrate_level = (int)KeyValue.readVar("LastRainrateLevel", -1);

		String unit = "mm";
		int[][] FLOOD_THRESHOLDS = FLOOD_THRESHOLDS_MM;
		if(!metric)
		{
			unit = "in";
			FLOOD_THRESHOLDS = FLOOD_THRESHOLDS_IN;
		}

		int[] alert = getAlertLevel(totals, FLOOD_THRESHOLDS, rainrate_alerts);
		int level = alert[0];
		int period = alert[1];

		if(level < 0 || period < 0)
		{
			if(!MainActivity.mqttEnabled)
				LogMessage("checkRainrateAlert() No current rainrate notification needed");
			return;
		}

		String debugunit = "minutes";
		String timelen_unit = getAndroidString(R.string.minutes);
		int timelen = 0;
		if(warning_delays[period] == 3_600)
		{
			debugunit = "hour";
			timelen_unit = getAndroidString(R.string.hour);
			timelen = 1;
		} else if(warning_delays[period] > 3_600) {
			debugunit = "hours";
			timelen_unit = getAndroidString(R.string.hours);
			timelen = warning_delays[period] % 3_600;
		}

		int time_ago = Math.round((now - last_rainrate_alert) / 1000f);

		if(time_ago >= warning_delays[period] || level > last_rainrate_level)
		{
			KeyValue.putVar("LastRainrateAlert", now);
			KeyValue.putVar("LastRainrateLevel", alert[0]);

			String tmpStr = String.format(Locale.ENGLISH, "%.1f", totals[period] / 100f) + "mm";
			if(!metric)
				tmpStr = String.format(Locale.ENGLISH, "%.2f", totals[period] / 100f) + "in";

			WeeWXApp.sendRainrateAlert(tmpStr, level, timelen, timelen_unit);
			LogMessage("checkRainrateAlert() rainfall (" + totals[period] + unit +
					   ") >= rainfall_limit (" + FLOOD_THRESHOLDS[period][level] + unit + ") " +
					   "fell in " + timelen + " " + debugunit + " so notification triggered");
		} else {
			if(!MainActivity.mqttEnabled)
				LogMessage("checkRainrateAlert() No threshold reached, so no notification triggered");
		}
	}

	private static int[] getAlertLevel(int[] totals, int[][] thresholds, boolean[] rainrate_alerts)
	{
		for(int level : levels)
		{
			if(!rainrate_alerts[level])
				continue;

			for(int period : periods)
			{
				if(totals[period] >= thresholds[period][level])
				{
					return new int[]{level, period};
				}
			}
		}

		return new int[]{-1, -1}; // no alert
	}

	static void checkTempAlerts()
	{
		boolean morning_temp_alert = (boolean)KeyValue.readVar("morning_temp_alert", WeeWXApp.morning_temp_alert_default);

		Calendar cal = Calendar.getInstance();
		Calendar cal1 = Calendar.getInstance();

		TempResult temps = getTempResult();

		// Don't trigger temp alerts before 6am
		if(cal.get(Calendar.HOUR_OF_DAY) < 6)
			return;

		if(cal.get(Calendar.HOUR_OF_DAY) < 12 && morning_temp_alert)
		{
			if(!MainActivity.mqttEnabled)
				LogMessage("checkTempAlerts() morning_temp_alert set to true");

			long last_morning_alert = (long)KeyValue.readVar("LastMorningTempAlert", 0L);
			cal1.setTimeInMillis(last_morning_alert);

			if(cal.get(Calendar.YEAR) == cal1.get(Calendar.YEAR) &&
			   cal.get(Calendar.DAY_OF_YEAR) == cal1.get(Calendar.DAY_OF_YEAR))
			{
				if(!MainActivity.mqttEnabled)
					LogMessage("checkTempAlerts() Notification already triggered this morning");
				return;
			}

			float morning_temp_limit = (int)KeyValue.readVar("MorningTemp", WeeWXApp.MorningTemp_default) / 10f;

			float minObservedTemp = Math.min(temps.minObservedTemp, temps.CurrTemp);

			if(!MainActivity.mqttEnabled)
				LogMessage("checkTempAlerts() minForecastTemp: " + minObservedTemp);

			boolean hasBottomedOut = temps.CurrTemp > minObservedTemp;

			if(!MainActivity.mqttEnabled)
				LogMessage("checkTempAlerts() hasBottomedOut: " + hasBottomedOut);

			boolean hasWarmedUp = hasBottomedOut && temps.CurrTemp >= morning_temp_limit && minObservedTemp < morning_temp_limit;

			if(!MainActivity.mqttEnabled)
				LogMessage("checkTempAlerts() hasWarmedUp: " + hasWarmedUp);

			if(hasWarmedUp)
			{
				KeyValue.putVar("LastMorningTempAlert", System.currentTimeMillis());
				WeeWXApp.sendTemperatureAlert(temps.CurrTemp, morning_temp_limit, false);
				LogMessage("checkTempAlerts() CurrTemp (" + temps.CurrTemp + ") >= morning_temp_limit (" +
						   morning_temp_limit + ") notification triggered");
			} else {
				if(!MainActivity.mqttEnabled)
					LogMessage("checkTempAlerts() CurrTemp (" + temps.CurrTemp + ") < morning_temp_limit (" +
						   morning_temp_limit + ") no notification triggered");
			}

			return;
		}

		if(cal.get(Calendar.HOUR_OF_DAY) < 12)
		{
			if(!MainActivity.mqttEnabled)
				LogMessage("checkTempAlerts() morning_temp_alert set to false");
			return;
		}

		boolean afternoon_temp_alert = (boolean)KeyValue.readVar("afternoon_temp_alert", WeeWXApp.afternoon_temp_alert_default);

		if(afternoon_temp_alert)
		{
			if(!MainActivity.mqttEnabled)
				LogMessage("checkTempAlerts() afternoon_temp_alert set to true");

			long last_afternoon_alert = (long)KeyValue.readVar("LastAfternoonTempAlert", 0L);
			cal1.setTimeInMillis(last_afternoon_alert);

			if(cal.get(Calendar.YEAR) == cal1.get(Calendar.YEAR) &&
			   cal.get(Calendar.DAY_OF_YEAR) == cal1.get(Calendar.DAY_OF_YEAR))
			{
				if(!MainActivity.mqttEnabled)
					LogMessage("checkTempAlerts() Notification already triggered this afternoon");
				return;
			}

			float afternoon_temp_limit = (int)KeyValue.readVar("AfternoonTemp", WeeWXApp.AfternoonTemp_default) / 10f;

			float maxObservedTemp = Math.max(temps.CurrTemp, temps.maxObservedTemp);

//			for(int i = 0; i < temps.outTemp_trend_ts.length; i++)
//			{
//				LogMessage("checkTempAlerts() outTemp_trend_ts[" + i + "]: " + WeeWXApp.getInstance().sdf10.format(new Date(temps.outTemp_trend_ts[i])));
//				LogMessage("checkTempAlerts() outTemp_trend_signal[" + i + "]: " + temps.outTemp_trend_signal[i]);
//				LogMessage("checkTempAlerts() outTemp_trend_count[" + i + "]: " + temps.outTemp_trend_count[i]);
//			}
//
//			if(temps.outTemp_trend_signal.length == 0)
//			{
//				LogMessage("checkTempAlerts() temps.outTemp_trend_signal.length == 0, skipping temp limit checks...");
//				return;
//			}

			//long last_ts = temps.outTemp_trend_ts[0];
			int last_signal = temps.outTemp_trend_signal[0];
			//int last_count = temps.outTemp_trend_count[0];

			boolean hasPeaked = (last_signal == -1 || cal.get(Calendar.HOUR_OF_DAY) >= 16) && temps.CurrTemp < maxObservedTemp;

			if(!MainActivity.mqttEnabled)
				LogMessage("checkTempAlerts() hasPeaked: " + hasPeaked);

			boolean hasCooledOffEnough = hasPeaked && temps.CurrTemp <= afternoon_temp_limit && maxObservedTemp > afternoon_temp_limit;

			if(!MainActivity.mqttEnabled)
				LogMessage("checkTempAlerts() hasCooledOffEnough: " + hasCooledOffEnough);

			if(hasCooledOffEnough)
			{
				KeyValue.putVar("LastAfternoonTempAlert", System.currentTimeMillis());
				WeeWXApp.sendTemperatureAlert(temps.CurrTemp, afternoon_temp_limit, true);
				LogMessage("checkTempAlerts() CurrTemp (" + temps.CurrTemp + ") <= afternoon_temp_limit (" +
						   afternoon_temp_limit + ") notification triggered");
			} else {
				LogMessage("checkTempAlerts() CurrTemp (" + temps.CurrTemp + ") > afternoon_temp_limit (" +
						   afternoon_temp_limit + ") no notification triggered");
			}
		} else {
			if(!MainActivity.mqttEnabled)
				LogMessage("checkTempAlerts() afternoon_temp_alert set to false");
		}
	}

	static TempResult getTempResult()
	{
		return new TempResult(
				(float)getJson("current_outTemp", 0f),
				(float)getJson("day_outTemp_min", 0f),
				(float)getJson("day_outTemp_max", 0f),
				(int[])getJson("outTemp_trend_count", emptyIntArr),
				(int[])getJson("outTemp_trend_signal", emptyIntArr),
				(long[])getJson("outTemp_trend_ts", emptyLongArr, true));
	}

	static Object getElement(String element, JSONObject jsonObject, Object defaultValue, boolean timeStamps)
	{
		if(jsonObject == null || jsonObject.length() == 0)
			return defaultValue;

		switch(defaultValue)
		{
			case Boolean b ->
			{
				return jsonObject.optBoolean(element, b);
			}
			case Double d ->
			{
				return jsonObject.optDouble(element, d);
			}
			case Float f ->
			{
				return (float)jsonObject.optDouble(element, f);
			}
			case Integer i ->
			{
				return jsonObject.optInt(element, i);
			}
			case int[] iarr ->
			{
				if(!jsonObject.has(element))
					return iarr;

				JSONArray temparr = jsonObject.optJSONArray(element);
				if(temparr == null || temparr.length() == 0)
					return iarr;

				int[] numbers = new int[temparr.length()];
				for(int i = 0; i < temparr.length(); ++i)
					numbers[i] = temparr.optInt(i);

				return numbers;
			}
			case Long l ->
			{
				return jsonObject.optLong(element, l);
			}
			case long[] larr ->
			{
				long multiplier = 1L;
				if(timeStamps)
					multiplier = 1_000L;

				if(!jsonObject.has(element))
					return larr;

				JSONArray temparr = jsonObject.optJSONArray(element);
				if(temparr == null || temparr.length() == 0)
					return larr;

				long[] numbers = new long[temparr.length()];
				for(int i = 0; i < temparr.length(); ++i)
					numbers[i] = temparr.optLong(i) * multiplier;

				return numbers;
			}
			case String s ->
			{
				return jsonObject.optString(element, s).strip();
			}

			default -> LogMessage("Unknown Object type " + defaultValue.getClass());
		}

		return defaultValue;
	}

	static String getDateTimeStr(long when, int timeMode)
	{
		String dateTimeStr = "";

		if(timeMode == 0)
			dateTimeStr = getHourMin(when);
		else if(timeMode == 1)
			dateTimeStr = getTimeMonth(when);
		else if(timeMode == 2)
			dateTimeStr = getTimeYear(when);
		else if(timeMode == 3)
			dateTimeStr = getAllTime(when);
		else if(timeMode == 4)
			dateTimeStr = getHourMinNext(when);

		return dateTimeStr;
	}

	static String getHourMin(long when)
	{
		return WeeWXApp.getInstance().sdf19.format(when);
	}

	static String getHourMinNext(long when)
	{
		Calendar cal1 = Calendar.getInstance();
		Calendar cal2 = Calendar.getInstance();
		cal2.setTimeInMillis(when);

		String str = WeeWXApp.getInstance().sdf20.format(when);

		if(cal1.get(Calendar.YEAR) != cal2.get(Calendar.YEAR) ||
		   cal1.get(Calendar.DAY_OF_YEAR) != cal2.get(Calendar.DAY_OF_YEAR))
			str += "+1";

		return str;
	}

	static String getOrdinal(int day)
	{
		Object[] testArgs = {day};
		MessageFormat mf = new MessageFormat("{0,ordinal}", Locale.getDefault());
		return mf.format(testArgs);
	}

	static String getTimeMonth(long when)
	{
		SimpleDateFormat sdf = new SimpleDateFormat("d", Locale.getDefault());
		int day = str2Int(sdf.format(when));
		return getOrdinal(day);
	}

	static String widgetTime(long when)
	{
		return WeeWXApp.getInstance().sdf19.format(new Date(when)) + " " + getTimeMonth(when) + " " + getShortMonth(when);
	}

	static String getShortMonth(long when)
	{
		SimpleDateFormat sdf = new SimpleDateFormat("MMM", Locale.getDefault());
		String mon = sdf.format(when);
		if(mon.length() > 3)
			mon = mon.substring(0, 3);

		return mon;
	}

	static String getTimeYear(long when)
	{
		SimpleDateFormat sdf = new SimpleDateFormat("d", Locale.getDefault());
		return sdf.format(when) + " " + getShortMonth(when);
	}

	static String getAllTime(long when)
	{
		SimpleDateFormat sdf = new SimpleDateFormat("MMM", Locale.getDefault());
		String mon = sdf.format(when);
		if(mon.length() > 3)
			mon = mon.substring(0, 3);

		sdf = new SimpleDateFormat("yy", Locale.getDefault());
		return mon + " '" + sdf.format(when);
	}

	static String formatString(String element)
	{
		String key = KeyValue.getKeyFromName(element);
		if(is_blank(key))
		{
			LogMessage("element '" + element + "' returned no key");
			return null;
		}

		if(key.equals("404"))
			return null;

		float f = (float)getJson(element, 0f);

		String fmt = KeyValue.getFormat(key);
		if(is_blank(fmt))
		{
			LogMessage("fmt was null or blank");
			LogMessage("element: " + element);
			LogMessage("f: " + f);
			LogMessage("key: " + key);
			return null;
		}

		String fmt_str = String.format(fmt, f);
		if(is_blank(fmt_str))
		{
			LogMessage("fmt_str was null or blank");
			LogMessage("element: " + element);
			LogMessage("f: " + f);
			LogMessage("key: " + key);
			LogMessage("fmt: " + fmt);
			return null;
		}

		return fmt_str;
	}

	static String getSinceHour(int since_str)
	{
		if(!hasElement("since_hour"))
			return "";

		int since_hour = (int)getJson("since_hour", 0);
		if(since_hour == 0)
			return getAndroidString(since_str) + " mn";

		SimpleDateFormat sdf = new SimpleDateFormat("ha", Locale.getDefault());
		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.HOUR_OF_DAY, since_hour);
		return getAndroidString(since_str) + " " + sdf.format(cal.getTimeInMillis());
	}

	static boolean hasElement(String element)
	{
		JSONObject jsonObject = getJson();
		if(jsonObject == null || jsonObject.length() == 0)
			return false;

		return jsonObject.has(element);
	}

	static Object getJson(String element, Object defaultValue)
	{
		return getJson(element, defaultValue, false);
	}

	static Object getJson(String element, Object defaultValue, boolean timestamps)
	{
		JSONObject jsonObject = getJson();
		return getElement(element, jsonObject, defaultValue, timestamps);
	}

	static final String WEBCAM_HAS_LAST_MODIFIED = "webcam_has_last_modified";

	// Use the server's Last-Modified time as the snapshot time when it sent one,
	// and remember which it was so the webcam screen can label it correctly
	static void setWebcamFileTime(File file, long lastModified)
	{
		boolean hasLastModified = lastModified > 0 && file.setLastModified(lastModified);
		if(lastModified > 0 && !hasLastModified)
			LogMessage("Failed to set " + file.getName() + " last modified time", KeyValue.w);

		KeyValue.putVar(WEBCAM_HAS_LAST_MODIFIED, hasLastModified);
	}

	static void updateJSON(String element, Object value)
	{
		if(!KeyValue.isPrefSet("json_combined_str"))
			return;

		// Never start from an empty object, it would wipe everything not in this update
		JSONObject jsonObject = getJson();
		if(jsonObject == null)
			return;

		try
		{
			jsonObject.put(element, value);
		} catch (JSONException ignore) {}

		KeyValue.putVar("json_combined_str", jsonObject.toString());
	}

	static void updateJSON(JSONObject updates)
	{
		if(updates == null || updates.length() == 0 || !KeyValue.isPrefSet("json_combined_str"))
			return;

		// Never start from an empty object, it would wipe everything not in these updates
		JSONObject jsonObject = getJson();
		if(jsonObject == null)
			return;

		for(Iterator<String> it = updates.keys(); it.hasNext();)
		{
			String element = it.next();
			try
			{
				jsonObject.put(element, updates.opt(element));
			} catch (JSONException ignore) {}
		}

		KeyValue.putVar("json_combined_str", jsonObject.toString());
	}

	static JSONObject getJson()
	{
		if(!KeyValue.isPrefSet("json_combined_str"))
			return null;

		String line = (String)KeyValue.readVar("json_combined_str", "");
		if(!is_blank(line))
		{
			try
			{
				return new JSONObject(line);
			} catch(Exception ignored) {}
		}

		return null;
	}

	static JSONObject getJSONerrors()
	{
		JSONObject jsonObject = new JSONObject();

		String line = (String)KeyValue.readVar("JSONerrors", "");
		if(!is_blank(line))
		{
			try
			{
				jsonObject = new JSONObject(line);
			} catch(Exception ignored) {}
		}

		return jsonObject;
	}

	static void saveJSONerrors(JSONObject jsonObject)
	{
		KeyValue.putVar("JSONerrors", jsonObject.toString());
	}

	static boolean is_valid_url(String url)
	{
		return !is_blank(url) &&
			(url.startsWith("http://") || url.startsWith("https://") ||
			url.startsWith("mqtt://") || url.startsWith("mqtts://") ||
			url.startsWith("ws://") || url.startsWith("wss://"));
	}

	static boolean is_blank(String str)
	{
		return str == null || str.isBlank() || str.equals("null");
	}

	static void noteError(Exception e)
	{
		LogMessage("UpdateCheck.noteError() Error! " + e.getMessage(), KeyValue.e);
		noteError(e.getLocalizedMessage());
	}

	static void noteError(int resId)
	{
		noteError(getAndroidString(resId));
	}

	static void noteError(String error)
	{
		JSONObject jsonObject = getJSONerrors();
		try
		{
			String when = "" + System.currentTimeMillis();
			jsonObject.put(when, error);
		} catch (JSONException ignored) {}

		saveJSONerrors(jsonObject);
	}

	static void noteError(int resId, Object[] vars)
	{
		LogMessage("UpdateCheck.noteError() Error! " + String.format(Locale.ENGLISH, getEnglishAndroidString(resId), vars), KeyValue.e);
		noteError(getAndroidString(resId, vars));
	}

	static int errorCount()
	{
		JSONObject jsonObject = getJSONerrors();
		return jsonObject.length();
	}

	static void processUpdateInBG(boolean forced, boolean weather, boolean forecast, boolean radar, boolean webcam)
	{
		Thread thread = new Thread(() -> processUpdates(forced, weather, forecast, radar, webcam));
		thread.setName("processUpdateInBG");
		thread.start();
	}

	static void processUpdates(boolean forced, boolean weather, boolean forecast, boolean radar, boolean webcam)
	{
//		Thread[] threads = new Thread[Thread.activeCount()];
//		Thread.enumerate(threads);
//
//		for(Thread thread : threads)
//		{
//			LogMessage("processUpdates() thread.getId(): " + thread.getId());
//			LogMessage("processUpdates() thread.isAlive(): " + thread.isAlive());
//			LogMessage("processUpdates() thread.isDaemon(): " + thread.isDaemon());
//			LogMessage("processUpdates() thread.getName(): " + thread.getName());
//			LogMessage("processUpdates() thread.getState(): " + thread.getState());
//			for(StackTraceElement element : thread.getStackTrace())
//				LogMessage("processUpdates() at element: " + element);
//		}

		if(notCheckConnection() && !forced)
		{
			if(errorCount() > 0)
			{
				LogMessage("sending UPDATE_ERRORS intent...");
				updateNotificationMessage(UPDATE_ERRORS);
				LogMessage("sent UPDATE_ERRORS intent...");
			}

			LogMessage("WeeWXAppCommon.processUpdates() Not on wifi and not a forced refresh, skipping...", KeyValue.d);
			if(weather)
				updateNotificationMessage(STOP_WEATHER_INTENT);

			if(forecast)
				updateNotificationMessage(STOP_FORECAST_INTENT);

			if(radar)
				updateNotificationMessage(STOP_RADAR_INTENT);

			if(webcam)
				updateNotificationMessage(STOP_WEBCAM_INTENT);

			return;
		}

		if(downloader != null && downloader.isRunning() && downloader.startTime + 1_000L > System.currentTimeMillis())
		{
			if(errorCount() > 0)
			{
				LogMessage("sending UPDATE_ERRORS intent...");
				updateNotificationMessage(UPDATE_ERRORS);
				LogMessage("sent UPDATE_ERRORS intent...");
			}

			LogMessage("WeeWXAppCommon.processUpdates() downloader is running already, skipping update...", KeyValue.d);
			if(weather)
				updateNotificationMessage(STOP_WEATHER_INTENT);

			if(forecast)
				updateNotificationMessage(STOP_FORECAST_INTENT);

			if(radar)
				updateNotificationMessage(STOP_RADAR_INTENT);

			if(webcam)
				updateNotificationMessage(STOP_WEBCAM_INTENT);

			return;
		}

		if(downloader != null && downloader.isRunning())
			downloader.shutdown();

		if(downloader != null)
			downloader = null;

		boolean doWeather = weather;
		boolean doForecast = forecast;

		try
		{
			List<Integer> idtype = new ArrayList<>();
			List<String> urls = new ArrayList<>();
			List<String> contentTypes = new ArrayList<>();
			List<Object> PossibleErrors = new ArrayList<>();

			String fctype = "", forecastURL = "";
			boolean hasForecastGson = false;

			ForecastDefaults fcDef = null;

			if(doWeather && !forced && KeyValue.isVisible &&
				(boolean)KeyValue.readVar(ENABLE_MQTT, enable_mqtt_default))
				doWeather = false;

			if(doWeather)
			{
				boolean has_json_combined = false;

				JSONObject jsonObject = getJson();
				if(jsonObject != null && jsonObject.length() != 0)
					has_json_combined = true;

				if(has_json_combined)
				{
					Boolean passes = passesRegularCheck(forced, true);
					if(!passes)
					{
						LogMessage("getWeather() passesRegularCheck(): false");
						doWeather = false;
					}
				}
			}

			if(doWeather)
			{
				for(int i = 0; i < 3; i++)
				{
					String JSONurl = (String)KeyValue.readVar(json_keys[i] + "_url", "");
					if(is_valid_url(JSONurl))
					{
						Calendar cal1 = Calendar.getInstance();
						Calendar cal2 = Calendar.getInstance();
						cal2.setTimeInMillis((long)KeyValue.readVar(json_keys[i] + TIME_EXT, 0L));

						if(i == 1 && cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR))
							continue;

						if(i == 2 && isJsonLastCurrent())
							continue;

						idtype.add(i);
						urls.add(JSONurl);
						contentTypes.add("JSON");
						PossibleErrors.add(new Object[]{R.string.wasnt_able_to_connect_or_download, new Object[]{json_labels[i], JSONurl}});
					}
				}
			}

			if(doForecast)
			{
				fctype = (String)KeyValue.readVar(WeeWXApp.FCTYPE, "");
				if(is_blank(fctype))
				{
					LogMessage("UpdateCheck.java Unable to get forecast defaults for fctype: " + fctype, KeyValue.w);
					doForecast = false;
				}
			}

			if(doForecast)
			{
				fcDef = WeeWXApp.getFCdefs(fctype);
				if(fcDef == null)
				{
					LogMessage("UpdateCheck.java Unable to get forecast defaults for fctype: " + fctype, KeyValue.w);
					noteError("Unable to get forecast defaults for fctype: " + fctype);
					doForecast = false;
				}
			}

			if(doForecast)
			{
				LogMessage("WeeWXAppCommon.processUpdates() fctype: " + fctype);

				if(fctype.equals("weatherzone3") || fctype.equals("metservice2"))
				{
					LogMessage("WeeWXAppCommon.processUpdates() fctype == weatherzone3 || metservice2, skipping...", KeyValue.d);
					noteError(R.string.forecast_type_is_invalid, new Object[]{fctype});
					doForecast = false;
				}
			}

			if(doForecast)
			{
				String forecast_url = (String)KeyValue.readVar("FORECAST_URL", "");
				if(is_blank(forecast_url))
				{
					LogMessage("WeeWXAppCommon.processUpdates() FORECAST_URL == null || isBlank(), skipping...", KeyValue.e);
					noteError(R.string.forecast_url_not_set, new Object[]{"inigo-settings.txt"});
					doForecast = false;
				}
			}

			if(doForecast)
			{
				int pos = (int)KeyValue.readVar(WeeWXApp.UPDATE_FREQUENCY, WeeWXApp.UpdateFrequency_default);
				if(!forced && pos == 0)
				{
					if(!hasForecastGson)
						LogMessage("WeeWXAppCommon.processUpdates() hasForecastGson is false, skipping...", KeyValue.w);
					else
						LogMessage("WeeWXAppCommon.processUpdates() hasForecastGson is true, skipping...", KeyValue.w);

					doForecast = false;
				}
			}

			long now = System.currentTimeMillis();
			LogMessage("WeeWXAppCommon.processUpdates() current_time: " + now);

			long rssCheckTime = getRSSms();
			LogMessage("WeeWXAppCommon.processUpdates() rssCheckTime: " + rssCheckTime);

			long lastAttemptedForecastDownload = getLAFDms();

			if(doForecast)
			{
				if(rssCheckTime == 0)
				{
					if(!hasForecastGson)
						LogMessage("WeeWXAppCommon.processUpdates() hasForecastGson is false, skipping...", KeyValue.e);
					else
						LogMessage("WeeWXAppCommon.processUpdates() hasForecastGson is true, skipping...", KeyValue.e);

					doForecast = false;
				}
			}

			if(doForecast)
			{
				long dur = (now - lastAttemptedForecastDownload) / 1000;
				if(!forced && lastAttemptedForecastDownload > 0 && dur < fcDef.delay_before_downloading)
				{
					LogMessage("WeeWXAppCommon.processUpdates() !forced and last attempt was less than " + fcDef.delay_before_downloading +
							   "s ago (" + dur + "s ago)");
					doForecast = false;
				}
			}

			if(doForecast)
			{
				long dur = (now - rssCheckTime) / 1000;
				if(!forced && dur < fcDef.default_forecast_refresh)
				{
					LogMessage("WeeWXAppCommon.processUpdates() !forced and cache isn't more than " +
							   fcDef.default_forecast_refresh + "s old (" + dur + "s ago), skipping...");
					doForecast = false;
				}
			}

			if(doForecast)
			{
				forecastURL = (String)KeyValue.readVar("FORECAST_URL", "");
				if(is_valid_url(forecastURL))
				{
					idtype.add(3);
					urls.add(forecastURL);
					contentTypes.add("HTML");
					PossibleErrors.add(R.string.wasnt_able_to_connect_forecast);
					KeyValue.putVar("lastAttemptedForecastDownloadTime", now);
				}
			}

			if(radar)
			{
				String radtype = (String)KeyValue.readVar("radtype", WeeWXApp.radtype_default);
				if(radtype != null && radtype.equals("image"))
				{
					String radarURL = (String)KeyValue.readVar("RADAR_URL", "");
					if(!is_blank(radarURL))
					{
						idtype.add(4);
						urls.add(radarURL);
						contentTypes.add("IMAGE");
						PossibleErrors.add(R.string.wasnt_able_to_connect_radar_image);
					}
				}
			}

			if(webcam)
			{
				String webcam_url = (String)KeyValue.readVar("WEBCAM_URL", "");
				if(!is_blank(webcam_url))
				{
					idtype.add(5);
					urls.add(webcam_url);
					contentTypes.add("IMAGE");
					PossibleErrors.add(R.string.wasnt_able_to_connect_webcam_url);
				}
			}

			if(urls.isEmpty())
			{
				if(errorCount() > 0)
				{
					LogMessage("sending UPDATE_ERRORS intent...");
					updateNotificationMessage(UPDATE_ERRORS);
					LogMessage("sent UPDATE_ERRORS intent...");
				}

				LogMessage("WeeWXAppCommon.processUpdates() No jobs to run...", KeyValue.w);
				if(weather)
					updateNotificationMessage(STOP_WEATHER_INTENT);

				if(forecast)
					updateNotificationMessage(STOP_FORECAST_INTENT);

				if(radar)
					updateNotificationMessage(STOP_RADAR_INTENT);

				if(webcam)
					updateNotificationMessage(STOP_WEBCAM_INTENT);

				return;
			}

			boolean updatedWeather = false;
			boolean updatedForecast = false;
			boolean updatedRadar = false;
			boolean updatedWebcam = false;

			downloader = new ParallelDownloader(urls.size(), "processUpdates");
			List<ParallelDownloader.DownloadResult> results = downloader.downloadAll(idtype, urls, contentTypes);

			boolean allOk = results.stream().allMatch(ParallelDownloader.DownloadResult::success);
			if(!allOk)
			{
				List<ParallelDownloader.DownloadResult> failed = results.stream().filter(r -> !r.success()).toList();
				for(ParallelDownloader.DownloadResult r : failed)
				{
					LogMessage("WeeWXAppcommon.processUpdates(" + r.id() + ") Error! " + r.error(), KeyValue.e);
					for(int i = 0; i < PossibleErrors.size(); i++)
					{
						if(idtype.get(i) == r.id())
						{
							Object obj = PossibleErrors.get(i);
							if(obj instanceof Object[] objects)
							{
								int resid = (int)objects[0];
								Object[] object = (Object[])objects[1];
								noteError(resid, object);
							} else if(obj instanceof Integer resid) {
								noteError(resid);
							}

							break;
						}
					}
				}
			} else {
				int UpdateInterval = (int)KeyValue.readVar("UpdateInterval", 0);
				int modhour = getIntervalTime(UpdateInterval)[1];

				boolean needToMerge = false;
				List<ParallelDownloader.DownloadResult> succeeded = results.stream().toList();
				for(ParallelDownloader.DownloadResult r : succeeded)
				{
					LogMessage("processUpdates() r.id: " + r.id());
					if(0 <= r.id() && r.id() <= 2)
					{
						Boolean ret = processWeather(r.id(), r.string());
						if(Boolean.TRUE.equals(ret))
						{
							if(r.id() != 1)
							{
								updatedWeather = true;
								needToMerge = true;
							} else if(KeyValue.parseDicts()) {
								LogMessage("Failed to process inigo-dicts.json file", KeyValue.e);
								noteError(R.string.failed_to_process_units, new Object[]{json_labels[r.id()]});
							}
						} else if(Boolean.FALSE.equals(ret)) {
							LogMessage("Failed to process " + json_labels[r.id()] + " file", KeyValue.w);
						} else {
							LogMessage("Failed to process " + json_labels[r.id()] + " file", KeyValue.e);
							noteError(R.string.failed_to_process_weather_data, new Object[]{json_labels[r.id()]});
						}
					}

					if(r.id() == 3)
					{
						Result3 r3 = processForecast(modhour, fctype, r.string(), forecastURL);

						if(!r3.succeeded())
							noteError(r3.error());
						else
							updatedForecast = true;
					}

					if(r.id() == 4 && r.contentType().equals("IMAGE"))
					{
						File file = getFile(getDataDir(), getFileNameFromURL(r.url()));
						try(FileOutputStream out = new FileOutputStream(file))
						{
							LogMessage("Attempting to save to " + file.getAbsoluteFile());
							out.write(r.bytes());
							LogMessage("1Got past the save... ");
							updatedRadar = true;
						} catch(Exception e) {
							LogMessage(ERROR_E + e, KeyValue.e);
							doStackOutput(e);
							noteError(e);
						}
					}

					if(r.id() == 5 && r.bytes() != null)
					{
						File file = getFile(getDataDir(), WeeWXApp.webcamFilename);
						try
						{
							// BitmapFactory detects JPEG, PNG and supported WebP data by content,
							// regardless of the URL extension or our legacy webcam.jpg cache name.
							BitmapFactory.Options bounds = new BitmapFactory.Options();
							bounds.inJustDecodeBounds = true;
							BitmapFactory.decodeByteArray(r.bytes(), 0, r.bytes().length, bounds);
							if(bounds.outWidth <= 0 || bounds.outHeight <= 0)
								throw new IOException("Webcam response is not a supported still image");

							// Identical snapshots need neither a disk write nor a UI redraw.
							boolean changed = !file.exists() || !java.util.Arrays.equals(
									r.bytes(), java.nio.file.Files.readAllBytes(file.toPath()));
							if(changed)
							{
								try(FileOutputStream out = new FileOutputStream(file))
								{
									out.write(r.bytes());
								}
								updatedWebcam = true;
							}

							setWebcamFileTime(file, r.lastModified());
						} catch(Exception e) {
							LogMessage(ERROR_E + e, KeyValue.e);
							doStackOutput(e);
							noteError(e);
						}
					}
				}

				if(needToMerge && notMergeJsonObjects())
					noteError(R.string.failed_to_merge_weather_data, new Object[]{json_labels[0], json_labels[2]});

				downloader = null;

				if(updatedWeather)
				{
					checkTempAlerts();
					checkRainfallAlert();
					checkRainrateAlert();
					updateAppWidget();
				}
			}

			if(errorCount() > 0)
			{
				LogMessage("WeeWXAppcommon.processUpdates() Line 3621");

				LogMessage("sending UPDATE_ERRORS intent...");
				updateNotificationMessage(UPDATE_ERRORS);
				LogMessage("sent UPDATE_ERRORS intent...");

				if(weather)
					updateNotificationMessage(STOP_WEATHER_INTENT);

				if(forecast)
					updateNotificationMessage(STOP_FORECAST_INTENT);

				if(radar)
					updateNotificationMessage(STOP_RADAR_INTENT);

				if(webcam)
					updateNotificationMessage(STOP_WEBCAM_INTENT);
			} else {
				if(updatedWeather)
				{
					LogMessage("sending REFRESH_WEATHER_INTENT intent...");
					updateNotificationMessage(REFRESH_WEATHER_INTENT);
					LogMessage("sent REFRESH_WEATHER_INTENT intent...");
				} else if(weather)
					updateNotificationMessage(STOP_WEATHER_INTENT);

				if(updatedForecast)
				{
					LogMessage("sending REFRESH_FORECAST_INTENT intent...");
					updateNotificationMessage(REFRESH_FORECAST_INTENT);
					LogMessage("sent REFRESH_FORECAST_INTENT intent...");
				} else if(forecast)
					updateNotificationMessage(STOP_FORECAST_INTENT);

				if(updatedRadar)
				{
					LogMessage("sending REFRESH_RADAR_INTENT intent...");
					updateNotificationMessage(REFRESH_RADAR_INTENT);
					LogMessage("sent REFRESH_RADAR_INTENT intent...");
				} else if(radar)
					updateNotificationMessage(STOP_RADAR_INTENT);

				if(updatedWebcam)
				{
					LogMessage("sending REFRESH_WEBCAM_INTENT intent...");
					updateNotificationMessage(REFRESH_WEBCAM_INTENT);
					LogMessage("sent REFRESH_WEBCAM_INTENT intent...");
				} else if(webcam)
					updateNotificationMessage(STOP_WEBCAM_INTENT);
			}
		} catch(Exception e) {
			LogMessage("WeeWXAppcommon.processUpdates() Error! e: " + e.getMessage(), KeyValue.e);
			doStackOutput(e);
		}
	}

	static String getFileNameFromURL(String url)
	{
		if(!is_valid_url(url))
			return null;

		HttpUrl httpUrl = HttpUrl.parse(url);
		if(httpUrl == null)
			return null;

		List<String> paths = httpUrl.pathSegments();
		return paths.get(paths.size() - 1);
	}

	static Boolean processWeather(int id, String weatherStr) throws JSONException
	{
		if(is_blank(weatherStr))
			return false;

		int error_count = 0;
		JSONObject jsonObject = new JSONObject(weatherStr);

		if(jsonObject.length() == 0 || !jsonObject.has("version"))
		{
			LogMessage("processWeather() jsonObject.length() == 0 || jsonObject didn't have a version");
			return false;
		}

		int version = jsonObject.optInt("version", 0);
		if(id == 0 && version < WeeWXApp.minimum_inigo_version)
		{
			LogMessage("processWeather() sendAlert() triggered because version (" + version +
					   ") < WeeWXApp.minimum_inigo_version (" + WeeWXApp.minimum_inigo_version + ")");
			updateNotificationMessage(INIGO_INTENT);
			return false;
		}

		if(jsonObject.has("processingErrors"))
		{
			JSONArray jarr = jsonObject.optJSONArray("processingErrors");
			if(jarr != null)
			{
				for(int i = 0; i < jarr.length(); i++)
				{
					JSONObject jobj = jarr.getJSONObject(i);
					if(!jobj.has("type") || !jobj.getString("type").equals("error"))
						continue;

					error_count++;
					LogMessage("processWeather() Error in " + json_labels[id] + ": " + jarr.optString(i) +
						", msg: " + jobj.getString("msg"), KeyValue.e);
				}

				if(error_count > 0)
				{
					KeyValue.putVar("ProcessingErrorCount", error_count);
					KeyValue.putVar("ProcessingErrorID", id);
				}
			}
		}

		long now = System.currentTimeMillis();

		if(id == 2)
			carryOverRainSums(jsonObject);

		KeyValue.putVar(json_keys[id] + TIME_EXT, now);
		KeyValue.putVar(json_keys[id] + "_str", jsonObject.toString());
		KeyValue.putVar("LastWeatherError", null);

		LogMessage("processWeather() Last Server Update Time: " + WeeWXApp.getInstance().sdf14.format(jsonObject.optInt("report_time") * 1_000L));
		LogMessage("processWeather() LastDownloadTime: " + WeeWXApp.getInstance().sdf14.format(now));

		if(error_count == 0)
			return true;

		return null;
	}

	//	https://stackoverflow.com/questions/3841317/how-do-i-see-if-wi-fi-is-connected-on-android
	static boolean notCheckConnection()
	{
		if(!(boolean)KeyValue.readVar("onlyWIFI", WeeWXApp.onlyWIFI_default))
			return false;

		ConnectivityManager connMgr = (ConnectivityManager)WeeWXApp.getInstance().getSystemService(Context.CONNECTIVITY_SERVICE);
		if(connMgr == null)
			return true;

		Network network = connMgr.getActiveNetwork();
		if(network == null)
			return true;

		NetworkCapabilities capabilities = connMgr.getNetworkCapabilities(network);
		return capabilities == null || !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
	}

	// https://stackoverflow.com/questions/8710515/reading-an-image-file-into-bitmap-from-sdcard-why-am-i-getting-a-nullpointerexc
	private static Bitmap combineImage(Bitmap bmp1, String fnum, String snum)
	{
		Context context = WeeWXApp.getInstance();
		if(context == null)
			return null;

		try
		{
			int x1 = bmp1.getHeight();
			int y1 = bmp1.getWidth();

			Bitmap bmp = bmp1.copy(Bitmap.Config.ARGB_8888, true);

			Paint paint = new Paint();
			paint.setAntiAlias(true);
			Canvas comboImage = new Canvas(bmp);
			comboImage.drawBitmap(bmp1, 0f, 0f, null);

			if(!fnum.equals("%") || !snum.equals("%"))
			{
				Bitmap bmp2 = loadImage();
				if(bmp2 == null)
					return null;

				paint = new Paint();
				paint.setAlpha(100);
				comboImage.drawBitmap(bmp2, 0f, bmp1.getHeight() - bmp2.getHeight(), paint);
			}

			if(!fnum.equals("%") && !snum.equals("%"))
			{
				// Draw arrow
				paint = new Paint();
				paint.setAntiAlias(true);
				paint.setColor(ContextCompat.getColor(context, R.color.LightPrussianBlue));
				paint.setStyle(Paint.Style.STROKE);
				paint.setStrokeWidth(1);
				comboImage.drawLine( Math.round(x1 / 2.0) + 5, y1 - 9, Math.round(x1 / 2.0) + 10, y1 - 7, paint);
				comboImage.drawLine( Math.round(x1 / 2.0) - 10, y1 - 7, Math.round(x1 / 2.0) + 10, y1 - 7, paint);
				comboImage.drawLine( Math.round(x1 / 2.0) + 5, y1 - 5, Math.round(x1 / 2.0) + 10, y1 - 7, paint);
			}

			if(!fnum.equals("%"))
			{
				paint = new Paint();
				paint.setAntiAlias(true);
				paint.setColor(ContextCompat.getColor(context, R.color.LightPrussianBlue));
				paint.setTextSize(13);
				paint.setTypeface(tf_bold);

				comboImage.drawText(fnum, 0, y1 - 2, paint);
			}

			if(!snum.equals("%"))
			{
				paint = new Paint();
				paint.setAntiAlias(true);
				paint.setColor(ContextCompat.getColor(context, R.color.LightPrussianBlue));
				paint.setTextSize(13);
				paint.setTypeface(tf_bold);

				Rect textBounds = new Rect();
				paint.getTextBounds(snum, 0, snum.length(), textBounds);
				int width = (int) paint.measureText(snum);

				comboImage.drawText(snum, x1 - width, y1 - 2, paint);
			}

			return bmp;
		} catch(Exception e) {
			doStackOutput(e);
		}

		return null;
	}

	private static Bitmap combineImages(Bitmap bmp1, Bitmap bmp2, String fimg, String simg, String fnum, String snum)
	{
		Context context = WeeWXApp.getInstance();

		try
		{
			int x1 = bmp1.getHeight();
			int y1 = bmp1.getWidth();

			int x2 = bmp2.getHeight();
			int y2 = bmp2.getWidth();

			if(nws == null)
			{
				loadNWS();
				LogMessage("Loaded NWS");
			}

			String[] bits = nws.getString(fimg).split("\\|");
			switch (bits[1].strip())
			{
				case "L" -> bmp1 = Bitmap.createBitmap(bmp1, 0, 0, x1 / 2, y1);
				case "R" -> bmp1 = Bitmap.createBitmap(bmp1, x1 / 2, 0, x1, y1);
				default -> bmp1 = Bitmap.createBitmap(bmp1, x1 / 4, 0, x1 * 3 / 4, y1);
			}

			bits = nws.getString(simg).split("\\|");
			switch (bits[1].strip())
			{
				case "L" -> bmp2 = Bitmap.createBitmap(bmp2, 0, 0, x2 / 2, y2);
				case "R" -> bmp2 = Bitmap.createBitmap(bmp2, x2 / 2, 0, x2, y2);
				default -> bmp2 = Bitmap.createBitmap(bmp2, x2 / 4, 0, x2 * 3 / 4, y2);
			}

			Bitmap bmp = Bitmap.createBitmap(x1, y1, Bitmap.Config.ARGB_8888);
			bmp = bmp.copy(Bitmap.Config.ARGB_8888, true);

			Paint paint = new Paint();
			paint.setAntiAlias(true);
			Canvas comboImage = new Canvas(bmp);
			comboImage.drawBitmap(bmp1, 0f, 0f, null);
			comboImage.drawBitmap(bmp2, Math.round(x1 / 2.0), 0f, null);

			paint.setColor(ContextCompat.getColor(context, R.color.Black));
			paint.setStyle(Paint.Style.STROKE);
			paint.setStrokeWidth(4);
			comboImage.drawLine( Math.round(x1 / 2.0), 0, Math.round(x1 / 2.0), y1, paint);

			paint.setColor(ContextCompat.getColor(context, R.color.White));
			paint.setStyle(Paint.Style.STROKE);
			paint.setStrokeWidth(2);
			comboImage.drawLine( Math.round(x1 / 2.0), 0, Math.round(x1 / 2.0), y1, paint);

			if(!fnum.equals("%") || !snum.equals("%"))
			{
				Bitmap bmp4 = loadImage();
				if(bmp4 == null)
					return null;

				paint = new Paint();
				paint.setAlpha(100);
				comboImage.drawBitmap(bmp4, 0f, bmp1.getHeight() - bmp4.getHeight(), paint);
			}

			if(!fnum.equals("%") && !snum.equals("%"))
			{
				// Draw arrow
				paint = new Paint();
				paint.setAntiAlias(true);
				paint.setColor(ContextCompat.getColor(context, R.color.LightPrussianBlue));
				paint.setStyle(Paint.Style.STROKE);
				paint.setStrokeWidth(1);
				comboImage.drawLine( Math.round(x1 / 2.0) + 5, y1 - 9, Math.round(x1 / 2.0) + 10, y1 - 7, paint);
				comboImage.drawLine( Math.round(x1 / 2.0) - 10, y1 - 7, Math.round(x1 / 2.0) + 10, y1 - 7, paint);
				comboImage.drawLine( Math.round(x1 / 2.0) + 5, y1 - 5, Math.round(x1 / 2.0) + 10, y1 - 7, paint);
			}

			if(!fnum.equals("%"))
			{
				paint = new Paint();
				paint.setAntiAlias(true);
				paint.setColor(ContextCompat.getColor(context, R.color.LightPrussianBlue));
				paint.setTextSize(13);
				paint.setTypeface(tf_bold);

				comboImage.drawText(fnum, 2, y1 - 2, paint);
			}

			if(!snum.equals("%"))
			{
				paint = new Paint();
				paint.setAntiAlias(true);
				paint.setColor(ContextCompat.getColor(context, R.color.LightPrussianBlue));
				paint.setTextSize(13);
				paint.setTypeface(tf_bold);

				Rect textBounds = new Rect();
				paint.getTextBounds(snum, 0, snum.length(), textBounds);
				int width = (int) paint.measureText(snum);

				comboImage.drawText(snum, x1 - width - 2, y1 - 2, paint);
			}

			return bmp;
		} catch(Exception e) {
			doStackOutput(e);
		}

		return null;
	}

	// https://stackoverflow.com/questions/1540272/android-how-to-overlay-a-bitmap-draw-over-a-bitmap

	private static Bitmap overlay(Bitmap bmp1, Bitmap bmp2, String s)
	{
		Paint paint = new Paint();
		paint.setAlpha(100);

		Bitmap.Config btmp = bmp1.getConfig();
		if(btmp == null)
			btmp = Bitmap.Config.ARGB_8888;

		Bitmap bmp3 = Bitmap.createBitmap(bmp1.getWidth(), bmp1.getHeight(), btmp);
		Canvas canvas = new Canvas(bmp3);
		canvas.drawBitmap(bmp1, 0f, 0f, null);
		canvas.drawBitmap(bmp2, 0f, bmp1.getHeight() - bmp2.getHeight(), paint);
		paint.setAntiAlias(true);
		paint.setColor(ContextCompat.getColor(WeeWXApp.getInstance(), R.color.LightPrussianBlue));
		paint.setTextSize(13);
		paint.setTypeface(tf_bold);
		Rect textBounds = new Rect();
		paint.getTextBounds(s, 0, s.length(), textBounds);
		int width = (int)paint.measureText(s);

		canvas.drawText(s, bmp1.getWidth() - width, bmp1.getHeight() - 5, paint);

		return bmp3;
	}

	// https://stackoverflow.com/questions/19945411/android-java-how-can-i-parse-a-local-json-file-from-assets-folder-into-a-listvi

	private static void loadNWS()
	{
		try
		{
			InputStream is = WeeWXApp.openRawResource(R.raw.nws);
			int size = is.available();
			byte[] buffer = new byte[size];
			if(is.read(buffer) > 0)
				nws = new JSONObject(new String(buffer, StandardCharsets.UTF_8));
			is.close();
		} catch(Exception e) {
			doStackOutput(e);
		}
	}

	static String downloadSettings(String url) throws IOException
	{
		KeyValue.putVar("SETTINGS_URL", url);

		String cfg = downloadString(url);

		if(cfg == null)
			return null;

		if(cfg.startsWith(UTF8_BOM))
			cfg = cfg.substring(1).strip();

		return cfg;
	}

	static String downloadString(String url) throws IOException
	{
		if(!is_valid_url(url))
			return null;

		HttpUrl url2 = HttpUrl.parse(url);
		if(url2 == null)
			return null;

		OkHttpClient client = getInstance(url2);

		return reallyDownloadString(client, url2);
	}

	private static String reallyDownloadString(OkHttpClient client, HttpUrl url) throws IOException
	{
		LogMessage("reallyDownloadString() checking if url  " + url + " is valid");
		Request request = NetworkClient.getRequest(false, url, true);

		try(Response response = client.newCall(request).execute())
		{
			String bodyStr = response.body().string();

			LogMessage("response: " + response);
			//LogMessage("Returned string: " + bodyStr);
			//LogMessage("response.code(): " + response.code());

			if(!response.isSuccessful())
			{
				String error = "HTTP error " + response;
				if(!is_blank(bodyStr))
					error += ", body: " + bodyStr;
				LogMessage("reallyDownloadString() Error! error: " + error, true, KeyValue.w);
				throw new IOException(error);
			}

			return bodyStr;
		}
	}

	static String downloadString(String url, Map<String, String> args) throws IOException
	{
		if(is_blank(url) || args == null || args.isEmpty())
		{
			LogMessage("downloadString(url, args) Attempted uploading nothing, skipping...", true, KeyValue.d);
			return null;
		}

		FormBody.Builder fb = new FormBody.Builder();

		for(Map.Entry<String, String> arg: args.entrySet())
			fb.add(arg.getKey(), arg.getValue());

		fb.add("appName", WeeWXApp.APPLICATION_ID);
		fb.add("appVersion", WeeWXApp.VERSION_NAME);

		RequestBody requestBody = fb.build();

		HttpUrl url2 = HttpUrl.parse(url);
		if(url2 == null)
			return null;

		OkHttpClient client = getInstance(url2);

		return reallyDownloadString(client, requestBody, url2);
	}

	private static String reallyDownloadString(OkHttpClient client, RequestBody requestBody, HttpUrl url) throws IOException
	{
		LogMessage("reallyDownloadString() checking if url  " + url + " is valid");

		Request request = NetworkClient.getRequest(false, url, true)
			.newBuilder()
			.post(requestBody)
			.build();

		try(Response response = client.newCall(request).execute())
		{
			String bodyStr = response.body().string();
			if(response.isSuccessful())
			{
				LogMessage("reallyDownloadString(url, args) Successfully uploaded something... response: " + bodyStr);
				return bodyStr;
			} else {
				LogMessage("reallyDownloadString(url, args) Failed to upload something... response: " + bodyStr, true, KeyValue.w);
			}
		} catch(Exception e) {

//			if(false)
//			{
//				if(retries < maximum_retries)
//				{
//					retries++;
//
//	//				LogMessage("reallyDownloadString() Error! e: " + e.getMessage() + ", retry: " + retries +
//	//						   ", will sleep " + retry_sleep_time + " seconds and retry...", true);
//
//					try
//					{
//						Thread.sleep(retry_sleep_time);
//					} catch (InterruptedException ie) {
//						Thread.currentThread().interrupt();
//						throw ie;
//					}
//
//					return reallyDownloadString(client, requestBody, url, retries);
//				}
//			}

			//LogMessage("reallyDownloadString(url, args) Error! e: " + e.getMessage(), true, KeyValue.e);

			doStackOutput(e);
			throw e;
		}

		return null;
	}

	static void uploadMissingIcon(Map<String,String> args)
	{
		HttpUrl url = HttpUrl.parse(WeeWXApp.missingIconURL);
		if(url == null)
			return;

		FormBody.Builder fb = new FormBody.Builder();

		LogMessage("uploadMissingIcon() Adding appName and appVersion to FormBody");

		fb.add("appName", WeeWXApp.APPLICATION_ID);
		fb.add("appVersion", WeeWXApp.VERSION_NAME);

		for(Map.Entry<String, String> arg : args.entrySet())
		{
			LogMessage("uploadMissingIcon() Adding " + arg.getKey() + "=" + arg.getValue() + " to fb...");

			if(arg.getKey().equals("svgName"))
			{
				if(arg.getValue().equalsIgnoreCase("null") ||
						arg.getValue().toLowerCase(Locale.ENGLISH).contains("null.svg"))
					return;

				if(processedMissingIcons.contains(arg.getValue()))
					return;
				else
					processedMissingIcons.add(arg.getValue());
			}

			if(!arg.getKey().equalsIgnoreCase("appName") &&
			   !arg.getKey().equalsIgnoreCase("appVersion"))
				fb.add(arg.getKey(), arg.getValue());
		}

		executor.submit(() ->
		{
			RequestBody requestBody = fb.build();

			OkHttpClient client = getInstance(url);

			LogMessage("uploadMissingIcon() Sending everything to reallyUploadMissingIcon()");

			reallyUploadMissingIcon(client, requestBody, url, 0);
		});
	}

	private static void reallyUploadMissingIcon(OkHttpClient client, RequestBody requestBody, HttpUrl url, int retries)
	{
		Exception lastException = null;

		LogMessage("reallyUploadString() checking if url " + url + " is valid, attempt: #" + (retries + 1));

		Request request = NetworkClient.getRequest(false, url, true)
			.newBuilder().
			post(requestBody).
			build();

		try(Response response = client.newCall(request).execute())
		{
			String bodyStr = response.body().string();
			if(response.isSuccessful())
			{
				if(bodyStr.equals("OK"))
				{
					LogMessage("reallyUploadString() Map uploaded successfully...");
					return;
				}

				LogMessage("reallyUploadString() Error from server: " + bodyStr + ", retry: " + retries +
						   ", will sleep " + retry_sleep_time + " seconds and retry...", true, KeyValue.w);
			} else
				LogMessage("reallyUploadString() Failed to upload something... response code: " + response.code() +
						   ", body: " + bodyStr, true, KeyValue.w);
		} catch(Exception e) {
			lastException = e;
		}

		if(lastException != null)
		{
			if(retries < maximum_retries)
			{
				retries++;

				LogMessage("reallyUploadString() Error! lastException: " + lastException.getMessage() + ", retry: #" + retries +
							   ", will sleep " + Math.round(retry_sleep_time / 1_000D) + "s and then retry...", true, KeyValue.w);

				try
				{
					Thread.sleep(retry_sleep_time);
				} catch (InterruptedException ie) {
					LogMessage("reallyUploadString() Error! ie: " + ie.getMessage(), true, KeyValue.e);
					Thread.currentThread().interrupt();
					return;
				}

				reallyUploadMissingIcon(client, requestBody, url, retries);
				return;
			}
		}

		if(lastException != null)
		{
			LogMessage("reallyUploadString() Error! lastException: " + lastException.getMessage(), true, KeyValue.e);
			doStackOutput(lastException);
		}
	}

	static void publish(File f)
	{
		if(f.exists())
		{
			LogMessage("Let's tell Android about: " + f.getAbsolutePath());

			MediaScannerConnection.scanFile(WeeWXApp.getInstance(),
					new String[]{f.getAbsolutePath()}, null, null);
		} else
			LogMessage(f.getAbsolutePath() + " doesn't exist, skipping...");
	}

	static int getNextRandom(int min, int max)
	{
		int random;

		do
		{
			random = ThreadLocalRandom.current().nextInt(max);
		} while(random < min || random > max);

		return random;
	}

	@WorkerThread
	static String downloadWZHTML(String url, boolean addDelay, boolean fcString) throws IOException
	{
		if(addDelay)
		{
			try
			{
				int delay = getNextRandom(5_000, 10_000);
				Thread.sleep(delay);
			} catch(Exception ignored) {}
		}

		WebViewPreloader wvpl = new WebViewPreloader();

		if(fcString)
			return wvpl.getHTML(
				url,
				new String[]{"Districts"},
				new String[]{"Page Not found", "Oops! We can't find the page you're looking for. Please check the URL you entered or"},
				new String[]{"Oops! Unfortunately, there was an error on the page.", "Oops! We can't find the page you're looking for."}
			);

		return wvpl.getHTML(
			url,
			new String[]{"UPDATED", "Daily Forecast", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"},
			new String[]{"Page Not found", "Oops! We can't find the page you're looking for. Please check the URL you entered or"},
			new String[]{"Oops! Unfortunately, there was an error on the page.", "Oops! We can't find the page you're looking for."}
		);
	}

	@WorkerThread
	static String getWZHTML(String url, boolean fcString) throws IOException
	{
		String wzHTML;
		boolean addDelay = false;
		int attempt = 1;
		int attempts = 10;
		do
		{
			LogMessage("Starting attempt #" + attempt + " of " + attempts);
			wzHTML = downloadWZHTML(url, addDelay, fcString);
			if(wzHTML == null || wzHTML.startsWith("error|"))
				break;
			addDelay = true;
		} while(wzHTML.length() < 10_000 && attempt++ < attempts);

		if(wzHTML != null)
			LogMessage("wzHTML.length(): " + wzHTML.length());

		return wzHTML;
	}

	@SuppressWarnings("unused")
	static String prettyHTML(String html)
	{
		return Jsoup.parse(html).outputSettings(new Document.OutputSettings()
				.indentAmount(2).prettyPrint(true)).outerHtml();
	}

	static GsonHelper String2Gson(String forecastGson, int modhour)
	{
		boolean hasForecastGson = forecastGson != null && forecastGson.length() > 128;

		if(!hasForecastGson)
			return null;

		LogMessage("WeeWXAppCommon.String2Gson() forecastGson.length(): " + forecastGson.length());

		GsonHelper gh = new GsonHelper(forecastGson);
		if(gh.days == null || gh.days.isEmpty())
		{
			LogMessage("WeeWXAppCommon.String2Gson() #2 Failed to process WZ forecast data...");
			return null;
		}

		if(!gh.isDaily)
			gh.days = filterByInterval(gh.days, modhour);

		LogMessage("WeeWXAppCommon.String2Gson() displayDays.size(): " + gh.days.size());

		return gh;
	}

	@SuppressWarnings("SameParameterValue")
	static String[] getGsonContent(String forecastGson, boolean showHeader)
	{
		int modhour = getIntervalTime()[1];

		GsonHelper gh = String2Gson(forecastGson, modhour);
		if(gh == null)
			return new String[]{"error", getAndroidString(R.string.failed_to_process_forecast_data)};

		String content = generateForecast(gh.days, gh.timestamp, showHeader, gh.isDaily || modhour == 24);
		if(is_blank(content))
		{
			LogMessage("WeeWXAppCommon.getGsonContent() #3 Failed to process forecast data...");
			return new String[]{"error", getAndroidString(R.string.failed_to_process_forecast_data)};
		}

		LogMessage("WeeWXAppCommon.getGsonContent() content.length(): " + content.length());
//		LogMessage("WeeWXAppCommon.getGsonContent() content: " + content);
		return new String[]{null, content, gh.desc != null ? gh.desc : ""};
	}

	private static List<Day> filterByInterval(List<Day> allEntries, int modhour)
	{
//		LogMessage("filterByInterval(): modhour: " + modhour);
//		LogMessage("filterByInterval(): allEntries: " + allEntries);

		if(allEntries == null || allEntries.isEmpty())
			return allEntries;

		List<Day> filtered = new ArrayList<>();
		Calendar cal = Calendar.getInstance();
		for(int i = 0; i < allEntries.size(); i++)
		{
			Day day = allEntries.get(i);
			cal.setTimeInMillis(day.timestamp);

//			LogMessage("filterByInterval(): DAY_OF_MONTH: " + cal.get(Calendar.DAY_OF_MONTH));
//			LogMessage("filterByInterval(): DAY_OF_YEAR: " + cal.get(Calendar.DAY_OF_YEAR));
//
//			LogMessage("filterByInterval(): HOUR_OF_DAY: " + cal.get(Calendar.HOUR_OF_DAY));
//			LogMessage("filterByInterval(): MINUTE: " + cal.get(Calendar.MINUTE));
//			LogMessage("filterByInterval(): SECOND: " + cal.get(Calendar.SECOND));

			int hour = cal.get(Calendar.HOUR_OF_DAY);
			if(hour % modhour == 0)
				filtered.add(day);
		}

//		LogMessage("filterByInterval(): filtered: " + filtered);

		return filtered;
	}

	@WorkerThread
	static Result3 processWZ2(String url) throws IOException
	{
		String forecast_text_url = url.substring(0, url.lastIndexOf("/"));
		LogMessage("reallyGetForecast() Trying secondary URL for forecasts strings: " + forecast_text_url);

		int attempts = 0;
		Result r1 = null;
		Result2 r2 = null;
		String wzHTML;

		String forecastData;
//		if(DEBUG)
//		{
//			try
//			{
//				wzHTML = CustomDebug.readDebug("R2_body.html");
//				LogMessage("reallyGetForecast() wzHTML: " + wzHTML);
//				if(wzHTML != null && wzHTML.length() > 128)
//				{
//					r2 = JsoupHelper.processWZ2GetForecastStrings(wzHTML);
//				}
//			} catch(Exception e) {
//				LogMessage(ERROR_E + e.getMessage(), KeyValue.e);
//				doStackOutput(e);
//				return new Result3(false, e.getLocalizedMessage(), null);
//			}
//		}

//		if(r2 == null || r2.rc() == 0)
//		{
			do
			{
				wzHTML = getWZHTML(forecast_text_url, true);
				if(wzHTML == null)
					break;

				if(wzHTML.isBlank())
					continue;

				if(wzHTML.startsWith("error|"))
					break;

				if(wzHTML.length() > 128 && KeyValue.debugging_on())
					CustomDebug.writeDebug("R2_body.html", wzHTML);

				LogMessage("reallyGetForecast() Got data from WZ, let's try to find forecast strings in it...");
				r2 = JsoupHelper.processWZ2GetForecastStrings(wzHTML);
			} while(attempts++ < 3 && (r2 == null || r2.rc() == 0));

			if(is_blank(wzHTML))
			{
				LogMessage("Nothing substantial was returned from WZ...", KeyValue.w);
				return new Result3(false, "Nothing substantial was returned from WZ...", null);
			}

			if(wzHTML.startsWith("error|"))
			{
				String[] errors = wzHTML.strip().split("\\|", 2);
				LogMessage("Error! errors[1]: " + errors[1], KeyValue.w);
				return new Result3(false, errors[1], null);
			}
//		}

		if(r2 == null || r2.rc() == 0)
		{
			if(!is_blank(wzHTML))
				CustomDebug.writeDebug("R2_body.html", wzHTML);

			LogMessage("reallyGetForecast() Nothing substantial was returned from WZ...", KeyValue.w);
			return new Result3(false, "Nothing substantial was returned from WZ...", null);
		}

		LogMessage("reallyGetForecast() Got forecast strings from WZ... rc: " + r2.rc());

//		if(DEBUG && KeyValue.debugging_on())
//		{
//			try
//			{
//				forecastData = CustomDebug.readDebug("R1_body.html");
//				LogMessage("reallyGetForecast() forecastData: " + forecastData);
//				if(forecastData != null && forecastData.length() > 128)
//					r1 = JsoupHelper.processWZ2Forecasts(url, forecastData, r2);
//			} catch(Exception e) {
//				LogMessage(ERROR_E + e.getMessage(), KeyValue.e);
//				doStackOutput(e);
//				return new Result3(false, e.getLocalizedMessage(), null);
//			}
//		}

//		if(r1 == null || r1.days() == null || r1.days().isEmpty())
//		{
			attempts = 0;
			do
			{
				forecastData = getWZHTML(url, false);
				if(forecastData == null)
					break;

				if(is_blank(forecastData))
					continue;

				if(forecastData.startsWith("error|"))
					break;

				if(forecastData.length() > 128 && KeyValue.debugging_on())
					CustomDebug.writeDebug("R1_body.html", forecastData);

				LogMessage("reallyGetForecast() Got data from WZ, let's try to find forecast blocks in it...");
				r1 = JsoupHelper.processWZ2Forecasts(url, forecastData, r2);
			} while(attempts++ < 3 && (r1 == null || r1.days() == null || r1.days().isEmpty()));

			if(is_blank(forecastData))
			{
				LogMessage("Nothing substantial was returned from WZ...", KeyValue.w);
				return new Result3(false, "Nothing substantial was returned from WZ...", null);
			}

			if(forecastData.startsWith("error|"))
			{
				String[] errors = forecastData.strip().split("\\|", 2);
				LogMessage("Error! errors[1]: " + errors[1], KeyValue.w);
				KeyValue.putVar("LastForecastError", errors[1]);
				return new Result3(false, errors[1], null);
			}
//		}

		if(r1 == null || r1.days() == null || r1.days().isEmpty())
		{
			if(is_blank(forecastData))
				CustomDebug.writeDebug("R1_body.html", forecastData);

			LogMessage("reallyGetForecast() Failed to find any forecast blocks, giving up...", KeyValue.w);
			return new Result3(false, "Nothing substantial was returned from WZ...", null);
		}

		return new Result3(true, null, r1);
	}

	@WorkerThread
	static Result3 processForecast(int modhour, String fctype, String forecastData, String url) throws IOException
	{
		Result r1 = null;
		if(fctype.equals("weatherzone2"))
		{
			Result3 r3 = processWZ2(url);
			if(!r3.succeeded())
				return r3;

			r1 = r3.result();
		} else {
			if(is_blank(forecastData))
			{
				LogMessage("reallyGetForecast() Failed to get any forecast blocks after 3 attempts... giving up...", KeyValue.w);
				return new Result3(false, getAndroidString(R.string.failed_to_process_forecast_data), null);
			}

			if(forecastData.length() > 128 && KeyValue.debugging_on())
			{
				try
				{
					CustomDebug.writeDebug("forecast1.html", forecastData);
				} catch(Exception e) {
					LogMessage("reallyGetForecast() Debug write failed: " + e.getMessage(), KeyValue.w);
				}
			}
		}

		switch(fctype)
		{
			case "aemet.es" -> r1 = processAEMET(forecastData);
			case "bom2" -> r1 = JsoupHelper.processBoM2(forecastData);
			case "bom3daily" -> r1 = processBoM3Daily(forecastData, true);
			case "bom3hourly" -> r1 = processBoM3(modhour, forecastData, url);
			case "dwd.de" -> r1 = processDWD(forecastData);
			case "tempoitalia.it" -> r1 = JsoupHelper.processTempoItalia(forecastData);
			case "met.ie" -> r1 = processMETIE(forecastData);
			case "met.no" -> r1 = processMetNO(forecastData);
			case "metoffice.gov.uk" -> r1 = processMET(forecastData);
			case "metservice.com" -> r1 = processMetService(forecastData);
			case "openweathermap.org" -> r1 = processOWM(forecastData);
			case "weather.com" -> r1 = processWCOM(forecastData);
			case "weather.gc.ca" -> r1 = JsoupHelper.processWCA(forecastData);
			case "weather.gc.ca-fr" -> r1 = JsoupHelper.processWCAF(forecastData);
			case "weather.gov" -> r1 = processWGOV(forecastData);
			case "weatherzone2" -> {}
			case "wmo.int" -> r1 = processWMO(forecastData);
			case "yahoo" -> r1 = JsoupHelper.processYahoo(forecastData);
			default ->
			{
				LogMessage("reallyGetForecast() Failed to process forecast, fctype is invalid: " + fctype);
				String fmtStr = getAndroidString(R.string.forecast_type_is_invalid, fctype);
				return new Result3(false, fmtStr, null);
			}
		}

		if(r1 == null || r1.days() == null || r1.days().isEmpty())
		{
			LogMessage("reallyGetForecast() Failed to process forecast data, giving up...", KeyValue.w);
			return new Result3(false, getAndroidString(R.string.failed_to_process_forecast_data), null);
		}

		GsonHelper gh = new GsonHelper(r1.days(), r1.desc(),
			r1.timestamp() > 0 ? r1.timestamp() : System.currentTimeMillis(), r1.isDaily());
		String forecastGson = gh.toString();

		LogMessage("reallyGetForecast() Updating forecast cache, forecastGson.length(): " + forecastGson.length());
		KeyValue.putVar("forecastGsonEncoded", forecastGson);
		KeyValue.putVar(RSS_CHECK, gh.timestamp);
		KeyValue.putVar("LastForecastError", null);

		return new Result3(true, null, r1);
	}

	static byte[] bitmapToBytes(Bitmap bm)
	{
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		bm.compress(Bitmap.CompressFormat.JPEG, 75, stream);
		byte[] byteArray = stream.toByteArray();
		bm.recycle();

		return byteArray;
	}

	static String toBase64(byte[] in)
	{
		return removeWS(Base64.encodeToString(in, Base64.DEFAULT));
	}

	static String removeWS(String str)
	{
		return str.replaceAll("[\n\r\t]", "").strip();
	}

	static String indentNonBlankLines(String s)
	{
		String indents = String.join("", Collections.nCopies(2, "\t"));
		StringBuilder out = new StringBuilder();
		try(BufferedReader br = new BufferedReader(new StringReader(s)))
		{
			String line;
			boolean first = true;
			while((line = br.readLine()) != null)
			{
				if (!first) out.append('\n');
				first = false;

				if(line.trim().isBlank())
					out.append(line);  // keep empty lines unchanged
				else
					out.append(indents).append(line);
			}
		} catch (IOException e) {
			LogMessage(ERROR_E + e, KeyValue.e);
			doStackOutput(e);
		}

		return out.toString();
	}

	static File getExtFile(String filename) throws IOException
	{
		File dir = new File(Environment.getExternalStorageDirectory(), "Download");
		dir = new File(dir, WeeWXApp.WEEWX_DIR);

		if(dir.exists() && !dir.isDirectory())
		{
			LogMessage("Something called '" + WeeWXApp.WEEWX_DIR + "' already exist, but it isn't a directory...", KeyValue.w);
			throw new IOException("There is already something named " + WeeWXApp.WEEWX_DIR + " but it's not a directory");
		}

		if(!dir.exists())
		{
			if(!dir.mkdirs())
			{
				LogMessage("Can't make '" + dir.getAbsoluteFile() + "' dir...", KeyValue.w);
				throw new IOException("Tried to create " + dir.getAbsoluteFile() + " but failed to create the directory");
			}

			publish(dir);
		}

		return new File(dir, filename);
	}

	static File getDataDir()
	{
		LogMessage("filesDir: " + WeeWXApp.getInstance().getFilesDir().getAbsolutePath());
		return WeeWXApp.getInstance().getFilesDir();
	}

	static File getDir()
	{
		return new File(getDataDir(), "icons");
	}

	static File getFile(File dir, String filename)
	{
		return new File(dir, filename);
	}

	static Bitmap loadImage()
	{
		return getImage(new File(getDir(), "wgovoverlay.jpg"));
	}

	static Bitmap getImage(File file)
	{
		if(!file.exists())
		{
			LogMessage("getImage() : Error! file " + file.getAbsolutePath() + " doesn't exist", KeyValue.e);
			return null;
		}

		LogMessage("loadImage() Loading: " + file.getAbsolutePath());
		return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
	}

	// Webcam and radar images can be far larger than the screen, so decode them downsampled
	static Bitmap getImage(String filename)
	{
		DisplayMetrics dm = WeeWXApp.getInstance().getResources().getDisplayMetrics();
		// Use the longest side for both, as these images may be shown rotated
		int maxSide = Math.max(dm.widthPixels, dm.heightPixels);
		return getImage(new File(getDataDir(), filename), maxSide, maxSide);
	}

	static Bitmap getImage(File file, int reqWidth, int reqHeight)
	{
		BitmapFactory.Options bounds = getImageBounds(file);
		if(bounds == null)
			return null;

		int inSampleSize = 1;
		while(bounds.outWidth / (inSampleSize * 2) >= reqWidth &&
			  bounds.outHeight / (inSampleSize * 2) >= reqHeight)
			inSampleSize *= 2;

		BitmapFactory.Options opts = new BitmapFactory.Options();
		opts.inSampleSize = inSampleSize;
		opts.inPreferredConfig = Bitmap.Config.ARGB_8888;

		LogMessage("getImage() Loading: " + file.getAbsolutePath() + ", " + bounds.outWidth + "x" +
				   bounds.outHeight + ", inSampleSize: " + inSampleSize);
		return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
	}

	// Reads only the image header, returns null if the file is missing or not a valid image
	static BitmapFactory.Options getImageBounds(String filename)
	{
		return getImageBounds(new File(getDataDir(), filename));
	}

	static BitmapFactory.Options getImageBounds(File file)
	{
		if(!file.exists())
		{
			LogMessage("getImageBounds() : Error! file " + file.getAbsolutePath() + " doesn't exist", KeyValue.e);
			return null;
		}

		BitmapFactory.Options opts = new BitmapFactory.Options();
		opts.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
		if(opts.outWidth <= 0 || opts.outHeight <= 0)
			return null;

		return opts;
	}

	static Activity getActivity()
	{
		Context context = WeeWXApp.getInstance();

		while(context instanceof ContextWrapper)
		{
			if(context instanceof Activity)
				return (Activity)context;

			context = ((ContextWrapper)context).getBaseContext();
		}

		return null;
	}

	static String fiToSVG(String cssname)
	{
		if(cssname.startsWith("<img "))
			return cssname;

		String tmpStr = "<img class='wi'";

		if((int)KeyValue.readVar("theme", WeeWXApp.theme_default) == R.style.AppTheme_WeeWXApp_Dark_Common)
			tmpStr += " style='filter:invert(1);'";

		tmpStr += " src='";

		String tmpImg = "";

		switch(cssname)
		{
			case "flaticon-temperature" -> tmpImg = "glyphs/uniF100.svg";
			case "flaticon-home-page" -> tmpImg = "glyphs/uniF101.svg";
			case "flaticon-women-sunglasses" -> tmpImg = "glyphs/uniF102.svg";
			case "flaticon-windy" -> tmpImg = "glyphs/uniF103.svg";
			case "flaticon-thermometer" -> tmpImg = "glyphs/uniF104.svg";
			case "flaticon-cactus" -> tmpImg = "glyphs/uniF105.svg";
			case "flaticon-cool" -> tmpImg = "glyphs/uniF106.svg";
			case "flaticon-cold" -> tmpImg = "glyphs/uniF107.svg";
			case "flaticon-warm" -> tmpImg = "glyphs/uniF108.svg";
			default -> LogMessage("Invalid FlatIcon String: " + cssname, true, KeyValue.w);
		}

		if(!is_blank(tmpImg))
			tmpImg= "file:///android_asset/" + tmpImg;

		return tmpStr + tmpImg + "'/>";
	}

	@SuppressWarnings("unused")
	static int getDirection(String direction)
	{
		int dir;

		switch(direction.toLowerCase(Locale.ENGLISH).strip())
		{
			case "nne" -> dir = 23;
			case "ne" -> dir = 45;
			case "ene" -> dir = 68;
			case "e" -> dir = 90;
			case "ese" -> dir = 113;
			case "se" -> dir = 135;
			case "sse" -> dir = 158;
			case "s" -> dir = 180;
			case "ssw" -> dir = 203;
			case "sw" -> dir = 225;
			case "wsw" -> dir = 248;
			case "w" -> dir = 270;
			case "wnw" -> dir = 293;
			case "nw" -> dir = 313;
			case "nnw" -> dir = 336;
			default -> dir = 0;
		}

		return dir;
	}

	static String cssToSVG(String cssname)
	{
		if(is_blank(cssname))
			return null;

		return cssToSVG(cssname, null);
	}

	static float C2F(float C)
	{
		return C * 9.0f / 5.0f + 32.0f;
	}

	static float F2C(float F)
	{
		return (F - 32.0f) / 1.8f;
	}

	static String C2Fdeg(int C)
	{
		return Math.round(C2F(C)) + "&deg;F";
	}

	static String F2Cdeg(int F)
	{
		return Math.round(F2C(F)) + "&deg;C";
	}

	static String C2Fdeg(float C)
	{
		return (Math.round(C2F(C) * 10.0) / 10.0) + "&deg;F";
	}

	static String F2Cdeg(float F)
	{
		return (Math.round(F2C(F) * 10.0) / 10.0) + "&deg;C";
	}

	static String C2Fdeground(float C)
	{
		return C2F(C) + "&deg;F";
	}

	static String F2Cdeground(float F)
	{
		return F2C(F) + "&deg;C";
	}

	static int str2Int(String f)
	{
		return Math.round(str2Float(f));
	}

	static float str2Float(String f)
	{
		if(is_blank(f))
			return 0;

		String tmp = f.replaceAll("[^0-9-.]", "").strip();

		try
		{
			return (float)Double.parseDouble(tmp);
		} catch(Exception ignored) {}

		return 0;
	}

	static float mm2in(float mm)
	{
		return mm / 25.4f;
	}

	static float in2mm(float in)
	{
		return in * 25.4f;
	}

	@SuppressWarnings("unused")
	static float mps2kmph(float mps)
	{
		return round(mps * 3.6f);
	}

	@SuppressWarnings("unused")
	static float mps2mph(float mps)
	{
		return round(mps * 2.236936f);
	}

	static float round(float num)
	{
		return BigDecimal.valueOf(num)
				.setScale(1, RoundingMode.HALF_UP)
				.floatValue();
	}

	static double round(double num)
	{
		return BigDecimal.valueOf(num)
				.setScale(1, RoundingMode.HALF_UP)
				.doubleValue();
	}

	static String cssToSVG(String cssname, Integer Angle)
	{
		if(is_blank(cssname))
			return null;

		int dir;

		if(cssname.startsWith("<img "))
			return cssname;

		String tmpStr = "<img class='wi'";

		int theme = (int)KeyValue.readVar("theme", WeeWXApp.theme_default);

		if((cssname.equals("wi-wind-deg") && Angle != null) || theme == R.style.AppTheme_WeeWXApp_Dark_Common)
		{
			tmpStr += " style='";

			if(theme == R.style.AppTheme_WeeWXApp_Dark_Common)
				tmpStr += "filter:invert(1);";

			if(cssname.equals("wi-wind-deg") && Angle instanceof Integer angle)
			{
				dir = angle;

				if(dir < 0 || dir > 359)
					dir = 0;

				tmpStr += "transform:rotate(" + dir + "deg);display: inline-block;";
			}

			tmpStr += "'";
		}

		tmpStr += " src='file:///android_asset/glyphs/" + cssname + ".svg'/>";

		return tmpStr;
	}

	static int[] getIntervalTime()
	{
		int pos = (int)KeyValue.readVar("UpdateInterval", WeeWXApp.UpdateInterval_default);
		return getIntervalTime(pos);
	}

	static int[] getIntervalTime(int pos)
	{
		int IntervalTime;

		switch(pos)
		{
			case 1 -> IntervalTime = 43_200;
			case 2 -> IntervalTime = 21_600;
			case 3 -> IntervalTime = 10_800;
			case 4 -> IntervalTime = 3_600;
			default -> IntervalTime = 86_400;
		}

		//LogMessage("IntervalTime: " + IntervalTime + "s");

		int modhour = Math.round(IntervalTime / 3_600f);

		if(modhour < 1 || modhour > 24)
			modhour = 24;

		return new int[]{pos, modhour, IntervalTime};
	}

	static NPWSLL getNPWSLL()
	{
		long now = System.currentTimeMillis();

		String string_time = WeeWXApp.getInstance().sdf8.format(now);
		LogMessage("getNPWSLL() now: " + string_time);

		long period = 300_000L;
		long wait = 45_000L;

		int pos = (int)KeyValue.readVar(WeeWXApp.UPDATE_FREQUENCY, WeeWXApp.UpdateFrequency_default);
		if(pos > 0)
		{
			switch(pos)
			{
				case 2 -> period *= 2;
				case 3 -> period *= 3;
				case 4 -> period *= 6;
				case 5 -> period *= 12;
			}

			LogMessage("period: " + period);
		}

		long report_time = getLDTms() + wait;

		string_time = WeeWXApp.getInstance().sdf8.format(report_time);
		LogMessage("getNPWSLL() report_time: " + string_time);

		long start = Math.round((double)now / (double)period) * period;

		string_time = WeeWXApp.getInstance().sdf8.format(start);
		LogMessage("getNPWSLL() start: " + string_time);

		start += wait;

		string_time = WeeWXApp.getInstance().sdf8.format(start);
		LogMessage("getNPWSLL() start+wait: " + string_time);

		while(start < now + 3_000L)
			start += period;

		string_time = WeeWXApp.getInstance().sdf8.format(start);
		LogMessage("getNPWSLL() next start: " + string_time);

		long lastStart = start - period;

		string_time = WeeWXApp.getInstance().sdf8.format(lastStart);
		LogMessage("getNPWSLL() lastStart: " + string_time);

		return new NPWSLL(now, period, wait, start, lastStart, report_time);
	}

	// Converts numeric degrees to the 16 configured compass sectors.
	// The 17th label represents an unavailable direction.
	static String compassFromDegrees(Number direction)
	{
		List<String> labels = KeyValue.ordinateNames;
		if(labels.size() != 17 || direction == null)
			return null;

		double degrees = direction.doubleValue();
		if(!Double.isFinite(degrees) || degrees < 0 || degrees > 360)
			return labels.get(16);

		int sector = ((int)Math.floor(((degrees % 360) + 11.25) / 22.5)) % 16;
		return labels.get(sector);
	}

	static String compassUnavailable()
	{
		List<String> labels = KeyValue.ordinateNames;
		return labels.size() == 17 ? labels.get(16) : "N/A";
	}

	static String deg2Str(String direction_element, String speed_element)
	{
		float speed = (float)getJson(speed_element, 0f);

		if(speed <= 0)
			return compassUnavailable();

		return deg2Str(direction_element);
	}

	static String deg2Str(String direction_element)
	{
		String legacy = (String)getJson(direction_element, "N/A");
		String numericKey = direction_element.endsWith("_str")
			? direction_element.substring(0, direction_element.length() - 4)
			: direction_element;

		if(!hasElement(numericKey))
			return legacy;

		Object numericValue = getJson(numericKey, Double.NaN);
		String mapped = numericValue instanceof Number
			? compassFromDegrees((Number)numericValue) : null;
		return mapped != null ? mapped : legacy;
	}
}
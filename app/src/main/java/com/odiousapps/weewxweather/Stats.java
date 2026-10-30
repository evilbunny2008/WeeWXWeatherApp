package com.odiousapps.weewxweather;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.webkit.JavascriptInterface;
import android.webkit.WebMessage;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.slider.Slider;

import org.json.JSONObject;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;


import static com.odiousapps.weewxweather.WeeWXApp.getAndroidString;
import static com.odiousapps.weewxweather.WeeWXAppCommon.LogMessage;
import static com.odiousapps.weewxweather.WeeWXAppCommon.headingTime;
import static com.odiousapps.weewxweather.WeeWXAppCommon.is_blank;
import static com.odiousapps.weewxweather.WeeWXAppCommon.cssToSVG;
import static com.odiousapps.weewxweather.WeeWXAppCommon.deg2Str;
import static com.odiousapps.weewxweather.WeeWXAppCommon.fiToSVG;
import static com.odiousapps.weewxweather.WeeWXAppCommon.formatString;
import static com.odiousapps.weewxweather.WeeWXAppCommon.getDateTimeStr;
import static com.odiousapps.weewxweather.WeeWXAppCommon.getJson;
import static com.odiousapps.weewxweather.WeeWXAppCommon.getSinceHour;
import static com.odiousapps.weewxweather.WeeWXAppCommon.hasElement;
import static com.odiousapps.weewxweather.WeeWXAppCommon.processUpdateInBG;
import static com.odiousapps.weewxweather.WeeWXAppCommon.str2Float;
import static com.odiousapps.weewxweather.WeeWXNotificationManager.observeNotifications;
import static com.odiousapps.weewxweather.WeeWXNotificationManager.removeNotificationObserver;

@SuppressLint("unused")
public class Stats extends Fragment implements Refreshable
{
	private Slider mySlider;
	private int currZoom = 0;
	private View rootView;
	private SafeWebView wv;
	private SwipeRefreshLayout swipeLayout;
	private final ViewTreeObserver.OnScrollChangedListener scl = () -> swipeLayout.setEnabled(wv.getScrollY() == 0);

	boolean pageReady = false;

	@Override
	public void refresh()
	{
		swipeLayout.setRefreshing(true);
		LogMessage("WeeWXAppCommon.getWeather(true, false)...");
		processUpdateInBG(true, true, false, false, false);
	}

	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		LogMessage("Stats.onCreateView()");
		super.onCreateView(inflater, container, savedInstanceState);

		int bgColour = WeeWXApp.getColours().bgColour;

		rootView = inflater.inflate(R.layout.fragment_stats, container, false);

		LinearLayout ll = rootView.findViewById(R.id.stats_ll);
		ll.setBackgroundColor(bgColour);

		swipeLayout = rootView.findViewById(R.id.swipeToRefresh);
		swipeLayout.setOnRefreshListener(this::refresh);

		mySlider = rootView.findViewById(R.id.pageZoom);
		mySlider.setBackgroundColor(bgColour);
		mySlider.addOnChangeListener((slider, value, fromUser) ->
		{
			LogMessage("Current Slider zoom = " + (int)mySlider.getValue() + "%");
			LogMessage("New Slider zoom = " + value + "%");

			if(fromUser && currZoom != (int)value)
			{
				currZoom = (int)value;
				KeyValue.putVar("mySlider", currZoom);
				setZoom(currZoom, true);
			}
		});

		if(wv == null)
		{
			wv = new SafeWebView(WeeWXApp.getInstance());
			wv.addJavascriptInterface(this, "WeeWXApp");
		}

		FrameLayout fl = rootView.findViewById(R.id.webViewFrameLayout);
		fl.addView(wv);

		wv.getViewTreeObserver().addOnScrollChangedListener(scl);

		wv.setOnCustomPageFinishedListener((v, url) ->
		{
			LogMessage("Stats.setOnPageFinishedListener()");

			if(currZoom == 0)
			{
				currZoom = sanitiseZoom((int)KeyValue.readVar("mySlider", WeeWXApp.mySlider_default));
				LogMessage("Stats.setOnPageFinishedListener() currZoom: " + currZoom + "%", KeyValue.d);
				wv.postDelayed(() -> setZoom(currZoom, false), 50);
			}

			stopRefreshing();
		}, false);

		swipeLayout.setRefreshing(true);

		updateFields();

		LogMessage("Stats.onViewCreated()-- adding notification manager...");
		observeNotifications(getViewLifecycleOwner(), notificationObserver);

		return rootView;
	}

	public void onResume()
	{
		super.onResume();

		currZoom = sanitiseZoom((int)KeyValue.readVar("mySlider", WeeWXApp.mySlider_default));

		LogMessage("Stats.onResume() currZoom: " + currZoom + "%", KeyValue.d);

		wv.postDelayed(() -> setZoom(currZoom, false), 50);
	}

	@Override
	public void onDestroyView()
	{
		LogMessage("Stats.onDestroyView()");
		super.onDestroyView();

		removeNotificationObserver(notificationObserver);

		if(wv != null)
		{
			ViewParent parent = wv.getParent();
			if(parent instanceof ViewGroup)
				((ViewGroup)parent).removeView(wv);

			wv.getViewTreeObserver().removeOnScrollChangedListener(scl);

			wv.destroy();

			LogMessage("Stats.onDestroyView() recycled wv...");
		}
	}

	void stopRefreshing()
	{
		if(!swipeLayout.isRefreshing())
			return;

		swipeLayout.post(() -> swipeLayout.setRefreshing(false));
	}

	int sanitiseZoom(int zoom)
	{
		//zoom = Math.round(Math.round(zoom * 10.0f) * 10.0f);

		if(zoom < 50)
			zoom = 100;

		if(zoom > 200)
			zoom = 100;

		return zoom;
	}

	void setZoom(int zoom, boolean fromUser)
	{
		if(mySlider == null || wv == null)
			return;

		if(!fromUser && (int)mySlider.getValue() == zoom)
			return;

		final int finalZoom = sanitiseZoom(zoom);
		final float finalZoomDec = finalZoom / 100.0f;

		String jsCommon = """
			(function()
			{
				if (!document || !document.body || !document.body.style) return -1;

			""";

		String jsBottom = "})();";

		String js1 = jsCommon + "var z = window.getComputedStyle(document.body).zoom;" +
								"return z ? z : -1;" + jsBottom;

		LogMessage("Get Zoom JS: " + js1.replaceAll("[\n\r\t]", " ")
				.replaceAll("\\s+", " "), KeyValue.d);

		String js2 = jsCommon + "document.body.style.zoom = " + finalZoomDec + "; return 'OK';" + jsBottom;

		LogMessage("Set Zoom JS: " + js2.replaceAll("[\n\r\t]", " ")
				.replaceAll("\\s+", " "), KeyValue.d);

		Handler handler = new Handler(Looper.getMainLooper());
		Runnable poll = new Runnable()
		{
			@Override
			public void run()
			{
				wv.evaluateJavascript(js1, value1 ->
				{
					LogMessage("value1: " + value1, KeyValue.i);

					if(is_blank(value1) || value1.equals("null") || value1.equals("-1"))
					{
						handler.postDelayed(this, 100);
						return;
					}

					float f = str2Float(value1);

					LogMessage("f: " + f + ", finalZoomDec: " + finalZoomDec, KeyValue.i);

					if(f == finalZoomDec)
					{
						LogMessage("Current page zoom: " + finalZoom + "%, no change required", KeyValue.d);
						mySlider.setValue(finalZoom);
						return;
					}

					wv.post(() -> wv.evaluateJavascript(js2, value2 ->
					{
						LogMessage("Stats.evaluateJavascript() returned value: " + value2);
						if(!value2.equals("\"OK\""))
						{
							LogMessage("value != OK: " + value2);

							handler.postDelayed(this, 100);
							return;
						}

						LogMessage("New zoom set to value: " + finalZoom + "%", KeyValue.d);
						mySlider.setValue(finalZoom);
					}));
				});
			}
		};

		handler.post(poll);
	}

	private final Observer<String> notificationObserver = str ->
	{
		LogMessage("Stats.notificationObserver: " + str);

		if(str.equals(WeeWXAppCommon.REFRESH_WEATHER_INTENT))
			updateFields();

		if(str.equals(WeeWXAppCommon.STOP_WEATHER_INTENT))
			stopRefreshing();
	};

	private void checkFields(final TextView tv, final String txt)
	{
		if(!tv.getText().toString().equals(txt))
			tv.post(() ->
			{
				tv.setBackgroundColor(WeeWXApp.getColours().bgColour);
				tv.setTextColor(WeeWXApp.getColours().fgColour);
				tv.setText(txt);
			});
	}

	private String createRowLeft()
	{
		return createRowLeft(null, WeeWXApp.emptyField, WeeWXApp.emptyField);
	}

	private String createRowLeft(String class1, String str1, String str2)
	{
		String icon = "";
		if(!is_blank(class1))
			icon = cssToSVG(class1);

		return createRowLeft2(icon, str1, str2);
	}

	private String createRowLeft2(String icon, String str1, String str2)
	{
		return "\t\t<div class='statsDataRow'>\n" +
			   "\t\t\t<div class='statsDataCell left'>" + icon + "</div>\n" +
			   WeeWXApp.currentSpacer +
			   "\t\t\t<div class='statsDataCell midleft'>" + str1 + "</div>\n" +
			   WeeWXApp.currentSpacer +
			   "\t\t\t<div class='statsDataCell midright'>" + str2 + "</div>\n";
	}

	private String createRowRight()
	{
		return createRowRight(null, WeeWXApp.emptyField, WeeWXApp.emptyField);
	}

	private String createRowRight2(String icon, String str3, String str4)
	{
		return "\t\t\t<div class='statsDataCell midleft'>" + str3 + "</div>\n" +
			   WeeWXApp.currentSpacer +
			   "\t\t\t<div class='statsDataCell midright'>" + str4 + "</div>\n" +
			   WeeWXApp.currentSpacer +
			   "\t\t\t<div class='statsDataCell right'>" + icon + "</div>\n" +
			   "\t\t</div>\n\n";
	}

	private String createRowRight(String class2, String str3, String str4)
	{
		String icon = "";
		if(!is_blank(class2))
			icon = cssToSVG(class2);

		return createRowRight2(icon, str3, str4);
	}

	private String createRow(String class1, String class2, String str1,
							 String str2, String str3, String str4)
	{
		if(is_blank(str1) && is_blank(str2) && is_blank(str3) && is_blank(str4))
			return "";

		return createRowLeft(class1, str1, str2) +
			   WeeWXApp.currentSpacer +
			   createRowRight(class2, str3, str4);
	}

	private String createRowLeft(String id, int degree, String str1, String dateTime)
	{
		return "\t\t<div class='statsDataRow'>\n" +
			   "\t\t\t<div class='statsDataCell left'><span id='" + id + "'>" +
			   cssToSVG("wi-wind-deg", degree) + "</span></div>\n" +
			   WeeWXApp.currentSpacer +
			   "\t\t\t<div class='statsDataCell midleft'>" + str1 + "</div>\n" +
			   WeeWXApp.currentSpacer +
			   "\t\t\t<div class='statsDataCell midright'>" + dateTime + "</div>\n";
	}

	private String createRow(String id, int degree, String str1, String dateTime, String since, String str2)
	{
		return createRowLeft(id, degree, str1, dateTime) +
			   WeeWXApp.currentSpacer +
			   createRowRight("wi-umbrella", since, str2);
	}

	private String createSolarUV(String timeperiod, int timeMode)
	{
		String uv = timeperiod + "UV_max";
		String uvWhen = timeperiod + "UV_maxtime";
		String solar = timeperiod + "radiation_max";
		String solarWhen = timeperiod + "radiation_maxtime";
		String key1 = KeyValue.getKeyFromName(solarWhen);
		String key2 = KeyValue.getKeyFromName(uvWhen);
		if((key1 == null || is_blank(key1)) && (key2 == null || is_blank(key2)))
		{
			LogMessage("No solar or UV data, skipping...");
			return "";
		}

		String className = fiToSVG("flaticon-women-sunglasses");
		String out = "";

		if(key2 != null && !key2.equals("404"))
		{
			long UVWhen = Math.round((double)getJson(uvWhen, -1D) * 1_000L);

			String UV = formatString(uv);

			String dateTimeStr = getDateTimeStr(UVWhen, timeMode);
			out += createRowLeft(className, "<p><span class='SmallText' id='" +
				timeperiod + "UV_max'>" + UV + "</span> " + KeyValue.getLabel(uv, "UVI").strip() + "</p>",
				"<span id='" + timeperiod + "UV_maxtime'>" + dateTimeStr + "</span>");
		} else {
			out += createRowLeft();
		}

		out += WeeWXApp.currentSpacer;

		if(key2 != null && !key2.equals("404"))
		{
			long SolarWhen = Math.round((double)getJson(solarWhen, -1D) * 1_000L);

			String SOLAR = formatString(solar);

			String dateTimeStr = getDateTimeStr(SolarWhen, timeMode);
			out += createRowRight(className,
				"<span id='" + timeperiod + "radiation_maxtime'>" + dateTimeStr + "</span>",
				"<p><span class='SmallText' id='" + timeperiod + "radiation_max'>" +
					SOLAR + "</span> " + KeyValue.getLabel(solar, "W/m²") + "</p>");
		} else {
			out += createRowRight();
		}

		return out;
	}

	private String generateSection(int header, String timeperiod)
	{
		int timeMode = 0;
		if(timeperiod.contains("month_"))
			timeMode = 1;
		else if(timeperiod.contains("year_"))
			timeMode = 2;
		else if(timeperiod.equals("alltime_"))
			timeMode = 3;

		StringBuilder sb = new StringBuilder();

		sb.append("\t\t<div class='statsHeader'>\n\t\t\t");
		sb.append(getAndroidString(header));
		sb.append("\n\t\t</div>\n\n");

		String tempSym = KeyValue.getLabel("outTemp", "°C");
		String humSym = KeyValue.getLabel("outHumidity", "%");
		String pressSym = KeyValue.getLabel("barometer", "hPa");
		String speedSym = KeyValue.getLabel("wind", "km/h");
		String rainSym = KeyValue.getLabel("rain", "mm");

		String[] loop = {"outTemp", "dewpoint", "outHumidity", "barometer"};
		String[] syms = {tempSym, tempSym, humSym, pressSym};
		String[] css = {fiToSVG("flaticon-temperature"),
						cssToSVG("wi-raindrop"),
						cssToSVG("wi-humidity"),
						cssToSVG("wi-barometer")};

		for(int i = 0; i < loop.length; i++)
		{
			sb.append(createRow(css[i], css[i], "<p><span class='SmallText' id='" + timeperiod + loop[i] + "_max'>" +
					formatString(timeperiod + loop[i] + "_max") + "</span>" + syms[i] + "</p>",
					"<span id='" + timeperiod + loop[i] + "_maxtime'>" +
					getDateTimeStr(Math.round((double)getJson(timeperiod + loop[i] + "_maxtime", 0D) * 1_000L), timeMode) +
					"</span>",
				"<span id='" + timeperiod + loop[i] + "_mintime'>" +
					getDateTimeStr(Math.round((double)getJson(timeperiod + loop[i] + "_mintime", 0D) * 1_000L), timeMode) +
					"</span>",
					"<p><span class='SmallText' id='" + timeperiod + loop[i] + "_min'>" +
						formatString(timeperiod + loop[i] + "_min") + "</span>" + syms[i] + "</p>"));
		}

		if((boolean)KeyValue.readVar("showIndoor", WeeWXApp.showIndoor_default))
		{
			long maxtime = Math.round((double)getJson(timeperiod + "inTemp_maxtime", 0D) * 1_000L);
			long mintime = Math.round((double)getJson(timeperiod + "inTemp_mintime", 0D) * 1_000L);
			if(mintime > 0 && maxtime > 0)
				sb.append(createRow(fiToSVG("flaticon-home-page"), fiToSVG("flaticon-home-page"),
						"<p><span class='SmallText' id='" + timeperiod + "inTemp_max'>" +
							formatString(timeperiod + "inTemp_max") + "</span>" + tempSym + "</p>",
						"<span id='" + timeperiod + "inTemp_maxtime'>" + getDateTimeStr(maxtime, timeMode) + "</span>",
						"<span id='" + timeperiod + "inTemp_mintime'>" + getDateTimeStr(mintime, timeMode) + "</span>",
						"<p><span class='SmallText' id='" + timeperiod + "inTemp_min'>" +
							formatString(timeperiod + "inTemp_min") + "</span>" + tempSym + "</p>"));

			maxtime = Math.round((double)getJson(timeperiod + "inHumidity_maxtime", 0D) * 1_000L);
			mintime = Math.round((double)getJson(timeperiod + "inHumidity_mintime", 0D) * 1_000L);
			if(mintime > 0 && maxtime > 0)
				sb.append(createRow(fiToSVG("flaticon-home-page"), fiToSVG("flaticon-home-page"),
					"<p><span class='SmallText' id='" + timeperiod + "inHumidity_max'>" +
						formatString(timeperiod + "inHumidity_max") + "</span>" + humSym + "</p>",
					"<span id='" + timeperiod + "inHumidity_maxtime'>" + getDateTimeStr(maxtime, timeMode) + "</span>",
					"<span id='" + timeperiod + "inHumidity_mintime'>" + getDateTimeStr(mintime, timeMode) + "</span>",
					"<p><span class='SmallText' id='" + timeperiod + "inHumidity_min'>" +
						formatString(timeperiod + "inHumidity_min") + "</span>" + humSym + "</p>"));
		}

		sb.append(createSolarUV(timeperiod, timeMode));

		// "Since" only makes sense for daily rain totals
		String since = "";
		if(timeperiod.equals("day_") || timeperiod.equals("yesterday_"))
			since = getSinceHour(R.string.since);

		boolean hasVec = hasElement(timeperiod + "wind_vecdir");
		boolean hasET = hasElement(timeperiod + "ET_sum");

		if(hasVec || hasET)
		{
			if(hasVec)
				sb.append(createRowLeft2("<span id='" + timeperiod + "wind_vecdir'>" + cssToSVG("wi-wind-deg",
						Math.round((float)getJson(timeperiod + "wind_vecdir", 0f))) + "</span>",
						"<p><span class='SmallText' id='" + timeperiod + "wind_avg'>" + formatString(timeperiod +
							"wind_avg") + "</span>" + speedSym + " <span class='evenSmallerText' id='" + timeperiod +
							"wind_vecdir_str'>" + deg2Str(timeperiod + "wind_vecdir_str", timeperiod + "wind_avg") + "</span></p>",
						getAndroidString(R.string.avg)));
			else
				sb.append(createRowLeft());

			sb.append(WeeWXApp.currentSpacer);

			if(hasET)
				sb.append(createRowRight2(cssToSVG("evaporation"), "ET",
						"<p><span class='SmallText' id='" + timeperiod + "ET_sum'>" + formatString(timeperiod + "ET_sum")
							+ "</span>" + rainSym + "</p>"));
			else
				sb.append(createRowRight());
		}

		sb.append(createRow(timeperiod + "wind_gustdir", Math.round((float)getJson(timeperiod + "wind_gustdir", 0f)),
			"<p><span class='SmallText' id='" + timeperiod + "wind_max'>" + formatString(timeperiod +
				"wind_max") + "</span>" + speedSym + " <span class='evenSmallerText' id='" + timeperiod + "wind_gustdir_str'>" +
				deg2Str(timeperiod + "wind_gustdir_str", timeperiod + "wind_max") + "</span></p>",
		"<span id='" + timeperiod + "wind_maxtime'>" +
				getDateTimeStr(Math.round((double)getJson(timeperiod + "wind_maxtime", 0D) * 1_000L), timeMode) +
				"</span>",
				since, "<p><span class='SmallText' id='" + timeperiod + "rain_sum'>" +
				formatString(timeperiod + "rain_sum") + "</span>" + rainSym + "</p>"));

		LogMessage(timeperiod + "rain_sum: " + formatString(timeperiod + "rain_sum"), KeyValue.i);

		return sb.toString();
	}

	private void updateFields()
	{
		LogMessage("Stats.java updateFields()");

		long report_time = Math.round((double)getJson("report_time", 0D) * 1_000L);

		// Today Stats
		checkFields(rootView.findViewById(R.id.textView), (String)getJson("station_location", ""));
		checkFields(rootView.findViewById(R.id.textView2), headingTime(report_time));

		final StringBuilder sb = new StringBuilder();

		sb.append(WeeWXApp.current_html_headers);

		if(currZoom != 100)
		{
			float newZoom = (float)Math.round(currZoom / 10f) / 10;

			sb.append("\n\t\t<style>\n\t\t\tbody\n\t\t\t{\n\t\t\t\tzoom: ").append(newZoom).append(";\n\t\t\t}\n\t\t</style>\n");
		}

		sb.append(WeeWXApp.script_header)
				.append(WeeWXApp.html_header_rest)
				.append(WeeWXApp.inline_arrow);

		sb.append("\n<div class='statsLayout'>\n\n");

		// Show today's stats
		sb.append("\t<div class='statsSection'>\n");
		sb.append(generateSection(R.string.todayStats, "day_"));
		sb.append("\t\t<hr />\n");
		sb.append("\t</div>\n\n");

		// Do stats for yesterday...
		sb.append("\t<div class='statsSection'>\n");
		sb.append(generateSection(R.string.yesterdayStats, "yesterday_"));
		sb.append("\t\t<hr />\n");
		sb.append("\t</div>\n\n");

		// Do stats for this month
		sb.append("\t<div class='statsSection'>\n");
		sb.append(generateSection(R.string.this_months_stats, "month_"));
		sb.append("\t\t<hr />\n");
		sb.append("\t</div>\n\n");

		// Do last month's stats
		sb.append("\t<div class='statsSection'>\n");
		sb.append(generateSection(R.string.last_month_stats, "last_month_"));
		sb.append("\t\t<hr />\n");
		sb.append("\t</div>\n\n");

		// Do stats for this year
		sb.append("\t<div class='statsSection'>\n");
		sb.append(generateSection(R.string.this_year_stats, "year_"));
		sb.append("\t\t<hr />\n");
		sb.append("\t</div>\n\n");

		// Do last year's stats
		sb.append("\t<div class='statsSection'>\n");
		sb.append(generateSection(R.string.last_year_stats, "last_year_"));
		sb.append("\t\t<hr />\n");
		sb.append("\t</div>\n\n");

		// Do all-time stats
		sb.append("\t<div class='statsSection'>\n");
		sb.append(generateSection(R.string.all_time_stats, "alltime_"));
		sb.append("\t</div>\n\n");

		sb.append("</div>\n").append(WeeWXApp.arrow_spacer);

		if(WeeWXAppCommon.web_debug_on)
			sb.append(WeeWXApp.debug_html);

		sb.append(WeeWXApp.html_footer);

		if(WeeWXAppCommon.debug_html)
			CustomDebug.writeOutput(requireContext(), "stats", sb.toString(), isVisible(), requireActivity());

		wv.post(() -> wv.loadDataWithBaseURL("file:///android_asset/",
				sb.toString(), "text/html", "utf-8", null));

		stopRefreshing();
	}

	JSONObject updateField(JSONObject mqttOutput)
	{
		if(mqttOutput == null)
		{
			LogMessage("Stats.java mqttOutput == null");
			return new JSONObject();
		}

		if(mqttOutput.length() == 0)
		{
			LogMessage("Stats.java mqttOutput.length() == 0");
			return new JSONObject();
		}

		if(wv == null)
		{
			LogMessage("Stats.java wv == null");
			return mqttOutput;
		}

		if(!pageReady)
		{
			LogMessage("Stats.java !pageReady");
			return mqttOutput;
		}

		String out = mqttOutput.toString();
		LogMessage("Stats.java updateField(), output: " + out, KeyValue.d);

		wv.post(() -> wv.postWebMessage(
		    new WebMessage(out),
		    Uri.parse("*")
		));

		LogMessage("Stats.java, returning new JSONObject()", KeyValue.d);
		return new JSONObject();
	}

	void updateTimeStr(String newTime)
	{
		//LogMessage("newTime: " + newTime, KeyValue.d);
		checkFields(rootView.findViewById(R.id.textView2), newTime);
	}

	@JavascriptInterface
	public void onReady()
	{
		LogMessage("Stats.java pageReady == true!");
		pageReady = true;
	}
}
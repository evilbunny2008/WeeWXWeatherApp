package com.odiousapps.weewxweather;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.odiousapps.weewxweather.WeeWXAppCommon.Result;
import com.odiousapps.weewxweather.WeeWXAppCommon.Result2;

import static com.odiousapps.weewxweather.WeeWXApp.ERROR_E;
import static com.odiousapps.weewxweather.WeeWXApp.getInstance;
import static com.odiousapps.weewxweather.WeeWXApp.loadFileFromAssets;
import static com.odiousapps.weewxweather.WeeWXApp.metric_default;
import static com.odiousapps.weewxweather.WeeWXAppCommon.C2Fdeg;
import static com.odiousapps.weewxweather.WeeWXAppCommon.C2Fdeground;
import static com.odiousapps.weewxweather.WeeWXAppCommon.F2Cdeg;
import static com.odiousapps.weewxweather.WeeWXAppCommon.F2Cdeground;
import static com.odiousapps.weewxweather.WeeWXAppCommon.convertDaytoTS;
import static com.odiousapps.weewxweather.WeeWXAppCommon.doStackOutput;
import static com.odiousapps.weewxweather.WeeWXAppCommon.LogMessage;
import static com.odiousapps.weewxweather.WeeWXAppCommon.is_blank;
import static com.odiousapps.weewxweather.WeeWXAppCommon.str2Float;
import static com.odiousapps.weewxweather.WeeWXAppCommon.str2Int;

@SuppressWarnings("StringBufferMayBeStringBuilder")
class JsoupHelper
{
	private static final Set<String> processedFiles = new HashSet<>();
	private static final Set<String> skipTitles = new HashSet<>();

	private static final Map<String, DayOfWeek> IT_DAYS = Map.of(
			"lun", DayOfWeek.MONDAY,
			"mar", DayOfWeek.TUESDAY,
			"mer", DayOfWeek.WEDNESDAY,
			"gio", DayOfWeek.THURSDAY,
			"ven", DayOfWeek.FRIDAY,
			"sab", DayOfWeek.SATURDAY,
			"dom", DayOfWeek.SUNDAY
	);

	static
	{
		processedFiles.addAll(Arrays.asList("arrow_accordion.svg", "icon_setting.svg", "logodtninverted.svg",
				"profile.svg", "arrow_in_circle.svg", "iconmenuwhite.svg", "welcome_to_weatherzone.svg",
				"steady.svg", "icon_arrow_selector.svg", "falling.svg", "rising.svg", "steady.svg"));

		skipTitles.addAll(Arrays.asList("arrow_accordion", "icon_setting", "logodtninverted",
				"profile", "arrow_in_circle", "iconmenuwhite", "welcome_to_weatherzone",
				"steady", "icon_arrow_selector", "falling", "rising", "steady"));
	}

	private static DayOfWeek parseDay(String name)
	{
		return DayOfWeek.valueOf(name.toUpperCase(Locale.getDefault()));
	}

	private static int indexFromToday(DayOfWeek target)
	{
		DayOfWeek today = LocalDate.now().getDayOfWeek();

		int diff = target.getValue() - today.getValue();

		while(diff < 0)
			diff += 7;

		return diff;   // 0..6
	}

	static Date parseItalianDate(String input)
	{
		String[] parts = input.toLowerCase(Locale.ITALIAN).strip().split("\\s+");

		DayOfWeek targetDow = IT_DAYS.get(parts[0]);
		int targetDay = Integer.parseInt(parts[1]);

		LocalDate date = LocalDate.now();
		date = date.minusDays(7);

		for(int i = -7; i < 7; i++)
		{
			if(date.getDayOfMonth() == targetDay && date.getDayOfWeek() == targetDow)
				return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());

			date = date.plusDays(1);
		}

		return null;
	}

	static Result processYahoo(String data)
	{
		boolean metric = (boolean)KeyValue.readVar("metric", metric_default);
		List<Day> days = new ArrayList<>();
		long timestamp = WeeWXAppCommon.getRSSms();
		String desc = "";
		JSONArray jarr;
		JSONObject jobj;

		Document doc = Jsoup.parse(data);

		Pattern p = Pattern.compile("self.__next_f\\.push\\(\\[\\d+,\"(.*)\"]\\)", Pattern.DOTALL);

		boolean hasUploadedMissing = false;

		try
		{
			String forecastStr = null;
			Elements scripts = doc.select("script");

			for(Element element : scripts)
			{
				Matcher m = p.matcher(element.data());
				if(!m.find())
					continue;

				String payload = m.group(1);

				if(payload == null)
					continue;

				String unescaped = payload.replace("\\\"", "\"").replace("\\\\", "\\");

				int colon = unescaped.indexOf(':');
				if(colon < 1)
					continue;

				String jsonPart = unescaped.substring(colon + 1).strip();
				String before = unescaped.substring(0, colon).strip();
				if(before.equals("9"))
				{
					try
					{
						jobj = new JSONObject(jsonPart);
						jarr = jobj.getJSONArray("metadata");
						for(int i = 0; i < jarr.length(); i++)
						{
							if(!jarr.getJSONArray(1).getString(2).equals("1"))
								continue;

							forecastStr = jarr.getJSONArray(1).getJSONObject(3).getString("content");

							boolean isFahrenheit = forecastStr.contains("°F");

							if(metric && isFahrenheit)
							{
								Pattern TEMP_F = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s+°F");

								Matcher m2 = TEMP_F.matcher(forecastStr);

								StringBuffer sb = new StringBuffer();

								while(m2.find())
								{
									int f = str2Int(m2.group(1));
									String c = F2Cdeground(f);
									m2.appendReplacement(sb, c);
								}

								m2.appendTail(sb);

								forecastStr = sb.toString();
							}

							if(!metric && !isFahrenheit)
							{
								Pattern TEMP_C = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s+°C");

								Matcher m2 = TEMP_C.matcher(forecastStr);

								StringBuffer sb = new StringBuffer();

								while(m2.find())
								{
									int c = str2Int(m2.group(1));
									String f = C2Fdeground(c);
									m2.appendReplacement(sb, f);
								}

								m2.appendTail(sb);

								forecastStr = sb.toString();
							}
						}
					} catch(Exception e) {
						LogMessage(ERROR_E + e.getMessage(), KeyValue.e);
						doStackOutput(e);
					}

					continue;
				}

				if(!before.equals("5"))
					continue;

				try
				{
					jarr = new JSONArray(jsonPart);

					jarr = jarr.getJSONObject(3).getJSONArray("children").getJSONArray(4)
							.getJSONObject(3).getJSONArray("children").getJSONArray(2)
							.getJSONObject(3).getJSONArray("children").getJSONArray(1)
							.getJSONArray(1).getJSONObject(3).getJSONArray("dailyForecasts");

					for(int i = 0; i < jarr.length(); i++)
					{
						Day day = new Day();

						jobj = jarr.getJSONObject(i);

						day.timestamp = System.currentTimeMillis() + (86_400_000L * i);

						if(i == 0)
							day.text = forecastStr;

						if(metric)
						{
							if(jobj.getString("unit").equals("°F"))
							{
								try
								{
									day.max = F2Cdeg(jobj.getInt("highTemperature"));
								} catch(Exception ignored) {}

								try
								{
									day.min = F2Cdeg(jobj.getInt("lowTemperature"));
								} catch(Exception ignored) {}

							} else {

								try
								{
									day.max = jobj.getInt("highTemperature") + "&deg;C";
								} catch(Exception ignored) {}

								try
								{
									day.min = jobj.getInt("lowTemperature") + "&deg;C";
								} catch(Exception ignored) {}
							}
						} else {
							if(jobj.getString("unit").equals("°F"))
							{
								try
								{
									day.max = jobj.getInt("highTemperature") + "&deg;F";
								} catch(Exception ignored) {}

								try
								{
									day.min = jobj.getInt("lowTemperature") + "&deg;F";
								} catch(Exception ignored) {}
							} else {
								try
								{
									day.max = C2Fdeg(jobj.getInt("highTemperature"));
								} catch(Exception ignored) {}

								try
								{
									day.min = F2Cdeg(jobj.getInt("lowTemperature"));
								} catch(Exception ignored) {}
							}
						}

						String content = null;
						String[] options = {"32x32_1", "32x32_0", "24x24_0"};

						for(String option : options)
						{
							day.icon = "icons/yahoo/" + jobj.getString("iconLabel")
									.replace(" ", "_") + "_" + option + "_light.svg";
							content = loadFileFromAssets(day.icon);
							if(!is_blank(content))
							{
								day.icon = "file:///android_asset/" + day.icon;
								break;
							}
						}

						if(is_blank(content) && !is_blank(day.icon))
						{
							if(!hasUploadedMissing)
							{
								 hasUploadedMissing = true;
								 uploadMissingYahooIcons(doc);
							}

							day.icon = null;
						}

						days.add(day);
					}
				} catch(Exception e) {
					doStackOutput(e);
				}
			}

			return new Result(days, desc, timestamp, true);
		} catch(Exception e) {
			doStackOutput(e);
			return null;
		}
	}

	private static void uploadMissingYahooIcons(Document doc)
	{
		try(ExecutorService executor = Executors.newSingleThreadExecutor())
		{
			executor.submit(() ->
			{
				Elements svgs = doc.select("svg");

				for(Element svg: svgs)
				{
					if(is_blank(svg.outerHtml()))
						continue;

					if(!svg.hasAttr("aria-label"))
						continue;

					String filenameOrig = svg.attr("aria-label");

					if(is_blank(filenameOrig) || filenameOrig.equals("Yahoo Weather") ||
						filenameOrig.equals("Yahoo_0"))
						continue;

					filenameOrig = filenameOrig.replace(" ", "_");

					int height = 0;

					if(svg.hasAttr("height"))
						height = str2Int(svg.attr("height"));

					int width = 0;
					if(svg.hasAttr("width"))
						width = str2Int(svg.attr("width"));

					if(width > 0 && height > 0)
						filenameOrig += "_" + height + "x" + width;

					boolean hasDark = false;
					boolean hasLight = false;

					Element lightSVG = svg.clone();
					if(!lightSVG.select("g.uds-light-mode-icon").isEmpty())
					{
						hasLight = true;
						lightSVG.select("g.uds-dark-mode-icon").remove();
					}

					Element darkSVG = svg.clone();
					if(!darkSVG.select("g.uds-dark-mode-icon").isEmpty())
					{
						hasDark = true;
						darkSVG.select("g.uds-light-mode-icon").remove();
					}

					try
					{
						if(hasLight)
						{
							boolean alreadyWrittenLight = false;
							String outputLight = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
												 WeeWXAppCommon.convertRGB2Hex(lightSVG.outerHtml()) + "\n";
							String filenameLight = null;
							for(int i = 0; i < 10; i++)
							{
								filenameLight = "yahoo" + "_" + filenameOrig + "_" + i + "_light.svg";

								String content = loadFileFromAssets("icons/yahoo/" + filenameLight);

								if(is_blank(content))
									break;

								if(content.strip().equals(outputLight.strip()))
								{
									alreadyWrittenLight = true;
									break;
								}
							}

							if(!is_blank(filenameLight) && !alreadyWrittenLight)
								WeeWXAppCommon.uploadMissingIcon(Map.of(
										"svgName", filenameLight,
										"svgHeight", "" + height,
										"svgWidth", "" + width,
										"svg", outputLight));
						}

						if(hasDark)
						{
							boolean alreadyWrittenDark = false;
							String outputDark = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
												WeeWXAppCommon.convertRGB2Hex(darkSVG.outerHtml()) + "\n";
							String filenameDark = null;
							for(int i = 0; i < 10; i++)
							{
								filenameDark = "yahoo" + "_" + filenameOrig + "_" + i + "_dark.svg";

								String content = loadFileFromAssets("icons/yahoo/" + filenameDark);

								if(is_blank(content))
									break;

								if(content.strip().equals(outputDark.strip()))
								{
									alreadyWrittenDark = true;
									break;
								}
							}

							if(!is_blank(filenameDark) && !alreadyWrittenDark)
								WeeWXAppCommon.uploadMissingIcon(Map.of(
										"svgName", filenameDark,
										"svgHeight", "" + height,
										"svgWidth", "" + width,
										"svg", outputDark));
						}

						if(!hasLight && !hasDark)
						{
							boolean alreadyWritten = false;
							String output = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
											WeeWXAppCommon.convertRGB2Hex(svg.outerHtml()) + "\n";
							String filename = null;
							for(int i = 0; i < 10; i++)
							{
								filename = "yahoo" + "_" + filenameOrig + "_" + i + ".svg";

								String content = loadFileFromAssets("icons/yahoo/" + filename);

								if(is_blank(content))
									break;

								if(content.strip().equals(output.strip()))
								{
									alreadyWritten = true;
									break;
								}
							}

							if(!is_blank(filename) && !alreadyWritten)
								WeeWXAppCommon.uploadMissingIcon(Map.of(
										"svgName", filename,
										"svgHeight", "" + height,
										"svgWidth", "" + width,
										"svg", output));
						}
					} catch(Exception e) {
						doStackOutput(e);
					}
				}
			});
		} catch(Exception e) {
			LogMessage(ERROR_E + e.getMessage(), KeyValue.e);
			doStackOutput(e);
		}
	}

	static Result processWCAF(String data)
	{
		boolean metric = (boolean)KeyValue.readVar("metric", metric_default);
		String desc;
		List<Day> days = new ArrayList<>();
		long timestamp, lastTS;

		try
		{
			String obs = data.split("Prévisions émises à : ", 2)[1].strip();
			obs = obs.split("</span>", 2)[0].strip();

			int i = 0, j = obs.indexOf("h");
			String hour = obs.substring(i, j);
			i = j + 1;
			j = obs.indexOf(" ", i);
			String minute = obs.substring(i, j);

			String[] bits = obs.split(" ");
			String date = bits[bits.length - 3];
			String month = bits[bits.length - 2];
			String year = bits[bits.length - 1];

			obs = hour + ":" + minute + " " + date + " " + month + " " + year;

			lastTS = timestamp = 0;
			Date df = getInstance().sdf9.parse(obs);
			if(df != null)
				lastTS = timestamp = df.getTime();

			if(timestamp > 0)
				WeeWXAppCommon.updateCacheTime(timestamp);

			desc = data.split("<dt>Enregistrées à :</dt>", 2)[1].split("<dd class='mrgn-bttm-0'>")[1].split("</dd>")[0].strip();

			data = data.split("<div class='div-table'>", 2)[1].strip();
			data = data.split("<section><details open='open' class='wxo-detailedfore'>")[0].strip();
			data = data.substring(0, data.length() - 7).strip();

			bits = data.split("<div class='div-column'>");

			for(i = 1; i < bits.length; i++)
			{
				Document doc = Jsoup.parse(bits[i].strip());
				Elements div = doc.select("div");
				for (j = 0; j < div.size(); j++)
				{
					Day day = new Day();
					String text = "", img_url = "", temp = "", pop = "";

					if(div.get(j).className().contains("greybkgrd"))
					{
						j++;
						continue;
					}

					if(div.get(j).toString().contains("div-row-head"))
					{
						if(div.get(j).select("div").html().contains("Ce soir et cette nuit"))
						{
							day.timestamp = lastTS;
						} else if(div.get(j).select("div").html().contains("Nuit")) {
							day.timestamp = lastTS;
						} else {
							date = div.get(j).html().split("<strong title='", 2)[1].split("'>", 2)[0].strip();
							day.timestamp = convertDaytoTS(date, Locale.CANADA_FRENCH, lastTS);
						}
					} else
						continue;

					j++;

					if(j >= div.size())
					{
						LogMessage("continue");
						continue;
					}

					try
					{
						if(div.outerHtml().contains("div-row-data"))
						{
							if(metric)
								temp = div.get(j).select("div").select("span").html().split("<abbr")[0].strip() + "C";
							else
								temp = div.get(j).select("div").select("span").html().split("</abbr>")[1].split("<abbr")[0].strip() + "F";
							text = div.get(j).select("div").select("img").outerHtml().split("alt='", 2)[1].split("'", 2)[0].strip();
							img_url = "https://www.weather.gc.ca" + div.get(j).select("div").select("img").outerHtml().split("src='", 2)[1].split("'", 2)[0].strip();
							pop = div.get(j).select("div").select("small").html().strip();
						}
					} catch(Exception e) {
						LogMessage("hmmm 2 == " + div.html());
						doStackOutput(e);
					}

					String fileName = "wca" + img_url.substring(img_url.lastIndexOf('/') + 1)
							.replaceAll("\\.gif$", "") + ".png";

					day.icon = "icons/wca/" + fileName;
					String content = loadFileFromAssets(day.icon);
					if(!is_blank(content))
					{
						day.icon = "file:///android_asset/" + day.icon;
						break;
					} else
						day.icon = null;

					day.max = temp;
					day.text = text;
					day.min = pop;
					days.add(day);

					lastTS = day.timestamp;
				}
			}

			return new Result(days, desc, timestamp, true);
		} catch(Exception e) {
			doStackOutput(e);
			return null;
		}
	}

	static Result processWCA(String data)
	{
		boolean metric = (boolean)KeyValue.readVar("metric", metric_default);
		String desc;
		List<Day> days = new ArrayList<>();
		long timestamp, lastTS;

		try
		{
			String obs = data.split("Forecast issued: ", 2)[1].strip();
			obs = obs.split("</span>", 2)[0].strip();

			int i = 0, j = obs.indexOf(":");
			String hour = obs.substring(i, j);
			i = j + 1;
			j = obs.indexOf(" ", i);
			String minute = obs.substring(i, j);
			i = j + 1;
			j = obs.indexOf(" ", i);
			String am_pm = obs.substring(i, j);
			i = j + 1;
			j = obs.indexOf(" ", i);
			i = j + 1;
			j = obs.indexOf(" ", i);
			i = j + 1;
			j = obs.indexOf(" ", i);
			String date = obs.substring(i, j);
			i = j + 1;
			j = obs.indexOf(" ", i);
			String month = obs.substring(i, j);
			i = j + 1;
			j = obs.length();
			String year = obs.substring(i, j);

			obs = hour + ":" + minute + " " + am_pm + " " + date + " " + month + " " + year;

			lastTS = timestamp = 0;
			Date df = getInstance().sdf6.parse(obs);
			if(df != null)
				lastTS = timestamp = df.getTime();

			if(timestamp > 0)
				WeeWXAppCommon.updateCacheTime(timestamp);

			desc = data.split("<dt>Observed at:</dt>", 2)[1].split("<dd class='mrgn-bttm-0'>")[1].split("</dd>")[0].strip();

			data = data.split("<div class='div-table'>", 2)[1].strip();
			data = data.split("<section><details open='open' class='wxo-detailedfore'>")[0].strip();
			data = data.substring(0, data.length() - 7).strip();

			String[] bits = data.split("<div class='div-column'>");
			for(i = 1; i < bits.length; i++)
			{
				Document doc = Jsoup.parse(bits[i].strip());
				Elements div = doc.select("div");
				for (j = 0; j < div.size(); j++)
				{
					String text = "", img_url = "", temp = "", pop = "";
					Day day = new Day();

					if(div.get(j).className().contains("greybkgrd"))
					{
						j++;
						continue;
					}

					if(div.get(j).toString().contains("div-row-head"))
					{
						if(div.get(j).select("div").html().contains("Tonight"))
						{
							day.timestamp = lastTS;
						} else if(div.get(j).select("div").html().contains("Night")) {
							day.timestamp = lastTS;
						} else {
							date = div.get(j).html().split("<strong title='", 2)[1].split("'>", 2)[0].strip();
							day.timestamp = convertDaytoTS(date, Locale.CANADA, lastTS);
						}
					} else
						continue;

					j++;

					if(j >= div.size())
					{
						LogMessage("continue");
						continue;
					}

					try
					{
						if(div.outerHtml().contains("div-row-data"))
						{
							if(metric)
								temp = div.get(j).select("div").select("span").html().split("<abbr")[0].strip() + "C";
							else
								temp = div.get(j).select("div").select("span").html().split("</abbr>")[1].split("<abbr")[0].strip() + "F";
							text = div.get(j).select("div").select("img").outerHtml().split("alt='", 2)[1].split("'", 2)[0].strip();
							img_url = "https://www.weather.gc.ca" + div.get(j).select("div").select("img").outerHtml().split("src='", 2)[1].split("'", 2)[0].strip();
							pop = div.get(j).select("div").select("small").html().strip();
						}
					} catch(Exception e) {
						LogMessage("hmmm 2 == " + div.html());
						doStackOutput(e);
					}

					String fileName = img_url.substring(img_url.lastIndexOf('/') + 1).replaceAll("\\.gif$", "");

					fileName = "wca" + fileName + ".png";

					day.icon = "icons/wca/" + fileName;
					String content = loadFileFromAssets(day.icon);
					if(!is_blank(content))
					{
						day.icon = "file:///android_asset/" + day.icon;
						break;
					} else
						day.icon = null;

					day.max = temp;
					day.text = text;
					day.min = pop;

					days.add(day);

					lastTS = day.timestamp;
				}
			}

			return new Result(days, desc, timestamp, true);
		} catch(Exception e) {
			doStackOutput(e);
		}

		return null;
	}

	static Result processBoM2(String data)
	{
		try
		{
			boolean metric = (boolean)KeyValue.readVar("metric", metric_default);
			List<Day> days = new ArrayList<>();
			long timestamp = 0;

			Document doc = Jsoup.parse(data);
			String desc = doc.title().split(" - Bureau of Meteorology")[0].strip();
			String fcdiv = doc.select("div.forecasts").html();
			String obs = doc.select("span").html().split("issued at ")[1].split("\\.", 2)[0].strip();

			int i = 0, j = obs.indexOf(":");
			String hour = obs.substring(i, j);
			i = j + 1;
			j = obs.indexOf(" ", i);
			String minute = obs.substring(i, j);
			i = j + 1;
			j = obs.indexOf(" ", i);
			String am_pm = obs.substring(i, j);
			i = j + 1;
			j = obs.indexOf(" ", i);
			//String TZ = obs.substring(i, j);
			i = j + 5;
			j = obs.indexOf(" ", i);
			//String DOW = obs.substring(i, j);
			i = j + 1;
			j = obs.indexOf(" ", i);
			String date = obs.substring(i, j);
			i = j + 1;
			j = obs.indexOf(" ", i);
			String month = obs.substring(i, j);
			i = j + 1;
			j = obs.length();
			String year = obs.substring(i, j);

			obs = hour + ":" + minute + " " + am_pm + " " + date + " " + month + " " + year;

			Date df = getInstance().sdf6.parse(obs);
			if(df != null)
				timestamp = df.getTime();

			if(timestamp > 0)
				WeeWXAppCommon.updateCacheTime(timestamp);

			String[] bits = fcdiv.split("<dl class='forecast-summary'>");
			String bit = bits[1];
			Day day = new Day();

			String dayName = bit.split("<a href='", 2)[1].split("'>", 2)[0].split("/forecast/detailed/#d", 2)[1].strip();

			day.timestamp = 0;
			df = getInstance().sdf4.parse(dayName);
			if(df != null)
				day.timestamp = df.getTime();

			day.icon = "https://reg.bom.gov.au" + bit.split("<img src='", 2)[1].split("' alt='", 2)[0].strip();

			if(bit.contains("<dd class='max'>"))
				day.max = bit.split("<dd class='max'>")[1].split("</dd>")[0].strip();

			if(bit.contains("<dd class='min'>"))
				day.min = bit.split("<dd class='min'>")[1].split("</dd>")[0].strip();

			day.text = bit.split("<dd class='summary'>")[1].split("</dd>")[0].strip();

			String fileName = "bom2" + day.icon.substring(day.icon.lastIndexOf("/") + 1).replace("-", "_");

			day.icon = "file:///android_asset/icons/bom/" + fileName;

			day.max = day.max.replace("°C", "").strip();
			day.min = day.min.replace("°C", "").strip();

			if(metric)
			{
				day.max += "&deg;C";
				day.min += "&deg;C";
			} else {
				if(!is_blank(day.max))
					day.max += C2Fdeground(str2Float(day.max));
				if(!is_blank(day.min))
					day.min += C2Fdeground(str2Float(day.min));
			}

			if(is_blank(day.max) || day.max.startsWith("&deg;"))
				day.max = "N/A";

			days.add(day);

			for(i = 2; i < bits.length; i++)
			{
				day = new Day();
				bit = bits[i];
				dayName = bit.split("<a href='", 2)[1].split("'>", 2)[0].split("/forecast/detailed/#d", 2)[1].strip();

				day.timestamp = 0;
				df = getInstance().sdf1.parse(dayName);
				if(df != null)
					day.timestamp = df.getTime();

				day.icon = "https://reg.bom.gov.au" + bit.split("<img src='", 2)[1].split("' alt='", 2)[0].strip();
				day.max = bit.split("<dd class='max'>")[1].split("</dd>")[0].strip();
				day.min = bit.split("<dd class='min'>")[1].split("</dd>")[0].strip();
				day.text = bit.split("<dd class='summary'>")[1].split("</dd>")[0].strip();

				fileName = "bom2" + day.icon.substring(day.icon.lastIndexOf("/") + 1).replace("-", "_");
				day.icon = "file:///android_asset/icons/bom/" + fileName;

				day.max = day.max.replace("°C", "").strip() + "&deg;C";
				day.min = day.min.replace("°C", "").strip() + "&deg;C";

				if(!metric)
				{
					day.max = C2Fdeground(str2Float(day.max));
					day.min = C2Fdeground(str2Float(day.min));
				}

				days.add(day);
			}

			return new Result(days, desc, timestamp, true);
		} catch(Exception e) {
			doStackOutput(e);
		}

		return null;
	}

	static Result processTempoItalia(String data)
	{
		boolean metric = (boolean)KeyValue.readVar("metric", metric_default);
		List<Day> days = new ArrayList<>();
		String desc;
		long timestamp = 0;

		String timeweek;
		try
		{
			Document doc = Jsoup.parse(data.strip());
			Elements title = doc.select("title");

			desc = title.text().split("Meteo", 2)[1].split("dal", 2)[0].strip();

			Elements tr = doc.select("tbody").select("tr");
			for(Element e : tr)
			{
				Day day = new Day();
				Element td = e.selectFirst("td.timeweek");
				if(td != null)
				{
					Date date = null;
					timeweek = td.text();
					if(!is_blank(timeweek))
						date = parseItalianDate(timeweek);

					if(date != null)
					{
						day.timestamp = date.getTime();
						LogMessage("date.getTime(): " + date.getTime());
						LogMessage("sdf2: " + getInstance().sdf2.format(date.getTime()));
					}
				}

				td = e.selectFirst("td.skyIcon img");
				if(td != null)
				{
					day.icon = td.attr("src");
					day.icon = "icons/tempoitalia/" + day.icon.substring(day.icon.lastIndexOf('/') + 1);

					String content = loadFileFromAssets(day.icon);
					if(!is_blank(content))
						day.icon = "file:///android_asset/" + day.icon;
					else
						day.icon = null;

					LogMessage("day.icon: " + day.icon);
				}

				td = e.selectFirst("td.skyDesc");
				if(td != null)
					day.text = td.text();

				td = e.selectFirst("td.tempin");
				if(td != null)
				{
					if(metric)
						day.min = str2Int(td.text().replace("°C", "")) + "&deg;C";
					else
						day.min = C2Fdeground(str2Float(td.text().replace("°C", "")));
				}

				td = e.selectFirst("td.tempmax");
				if(td != null)
				{
					if(metric)
						day.max = str2Int(td.text().replace("°C", "")) + "&deg;C";
					else
						day.max = C2Fdeground(str2Float(td.text().replace("°C", ""))) + "&deg;F";
				}

				days.add(day);
			}

			return new Result(days, desc, timestamp, true);
		} catch(Exception e) {
			doStackOutput(e);
			return null;
		}
	}

	static void processWZhtml(String url, String html)
	{
		LogMessage("Starting SVG checking/downloading...");

		int rc = 0;
		int totalrc = 0;

		Elements svgs = Jsoup.parse(html.strip()).select("svg:has(title)");
		for(Element svg : svgs)
		{
			totalrc++;

			if(!svg.hasText())
				continue;

			Element element = svg.selectFirst("title");
			if(element == null)
				continue;

			String title = element.text();
			if(is_blank(title))
				continue;

			title = wzTitle2Filename(url, title, svg.outerHtml(), true);
			LogMessage("Found new SVG: " + title);
		}

		LogMessage("TOTALSVGS SVGs found but only NEWSVGS new SVGs extracted and saved..."
				.replace("TOTALSVGS", "" + totalrc).replace("NEWSVGS", "" + rc));
	}

	private static void removeCommentsIMGsAndUnwantedSVGs(Node node, boolean removeAllSVGs)
	{
		List<Node> children = new ArrayList<>(node.childNodes());

		if(children.isEmpty())
			return;

		for(Node child : children)
		{
			if(child == null)
				continue;

			if("#comment".equals(child.nodeName()))
			{
				child.remove();
				continue;
			} else if("noscript".equals(child.nodeName())) {
				child.remove();
				continue;
			} else if("script".equals(child.nodeName())) {
				child.remove();
				continue;
			} else if("img".equals(child.nodeName())) {
				child.remove();
				continue;
			} else if("svg".equals(child.nodeName())) {
				if(removeAllSVGs)
				{
					child.remove();
					continue;
				}

				Element svg = (Element)child;
				Element svgTitle = svg.selectFirst("> title");

				if(svgTitle == null || !svgTitle.hasText())
				{
					child.remove();
					continue;
				}

				String title = svgTitle.text();
				if(is_blank(title))
				{
					child.remove();
					continue;
				}

				title = title.strip().replace(" ", "_").toLowerCase(Locale.ENGLISH).strip();

				if(title.endsWith("_icon") || title.startsWith("wind_speed_") || title.startsWith("logo") ||
				   title.startsWith("icon") || title.startsWith("layer_") || title.startsWith("miscellaneous_"))
				{
					child.remove();
					continue;
				}

				if(skipTitles.contains(title))
				{
					child.remove();
					continue;
				}
			}

			removeCommentsIMGsAndUnwantedSVGs(child, removeAllSVGs);
		}
	}

	static Element searchElemntForWords(String lineCalledFrom, Element parent, String wordsToFind)
	{
		if(parent == null)
			return null;

		if(is_blank(wordsToFind))
			return null;

		if(is_blank(lineCalledFrom))
			lineCalledFrom = "N/A";

		for(int i = 0; i < parent.childrenSize(); i++)
		{
			Element child = parent.child(i);
			if(!child.hasText() || is_blank(child.text()))
				continue;

			String text = normaliseText(child.text());
			if(is_blank(text))
				continue;

			if(!(" " + text.toLowerCase(Locale.ENGLISH) + " ").contains(" " + wordsToFind.toLowerCase(Locale.ENGLISH) + " "))
				continue;

			text = normaliseText(child.ownText());
			if(!is_blank(text))
				if((" " + text.toLowerCase(Locale.ENGLISH) + " ").contains(" " + wordsToFind.toLowerCase(Locale.ENGLISH) + " "))
					return child;

			Element newchild = searchElemntForWords(lineCalledFrom, child, wordsToFind);
			if(newchild != null)
				return newchild;
		}

		return null;
	}

	static String normaliseText(String text)
	{
		if(is_blank(text))
			return null;

		return text.strip().replaceAll("\\s+", " ").replace("' ", "'").replace(" '", "'");
	}

	static void cleanWZDoc(Element body)
	{
		LogMessage("cleanWZDoc() Let's start by removing all the comments, IMGs, all SVGs " +
								  "and empty tags, doc original size: " + body.outerHtml().length());

		for(Element el : body.getAllElements())
			for(TextNode tn : el.textNodes())
				tn.text(tn.text().replace("\u00A0", " "));

		for(Node node : body.childNodes())
			removeCommentsIMGsAndUnwantedSVGs(node, false);

		String[] thingsThatCanBeEmpty = {"style", "a", "span", "p", "li", "ul", "div", "nav"};

		for(int i = 0; i < 2; i++)
			for(String tag : thingsThatCanBeEmpty)
				body.select("HTMLTAG:matchesOwn(^\\s+$):not(:has(*))".replace("HTMLTAG", tag)).remove();

		body.select("*").forEach(el ->
		{
			if(el.childrenSize() == 0 && is_blank(el.text()))
				el.remove();
		});

		String elements = "span, font, a, i, b, p, button";

		body.select(elements).forEach(tag ->
		{
			String t = tag.text().replace("\u00A0", "").strip();

			if(t.length() <= 1 && tag.childrenSize() == 0)
			{
				tag.before(" ");
				tag.unwrap();
			}
		});

		body.select(elements).forEach(tag ->
		{
			tag.before(" ");
			tag.unwrap();
		});

		LogMessage("cleanWZDoc() Comments, IMGs, all SVGs and empty tags are now gone from wzHTML, " +
								  "doc new html size: " + body.outerHtml().length());
	}

	static String nowTS()
	{
		return "" + System.currentTimeMillis();
	}

	static Result2 processWZ2GetForecastStrings(String data)
	{
		LogMessage("processWZ2GetForecastStrings() starting to process WZ data...");

		String[] forecast_text = new String[28];
		String desc;
		int rc = 0;

		try
		{
			Element body = Jsoup.parse(data).body();
			if(!body.hasText())
				return null;

			Element districts_block = searchElemntForWords("1209", body, "Districts");
			if(districts_block == null)
			{
				LogMessage("processWZ2GetForecastStrings() districts_block came back as null, skipping...");

				String nowTS = nowTS();
				LogMessage("processWZ2GetForecastStrings() Writting to WZtest_ndb_body_" + nowTS + ".html");
				CustomDebug.writeDebug("WZtest_ndb_body_" + nowTS + ".html", body.html());

				return null;
			}

			Element parent = districts_block.parent();
			if(parent == null)
				return null;

			parent = parent.parent();
			if(parent == null)
				return null;

			Element forecast_block = searchElemntForWords("1222", parent, "Forecast");
			if(forecast_block == null)
			{
				LogMessage("processWZ2GetForecastStrings() forecast_block came back as null, skipping...");

				String nowTS = nowTS();
				LogMessage("processWZ2GetForecastStrings() Writting to WZtest_nf_parent_" + nowTS + ".html");
				CustomDebug.writeDebug("WZtest_nf_parent_" + nowTS + ".html", parent.html());

				return null;
			}

			String normText = normaliseText(forecast_block.text());
			if(is_blank(normText))
				return null;

			StringBuilder sb = new StringBuilder();
			String[] words = normText.split(" ");
			for(String word : words)
			{
				if(word.equals("Forecast"))
					break;

				if(sb.length() > 0)
					sb.append(" ");

				sb.append(word);
			}

			desc = sb.toString();

			parent = forecast_block.parent();
			if(parent == null)
				return null;

			Elements dayEls = parent.select("*:matchesOwn(^(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)$)");
			Pattern p = Pattern.compile("^(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\\s+(.*)", Pattern.DOTALL);

			String text;

			DayOfWeek dow = null;
			int idx = 0;
			for(int i = 0; i < dayEls.size(); i++)
			{
				Element previous_element = dayEls.get(i);
				parent = previous_element.parent();
				if(parent == null)
					return null;

				text = normaliseText(parent.text());
				if(is_blank(text))
					continue;

				Matcher m = p.matcher(text);
				if(!m.find())
				{
					String nowTS = nowTS();
					LogMessage("processWZ2GetForecastStrings() Writting to WZtest_previous_element_" + i + "_" + nowTS + ".html");
					CustomDebug.writeDebug("WZtest_previous_element_" + i + "_" + nowTS + ".html", previous_element.html());
					continue;
				}

				String dayName = m.group(1);
				String forecast2 = m.group(2);

				if(is_blank(dayName) || is_blank(forecast2))
					continue;

				dayName = dayName.strip();
				forecast2 = forecast2.strip();

				if(dow == null)
				{
					dow = parseDay(dayName);
					idx = indexFromToday(dow);
				} else
					idx++;

				forecast_text[idx] = forecast2;
				rc++;
			}

			if(rc > 0)
				return new Result2(forecast_text, desc, rc);
		} catch(Exception e) {
			LogMessage("processWZ2GetForecastStrings() Error! e: " + e.getMessage(), true, KeyValue.e);
			doStackOutput(e);
		}

		return null;
	}

	static Result processWZ2Forecasts(String url, String data, Result2 result2) throws IOException
	{
		if(is_blank(data) || result2 == null || result2.rc() == 0)
			return null;

		String[] forecast_text = result2.forecast_text();
		String desc = result2.desc();

		boolean metric = (boolean)KeyValue.readVar("metric", metric_default);
		boolean rainInInches = (boolean)KeyValue.readVar("rainInInches", WeeWXApp.rain_in_inches_default);
		Day[] days = new Day[28];

		LogMessage("processWZ2Forecasts() starting to process wzHTML...");

		Element body = Jsoup.parse(data).body();

		if(!body.hasText())
		{
			LogMessage("processWZ2Forecasts() body has no text...");
			return new Result(null, null, 0L, true);
		}

		Element daily_forecast_block = searchElemntForWords("1566", body, "Daily Forecast");
		if(daily_forecast_block == null)
		{
			LogMessage("processWZ2Forecasts() daily_forecast_block came back as null, skipping...");

			String nowTS = nowTS();
			LogMessage("processWZ2Forecasts() Writting to WZtest_no_daily_forecast_block_body_" + nowTS + ".html");
			CustomDebug.writeDebug("WZtest_no_daily_forecast_block_body_" + nowTS + ".html", body.html());

			return new Result(null, null, 0L, true);
		}

		Element parent = daily_forecast_block.parent();
		if(parent == null)
		{
			String nowTS = nowTS();
			LogMessage("processWZ2Forecasts() Writting to WZtest_no_svg_found_" + nowTS + ".html");
			CustomDebug.writeDebug("WZtest_no_svg_found_" + nowTS + ".html", daily_forecast_block.outerHtml());

			return new Result(null, null, 0L, true);
		}

		DayOfWeek dow = null;
		int idx = -1;
		Elements svgs = parent.select("svg:has(title)");
		Pattern dayPat = Pattern.compile("^(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\\s+(.*)$");
		Pattern tempPat = Pattern.compile("(-?\\d+)\\s+°");
		for(int i = 0; i < svgs.size(); i++)
		{
			Element svg = svgs.get(i);

			Element title = svg.selectFirst("title");
			if(title == null)
				continue;

			Day day = new Day();

			day.icon = wzTitle2Filename(url, title.text().strip(), svg.outerHtml(), false);
			if(!is_blank(day.icon))
				day.icon = "file:///android_asset/icons/wz/" + day.icon;
			else
				day.icon = null;

			Element block = svg.parent();
			if(block == null)
				continue;

			block = svg.parent();
			if(block == null)
				continue;

			svg.remove();

			Element dayEl = block.selectFirst("*:matchesOwn(^(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\\b)");
			if(dayEl == null)
				continue;

			String text = normaliseText(dayEl.text());
			if(is_blank(text))
				continue;

			Matcher dm = dayPat.matcher(text);
			if(dm.find())
			{
				String dayName = dm.group(1);
				if(dayName != null)
					dayName = dayName.strip();

				String dayMonth = dm.group(2);
				if(dayMonth != null)
					dayMonth = dayMonth.strip();

				if(dow == null)
				{
					if(dayName != null)
					{
						dow = DayOfWeek.valueOf(dayName.toUpperCase(Locale.ENGLISH));
						idx = (dow.getValue() - LocalDate.now().getDayOfWeek().getValue() + 7) % 7;
					}
				} else
					idx++;

				int year = Year.now(ZoneId.systemDefault()).getValue();
				String full = dayMonth + " " + year;

				DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH);

				LocalDate date = LocalDate.parse(full, fmt);

				if(date.isBefore(LocalDate.now()))
					date = date.plusYears(1);

				day.timestamp = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
			} else {
				String nowTS = nowTS();
				LogMessage("processWZ2Forecasts() Writting to WZtest_no_day_matched_dayEl_" + i + "_" + nowTS + ".html");
				CustomDebug.writeDebug("WZtest_no_day_matched_dayEl_" + i + "_" + nowTS + ".html", dayEl.outerHtml());
				continue;
			}

			List<String> temps = new ArrayList<>();
			for(Element e : block.select("*:containsOwn(°)"))
			{
				Matcher tm = tempPat.matcher(e.text());
				if(tm.find())
				{
					String string = tm.group(1);
					if(is_blank(string))
						continue;

					int C = Math.round(str2Float(string));

					if(metric)
						temps.add(C + "&deg;C");
					else
						temps.add(WeeWXAppCommon.C2Fdeground(C) + "&deg;F");
				}
			}

			if(temps.size() > 1)
			{
				day.min = temps.get(0);
				day.max = temps.get(1);
			} else if(!temps.isEmpty())
				day.max = temps.get(0);

			if(is_blank(day.max))
			{
				String nowTS = nowTS();
				LogMessage("processWZ2Forecasts() Writting to WZtest_no_temps_block_" + nowTS + ".html");
				CustomDebug.writeDebug("WZtest_no_temps_block_" + nowTS + ".html", dayEl.html());
				continue;
			}

			Element rainEl = block.selectFirst("*:containsOwn(mm)");
			if(rainEl == null || !rainEl.hasText() || is_blank(rainEl.text()))
			{
				String nowTS = nowTS();
				LogMessage("processWZ2Forecasts() Writting to WZtest_no_rainEl_" + nowTS + ".html");
				if(rainEl != null)
					CustomDebug.writeDebug("WZtest_no_rainEl_" + nowTS + ".html", rainEl.html());
				continue;
			}

			String possrain = possrain2Record(rainEl.text().strip(), metric && !rainInInches);

			if(idx < forecast_text.length && !is_blank(forecast_text[idx]))
			{
				day.text = forecast_text[idx].strip();

				if(!day.text.endsWith("."))
					day.text += ".";

				day.text += "<br/>\n<br/>\n" + possrain;
			} else {
				day.text = possrain;
			}

			days[idx] = day;
		}

		List<Day> newdays = new ArrayList<>();
		for(Day day : days)
			if(day != null && !is_blank(day.max))
				newdays.add(day);

		if(!newdays.isEmpty())
			return new Result(newdays, desc, 0L, true);

		return new Result(null, null, 0L, true);
	}

	static String wzTitle2Filename(String url, String title, String svg, boolean saveToFile)
	{
		title = normaliseText(title);
		if(is_blank(title))
			return null;

		title = title.replaceAll("[ -]", "_")
				.replaceAll("_+", "_")
				.toLowerCase(Locale.ENGLISH).strip();

		if(title.endsWith("_icon") || title.startsWith("wind_speed_") || title.startsWith("logo") || title.startsWith("icon") ||
		   title.startsWith("layer_") || title.startsWith("miscellaneous_"))
			return null;

		if(skipTitles.contains(title))
			return null;

		String filenameSVG = "icons/wz/" + title + ".svg";
		String content = loadFileFromAssets(filenameSVG);
		if(!is_blank(content))
			return title + ".svg";

		String filenamePNG = "icons/wz/wz" + title + ".png";
		content = loadFileFromAssets(filenamePNG);
		boolean foundPNG = !is_blank(content);

		if(processedFiles.contains(title + ".svg"))
		{
			if(foundPNG)
				return "wz" + title + ".png";
			else
				return null;
		}

		File f = null;
		int len = svg != null && svg.length() > 0 ? svg.length() : 0;

		try
		{
			f = WeeWXAppCommon.getExtFile(title);
			if(f.exists() && f.length() > 0 && f.length() == len)
			{
				if(foundPNG)
					return "wz" + title + ".png";
				else
					return null;
			}
		} catch(Exception ignored) {}

		LogMessage("wzTitle2Filename() Unable to locate title: " + title, KeyValue.d);

		Map<String, String> map = new HashMap<>();
		map.put("svgName", title);
		map.put("svgURL", url);

		if(!is_blank(svg))
		{
			LogMessage("wzTitle2Filename() svg != null && !isBlank()", KeyValue.d);
			map.put("svg", svg);
		}

		WeeWXAppCommon.uploadMissingIcon(map);

		if(saveToFile && f != null)
		{
			try
			{
				if((!f.exists() || f.length() != len) && len > 0 && f.canWrite())
				{
					LogMessage("Writting to new svg file: " + title + ".svg");

					try(FileOutputStream fos = new FileOutputStream(f))
					{
						fos.write(svg.getBytes());
						LogMessage("Successfully wrote to " + title + ".svg");
					} catch(Exception ignored) {}
				}
			} catch(Exception ignored) {}
		}

		processedFiles.add(title + ".svg");

		if(foundPNG)
			return "wz" + title + ".png";
		else
			return null;
	}

	static String possrain2Record(String possrain, boolean metric)
	{
		if(is_blank(possrain))
			return null;

		possrain = possrain.strip();

		String output = "Possible rainfall ";

		// 5% / < 1mm
		// 50% / 1-5mm
		// 90% / 10-20mm
		String percent = null;
		String max = null;
		String min = null;

		if(possrain.contains("/"))
		{
			String[] bits = possrain.split("/", 2);

			if(bits.length >= 2)
			{
				possrain = bits[1].strip();

				if(bits[0].contains("%"))
					percent = "" + Math.round(str2Float(bits[0]));
			}
		}

		boolean lt = possrain.contains("&lt;") || possrain.contains("<");
		if(lt)
			possrain = possrain.replace("&lt;", "").replace("<", "").strip();

		boolean gt = possrain.contains("&gt;") || possrain.contains(">");
		if(!lt && gt)
			possrain = possrain.replace("&gt;", "").replace(">", "").strip();

		if(possrain.contains("-"))
		{
			String[] bits = possrain.split("-", 2);

			if(!is_blank(bits[0]))
				bits[0] = "" + Math.round(str2Float(bits[0]));
			else
				bits[0] = null;

			if(!is_blank(bits[1]))
				bits[1] = "" + Math.round(str2Float(bits[1]));
			else
				bits[1] = null;

			if(bits.length == 2)
			{
				if(bits[0] != null && bits[1] != null)
				{
					min = bits[0];
					max = bits[1];
				} else if(bits[1] != null) {
					max = bits[1];
				} else {
					max = bits[0];
				}
			}
		} else {
			max = "" + Math.round(str2Float(possrain));
		}

		if(min != null || max != null)
		{
			if(!is_blank(percent))
				output += Math.round(str2Float(percent)) + "% / ";

			if(lt)
				output += "&lt;";

			if(gt)
				output += "&gt;";

			if(metric)
			{
				if(min != null)
					output += min + "mm to ";

				output += max + "mm";
			} else {
				if(min != null)
					min = "" + str2Int(min);

				max = "" + str2Int(max);

				if(min != null)
					output += min + "in to ";

				output += max + "in";
			}
		}

		return output;
	}
}
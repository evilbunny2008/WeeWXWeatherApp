package com.odiousapps.weewxweather;

import android.location.Location;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


import static com.odiousapps.weewxweather.WeeWXApp.SAVE_APP_DEBUG_LOGS;
import static com.odiousapps.weewxweather.WeeWXApp.getInstance;
import static com.odiousapps.weewxweather.WeeWXApp.save_app_debug_logs_default;
import static com.odiousapps.weewxweather.WeeWXAppCommon.LogMessage;
import static com.odiousapps.weewxweather.WeeWXAppCommon.is_blank;
import static com.odiousapps.weewxweather.WeeWXAppCommon.json_keys;

@SuppressWarnings("unused")
class KeyValue
{
	final static Map<Integer, Integer> widgetMinHeight = new HashMap<>();
	final static Map<Integer, Integer> widgetMinWidth = new HashMap<>();
	final static Map<Integer, Integer> widgetMaxHeight = new HashMap<>();
	final static Map<Integer, Integer> widgetMaxWidth = new HashMap<>();

	final static int e = 0;
	final static int w = 1;
	final static int i = 2;
	final static int d = 3;
	final static int v = 4;

	static int theme;
	static int mode;

	static String currWebViewVer = null;
	static int webview_major_version = 0;

	static boolean isVisible = false;

	record Result(String Key, Object Val) {}

	static final List<Result> prefs = new ArrayList<>();

	static final List<KV> yahoo = new ArrayList<>();

	static final List<County> counties = new ArrayList<>();

	static String countyName = null;
	static String bomLocation = null;
	static String bomGeohash = null;

	static boolean hasNotificationPerm = false;

	static int since_hour = 0;

	static final HashMap<String, String> obsGroup = new HashMap<>();
	static final HashMap<String, String> labels = new HashMap<>();
	static final HashMap<String, String> formats = new HashMap<>();

	static final Map<String, Object> values = new HashMap<>();

	// The 16 configured compass sector labels plus the unavailable-direction label.
	// Empty until a compatible dictionary is loaded, retaining legacy behavior.
	static volatile List<String> ordinateNames = List.of();

	static JSONObject getDict(String dict_name, JSONObject jsonObject)
	{
		if(jsonObject == null || jsonObject.length() == 0)
			return null;

		if(!jsonObject.has(dict_name))
			return null;

		JSONObject jobj = jsonObject.optJSONObject(dict_name);
		if(jobj == null || jobj.length() == 0)
			return null;

		return jobj;
	}

	static boolean parseDicts()
	{
		//LogMessage("KeyValue.parseDict(): Loading...");

		JSONObject jsonObject;
		ordinateNames = List.of();

		String json_str = (String)readVar(json_keys[1] + "_str", "");
		if(is_blank(json_str))
			return true;

		try
		{
			jsonObject = new JSONObject(json_str);
			if(jsonObject.length() == 0)
				return true;
		} catch(Exception r) {
			return true;
		}

		JSONObject group_dict = getDict("group_unit_dict", jsonObject);
		JSONObject obs_group_dict = getDict("obs_group_dict", jsonObject);
		JSONObject format_dict = getDict("unit_format_dict", jsonObject);
		JSONObject label_dict = getDict("unit_label_dict", jsonObject);

		if(obs_group_dict == null || obs_group_dict.length() == 0)
		{
			LogMessage("obs_group_dict == null || length() == 0", KeyValue.e);
			return true;
		}

		if(group_dict == null || group_dict.length() == 0)
		{
			LogMessage("group_dict == null || length() == 0", KeyValue.e);
			return true;
		}

		if(format_dict == null || format_dict.length() == 0)
		{
			LogMessage("format_dict == null || length() == 0", KeyValue.e);
			return true;
		}


		if(label_dict == null || label_dict.length() == 0)
		{
			LogMessage("label_dict == null || length() == 0", KeyValue.e);
			return true;
		}

		// Optional InigoPlugin dictionary extension. Retain *_str fallback
		// if absent or malformed. The final label represents unavailable wind.
		JSONArray names = jsonObject.optJSONArray("ordinate_names");
		if(names != null && names.length() == 17)
		{
			List<String> parsed = new ArrayList<>(17);
			for(int idx = 0; idx < 17; idx++)
			{
				Object raw = names.opt(idx);
				if(!(raw instanceof String label) || is_blank(label))
				{
					parsed.clear();
					break;
				}
				parsed.add(label.strip());
			}
			if(parsed.size() == 17)
				ordinateNames = List.copyOf(parsed);
		}

		Iterator<String> keys = group_dict.keys();
		while(keys.hasNext())
		{
			String key = keys.next();

			if(!key.startsWith("group_"))
				continue;

			String group_name = group_dict.optString(key);
			if(is_blank(group_name))
			{
				LogMessage("group_name == null or isBlank()", KeyValue.e);
				continue;
			}

			if(label_dict.has(group_name) && format_dict.has(group_name))
			{
				//String obs_group = obs_group_dict.optString(group_name).strip();
				String label = label_dict.optString(group_name).strip();
				String format = format_dict.optString(group_name).strip();

				labels.put(key, label);
				formats.put(key, format);
			}
		}

		keys = obs_group_dict.keys();
		while(keys.hasNext())
		{
			String key = keys.next();
			String group_name = obs_group_dict.optString(key);

			if(!group_name.startsWith("group_"))
				continue;

			obsGroup.put(key, group_name);
		}

		return false;
	}

	static String getFormat(String key)
	{
		return formats.get(key);
	}

	static String getLabel(String key, String defaultValue)
	{
		String label = labels.get(key);

		if(is_blank(label))
		{
			String new_key = getKeyFromName(key);
			label = labels.get(new_key);
			if(!is_blank(label))
				return label;

			return defaultValue;
		}

		return label;
	}

	static String getKeyFromName(String name)
	{
		if(obsGroup.isEmpty())
			return "404";

		for(String key : obsGroup.keySet())
			if(name.contains(key))
				return obsGroup.get(key);

		LogMessage("KeyValue.getKeyFromName(): '" +name + "' not matched in obsGroup!", KeyValue.e);

		return null;
	}

	static boolean debugging_on()
	{
		return isPrefSet(SAVE_APP_DEBUG_LOGS) &&
			   (boolean)readVar(SAVE_APP_DEBUG_LOGS, save_app_debug_logs_default);
	}

	static boolean isPrefSet(String var)
	{
		for(int i = 0; i < prefs.size(); i++)
		{
			Result r = prefs.get(i);
			if(r.Key.equals(var))
				return true;
		}

		return WeeWXAppCommon.isPrefSet(var);
	}

	static Object readVar(String var, Object defVal)
	{
		try
		{
			for(int i = 0; i < prefs.size(); i++)
			{
				Result r = prefs.get(i);
				if(r.Key.equals(var))
					return r.Val();
			}

			Object ret = WeeWXAppCommon.readVar(var, defVal);

			if(ret != null)
				return ret;

			return defVal;
		} catch(Exception err) {
			Log.e(WeeWXAppCommon.LOGTAG, "Error! err: ", err);
		}

		return defVal;
	}

	static void putVar(String var, Object val)
	{
		boolean isSet = isPrefSet(var);

		if(val == null)
		{
			if(!isSet)
				return;

			for(int i = 0; i < prefs.size(); i++)
			{
				Result tmpr = prefs.get(i);
				if(tmpr.Key.equals(var))
				{
					prefs.remove(i);
					WeeWXAppCommon.setVar(var, null);
					return;
				}
			}

			WeeWXAppCommon.setVar(var, null);
			return;
		}

		Result r = new Result(var, val);
		WeeWXAppCommon.setVar(var, val);

		if(!isSet)
		{
			prefs.add(r);
			return;
		}

		for(int i = 0; i < prefs.size(); i++)
		{
			Result tmpr = prefs.get(i);
			if(tmpr.Key.equals(var))
			{
				prefs.set(i, r);
				return;
			}
		}
	}

	public static Object[] closestCounty(float targetLat, float targetLon) {

		float[] results = new float[1];
		float minDistance = Float.MAX_VALUE;
		County closest = null;

		for(County c : counties)
		{
			Location.distanceBetween(targetLat, targetLon, c.latitude, c.longitude, results);

			if(results[0] < minDistance)
			{
				minDistance = results[0];
				closest = c;
			}
		}

		return new Object[]{closest, minDistance};
	}

	static void fillCounties()
	{
		try
		{
			Date d = getInstance().sdf4.parse("2022-03-09");
			if(d != null)
			{
				County c = new County();
				c.geonameid = 2964574;
				c.name = "Dublin";
				c.cc = "IE";
				c.population = 1024027;
				c.timezone = "Europe/Dublin";
				c.lastModified = d.getTime();
				c.latitude = 53.3331f;
				c.longitude = -6.24889f;
				counties.add(c);
			}
		} catch(Exception ignored) {}

		try
		{
			Date d = getInstance().sdf4.parse("2022-12-15");
			if(d != null)
			{
				County c = new County();
				c.geonameid = 7521313;
				c.name = "Connacht";
				c.cc = "IE";
				c.population = 588583;
				c.timezone = "Europe/Dublin";
				c.lastModified = d.getTime();
				c.latitude = 53.87f;
				c.longitude = -8.63333f;
				counties.add(c);
			}
		} catch(Exception ignored) {}

		try
		{
			Date d = getInstance().sdf4.parse("2022-12-15");
			if(d != null)
			{
				County c = new County();
				c.geonameid = 7521314;
				c.name = "Leinster";
				c.cc = "IE";
				c.population = 2858501;
				c.timezone = "Europe/Dublin";
				c.lastModified = d.getTime();
				c.latitude = 53.1667f;
				c.longitude = -7.02121f;
				counties.add(c);
			}
		} catch(Exception ignored) {}

		try
		{
			Date d = getInstance().sdf4.parse("2024-12-03");
			if(d != null)
			{
				County c = new County();
				c.geonameid = 7521315;
				c.name = "Munster";
				c.cc = "IE";
				c.population = 1364098;
				c.timezone = "Europe/Dublin";
				c.lastModified = d.getTime();
				c.latitude = 52.3433f;
				c.longitude = -8.71667f;
				counties.add(c);
			}
		} catch(Exception ignored) {}

		try
		{
			Date d = getInstance().sdf4.parse("2022-12-15");
			if(d != null)
			{
				County c = new County();
				c.geonameid = 7521316;
				c.name = "Ulster";
				c.cc = "IE";
				c.population = 2215454;
				c.timezone = "Europe/Dublin";
				c.lastModified = d.getTime();
				c.latitude = 54.9273f;
				c.longitude = -7.9395f;
				counties.add(c);
			}
		} catch(Exception ignored) {}
	}

	static void loadYahooRGB2SVGTable()
	{
		KV kv;

		kv = new KV();
		kv.Key = "--uds-spectrum-color-blue-0";
		kv.Val = "#F2FAFF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-blue-1";
		kv.Val = "#DFF2FF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-blue-2";
		kv.Val = "#CAEAFF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-blue-3";
		kv.Val = "#A2DAFF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-blue-4";
		kv.Val = "#7DCBFF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-blue-5";
		kv.Val = "#59BDFF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-blue-6";
		kv.Val = "#12A9FF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-blue-7";
		kv.Val = "#188FFF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-blue-8";
		kv.Val = "#0F69FF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-blue-9";
		kv.Val = "#0063EB";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-blue-10";
		kv.Val = "#0059DE";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-blue-11";
		kv.Val = "#0048C4";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-blue-12";
		kv.Val = "#0037A4";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-blue-13";
		kv.Val = "#00277E";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-blue-14";
		kv.Val = "#001753";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-blue-15";
		kv.Val = "#000A26";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-brown-0";
		kv.Val = "#FFFDFA";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-brown-1";
		kv.Val = "#FFF6E0";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-brown-2";
		kv.Val = "#FFECD1";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-brown-3";
		kv.Val = "#FBDDBF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-brown-4";
		kv.Val = "#EDC79F";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-brown-5";
		kv.Val = "#D9AA76";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-brown-6";
		kv.Val = "#B9884F";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-brown-7";
		kv.Val = "#97682F";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-brown-8";
		kv.Val = "#865A23";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-brown-9";
		kv.Val = "#754C19";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-brown-10";
		kv.Val = "#5B380B";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-brown-11";
		kv.Val = "#472A04";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-brown-12";
		kv.Val = "#372101";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-brown-13";
		kv.Val = "#2D1B00";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-brown-14";
		kv.Val = "#261700";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-brown-15";
		kv.Val = "#1A1000";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-carbon-0";
		kv.Val = "#FFFFFF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-carbon-1";
		kv.Val = "#FCFBFB";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-carbon-2";
		kv.Val = "#F5F5F5";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-carbon-3";
		kv.Val = "#E3E3E3";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-carbon-4";
		kv.Val = "#CDCDCD";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-carbon-5";
		kv.Val = "#B0B0B0";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-carbon-6";
		kv.Val = "#8E8E8E";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-carbon-7";
		kv.Val = "#6A6A6A";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-carbon-8";
		kv.Val = "#5A5A5A";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-carbon-9";
		kv.Val = "#444444";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-carbon-10";
		kv.Val = "#323232";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-carbon-11";
		kv.Val = "#292929";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-carbon-12";
		kv.Val = "#1F1F1F";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-carbon-13";
		kv.Val = "#141414";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-carbon-14";
		kv.Val = "#0A0A0A";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-carbon-15";
		kv.Val = "#000000";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-citron-0";
		kv.Val = "#FDFFE6";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-citron-1";
		kv.Val = "#F9FDC1";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-citron-2";
		kv.Val = "#F5FC9E";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-citron-3";
		kv.Val = "#EBF457";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-citron-4";
		kv.Val = "#DEE717";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-citron-5";
		kv.Val = "#CDD400";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-citron-6";
		kv.Val = "#B8BC00";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-citron-7";
		kv.Val = "#9E9F00";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-citron-8";
		kv.Val = "#7A7A00";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-citron-9";
		kv.Val = "#737100";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-citron-10";
		kv.Val = "#656300";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-citron-11";
		kv.Val = "#4E4C00";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-citron-12";
		kv.Val = "#3D3A00";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-citron-13";
		kv.Val = "#312E00";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-citron-14";
		kv.Val = "#2A2700";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-citron-15";
		kv.Val = "#262300";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-cyan-0";
		kv.Val = "#F5FFFF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-cyan-1";
		kv.Val = "#DFFFFF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-cyan-2";
		kv.Val = "#C9FFFF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-cyan-3";
		kv.Val = "#A1F5FB";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-cyan-4";
		kv.Val = "#79E5F0";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-cyan-5";
		kv.Val = "#55CDDF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-cyan-6";
		kv.Val = "#36B1C9";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-cyan-7";
		kv.Val = "#1D92AE";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-cyan-8";
		kv.Val = "#14728E";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-cyan-9";
		kv.Val = "#13647E";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-cyan-10";
		kv.Val = "#11566F";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-cyan-11";
		kv.Val = "#0D4056";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-cyan-12";
		kv.Val = "#0A3043";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-cyan-13";
		kv.Val = "#092535";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-cyan-14";
		kv.Val = "#081D2C";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-cyan-15";
		kv.Val = "#04121C";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-gray-0";
		kv.Val = "#FFFFFF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-gray-1";
		kv.Val = "#F5F8FA";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-gray-2";
		kv.Val = "#F0F3F5";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-gray-3";
		kv.Val = "#E0E4E9";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-gray-4";
		kv.Val = "#C7CDD2";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-gray-5";
		kv.Val = "#B0B9C1";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-gray-6";
		kv.Val = "#979EA8";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-gray-7";
		kv.Val = "#828A93";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-gray-8";
		kv.Val = "#6E7780";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-gray-9";
		kv.Val = "#5B636A";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-gray-10";
		kv.Val = "#464E56";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-gray-11";
		kv.Val = "#2C363F";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-gray-12";
		kv.Val = "#232A31";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-gray-13";
		kv.Val = "#1D2228";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-gray-14";
		kv.Val = "#101518";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-gray-15";
		kv.Val = "#000000";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-green-0";
		kv.Val = "#F6FFF4";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-green-1";
		kv.Val = "#E1FDDC";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-green-2";
		kv.Val = "#CAFAC5";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-green-3";
		kv.Val = "#A2F2A2";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-green-4";
		kv.Val = "#7EE487";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-green-5";
		kv.Val = "#56C470";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-green-6";
		kv.Val = "#30A159";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-green-7";
		kv.Val = "#008751";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-green-8";
		kv.Val = "#0A7A44";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-green-9";
		kv.Val = "#006C3B";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-green-10";
		kv.Val = "#045E34";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-green-11";
		kv.Val = "#044D2C";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-green-12";
		kv.Val = "#043B23";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-green-13";
		kv.Val = "#04331F";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-green-14";
		kv.Val = "#042B1B";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-green-15";
		kv.Val = "#032115";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-indigo-0";
		kv.Val = "#F2F4FF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-indigo-1";
		kv.Val = "#E2E6FF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-indigo-2";
		kv.Val = "#D1D5FF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-indigo-3";
		kv.Val = "#B8BCFF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-indigo-4";
		kv.Val = "#9CA0FF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-indigo-5";
		kv.Val = "#8282FF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-indigo-6";
		kv.Val = "#6D6EFF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-indigo-7";
		kv.Val = "#5D5EFF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-indigo-8";
		kv.Val = "#4040E3";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-indigo-9";
		kv.Val = "#2015C2";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-indigo-10";
		kv.Val = "#1A0DAB";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-indigo-11";
		kv.Val = "#160990";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-indigo-12";
		kv.Val = "#120576";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-indigo-13";
		kv.Val = "#0F035B";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-indigo-14";
		kv.Val = "#0B0141";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-indigo-15";
		kv.Val = "#060026";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-lime-0";
		kv.Val = "#F9FFF5";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-lime-1";
		kv.Val = "#EDFFE0";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-lime-2";
		kv.Val = "#E6FFD1";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-lime-3";
		kv.Val = "#D9FABE";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-lime-4";
		kv.Val = "#CAEFA8";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-lime-5";
		kv.Val = "#ACDE7A";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-lime-6";
		kv.Val = "#89C746";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-lime-7";
		kv.Val = "#65A915";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-lime-8";
		kv.Val = "#4B8500";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-lime-9";
		kv.Val = "#467A00";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-lime-10";
		kv.Val = "#3E6B00";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-lime-11";
		kv.Val = "#325300";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-lime-12";
		kv.Val = "#284100";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-lime-13";
		kv.Val = "#213300";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-lime-14";
		kv.Val = "#1C2B00";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-lime-15";
		kv.Val = "#1A2600";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-magenta-0";
		kv.Val = "#F4EBFF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-magenta-1";
		kv.Val = "#ECD9FB";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-magenta-2";
		kv.Val = "#E5C7F7";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-magenta-3";
		kv.Val = "#DFB6F3";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-magenta-4";
		kv.Val = "#DCA6F0";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-magenta-5";
		kv.Val = "#D488E3";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-magenta-6";
		kv.Val = "#CF6DD6";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-magenta-7";
		kv.Val = "#C954C7";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-magenta-8";
		kv.Val = "#BC3DB1";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-magenta-9";
		kv.Val = "#A42E97";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-magenta-10";
		kv.Val = "#8C207E";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-magenta-11";
		kv.Val = "#741667";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-magenta-12";
		kv.Val = "#5C0D51";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-magenta-13";
		kv.Val = "#44063C";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-magenta-14";
		kv.Val = "#2C0227";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-magenta-15";
		kv.Val = "#140012";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-mint-0";
		kv.Val = "#F5FFF9";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-mint-1";
		kv.Val = "#E7FCF0";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-mint-2";
		kv.Val = "#DAF8E8";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-mint-3";
		kv.Val = "#C9EFDB";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-mint-4";
		kv.Val = "#AEE0C7";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-mint-5";
		kv.Val = "#85CBAC";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-mint-6";
		kv.Val = "#58B38E";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-mint-7";
		kv.Val = "#2D9771";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-mint-8";
		kv.Val = "#0C7A58";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-mint-9";
		kv.Val = "#016D4E";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-mint-10";
		kv.Val = "#016047";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-mint-11";
		kv.Val = "#004A39";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-mint-12";
		kv.Val = "#003A2E";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-mint-13";
		kv.Val = "#002F26";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-mint-14";
		kv.Val = "#002921";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-mint-15";
		kv.Val = "#001C17";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-nude-0";
		kv.Val = "#FFF5F5";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-nude-1";
		kv.Val = "#FBDEDD";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-nude-2";
		kv.Val = "#F8CBC4";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-nude-3";
		kv.Val = "#EBB9AC";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-nude-4";
		kv.Val = "#DEA995";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-nude-5";
		kv.Val = "#CA9B88";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-nude-6";
		kv.Val = "#B68B7A";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-nude-7";
		kv.Val = "#A27A6A";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-nude-8";
		kv.Val = "#8E6859";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-nude-9";
		kv.Val = "#835D50";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-nude-10";
		kv.Val = "#785347";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-nude-11";
		kv.Val = "#643D30";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-nude-12";
		kv.Val = "#502213";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-nude-13";
		kv.Val = "#3C0E00";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-nude-14";
		kv.Val = "#280900";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-nude-15";
		kv.Val = "#140400";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-orange-0";
		kv.Val = "#FFF9F2";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-orange-1";
		kv.Val = "#FFEFDE";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-orange-2";
		kv.Val = "#FFE5CA";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-orange-3";
		kv.Val = "#F9D0A7";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-orange-4";
		kv.Val = "#FCB66F";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-orange-5";
		kv.Val = "#FF9E42";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-orange-6";
		kv.Val = "#FF8915";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-orange-7";
		kv.Val = "#E26900";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-orange-8";
		kv.Val = "#CC4E00";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-orange-9";
		kv.Val = "#BF4900";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-orange-10";
		kv.Val = "#A64000";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-orange-11";
		kv.Val = "#913800";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-orange-12";
		kv.Val = "#732C00";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-orange-13";
		kv.Val = "#5E2501";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-orange-14";
		kv.Val = "#441A00";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-orange-15";
		kv.Val = "#260F00";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-pink-0";
		kv.Val = "#FFF7FC";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-pink-1";
		kv.Val = "#FFEBF9";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-pink-2";
		kv.Val = "#FED8F7";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-pink-3";
		kv.Val = "#FFB0E7";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-pink-4";
		kv.Val = "#FF85D3";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-pink-5";
		kv.Val = "#FF55C3";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-pink-6";
		kv.Val = "#FF36A1";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-pink-7";
		kv.Val = "#FF0080";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-pink-8";
		kv.Val = "#E60073";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-pink-9";
		kv.Val = "#DC0276";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-pink-10";
		kv.Val = "#B20666";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-pink-11";
		kv.Val = "#71004B";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-pink-12";
		kv.Val = "#55003A";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-pink-13";
		kv.Val = "#380028";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-pink-14";
		kv.Val = "#2D0022";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-pink-15";
		kv.Val = "#260010";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-purple-0";
		kv.Val = "#F8F4FF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-purple-1";
		kv.Val = "#EBE5FF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-purple-2";
		kv.Val = "#E1D9FF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-purple-3";
		kv.Val = "#D5CFFF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-purple-4";
		kv.Val = "#CAC2FF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-purple-5";
		kv.Val = "#B09FFC";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-purple-6";
		kv.Val = "#957EFC";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-purple-7";
		kv.Val = "#8367FF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-purple-8";
		kv.Val = "#7C49FC";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-purple-9";
		kv.Val = "#7D2EFF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-purple-10";
		kv.Val = "#6001D2";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-purple-11";
		kv.Val = "#5015B0";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-purple-12";
		kv.Val = "#39007D";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-purple-13";
		kv.Val = "#2B0067";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-purple-14";
		kv.Val = "#1D0047";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-purple-15";
		kv.Val = "#100026";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-red-0";
		kv.Val = "#FFFAFA";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-red-1";
		kv.Val = "#FFF0F1";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-red-2";
		kv.Val = "#FFE7E8";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-red-3";
		kv.Val = "#FFD6DA";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-red-4";
		kv.Val = "#FFBBC0";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-red-5";
		kv.Val = "#FF989F";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-red-6";
		kv.Val = "#FD6A74";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-red-7";
		kv.Val = "#FF4D52";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-red-8";
		kv.Val = "#EE0017";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-red-9";
		kv.Val = "#D30D2E";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-red-10";
		kv.Val = "#BB000A";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-red-11";
		kv.Val = "#9D0004";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-red-12";
		kv.Val = "#7F0000";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-red-13";
		kv.Val = "#620300";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-red-14";
		kv.Val = "#440400";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-red-15";
		kv.Val = "#260300";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-rose-0";
		kv.Val = "#FFF7FA";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-rose-1";
		kv.Val = "#FFEBF3";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-rose-2";
		kv.Val = "#FFE0EF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-rose-3";
		kv.Val = "#FFD1E2";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-rose-4";
		kv.Val = "#FFC1D5";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-rose-5";
		kv.Val = "#FAA2BB";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-rose-6";
		kv.Val = "#E97E9D";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-rose-7";
		kv.Val = "#D3597C";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-rose-8";
		kv.Val = "#BD395F";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-rose-9";
		kv.Val = "#A73455";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-rose-10";
		kv.Val = "#902F4B";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-rose-11";
		kv.Val = "#712339";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-rose-12";
		kv.Val = "#571B2C";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-rose-13";
		kv.Val = "#421623";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-rose-14";
		kv.Val = "#32121B";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-rose-15";
		kv.Val = "#260F16";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-sunset-0";
		kv.Val = "#FFF0EE";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-sunset-1";
		kv.Val = "#FBDDDA";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-sunset-2";
		kv.Val = "#F7CBC7";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-sunset-3";
		kv.Val = "#EAAAA3";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-sunset-4";
		kv.Val = "#DD8C82";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-sunset-5";
		kv.Val = "#D17163";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-sunset-6";
		kv.Val = "#C45947";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-sunset-7";
		kv.Val = "#B8442E";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-sunset-8";
		kv.Val = "#AB3318";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-sunset-9";
		kv.Val = "#9E2C12";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-sunset-10";
		kv.Val = "#91260C";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-sunset-11";
		kv.Val = "#7C1A01";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-sunset-12";
		kv.Val = "#661600";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-sunset-13";
		kv.Val = "#511200";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-sunset-14";
		kv.Val = "#3C0D00";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-sunset-15";
		kv.Val = "#260800";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-teal-0";
		kv.Val = "#F2FFFB";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-teal-1";
		kv.Val = "#E0FFF8";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-teal-2";
		kv.Val = "#CFFFF8";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-teal-3";
		kv.Val = "#B1FFF3";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-teal-4";
		kv.Val = "#85F7E9";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-teal-5";
		kv.Val = "#4EE8DC";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-teal-6";
		kv.Val = "#11D3CD";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-teal-7";
		kv.Val = "#00A0A0";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-teal-8";
		kv.Val = "#008282";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-teal-9";
		kv.Val = "#007A7A";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-teal-10";
		kv.Val = "#006E6B";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-teal-11";
		kv.Val = "#005956";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-teal-12";
		kv.Val = "#004543";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-teal-13";
		kv.Val = "#003635";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-teal-14";
		kv.Val = "#002C2C";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-teal-15";
		kv.Val = "#002626";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-yellow-0";
		kv.Val = "#FFFDEE";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-yellow-1";
		kv.Val = "#FFFAD1";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-yellow-2";
		kv.Val = "#FFF6A1";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-yellow-3";
		kv.Val = "#FFF27D";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-yellow-4";
		kv.Val = "#FFE957";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-yellow-5";
		kv.Val = "#FFD333";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-yellow-6";
		kv.Val = "#E5B917";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-yellow-7";
		kv.Val = "#977500";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-yellow-8";
		kv.Val = "#7C5A00";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-yellow-9";
		kv.Val = "#644300";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-yellow-10";
		kv.Val = "#583900";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-yellow-11";
		kv.Val = "#4B2F00";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-yellow-12";
		kv.Val = "#3A2200";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-yellow-13";
		kv.Val = "#2C1900";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-yellow-14";
		kv.Val = "#201100";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-yellow-15";
		kv.Val = "#150B00";
		KeyValue.yahoo.add(kv);
		kv.Key = "--uds-spectrum-color-blue-0";
		kv.Val = "#F2FAFF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-blue-1";
		kv.Val = "#DFF2FF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-blue-2";
		kv.Val = "#CAEAFF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-blue-3";
		kv.Val = "#A2DAFF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-blue-4";
		kv.Val = "#7DCBFF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-blue-5";
		kv.Val = "#59BDFF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-blue-6";
		kv.Val = "#12A9FF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-blue-7";
		kv.Val = "#188FFF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-blue-8";
		kv.Val = "#0F69FF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-blue-9";
		kv.Val = "#0063EB";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-blue-10";
		kv.Val = "#0059DE";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-blue-11";
		kv.Val = "#0048C4";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-blue-12";
		kv.Val = "#0037A4";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-blue-13";
		kv.Val = "#00277E";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-blue-14";
		kv.Val = "#001753";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-blue-15";
		kv.Val = "#000A26";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-brown-0";
		kv.Val = "#FFFDFA";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-brown-1";
		kv.Val = "#FFF6E0";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-brown-2";
		kv.Val = "#FFECD1";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-brown-3";
		kv.Val = "#FBDDBF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-brown-4";
		kv.Val = "#EDC79F";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-brown-5";
		kv.Val = "#D9AA76";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-brown-6";
		kv.Val = "#B9884F";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-brown-7";
		kv.Val = "#97682F";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-brown-8";
		kv.Val = "#865A23";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-brown-9";
		kv.Val = "#754C19";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-brown-10";
		kv.Val = "#5B380B";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-brown-11";
		kv.Val = "#472A04";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-brown-12";
		kv.Val = "#372101";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-brown-13";
		kv.Val = "#2D1B00";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-brown-14";
		kv.Val = "#261700";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-brown-15";
		kv.Val = "#1A1000";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-carbon-0";
		kv.Val = "#FFFFFF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-carbon-1";
		kv.Val = "#FCFBFB";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-carbon-2";
		kv.Val = "#F5F5F5";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-carbon-3";
		kv.Val = "#E3E3E3";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-carbon-4";
		kv.Val = "#CDCDCD";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-carbon-5";
		kv.Val = "#B0B0B0";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-carbon-6";
		kv.Val = "#8E8E8E";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-carbon-7";
		kv.Val = "#6A6A6A";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-carbon-8";
		kv.Val = "#5A5A5A";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-carbon-9";
		kv.Val = "#444444";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-carbon-10";
		kv.Val = "#323232";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-carbon-11";
		kv.Val = "#292929";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-carbon-12";
		kv.Val = "#1F1F1F";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-carbon-13";
		kv.Val = "#141414";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-carbon-14";
		kv.Val = "#0A0A0A";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-carbon-15";
		kv.Val = "#000000";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-purple-0";
		kv.Val = "#F8F4FF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-purple-1";
		kv.Val = "#EBE5FF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-purple-2";
		kv.Val = "#E1D9FF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-purple-3";
		kv.Val = "#D5CFFF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-purple-4";
		kv.Val = "#CAC2FF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-purple-5";
		kv.Val = "#B09FFC";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-purple-6";
		kv.Val = "#957EFC";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-purple-7";
		kv.Val = "#8367FF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-purple-8";
		kv.Val = "#7C49FC";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-purple-9";
		kv.Val = "#7D2EFF";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-purple-10";
		kv.Val = "#6001D2";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-purple-11";
		kv.Val = "#5015B0";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-purple-12";
		kv.Val = "#39007D";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-purple-13";
		kv.Val = "#2B0067";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-purple-14";
		kv.Val = "#1D0047";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-purple-15";
		kv.Val = "#100026";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-red-0";
		kv.Val = "#FFFAFA";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-red-1";
		kv.Val = "#FFF0F1";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-red-2";
		kv.Val = "#FFE7E8";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-red-3";
		kv.Val = "#FFD6DA";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-red-4";
		kv.Val = "#FFBBC0";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-red-5";
		kv.Val = "#FF989F";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-red-6";
		kv.Val = "#FD6A74";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-red-7";
		kv.Val = "#FF4D52";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-red-8";
		kv.Val = "#EE0017";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-red-9";
		kv.Val = "#D30D2E";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-red-10";
		kv.Val = "#BB000A";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-red-11";
		kv.Val = "#9D0004";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-red-12";
		kv.Val = "#7F0000";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-red-13";
		kv.Val = "#620300";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-red-14";
		kv.Val = "#440400";
		KeyValue.yahoo.add(kv);
		kv = new KV();
		kv.Key = "--uds-spectrum-color-red-15";
		kv.Val = "#260300";
		KeyValue.yahoo.add(kv);
	}
}
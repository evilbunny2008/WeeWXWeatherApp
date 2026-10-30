package com.odiousapps.weewxweather;

//
//  Android 17 (API 37) blocks connections to the local network unless the app holds
//  ACCESS_LOCAL_NETWORK. This class works out which URLs point at the LAN, so the app
//  can ask for the permission first, and fails those connections with a clear message
//  when the permission has been refused.
//

import android.content.pm.PackageManager;
import android.net.InetAddresses;
import android.os.Build;
import android.os.Looper;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import androidx.core.content.ContextCompat;

import static com.odiousapps.weewxweather.WeeWXApp.getAndroidString;
import static com.odiousapps.weewxweather.WeeWXAppCommon.LogMessage;
import static com.odiousapps.weewxweather.WeeWXAppCommon.is_blank;

final class LocalNetwork
{
	static final String PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK";

	// Names that only ever resolve on a LAN
	private static final String[] localSuffixes = {".local", ".lan", ".home", ".home.arpa", ".internal", ".localdomain"};

	// host -> resolves to a LAN address, so we only do the DNS lookup once per host
	private static final Map<String, Boolean> resolvedHosts = new ConcurrentHashMap<>();

	private LocalNetwork()
	{}

	static class LocalNetworkException extends UnknownHostException
	{
		LocalNetworkException(String host)
		{
			super(getAndroidString(R.string.local_network_permission_denied) + "\n\n" + host);
		}
	}

	static boolean hasPermission()
	{
		return Build.VERSION.SDK_INT < 37 ||
			   ContextCompat.checkSelfPermission(WeeWXApp.getInstance(), PERMISSION) == PackageManager.PERMISSION_GRANTED;
	}

	static String getHost(String url)
	{
		if(is_blank(url))
			return null;

		try
		{
			String host = URI.create(url.strip()).getHost();
			if(is_blank(host))
				return null;

			host = host.toLowerCase(Locale.ROOT);
			if(host.startsWith("[") && host.endsWith("]"))
				host = host.substring(1, host.length() - 1);

			return host;
		} catch(IllegalArgumentException e) {
			return null;
		}
	}

	static boolean isLocalAddress(InetAddress address)
	{
		// Loopback stays on the device, so it isn't the local network
		if(address == null || address.isLoopbackAddress())
			return false;

		if(address.isSiteLocalAddress() || address.isLinkLocalAddress() || address.isMulticastAddress())
			return true;

		// IPv6 unique local addresses, fc00::/7
		return address instanceof Inet6Address && (address.getAddress()[0] & 0xfe) == 0xfc;
	}

	// Checks IP literals and LAN-only names, never does a DNS lookup
	static boolean isLocalHostName(String host)
	{
		if(is_blank(host))
			return false;

		host = host.toLowerCase(Locale.ROOT);

		if(InetAddresses.isNumericAddress(host))
			return isLocalAddress(InetAddresses.parseNumericAddress(host));

		if(host.equals("localhost"))
			return false;

		// Single label names like http://weewx/ can only come from the LAN
		if(!host.contains("."))
			return true;

		for(String suffix : localSuffixes)
			if(host.endsWith(suffix))
				return true;

		return false;
	}

	// resolve = also look the hostname up in DNS, which must not happen on the main thread
	static boolean isLocalUrl(String url, boolean resolve)
	{
		String host = getHost(url);
		if(host == null)
			return false;

		if(isLocalHostName(host))
			return true;

		if(!resolve || InetAddresses.isNumericAddress(host) || WeeWXApp.customDns == null ||
		   Looper.myLooper() == Looper.getMainLooper())
			return false;

		Boolean cached = resolvedHosts.get(host);
		if(cached != null)
			return cached;

		boolean isLocal = false;
		for(InetAddress address : WeeWXApp.customDns.lookup(host))
		{
			if(isLocalAddress(address))
			{
				isLocal = true;
				break;
			}
		}

		LogMessage("LocalNetwork.isLocalUrl() " + host + " is on the local network: " + isLocal);
		resolvedHosts.put(host, isLocal);
		return isLocal;
	}

	// Returns the distinct hosts from urls that are on the local network
	static List<String> localHosts(Collection<String> urls, boolean resolve)
	{
		List<String> hosts = new ArrayList<>();

		for(String url : urls)
		{
			String host = getHost(url);
			if(host != null && !hosts.contains(host) && isLocalUrl(url, resolve))
				hosts.add(host);
		}

		return hosts;
	}

	// Called by the OkHttp interceptor before connecting
	static void checkHost(String host) throws LocalNetworkException
	{
		if(!hasPermission() && isLocalHostName(host))
			throw new LocalNetworkException(host);
	}

	// Called by the OkHttp DNS wrapper, drops LAN addresses we aren't allowed to use
	static List<InetAddress> filterAddresses(String hostname, List<InetAddress> addresses) throws LocalNetworkException
	{
		if(addresses.isEmpty() || hasPermission())
			return addresses;

		List<InetAddress> allowed = new ArrayList<>();
		for(InetAddress address : addresses)
			if(!isLocalAddress(address))
				allowed.add(address);

		if(allowed.isEmpty())
			throw new LocalNetworkException(hostname);

		return allowed;
	}
}

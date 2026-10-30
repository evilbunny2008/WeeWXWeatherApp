package com.odiousapps.weewxweather;

import android.annotation.SuppressLint;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Cache;
import okhttp3.ConnectionSpec;
import okhttp3.Credentials;
import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.TlsVersion;

import static com.odiousapps.weewxweather.WeeWXApp.cacheDir;
import static com.odiousapps.weewxweather.WeeWXApp.customDns;
import static com.odiousapps.weewxweather.WeeWXAppCommon.LogMessage;
import static com.odiousapps.weewxweather.WeeWXAppCommon.getNextRandom;

@SuppressLint({"CustomX509TrustManager", "TrustAllX509TrustManager"})
class NetworkClient
{
	final static String[] UAstrings = {
		"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36",
		"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36 Edg/134.0.3124.85",
		"Mozilla/5.0 (Windows NT 10.0; Win64; x64; Xbox; Xbox One) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36 Edge/44.18363.8131",
		"Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0",
		"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36 OPR/118.0.0.0",
		"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36",
		"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36 Edg/134.0.3124.85",
		"Mozilla/5.0 (Macintosh; Intel Mac OS X 14_7_4) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.3 Safari/605.1.15",
		"Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36",
		"Mozilla/5.0 (X11; Linux x86_64; rv:136.0) Gecko/20100101 Firefox/136.0",
		"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:136.0) Gecko/20100101 Firefox/136.0",
		"Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36",
		"Mozilla/5.0 (X11; CrOS x86_64 14541.0.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36",
		"AppleTV14,1/16.1",
		"Mozilla/5.0 (CrKey armv7l 1.5.16041) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/31.0.1650.0 Safari/537.36",
		"Mozilla/5.0 (PlayStation; PlayStation 5/2.26) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0 Safari/605.1.15",
		"Mozilla/5.0 (Windows NT 10.0; Win64; x64; Xbox; Xbox Series X) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/48.0.2564.82 Safari/537.36 Edge/20.02"
	};

	static String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36";

	private static OkHttpClient clientInstance = null;
	private static OkHttpClient clientNoTimeoutInstance = null;
	private static OkHttpClient.Builder defaultBuilder = null;

	private static TrustManager[] trustAllCerts;
	private static SSLSocketFactory sslSocketFactory;
	private static ConnectionSpec modernTLS;

	// Fail LAN connections with a clear error when the local network permission was refused
	private static final Dns localNetworkDns = hostname ->
			LocalNetwork.filterAddresses(hostname, customDns.lookup(hostname));

	static
	{
		resetUA();

		try
		{
			trustAllCerts = new TrustManager[]{new X509TrustManager()
			{
				@Override
				public void checkClientTrusted(X509Certificate[] chain, String authType)
				{}

				@Override
				public void checkServerTrusted(X509Certificate[] chain, String authType)
				{}

				@Override
				public X509Certificate[] getAcceptedIssuers()
				{
					return new X509Certificate[0];
				}
			}};

			SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(null, trustAllCerts, new SecureRandom());
			sslSocketFactory = sslContext.getSocketFactory();

			// Define connection spec with TLS 1.3 first, fallback to 1.2
			modernTLS = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
					.tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
					.allEnabledCipherSuites().build();
		} catch(Exception e) {
			LogMessage("NetworkClient.java Error! e: " + e.getMessage(), KeyValue.e);
			//noinspection CallToPrintStackTrace
			e.printStackTrace();
		}
	}

	static void generate()
	{
		File cache_dir = new File(cacheDir, "cache_dir");
		if(!cache_dir.exists() && !cache_dir.mkdirs())
		{
			LogMessage("Failed to create " + cache_dir.getAbsolutePath(), KeyValue.e);
			return;
		}

		try
		{
			Cache cache = new Cache(cache_dir, 512L * 1_024L * 1_024L);

			defaultBuilder = new OkHttpClient.Builder()
				.sslSocketFactory(sslSocketFactory, (X509TrustManager)trustAllCerts[0])
				.hostnameVerifier((hostname, session) -> true)
				.connectionSpecs(Arrays.asList(modernTLS, ConnectionSpec.CLEARTEXT))
				.addInterceptor(chain ->
				{
					LocalNetwork.checkHost(chain.request().url().host());
					return chain.proceed(chain.request());
				});

			clientNoTimeoutInstance = defaultBuilder.build().newBuilder()
				.connectTimeout(30_000L, TimeUnit.MILLISECONDS)
				.writeTimeout(30_000L, TimeUnit.MILLISECONDS)
				.readTimeout(30_000L, TimeUnit.MILLISECONDS)
				.callTimeout(60_000L, TimeUnit.MILLISECONDS)
				.dns(localNetworkDns)
				.build();

			clientInstance = defaultBuilder.build().newBuilder()
				.connectTimeout(WeeWXAppCommon.default_timeout, TimeUnit.MILLISECONDS)
				.writeTimeout(WeeWXAppCommon.default_timeout, TimeUnit.MILLISECONDS)
				.readTimeout(WeeWXAppCommon.default_timeout, TimeUnit.MILLISECONDS)
				.callTimeout(WeeWXAppCommon.default_timeout * 2, TimeUnit.MILLISECONDS)
				.dns(localNetworkDns)
				.cache(cache)
				.build();
		} catch (Exception e) {
			//noinspection CallToPrintStackTrace
			e.printStackTrace();
		}
	}

	private static void resetUA()
	{
		LogMessage("Shuffling UAstrings");
		UA = UAstrings[getNextRandom(0, UAstrings.length)];
		LogMessage("UA set to " + UA);
	}

	@NotNull
	static OkHttpClient.Builder getBuilder()
	{
		return defaultBuilder.build().newBuilder();
	}

	@NotNull
	static OkHttpClient getInstance(@NotNull HttpUrl url)
	{
		// windy.com is very noisy... 2s connectivity checks is beyond excessive...
		//if(!url.contains("windy.com"))
//		LogMessage("NetworkClient.getInstance() URL: " + url);
		if(url.encodedUsername().isBlank() || url.encodedPassword().isBlank())
			return clientInstance;

		String credentials = Credentials.basic(url.encodedUsername(), url.encodedPassword());
		return clientInstance.newBuilder().authenticator((route, response) ->
		{
			if(responseCount(response) >= 3)
				return null;

			return response.request().newBuilder().header("Authorization", credentials).build();
		}).build();
	}

	@NotNull
	static OkHttpClient getStream(@NotNull HttpUrl url)
	{
//		LogMessage("NetworkClient.getStream() URL: " + url);
		if(url.encodedUsername().isBlank() || url.encodedPassword().isBlank())
			return clientNoTimeoutInstance;

		String credentials = Credentials.basic(url.encodedUsername(), url.encodedPassword());
		return clientNoTimeoutInstance.newBuilder().authenticator((route, response) ->
		{
			if(responseCount(response) >= 3)
				return null;

			return response.request().newBuilder().header("Authorization", credentials).build();
		}).build();
	}

	private static int responseCount(@NotNull Response response)
	{
		int result = 1;

		while((response = response.priorResponse()) != null)
			result++;

		return result;
	}

	@SuppressWarnings("SameParameterValue")
	@NotNull
	static Request getRequest(boolean doHead, @NotNull HttpUrl url, boolean sendReferer)
	{
//		LogMessage("NetworkClient.getRequest() URL: " + url);

		Request.Builder req = new Request.Builder();

		req.header("User-Agent", UA)
			.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
			.header("Accept-Language", Locale.getDefault().getLanguage())
			.header("Cache-Control", "private, max-age=0, must-revalidate, no-cache, no-store")
			.header("Pragma", "no-cache")
			.header("Expires", "0")
			.url(url);

		if(sendReferer)
		{
			String url2 = url.toString();
			String referer = url2.indexOf("/", 8) > 0 ? url2.substring(0, url2.indexOf("/", 8)) : url2;
//			LogMessage("NetworkClient.getRequest() referer: " + referer);
			req.header("Referer", referer);
		}

		if(!url.isHttps())
			req.header("Upgrade-Insecure-Requests", "1");

		if(doHead)
			req.head();

		return req.build();
	}
}
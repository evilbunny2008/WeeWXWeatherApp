package com.odiousapps.weewxweather;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.InterruptedIOException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static com.odiousapps.weewxweather.NetworkClient.getInstance;
import static com.odiousapps.weewxweather.NetworkClient.getRequest;
import static com.odiousapps.weewxweather.WeeWXAppCommon.doStackOutput;
import static com.odiousapps.weewxweather.WeeWXAppCommon.LogMessage;
import static com.odiousapps.weewxweather.WeeWXAppCommon.is_blank;

@SuppressWarnings({"SequencedCollectionMethodCanBeUsed", "unused"})
public class SafeWebView extends WebView
{
	private final List<String> bad_paths = new ArrayList<>();
	private String Url = null;
	private String data = null;
	private String baseUrl = null;
	private String mimeType = null;
	private String encoding = null;
	private String historyUrl = null;
	private boolean hasBeenDestroyed = false;
	private boolean timeout;
	private boolean outputDebugLogs = false;

	private final String[] responseHeaderNames =
	{
		// CORS
		"Access-Control-Allow-Origin",
		"Access-Control-Allow-Methods",
		"Access-Control-Allow-Headers",
		"Access-Control-Allow-Credentials",
		"Access-Control-Expose-Headers",
		"Access-Control-Max-Age",

		// Caching / freshness
		"Cache-Control",
		"Expires",
		"Pragma",
		"Age",
		"ETag",
		"If-None-Match",
		"Last-Modified",

		// Content metadata
		"Content-Type",
		"Content-Language",
		"Content-Encoding",
		"Content-Disposition",
		"Content-Range",
		"Accept-Ranges",

		// Cookies / sessions
		"Set-Cookie",
		"Set-Cookie2",
		"Cookie",

		// Security / policies
		"Strict-Transport-Security",
		"X-Frame-Options",
		"Content-Security-Policy",
		"Content-Security-Policy-Report-Only",
		"Referrer-Policy",
		"Permissions-Policy",
		"Expect-CT",
		"Feature-Policy",		   // legacy name
		"X-Content-Type-Options",
		"X-XSS-Protection",

		// Cross-origin / timing / hints
		"Timing-Allow-Origin",
		"Origin",
		"Vary",
		"Link",
		"Server",
		"Host",
		"Via",

		// Performance / resource hints
		"Accept-Ranges",
		"Allow",
		"Retry-After",
		"Warning",
	};

	public SafeWebView(@NonNull Context context)
	{
		super(context);
		initSettings();
	}

	public SafeWebView(@NonNull Context context, @Nullable AttributeSet attrs)
	{
		super(context, attrs);
		initSettings();
	}

	public SafeWebView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr)
	{
		super(context, attrs, defStyleAttr);
		initSettings();
	}

	// Pages like windy.com size themselves to the viewport when they load, so wait
	// until the view has been laid out with a real size before running
	static void runWhenSized(View view, Runnable runnable)
	{
		if(view.isLaidOut() && view.getWidth() > 0 && view.getHeight() > 0)
		{
			view.post(runnable);
			return;
		}

		view.addOnLayoutChangeListener(new View.OnLayoutChangeListener()
		{
			@Override
			public void onLayoutChange(View v, int left, int top, int right, int bottom,
									   int oldLeft, int oldTop, int oldRight, int oldBottom)
			{
				if(v.getWidth() <= 0 || v.getHeight() <= 0)
					return;

				v.removeOnLayoutChangeListener(this);
				v.post(runnable);
			}
		});
	}

	// Initialize SafeWebView safely with common settings.
	@SuppressLint("SetJavaScriptEnabled")
	void initSettings()
	{
		if(hasBeenDestroyed)
		{
			LogMessage("SafeWebView.initSettings() hasBeenDestroyed is true, stop actions on dead webviews...", KeyValue.e);
			return;
		}

		bad_paths.add("favicon.ico");

		timeout = true;

		try
		{
//			ws.setAllowFileAccess(true);
//			ws.setAllowFileAccessFromFileURLs(true);
//			ws.setAllowUniversalAccessFromFileURLs(true);

			// Always safe to create on API 24+
			loadData("", "text/html", "utf-8");
			WebSettings ws = getSettings();
			ws.setJavaScriptEnabled(true);
			ws.setDomStorageEnabled(true);
			ws.setLoadsImagesAutomatically(true);
			ws.setAllowContentAccess(true);

			ws.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
			ws.setLoadWithOverviewMode(true);
			ws.setUseWideViewPort(true);
			ws.setDisplayZoomControls(false);
			ws.setBuiltInZoomControls(false);

			//ws.setCacheMode(WebSettings.LOAD_NO_CACHE);

			// Apply user agent
			ws.setUserAgentString(NetworkClient.UA);

			setOverScrollMode(View.OVER_SCROLL_NEVER);
			setNestedScrollingEnabled(true);
			setVerticalScrollBarEnabled(false);
			setHorizontalScrollBarEnabled(false);
			setWebChromeClient(new myWebChromeClient());
		} catch(Throwable t) {
			doStackOutput(t);
		}
	}

	public void destroy()
	{
		if(hasBeenDestroyed)
		{
			LogMessage("SafeWebView.destroy() hasBeenDestroyed is true, stop actions on dead webviews...", KeyValue.e);
			return;
		}

		if(outputDebugLogs)
			LogMessage("SafeWebView.destroy() setting hasBeenDestroyed to true, finish destroying now...");

		hasBeenDestroyed = true;

		try
		{
			if(outputDebugLogs)
				LogMessage("SafeWebView.destroy() stopping WebView loading...");

			stopLoading();
		} catch(Exception ignored) {}

		try
		{
			if(outputDebugLogs)
				LogMessage("SafeWebView.destroy() clearing cache, history and formdata...");

			clearCache(true);
			clearHistory();
			clearFormData();
		} catch(Exception ignored) {}

		try
		{
			if(outputDebugLogs)
				LogMessage("SafeWebView.destroy() wiping cookies...");

			CookieManager cookieManager = CookieManager.getInstance();
			cookieManager.removeAllCookies(null);
			cookieManager.flush();
		} catch(Exception ignored) {}

		try
		{
			if(outputDebugLogs)
				LogMessage("SafeWebView.destroy() removing any bridges...");

			removeJavascriptInterface("AndroidBridge");
		} catch(Exception ignored) {}

		try
		{
			if(outputDebugLogs)
				LogMessage("SafeWebView.destroy() removing from any parents...");

			ViewGroup parent = (ViewGroup)getParent();
			if(parent != null)
				parent.removeView(this);

		} catch(Exception ignored) {}

		try
		{
			if(outputDebugLogs)
				LogMessage("SafeWebView.destroy() removing any views");

			removeAllViews();
		} catch(Exception ignored) {}

		if(outputDebugLogs)
			LogMessage("SafeWebView.destroy() calling super.destroy()");

		super.destroy();
	}

	@Override
	public void loadUrl(@NonNull String url)
	{
		if(hasBeenDestroyed)
		{
			LogMessage("SafeWebView.loadUrl() hasBeenDestroyed is true, stop actions on dead webviews...", KeyValue.e);
			return;
		}

		if(outputDebugLogs)
			LogMessage("SafeWebView.loadUrl() Loading URL: " + url);

		this.Url = url;
		this.baseUrl = null;
		this.data = null;
		this.mimeType = null;
		this.encoding = null;
		this.historyUrl = null;
		super.loadUrl(url);
	}

	public void loadDataWithBaseURL(String baseUrl, @NonNull String data, String mimeType, String encoding, String historyUrl)
	{
		if(hasBeenDestroyed)
		{
			LogMessage("SafeWebView.loadDataWithBaseURL() hasBeenDestroyed is true, stop actions on dead webviews...", KeyValue.e);
			return;
		}

		if(outputDebugLogs)
			LogMessage("SafeWebView.loadDataWithBaseURL() calling super.loadDataWithBaseURL()");

		this.Url = null;
		this.baseUrl = baseUrl;
		this.data = data;
		this.mimeType = mimeType;
		this.encoding = encoding;
		this.historyUrl = historyUrl;
		super.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, historyUrl);
	}

	public void setOnCustomPageFinishedListener(final OnCustomPageFinishedListener listener, boolean overrideRequest)
	{
		if(overrideRequest)
		{
			setWebViewClient(new WebViewClient()
			{
				@Override
				public boolean onRenderProcessGone(WebView webView, RenderProcessGoneDetail detail)
				{
					if(hasBeenDestroyed)
					{
						LogMessage("SafeWebView.onRenderProcessGone() hasBeenDestroyed is true, stop actions on dead webviews...", KeyValue.e);
						return false;
					}

					if(!detail.didCrash())
					{
						// OOM kill — clean up and recreate
						restartWebview((SafeWebView)webView);

						// true = handled, prevents app crash
						return true;
					}

					return false; // crash — let it propagate
				}

				@Override
				public void onPageFinished(WebView view, String url)
				{
					super.onPageFinished(view, url);

					timeout = false;

					if(hasBeenDestroyed)
					{
						LogMessage("SafeWebView.onPageFinished() hasBeenDestroyed is true, stop actions on dead webviews...", KeyValue.e);
						return;
					}

					listener.onCustomPageFinished(SafeWebView.this, url);
				}

				@Override
				public void onLoadResource(WebView view, String url)
				{
					super.onLoadResource(view, url);

					if(hasBeenDestroyed)
					{
						LogMessage("SafeWebView.onPageFinished() hasBeenDestroyed is true, stop actions on dead webviews...", KeyValue.e);
						return;
					}

					if(outputDebugLogs)
						LogMessage("onLoadResource() url: " + url + ", progress: " + view.getProgress());
				}

				@Override
				public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request)
				{
					if(hasBeenDestroyed)
					{
						LogMessage("SafeWebView.shouldInterceptRequest() hasBeenDestroyed is true, stop actions on dead webviews...", KeyValue.e);
						return new WebResourceResponse("text/html", "UTF-8", null);
					}

					String hostname = request.getUrl().getHost();
					if(hostname == null)
						return new WebResourceResponse("text/html", "UTF-8", null);

					String extension_check = request.getUrl().toString().strip();
					if(is_blank(extension_check))
						return new WebResourceResponse("text/html", "UTF-8", null);

					extension_check = extension_check.toLowerCase(Locale.ENGLISH).strip();
					if(extension_check.endsWith(".mkv") || extension_check.endsWith(".mp4") || extension_check.endsWith(".webm"))
						return null;

					hostname = hostname.toUpperCase(Locale.ENGLISH).strip();
					if(!is_blank(hostname))
					{
						for(String bad_domain : CustomDns.bad_domains)
						{
							if(hostname.toLowerCase(Locale.ENGLISH).endsWith(bad_domain))
							{
								if(outputDebugLogs)
									LogMessage("SafeWebView.shouldInterceptRequest() hostname blocked: " + hostname);

								return new WebResourceResponse("text/html", "UTF-8", null);
							}
						}
					}

					String method = request.getMethod();
					if(method.equalsIgnoreCase("options"))
						return new WebResourceResponse(WeeWXApp.CONTENT_TYPE, "UTF-8", new ByteArrayInputStream(new byte[0]));

					List<String> paths = request.getUrl().getPathSegments();
					if(!paths.isEmpty())
					{
						String path = paths.get(paths.size() - 1);

						if(bad_paths.contains(path))
						{
							if(outputDebugLogs)
								LogMessage("SafeWebView.shouldInterceptRequest() Bad path found: " + path);

							return new WebResourceResponse("text/html", "UTF-8", null);
						}
					}

					String url = request.getUrl().toString().strip();
					HttpUrl url2 = HttpUrl.parse(url);
					if(url2 == null)
						return new WebResourceResponse("text/html", "UTF-8", null);

					Request.Builder b = getRequest(false, url2, true).newBuilder();

					for(String key : request.getRequestHeaders().keySet())
					{
						if(key.equalsIgnoreCase("user-agent"))
							continue;

						String val = request.getRequestHeaders().get(key);
						if(is_blank(val))
							continue;

						try
						{
							b.header(key, val);
						} catch (IllegalArgumentException ignored) {
							// Ignore bogus headers that crash the app
						}
					}

					if(method.equalsIgnoreCase("post"))
						b.post(RequestBody.create(new byte[0], null));

					OkHttpClient okHttpClient = getInstance(url2);

					try(Response response = okHttpClient.newCall(b.build()).execute())
					{
						byte[] bytes = response.body().bytes();

						if(outputDebugLogs)
							LogMessage("SafeWebView.shouldInterceptRequest() Got a response of " + bytes.length + " for " + url);

						ByteArrayInputStream bais = new ByteArrayInputStream(bytes);

						String encoding = "UTF-8";
						String mime = WeeWXApp.CONTENT_TYPE;

						MediaType mt = response.body().contentType();
						if(mt != null)
						{
							mime = mt.type() + "/" + mt.subtype();

							Charset cs = mt.charset();
							if(cs != null)
							{
								encoding = cs.name();
							}
						}

						WebResourceResponse wr = new WebResourceResponse(mime, encoding, bais);

						// copy CORS (and any other) headers
						Headers okHeaders = response.headers();
						Map<String, String> map = new HashMap<>();
						for(String name : responseHeaderNames)
						{
							List<String> values = okHeaders.values(name);
							if(!values.isEmpty())
							{
								// join multiple values with comma (HTTP allows multiple), or add first only
								map.put(name, String.join(", ", values));
							}
						}

						if(!map.isEmpty())
							wr.setResponseHeaders(map);

						return wr;
					} catch(InterruptedIOException | UnknownHostException ignored) {
						//doStackOutput(se);
					} catch(Exception e) {
						LogMessage("SafeWebView.shouldInterceptRequest() Error! e: " + e, true, KeyValue.e);
						doStackOutput(e);
					}

					return null;
				}
			});
		} else {
			setWebViewClient(new WebViewClient()
			{
				@Override
				public boolean onRenderProcessGone(WebView webView, RenderProcessGoneDetail detail)
				{
					if(hasBeenDestroyed)
					{
						LogMessage("SafeWebView.onRenderProcessGone() hasBeenDestroyed is true, stop actions on dead webviews...", KeyValue.e);
						return false;
					}

					if(!detail.didCrash())
					{
						// OOM kill — clean up and recreate
						restartWebview((SafeWebView)webView);

						// true = handled, prevents app crash
						return true;
					}

					return false; // crash — let it propagate
				}

				@Override
				public void onPageFinished(WebView view, String url)
				{
					super.onPageFinished(view, url);

					if(hasBeenDestroyed)
					{
						LogMessage("SafeWebView.onPageFinished() hasBeenDestroyed is true, stop actions on dead webviews...", KeyValue.e);
						return;
					}

					listener.onCustomPageFinished(SafeWebView.this, url);
				}

				@Override
				public void onLoadResource(WebView view, String url)
				{
					super.onLoadResource(view, url);

					if(hasBeenDestroyed)
					{
						LogMessage("SafeWebView.onPageFinished() hasBeenDestroyed is true, stop actions on dead webviews...", KeyValue.e);
						return;
					}

					if(outputDebugLogs)
						LogMessage("onLoadResource() url: " + url + ", progress: " + view.getProgress());
				}
			});
		}
	}

	private void restartWebview(SafeWebView webViewToRemake)
	{
		if(hasBeenDestroyed)
		{
			LogMessage("SafeWebView.restartWebview() hasBeenDestroyed is true, stop actions on dead webviews...", KeyValue.e);
			return;
		}

		webViewToRemake.destroy();
		webViewToRemake = new SafeWebView(WeeWXApp.getInstance());
		if(this.Url != null)
		{
			LogMessage("SafeWebView.restartWebview() restarting with URL: " + this.Url, KeyValue.w);
			webViewToRemake.loadUrl(this.Url);
		} else if(this.data != null) {
			LogMessage("SafeWebView.restartWebview() restarting with data...", KeyValue.w);
			webViewToRemake.loadDataWithBaseURL(this.baseUrl, this.data, this.mimeType, this.encoding, this.historyUrl);
		}
	}

	public void setDebugLogging(boolean newState)
	{
		if(newState != outputDebugLogs)
			outputDebugLogs = newState;
	}

	public interface OnCustomPageFinishedListener
	{
		void onCustomPageFinished(SafeWebView view, String url);
	}
}
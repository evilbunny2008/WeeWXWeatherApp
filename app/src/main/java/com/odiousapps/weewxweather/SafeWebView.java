package com.odiousapps.weewxweather;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
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
	private boolean tvPointer = false;
	private boolean dragging = false;
	private float pointerX = -1;
	private float pointerY = -1;
	private final Paint pointerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Path pointerShape = new Path();

	private static final int DRAG_STEPS = 8;
	private static final long DRAG_STEP_MS = 16;

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

	// The foreground (the TV focus ring) is drawn in content coordinates, which move as
	// the page scrolls, so undo the scroll to keep it around the edges of the view.
	// Scrollbars are also drawn here, but they're turned off in initSettings().
	@Override
	public void onDrawForeground(@NonNull Canvas canvas)
	{
		canvas.save();
		canvas.translate(getScrollX(), getScrollY());
		super.onDrawForeground(canvas);
		if(showTvPointer())
			drawTvPointer(canvas);
		canvas.restore();
	}

	// Pages like windy.com's map draw their own controls, so a remote has nothing it can
	// land on. Steer a pointer around those pages instead: centre clicks what's under it,
	// and pushing it against an edge drags the page. Back returns to the tabs as usual.
	void enableTvPointer()
	{
		tvPointer = WeeWXApp.isTV();
	}

	private boolean showTvPointer()
	{
		return tvPointer && !hasBeenDestroyed && isFocused() && pageFitsView();
	}

	private boolean pageFitsView()
	{
		return !canScrollVertically(1) && !canScrollVertically(-1);
	}

	private void drawTvPointer(Canvas canvas)
	{
		if(pointerX < 0 || pointerY < 0)
		{
			pointerX = getWidth() / 2f;
			pointerY = getHeight() / 2f;
		}

		float size = 24 * WeeWXApp.getDensity();
		pointerShape.reset();
		pointerShape.moveTo(pointerX, pointerY);
		pointerShape.lineTo(pointerX, pointerY + size);
		pointerShape.lineTo(pointerX + size * 0.28f, pointerY + size * 0.75f);
		pointerShape.lineTo(pointerX + size * 0.72f, pointerY + size * 0.72f);
		pointerShape.close();

		pointerPaint.setStyle(Paint.Style.FILL);
		pointerPaint.setColor(Color.WHITE);
		canvas.drawPath(pointerShape, pointerPaint);

		pointerPaint.setStyle(Paint.Style.STROKE);
		pointerPaint.setStrokeWidth(2 * WeeWXApp.getDensity());
		pointerPaint.setColor(Color.BLACK);
		canvas.drawPath(pointerShape, pointerPaint);
	}

	private boolean handleTvPointerKey(KeyEvent event)
	{
		int dx = 0, dy = 0;
		switch(event.getKeyCode())
		{
			case KeyEvent.KEYCODE_DPAD_LEFT -> dx = -1;
			case KeyEvent.KEYCODE_DPAD_RIGHT -> dx = 1;
			case KeyEvent.KEYCODE_DPAD_UP -> dy = -1;
			case KeyEvent.KEYCODE_DPAD_DOWN -> dy = 1;
			case KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER ->
			{
				if(event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0)
					clickAtPointer();

				return true;
			}
			default ->
			{
				return false;
			}
		}

		if(event.getAction() == KeyEvent.ACTION_DOWN)
			moveTvPointer(dx, dy, event.getRepeatCount());

		return true;
	}

	// Holding a key down speeds the pointer up
	private void moveTvPointer(int dx, int dy, int repeatCount)
	{
		float step = Math.min(getWidth(), getHeight()) / 40f * (1 + Math.min(repeatCount, 8) / 2f);
		float maxX = getWidth() - 1;
		float maxY = getHeight() - 1;

		if((dx < 0 && pointerX <= 0) || (dx > 0 && pointerX >= maxX) ||
		   (dy < 0 && pointerY <= 0) || (dy > 0 && pointerY >= maxY))
		{
			dragPage(dx, dy);
			return;
		}

		pointerX = Math.max(0, Math.min(maxX, pointerX + dx * step));
		pointerY = Math.max(0, Math.min(maxY, pointerY + dy * step));
		sendMouseEvent(MotionEvent.ACTION_HOVER_MOVE, 0, pointerX, pointerY, SystemClock.uptimeMillis());
		invalidate();
	}

	private void clickAtPointer()
	{
		long downTime = SystemClock.uptimeMillis();
		sendMouseEvent(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_PRIMARY, pointerX, pointerY, downTime);
		postDelayed(() ->
		{
			if(!hasBeenDestroyed)
				sendMouseEvent(MotionEvent.ACTION_UP, 0, pointerX, pointerY, downTime);
		}, 50);
	}

	// Drag from the middle of the view, the edges are where the page's own controls are
	private void dragPage(int dx, int dy)
	{
		if(dragging)
			return;

		dragging = true;
		float startX = getWidth() / 2f;
		float startY = getHeight() / 2f;
		float distance = Math.min(getWidth(), getHeight()) / 3f;
		long downTime = SystemClock.uptimeMillis();
		sendMouseEvent(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_PRIMARY, startX, startY, downTime);

		for(int step = 1; step <= DRAG_STEPS; step++)
		{
			int thisStep = step;
			float x = startX - dx * distance * step / DRAG_STEPS;
			float y = startY - dy * distance * step / DRAG_STEPS;
			postDelayed(() ->
			{
				if(hasBeenDestroyed)
					return;

				sendMouseEvent(MotionEvent.ACTION_MOVE, MotionEvent.BUTTON_PRIMARY, x, y, downTime);
				if(thisStep == DRAG_STEPS)
				{
					sendMouseEvent(MotionEvent.ACTION_UP, 0, x, y, downTime);
					dragging = false;
				}
			}, step * DRAG_STEP_MS);
		}
	}

	// A mouse rather than a touch, a page only sees touches if the device has a touchscreen
	private void sendMouseEvent(int action, int buttonState, float x, float y, long downTime)
	{
		MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties();
		properties.id = 0;
		properties.toolType = MotionEvent.TOOL_TYPE_MOUSE;

		MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
		coords.x = x;
		coords.y = y;
		coords.pressure = 1;
		coords.size = 1;

		MotionEvent event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, 1,
				new MotionEvent.PointerProperties[]{properties},
				new MotionEvent.PointerCoords[]{coords}, 0, buttonState, 1, 1, 0, 0,
				InputDevice.SOURCE_MOUSE, 0);

		if(action == MotionEvent.ACTION_HOVER_MOVE)
			dispatchGenericMotionEvent(event);
		else
			dispatchTouchEvent(event);

		event.recycle();
	}

	// A TV remote's up/down only scrolls a page about 40px at a time, and the page keeps
	// the focus until it's scrolled right back to the top. Scroll 40% of the screen per
	// press instead, and once there's no further to go let the key move the focus as
	// normal. Pages that don't scroll as a whole, like windy.com's map, get the keys.
	@Override
	public boolean dispatchKeyEvent(KeyEvent event)
	{
		if(showTvPointer() && handleTvPointerKey(event))
			return true;

		int keyCode = event.getKeyCode();
		if(hasBeenDestroyed || !WeeWXApp.isTV() ||
		   (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN))
			return super.dispatchKeyEvent(event);

		int direction = keyCode == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1;
		if(!canScrollVertically(direction))
			return super.dispatchKeyEvent(event);

		if(event.getAction() == KeyEvent.ACTION_DOWN)
			evaluateJavascript("window.scrollBy({top: " + direction +
							   " * Math.round(window.innerHeight * 0.4), behavior: 'smooth'});", null);

		return true;
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
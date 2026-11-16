package com.odiousapps.weewxweather;

import android.graphics.BitmapFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static com.odiousapps.weewxweather.NetworkClient.getInstance;
import static com.odiousapps.weewxweather.NetworkClient.getRequest;
import static com.odiousapps.weewxweather.NetworkClient.getStream;
import static com.odiousapps.weewxweather.WeeWXAppCommon.LogMessage;
import static com.odiousapps.weewxweather.WeeWXAppCommon.doStackOutput;
import static com.odiousapps.weewxweather.WeeWXAppCommon.is_blank;
import static com.odiousapps.weewxweather.WeeWXAppCommon.is_valid_url;

public class ParallelDownloader
{
	private final ExecutorService executor;
	public final long startTime;

	private boolean shuttingdown = false;

	public ParallelDownloader(int threadCount, String className)
	{
		this.executor = Executors.newFixedThreadPool(threadCount, r ->
		{
			Thread thread = new Thread(r);
			thread.setName(className + "-worker-" + Thread.activeCount());
			return thread;
		});

		startTime = System.currentTimeMillis();
	}

	// lastModified is the server's Last-Modified header in ms, or 0 if it wasn't sent
	public record DownloadResult(int id, String url, boolean success, String error,
								 String contentType, long length, String string, byte[] bytes, long lastModified)
	{
		public DownloadResult(int id, String url, boolean success, String error,
							  String contentType, long length, String string, byte[] bytes)
		{
			this(id, url, success, error, contentType, length, string, bytes, 0L);
		}
	}

	public List<DownloadResult> downloadAll(List<Integer> idtypes, List<String> urls, List<String> contentTypes)
	{
		if(idtypes == null || idtypes.isEmpty() || urls == null || urls.isEmpty() ||
				contentTypes == null || contentTypes.isEmpty())
			return null;

		List<Future<DownloadResult>> futures = new ArrayList<>();

		LogMessage("ParallelDownloader.downloadAll() contentTypes: " + contentTypes);

		// Submit all downloads
		for(int i = 0; i < urls.size(); i++)
		{
			int final_i = i;
			String contentType = final_i < contentTypes.size() && !is_blank(contentTypes.get(final_i)) ? contentTypes.get(final_i) : "HTML";
			int id = final_i < idtypes.size() && idtypes.get(final_i) >= 0 ? idtypes.get(final_i) : -1;
			LogMessage("ParallelDownloader.downloadAll(" + id + ") contentType: " + contentType);
			futures.add(executor.submit(() -> getContent(id, urls.get(final_i), contentType)));
		}

		// Collect results
		List<DownloadResult> results = new ArrayList<>();
		for(int i = 0; i < futures.size(); i++)
		{
			int id = i < idtypes.size() && idtypes.get(i) >= 0 ? idtypes.get(i) : -1;
			Future<DownloadResult> future = futures.get(i);
			try
			{
				results.add(future.get(30, TimeUnit.SECONDS));
			} catch(ExecutionException | InterruptedException | TimeoutException | CancellationException e) {
				// Don't leave a slow download running in the background after we've given up on it
				future.cancel(true);
				if(!shuttingdown)
					results.add(new DownloadResult(id,null, false, e.getLocalizedMessage(),
							"ERROR", 0,null, null));
				else
					results.add(new DownloadResult(id,null, true, null, "NULL", 0,null, null));
			}
		}

		return results;
	}

	// Only reads the image header, so no pixel memory is allocated just to validate a download
	private static boolean isValidImage(byte[] bytes)
	{
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
		return options.outWidth > 0 && options.outHeight > 0;
	}

	private DownloadResult getContent(int id, String url, String contentType)
	{
		if(!is_valid_url(url))
			return new DownloadResult(id, null, true, "Skipped", contentType, 0, null, null);

		DownloadResult dr = new DownloadResult(id, url, false, "Invalid URL", "ERROR", 0, null, null);

		HttpUrl url2 = HttpUrl.parse(url);
		if(url2 == null)
			return dr;

		return getContent(id, url2, contentType, 0, null);
	}

	private DownloadResult getContent(int id, HttpUrl url, String contentType, int attempt, String lastError)
	{
		if(attempt >= 3)
			return new DownloadResult(id, url.toString(), false, lastError, "ERROR", 0, null, null);

		if(attempt > 0 || Thread.currentThread().isInterrupted())
		{
			try
			{
				Thread.sleep(5_000L);
			} catch(InterruptedException ie) {
				if(!Thread.currentThread().isInterrupted())
				{
					LogMessage("ParallelDownloader.getContent(" + id + ") interrupted!");
					return new DownloadResult(id, url.toString(), false, "interrupted", "ERROR", 0, null, null);
				}
			}
		}

		if(shuttingdown || Thread.currentThread().isInterrupted())
			return new DownloadResult(id, url.toString(), true, null, "NULL", 0, null, null);

		LogMessage("ParallelDownloader.getContent(" + id + ") id: " + id);
		LogMessage("ParallelDownloader.getContent(" + id + ") contentType: " + contentType);
		LogMessage("ParallelDownloader.getContent(" + id + ") url: " + url);

		if(contentType.equals("MJPEG"))
		{
			OkHttpClient client = getStream(url);

			Request request = getRequest(false, url, true);
			try(Response response = client.newCall(request).execute())
			{
				if(!response.isSuccessful())
				{
					String bodyStr = response.body().string();
					String error = "HTTP error " + response;
					if(!is_blank(bodyStr))
						error += ", body: " + bodyStr;

					LogMessage("ParallelDownloader.getContent(" + id + ") Error! error: " + error, KeyValue.e);

					return getContent(id, url, contentType, attempt + 1, error);
				}

				LogMessage("ParallelDownloader.getContent(" + id + ") Successfully connected to server, now to grab a frame...");

				InputStream urlStream = response.body().byteStream();

				BufferedReader reader = new BufferedReader(new InputStreamReader(urlStream, StandardCharsets.US_ASCII));

				String line;
				int contentLength = -1;

				while((line = reader.readLine()) != null)
				{
					if(line.isEmpty() && contentLength > 0)
						break;

					if(line.startsWith("Content-Length:"))
						contentLength = Integer.parseInt(line.substring(15).strip());
				}

				if(contentLength == 0)
				{
					LogMessage("ParallelDownloader.getContent(" + id + ") Error! contentLength: " + contentLength, KeyValue.e);
					return getContent(id, url, contentType, attempt + 1, "contentLength was 0");
				}

				LogMessage("ParallelDownloader.getContent(" + id + ") contentLength: " + contentLength);

				byte[] imageBytes = new byte[contentLength];
				int offset = 0;
				while(offset < contentLength)
				{
					int read = urlStream.read(imageBytes, offset, contentLength - offset);
					if(read == -1)
					{
						String warning = "Stream ended prematurely";
						LogMessage("ParallelDownloader.getContent(" + id + ") Error! " + warning, KeyValue.e);
						return getContent(id, url, contentType, attempt + 1, warning);
					}

					offset += read;
				}

				if(isValidImage(imageBytes))
				{
					LogMessage("ParallelDownloader.getContent(" + id + ") Got an image... wooo!");
					return new DownloadResult(id, url.toString(), true, null, "IMAGE", imageBytes.length, null, imageBytes);
				}

				LogMessage("ParallelDownloader.getContent(" + id + ") Error! Invalid image, trying for another", KeyValue.v);
				return getContent(id, url, contentType, attempt + 1, "Invalid image");
			} catch (UnknownHostException e) {
				return new DownloadResult(id, url.toString(), false, e.getLocalizedMessage(), "ERROR", 0, null, null);
			} catch(IOException e) {
				LogMessage("ParallelDownloader.getContent(" + id + ") Error! " + e.getMessage(), KeyValue.e);
				doStackOutput(e);
				return getContent(id, url, contentType, attempt + 1, e.getLocalizedMessage());
			}
		} else if(contentType.equals("IMAGE")) {
			OkHttpClient client = getInstance(url);

			Request request = getRequest(false, url, true);
			try(Response response = client.newCall(request).execute())
			{
				if(!response.isSuccessful())
				{
					String string = response.body().string();

					String error = "HTTP error " + response;
					if(!is_blank(string))
						error += ", body: " + string;

					LogMessage("ParallelDownloader.getContent(" + id + ") Error! error: " + error, KeyValue.e);

					return getContent(id, url, contentType, attempt + 1, error);
				} else if(response.body().contentLength() == 0) {
					LogMessage("ParallelDownloader.getContent(" + id + ") Error! Download size was 0 bytes", KeyValue.e);
					return getContent(id, url, contentType, attempt + 1, "contentLength was 0");
				}

				byte[] bytes = response.body().bytes();
				LogMessage("ParallelDownloader.getContent(" + id + ") bytes.length: " + bytes.length);

				if(isValidImage(bytes))
				{
					LogMessage("ParallelDownloader.getContent(" + id + ") Got an image... wooo!");
					Date lastModified = response.headers().getDate("Last-Modified");
					return new DownloadResult(id, url.toString(), true, null, "IMAGE", bytes.length, null, bytes,
							lastModified != null ? lastModified.getTime() : 0L);
				}

				LogMessage("ParallelDownloader.getContent(" + id + ") Error! Invalid image returned", KeyValue.e);
				return getContent(id, url, contentType, attempt + 1, "Invalid image returned");
			} catch (UnknownHostException e) {
				return new DownloadResult(id, url.toString(), false, e.getLocalizedMessage(), "ERROR", 0, null, null);
			} catch(IOException e) {
				LogMessage("ParallelDownloader.getContent(" + id + ") Error! " + e.getMessage(), KeyValue.e);
				doStackOutput(e);
				return getContent(id, url, contentType, attempt + 1, e.getLocalizedMessage());
			}
		} else {
			OkHttpClient client = getInstance(url);

			Request request = getRequest(false, url, true);
			try(Response response = client.newCall(request).execute())
			{
				String string = response.body().string();

				if(!response.isSuccessful())
				{
					String error = "HTTP error " + response;
					if(!is_blank(string))
						error += ", body: " + string;

					LogMessage("ParallelDownloader.getContent(" + id + ") Error! " + error, KeyValue.e);
					return getContent(id, url, contentType, attempt + 1, error);
				} else if(string.length() == 0) {
					LogMessage("ParallelDownloader.getContent(" + id + ") Error! Download size was 0 bytes", KeyValue.e);
					return getContent(id, url, contentType, attempt + 1, "Download size was 0 bytes");
				}

				LogMessage("ParallelDownloader.getContent(" + id + ") Returning content of " + string.length() + " length");
				return new DownloadResult(id, url.toString(), true, null, contentType, string.length(), string, null);
			} catch (UnknownHostException e) {
				return new DownloadResult(id, url.toString(), false, e.getLocalizedMessage(), "ERROR", 0, null, null);
			} catch (IOException e) {
				LogMessage("ParallelDownloader.getContent(" + id + ") Error! " + e.getMessage(), KeyValue.e);
				doStackOutput(e);
				return getContent(id, url, contentType, attempt + 1, e.getLocalizedMessage());
			}
		}
	}

	public boolean isRunning()
	{
		return !executor.isShutdown();
	}

	public void shutdown()
	{
		shuttingdown = true;
		LogMessage("ParallelDownloader.shutdown()");
		executor.shutdownNow();

		try
		{
			if(!executor.awaitTermination(2, TimeUnit.SECONDS))
				LogMessage("ParallelDownloader.shutdown() Executor did not shut down properly", KeyValue.e);
		} catch (InterruptedException e) {
			executor.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}
}
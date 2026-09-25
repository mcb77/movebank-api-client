package de.firetail.compat.movebank.api.client;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

import java.awt.Frame;
import java.io.*;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import javax.net.ssl.HttpsURLConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MovebankApiClient {

	private static final Logger logger = LoggerFactory.getLogger(MovebankApiClient.class);

	private static final String CHARSET = "UTF-8";

	/** Upper bound on how much of an error response body is kept for {@link HttpException}. */
	private static final int MAX_ERROR_BODY_CHARS = 4096;

	private String movebankBaseUrl;
	private String user;
	private String password;
	private LicenseChecker licenseChecker;
	private boolean disableSslChecks;

	private String sessionId;

	private int connectTimeoutMs = 60_000;
	// Generous: Movebank can take minutes before it starts streaming a large event query.
	private int readTimeoutMs = 30 * 60_000;

	private int maxRetries = 5;
	private long initialRetryDelayMs = 30_000;
	private long maxRetryDelayMs = 15 * 60_000;

	public MovebankApiClient(String movebankBaseUrl, String user, String password, LicenseChecker licenseChecker)
			throws Exception {
		if ( movebankBaseUrl.endsWith("/"))
			movebankBaseUrl = movebankBaseUrl.substring(0, movebankBaseUrl.length() - 1);
		this.movebankBaseUrl = movebankBaseUrl;
		this.user = user;
		this.password = password;
		this.licenseChecker = licenseChecker;
		this.disableSslChecks = false;
	}

	public MovebankApiClient(String movebankBaseUrl, String user, String password, final Frame owner) throws Exception {
		this( movebankBaseUrl, user, password, new LicenseChecker() {
			public boolean licenseAccepted(String html) {
				return LicenseDialog.acceptLicense(owner, html);
			}
		});
	}

	private String getDirectReadBaseUrl() {
		return movebankBaseUrl + "/service/direct-read?";
	}

	/*
	 * Disables ssl certificate and hostname verification. Use only in testing environments!
	 */
	public void disableSslChecks() {
		this.disableSslChecks = true;
	}

	/**
	 * Sets connect and read timeouts in milliseconds (0 = infinite). The read timeout
	 * bounds each blocking read, i.e. the longest silence tolerated mid-response.
	 * Defaults: 60 s connect, 30 min read.
	 */
	public void setTimeouts(int connectTimeoutMs, int readTimeoutMs) {
		if (connectTimeoutMs < 0 || readTimeoutMs < 0)
			throw new IllegalArgumentException("timeouts must be >= 0");
		this.connectTimeoutMs = connectTimeoutMs;
		this.readTimeoutMs = readTimeoutMs;
	}

	/**
	 * Configures retries for HTTP 429 (rate limited) and 503 (unavailable) responses.
	 * The wait honours a {@code Retry-After} header (in seconds) when present, otherwise
	 * doubles from {@code initialDelayMs}; either way it is capped at {@code maxDelayMs}.
	 * {@code maxRetries = 0} disables retrying. Defaults: 5 retries, 30 s initial, 15 min cap.
	 */
	public void setRetryPolicy(int maxRetries, long initialDelayMs, long maxDelayMs) {
		if (maxRetries < 0 || initialDelayMs < 0 || maxDelayMs < initialDelayMs)
			throw new IllegalArgumentException("invalid retry policy");
		this.maxRetries = maxRetries;
		this.initialRetryDelayMs = initialDelayMs;
		this.maxRetryDelayMs = maxDelayMs;
	}

	private InputStream sendRequest(Map<String, String> requestParameters) throws Exception {
		return sendRequest(requestParameters, null);
	}

	public static class HttpException extends RuntimeException {
		private int responseCode;
		private String responsMessage;
		private String responseBody;

		public HttpException( int responseCode, String responsMessage ) {
			this(responseCode, responsMessage, null);
		}

		public HttpException( int responseCode, String responsMessage, String responseBody ) {
			super(responseCode + ": " + describe(responsMessage, responseBody));
			this.responseCode = responseCode;
			this.responsMessage = responsMessage;
			this.responseBody = responseBody;
		}

		private static String describe(String message, String body) {
			boolean hasMessage = message != null && !message.isBlank();
			boolean hasBody = body != null && !body.isBlank();
			if (hasMessage && hasBody) return message + " - " + body.strip();
			if (hasBody) return body.strip();
			return message;
		}

		public int getResponseCode() {
			return this.responseCode;
		}

		public String getResponseMessage() {
			return this.responsMessage;
		}

		/** @deprecated misspelled; use {@link #getResponseMessage()}. */
		@Deprecated
		public String getReponseMessage() {
			return this.responsMessage;
		}

		/** The error response body (truncated), or null if the server sent none. */
		public String getResponseBody() {
			return this.responseBody;
		}
	}

	private InputStream sendRequest(Map<String, String> requestParameters, String licenseMd5) throws Exception {

		if (licenseMd5 != null)
			requestParameters.put("license-md5", licenseMd5);
		String url = getDirectReadBaseUrl() + getQueryString(requestParameters);
		logger.debug("GET {}", url); // credentials travel in headers, never in the URL

		HttpURLConnection conn = openWithRetries(url);
		if (licenseMd5 == null && "true".equals(conn.getHeaderField("accept-license"))) {
			byte[] termsBytes;
			try (InputStream in = responseBody(conn)) {
				termsBytes = in.readAllBytes();
			}
			String termsHtml = "<html>" + new String(termsBytes, "UTF-8") + "</html>";
			boolean accepted = licenseChecker.licenseAccepted(termsHtml);
			if (accepted) {
				String md5 = new BigInteger(1, MessageDigest.getInstance("MD5").digest(termsBytes)).toString(16);
				return sendRequest(requestParameters, md5);
			} else
				throw new LicenseException();
		}
		return responseBody(conn);
	}

	/**
	 * Opens the connection and returns it once the server answered 200, retrying
	 * 429 and 503 responses per the retry policy. Any other status, or a 429/503
	 * after the last retry, is thrown as {@link HttpException}.
	 */
	private HttpURLConnection openWithRetries(String url) throws Exception {
		long backoffMs = initialRetryDelayMs;
		for (int attempt = 0; ; attempt++) {
			HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
			if (conn instanceof HttpsURLConnection && disableSslChecks)
				HttpsUtil.disableChecks((HttpsURLConnection) conn);
			conn.setConnectTimeout(connectTimeoutMs);
			conn.setReadTimeout(readTimeoutMs);
			conn.setRequestProperty("Accept-Encoding", "gzip, deflate");
			conn.setRequestProperty("user", user);
			conn.setRequestProperty("password", password);
			if (sessionId != null)
				conn.setRequestProperty("Cookie", sessionId);
			conn.connect();
			String cookieVal = conn.getHeaderField("Set-Cookie");
			if (cookieVal != null) {
				int semicolon = cookieVal.indexOf(';');
				sessionId = semicolon < 0 ? cookieVal : cookieVal.substring(0, semicolon);
			}
			int httpStatus = conn.getResponseCode();
			if (httpStatus == 200)
				return conn;

			String body = readErrorBody(conn);
			boolean retryable = httpStatus == 429 || httpStatus == 503;
			if (!retryable || attempt >= maxRetries)
				throw new HttpException(httpStatus, conn.getResponseMessage(), body);

			long waitMs = retryAfterMs(conn.getHeaderField("Retry-After"), backoffMs);
			logger.warn("HTTP {} from Movebank (attempt {}/{}), retrying in {}s",
					httpStatus, attempt + 1, maxRetries + 1, waitMs / 1000);
			Thread.sleep(waitMs);
			backoffMs = Math.min(backoffMs * 2, maxRetryDelayMs);
		}
	}

	/** Wait for the next retry: the server's Retry-After (delta-seconds) if given, else the backoff; capped. */
	private long retryAfterMs(String retryAfter, long backoffMs) {
		long waitMs = backoffMs;
		if (retryAfter != null) {
			try {
				waitMs = Long.parseLong(retryAfter.trim()) * 1000;
			} catch (NumberFormatException e) {
				// HTTP-date form is not worth parsing here; fall back to the backoff
			}
		}
		return Math.max(0, Math.min(waitMs, maxRetryDelayMs));
	}

	/** Reads (and closes) the error response body, truncated; null if there is none. */
	private static String readErrorBody(HttpURLConnection conn) {
		InputStream err = conn.getErrorStream();
		if (err == null)
			return null;
		try (Reader r = new InputStreamReader(decode(conn, err), CHARSET)) {
			char[] buf = new char[MAX_ERROR_BODY_CHARS];
			int n = 0, read;
			while (n < buf.length && (read = r.read(buf, n, buf.length - n)) != -1)
				n += read;
			return n == 0 ? null : new String(buf, 0, n);
		} catch (IOException e) {
			return null;
		}
	}

	/** The response body, length-checked and decoded. */
	private static InputStream responseBody(HttpURLConnection conn) throws IOException {
		InputStream in = conn.getInputStream();
		long contentLength = conn.getContentLengthLong();
		if (contentLength >= 0)
			in = new LengthCheckingInputStream(in, contentLength);
		return decode(conn, in);
	}

	/**
	 * Throws on EOF before {@code Content-Length} bytes arrived. HttpURLConnection reports
	 * a truncated fixed-length body as a normal end of stream (it only detects truncation
	 * for chunked responses), which would make a dropped connection look like complete data.
	 */
	static final class LengthCheckingInputStream extends FilterInputStream {
		private final long expected;
		private long received;

		LengthCheckingInputStream(InputStream in, long expected) {
			super(in);
			this.expected = expected;
		}

		@Override
		public int read() throws IOException {
			int b = super.read();
			if (b < 0) checkComplete(); else received++;
			return b;
		}

		@Override
		public int read(byte[] buf, int off, int len) throws IOException {
			int n = super.read(buf, off, len);
			if (n < 0) checkComplete(); else received += n;
			return n;
		}

		private void checkComplete() throws EOFException {
			if (received < expected)
				throw new EOFException("Premature end of response: received " + received
						+ " of " + expected + " bytes");
		}
	}

	/** Undoes the response's Content-Encoding. We advertise gzip/deflate, so we must handle them. */
	private static InputStream decode(HttpURLConnection conn, InputStream in) throws IOException {
		String encoding = conn.getContentEncoding();
		if (encoding == null)
			return in;
		switch (encoding.trim().toLowerCase(Locale.ROOT)) {
			case "gzip":
			case "x-gzip":
				return new GZIPInputStream(in);
			case "deflate":
				return new InflaterInputStream(in);
			default:
				return in;
		}
	}

	public static void parseResponse(InputStream in, RecordCallback callback) throws Exception {

//		BufferedReader reader = new BufferedReader( new InputStreamReader( in) );
//		int countLines = 0;
//		while (true) {
//			String line = reader.readLine();
//			if ( line == null ) {
//				break;
//			}
//			countLines++;
//		}
//		System.out.println( "lines: " + countLines );

		try ( CSVReader reader = new CSVReaderBuilder( new InputStreamReader( in, CHARSET ) )
				.withCSVParser( new CSVParserBuilder()
						.withSeparator(',')
						.withQuoteChar( '"' )
						.withEscapeChar('\\')
						.withIgnoreQuotations(false)
						.build() )
				.build() ) {

			int countLines = 0;
			String[] nextLine;

			// Read each line from the CSV
			while ((nextLine = reader.readNext()) != null) {
				// Convert the array to a List and add to the main list
				List<String> line = Arrays.asList(nextLine);

				if ( countLines == 0 ) {
					// Header
					callback.start( line );
				}
				else {
					callback.record( line, 0, 0 );
				}
				countLines++;
			}

			callback.end();
		}
		// No catch: an IOException mid-stream (connection reset, read timeout) must reach
		// the caller. Swallowing it made a truncated response indistinguishable from a
		// complete one.

//		CsvReader reader = new CsvReader(new InputStreamReader(in, CHARSET), ',');
//		callback.start(reader.readLine());
//		List<String> line;
//		while ((line = reader.readLine()) != null) {
//			callback.record( line, reader.getLineStart(), reader.getLineEnd() );
//			countLines++;
//		}
//		callback.end();
//		System.out.println( "lines: " + countLines );
	}

	private String getQueryString(Map<String, String> requestParameters) throws Exception {
		StringBuffer queryString = null;
		for (Map.Entry<String, String> entry : requestParameters.entrySet()) {
			if (queryString == null)
				queryString = new StringBuffer();
			else
				queryString.append("&");
			queryString.append(URLEncoder.encode(entry.getKey(), CHARSET) + "="
					+ URLEncoder.encode(entry.getValue(), CHARSET));
		}
		return queryString.toString();
	}

	public void sendRequest(RequestBuilder request, RecordCallback callback) throws Exception {
		parseResponse(sendRequest(request.getRequestParameters()), callback);
	}

	// mcb test
	InputStream sendRequestAndGetResponseAsInputStream(RequestBuilder request) throws Exception {
		return sendRequest(request.getRequestParameters());
	}

	public List<Record> readAll(RequestBuilder request) throws Exception {
		RecordCollector collector = new RecordCollector();
		sendRequest(request, collector);
		return collector.getRecords();
	}

	public void sendRequest(RequestBuilder request, OutputStream out) throws Exception {
		try (InputStream in = sendRequest(request.getRequestParameters())) {
			in.transferTo(out);
		}
		out.flush();
	}

	public void sendRequest(RequestBuilder request, OutputStream out, IDownloadProgressListener progressListener ) throws Exception {
		try (InputStream in = sendRequest(request.getRequestParameters())) {
			byte[] buf = new byte[10000];
			int len;
			while ((len = in.read(buf)) != -1) {
				if ( progressListener != null ) {
					progressListener.onProgress(len);
				}
				out.write(buf, 0, len);
			}
		}
		out.flush();
	}

	public static List<Record> readAll(InputStream in) throws Exception {
		RecordCollector collector = new RecordCollector();
		parseResponse(in, collector);
		return collector.getRecords();
	}
}

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
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

public class MovebankApiClient {

	private static final String CHARSET = "UTF-8";

	private String movebankBaseUrl;
	private String user;
	private String password;
	private LicenseChecker licenseChecker;
	private boolean disableSslChecks;

	private String sessionId;

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

	private InputStream sendRequest(Map<String, String> requestParameters) throws Exception {
		return sendRequest(requestParameters, null);
	}

	public static class HttpException extends RuntimeException {
		private int responseCode;
		private String responsMessage;
		public HttpException( int responseCode, String responsMessage ) {
			super(responseCode + ": " + responsMessage);
			this.responseCode = responseCode;
			this.responsMessage = responsMessage;
		}

		public int getResponseCode() {
			return this.responseCode;
		}

		public String getReponseMessage() {
			return this.responsMessage;
		}
	}

	private InputStream sendRequest(Map<String, String> requestParameters, String licenseMd5) throws Exception {

		System.out.println("URL: "+getDirectReadBaseUrl() + getQueryString(requestParameters));

		if (licenseMd5 != null)
			requestParameters.put("license-md5", licenseMd5);
		HttpURLConnection conn = (HttpURLConnection) new URL(getDirectReadBaseUrl()
				+ getQueryString(requestParameters)).openConnection();
		if (conn instanceof HttpsURLConnection && disableSslChecks)
			HttpsUtil.disableChecks((HttpsURLConnection) conn);
		conn.setRequestProperty("Accept-Encoding", "gzip, deflate");
		conn.setRequestProperty("user", user);
		conn.setRequestProperty("password", password);
		if (sessionId != null)
			conn.setRequestProperty("Cookie", sessionId);
		conn.connect();
		String cookieVal = conn.getHeaderField("Set-Cookie");
		if (cookieVal != null) {
			sessionId = cookieVal.substring(0, cookieVal.indexOf(";"));
		}
		int httpStatus = conn.getResponseCode();
		if (httpStatus != 200)
			throw new HttpException(httpStatus, conn.getResponseMessage());
		if (licenseMd5 == null && "true".equals(conn.getHeaderField("accept-license"))) {
			InputStream in = conn.getInputStream();
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[1024];
			int len;
			while ((len = in.read(buf)) != -1)
				out.write(buf, 0, len);
			byte[] termsBytes = out.toByteArray();
			String termsHtml = "<html>" + new String(termsBytes, "UTF-8") + "</html>";
			boolean accepted = licenseChecker.licenseAccepted(termsHtml);
			if (accepted) {
				String md5 = new BigInteger(1, MessageDigest.getInstance("MD5").digest(termsBytes)).toString(16);
				return sendRequest(requestParameters, md5);
			} else
				throw new LicenseException();
		}
		return conn.getInputStream();
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
//			System.out.println( "lines: " + countLines );
		} catch (IOException e) {
			e.printStackTrace();
		}

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
		InputStream in = sendRequest(request.getRequestParameters());
		byte[] buf = new byte[10000];
		int len;
		while ((len = in.read(buf)) != -1)
			out.write(buf, 0, len);
		out.flush();
	}

	public void sendRequest(RequestBuilder request, OutputStream out, IDownloadProgressListener progressListener ) throws Exception {
		InputStream in = sendRequest(request.getRequestParameters());
		byte[] buf = new byte[10000];
		int len;
		while ((len = in.read(buf)) != -1) {
			if ( progressListener != null ) {
				progressListener.onProgress(len);
			}
			out.write(buf, 0, len);
		}
		out.flush();
	}

	public static List<Record> readAll(InputStream in) throws Exception {
		RecordCollector collector = new RecordCollector();
		parseResponse(in, collector);
		return collector.getRecords();
	}
}

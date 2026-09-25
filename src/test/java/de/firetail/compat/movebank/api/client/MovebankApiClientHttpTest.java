package de.firetail.compat.movebank.api.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises MovebankApiClient's HTTP handling against a local, scripted server. */
class MovebankApiClientHttpTest {

	private static final String CSV = "id,name\n1,alpha\n2,beta\n";

	/** A scripted server response. */
	interface Handler {
		void handle(HttpExchange exchange) throws IOException;
	}

	private HttpServer server;
	private final Deque<Handler> script = new ArrayDeque<>();
	private final AtomicInteger requests = new AtomicInteger();
	private MovebankApiClient client;

	@BeforeEach
	void start() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			requests.incrementAndGet();
			try {
				Handler h = script.pollFirst();
				if (h == null) {
					send(exchange, 500, "unscripted request");
				} else {
					h.handle(exchange);
				}
			} finally {
				exchange.close();
			}
		});
		server.start();
		client = new MovebankApiClient("http://127.0.0.1:" + server.getAddress().getPort() + "/movebank",
				"u", "p", html -> true);
		client.setRetryPolicy(3, 10, 50); // keep tests fast
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	private static void send(HttpExchange ex, int status, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		ex.sendResponseHeaders(status, bytes.length);
		try (OutputStream os = ex.getResponseBody()) {
			os.write(bytes);
		}
	}

	private List<Record> readStudies() throws Exception {
		return client.readAll(new RequestBuilderStudy());
	}

	// ── tests ──────────────────────────────────────────────────────────────

	@Test
	void readsCompleteResponse() throws Exception {
		script.add(ex -> send(ex, 200, CSV));
		List<Record> records = readStudies();
		assertEquals(2, records.size());
		assertEquals("beta", records.get(1).getStringValue("name"));
	}

	@Test
	void truncatedResponseThrowsInsteadOfReturningPartialData() {
		script.add(ex -> {
			byte[] partial = "id,name\n1,alpha\n2,be".getBytes(StandardCharsets.UTF_8);
			ex.sendResponseHeaders(200, partial.length + 1000); // promise more than we send
			ex.getResponseBody().write(partial);
			ex.getResponseBody().flush();
			// handler returns → connection closed early
		});
		assertThrows(IOException.class, this::readStudies);
	}

	@Test
	void truncatedChunkedResponseThrows() throws Exception {
		// Movebank streams with Transfer-Encoding: chunked. HttpServer always terminates
		// chunked bodies cleanly, so drop the connection by hand from a raw socket.
		try (ServerSocket raw = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			Thread serverThread = new Thread(() -> {
				try (Socket s = raw.accept()) {
					BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
					String line;
					while ((line = in.readLine()) != null && !line.isEmpty()) { /* drain request headers */ }
					String chunk = "id,name\n1,alpha\n2,be";
					OutputStream out = s.getOutputStream();
					out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/csv\r\nTransfer-Encoding: chunked\r\n\r\n"
							+ Integer.toHexString(chunk.length()) + "\r\n" + chunk + "\r\n")
							.getBytes(StandardCharsets.US_ASCII));
					out.flush();
					// close without the terminating 0-length chunk
				} catch (IOException ignored) {
				}
			});
			serverThread.start();
			MovebankApiClient rawClient = new MovebankApiClient(
					"http://127.0.0.1:" + raw.getLocalPort() + "/movebank", "u", "p", html -> true);
			assertThrows(IOException.class, () -> rawClient.readAll(new RequestBuilderStudy()));
			serverThread.join(5_000);
		}
	}

	@Test
	void stalledResponseTimesOut() {
		client.setTimeouts(1_000, 200);
		script.add(ex -> {
			try {
				Thread.sleep(2_000);
			} catch (InterruptedException ignored) {
			}
		});
		assertThrows(SocketTimeoutException.class, this::readStudies);
	}

	@Test
	void errorBodyIsIncludedInHttpException() {
		script.add(ex -> send(ex, 403, "No permission to access study 42"));
		MovebankApiClient.HttpException e =
				assertThrows(MovebankApiClient.HttpException.class, this::readStudies);
		assertEquals(403, e.getResponseCode());
		assertEquals("No permission to access study 42", e.getResponseBody());
		assertTrue(e.getMessage().contains("No permission to access study 42"), e.getMessage());
	}

	@Test
	void rateLimitedRequestIsRetried() throws Exception {
		script.add(ex -> {
			ex.getResponseHeaders().add("Retry-After", "0");
			send(ex, 429, "You have triggered a rate limiting mechanism.");
		});
		script.add(ex -> send(ex, 503, "busy"));
		script.add(ex -> send(ex, 200, CSV));
		assertEquals(2, readStudies().size());
		assertEquals(3, requests.get());
	}

	@Test
	void rateLimitGivesUpAfterMaxRetries() {
		for (int i = 0; i < 4; i++) script.add(ex -> send(ex, 429, "slow down"));
		MovebankApiClient.HttpException e =
				assertThrows(MovebankApiClient.HttpException.class, this::readStudies);
		assertEquals(429, e.getResponseCode());
		assertEquals(4, requests.get(), "1 attempt + 3 retries");
	}

	@Test
	void otherErrorsAreNotRetried() {
		script.add(ex -> send(ex, 500, "boom"));
		assertThrows(MovebankApiClient.HttpException.class, this::readStudies);
		assertEquals(1, requests.get());
	}

	@Test
	void gzipResponseIsDecoded() throws Exception {
		script.add(ex -> {
			ByteArrayOutputStream buf = new ByteArrayOutputStream();
			try (GZIPOutputStream gz = new GZIPOutputStream(buf)) {
				gz.write(CSV.getBytes(StandardCharsets.UTF_8));
			}
			ex.getResponseHeaders().add("Content-Encoding", "gzip");
			ex.sendResponseHeaders(200, buf.size());
			ex.getResponseBody().write(buf.toByteArray());
		});
		assertEquals(2, readStudies().size());
	}

	@Test
	void writesNothingToStdout() throws Exception {
		script.add(ex -> send(ex, 200, CSV));
		PrintStream original = System.out;
		ByteArrayOutputStream captured = new ByteArrayOutputStream();
		System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
		try {
			readStudies();
		} finally {
			System.setOut(original);
		}
		assertFalse(captured.toString(StandardCharsets.UTF_8).contains("URL:"),
				"library output would corrupt callers' stdout");
	}
}

# movebank-api-client
![proto-mullet.jpg](proto-mullet.jpg)


A Java client library for the [Movebank REST API (v1)](https://github.com/movebank/movebank-api-doc/blob/master/movebank-api.md).

---

## Installation

**Gradle:**
```groovy
implementation 'de.firetail:movebank-api-client:0.0.2'
```

**Maven:**
```xml
<dependency>
    <groupId>de.firetail</groupId>
    <artifactId>movebank-api-client</artifactId>
    <version>0.0.2</version>
</dependency>
```

---

## Quick Start

```java
MovebankApiClient client = new MovebankApiClient(
    "https://www.movebank.org/movebank",
    "your-username",
    "your-password",
    (LicenseChecker) html -> true   // auto-accept licenses; see License Handling below
);

List<Record> studies = client.readAll(new RequestBuilderStudy());

for (Record study : studies) {
    System.out.println(study.getStringValue(Constants.Attributes.ID)
        + " " + study.getStringValue(Constants.Attributes.NAME));
}
```

---

## Authentication

Pass your Movebank username and password to the `MovebankApiClient` constructor. Credentials are sent as HTTP headers on every request. The client maintains a session cookie automatically after the first successful response.

---

## Core Concepts

### RequestBuilder

Each Movebank entity type has its own `RequestBuilder` subclass. Build a request, optionally configure filters, then pass it to the client.

| Class | Entity |
|---|---|
| `RequestBuilderStudy` | Studies |
| `RequestBuilderSensor` | Sensors (requires study ID) |
| `RequestBuilderTag` | Tags (requires study ID) |
| `RequestBuilderIndividual` | Individuals (requires study ID) |
| `RequestBuilderDeployment` | Deployments (requires study ID) |
| `RequestBuilderEvent` | Events / tracking data (requires study ID + sensor type) |
| `RequestBuilderStudyAttribute` | Study attributes (requires study ID + sensor type) |
| `RequestBuilderSensorType` | Sensor type lookup table |

All builders support optional attribute selection, sorting, and result limits:

```java
RequestBuilderStudy request = new RequestBuilderStudy();
request.setSelectAttributes(List.of(
    Constants.Attributes.ID,
    Constants.Attributes.NAME
));
request.setLimit(100);
```

### Record

Query responses are rows of CSV data. Each `Record` gives typed access to fields by attribute name:

```java
record.getStringValue("name");
record.getLongValue("id");
record.getDoubleValue("location_lat");
record.getDateValue("timestamp");   // parsed as UTC
record.getBooleanValue("i_am_owner");
```

### Reading Data

**Collect all records into a list:**
```java
List<Record> records = client.readAll(request);
```

**Stream records via callback** (better for large result sets):
```java
client.sendRequest(request, new RecordCallbackDefault() {
    protected void record() throws Exception {
        System.out.println(getStringValue(Constants.Attributes.TIMESTAMP));
    }
});
```

**Download raw response to a stream** (e.g. to save a file):
```java
try (OutputStream out = new FileOutputStream("events.csv")) {
    client.sendRequest(request, out);
}
```
A download progress listener can be supplied as an optional third argument.

---

## Usage Examples

### List accessible studies

```java
List<Record> studies = client.readAll(new RequestBuilderStudy());
```

### Filter by study name

```java
RequestBuilderStudy request = new RequestBuilderStudy();
request.setName("My Study");
List<Record> studies = client.readAll(request);
```

### Read GPS events for a study using StudyBrowser

`StudyBrowser` is a convenience wrapper that loads a study's metadata and builds correctly configured event requests for you:

```java
StudyBrowser browser = new StudyBrowser("12345678", client);

// Sensor type IDs are numeric strings in Movebank.
// Use StaticDataBrowser to resolve the readable name to an ID.
StaticDataBrowser staticData = new StaticDataBrowser(client);
String gpsTypeId = staticData.getSensorTypeId(Constants.SensorTypes.GPS);

RequestBuilderEvent eventRequest = browser.getRequestBuilderEvent(
    gpsTypeId,
    Constants.Attributes.TIMESTAMP,
    Constants.Attributes.LOCATION_LAT,
    Constants.Attributes.LOCATION_LONG
);

List<Record> events = client.readAll(eventRequest);
```

### Filter events by individual or tag

```java
eventRequest.setIndividualId("987654");
// or
eventRequest.setTagId("111222");
```

### Read tags and deployments

```java
List<Record> tags        = client.readAll(new RequestBuilderTag(studyId));
List<Record> individuals = client.readAll(new RequestBuilderIndividual(studyId));
List<Record> deployments = client.readAll(new RequestBuilderDeployment(studyId));
```

---

## License Handling

Some Movebank studies require you to accept a license agreement before data is returned. The API signals this with an `accept-license` response header, and sends the license text as the response body.

The `MovebankApiClient` constructor accepts a `LicenseChecker` that decides whether to accept:

```java
// Programmatic acceptance (e.g. in batch jobs after prior review)
LicenseChecker checker = html -> true;

// Swing dialog (prompts the user interactively)
MovebankApiClient client = new MovebankApiClient(baseUrl, user, password, ownerFrame);
```

If the license is declined, a `LicenseException` is thrown.

---

## HTTP and SSL

Both `http://` and `https://` base URLs are supported. The live Movebank API requires HTTPS; plain HTTP is useful when pointing the client at a local mirror such as [movebank-mirror-api](https://github.com/mcb77/movebank-mirror-api).

```java
// Live Movebank (HTTPS)
MovebankApiClient client = new MovebankApiClient(
    "https://www.movebank.org/movebank", user, password, licenseChecker);

// Local mirror (plain HTTP)
MovebankApiClient client = new MovebankApiClient(
    "http://localhost:8080/movebank", user, password, licenseChecker);
```

For test environments with self-signed certificates, SSL verification can be disabled:

```java
client.disableSslChecks();  // never use in production
```

---

## Building from Source

Requires Java 21 and Gradle (or use the included wrapper).

```bash
./gradlew build
```

---

## License

GNU Lesser General Public License v2.1 — see [LICENSE](LICENSE).

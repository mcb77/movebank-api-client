package de.firetail.compat.movebank.api.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class StudyBrowser {

	private String studyId;
	private Record study;
	private List<Record> sensors;
	private Map<String, List<Record>> attributesBySensorType = new TreeMap<String, List<Record>>();

	public StudyBrowser(String studyId, MovebankApiClient client) throws Exception {
		this.studyId = studyId;
		init(client);
	}

	protected void init( MovebankApiClient client) throws Exception {
		readStudy(client);
		readSensors(client);
		readStudyAttributes(client);
	}

	private void readStudy( MovebankApiClient client) throws Exception {
		RequestBuilderStudy request = new RequestBuilderStudy();
		request.setId(studyId);
		RecordCollector collector = new RecordCollector();
		client.sendRequest(request, collector);
		if (collector.getRecords().size() == 0)
			throw new RuntimeException("No study found with id " + studyId);
		study = collector.toSingleRecord();
	}

	private void readSensors( MovebankApiClient client) throws Exception {
		sensors = client.readAll(new RequestBuilderSensor(studyId));
	}

	private void readStudyAttributes( MovebankApiClient client) throws Exception {
		Set<String> sensorTypeIds = new TreeSet<String>();
		for ( Record sensor : getSensors())
			sensorTypeIds.add(sensor.getValue(Constants.Attributes.SENSOR_TYPE_ID));
		for (String sensorTypeId : sensorTypeIds) {
			List<Record> attributes = client.readAll(new RequestBuilderStudyAttribute(studyId, sensorTypeId));
			attributesBySensorType.put(sensorTypeId, attributes);
		}
	}

	public Record getStudy() {
		return study;
	}

	public List<Record> getSensors() {
		return sensors;
	}

	public Collection<String> getSensorTypeIds() {
		return attributesBySensorType.keySet();
	}

	public List<Record> getStudyAttributes( String sensorTypeId) {
		return attributesBySensorType.get(sensorTypeId);
	}

	public List<String> getStudyAttributeNames(String sensorTypeId) {
		List<String> retval = new ArrayList<String>();
		List<Record> attributes = attributesBySensorType.get(sensorTypeId);
		if (attributes == null)
			throw new RuntimeException("Unknown sensor type.");
		else
			for ( Record attribute : attributes)
				retval.add(attribute.getStringValue(Constants.Attributes.SHORT_NAME));
		return retval;
	}

	public RequestBuilderEvent getRequestBuilderEvent(String sensorTypeId, String... eventSetAttributes) {
		List<String> studyAttributes = getStudyAttributeNames(sensorTypeId);
		studyAttributes.addAll(Arrays.asList(eventSetAttributes));
		return new RequestBuilderEvent(studyId, sensorTypeId, studyAttributes);
	}

	// mcb
	public RequestBuilderEvent getRequestBuilderEvent(String sensorTypeId, List<String> eventSetAttributes) {
		List<String> studyAttributes = getStudyAttributeNames(sensorTypeId);
		studyAttributes.addAll(eventSetAttributes);
		return new RequestBuilderEvent(studyId, sensorTypeId, studyAttributes);
	}
}

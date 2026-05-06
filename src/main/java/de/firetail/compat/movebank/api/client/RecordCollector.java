package de.firetail.compat.movebank.api.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RecordCollector implements RecordCallback {

	protected List<String> attributes;
	protected Map<String, Integer> attributePositions;
	protected List<Record> records = new ArrayList<Record>();
	protected DataTypeConverter dataTypeConverter;

	public RecordCollector(DataTypeConverter dataTypeConverter) {
		this.dataTypeConverter = dataTypeConverter;
	}

	public RecordCollector() {
		this(new DataTypeConverter());
	}

	public List<String> getAttributes() {
		return attributes;
	}

	public List<Record> getRecords() {
		return records;
	}

	public void start(List<String> attributes) throws Exception {
		this.attributes = attributes;
		this.attributePositions = AbstractRecord.getAttributePositions(attributes);
	}

	public void record(List<String> record, long lineStart, long lineEnd) throws Exception {
		records.add(new Record(attributes, attributePositions, record, dataTypeConverter));
	}

	public void end() throws Exception {
	}

	public int size() {
		return records.size();
	}

	public Record getRecord( int i) {
		return records.get(i);
	}

	public Record toSingleRecord() {
		if (records.size() != 1)
			throw new RuntimeException("Number of records must be 1");
		else
			return getRecord(0);
	}

}

package de.firetail.compat.movebank.api.client;

import java.util.List;
import java.util.Map;

public class Record extends AbstractRecord {

	private List<String> values;

	public Record(List<String> attributes, List<String> values) {
		super(new DataTypeConverter());
		setAttributes(attributes);
		this.values = values;
	}

	public Record(List<String> attributes, Map<String, Integer> attributePositions, List<String> values,
			DataTypeConverter dataTypeConverter) {
		super(attributes, attributePositions, dataTypeConverter);
		this.values = values;
	}

	@Override
	public List<String> getValues() {
		return values;
	}

}

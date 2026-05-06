package de.firetail.compat.movebank.api.client;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractRecord {

	protected List<String> attributes;
	protected Map<String, Integer> attributePositions;
	protected DataTypeConverter dataTypeConverter;

	public AbstractRecord(DataTypeConverter dataTypeConverter) {
		this.dataTypeConverter = dataTypeConverter;
	}

	public AbstractRecord(List<String> attributes, Map<String, Integer> attributePositions,
			DataTypeConverter dataTypeConverter) {
		this.attributes = attributes;
		this.attributePositions = attributePositions;
		this.dataTypeConverter = dataTypeConverter;
	}

	public final void setAttributes(List<String> attributes) {
		this.attributes = attributes;
		attributePositions = getAttributePositions(attributes);
	}

	public static Map<String, Integer> getAttributePositions(List<String> attributes) {
		Map<String, Integer> attributePositions = new HashMap<String, Integer>();
		for (int i = 0; i < attributes.size(); i++)
			attributePositions.put(attributes.get(i), i);
		return attributePositions;
	}

	public List<String> getAttributes() {
		return attributes;
	}

	protected abstract List<String> getValues();

	public Integer getAttributePosition(String attribute) {
		return attributePositions.get(attribute);
	}

	public String getValue(String attribute) {
		Integer pos = getAttributePosition(attribute);
		if (pos != null)
			return getValues().get(pos);
		else
			return null;
	}

	public String getStringValue(String attribute) {
		return getValue(attribute);
	}

	public Date getDateValue(String attribute) {
		return dataTypeConverter.parseDate(getValue(attribute));
	}

	public Long getLongValue(String attribute) {
		return dataTypeConverter.parseLong(getValue(attribute));
	}

	public Boolean getBooleanValue(String attribute) {
		return dataTypeConverter.parseBoolean(getValue(attribute));
	}

	public Double getDoubleValue(String attribute) {
		return dataTypeConverter.parseDouble(getValue(attribute));
	}

}

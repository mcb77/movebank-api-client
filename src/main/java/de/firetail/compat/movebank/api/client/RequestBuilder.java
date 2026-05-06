package de.firetail.compat.movebank.api.client;

import java.util.*;

public abstract class RequestBuilder {

	private List<String> selectAttributes;

	protected Map<String, String> parameters = new HashMap<String, String>();

	public final Map<String, String> getRequestParameters() {
		return parameters;
	}

	public RequestBuilder(String type) {
		parameters.put(Constants.TYPE, type);
	}

	public RequestBuilder(String type, String studyId) {
		this(type);
		parameters.put(Constants.Attributes.STUDY_ID, studyId);
	}

	public void setId(String id) {
		parameters.put(Constants.Attributes.ID, id);
	}

	public void addSelectAttribute(String attribute) {
		if (selectAttributes == null) {
			selectAttributes = new ArrayList<String>();
		}
		selectAttributes.add(attribute);
		setList(Constants.ATTRIBUTES, selectAttributes);
	}

	public void setSelectAttributes(List<String> selectAttributes) {
		this.selectAttributes = selectAttributes;
		setList(Constants.ATTRIBUTES, selectAttributes);
	}


	public List<String> getSelectAttributes() {
		return selectAttributes;
	}
	
	public void setSortAttributes(List<String> sortAttributes) {
		setList(Constants.SORT, sortAttributes);
	}

	public void setSortAttributes(String... attributes) {
		setSortAttributes(Arrays.asList(attributes));
	}

	public void setLimit(int limit) {
		parameters.put(Constants.LIMIT, limit + "");
	}
	
	protected void setList(String key, List<String> values) {
		StringBuffer buf = new StringBuffer();
		for (int i = 0; i < values.size(); i++) {
			if (i > 0)
				buf.append(",");
			buf.append(values.get(i));
		}
		parameters.put(key, buf.toString());
	}
}

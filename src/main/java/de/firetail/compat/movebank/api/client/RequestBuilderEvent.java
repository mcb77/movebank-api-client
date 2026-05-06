package de.firetail.compat.movebank.api.client;

import java.util.List;

public class RequestBuilderEvent extends RequestBuilder {

	public RequestBuilderEvent(String studyId, String sensorTypeId, List<String> attributes) {
		super(Constants.Types.EVENT, studyId);
		parameters.put(Constants.Attributes.SENSOR_TYPE_ID, sensorTypeId);
		setSelectAttributes(attributes);
	}

	public void setTagId(String tagId) {
		parameters.put(Constants.Attributes.TAG_ID, tagId);
	}

	public void setIndividualId(String individualId) {
		parameters.put(Constants.Attributes.INDIVIDUAL_ID, individualId);
	}

	public void setDeploymentId(String deploymentId) {
		parameters.put("deployment_id", deploymentId);
	}
}

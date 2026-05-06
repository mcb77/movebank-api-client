package de.firetail.compat.movebank.api.client;

public class RequestBuilderDeployment extends RequestBuilder {

	public RequestBuilderDeployment(String studyId) {
		super(Constants.Types.DEPLOYMENT, studyId);
	}

	public void setTagId(String tagId) {
		parameters.put(Constants.Attributes.TAG_ID, tagId);
	}

	public void setIndividualId(String individualId) {
		parameters.put(Constants.Attributes.INDIVIDUAL_ID, individualId);
	}

}

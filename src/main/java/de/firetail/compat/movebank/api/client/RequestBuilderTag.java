package de.firetail.compat.movebank.api.client;

public class RequestBuilderTag extends RequestBuilder {

	public RequestBuilderTag(String studyId) {
		super(Constants.Types.TAG, studyId);
	}

}

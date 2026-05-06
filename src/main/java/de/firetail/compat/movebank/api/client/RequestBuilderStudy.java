package de.firetail.compat.movebank.api.client;

public class RequestBuilderStudy extends RequestBuilder {

	public RequestBuilderStudy() {
		super(Constants.Types.STUDY);
	}

	public void setName(String name) {
		parameters.put(Constants.Attributes.NAME, name);
	}
	
}
package de.firetail.compat.movebank.api.client;

public class RequestBuilderIndividual extends RequestBuilder {

	public RequestBuilderIndividual(String studyId) {
		super(Constants.Types.INDIVIDUAL, studyId);
	}

}

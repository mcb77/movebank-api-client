package de.firetail.compat.movebank.api.client;

import java.util.List;

public class StaticDataBrowser {

	List<Record> sensorTypes;

	public StaticDataBrowser( MovebankApiClient client) throws Exception {
		sensorTypes = client.readAll(new RequestBuilderSensorType());
	}

	public String getSensorTypeId(String externalId) {
		for ( Record sensorType : sensorTypes)
			if (externalId.equals(sensorType.getValue(Constants.Attributes.EXTERNAL_ID)))
				return sensorType.getValue(Constants.Attributes.ID);
		return null;
	}

}

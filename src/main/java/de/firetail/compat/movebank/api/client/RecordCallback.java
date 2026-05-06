package de.firetail.compat.movebank.api.client;

import java.util.List;

public interface RecordCallback {

	public void start(List<String> attributes) throws Exception;

	public abstract void record(List<String> record, long lineStart, long lineEnd) throws Exception;

	public void end() throws Exception;

}

package de.firetail.compat.movebank.api.client;

import java.util.List;

public abstract class RecordCallbackDefault extends AbstractRecord implements RecordCallback {

	private long lineStart;
	private long lineEnd;
	private List<String> currentRecord;

	public RecordCallbackDefault() {
		super(new DataTypeConverter());
	}

	public final void start(List<String> attributes) throws Exception {
		setAttributes(attributes);
		start();
	}

	public final void record(List<String> currentRecord, long lineStart, long lineEnd) throws Exception {
		this.currentRecord = currentRecord;
		this.lineStart = lineStart;
		this.lineEnd = lineEnd;
		record();
	}

	public long getLineStart() {
		return lineStart;
	}

	public long getLineEnd() {
		return lineEnd;
	}

	protected List<String> getValues() {
		return currentRecord;
	}

	protected void start() throws Exception {
	}

	protected abstract void record() throws Exception;

	public void end() throws Exception {
	}

}

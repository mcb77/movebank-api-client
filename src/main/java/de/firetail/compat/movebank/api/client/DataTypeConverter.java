package de.firetail.compat.movebank.api.client;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class DataTypeConverter {

	DateFormat dateFormat;
	{
		dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
	}

	public Date parseDate(String s) {
		if (s != null && s.length() > 0)
			try {
				return dateFormat.parse(s);
			} catch (ParseException e) {
				throw new RuntimeException(e);
			}
		else
			return null;
	}

	public Long parseLong(String s) {
		if (s != null && s.length() > 0)
			return Long.parseLong(s);
		else
			return null;
	}

	public Boolean parseBoolean(String s) {
		if (s != null && s.length() > 0)
			return Boolean.parseBoolean(s);
		else
			return null;
	}

	public Double parseDouble(String s) {
		if (s != null && s.length() > 0)
			return Double.parseDouble(s);
		else
			return null;
	}

}

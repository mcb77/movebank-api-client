package de.firetail.compat.movebank.api.client;


public interface Constants {

	public static String TYPE = "entity_type";
	public static String ATTRIBUTES = "attributes";
	public static String SORT = "sort";
	public static String LIMIT = "limit";

	public interface Types {
		public static String STUDY = "study";
		public static String STUDY_ATTRIBUTE = "study_attribute";
		public static String SENSOR = "sensor";
		public static String TAG = "tag";
		public static String INDIVIDUAL = "individual";
		public static String DEPLOYMENT = "deployment";
		public static String EVENT = "event";
		public static String TAG_TYPE = "tag_type";
	}

	public interface Attributes {
		public static String ID = "id";
		public static String STUDY_ID = "study_id";
		public static String SENSOR_TYPE_ID = "sensor_type_id";
		public static String SENSOR_SENSOR_TYPE_ID = "sensor_sensor_type_id";
		public static String TAG_ID = "tag_id";
		public static String INDIVIDUAL_ID = "individual_id";
		public static String SHORT_NAME = "short_name";
		public static String TIMESTAMP = "timestamp";
		public static String LOCATION_LONG = "location_long";
		public static String LOCATION_LAT = "location_lat";
		public static String NAME = "name";
		public static String EXTERNAL_ID = "external_id";
		public static String LOCAL_IDENTIFIER = "local_identifier";

		public static String TAG_STUDY_ID = "tag_study_id";
		public static String TAG_LOCAL_IDENTIFIER = "tag_local_identifier";

		public static String I_AM_OWNER = "i_am_owner";
		public static String I_CAN_SEE_DATA = "i_can_see_data";
		public static String THERE_ARE_DATA_WHICH_I_CANNOT_SEE = "there_are_data_which_i_cannot_see";
	}

	public interface SensorTypes {
		public static final String ARGOS_DOPPLER_SHIFT = "argos-doppler-shift";
		public static final String BIRD_RING = "bird-ring";
		public static final String GPS = "gps";
		public static final String RADIO_TRANSMITTER = "radio-transmitter";
		public static final String ACCELERATION = "acceleration";
	}

}

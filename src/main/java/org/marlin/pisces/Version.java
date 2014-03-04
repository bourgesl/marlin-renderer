package org.marlin.pisces;

import java.io.InputStream;
import java.util.Properties;

public final class Version {

	private static String version = null;

	public static String getVersion() {
		if (version == null) {
			version="undefined";
			/* load Version.properties */
			try {
				InputStream in = Version.class.getResourceAsStream("Version.properties");
				Properties prop = new Properties();
				prop.load(in);

				version = prop.getProperty("version", version);
				in.close(); /* TODO: use final */
			} catch (Exception e) {}
		}
		return version;
	}

	private Version() {}

}

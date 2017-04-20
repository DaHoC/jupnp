/**
 * Copyright (C) 2014 4th Line GmbH, Switzerland and others
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License Version 1 or later
 * ("CDDL") (collectively, the "License"). You may not use this file
 * except in compliance with the License. See LICENSE.txt for more
 * information.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package org.jupnp.util;

import org.jupnp.model.meta.Device;
import org.jupnp.model.meta.DeviceIdentity;
import org.jupnp.model.meta.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class reports violations again UPnP specification. It allows to
 * enable/disable these reports. E.g. for embedded devices it makes sense to
 * disable these checks for performance improvement and to avoid flooding of
 * logs if you have UPnP devices in your network which does not comply to UPnP
 * specifications.
 * 
 * @author Jochen Hiller
 */
public class SpecificationViolationReporter {

	private static final String PROPERTY_ENABLE_VIOLATION_REPORTER = "org.jupnp.report.specviolation";

	/**
	 * Defaults to enabled if system property not set. Is volatile to reflect
	 * changes in arbitrary threads immediately.
	 */
	private static volatile boolean enabled = Boolean
			.valueOf(System.getProperty(PROPERTY_ENABLE_VIOLATION_REPORTER, "true"));

	private static Logger logger = LoggerFactory.getLogger(SpecificationViolationReporter.class);

	public static void disableReporting() {
		enabled = false;
	}

	public static void enableReporting() {
		enabled = true;
	}

	public static void violate(String msg) {
		if (enabled) {
			logger.warn("UPnP specification violation: " + msg);
		}
	}

	public static void violate(Device<DeviceIdentity, Device, Service> device, String msg) {
		if (enabled) {
			logger.warn("UPnP specification violation"
					+ (device != null ? " of device '" + device.toString() + "'" : "") + ": " + msg);
		}
	}

}

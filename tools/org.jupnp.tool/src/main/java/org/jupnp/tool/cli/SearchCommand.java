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

package org.jupnp.tool.cli;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.jupnp.UpnpService;
import org.jupnp.UpnpServiceImpl;
import org.jupnp.model.message.header.STAllHeader;
import org.jupnp.model.meta.LocalDevice;
import org.jupnp.model.meta.RemoteDevice;
import org.jupnp.registry.Registry;
import org.jupnp.registry.RegistryListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SearchCommand {

	private static Logger logger = LoggerFactory.getLogger(SearchCommand.class);
	private JUPnPTool tool;

	public SearchCommand(JUPnPTool tool) {
		this.tool = tool;
	}

	public int run(int timeout, String sortBy, String filter, boolean verbose) {
		// This will create necessary network resources for UPnP right away
		logger.debug("Starting jUPnP search...");
		UpnpService upnpService = new UpnpServiceImpl();

		SearchResultPrinter printer = new SearchResultPrinter(sortBy, verbose);
		if (!hasToSort(sortBy)) {
			upnpService.getRegistry()
					.addListener(
							new SearchRegistryListener(printer, sortBy, filter,
									verbose));
		}
		upnpService.startup();
		printer.printHeader();

		// Send a search message to all devices and services, they should
		// respond soon
		logger.debug("Sending SEARCH message to all devices...");
		upnpService.getControlPoint().search(new STAllHeader());

		// Let's wait "timeout" for them to respond
		logger.debug("Waiting " + timeout + " seconds before shutting down...");
		try {
			Thread.sleep(timeout * 1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		logger.debug("Processing results...");
		Registry registry = upnpService.getRegistry();

		for (Iterator<RemoteDevice> iter = registry.getRemoteDevices()
				.iterator(); iter.hasNext();) {
			RemoteDevice device = iter.next();
			handleRemoteDevice(device, printer, sortBy, filter, verbose);
		}

		printer.printBody();

		// Release all resources and advertise BYEBYE to other UPnP devices
		logger.debug("Stopping jUPnP...");
		try {
			upnpService.shutdown();
		} catch (Exception ex) {
			logger.error("Error during shutdown", ex);
		}

		return JUPnPTool.RC_OK;
	}

	private void handleRemoteDevice(RemoteDevice device,
			SearchResultPrinter searchResult, String sortBy, String filter,
			boolean verbose) {
		if (device.isRoot()) {
			// logStdout(device.toString());
			String ipAddress = device.getIdentity().getDescriptorURL()
					.getHost();
			String model = device.getDetails().getModelDetails().getModelName();
			String manu = device.getDetails().getManufacturerDetails()
					.getManufacturer();
			String udn = device.getIdentity().getUdn().getIdentifierString();
			String name = device.getDisplayString();
			String serialNumber = device.getDetails().getSerialNumber();
			// some devices will return "null" as serialNumber
			// TODO needs check where this happens in JUPnP
			if ((serialNumber == null) || ("null".equals(serialNumber))) {
				serialNumber = "-";
			}

			String fullDeviceInformationString = ipAddress + "\n" + model
					+ "\n" + manu + "\n" + udn + "\n" + serialNumber + "\n"
					+ name;
			boolean filterOK = false;
			if (filter.equals("*")) {
				filterOK = true;
			} else if (fullDeviceInformationString.contains(filter)) {
				logger.debug("Filter check: filter '" + filter + "' matched '"
						+ fullDeviceInformationString + "'");
				filterOK = true;
			} else {
				logger.debug("Filter check: filter '" + filter
						+ "' NOT matched '" + fullDeviceInformationString + "'");
			}

			// filter out: very simple: details from above should include
			// this text
			if (filterOK) {
				searchResult.add(ipAddress, model, serialNumber, manu, udn);
			}
		}
	}

	class SearchRegistryListener implements RegistryListener {

		private final SearchResultPrinter printer;
		private final String sortBy;
		private final String filter;
		private final boolean verbose;

		public SearchRegistryListener(SearchResultPrinter printer,
				String sortBy, String filter, boolean verbose) {
			this.printer = printer;
			this.sortBy = sortBy;
			this.filter = filter;
			this.verbose = verbose;
		}

		@Override
		public void remoteDeviceDiscoveryStarted(Registry registry,
				RemoteDevice device) {
			// ignore
		}

		@Override
		public void remoteDeviceDiscoveryFailed(Registry registry,
				RemoteDevice device, Exception ex) {
			// ignore
		}

		@Override
		public void remoteDeviceAdded(Registry registry, RemoteDevice device) {
			handleRemoteDevice(device, printer, sortBy, filter, verbose);
		}

		@Override
		public void remoteDeviceUpdated(Registry registry, RemoteDevice device) {
			// ignore
		}

		@Override
		public void remoteDeviceRemoved(Registry registry, RemoteDevice device) {
			// ignore
		}

		@Override
		public void localDeviceAdded(Registry registry, LocalDevice device) {
			// ignore
		}

		@Override
		public void localDeviceRemoved(Registry registry, LocalDevice device) {
			// ignore
		}

		@Override
		public void beforeShutdown(Registry registry) {
			// ignore
		}

		@Override
		public void afterShutdown() {
			// ignore
		}

	}

	/**
	 * Will contain search results, will filter out duplicate ip addresses.
	 */
	public class SearchResultPrinter {

		private class Result {
			private String ipAddress;
			private String model;
			private String serialNumber;
			private String manufacturer;
			private String udn;

			public Result(String i, String m, String s, String manu, String udn) {
				this.ipAddress = i;
				this.model = m;
				this.serialNumber = s;
				this.manufacturer = manu;
				this.udn = udn;
			}
		}

		private final int[] COLUMN_WIDTH = new int[] { 17, 25, 25, 25, 25 };
		private final List<Result> results = new ArrayList<Result>();
		private final List<String> ipAddresses = new ArrayList<String>();
		private final String sortBy;
		private final boolean verbose;

		public SearchResultPrinter(String sortBy, boolean verbose) {
			this.sortBy = sortBy;
			this.verbose = verbose;
		}

		public void printHeader() {
			if (hasToSort(sortBy)) {
				// nothing to do, header will be printed later
				return;
			}
			String msg;
			if (verbose) {
				msg = fixedWidth("IP address", COLUMN_WIDTH[0])
						+ fixedWidth("Model", COLUMN_WIDTH[1])
						+ fixedWidth("Manufacturer", COLUMN_WIDTH[2])
						+ fixedWidth("SerialNumber", COLUMN_WIDTH[3])
						+ fixedWidth("UDN", COLUMN_WIDTH[4]);
			} else {
				msg = fixedWidth("IP address", COLUMN_WIDTH[0])
						+ fixedWidth("Model", COLUMN_WIDTH[1])
						+ fixedWidth("SerialNumber", COLUMN_WIDTH[3]);
			}
			tool.printStdout(msg);
		}

		public void add(String ip, String model, String serialNumber,
				String manu, String udn) {
			if (!ipAddresses.contains(ip)) {
				results.add(new Result(ip, model, serialNumber, manu, udn));
				if (!hasToSort(sortBy)) {
					String msg;
					if (verbose) {
						msg = fixedWidth(ip, COLUMN_WIDTH[0])
								+ fixedWidth(model, COLUMN_WIDTH[1])
								+ fixedWidth(manu, COLUMN_WIDTH[2])
								+ fixedWidth(serialNumber, COLUMN_WIDTH[3])
								+ fixedWidth(udn, COLUMN_WIDTH[4]);
					} else {
						msg = fixedWidth(ip, COLUMN_WIDTH[0])
								+ fixedWidth(model, COLUMN_WIDTH[1])
								+ fixedWidth(serialNumber, COLUMN_WIDTH[3]);
					}
					tool.printStdout(msg);
				}
				ipAddresses.add(ip);
			}
		}

		public void printBody() {
			if (!hasToSort(sortBy)) {
				// nothing to do, results have been printed during add()
				return;
			}
			String msg = asBody();
			tool.printStdout(msg);

		}

		public String asBody() {
			// sort now
			sortResults(sortBy);
			// convert map to table
			List<String[]> table = new ArrayList<String[]>();
			if (verbose) {
				table.add(new String[] { "IP address", "Model", "Manufacturer",
						"SerialNumber", "UDN" });
			} else {
				table.add(new String[] { "IP address", "Model", "SerialNumber" });
			}
			for (Iterator<Result> iter = results.iterator(); iter.hasNext();) {
				Result result = iter.next();
				if (verbose) {
					table.add(new String[] { result.ipAddress, result.model,
							result.manufacturer, result.serialNumber,
							result.udn });
				} else {
					table.add(new String[] { result.ipAddress, result.model,
							result.serialNumber });
				}
			}
			String msg = PrintUtils.printTable(table, 4);
			// if only one line: no device found
			if (results.size() == 0) {
				msg = msg + "<no devices found>";
			}
			return msg;
		}

		private void sortResults(final String columnName) {
			Comparator<Result> comparator = new Comparator<Result>() {
				@Override
				public int compare(Result o1, Result o2) {
					if ("ip".equals(columnName)) {
						return compareIpAddress(o1.ipAddress, o2.ipAddress);
					} else if ("model".equals(columnName)) {
						return o1.model.compareTo(o2.model);
					} else if ("serialNumber".equals(columnName)) {
						return o1.serialNumber.compareTo(o2.serialNumber);
					} else if ("manufacturer".equals(columnName)) {
						return o1.manufacturer.compareTo(o2.manufacturer);
					} else if ("udn".equals(columnName)) {
						return o1.udn.compareTo(o2.udn);
					} else {
						return 0;
					}
				}
			};
			Collections.sort(results, comparator);
		}

		private int compareIpAddress(String ip1, String ip2) {
			String[] ip1Parts = ip1.split("[\\.]");
			String[] ip2Parts = ip2.split("[\\.]");
			for (int i = 0; i < ip1Parts.length; i++) {
				int ip1Int = Integer.parseInt(ip1Parts[i]);
				int ip2Int = Integer.parseInt(ip2Parts[i]);
				if (ip1Int < ip2Int) {
					return -1;
				} else if (ip1Int > ip2Int) {
					return 1;
				}
			}
			return 0;
		}

		private final static String STRING_WITH_SPACES = "                           ";

		private String fixedWidth(String s, int width) {
			if (s.length() >= width) {
				return s;
			} else {
				return s + STRING_WITH_SPACES.substring(0, width - s.length());
			}
		}
	}

	private boolean hasToSort(String sortBy) {
		return !"none".equals(sortBy);
	}

}

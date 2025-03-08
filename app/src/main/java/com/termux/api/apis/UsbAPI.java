package com.termux.api.apis;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConfiguration;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.JsonWriter;
import android.util.SparseArray;

import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import com.termux.api.UsbAPIProto.termuxUsb;
import com.termux.api.UsbAPIProto.termuxUsbConfigDescriptor;
import com.termux.api.UsbAPIProto.termuxUsbDevice;
import com.termux.api.UsbAPIProto.termuxUsbDeviceDescriptor;
import com.termux.api.UsbAPIProto.termuxUsbEndpointDescriptor;
import com.termux.api.UsbAPIProto.termuxUsbInterfaceDescriptor;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import androidx.annotation.NonNull;

public class UsbAPI {

    protected static final String LOG_TAG = "UsbAPI";

    protected static SparseArray<UsbDeviceConnection> openDevices = new SparseArray<>();

    protected static final String ACTION_USB_PERMISSION = TermuxConstants.TERMUX_API_PACKAGE_NAME + ".USB_PERMISSION";

    public static void onReceive(final Context context, final Intent intent) {
	Logger.logDebug(LOG_TAG, "onReceive");

	Intent serviceIntent = new Intent(context, UsbService.class);
	serviceIntent.setAction(intent.getAction());
	Bundle extras = intent.getExtras();
	if (extras != null)
	    serviceIntent.putExtras(extras);
	context.startService(serviceIntent);
    }

    public static class UsbService extends Service {

	protected static final String LOG_TAG = "UsbService";

	private final ThreadPoolExecutor mThreadPoolExecutor;

	public UsbService() {
	    super();
	    mThreadPoolExecutor = new ThreadPoolExecutor(1, 1,
		    0L, TimeUnit.MILLISECONDS,
		    new LinkedBlockingQueue<>());
	}

	@Override
	public IBinder onBind(Intent intent) {
	    return null;
	}

	public void onCreate() {
	    Logger.logDebug(LOG_TAG, "onCreate");

	    super.onCreate();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
	    Logger.logDebug(LOG_TAG, "onStartCommand");

	    String action = intent.getAction();
	    if (action == null) {
		Logger.logError(LOG_TAG, "No action passed");
		ResultReturner.returnData(this, intent, out -> out.append("Missing action\n"));
	    }

	    if (action != null) {
		switch (action) {
		    case "list":
			runListAction(intent);
			break;
		    case "permission":
			runPermissionAction(intent);
			break;
		    case "open":
			runOpenAction(intent);
			break;

		   /* The following cases produce serialised data that is
		    * supposed to be parsed by libusb (or some other
		    * program/library) in userspace, and not printed to
		    * stdout */
		    case "getDevices":
			runGetDevicesAction(intent);
			break;
		    case "getConfigDescriptor":
			getConfigDescriptorAction(intent);
			break;
		    default:
			Logger.logError(LOG_TAG, "Invalid action: \"" + action + "\"");
			ResultReturner.returnData(this, intent, out -> out.append("Invalid action: \"" + action + "\"\n"));
		}
	    }

	    return Service.START_NOT_STICKY;
	}

	@Override
	public void onDestroy() {
	    Logger.logDebug(LOG_TAG, "onDestroy");

	    super.onDestroy();
	}



	protected void runListAction(Intent intent) {
	    Logger.logVerbose(LOG_TAG,"Running 'list' usb devices action");

	    ResultReturner.returnData(this, intent, new ResultReturner.ResultJsonWriter() {
		@Override
		public void writeJson(JsonWriter out) throws Exception {
		    listDevices(out);
		}
	    });
	}

	protected void listDevices(JsonWriter out) throws IOException {
	    UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
	    HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
	    out.beginArray();
	    for (UsbDevice device : deviceList.values()) {
		out.beginObject();
		out.name("device_name").value(device.getDeviceName());
		out.name("device_id").value(device.getDeviceId());
		out.name("vendor_id").value(String.format("0x%04x", device.getVendorId()));
		out.name("product_id").value(String.format("0x%04x", device.getProductId()));
		out.name("device_class").value(device.getDeviceClass()+" - "+translateDeviceClass(device.getDeviceClass()));
		out.name("device_subclass").value(device.getDeviceSubclass());
		if (device.getManufacturerName() != null) {
		    out.name("manufacturer_name").value(device.getManufacturerName().replace("\u0000", ""));
		} else {
		    out.name("manufacturer_name").value(device.getManufacturerName());
		}
		out.name("device_protocol").value(device.getDeviceProtocol());
		if (device.getProductName() != null) {
		    out.name("product name").value(device.getProductName().replace("\u0000", ""));
		} else {
		    out.name("product name").value(device.getProductName());
		}
		out.name("serial_number").value(device.getSerialNumber());
		out.name("configurations").value(device.getConfigurationCount());
		out.name("descriptor_type").value(device.describeContents());
		out.name("access_granted").value(usbManager.hasPermission(device));
		out.endObject();
	    }
	    out.endArray();
	}

	private static String translateDeviceClass(int usbClass){
	    switch(usbClass){
	    case UsbConstants.USB_CLASS_APP_SPEC:
		return "App specific USB class";
	    case UsbConstants.USB_CLASS_AUDIO:
		return "Audio device";
	    case UsbConstants.USB_CLASS_CDC_DATA:
		return "CDC device (communications device class)";
	    case UsbConstants.USB_CLASS_COMM:
		return "Communication device";
	    case UsbConstants.USB_CLASS_CONTENT_SEC:
		return "Content security device";
	    case UsbConstants.USB_CLASS_CSCID:
		return "Content smart card device";
	    case UsbConstants.USB_CLASS_HID:
		return "Human interface device (for example a keyboard)";
	    case UsbConstants.USB_CLASS_HUB:
		return "USB hub";
	    case UsbConstants.USB_CLASS_MASS_STORAGE:
		return "Mass storage device";
	    case UsbConstants.USB_CLASS_MISC:
		return "Wireless miscellaneous devices";
	    case UsbConstants.USB_CLASS_PER_INTERFACE:
		return "Usb class is determined on a per-interface basis";
	    case UsbConstants.USB_CLASS_PHYSICA:
		return "Physical device";
	    case UsbConstants.USB_CLASS_PRINTER:
		return "Printer";
	    case UsbConstants.USB_CLASS_STILL_IMAGE:
		return "Still image devices (digital cameras)";
	    case UsbConstants.USB_CLASS_VENDOR_SPEC:
		return "Vendor specific USB class";
	    case UsbConstants.USB_CLASS_VIDEO:
		return "Video device";
	    case UsbConstants.USB_CLASS_WIRELESS_CONTROLLER:
		return "Wireless controller device";
	    default: return "Unknown USB class!";
	    }
	}


	protected void runPermissionAction(Intent intent) {
	    mThreadPoolExecutor.submit(() -> {
		String deviceName = intent.getStringExtra("device");

		Logger.logVerbose(LOG_TAG,"Running 'permission' action for device \"" + deviceName + "\"");

		UsbDevice device = getDevice(intent, deviceName);
		if (device == null) return;

		int status = checkAndRequestUsbDevicePermission(intent, device);
		ResultReturner.returnData(this, intent, out -> {
		    if (status == 0) {
			Logger.logVerbose(LOG_TAG, "Permission granted for device \"" + device.getDeviceName() + "\"");
			out.append("Permission granted.\n" );
		    } else if (status == 1) {
			Logger.logVerbose(LOG_TAG, "Permission denied for device \"" + device.getDeviceName() + "\"");
			out.append("Permission denied.\n" );
		    } else if (status == -1) {
			out.append("Permission request timeout.\n" );
		    }
		});
	    });
	}



	protected void runOpenAction(Intent intent) {
	    mThreadPoolExecutor.submit(() -> {
		String deviceName = intent.getStringExtra("device");
		String vendorId = intent.getStringExtra("vendorId");
		String productId = intent.getStringExtra("productId");
		if (deviceName == null && (vendorId == null || productId == null)) {
		    Logger.logError(LOG_TAG, "Missing usb device info in open()");
		}

		UsbDevice device;
		if (deviceName != null) {
		    Logger.logVerbose(LOG_TAG,"Running 'open' action for device \"" + deviceName + "\"");
		    device = getDevice(intent, deviceName);
		} else {
		    Logger.logVerbose(LOG_TAG,"Running 'open' action for vendor Id \"" + vendorId + "\" and product Id \"" + productId + "\"");
		    device = getDevice(intent, vendorId, productId);
		}

		if (device == null) return;

		int status = checkAndRequestUsbDevicePermission(intent, device);
		ResultReturner.returnData(this, intent, new ResultReturner.WithAncillaryFd() {
		    @Override
		    public void writeResult(PrintWriter out) {
			if (status == 0) {
			    int fd = open(device);
			    if (fd < 0) {
				Logger.logVerbose(LOG_TAG, "Failed to open device \"" + device.getDeviceName() + "\": " + fd);
				out.append("Open device failed.\n");
			    } else {
				Logger.logVerbose(LOG_TAG, "Open device \"" + device.getDeviceName() + "\" successful");
				this.sendFd(out, fd);
			    }
			} else if (status == 1) {
			    Logger.logVerbose(LOG_TAG, "Permission denied to open device \"" + device.getDeviceName() + "\"");
			    out.append("Permission denied.\n" );
			} else if (status == -1) {
			    out.append("Permission request timeout.\n" );
			}
		    }
		});
	    });
	}

	protected int open(@NonNull UsbDevice device) {
	    UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

	    UsbDeviceConnection connection = usbManager.openDevice(device);
	    if (connection == null) return -2;

	    int fd = connection.getFileDescriptor();
	    if (fd == -1) {
		connection.close();
		return -1;
	    }

	    openDevices.put(fd, connection);
	    return fd;
	}



	protected UsbDevice getDevice(Intent intent, String deviceName) {
	    UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

	    HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
	    UsbDevice device = deviceList.get(deviceName);
	    if (device == null) {
		Logger.logVerbose(LOG_TAG, "Failed to find device \"" + deviceName + "\"");
		ResultReturner.returnData(this, intent, out -> out.append("No such device.\n"));
	    }

	    return device;
	}

	protected UsbDevice getDevice(Intent intent, String vendorId, String productId) {
	    UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

	    HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
	    for (UsbDevice dev : deviceList.values()) {
		    if (String.format("0x%04x", dev.getVendorId()).equalsIgnoreCase(vendorId) &&
			String.format("0x%04x", dev.getProductId()).equalsIgnoreCase(productId)) {
			    return dev;
		    }

	    }
	    Logger.logVerbose(LOG_TAG, "Failed to find device with vendor Id \"" + vendorId + "\" and product Id\"" + productId + "\"");
	    ResultReturner.returnData(this, intent, out -> out.append("No such device.\n"));

	    return null;
	}


	protected boolean checkUsbDevicePermission(@NonNull UsbDevice device) {
	    UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
	    return usbManager.hasPermission(device);
	}

	protected int checkAndRequestUsbDevicePermission(Intent intent, @NonNull UsbDevice device) {
	    boolean checkResult = checkUsbDevicePermission(device);
	    Logger.logVerbose(LOG_TAG, "Permission check result for device \"" + device.getDeviceName() + "\": " + checkResult);
	    if (checkResult) {
		return 0;
	    }

	    if(!intent.getBooleanExtra("request", false)) {
		return 1;
	    }

	    Logger.logVerbose(LOG_TAG, "Requesting permission for device \"" + device.getDeviceName() + "\"");

	    CountDownLatch latch = new CountDownLatch(1);
	    AtomicReference<Boolean> result = new AtomicReference<>();

	    BroadcastReceiver usbReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent usbIntent) {
		    if (ACTION_USB_PERMISSION.equals(usbIntent.getAction())) {
			boolean requestResult = usbIntent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
			Logger.logVerbose(LOG_TAG, "Permission request result for device \"" + device.getDeviceName() + "\": " + requestResult);
			result.set(requestResult);
		    }
		    context.unregisterReceiver(this);
		    latch.countDown();
		}
	    };

	    UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

	    Intent usbIntent = new Intent(ACTION_USB_PERMISSION);
	    // Use explicit intent, otherwise permission request intent will be blocked if intent is
	    // mutable and app uses `targetSdkVersion` `>= 34`, or following exception will be logged
	    // to logcat if app uses `targetSdkVersion` `< 34`.
	    // > `android.app.StackTrace: New mutable implicit PendingIntent: pkg=com.termux.api,
	    // > action=com.termux.api.USB_PERMISSION, featureId=null. This will be blocked once the
	    // > app targets U+ for security reasons.`
	    // - https://developer.android.com/about/versions/14/behavior-changes-14#safer-intents
	    usbIntent.setPackage(getPackageName());

	    // Use mutable intent, otherwise permission request intent will be blocked if app
	    // uses `targetSdkVersion` `>= 31` and following exception may be logged to logcat.
	    // > java.lang.IllegalArgumentException: com.termux.api: Targeting S+ (version 31 and above)
	    // > requires that one of FLAG_IMMUTABLE or FLAG_MUTABLE be specified when creating a PendingIntent.
	    // > Strongly consider using FLAG_IMMUTABLE, only use FLAG_MUTABLE if some functionality
	    // > depends on the PendingIntent being mutable, e.g. if it needs to be used with inline
	    // > replies or bubbles.
	    // The intent must not be immutable as the `EXTRA_PERMISSION_GRANTED` extra needs to be
	    // returned by the Android framework. Otherwise, if requesting permission after
	    // reattaching device, and user presses `OK` to grant permission, the
	    // `EXTRA_PERMISSION_GRANTED` extra would not exist in the intent, and default `false`
	    // value would get used, and `No permission` condition of the open request would get
	    // triggered, even though permission was granted and it won't need to be requested for
	    // next open request.
	    // - https://developer.android.com/about/versions/12/behavior-changes-12#pending-intent-mutability
	    //noinspection ObsoleteSdkInt
	    int pendingIntentFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_MUTABLE : 0;
	    PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, usbIntent, pendingIntentFlags);

	    try {
		// Specify flag to not export receiver, otherwise permission request intent will be
		// blocked if app uses `targetSdkVersion` `>= 34`.
		// - https://developer.android.com/about/versions/14/behavior-changes-14#runtime-receivers-exported
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
		    registerReceiver(usbReceiver, new IntentFilter(ACTION_USB_PERMISSION),
			    Context.RECEIVER_NOT_EXPORTED);
		} else {
		    //noinspection UnspecifiedRegisterReceiverFlag
		    registerReceiver(usbReceiver, new IntentFilter(ACTION_USB_PERMISSION));
		}

		// Request permission and wait.
		usbManager.requestPermission(device, permissionIntent);

		try {
		    if (!latch.await(30L, TimeUnit.SECONDS)) {
			Logger.logVerbose(LOG_TAG, "Permission request time out for device \"" + device.getDeviceName() + "\" after 30s");
			return -1;
		    }
		} catch (InterruptedException e) {
		    throw new RuntimeException(e);
		}

		Boolean requestResult = result.get();
		if (requestResult != null) {
		    usbReceiver = null;
		    return requestResult ? 0 : 1;
		} else {
		    return 1;
		}
	    } finally {
		try {
		    if (usbReceiver != null) {
			unregisterReceiver(usbReceiver);
		    }
		} catch (Exception e) {
		    // Ignore
		}
	    }
	}
	/* The following actions produce serialised data that is suppose
	 * to be parsed by some program or library in userspace, and not
	 * printed to stdout */

	protected void runGetDevicesAction(Intent intent) {
	    ResultReturner.returnData(this, intent, new ResultReturner.BinaryOutput() {
		@Override
		public void writeResult(OutputStream out) throws Exception {
		    termuxUsb.Builder devices = termuxUsb.newBuilder();
		    UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

		    HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
		    for (UsbDevice dev : deviceList.values()) {
			termuxUsbDevice.Builder deviceBuilder = termuxUsbDevice.newBuilder();

			String[] devName = dev.getDeviceName().split("/");
			int busNum = Integer.valueOf(devName[devName.length - 1]);
			int portNum = Integer.valueOf(devName[devName.length - 2]);

			deviceBuilder.setBusNumber(busNum);
			deviceBuilder.setPortNumber(portNum);
			deviceBuilder.setDeviceAddress(dev.getDeviceName());

			termuxUsbDeviceDescriptor.Builder deviceDescBuilder = termuxUsbDeviceDescriptor.newBuilder();

			deviceDescBuilder.setConfigurationCount(dev.getConfigurationCount());
			deviceDescBuilder.setDeviceClass(dev.getDeviceClass());
			deviceDescBuilder.setDeviceProtocol(dev.getDeviceProtocol());
			deviceDescBuilder.setDeviceSubclass(dev.getDeviceSubclass());
			deviceDescBuilder.setProductId(dev.getProductId());
			deviceDescBuilder.setVendorId(dev.getVendorId());
			if (dev.getManufacturerName() != null) {
			deviceDescBuilder.setManufacturerName(dev.getManufacturerName().replace("\u0000", ""));
			} else {
			deviceDescBuilder.setManufacturerName("");
			}
			if (dev.getProductName() != null) {
			deviceDescBuilder.setProductName(dev.getProductName().replace("\u0000", ""));
			} else {
			deviceDescBuilder.setProductName("");
			}
			if (dev.getSerialNumber() != null) {
			deviceDescBuilder.setSerialNumber(dev.getSerialNumber());
			} else {
			deviceDescBuilder.setSerialNumber("");
			}

			termuxUsbDeviceDescriptor deviceDesc = deviceDescBuilder.build();

			deviceBuilder.setDevice(deviceDesc);

			termuxUsbDevice device = deviceBuilder.build();

			Logger.logDebug(LOG_TAG, device.toString());
			devices.addDevice(device);
		    }
		    devices.build().writeTo(out);
		}
	    });
	}

	protected void getConfigDescriptorAction(Intent intent) {
	    ResultReturner.returnData(this, intent, new ResultReturner.BinaryOutput() {
		@Override
		public void writeResult(OutputStream out) throws Exception {
		    String deviceName = intent.getStringExtra("device");
		    int configIndex = intent.getIntExtra("config", 0);
		    UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
		    HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();

		    if (deviceName == null) {
			Logger.logError(LOG_TAG, "Missing device argument\n");
			return;
		    }

		    UsbDevice device = deviceList.get(deviceName);
		    if (device == null) {
			Logger.logError(LOG_TAG, "No such device\n");
			return;
		    }
		    if (device.getConfigurationCount()-1 < configIndex) {
			Logger.logError(LOG_TAG, "Requested config does not exist\n");
			return;
		    }
		    UsbConfiguration configuration = device.getConfiguration(configIndex);
		    int numInterfaces = configuration.getInterfaceCount();

		    termuxUsbConfigDescriptor.Builder configBuilder = termuxUsbConfigDescriptor.newBuilder();
		    configBuilder.setConfigurationValue(configIndex);
		    configBuilder.setMaxPower(configuration.getMaxPower());
		    if (configuration.getName() != null) {
			configBuilder.setConfiguration(configuration.getName());
		    } else {
			configBuilder.setConfiguration("");
		    }

		    for (int i = 0; i < numInterfaces; i++) {
			UsbInterface intf = configuration.getInterface(i);

			termuxUsbInterfaceDescriptor.Builder intfDescBuilder = termuxUsbInterfaceDescriptor.newBuilder();
			intfDescBuilder.setAlternateSetting(intf.getAlternateSetting());
			intfDescBuilder.setInterfaceClass(intf.getInterfaceClass());
			intfDescBuilder.setInterfaceSubclass(intf.getInterfaceSubclass());
			intfDescBuilder.setInterfaceProtocol(intf.getInterfaceProtocol());
			if (intf.getName() != null) {
			    intfDescBuilder.setInterface(intf.getName());
			} else {
			    intfDescBuilder.setInterface("");
			}
			int numEndpoints = intf.getEndpointCount();

			for (int j = 0; j < numEndpoints; j++) {
			    UsbEndpoint endpoint = intf.getEndpoint(j);

			    termuxUsbEndpointDescriptor.Builder endpointDescBuilder = termuxUsbEndpointDescriptor.newBuilder();
			    endpointDescBuilder.setEndpointAddress(endpoint.getAddress());
			    endpointDescBuilder.setAttributes(endpoint.getAttributes());
			    endpointDescBuilder.setMaxPacketSize(endpoint.getMaxPacketSize());
			    endpointDescBuilder.setInterval(endpoint.getInterval());

			    intfDescBuilder.addEndpoint(endpointDescBuilder.build());
			}

			configBuilder.addInterface(intfDescBuilder.build());
		    }

		    termuxUsbConfigDescriptor config = configBuilder.build();
		    Logger.logDebug(LOG_TAG, config.toString());
		    config.writeTo(out);
		}
	    });
	}
    }

}

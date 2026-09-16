package com.termux.api.apis;

import android.content.Context;
import android.content.Intent;

import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;

import java.io.PrintWriter;

import com.termux.api.TermuxAccessibilityService;
import android.view.accessibility.AccessibilityNodeInfo;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import java.io.StringWriter;

import android.graphics.Rect;

import android.provider.Settings;

import android.view.accessibility.AccessibilityManager;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.pm.ServiceInfo;
import java.util.List;
import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityWindowInfo;
import android.util.SparseArray;

import android.os.Bundle;

import android.view.Display;
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback;
import android.accessibilityservice.AccessibilityService.ScreenshotResult;
import android.hardware.HardwareBuffer;
import android.graphics.ColorSpace;
import android.graphics.Bitmap;
import java.io.OutputStream;
import java.io.IOException;
import java.lang.reflect.Field;

import android.hardware.display.DisplayManager;
import android.util.JsonWriter;
import com.termux.api.util.ResultReturner.ResultJsonWriter;
import android.graphics.Point;

public class AccessibilityAPI {

    private static final String LOG_TAG = "AccessibilityAPI";

    public static void onReceive(TermuxApiReceiver apiReceiver, final Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        // Not always reliable, see https://codeberg.org/Benjamin_Loison/Voice_assistant/issues/58#issuecomment-19684162
		/*boolean isAccessibilityEnabled = isAccessibilityServiceEnabled(context, TermuxAccessibilityService.class);
        if (!isAccessibilityEnabled) {
            Intent accessibilityIntent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            accessibilityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(accessibilityIntent);
        }*/

		int displayId = intent.getIntExtra("display-id", Display.DEFAULT_DISPLAY);

		if (intent.hasExtra("dump")) {
			dump(apiReceiver, intent, displayId);
		} else if (intent.hasExtra("click")) {
			click(intent.getIntExtra("x", 0), intent.getIntExtra("y", 0), intent.getIntExtra("duration", 1), displayId);
			returnEmptyString(apiReceiver, intent);
		} else if (intent.hasExtra("type")) {
			type(intent.getStringExtra("type"));
			returnEmptyString(apiReceiver, intent);
		} else if (intent.hasExtra("global-action")) {
			performGlobalAction(intent.getStringExtra("global-action"));
			returnEmptyString(apiReceiver, intent);
		} else if (intent.hasExtra("screenshot")) {
			screenshot(apiReceiver, context, intent, displayId);
		} else if (intent.hasExtra("list-displays")) {
			ResultReturner.returnData(apiReceiver, intent, new ResultJsonWriter() {
				@Override
				public void writeJson(final JsonWriter out) throws Exception {
					out.beginArray();
					DisplayManager displayManager = (DisplayManager)context.getSystemService(Context.DISPLAY_SERVICE);
					for(Display display : displayManager.getDisplays()) {
						out.beginObject();
						out.name("id").value(display.getDisplayId());
						out.name("name").value(display.getName());
						out.name("refresh_rate").value(display.getRefreshRate());
						out.name("flags").value(display.getFlags());
						Point size = new Point();
						display.getSize(size);
						out.name("height").value(size.y);
						out.name("width").value(size.x);
						out.endObject();
					}
					out.endArray();
				}
			});
		}
    }

	// Necessary for void functions not to hang.
	private static void returnEmptyString(TermuxApiReceiver apiReceiver, Intent intent) {
		ResultReturner.returnData(apiReceiver, intent, out -> {});
	}

    // [The Stack Overflow answer 14923144](https://stackoverflow.com/a/14923144)
    public static boolean isAccessibilityServiceEnabled(Context context, Class<? extends AccessibilityService> service) {
        AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);

        for (AccessibilityServiceInfo enabledService : enabledServices) {
            ServiceInfo enabledServiceInfo = enabledService.getResolveInfo().serviceInfo;
            if (enabledServiceInfo.packageName.equals(context.getPackageName()) && enabledServiceInfo.name.equals(service.getName()))
                return true;
        }

        return false;
    }

    private static void click(int x, int y, int millisecondsDuration, int displayId) {
        Path swipePath = new Path();
        swipePath.moveTo(x, y);
        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
		gestureBuilder.setDisplayId(displayId);
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(swipePath, 0, millisecondsDuration));
        TermuxAccessibilityService.instance.dispatchGesture(gestureBuilder.build(), null, null);
    }

    // The aim of this function is to give a compatible output with `adb` `uiautomator dump`.
    private static void dump(TermuxApiReceiver apiReceiver, Intent intent, int displayId) {
        SparseArray<List<AccessibilityWindowInfo>> windowsOnAllDisplays = TermuxAccessibilityService.instance.getWindowsOnAllDisplays();
		List<AccessibilityWindowInfo> windowsOnDisplay = windowsOnAllDisplays.get(displayId);
        AccessibilityNodeInfo node = windowsOnDisplay.getLast().getRoot();
		// On Signal *App permissions* for instance
        if (node == null) {
			ResultReturner.returnData(apiReceiver, intent, out -> {});
            return;
        }

		String swString = dumpAuxiliary(node);

		ResultReturner.returnData(apiReceiver, intent, out -> {
			out.write(swString);
		});
    }

	private static String dumpAuxiliary(AccessibilityNodeInfo node) {
		// Create a DocumentBuilder
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = null;
        try {
            builder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException parserConfigurationException) {
            Logger.logDebug(LOG_TAG, "ParserConfigurationException");
        }

        // Create a new Document
        Document document = builder.newDocument();

        // Create root element
        Element root = document.createElement("hierarchy");
        document.appendChild(root);

        dumpNodeAuxiliary(document, root, node);

        // Write as XML
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = null;
        try {
            transformer = transformerFactory.newTransformer();
        } catch (TransformerException transformerException) {
            Logger.logDebug(LOG_TAG, "TransformerException transformerFactory.newTransformer");
        }
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        // Necessary to not have surrogate pairs for emojis, see [Benjamin_Loison/Voice_assistant/issues/83#issue-3661619](https://codeberg.org/Benjamin_Loison/Voice_assistant/issues/83#issue-3661619)
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-16");
        DOMSource source = new DOMSource(document);

        StringWriter sw = new StringWriter();
        StreamResult result = new StreamResult(sw);
        try {
            transformer.transform(source, result);
        } catch (TransformerException transformerException) {
            Logger.logDebug(LOG_TAG, "TransformerException transformer.transform");
        }
		return sw.toString();
	}

    private static void dumpNodeAuxiliary(Document document, Element element, AccessibilityNodeInfo node) {
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo nodeChild = node.getChild(i);
            // May be faced randomly, see [Benjamin-Loison/android/issues/28#issuecomment-3975714760](https://github.com/Benjamin-Loison/android/issues/28#issuecomment-3975714760)
            if (nodeChild == null)
            {
                continue;
            }
            Element elementChild = document.createElement("node");

            elementChild.setAttribute("index", String.valueOf(i));

            elementChild.setAttribute("text", getCharSequenceAsString(nodeChild.getText()));

            String nodeChildViewIdResourceName = nodeChild.getViewIdResourceName();
            elementChild.setAttribute("resource-id", nodeChildViewIdResourceName != null ? nodeChildViewIdResourceName : "");

            elementChild.setAttribute("class", getCharSequenceAsString(nodeChild.getClassName()));

            elementChild.setAttribute("package", nodeChild.getPackageName().toString());

            elementChild.setAttribute("content-desc", getCharSequenceAsString(nodeChild.getContentDescription()));

            elementChild.setAttribute("checkable", String.valueOf(nodeChild.isCheckable()));

            elementChild.setAttribute("checked", String.valueOf(nodeChild.isChecked()));

            elementChild.setAttribute("clickable", String.valueOf(nodeChild.isClickable()));

            elementChild.setAttribute("enabled", String.valueOf(nodeChild.isEnabled()));

            elementChild.setAttribute("focusable", String.valueOf(nodeChild.isFocusable()));

            elementChild.setAttribute("focused", String.valueOf(nodeChild.isFocused()));

            elementChild.setAttribute("scrollable", String.valueOf(nodeChild.isScrollable()));

            elementChild.setAttribute("long-clickable", String.valueOf(nodeChild.isLongClickable()));

            elementChild.setAttribute("password", String.valueOf(nodeChild.isPassword()));

            elementChild.setAttribute("selected", String.valueOf(nodeChild.isSelected()));

            Rect nodeChildBounds = new Rect();
            nodeChild.getBoundsInScreen(nodeChildBounds);
            elementChild.setAttribute("bounds", nodeChildBounds.toShortString());

            elementChild.setAttribute("drawing-order", String.valueOf(nodeChild.getDrawingOrder()));

            elementChild.setAttribute("hint", getCharSequenceAsString(nodeChild.getHintText()));

            element.appendChild(elementChild);
            dumpNodeAuxiliary(document, elementChild, nodeChild);
        }
    }

    private static String getCharSequenceAsString(CharSequence charSequence) {
        return charSequence != null ? charSequence.toString() : "";
    }

    private static void type(String toType) {
		SparseArray<List<AccessibilityWindowInfo>> windowsOnAllDisplays = TermuxAccessibilityService.instance.getWindowsOnAllDisplays();
		for(int windowsOnAllDisplayIndex = 0; windowsOnAllDisplayIndex < windowsOnAllDisplays.size(); windowsOnAllDisplayIndex++)
        {
            List<AccessibilityWindowInfo> windowsOnAllDisplay = windowsOnAllDisplays.valueAt(windowsOnAllDisplayIndex);
            for(AccessibilityWindowInfo accessibilityWindowInfo : windowsOnAllDisplay)
			{
				AccessibilityNodeInfo focusedNode = accessibilityWindowInfo.getRoot().findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
				if(focusedNode != null)
				{
					Bundle arguments = new Bundle();
					arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, toType);
					focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
					return;
				}
			}
		}
    }

    private static void performGlobalAction(String globalActionString) {
		String fieldName = "GLOBAL_ACTION_" + globalActionString.toUpperCase();
		Field field = null;
		try {
			field = AccessibilityService.class.getDeclaredField(fieldName);
		} catch (NoSuchFieldException noSuchFieldException) {
			Logger.logDebug(LOG_TAG, "NoSuchFieldException");
		}
		Object globalActionObject = null;
		try {
			globalActionObject = field.get(null);
		} catch(IllegalAccessException illegalAccessException) {
			Logger.logDebug(LOG_TAG, "IllegalAccessException");
		}
		int globalActionInt = (int)globalActionObject;
        TermuxAccessibilityService.instance.performGlobalAction(globalActionInt);
    }

	private static void screenshot(TermuxApiReceiver apiReceiver, final Context context, Intent intent, int displayId) {
		TermuxAccessibilityService.instance.takeScreenshot(
            displayId,
            context.getMainExecutor(),
            new TakeScreenshotCallback() {

                @Override
                public void onSuccess(ScreenshotResult screenshotResult) {
                    Logger.logDebug(LOG_TAG, "onSuccess");
                    HardwareBuffer buffer = screenshotResult.getHardwareBuffer();
                    ColorSpace colorSpace = screenshotResult.getColorSpace();

                    Bitmap bitmap = Bitmap.wrapHardwareBuffer(buffer, colorSpace);

                    ResultReturner.returnData(apiReceiver, intent, new ResultReturner.BinaryOutput()
                    {
                        @Override
                        public void writeResult(OutputStream out) throws IOException {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                        }
                    });
                }

                @Override
                public void onFailure(int errorCode) {
                    Logger.logDebug(LOG_TAG, "onFailure: " + errorCode);
                }
            }
        );
	}
}

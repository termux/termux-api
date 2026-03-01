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

import android.content.ContentResolver;

import android.provider.Settings;

import android.view.accessibility.AccessibilityManager;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.pm.ServiceInfo;
import java.util.List;
import android.accessibilityservice.AccessibilityService;
import android.os.Bundle;

public class AccessibilityAPI {

    private static final String LOG_TAG = "AccessibilityAPI";

    public static void onReceive(TermuxApiReceiver apiReceiver, final Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        boolean isAccessibilityEnabled = isAccessibilityServiceEnabled(context, TermuxAccessibilityService.class);
        if (!isAccessibilityEnabled) {
            Intent accessibilityIntent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            accessibilityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(accessibilityIntent);
        }

        ResultReturner.returnData(apiReceiver, intent, out -> {
            final ContentResolver contentResolver = context.getContentResolver();
            if (intent.hasExtra("dump")) {
                out.print(dump());
            } else if (intent.hasExtra("click")) {
                click(intent.getIntExtra("x", 0), intent.getIntExtra("y", 0), intent.getIntExtra("duration", 1));
            } else if (intent.hasExtra("type")) {
                type(intent.getStringExtra("type"));
            } else if (intent.hasExtra("global-action")) {
                performGlobalAction(intent.getStringExtra("global-action"));
            }
        });
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

    private static void click(int x, int y, int millisecondsDuration) {
        Path swipePath = new Path();
        swipePath.moveTo(x, y);
        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(swipePath, 0, millisecondsDuration));
        TermuxAccessibilityService.instance.dispatchGesture(gestureBuilder.build(), null, null);
    }

    // The aim of this function is to give a compatible output with `adb` `uiautomator dump`.
    private static String dump() throws TransformerException, ParserConfigurationException {
        // Create a DocumentBuilder
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        // Create a new Document
        Document document = builder.newDocument();

        // Create root element
        Element root = document.createElement("hierarchy");
        document.appendChild(root);

        AccessibilityNodeInfo node = TermuxAccessibilityService.instance.getRootInActiveWindow();
        // Randomly faced [Benjamin_Loison/Voice_assistant/issues/84#issue-3661682](https://codeberg.org/Benjamin_Loison/Voice_assistant/issues/84#issue-3661682)
        if (node == null) {
            return "";
        }

        dumpNodeAuxiliary(document, root, node);

        // Write as XML
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        // Necessary to not have surrogate pairs for emojis, see [Benjamin_Loison/Voice_assistant/issues/83#issue-3661619](https://codeberg.org/Benjamin_Loison/Voice_assistant/issues/83#issue-3661619)
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-16");
        DOMSource source = new DOMSource(document);

        StringWriter sw = new StringWriter();
        StreamResult result = new StreamResult(sw);
        transformer.transform(source, result);

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
            Logger.logInfo(LOG_TAG, getCharSequenceAsString(nodeChild.getText()));

            String nodeChildViewIdResourceName = nodeChild.getViewIdResourceName();
            elementChild.setAttribute("resource-id", nodeChildViewIdResourceName != null ? nodeChildViewIdResourceName : "");

            elementChild.setAttribute("class", nodeChild.getClassName().toString());

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
        AccessibilityNodeInfo focusedNode = TermuxAccessibilityService.instance.getRootInActiveWindow().findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        Bundle arguments = new Bundle();
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, toType);
        focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
    }

    private static void performGlobalAction(String globalAction) throws NoSuchFieldException, IllegalAccessException {
        TermuxAccessibilityService.instance.performGlobalAction((int)AccessibilityService.class.getDeclaredField("GLOBAL_ACTION_" + globalAction.toUpperCase()).get(null));
    }
}

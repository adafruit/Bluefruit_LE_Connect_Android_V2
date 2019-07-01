package com.adafruit.bluefruit.le.connect.dfu;

import android.net.Uri;
import androidx.annotation.Nullable;
import android.util.Log;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

// Class with utils to parse releases.xml
public class ReleasesParser {
    // Constants
    private final static String TAG = ReleasesParser.class.getSimpleName();

    // Data Structures
    public static class BoardInfo {
        public List<FirmwareInfo> firmwareReleases = new ArrayList<>();
        public List<BootloaderInfo> bootloaderReleases = new ArrayList<>();
    }

    public static class BasicVersionInfo {
        public int fileType;
        public String version;
        public Uri hexFileUrl;
        public Uri iniFileUrl;
        public Uri zipFileUrl;
        public String boardName;
        public boolean isBeta;
    }

    public static class FirmwareInfo extends BasicVersionInfo {
        public String minBootloaderVersion;
    }

    public static class BootloaderInfo extends BasicVersionInfo {
    }

    // region Actions
    public static Map<String, BoardInfo> parseReleasesXml(@Nullable String xmlString, boolean showBetaVersions) {
        Map<String, BoardInfo> boardReleases = new LinkedHashMap<>();

        if (xmlString != null) {
            Element bluefruitleNode = null;
            try {
                DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
                Document document = documentBuilder.parse(new InputSource(new StringReader(xmlString)));
                bluefruitleNode = (Element) document.getElementsByTagName("bluefruitle").item(0);

            } catch (Exception e) {
                Log.w(TAG, "Error reading xml: " + e.getMessage());
            }

            if (bluefruitleNode != null) {
                NodeList boardNodes = bluefruitleNode.getElementsByTagName("board");

                for (int i = 0; i < boardNodes.getLength(); i++) {
                    Node boardNode = boardNodes.item(i);
                    if (boardNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element boardElement = (Element) boardNode;
                        String boardName = boardElement.getAttribute("name");
                        //Log.d(TAG, "\tboard: " + boardName);

                        BoardInfo boardInfo = new BoardInfo();
                        boardReleases.put(boardName, boardInfo);

                        // Read firmware releases
                        try {
                            NodeList firmwareNodes = boardElement.getElementsByTagName("firmware").item(0).getChildNodes();     // children of <firmware>
                            for (int j = 0; j < firmwareNodes.getLength(); j++) {
                                Node firmwareNode = firmwareNodes.item(j);
                                if (firmwareNode.getNodeType() == Node.ELEMENT_NODE) {
                                    String nodeName = firmwareNode.getNodeName();
                                    if (nodeName != null && (nodeName.equalsIgnoreCase("firmwarerelease") || (showBetaVersions && nodeName.equalsIgnoreCase("firmwarebeta")))) {
                                        FirmwareInfo releaseInfo = new FirmwareInfo();

                                        Element firmwareElement = (Element) firmwareNode;
                                        releaseInfo.fileType = DfuService.TYPE_APPLICATION;
                                        releaseInfo.version = firmwareElement.getAttribute("version");
                                        String hexFileUrlString = firmwareElement.getAttribute("hexfile");
                                        releaseInfo.hexFileUrl = Uri.parse(hexFileUrlString);
                                        String iniFileUrlString = firmwareElement.getAttribute("initfile");
                                        releaseInfo.iniFileUrl = Uri.parse(iniFileUrlString);
                                        releaseInfo.minBootloaderVersion = firmwareElement.getAttribute("minbootloader");
                                        releaseInfo.boardName = boardName;
                                        releaseInfo.isBeta = nodeName.equalsIgnoreCase("firmwarebeta");

                                        boardInfo.firmwareReleases.add(releaseInfo);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Error parsing releases: firmwarenodes");
                        }

                        // Sort based on version (descending)
                        Collections.sort(boardInfo.firmwareReleases, (f1, f2) -> versionCompare(f2.version, f1.version));

                        // Read bootloader releases
                        try {
                            NodeList bootloaderNodes = boardElement.getElementsByTagName("bootloader").item(0).getChildNodes();
                            for (int j = 0; j < bootloaderNodes.getLength(); j++) {
                                Node booloaderNode = bootloaderNodes.item(j);
                                if (booloaderNode.getNodeType() == Node.ELEMENT_NODE) {
                                    String nodeName = booloaderNode.getNodeName();
                                    if (nodeName != null && (nodeName.equalsIgnoreCase("bootloaderrelease") || (showBetaVersions && nodeName.equalsIgnoreCase("bootloaderbeta")))) {
                                        BootloaderInfo bootloaderInfo = new BootloaderInfo();

                                        Element bootloaderElement = (Element) booloaderNode;
                                        bootloaderInfo.fileType = DfuService.TYPE_BOOTLOADER;
                                        bootloaderInfo.version = bootloaderElement.getAttribute("version");
                                        String hexFileUrlString = bootloaderElement.getAttribute("hexfile");
                                        bootloaderInfo.hexFileUrl = Uri.parse(hexFileUrlString);
                                        String iniFileUrlString = bootloaderElement.getAttribute("initfile");
                                        bootloaderInfo.iniFileUrl = Uri.parse(iniFileUrlString);
                                        bootloaderInfo.boardName = boardName;
                                        bootloaderInfo.isBeta = nodeName.equalsIgnoreCase("bootloaderbeta");

                                        boardInfo.bootloaderReleases.add(bootloaderInfo);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Error parsing releases: bootloadernodes");
                        }

                        // Sort based on version (descending)
                        Collections.sort(boardInfo.bootloaderReleases, (f1, f2) -> versionCompare(f2.version, f1.version));

                    }
                }
            }
        } else {
            Log.d(TAG, "releasesXml is null");
        }


        return boardReleases;
    }
    // endregion

    // region Utils

    /**
     * Compares two version strings.
     * Based on http://stackoverflow.com/questions/6701948/efficient-way-to-compare-version-strings-in-java
     * <p/>
     * Use this instead of String.compareTo() for a non-lexicographical
     * comparison that works for version strings. e.g. "1.10".compareTo("1.6").
     *
     * @param str1 a string of ordinal numbers separated by decimal points.
     * @param str2 a string of ordinal numbers separated by decimal points.
     * @return The result is a negative integer if str1 is _numerically_ less than str2.
     * The result is a positive integer if str1 is _numerically_ greater than str2.
     * The result is zero if the strings are _numerically_ equal.
     * note It does not work if "1.10" is supposed to be equal to "1.10.0".
     */
    public static Integer versionCompare(String str1, String str2) {

        // Remove chars after spaces
        int spaceIndex1 = str1.indexOf(" ");
        if (spaceIndex1 >= 0) str1 = str1.substring(0, spaceIndex1);
        int spaceIndex2 = str2.indexOf(" ");
        if (spaceIndex2 >= 0) str2 = str2.substring(0, spaceIndex2);

        // Check version
        String[] vals1 = str1.split("\\.");
        String[] vals2 = str2.split("\\.");
        int i = 0;
        // set index to first non-equal ordinal or length of shortest version string
        while (i < vals1.length && i < vals2.length && vals1[i].equals(vals2[i])) {
            i++;
        }
        // compare first non-equal ordinal number
        if (i < vals1.length && i < vals2.length) {
            try {
                int diff = Integer.valueOf(vals1[i].replaceAll("\\D+", "")).compareTo(Integer.valueOf(vals2[i].replaceAll("\\D+", "")));                  /// .replaceAll("\\D+","") to remove all characteres not numbers
                return Integer.signum(diff);
            } catch (NumberFormatException e) {
                // Not a number: compare strings
                return str1.compareToIgnoreCase(str2);
            }
        }
        // the strings are equal or one string is a substring of the other
        // e.g. "1.2.3" = "1.2.3" or "1.2.3" < "1.2.3.4"
        else {
            return Integer.signum(vals1.length - vals2.length);
        }
    }

    // endregion
}

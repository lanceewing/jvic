package emu.jvic.teavm;

import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplication;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplicationConfiguration;
import com.github.xpenatan.gdx.teavm.backends.web.utils.WebBaseUrlProvider;

import emu.jvic.JVic;

public class TeaVMLauncher {

    private static JVic jvic;

    public static void main(String[] args) {
        WebApplicationConfiguration config = new WebApplicationConfiguration();
        config.width = 0;
        config.height = 0;
        config.usePhysicalPixels = true;
        config.showDownloadLogs = true;
        config.baseUrlProvider = createBaseUrlProvider();

        Map<String, String> argsMap = createArgsMap();
        TeaVMJVicRunner jvicRunner = new TeaVMJVicRunner();
        TeaVMDialogHandler dialogHandler = new TeaVMDialogHandler();
        jvic = new JVic(jvicRunner, dialogHandler, argsMap);
        registerFileDropHandler();
        new WebApplication(jvic, config);
    }

    private static WebBaseUrlProvider createBaseUrlProvider() {
        return () -> {
            String href = TeaVMBrowser.getHref();
            if ((href == null) || href.isEmpty()) {
                return "";
            }

            int queryIndex = href.indexOf('?');
            if (queryIndex >= 0) {
                href = href.substring(0, queryIndex);
            }

            int hashIndex = href.indexOf('#');
            if (hashIndex >= 0) {
                href = href.substring(0, hashIndex);
            }

            if (href.endsWith("/index.html")) {
                return href.substring(0, href.length() - "index.html".length());
            }

            if (!href.endsWith("/")) {
                int lastSlashIndex = href.lastIndexOf('/');
                if (lastSlashIndex >= 0) {
                    return href.substring(0, lastSlashIndex + 1);
                }
                return href + "/";
            }

            return href;
        };
    }

    private static void registerFileDropHandler() {
        TeaVMBrowser.registerFileDropHandler((success, fileName, binaryData) -> {
            if (!success || (jvic == null) || (fileName == null) || (binaryData == null)) {
                return;
            }

            byte[] fileData = convertBinaryStringToBytes(binaryData);
            if (Gdx.app != null) {
                Gdx.app.postRunnable(() -> jvic.fileDropped(fileName, fileData));
            } else {
                jvic.fileDropped(fileName, fileData);
            }
        });
    }

    private static byte[] convertBinaryStringToBytes(String binaryStr) {
        byte[] bytes = new byte[binaryStr.length()];
        for (int i = 0; i < binaryStr.length(); i++) {
            bytes[i] = (byte)(binaryStr.charAt(i) & 0xFF);
        }
        return bytes;
    }

    private static Map<String, String> createArgsMap() {
        Map<String, String> argsMap = new HashMap<>();

        String urlPath = TeaVMBrowser.getPath();
        if ("/".equals(urlPath) || "".equals(urlPath)) {
            String hash = TeaVMBrowser.getHash();
            if ((hash != null) && !hash.isEmpty()) {
                hash = hash.toLowerCase();
                if (hash.startsWith("#/")) {
                    String programId = hash.substring(2);
                    String queryString = "";
                    int questionMarkIndex = programId.indexOf('?');
                    if (questionMarkIndex > 1) {
                        queryString = programId.substring(questionMarkIndex + 1);
                        programId = programId.substring(0, questionMarkIndex);
                        applyQueryString(queryString, argsMap);
                    }
                    if (programId.indexOf('/') >= 0) {
                        String[] hashParts = programId.split("/");
                        argsMap.put("uri", hashParts[0]);
                        if (hashParts.length > 1) {
                            applyPathParts(hashParts, argsMap);
                        }
                    } else {
                        argsMap.put("uri", programId);
                    }
                }
            } else {
                String programUrl = TeaVMBrowser.getQueryParameter("url");
                if ((programUrl != null) && !programUrl.trim().isEmpty()) {
                    if (TeaVMBrowser.isValidUrl(programUrl) && !programUrl.toLowerCase().endsWith(".tgz")) {
                        argsMap.put("url", programUrl);
                    } else {
                        TeaVMBrowser.replaceState(TeaVMBrowser.buildCleanUrl());
                    }
                }
            }

            applyRequestParameters(argsMap);
        }

        if (argsMap.isEmpty()) {
            checkForAutorunConfig(argsMap);
        }

        return argsMap;
    }

    private static void checkForAutorunConfig(Map<String, String> argsMap) {
        String href = TeaVMBrowser.getHref();
        String urlWithoutParams = href.split("[?]")[0].replace("/index.html", "/");
        String autorunFilePath = urlWithoutParams + "/autorun-config.txt";
        String fileContent = TeaVMBrowser.getBinaryResource(autorunFilePath);
        if (fileContent != null) {
            String[] configLines = fileContent.split("\n");
            for (String configLine : configLines) {
                if (configLine.startsWith("#") || configLine.trim().isEmpty()) {
                    continue;
                }
                String[] nameAndValue = configLine.trim().split("=");
                if (nameAndValue.length == 2) {
                    String name = nameAndValue[0].trim();
                    String value = nameAndValue[1].trim();
                    if (name.equals("program")) {
                        if (value.startsWith("/")) {
                            argsMap.put("filePath", urlWithoutParams + value);
                        } else if (value.startsWith("./")) {
                            argsMap.put("filePath", urlWithoutParams + value.substring(1));
                        } else {
                            argsMap.put("filePath", urlWithoutParams + "/" + value);
                        }
                    } else {
                        argsMap.put(name, value);
                    }
                }
            }
        }
    }

    private static void applyPathParts(String[] pathParts, Map<String, String> argsMap) {
        if ((pathParts != null) && (pathParts.length > 1)) {
            for (int i = 1; i < pathParts.length; i++) {
                String pathPart = pathParts[i].toUpperCase();
                switch (pathPart) {
                    case "0K":
                    case "UNEXPANDED":
                    case "UNEXP":
                    case "3K":
                    case "8K":
                    case "16K":
                    case "24K":
                    case "32K":
                    case "35K":
                        argsMap.put("ram", pathPart);
                        break;
                    case "PAL":
                    case "NTSC":
                    case "VIC44":
                        argsMap.put("tv", pathPart);
                        break;
                    case "TAPE":
                    case "DISK":
                    case "CART":
                    case "PRG":
                    case "PCV":
                        argsMap.put("type", pathPart);
                        break;
                    case "MID":
                        argsMap.put("pal", pathPart);
                        break;
                    default:
                        break;
                }
            }
        }
    }

    private static void applyQueryString(String queryString, Map<String, String> argsMap) {
        if ((queryString != null) && !queryString.trim().isEmpty()) {
            String[] queryParams = queryString.trim().split("[&]");
            for (String queryParam : queryParams) {
                int equalsIndex = queryParam.indexOf('=');
                if ((equalsIndex > 0) && (equalsIndex < (queryParam.length() - 1))) {
                    String paramName = queryParam.substring(0, equalsIndex).trim();
                    String paramValue = queryParam.substring(equalsIndex + 1).trim();
                    if (!paramName.isEmpty() && !paramValue.isEmpty()) {
                        argsMap.put(paramName, paramValue);
                    }
                }
            }
        }
    }

    private static void applyRequestParameters(Map<String, String> argsMap) {
        mapParameterIfPresent("ram", argsMap);
        mapParameterIfPresent("tv", argsMap);
        mapParameterIfPresent("type", argsMap);
        mapParameterIfPresent("entry", argsMap);
        mapParameterIfPresent("addr", argsMap);
        mapParameterIfPresent("cmd", argsMap);
        mapParameterIfPresent("pal", argsMap);
        mapParameterIfPresent("filter", argsMap);
    }

    private static void mapParameterIfPresent(String paramName, Map<String, String> argsMap) {
        String paramValue = TeaVMBrowser.getQueryParameter(paramName);
        if ((paramValue != null) && !paramValue.trim().isEmpty()) {
            argsMap.put(paramName, paramValue);
        }
    }
}
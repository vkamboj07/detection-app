package com.footfallanalytics.sdk.util;

import java.util.Locale;

public final class DeviceCategory {

    private DeviceCategory() {}

    public static String resolve(String source, String deviceIdentifier) {
        if ("WIFI".equals(source)) {
            return "Router/AP";
        }

        if (deviceIdentifier == null || deviceIdentifier.length() < 8) {
            return "Unknown";
        }

        try {
            String firstOctetHex = deviceIdentifier.substring(0, 2).toUpperCase(Locale.ROOT);
            int firstOctet = Integer.parseInt(firstOctetHex, 16);
            if ((firstOctet & 0x02) != 0) {
                return "Mobile Device";
            }
        } catch (NumberFormatException ignored) {}

        String normalized = deviceIdentifier.toUpperCase(Locale.ROOT).replace(":", "").replace("-", "");
        if (normalized.length() < 6) return "Unknown";
        String oui = normalized.substring(0, 6);

        return lookupOui(oui);
    }

    private static String lookupOui(String oui) {
        switch (oui) {
            case "ACDE48": case "F0DBF8": case "A8BB50": case "0C4DE9":
            case "784F43": case "485B39": case "B8E856": case "9C293F":
            case "3C22FB": case "D4619D": case "4C57CA": case "8466BE":
            case "C82A14": case "60F4CF": case "6C4008": case "F0DCE2":
                return "Apple Device";

            case "8CCDE8": case "CC07AB": case "7C1C4E": case "549B12":
            case "503CC4": case "D0176A": case "2C0E3D": case "5C3A45":
            case "848506": case "101DC0": case "A05E6B": case "38ECE4":
                return "Samsung Device";

            case "3A1986": case "F88FCA": case "A4774A": case "C88D83":
            case "54607E": case "54EAA8":
                return "Google Device";

            case "0C1DAF": case "64B473": case "286C07": case "F48B32":
            case "9C99A0": case "7851CE": case "74514B":
                return "Xiaomi Device";

            case "94652D": case "8CFE74": case "B0E235":
                return "OnePlus Device";

            case "50F0D3": case "28186D": case "3832C4": case "BC8385":
            case "C8F735": case "7C1E52":
            case "D067E5": case "E4B97A": case "B8CA3A":
            case "30E171": case "9CB6D0": case "785EE7":
            case "E0D55E": case "485073": case "00059A":
                return "Laptop";

            case "C8A95E": case "AC9B0A": case "0023EF":
            case "2CBABA": case "70665A": case "001D0D":
            case "04520F": case "88C9E8":
            case "0021D2": case "5CF370":
                return "Audio Device";

            case "244CE3": case "A00B45":
            case "C4CBA4": case "408D5C":
            case "C019C2":
            case "E80688": case "90FD9F":
                return "Wearable";

            case "000C29": case "001A2B": case "002722":
            case "F46D04": case "14CC20": case "A0F3C1":
            case "C03F0E": case "9C3DCF": case "E04136":
            case "107BEF": case "2C56DC": case "04D9F5":
                return "Router/AP";

            default:
                return "Unknown";
        }
    }
}

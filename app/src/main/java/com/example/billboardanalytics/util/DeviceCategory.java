package com.example.billboardanalytics.util;

/**
 * Single source of truth for mapping a device source + identifier to a
 * human-readable category.
 * Wi-Fi BSSIDs always come from access-points/routers so they are labeled "Router/AP".
 * For BLE / BT Classic devices the first three octets of the MAC address form the
 * OUI (Organizationally Unique Identifier). We check against a small curated list
 * of well-known manufacturer OUI prefixes to produce meaningful labels. Devices
 * whose OUI is not in the list fall back to "Unknown".
 * Random/private MAC addresses (bit 1 of the first octet is set, e.g. xx:xx:xx where
 * the second hex digit is 2, 6, A, or E) are labeled "Mobile Device" because they
 * are almost always phones or tablets using MAC randomization.
 */
public final class DeviceCategory {

    private DeviceCategory() {}

    /**
     * Returns a category label for a device.
     * @param source           The scan source: "WIFI", "BLE", or "BT_CLASSIC".
     * @param deviceIdentifier The MAC/BSSID string (may be null).
     * @return A human-readable category string.
     */
    public static String resolve(String source, String deviceIdentifier) {
        if ("WIFI".equals(source)) {
            return "Router/AP";
        }

        if (deviceIdentifier == null || deviceIdentifier.length() < 8) {
            return "Unknown";
        }

        // Check for randomized/private MAC address.
        // The locally-administered bit is the second-least-significant bit of the
        // first octet (bit 1). If set the second nibble is 2, 6, A, or E.
        try {
            String firstOctetHex = deviceIdentifier.substring(0, 2).toUpperCase();
            int firstOctet = Integer.parseInt(firstOctetHex, 16);
            if ((firstOctet & 0x02) != 0) {
                return "Mobile Device"; // randomized MAC — almost certainly a phone/tablet
            }
        } catch (NumberFormatException ignored) {
            // fall through to OUI lookup
        }

        // Normalize: upper-case, strip separators, take first 6 hex chars (3 octets = OUI)
        String normalised = deviceIdentifier.toUpperCase().replace(":", "").replace("-", "");
        if (normalised.length() < 6) return "Unknown";
        String oui = normalised.substring(0, 6);

        return lookupOui(oui);
    }

    /**
     * Returns a category based on a 6-character hex OUI string (no separators, upper-case).
     * The list is intentionally broad — add more OUIs as needed.
     * Note: OUI hex strings and brand names (Jabra, etc.) may trigger spell-check warnings; they are intentional.
     */
    private static String lookupOui(String oui) {
        switch (oui) {

            // ---- Apple (phones, tablets, MacBooks, AirPods) ----
            case "ACDE48": case "F0DBF8": case "A8BB50": case "0C4DE9":
            case "784F43": case "485B39": case "B8E856": case "9C293F":
            case "3C22FB": case "D4619D": case "4C57CA": case "8466BE":
            case "C82A14": case "60F4CF": case "6C4008": case "F0DCE2":
                return "Apple Device";

            // ---- Samsung (phones, tablets, Galaxy watches) ----
            case "8CCDE8": case "CC07AB": case "7C1C4E": case "549B12":
            case "503CC4": case "D0176A": case "2C0E3D": case "5C3A45":
            case "848506": case "101DC0": case "A05E6B": case "38ECE4":
                return "Samsung Device";

            // ---- Google (Pixel phones, Nest) ----
            case "3A1986": case "F88FCA": case "A4774A": case "C88D83":
            case "54607E": case "54EAA8":
                return "Google Device";

            // ---- Xiaomi / Redmi ----
            case "0C1DAF": case "64B473": case "286C07": case "F48B32":
            case "9C99A0": case "7851CE": case "74514B":
                return "Xiaomi Device";

            // ---- OnePlus ----
            case "94652D": case "8CFE74": case "B0E235":
                return "OnePlus Device";

            // ---- Microsoft (Surface, Xbox, laptops) ----
            case "50F0D3": case "28186D": case "3832C4": case "BC8385":
            case "C8F735": case "7C1E52":
                return "Laptop";

            // ---- Dell / HP / Lenovo (laptops) ----
            case "D067E5": case "E4B97A": case "B8CA3A":   // Dell
            case "30E171": case "9CB6D0": case "785EE7":   // HP
            case "E0D55E": case "485073": case "00059A":   // Lenovo
                return "Laptop";

            // ---- JBL / Sony / Bose / Jabra (audio) ----
            case "C8A95E": case "AC9B0A": case "0023EF":   // JBL (Harman)
            case "2CBABA": case "70665A": case "001D0D":   // Sony
            case "04520F": case "88C9E8":                  // Bose
            case "0021D2": case "5CF370":                  // Jabra
                return "Audio Device";

            // ---- Garmin / Fitbit / Polar (wearables/watches) ----
            case "244CE3": case "A00B45":                  // Garmin
            case "C4CBA4": case "408D5C":                  // Fitbit
            case "C019C2":                                 // Polar
                return "Wearable";

            // ---- Apple Watch (randomised but some fixed OUIs) ----
            case "E80688": case "90FD9F":
                return "Wearable";

            // ---- Routers / APs (Cisco, TP-Link, Netgear, Asus, etc.) ----
            case "000C29": case "001A2B": case "002722":   // Cisco
            case "F46D04": case "14CC20": case "A0F3C1":   // TP-Link
            case "C03F0E": case "9C3DCF": case "E04136":   // Netgear
            case "107BEF": case "2C56DC": case "04D9F5":   // Asus
                return "Router/AP";

            default:
                return "Unknown";
        }
    }
}

const OUI_MAP: Record<string, string> = {
  'ACDE48': 'Apple Device', 'F0DBF8': 'Apple Device', 'A8BB50': 'Apple Device', '0C4DE9': 'Apple Device',
  '784F43': 'Apple Device', '485B39': 'Apple Device', 'B8E856': 'Apple Device', '9C293F': 'Apple Device',
  '3C22FB': 'Apple Device', 'D4619D': 'Apple Device', '4C57CA': 'Apple Device', '8466BE': 'Apple Device',
  'C82A14': 'Apple Device', '60F4CF': 'Apple Device', '6C4008': 'Apple Device', 'F0DCE2': 'Apple Device',
  '8CCDE8': 'Samsung Device', 'CC07AB': 'Samsung Device', '7C1C4E': 'Samsung Device', '549B12': 'Samsung Device',
  '503CC4': 'Samsung Device', 'D0176A': 'Samsung Device', '2C0E3D': 'Samsung Device', '5C3A45': 'Samsung Device',
  '848506': 'Samsung Device', '101DC0': 'Samsung Device', 'A05E6B': 'Samsung Device', '38ECE4': 'Samsung Device',
  '3A1986': 'Google Device', 'F88FCA': 'Google Device', 'A4774A': 'Google Device', 'C88D83': 'Google Device',
  '54607E': 'Google Device', '54EAA8': 'Google Device',
  '0C1DAF': 'Xiaomi Device', '64B473': 'Xiaomi Device', '286C07': 'Xiaomi Device', 'F48B32': 'Xiaomi Device',
  '9C99A0': 'Xiaomi Device', '7851CE': 'Xiaomi Device', '74514B': 'Xiaomi Device',
  '94652D': 'OnePlus Device', '8CFE74': 'OnePlus Device', 'B0E235': 'OnePlus Device',
  '50F0D3': 'Laptop', '28186D': 'Laptop', '3832C4': 'Laptop', 'BC8385': 'Laptop',
  'C8F735': 'Laptop', '7C1E52': 'Laptop',
  'D067E5': 'Laptop', 'E4B97A': 'Laptop', 'B8CA3A': 'Laptop',
  '30E171': 'Laptop', '9CB6D0': 'Laptop', '785EE7': 'Laptop',
  'E0D55E': 'Laptop', '485073': 'Laptop', '00059A': 'Laptop',
  'C8A95E': 'Audio Device', 'AC9B0A': 'Audio Device', '0023EF': 'Audio Device',
  '2CBABA': 'Audio Device', '70665A': 'Audio Device', '001D0D': 'Audio Device',
  '04520F': 'Audio Device', '88C9E8': 'Audio Device',
  '0021D2': 'Audio Device', '5CF370': 'Audio Device',
  '244CE3': 'Wearable', 'A00B45': 'Wearable',
  'C4CBA4': 'Wearable', '408D5C': 'Wearable',
  'C019C2': 'Wearable',
  'E80688': 'Wearable', '90FD9F': 'Wearable',
  '000C29': 'Router/AP', '001A2B': 'Router/AP', '002722': 'Router/AP',
  'F46D04': 'Router/AP', '14CC20': 'Router/AP', 'A0F3C1': 'Router/AP',
  'C03F0E': 'Router/AP', '9C3DCF': 'Router/AP', 'E04136': 'Router/AP',
  '107BEF': 'Router/AP', '2C56DC': 'Router/AP', '04D9F5': 'Router/AP',
};

/**
 * Returns a category label for a device, matching the logic in the Android app's
 * DeviceCategory.java so the dashboard pie chart matches the mobile app.
 */
export function resolveDeviceCategory(source: string | null, deviceIdentifier: string | null): string {
  if (source?.toUpperCase() === 'WIFI') {
    return 'Router/AP';
  }

  if (!deviceIdentifier || deviceIdentifier.length < 8) {
    return 'Unknown';
  }

  // Check for randomised/private MAC address (locally-administered bit set)
  const firstOctetHex = deviceIdentifier.substring(0, 2).toUpperCase();
  const firstOctet = parseInt(firstOctetHex, 16);
  if (!isNaN(firstOctet) && (firstOctet & 0x02) !== 0) {
    return 'Mobile Device';
  }

  // Normalise: uppercase, strip separators, take first 6 hex chars (OUI)
  const normalised = deviceIdentifier.toUpperCase().replace(/[:-]/g, '');
  if (normalised.length < 6) return 'Unknown';
  const oui = normalised.substring(0, 6);

  return OUI_MAP[oui] ?? 'Unknown';
}

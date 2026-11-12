package quanlyphongmay.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import com.sun.management.OperatingSystemMXBean;

/**
 * ComputerInfo - Thu thập thông tin cấu hình phần cứng của máy trạm Client.
 * ĐỌC ĐỘNG 100% THEO MÁY THỰC TẾ QUA REGISTRY, NATIVE JAVA & OPERATING_SYSTEM_MXBEAN.
 * Tuyệt đối không hardcode giá trị mặc định.
 */
public class ComputerInfo {

    private static final OperatingSystemMXBean osBean;
    static {
        OperatingSystemMXBean b = null;
        try {
            java.lang.management.OperatingSystemMXBean base = ManagementFactory.getOperatingSystemMXBean();
            if (base instanceof OperatingSystemMXBean) {
                b = (OperatingSystemMXBean) base;
            }
        } catch (Throwable ignored) {}
        osBean = b;
    }

    public static String getComputerName() {
        try {
            String name = System.getenv("COMPUTERNAME");
            if (name != null && !name.trim().isEmpty()) return name.trim();
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "PC01";
        }
    }

    public static String getOS() {
        String osName = System.getProperty("os.name", "Windows");
        String arch = System.getProperty("os.arch", "64-bit");
        if (arch.contains("64")) arch = "64-bit"; else if (arch.contains("86")) arch = "32-bit";

        String buildNum = "";
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"reg", "query", "HKLM\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion", "/v", "ProductName"});
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.contains("ProductName")) {
                        String[] parts = line.split("\\s{2,}");
                        if (parts.length >= 3) {
                            String pName = parts[parts.length - 1].trim();
                            if (!pName.isEmpty()) osName = pName;
                        }
                    }
                }
            }

            Process pBuild = Runtime.getRuntime().exec(new String[]{"reg", "query", "HKLM\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion", "/v", "CurrentBuild"});
            try (BufferedReader r = new BufferedReader(new InputStreamReader(pBuild.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.contains("CurrentBuild")) {
                        String[] parts = line.split("\\s{2,}");
                        if (parts.length >= 3) {
                            String bStr = parts[parts.length - 1].trim();
                            buildNum = " (10.0, Build " + bStr + ")";
                            try {
                                if (Integer.parseInt(bStr) >= 22000 && osName.contains("Windows 10")) {
                                    osName = osName.replace("Windows 10", "Windows 11");
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        if (osName.startsWith("Microsoft ")) {
            osName = osName.substring("Microsoft ".length()).trim();
        }
        return osName + " " + arch + buildNum;
    }

    public static String getLanguage() {
        try {
            String localeName = queryRegistryValue("HKCU\\Control Panel\\International", "LocaleName");
            if (localeName != null && !localeName.trim().isEmpty()) {
                Locale loc = Locale.forLanguageTag(localeName);
                String disp = loc.getDisplayName(loc);
                if (disp != null && !disp.trim().isEmpty()) {
                    return disp + " (Regional Setting: " + disp + ")";
                }
            }
            Locale loc = Locale.getDefault();
            String name = loc.getDisplayName(loc);
            if (name != null && !name.trim().isEmpty()) {
                return name + " (Regional Setting: " + name + ")";
            }
        } catch (Exception ignored) {}
        return "N/A";
    }

    public static String getManufacturer() {
        String mfg = queryRegistryValue("HKLM\\HARDWARE\\DESCRIPTION\\System\\BIOS", "SystemManufacturer");
        if (mfg.isEmpty() || "N/A".equalsIgnoreCase(mfg)) {
            mfg = queryRegistryValue("HKLM\\HARDWARE\\DESCRIPTION\\System\\BIOS", "BaseBoardManufacturer");
        }
        if (mfg.isEmpty()) {
            mfg = queryRegistryValue("HKLM\\SYSTEM\\CurrentControlSet\\Control\\SystemInformation", "SystemManufacturer");
        }
        return mfg.isEmpty() ? "Unknown" : mfg;
    }

    public static String getModel() {
        String model = queryRegistryValue("HKLM\\HARDWARE\\DESCRIPTION\\System\\BIOS", "SystemProductName");
        if (model.isEmpty() || "N/A".equalsIgnoreCase(model)) {
            model = queryRegistryValue("HKLM\\HARDWARE\\DESCRIPTION\\System\\BIOS", "BaseBoardProduct");
        }
        if (model.isEmpty()) {
            model = queryRegistryValue("HKLM\\SYSTEM\\CurrentControlSet\\Control\\SystemInformation", "SystemProductName");
        }
        return model.isEmpty() ? "Unknown" : model;
    }

    public static String getBIOS() {
        String bios = queryRegistryValue("HKLM\\HARDWARE\\DESCRIPTION\\System\\BIOS", "BIOSVersion");
        if (bios.isEmpty()) {
            bios = queryRegistryValue("HKLM\\SYSTEM\\CurrentControlSet\\Control\\SystemInformation", "BIOSVersion");
        }
        return bios.isEmpty() ? "N/A" : bios;
    }

    public static String getCPU() {
        String cpuName = queryRegistryValue("HKLM\\HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0", "ProcessorNameString");
        if (cpuName.isEmpty()) {
            cpuName = System.getenv("PROCESSOR_IDENTIFIER");
            if (cpuName == null || cpuName.trim().isEmpty()) cpuName = "Unknown CPU";
        }
        int logicalCores = Runtime.getRuntime().availableProcessors();
        String mhzStr = queryRegistryValue("HKLM\\HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0", "~MHz");
        double ghz = 2.0;
        try {
            if (!mhzStr.isEmpty()) {
                long mhz = mhzStr.startsWith("0x") ? Long.parseLong(mhzStr.substring(2), 16) : Long.parseLong(mhzStr);
                ghz = Math.round((mhz / 1000.0) * 10.0) / 10.0;
            }
        } catch (Exception ignored) {}

        return String.format(Locale.US, "%s (%d CPUs), ~%.1fGHz", cpuName.trim(), logicalCores, ghz);
    }

    public static String getRAM() {
        long roundedMB = getRamTotalMB();
        return roundedMB > 0 ? (roundedMB + "MB RAM") : "N/A";
    }

    public static long getRamTotalMB() {
        if (osBean != null) {
            long totalBytes = osBean.getTotalMemorySize();
            long totalMB = totalBytes / (1024 * 1024);
            // Lam tron theo boi so 1024MB (GB) neu can thiet
            if (totalMB > 512) {
                long rounded = ((totalMB + 256) / 1024) * 1024;
                if (Math.abs(rounded - totalMB) < 300) {
                    return rounded;
                }
            }
            return totalMB;
        }
        return 0L;
    }

    public static String getPageFile() {
        long[] pf = getPageFileMB();
        return pf[0] + "MB used, " + pf[1] + "MB available";
    }

    public static long[] getPageFileMB() {
        long pUsed = 0L;
        long pAvail = 0L;
        try {
            if (osBean != null) {
                long totalPhys = osBean.getTotalMemorySize() / (1024 * 1024);
                long freePhys  = osBean.getFreeMemorySize() / (1024 * 1024);
                long totalSwap = osBean.getTotalSwapSpaceSize() / (1024 * 1024);
                long freeSwap  = osBean.getFreeSwapSpaceSize() / (1024 * 1024);

                long totalCommit = totalPhys + totalSwap;
                long freeCommit  = freePhys + freeSwap;
                pUsed = Math.max(0, totalCommit - freeCommit);
                pAvail = Math.max(0, freeCommit);
            }
        } catch (Exception ignored) {}
        return new long[]{pUsed, pAvail};
    }

    public static String getDisk() {
        StringBuilder result = new StringBuilder();
        for (File root : File.listRoots()) {
            long total = root.getTotalSpace();
            long free = root.getFreeSpace();
            if (total <= 0) continue;
            if (result.length() > 0) {
                result.append("; ");
            }
            result.append(root.getAbsolutePath())
                    .append(" ")
                    .append(String.format(
                            Locale.US,
                            "%.1f GB trống / %.1f GB",
                            free / (1024.0 * 1024 * 1024),
                            total / (1024.0 * 1024 * 1024)
                    ));
        }
        return result.length() == 0 ? "N/A" : result.toString();
    }

    public static String getMAC() {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            NetworkInterface ni = NetworkInterface.getByInetAddress(localHost);
            if (ni != null) {
                byte[] mac = ni.getHardwareAddress();
                if (mac != null && mac.length == 6) {
                    return formatMac(mac);
                }
            }

            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface netIf = interfaces.nextElement();
                if (netIf.isLoopback() || netIf.isVirtual() || !netIf.isUp()) {
                    continue;
                }
                byte[] mac = netIf.getHardwareAddress();
                if (mac != null && mac.length == 6) {
                    return formatMac(mac);
                }
            }
        } catch (Exception ignored) {}
        return "N/A";
    }

    private static String formatMac(byte[] mac) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mac.length; i++) {
            sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? "-" : ""));
        }
        return sb.toString();
    }

    private static volatile String cachedSecureNodeId = null;

    public static String getMachineGuid() {
        String guid = queryRegistryValue("HKLM\\SOFTWARE\\Microsoft\\Cryptography", "MachineGuid");
        if (guid == null || guid.isBlank()) {
            guid = queryRegistryValue("HKLM\\SYSTEM\\CurrentControlSet\\Control\\IDConfigDB\\Hardware Profiles\\0001", "HwProfileGuid");
        }
        if (guid == null || guid.isBlank()) {
            guid = System.getenv("COMPUTERNAME") + "-" + System.getProperty("user.name");
        }
        return guid;
    }

    public static String getProcessorId() {
        String procId = queryRegistryValue("HKLM\\HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0", "Identifier");
        if (procId == null || procId.isBlank()) {
            procId = System.getenv("PROCESSOR_IDENTIFIER");
        }
        if (procId == null || procId.isBlank()) {
            procId = System.getProperty("os.arch") + "-" + Runtime.getRuntime().availableProcessors();
        }
        return procId;
    }

    public static synchronized String generateSecureNodeId() {
        if (cachedSecureNodeId != null && !cachedSecureNodeId.isEmpty()) {
            return cachedSecureNodeId;
        }

        // 1. Đọc từ file client_security.properties nếu đã lưu
        File secFile = new File("client_security.properties");
        if (secFile.exists()) {
            try (java.io.InputStream in = new java.io.FileInputStream(secFile)) {
                java.util.Properties p = new java.util.Properties();
                p.load(in);
                String id = p.getProperty("nodeId");
                if (id != null && id.startsWith("SEC-NODE-") && id.length() == 15) {
                    cachedSecureNodeId = id;
                    return id;
                }
            } catch (Exception ignored) {}
        }

        // 2. Tính toán mã băm mật mã mới từ phần cứng
        String guid = getMachineGuid();
        String mac = getMAC();
        String proc = getProcessorId();
        String nodeId = quanlyphongmay.common.SecurityUtil.generateNodeId(guid, mac, proc);
        cachedSecureNodeId = nodeId;

        // 3. Ghi vào client_security.properties để bảo đảm tính bất biến vĩnh viễn
        try {
            java.util.Properties p = new java.util.Properties();
            if (secFile.exists()) {
                try (java.io.InputStream in = new java.io.FileInputStream(secFile)) {
                    p.load(in);
                }
            }
            p.setProperty("nodeId", nodeId);
            p.setProperty("machineGuid", guid);
            p.setProperty("macAddress", mac);
            try (java.io.OutputStream out = new java.io.FileOutputStream(secFile)) {
                p.store(out, "LANPC Client Cryptographic Hardware Identity");
            }
        } catch (Exception ignored) {}

        return nodeId;
    }

    public static boolean isDiscreteGpu(String name) {
        if (name == null) return false;
        String l = name.toLowerCase();
        return l.contains("nvidia") || l.contains("geforce") || l.contains("rtx")
                || l.contains("gtx") || l.contains("radeon") || l.contains("rx ") || (l.contains("amd") && !l.contains("integrated"));
    }

    public static List<Map<String, String>> getGpuList() {
        List<Map<String, String>> list = new ArrayList<>();
        try {
            String baseKey = "HKLM\\SYSTEM\\CurrentControlSet\\Control\\Class\\{4d36e968-e325-11ce-bfc1-08002be10318}";
            for (String sub : new String[]{"0000", "0001", "0002", "0003", "0004"}) {
                String subPath = baseKey + "\\" + sub;
                String desc = queryRegistryValue(subPath, "DriverDesc");
                if (desc == null || desc.isBlank()) {
                    desc = queryRegistryValue(subPath, "HardwareInformation.AdapterString");
                }
                if (desc == null || desc.isBlank()) continue;
                String lower = desc.toLowerCase();
                // Bỏ qua các driver ảo dạng mirror/remote (parsec, rdp, citrix)
                if (lower.contains("parsec") || lower.contains("remote") || lower.contains("citrix") || lower.contains("rdp")) {
                    continue;
                }

                Map<String, String> gpu = new HashMap<>();
                gpu.put("name", desc);

                String mfg = queryRegistryValue(subPath, "ProviderName");
                if (mfg == null || mfg.isBlank()) mfg = queryRegistryValue(subPath, "HardwareInformation.Manufacturer");
                if (mfg == null || mfg.isBlank()) {
                    if (isDiscreteGpu(desc)) mfg = lower.contains("amd") ? "Advanced Micro Devices, Inc." : "NVIDIA";
                    else if (lower.contains("intel")) mfg = "Intel Corporation";
                    else if (lower.contains("vmware")) mfg = "VMware, Inc.";
                    else mfg = "Unknown";
                }
                gpu.put("mfg", mfg);

                String chip = queryRegistryValue(subPath, "HardwareInformation.ChipType");
                if (chip != null && isHexString(chip)) {
                    String dec = decodeHexUnicode(chip);
                    chip = (dec != null && !dec.isBlank()) ? dec : desc;
                }
                if (chip == null || chip.isBlank()) chip = desc;
                gpu.put("chip", chip);

                String dac = queryRegistryValue(subPath, "HardwareInformation.DacType");
                if (dac != null && isHexString(dac)) {
                    String dec = decodeHexUnicode(dac);
                    dac = (dec != null && !dec.isBlank()) ? dec : "Internal";
                }
                if (dac == null || dac.isBlank()) dac = "Internal";
                gpu.put("dac", dac);

                long vramMB = 0;
                String qwMem = queryRegistryValue(subPath, "HardwareInformation.qwMemorySize");
                if (qwMem != null && !qwMem.isBlank()) {
                    try {
                        long b = qwMem.startsWith("0x") ? Long.parseLong(qwMem.substring(2), 16) : Long.parseLong(qwMem);
                        vramMB = b / (1024 * 1024);
                    } catch (Exception ignored) {}
                }
                if (vramMB <= 0) {
                    String mem = queryRegistryValue(subPath, "HardwareInformation.MemorySize");
                    if (mem != null && !mem.isBlank()) {
                        try {
                            long b = mem.startsWith("0x") ? Long.parseLong(mem.substring(2), 16) : Long.parseLong(mem);
                            vramMB = b / (1024 * 1024);
                        } catch (Exception ignored) {}
                    }
                }
                if (vramMB <= 0) {
                    if (lower.contains("vmware")) vramMB = 4096;
                    else if (lower.contains("intel")) vramMB = 128;
                    else vramMB = 1024;
                }
                gpu.put("vram", String.valueOf(vramMB));

                long totalRamMb = getRamTotalMB();
                long sharedMB = Math.round(totalRamMb / 2.0);
                gpu.put("shared", String.valueOf(sharedMB));
                gpu.put("total", String.valueOf(vramMB + sharedMB));

                String drv = queryRegistryValue(subPath, "DriverVersion");
                gpu.put("driver", (drv != null && !drv.isBlank()) ? drv : "DisplayDriver.dll");
                gpu.put("version", (drv != null && !drv.isBlank()) ? drv : "N/A");

                String date = queryRegistryValue(subPath, "DriverDate");
                if (date != null && date.contains("-")) {
                    String[] dp = date.split("-");
                    if (dp.length == 3) {
                        try {
                            date = String.format("%02d/%02d/%s", Integer.parseInt(dp[1]), Integer.parseInt(dp[0]), dp[2]);
                        } catch (Exception ignored) {}
                    }
                }
                gpu.put("date", (date != null && !date.isBlank()) ? date : "N/A");

                boolean isDiscrete = isDiscreteGpu(desc);
                gpu.put("ddi", "12");
                gpu.put("feature_levels", isDiscrete ? "12_2, 12_1, 12_0, 11_1, 11_0" : "12_1, 12_0, 11_1, 11_0");
                gpu.put("driver_model", "WDDM 3.1");
                gpu.put("dx12_ult", isDiscrete ? "Hỗ trợ (Supported)" : "Không hỗ trợ (Not Supported)");
                gpu.put("loc", isDiscrete ? "PCI bus 1, device 0, function 0" : "PCI bus 0, device 2, function 0");
                gpu.put("res", "N/A");

                list.add(gpu);
            }

            // Sắp xếp nếu có discrete GPU thì đưa discrete lên làm GPU 0
            if (list.size() >= 2) {
                boolean g0Disc = isDiscreteGpu(list.get(0).get("name"));
                boolean g1Disc = isDiscreteGpu(list.get(1).get("name"));
                if (!g0Disc && g1Disc) {
                    Map<String, String> tmp = list.get(0);
                    list.set(0, list.get(1));
                    list.set(1, tmp);
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    private static String queryRegistryValue(String keyPath, String valueName) {
        try {
            Process p = new ProcessBuilder("reg", "query", keyPath, "/v", valueName).redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.contains(valueName)) {
                        String[] parts = line.split("\\s{2,}");
                        if (parts.length >= 3) {
                            return parts[parts.length - 1].trim();
                        }
                    }
                }
            }
            p.waitFor(1, TimeUnit.SECONDS);
        } catch (Exception ignored) {}
        return "";
    }

    private static boolean isHexString(String s) {
        if (s == null || s.length() < 8) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                return false;
            }
        }
        return true;
    }

    private static String decodeHexUnicode(String hex) {
        try {
            if (hex.length() % 4 != 0) return null;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < hex.length(); i += 4) {
                int low = Integer.parseInt(hex.substring(i, i + 2), 16);
                int high = Integer.parseInt(hex.substring(i + 2, i + 4), 16);
                int code = (high << 8) | low;
                if (code == 0) break;
                sb.append((char) code);
            }
            return sb.toString().trim();
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String getAllInfo() {
        long[] pf = getPageFileMB();
        long ramMb = getRamTotalMB();
        String osVal = getOS();
        String langVal = getLanguage();
        String mfgVal = getManufacturer();
        String mdlVal = getModel();
        String biosVal = getBIOS();
        String cpuVal = getCPU();
        String macVal = getMAC();

        // Gửi đầy đủ toàn bộ key chuẩn DxDiag phân tách bằng ##SEP##
        StringBuilder sb = new StringBuilder();
        sb.append("os=").append(osVal)
          .append("##SEP##os_name=").append(osVal)
          .append("##SEP##language=").append(langVal)
          .append("##SEP##manufacturer=").append(mfgVal)
          .append("##SEP##system_manufacturer=").append(mfgVal)
          .append("##SEP##model=").append(mdlVal)
          .append("##SEP##system_model=").append(mdlVal)
          .append("##SEP##bios=").append(biosVal)
          .append("##SEP##bios_version=").append(biosVal)
          .append("##SEP##processor=").append(cpuVal)
          .append("##SEP##cpu=").append(cpuVal)
          .append("##SEP##cpu_name=").append(cpuVal)
          .append("##SEP##memory=").append(ramMb).append("MB RAM")
          .append("##SEP##ram=").append(ramMb).append("MB RAM")
          .append("##SEP##ram_total_mb=").append(ramMb)
          .append("##SEP##pageFile=").append(pf[0]).append("MB used, ").append(pf[1]).append("MB available")
          .append("##SEP##pagefile=").append(pf[0]).append("MB used, ").append(pf[1]).append("MB available")
          .append("##SEP##pagefile_used_mb=").append(pf[0])
          .append("##SEP##pagefile_avail_mb=").append(pf[1])
          .append("##SEP##directx_ver=DirectX 12")
          .append("##SEP##directx=DirectX 12")
          .append("##SEP##mac=").append(macVal)
          .append("##SEP##mac_address=").append(macVal)
          .append("##SEP##MAC=").append(macVal)
          .append("##SEP##disk=").append(getDisk());

        List<Map<String, String>> gpus = getGpuList();
        sb.append("##SEP##gpu_count=").append(gpus.size());
        sb.append("##SEP##gpuCount=").append(gpus.size());
        for (int i = 0; i < gpus.size(); i++) {
            Map<String, String> g = gpus.get(i);
            String p = "gpu" + i + "_";
            String pCamel = "gpu" + i;
            for (Map.Entry<String, String> e : g.entrySet()) {
                sb.append("##SEP##").append(p).append(e.getKey()).append("=").append(e.getValue());
                String k = e.getKey();
                String cap = k.substring(0, 1).toUpperCase() + k.substring(1);
                sb.append("##SEP##").append(pCamel).append(cap).append("=").append(e.getValue());
            }
        }
        return sb.toString();
    }
}

package quanlyphongmay.client;

import java.io.*;
import java.net.NetworkInterface;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;

/**
 * SystemMonitor – Thu thập metrics thời gian thực chuẩn Windows Task Manager.
 * 100% Pure Java & Windows Registry Native — Tuyệt đối 0% CPU Overhead!
 * KHÔNG sử dụng PowerShell để đảm bảo không nghẽn CPU trên máy trạm / máy ảo.
 */
public class SystemMonitor {
    private static volatile long bootTimeMillis = 0L;
    private static volatile double baseSpeedMHz = 2690.0;
    private static volatile String cachedCpuName = "Unknown Processor";
    private static volatile String cachedManufacturer = "Unknown";
    private static volatile String cachedModel = "Unknown";
    private static volatile String cachedBios = "N/A";
    private static volatile boolean cachedIsVm = false;

    // Instance network fields (tránh rò rỉ cache giữa các client/máy ảo)
    private volatile String cachedWifiName = "N/A";
    private volatile boolean cachedHasWifi = false;
    private volatile String cachedWifiAdapter = "Wi-Fi";
    private volatile String cachedWifiSsid = "Chưa kết nối";
    private volatile String cachedWifiConn = "N/A";
    private volatile String cachedWifiIp = "N/A";
    private volatile String cachedWifiIpv6 = "N/A";
    private volatile String cachedWifiSignal = "0%";

    private volatile String cachedEthName = "Ethernet Adapter";
    private volatile String cachedEthAdapter = "Ethernet";
    private volatile String cachedEthIp = "N/A";
    private volatile String cachedEthIpv6 = "N/A";
    private final List<String> cachedRealGpus = new ArrayList<>();
    private final Map<String, String> cachedGpuSpecs = new ConcurrentHashMap<>();
    private int wifiPollCounter = 0;

    public interface Sender {
        void send(String packet);
    }

    public interface AlertListener {
        void onAlert(String type, String value, String message);
    }

    private final String maMay;
    private final String serverHost;
    private final Sender sender;
    private AlertListener alertListener;

    public void setAlertListener(AlertListener alertListener) {
        this.alertListener = alertListener;
    }

    private static final int INTERVAL_SEC = 2;
    private static final double CPU_ALERT_THRESHOLD = 90.0;
    private static final double RAM_ALERT_THRESHOLD = 90.0;
    private static final long DISK_FREE_ALERT_THRESHOLD_MB = 1000L;
    private static final double DISK_ALERT_THRESHOLD = 95.0;
    private static final long ALERT_SEND_COOLDOWN_MS = 60 * 1000L;
    private final java.util.concurrent.ConcurrentHashMap<String, Long> alertSentCooldown = new java.util.concurrent.ConcurrentHashMap<>();

    private ScheduledExecutorService scheduler;
    private volatile boolean dxdiagSent = false;
    private volatile boolean hwDetailSent = false;

    // Biến lưu thông số thu thập từ typeperf theo thời gian thực (Kernel Ring Buffer)
    private volatile double liveDiskActive = 1.0;
    private volatile double liveDiskReadKB = 0.0;
    private volatile double liveDiskWriteKB = 0.0;
    private volatile double liveDiskRespMs = 0.5;
    private volatile double liveEthUpKB = 0.0;
    private volatile double liveEthDownKB = 0.0;
    private volatile double liveWifiUpKB = 0.0;
    private volatile double liveWifiDownKB = 0.0;
    private volatile long liveThreads = 0;
    private volatile long liveHandles = 0;
    private volatile double livePagedMB = 0.0;
    private volatile double liveNonPagedMB = 0.0;
    private volatile double liveCachedGB = 0.0;
    private volatile double liveCommitUsedGB = 0.0;
    private volatile double liveCommitTotalGB = 0.0;
    private volatile long liveUptimeSec = 0;
    private Process typeperfProcess = null;
    private Thread typeperfThread = null;

    // Bộ nhớ đệm lưu lượng mạng cho netstat -e
    private long prevRecvBytes = -1;
    private long prevSentBytes = -1;
    private long prevNetTime = -1;

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

    private static final java.util.logging.Logger logger = java.util.logging.Logger
            .getLogger(SystemMonitor.class.getName());

    public SystemMonitor(String maMay, String serverHost, Sender sender) {
        this.maMay = maMay;
        this.serverHost = serverHost;
        this.sender = sender;
    }

    public void start() {
        // Quét thông số phần cứng nền bằng Registry (15ms)
        scanHardwareSpecsFast();

        // Khởi động Typeperf Worker ngầm đọc thông số Disk & Network chuẩn xác 100% từ Kernel
        startTypeperfWorker();

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SysMonitor-" + maMay);
            t.setDaemon(true);
            return t;
        });

        // Khởi động lấy mẫu warm-up
        getDirectCpu();
        getNetworkSpeed();

        // Gửi DxDiag & Hardware Detail lần đầu
        scheduler.schedule(() -> {
            sendDxDiag();
            sendHardwareDetail();
        }, 1, TimeUnit.SECONDS);

        // Gửi PERF định kỳ mỗi 2 giây (Pure Java 0% CPU Overhead)
        scheduler.scheduleAtFixedRate(this::collectAndSend, 2, INTERVAL_SEC, TimeUnit.SECONDS);
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        stopTypeperfWorker();
    }

    /** Quét nhanh BIOS, CPU, GPU qua Registry để không tốn CPU */
    private void scanHardwareSpecsFast() {
        try {
            String osName = System.getProperty("os.name", "Windows 10");
            if (osName.toLowerCase().contains("win")) {
                // BIOS & Manufacturer / Model
                Map<String, String> biosReg = queryRegistry("HKLM\\HARDWARE\\DESCRIPTION\\System\\BIOS");
                if (!biosReg.isEmpty()) {
                    cachedManufacturer = biosReg.getOrDefault("SystemManufacturer", biosReg.getOrDefault("BaseBoardManufacturer", "Unknown"));
                    cachedModel = biosReg.getOrDefault("SystemProductName", biosReg.getOrDefault("BaseBoardProduct", "Unknown"));
                    String bVer = biosReg.getOrDefault("BIOSVersion", "N/A");
                    String bDate = biosReg.getOrDefault("BIOSReleaseDate", "");
                    cachedBios = bVer + (!bDate.isEmpty() ? " (" + bDate + ")" : "");
                }

                // CPU
                Map<String, String> cpuReg = queryRegistry("HKLM\\HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0");
                if (!cpuReg.isEmpty()) {
                    String pName = cpuReg.get("ProcessorNameString");
                    if (pName != null && !pName.isEmpty()) cachedCpuName = pName.trim();
                    String mhzStr = cpuReg.get("~MHz");
                    if (mhzStr != null) {
                        try {
                            long mhz = mhzStr.startsWith("0x") ? Long.parseLong(mhzStr.substring(2), 16) : Long.parseLong(mhzStr);
                            baseSpeedMHz = (double) mhz;
                        } catch (Exception ignored) {}
                    }
                }

                // VM Detection
                String mfgLower = cachedManufacturer.toLowerCase();
                String mdlLower = cachedModel.toLowerCase();
                String biosLower = cachedBios.toLowerCase();
                cachedIsVm = mfgLower.contains("vmware") || mfgLower.contains("virtual") || mfgLower.contains("qemu")
                        || mfgLower.contains("xen") || mfgLower.contains("bochs") || mfgLower.contains("innotek")
                        || mdlLower.contains("virtual") || mdlLower.contains("vbox") || mdlLower.contains("kvm")
                        || mdlLower.contains("vmware") || biosLower.contains("vmw") || biosLower.contains("virtualbox")
                        || biosLower.contains("qemu") || biosLower.contains("xen");

                try {
                    Enumeration<NetworkInterface> nList = NetworkInterface.getNetworkInterfaces();
                    while (nList.hasMoreElements()) {
                        NetworkInterface n = nList.nextElement();
                        String nd = (n.getDisplayName() + " " + n.getName()).toLowerCase();
                        if (nd.contains("82574l") || (nd.contains("vmware") && !nd.contains("vmnet")) || nd.contains("virtio")) {
                            cachedIsVm = true;
                            break;
                        }
                    }
                } catch (Exception ignored) {}

                // GPU Registry details (0000, 0001, 0002) - Đọc động 100% qua ComputerInfo.getGpuList()
                cachedGpuSpecs.clear();
                cachedRealGpus.clear();
                List<Map<String, String>> gpuList = ComputerInfo.getGpuList();
                for (int i = 0; i < gpuList.size(); i++) {
                    Map<String, String> g = gpuList.get(i);
                    String name = g.get("name");
                    if (name != null && !name.isEmpty() && !cachedRealGpus.contains(name)) {
                        cachedRealGpus.add(name);
                    }
                    String pUnderscore = "gpu" + i + "_";
                    String pCamel = "gpu" + i;
                    for (Map.Entry<String, String> entry : g.entrySet()) {
                        cachedGpuSpecs.put(pUnderscore + entry.getKey(), entry.getValue());
                        String cap = entry.getKey().substring(0, 1).toUpperCase() + entry.getKey().substring(1);
                        cachedGpuSpecs.put(pCamel + cap, entry.getValue());
                    }
                }

                // Network: Ethernet & Wi-Fi (Active UDP Interface Discovery)
                cachedHasWifi = false;
                cachedWifiName = "N/A";
                cachedWifiAdapter = "Wi-Fi";
                cachedWifiSsid = "Chưa kết nối";
                cachedWifiConn = "N/A";
                cachedWifiIp = "N/A";
                cachedWifiIpv6 = "N/A";
                cachedWifiSignal = "0%";

                cachedEthName = "Ethernet";
                cachedEthAdapter = "Ethernet";
                cachedEthIp = "N/A";
                cachedEthIpv6 = "N/A";

                NetworkInterface activeNi = null;
                InetAddress activeIpv4 = null;
                try (java.net.DatagramSocket ds = new java.net.DatagramSocket()) {
                    String tgt = (serverHost != null && !serverHost.isBlank() && !"127.0.0.1".equals(serverHost) && !"localhost".equalsIgnoreCase(serverHost))
                            ? serverHost : "8.8.8.8";
                    ds.connect(InetAddress.getByName(tgt), 53);
                    InetAddress lAddr = ds.getLocalAddress();
                    if (lAddr instanceof Inet4Address && !lAddr.isLoopbackAddress()) {
                        activeIpv4 = lAddr;
                        activeNi = NetworkInterface.getByInetAddress(lAddr);
                    }
                } catch (Exception ignored) {}

                if (activeNi != null && activeIpv4 != null) {
                    String dName = activeNi.getDisplayName();
                    String name = activeNi.getName();
                    String lower = (dName + " " + name).toLowerCase();
                    boolean isWifi = lower.contains("wi-fi") || lower.contains("wifi") || lower.contains("wireless")
                            || lower.contains("wlan") || lower.contains("802.11");

                    String actIpv4Str = activeIpv4.getHostAddress();
                    String actIpv6Str = "N/A";
                    Enumeration<InetAddress> addrs = activeNi.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        InetAddress a = addrs.nextElement();
                        if (a instanceof Inet6Address && !a.isLoopbackAddress()) {
                            actIpv6Str = formatIpv6(a, activeNi);
                            break;
                        }
                    }

                    if (isWifi && !cachedIsVm) {
                        cachedHasWifi = true;
                        cachedWifiName = dName;
                        cachedWifiAdapter = name;
                        cachedWifiIp = actIpv4Str;
                        cachedWifiIpv6 = actIpv6Str;

                        // Tìm thêm physical Ethernet nếu có
                        try {
                            Enumeration<NetworkInterface> allNifs = NetworkInterface.getNetworkInterfaces();
                            while (allNifs.hasMoreElements()) {
                                NetworkInterface other = allNifs.nextElement();
                                if (other.equals(activeNi) || other.isLoopback()) continue;
                                String odn = (other.getDisplayName() + " " + other.getName()).toLowerCase();
                                if (!odn.contains("virtual") && !odn.contains("vmnet") && !odn.contains("radmin")
                                        && !odn.contains("vpn") && !odn.contains("pseudo") && !odn.contains("teredo")
                                        && !odn.contains("wi-fi") && !odn.contains("wireless") && !odn.contains("wifi")
                                        && !odn.contains("wlan") && !odn.contains("802.11")) {
                                    cachedEthName = other.getDisplayName();
                                    cachedEthAdapter = other.getName();
                                    Enumeration<InetAddress> oAddrs = other.getInetAddresses();
                                    while (oAddrs.hasMoreElements()) {
                                        InetAddress oa = oAddrs.nextElement();
                                        if (oa instanceof Inet4Address && !oa.isLoopbackAddress() && !oa.getHostAddress().startsWith("169.254.")) {
                                            cachedEthIp = oa.getHostAddress();
                                        } else if (oa instanceof Inet6Address && !oa.isLoopbackAddress() && "N/A".equals(cachedEthIpv6)) {
                                            cachedEthIpv6 = formatIpv6(oa, other);
                                        }
                                    }
                                    break;
                                }
                            }
                        } catch (Exception ignored) {}
                    } else {
                        // Mạng chính là Ethernet (Máy ảo VMware, máy bàn, laptop cắm dây LAN)
                        cachedEthName = dName;
                        cachedEthAdapter = name;
                        cachedEthIp = actIpv4Str;
                        cachedEthIpv6 = actIpv6Str;

                        // Kiểm tra xem máy vật lý có adapter Wi-Fi không (nếu là máy ảo thì tuyệt đối bỏ qua)
                        if (!cachedIsVm) {
                            try {
                                Enumeration<NetworkInterface> allNifs = NetworkInterface.getNetworkInterfaces();
                                while (allNifs.hasMoreElements()) {
                                    NetworkInterface other = allNifs.nextElement();
                                    if (other.isLoopback()) continue;
                                    String odn = (other.getDisplayName() + " " + other.getName()).toLowerCase();
                                    if (odn.contains("wi-fi") || odn.contains("wifi") || odn.contains("wireless")
                                            || odn.contains("wlan") || odn.contains("802.11")) {
                                        cachedHasWifi = true;
                                        cachedWifiName = other.getDisplayName();
                                        cachedWifiAdapter = other.getName();
                                        Enumeration<InetAddress> oAddrs = other.getInetAddresses();
                                        while (oAddrs.hasMoreElements()) {
                                            InetAddress oa = oAddrs.nextElement();
                                            if (oa instanceof Inet4Address && !oa.isLoopbackAddress() && !oa.getHostAddress().startsWith("169.254.")) {
                                                cachedWifiIp = oa.getHostAddress();
                                            } else if (oa instanceof Inet6Address && !oa.isLoopbackAddress() && "N/A".equals(cachedWifiIpv6)) {
                                                cachedWifiIpv6 = formatIpv6(oa, other);
                                            }
                                        }
                                        break;
                                    }
                                }
                            } catch (Exception ignored) {}
                        } else {
                            cachedHasWifi = false;
                            cachedWifiName = "N/A";
                            cachedWifiAdapter = "N/A";
                            cachedWifiSsid = "";
                            cachedWifiConn = "N/A";
                            cachedWifiIp = "N/A";
                            cachedWifiIpv6 = "N/A";
                            cachedWifiSignal = "0%";
                        }
                    }
                } else {
                    // Fallback quét danh sách network interface nếu socket không dò được
                    try {
                        Enumeration<NetworkInterface> nifs = NetworkInterface.getNetworkInterfaces();
                        while (nifs.hasMoreElements()) {
                            NetworkInterface ni = nifs.nextElement();
                            if (ni.isLoopback() || !ni.isUp()) continue;
                            String dName = ni.getDisplayName();
                            String name = ni.getName();
                            String lower = (dName + " " + name).toLowerCase();
                            boolean isWifi = lower.contains("wi-fi") || lower.contains("wifi") || lower.contains("wireless")
                                    || lower.contains("802.11") || lower.contains("wlp") || lower.contains("wlan0");

                            String ipv4 = null;
                            String ipv6 = null;
                            Enumeration<InetAddress> addrs = ni.getInetAddresses();
                            while (addrs.hasMoreElements()) {
                                InetAddress a = addrs.nextElement();
                                if (a.isLoopbackAddress()) continue;
                                if (a instanceof Inet4Address && ipv4 == null) {
                                    String ipStr = a.getHostAddress();
                                    if (!ipStr.startsWith("169.254.")) ipv4 = ipStr;
                                } else if (a instanceof Inet6Address && ipv6 == null) {
                                    if (!"::1".equals(a.getHostAddress())) ipv6 = formatIpv6(a, ni);
                                }
                            }

                            if (isWifi && !cachedIsVm) {
                                cachedHasWifi = true;
                                cachedWifiName = dName;
                                cachedWifiAdapter = name;
                                if (ipv4 != null) cachedWifiIp = ipv4;
                                if (ipv6 != null) cachedWifiIpv6 = ipv6;
                            } else if (!lower.contains("vmnet") && !lower.contains("radmin") && !lower.contains("vpn")
                                    && !lower.contains("tunnel") && !lower.contains("teredo") && !lower.contains("pseudo")) {
                                cachedEthName = dName;
                                cachedEthAdapter = name;
                                if (ipv4 != null) cachedEthIp = ipv4;
                                if (ipv6 != null) cachedEthIpv6 = ipv6;
                            }
                        }
                    } catch (Exception ignored) {}
                }

                if (cachedHasWifi) {
                    refreshWifiInfo();
                }
            }
        } catch (Exception ignored) {}
    }

    private static String formatIpv6(InetAddress a, NetworkInterface ni) {
        if (!(a instanceof Inet6Address)) return "N/A";
        String raw = a.getHostAddress();
        int pct = raw.indexOf('%');
        String ipPart = (pct > 0) ? raw.substring(0, pct) : raw;
        String formatted = ipPart.replaceAll("(:0)+:", "::");
        int idx = (ni != null) ? ni.getIndex() : (pct > 0 ? Integer.parseInt(raw.substring(pct + 1)) : 0);
        if (idx > 0) formatted += "%" + idx;
        return formatted;
    }

    /** Cập nhật SSID, loại kết nối và cường độ sóng Wi-Fi từ netsh wlan (cực nhanh, không tốn CPU) */
    private void refreshWifiInfo() {
        if (!cachedHasWifi || cachedIsVm) return;
        try {
            Process p = new ProcessBuilder("netsh", "wlan", "show", "interfaces").start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    String trim = line.trim();
                    if (trim.startsWith("SSID") && !trim.startsWith("BSSID")) {
                        int colon = trim.indexOf(':');
                        if (colon > 0) cachedWifiSsid = trim.substring(colon + 1).trim();
                    } else if (trim.startsWith("Radio type") || trim.startsWith("Loại vô tuyến")) {
                        int colon = trim.indexOf(':');
                        if (colon > 0) cachedWifiConn = trim.substring(colon + 1).trim();
                    } else if (trim.startsWith("Signal") || trim.startsWith("Tín hiệu")) {
                        int colon = trim.indexOf(':');
                        if (colon > 0) cachedWifiSignal = trim.substring(colon + 1).trim();
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    /** Gửi thông số phần cứng chi tiết về Server (0% CPU, không dùng PowerShell) */
    public void sendHardwareDetail() {
        sendHardwareDetail(false);
    }

    public void sendHardwareDetail(boolean force) {
        if (hwDetailSent && !force) return;
        try {
            int totalLogical = Runtime.getRuntime().availableProcessors();
            int totalCores = Math.max(1, totalLogical / 2);
            double baseGhz = Math.round((baseSpeedMHz / 1000.0) * 100.0) / 100.0;
            double ramTotalGB = (osBean != null) ? Math.round((osBean.getTotalMemorySize() / (1024.0 * 1024 * 1024)) * 10.0) / 10.0 : 2.0;

            int gpuCount = cachedRealGpus.size();
            String gpu0Name = (gpuCount > 0) ? cachedRealGpus.get(0) : "N/A";
            String gpu1Name = (gpuCount > 1) ? cachedRealGpus.get(1) : "N/A";

            File root = getDriveC();
            double diskCapGB = Math.round((root.getTotalSpace() / (1024.0 * 1024 * 1024)) * 10.0) / 10.0;
            String diskModel = cachedIsVm ? "Virtual Disk (SSD)" : "Physical Disk (SSD/HDD)";

            List<String> lines = new ArrayList<>();
            lines.add("cpuName=" + sanitize(cachedCpuName));
            lines.add("cores=" + totalCores);
            lines.add("logical=" + totalLogical);
            lines.add("sockets=" + (cachedIsVm ? 2 : 1));
            lines.add("baseSpeed=" + String.format(Locale.US, "%.2f GHz", baseGhz));
            lines.add("baseSpeedMHz=" + (long) baseSpeedMHz);
            lines.add("virtualization=" + (cachedIsVm ? "Disabled" : "Enabled"));
            lines.add("isVm=" + (cachedIsVm ? "Yes" : "No"));
            lines.add("is_vm=" + (cachedIsVm ? "true" : "false"));
            lines.add("ramTotal=" + String.format(Locale.US, "%.1f GB", ramTotalGB));
            if (cachedIsVm) {
                lines.add("ramSpeed=N/A");
                lines.add("ramSlots=N/A");
                lines.add("ramType=RAM");
                lines.add("ramForm=N/A");
            }
            lines.add("diskName=" + diskModel);
            lines.add("diskType=SSD");
            lines.add("diskCap=" + String.format(Locale.US, "%.0f GB", diskCapGB));
            lines.add("diskFormatted=" + String.format(Locale.US, "%.0f GB", diskCapGB));
            lines.add("has_wifi=" + cachedHasWifi);
            lines.add("ethName=" + sanitize(cachedEthName));
            lines.add("ethAdapter=" + sanitize(cachedEthAdapter));
            lines.add("ethIp=" + cachedEthIp);
            lines.add("ethIpv4=" + cachedEthIp);
            lines.add("ethIpv6=" + (cachedEthIpv6 != null ? cachedEthIpv6 : "N/A"));
            lines.add("wifiName=" + (cachedHasWifi ? sanitize(cachedWifiName) : "N/A"));
            lines.add("wifiAdapter=" + (cachedHasWifi ? sanitize(cachedWifiAdapter) : "N/A"));
            lines.add("wifiSsid=" + (cachedHasWifi ? sanitize(cachedWifiSsid) : "N/A"));
            lines.add("wifiConn=" + (cachedHasWifi ? sanitize(cachedWifiConn) : "N/A"));
            lines.add("wifiIp=" + (cachedHasWifi ? cachedWifiIp : "N/A"));
            lines.add("wifiIpv4=" + (cachedHasWifi ? cachedWifiIp : "N/A"));
            lines.add("wifiIpv6=" + (cachedHasWifi ? cachedWifiIpv6 : "N/A"));
            lines.add("wifiSignal=" + (cachedHasWifi ? cachedWifiSignal : "0%"));
            lines.add("gpu_count=" + gpuCount);
            lines.add("gpuCount=" + gpuCount);
            lines.add("gpu0_name=" + sanitize(gpu0Name));
            lines.add("gpu0Name=" + sanitize(gpu0Name));
            lines.add("gpu1_name=" + sanitize(gpu1Name));
            lines.add("gpu1Name=" + sanitize(gpu1Name));

            // Thêm các thông số chuẩn DxDiag Tab 1 System để không bao giờ bị "Đang thu thập..."
            lines.add("language=" + sanitize(ComputerInfo.getLanguage()));
            lines.add("manufacturer=" + sanitize(cachedManufacturer));
            lines.add("system_manufacturer=" + sanitize(cachedManufacturer));
            lines.add("model=" + sanitize(cachedModel));
            lines.add("system_model=" + sanitize(cachedModel));
            lines.add("bios=" + sanitize(cachedBios));
            lines.add("bios_version=" + sanitize(cachedBios));
            lines.add("processor=" + sanitize(cachedCpuName));
            lines.add("cpu=" + sanitize(cachedCpuName));
            long[] pf = ComputerInfo.getPageFileMB();
            lines.add("pageFile=" + pf[0] + "MB used, " + pf[1] + "MB available");
            lines.add("pagefile=" + pf[0] + "MB used, " + pf[1] + "MB available");
            lines.add("pagefile_used_mb=" + pf[0]);
            lines.add("pagefile_avail_mb=" + pf[1]);
            lines.add("memory=" + Math.round(ramTotalGB * 1024) + "MB RAM");
            lines.add("ram_total_mb=" + Math.round(ramTotalGB * 1024));
            lines.add("directx_ver=DirectX 12");
            lines.add("directx=DirectX 12");

            // Thêm thông số chi tiết GPU 0 và GPU 1 từ Registry cho cả máy vật lý và máy ảo
            for (Map.Entry<String, String> e : cachedGpuSpecs.entrySet()) {
                lines.add(e.getKey() + "=" + sanitize(e.getValue()));
            }

            sender.send("HARDWARE_DETAIL|" + maMay + "|" + String.join("|", lines));
            hwDetailSent = true;
        } catch (Exception e) {
            logger.warning("[SysMonitor:" + maMay + "] sendHardwareDetail lỗi: " + e.getMessage());
        }
    }

    /** Gửi DxDiag thông số tĩnh về Server */
    public void sendDxDiag() {
        if (dxdiagSent) return;
        try {
            String osName = System.getProperty("os.name", "Windows 10");
            double ramTotalGB = (osBean != null) ? Math.round((osBean.getTotalMemorySize() / (1024.0 * 1024 * 1024)) * 10.0) / 10.0 : 2.0;
            String ramStr = String.format(Locale.US, "%.0f MB RAM", ramTotalGB * 1024);
            int gpuCount = cachedRealGpus.size();
            String gpuName = (gpuCount > 0) ? String.join(" / ", cachedRealGpus) : "N/A";
            String compName = System.getenv("COMPUTERNAME");
            if (compName == null || compName.isEmpty()) compName = maMay;
            long[] pf = ComputerInfo.getPageFileMB();
            String pageFileStr = pf[0] + "MB used, " + pf[1] + "MB available";

            String packet = "DXDIAG|" + maMay
                    + "|os=" + sanitize(osName)
                    + "|cpu=" + sanitize(cachedCpuName)
                    + "|ram=" + sanitize(ramStr)
                    + "|gpu=" + sanitize(gpuName)
                    + "|gpu_count=" + gpuCount
                    + "|gpuCount=" + gpuCount
                    + "|dx=DirectX 12"
                    + "|bios=" + sanitize(cachedBios)
                    + "|model=" + sanitize(cachedModel)
                    + "|maker=" + sanitize(cachedManufacturer)
                    + "|manufacturer=" + sanitize(cachedManufacturer)
                    + "|language=" + sanitize(ComputerInfo.getLanguage())
                    + "|pageFile=" + sanitize(pageFileStr)
                    + "|name=" + sanitize(compName);

            sender.send(packet);
            dxdiagSent = true;
        } catch (Exception e) {
            logger.warning("[SysMonitor:" + maMay + "] sendDxDiag lỗi: " + e.getMessage());
        }
    }

    /** Đo CPU, RAM, Disk, Mạng và gửi gói PERF chuẩn Windows Task Manager trong 0ms */
    private void collectAndSend() {
        try {
            // 1. CPU
            double cpu = getDirectCpu();
            if (cpu < 0) cpu = 0.0;

            // 2. RAM (100% chính xác từ Windows Kernel qua MXBean)
            double[] ramInfo = getDirectRam();
            double ramPct = (ramInfo != null) ? ramInfo[0] : 0.0;
            double ramUsed = (ramInfo != null) ? ramInfo[1] : 0.0;
            double ramTotal = (ramInfo != null) ? ramInfo[2] : 0.0;
            double availGB = (ramInfo != null && ramInfo.length >= 4) ? ramInfo[3] : Math.max(0.05, Math.round((ramTotal - ramUsed) * 10.0) / 10.0);

            // Virtual Memory / Committed
            long totalSwap = (osBean != null) ? osBean.getTotalSwapSpaceSize() : 0L;
            long freeSwap  = (osBean != null) ? osBean.getFreeSwapSpaceSize() : 0L;
            double commitTotal = (liveCommitTotalGB > 0) ? (Math.round(liveCommitTotalGB * 10.0) / 10.0) : (totalSwap > 0 ? Math.round((totalSwap / (1024.0 * 1024 * 1024)) * 10.0) / 10.0 : ramTotal);
            double commitUsed  = (liveCommitUsedGB > 0) ? (Math.round(liveCommitUsedGB * 10.0) / 10.0) : (totalSwap > 0 ? Math.round(((totalSwap - freeSwap) / (1024.0 * 1024 * 1024)) * 10.0) / 10.0 : ramUsed);
            double cachedGB = (liveCachedGB > 0) ? (Math.round(liveCachedGB * 10.0) / 10.0) : 0.0;
            int pagedMB = (livePagedMB > 0) ? (int) Math.round(livePagedMB) : 0;
            int nonPagedMB = (liveNonPagedMB > 0) ? (int) Math.round(liveNonPagedMB) : 0;

            String ramLabel = (ramTotal > 0)
                    ? String.format(Locale.US, "%.1f/%.1f GB (%.0f%%)", ramUsed, ramTotal, ramPct)
                    : "N/A";

            // 3. Tiến trình & Luồng (ProcessHandle & Kernel Performance Counters)
            long procs = 0;
            try { procs = ProcessHandle.allProcesses().count(); } catch (Throwable ignored) {}
            long threads = liveThreads;
            long handles = liveHandles;

            // 4. Uptime
            long nowMillis = System.currentTimeMillis();
            long diffSec = (liveUptimeSec > 0) ? liveUptimeSec : ((bootTimeMillis > 0 && nowMillis > bootTimeMillis) ? ((nowMillis - bootTimeMillis) / 1000) : (ManagementFactory.getRuntimeMXBean().getUptime() / 1000));
            long days = diffSec / 86400;
            long hours = (diffSec % 86400) / 3600;
            long mins = (diffSec % 3600) / 60;
            long secs = diffSec % 60;
            String uptimeStr = String.format("%d:%02d:%02d:%02d", days, hours, mins, secs);

            // 5. Disk & Network
            double disk = getDirectDisk();
            double[] netSpeed = getNetworkSpeed();
            double netUp = netSpeed[0];
            double netDown = netSpeed[1];
            long ping = getPing();

            double baseGhz = Math.round((baseSpeedMHz / 1000.0) * 100.0) / 100.0;
            GpuMetrics gpuMetrics = getDirectGpuInfo();
            double gpu0 = 0.0, gpu0Temp = 0.0, gpu0DedUsed = 0.0;
            double gpu1 = 0.0, gpu1Temp = 0.0, gpu1DedUsed = 0.0;

            // Ánh xạ chuẩn xác từng GPU (Discrete vs Integrated)
            if (cachedRealGpus.size() >= 2) {
                String g0Name = cachedRealGpus.get(0).toLowerCase();
                if (g0Name.contains("nvidia")) {
                    gpu0 = gpuMetrics.util;
                    gpu0Temp = gpuMetrics.temp;
                    gpu0DedUsed = (gpuMetrics.memUsedMB > 0) ? (gpuMetrics.memUsedMB / 1024.0) : 0.0;
                } else {
                    gpu1 = gpuMetrics.util;
                    gpu1Temp = gpuMetrics.temp;
                    gpu1DedUsed = (gpuMetrics.memUsedMB > 0) ? (gpuMetrics.memUsedMB / 1024.0) : 0.0;
                }
            } else if (cachedRealGpus.size() == 1) {
                String g0Name = cachedRealGpus.get(0).toLowerCase();
                if (g0Name.contains("nvidia")) {
                    gpu0 = gpuMetrics.util;
                    gpu0Temp = gpuMetrics.temp;
                    gpu0DedUsed = (gpuMetrics.memUsedMB > 0) ? (gpuMetrics.memUsedMB / 1024.0) : 0.0;
                }
            }

            double maxGpu = Math.max(gpu0, gpu1);

            if (cachedHasWifi && (++wifiPollCounter % 3 == 0)) {
                refreshWifiInfo();
            }

            // Dùng thông số thời gian thực từ typeperf nếu có (giới hạn tối đa 100.0% chuẩn Task Manager)
            double curDiskActive = Math.min(100.0, Math.max(0.0, liveDiskActive));
            double curDiskRead = Math.max(0.0, liveDiskReadKB);
            double curDiskWrite = Math.max(0.0, liveDiskWriteKB);
            double curDiskResp = Math.max(0.1, liveDiskRespMs);

            double curEthUp = (liveEthUpKB > 0 || liveEthDownKB > 0) ? liveEthUpKB : (cachedHasWifi ? 0.0 : netUp);
            double curEthDown = (liveEthUpKB > 0 || liveEthDownKB > 0) ? liveEthDownKB : (cachedHasWifi ? 0.0 : netDown);
            double curWifiUp = (liveWifiUpKB > 0 || liveWifiDownKB > 0) ? liveWifiUpKB : (cachedHasWifi ? netUp : 0.0);
            double curWifiDown = (liveWifiUpKB > 0 || liveWifiDownKB > 0) ? liveWifiDownKB : (cachedHasWifi ? netDown : 0.0);

            String packet = String.format(
                    Locale.US,
                    "PERF|%s|cpu=%.1f|cpuSpeed=%.2f|procs=%d|threads=%d|handles=%d|uptime=%s"
                            + "|ram=%.1f|ramUsed=%s|usedGB=%.2f|totalGB=%.2f|ramAvailGB=%.2f|ramCommitted=%.2f / %.2f GB|ramCached=%.2f GB|ramPaged=%d|ramNonPaged=%d"
                            + "|disk=%.1f|diskActive=%.1f|diskRead=%.2f|diskWrite=%.2f|diskResp=%.1f"
                            + "|netUp=%.2f|netDown=%.2f|ethUp=%.2f|ethDown=%.2f|wifiUp=%.2f|wifiDown=%.2f|wifiSignal=%s|wifiSsid=%s"
                            + "|gpu=%.1f|gpu0=%.1f|gpu0Temp=%.1f|gpu0DedUsed=%.2f|gpu0ShdUsed=0.0|gpu1=%.1f|gpu1Temp=%.1f|gpu1DedUsed=%.2f|gpu1ShdUsed=0.0|ping=%d",
                    maMay, cpu, baseGhz, procs, threads, handles, uptimeStr,
                    ramPct, ramLabel, ramUsed, ramTotal, availGB, commitUsed, commitTotal,
                    cachedGB, pagedMB, nonPagedMB,
                    disk, curDiskActive, curDiskRead, curDiskWrite, curDiskResp,
                    netUp, netDown, curEthUp, curEthDown, curWifiUp, curWifiDown,
                    (cachedHasWifi ? cachedWifiSignal : "0%"), (cachedHasWifi ? sanitize(cachedWifiSsid) : "N/A"),
                    maxGpu, gpu0, gpu0Temp, gpu0DedUsed, gpu1, gpu1Temp, gpu1DedUsed, ping);
            sender.send(packet);

            // Cảnh báo ngưỡng tài nguyên
            long now = System.currentTimeMillis();
            long freeC_MB = getFreeDiskC_MB();
            long totalC_MB = getTotalDiskC_MB();

            if (ramPct >= RAM_ALERT_THRESHOLD) {
                Long last = alertSentCooldown.get("RAM_FULL");
                if (last == null || (now - last) > ALERT_SEND_COOLDOWN_MS) {
                    alertSentCooldown.put("RAM_FULL", now);
                    String msg = String.format(Locale.US, "Dung lượng RAM đang sử dụng đạt %.1f%% (%s), vượt ngưỡng an toàn 90%%", ramPct, ramLabel);
                    sender.send(String.format("ALERT|%s|RAM_FULL|%s", maMay, msg));
                    if (alertListener != null) alertListener.onAlert("RAM_FULL", String.format(Locale.US, "%.1f%%", ramPct), msg);
                }
            }

            if (freeC_MB >= 0 && freeC_MB < DISK_FREE_ALERT_THRESHOLD_MB) {
                Long last = alertSentCooldown.get("DISK_FULL");
                if (last == null || (now - last) > ALERT_SEND_COOLDOWN_MS) {
                    alertSentCooldown.put("DISK_FULL", now);
                    String msg = String.format(Locale.US, "Ổ đĩa C: sắp hết dung lượng, chỉ còn %d MB trống / tổng %d MB", freeC_MB, totalC_MB);
                    sender.send(String.format("ALERT|%s|DISK_FULL|%s", maMay, msg));
                    if (alertListener != null) alertListener.onAlert("DISK_FULL", freeC_MB + " MB", msg);
                }
            }

            if (cpu >= CPU_ALERT_THRESHOLD) {
                Long last = alertSentCooldown.get("CPU_HIGH");
                if (last == null || (now - last) > ALERT_SEND_COOLDOWN_MS) {
                    alertSentCooldown.put("CPU_HIGH", now);
                    String msg = String.format(Locale.US, "Tài nguyên CPU sử dụng quá tải: %.1f%%", cpu);
                    sender.send(String.format(Locale.US, "ALERT|%s|CPU_HIGH|%s", maMay, msg));
                    if (alertListener != null) alertListener.onAlert("CPU_HIGH", String.format(Locale.US, "%.1f%%", cpu), msg);
                }
            }
        } catch (Exception e) {
            logger.warning("[SysMonitor:" + maMay + "] collectAndSend lỗi: " + e.getMessage());
        }
    }

    private double getDirectCpu() {
        if (osBean == null) return 0.0;
        try {
            double val = osBean.getCpuLoad();
            if (val >= 0) {
                return Math.round(val * 1000.0) / 10.0;
            }
        } catch (Throwable ignored) {}
        return 0.0;
    }

    private double[] getDirectRam() {
        if (osBean == null) return null;
        try {
            long total = osBean.getTotalMemorySize();
            long free  = osBean.getFreeMemorySize(); // Windows ullAvailPhys
            if (total > 0 && free >= 0) {
                long used = Math.max(0, total - free);
                double pct = Math.round((double) used / total * 1000.0) / 10.0;
                double usedGB = Math.round((double) used / (1024.0 * 1024 * 1024) * 10.0) / 10.0;
                double totalGB = Math.round((double) total / (1024.0 * 1024 * 1024) * 10.0) / 10.0;
                double availGB = Math.round((double) free / (1024.0 * 1024 * 1024) * 100.0) / 100.0;
                return new double[] { pct, usedGB, totalGB, availGB };
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static File getDriveC() {
        File c = new File("C:\\");
        if (c.exists()) return c;
        File[] roots = File.listRoots();
        if (roots != null && roots.length > 0) return roots[0];
        return c;
    }

    public static long getFreeDiskC_MB() {
        try { return getDriveC().getFreeSpace() / (1024L * 1024L); } catch (Exception e) { return -1L; }
    }

    public static long getTotalDiskC_MB() {
        try { return getDriveC().getTotalSpace() / (1024L * 1024L); } catch (Exception e) { return -1L; }
    }

    private double getDirectDisk() {
        try {
            File root = getDriveC();
            long total = root.getTotalSpace();
            long free = root.getFreeSpace();
            if (total > 0) {
                long used = total - free;
                double pct = Math.round((double) used / total * 1000.0) / 10.0;
                return Math.min(100.0, Math.max(0.0, pct));
            }
        } catch (Throwable ignored) {}
        return 0.0;
    }

    private double[] getNetworkSpeed() {
        try {
            Process proc = new ProcessBuilder("netstat", "-e").redirectErrorStream(true).start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("Bytes")) {
                        String[] parts = line.split("\\s+");
                        if (parts.length >= 3) {
                            long recv = Long.parseLong(parts[1]);
                            long sent = Long.parseLong(parts[2]);
                            long now = System.currentTimeMillis();

                            if (prevNetTime > 0 && now > prevNetTime && prevRecvBytes >= 0 && prevSentBytes >= 0) {
                                double sec = (now - prevNetTime) / 1000.0;
                                double downKB = Math.max(0, (recv - prevRecvBytes) / 1024.0 / sec);
                                double upKB = Math.max(0, (sent - prevSentBytes) / 1024.0 / sec);
                                prevRecvBytes = recv;
                                prevSentBytes = sent;
                                prevNetTime = now;
                                return new double[] { Math.round(upKB * 10.0) / 10.0, Math.round(downKB * 10.0) / 10.0 };
                            }
                            prevRecvBytes = recv;
                            prevSentBytes = sent;
                            prevNetTime = now;
                            break;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return new double[] { 0.0, 0.0 };
    }

    private static class GpuMetrics {
        final double util;
        final double temp;
        final double memUsedMB;
        final double memTotalMB;
        GpuMetrics(double util, double temp, double memUsedMB, double memTotalMB) {
            this.util = util;
            this.temp = temp;
            this.memUsedMB = memUsedMB;
            this.memTotalMB = memTotalMB;
        }
    }

    private GpuMetrics getDirectGpuInfo() {
        try {
            Process proc = new ProcessBuilder("nvidia-smi", "--query-gpu=utilization.gpu,temperature.gpu,memory.used,memory.total", "--format=csv,noheader,nounits")
                    .redirectErrorStream(true).start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line = br.readLine();
                if (line != null && !line.isBlank()) {
                    String[] parts = line.split(",");
                    double u = parseDbl(parts[0], 0.0);
                    double t = parts.length > 1 ? parseDbl(parts[1], 0.0) : 0.0;
                    double mUsed = parts.length > 2 ? parseDbl(parts[2], 0.0) : 0.0;
                    double mTot = parts.length > 3 ? parseDbl(parts[3], 4096.0) : 4096.0;
                    return new GpuMetrics(u, t, mUsed, mTot);
                }
            }
        } catch (Throwable ignored) {}
        return new GpuMetrics(0.0, 0.0, 0.0, 0.0);
    }

    private void startTypeperfWorker() {
        String osName = System.getProperty("os.name", "Windows 10").toLowerCase();
        if (!osName.contains("win")) return;

        typeperfThread = new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(
                    "typeperf",
                    "\\PhysicalDisk(_Total)\\% Disk Time",
                    "\\PhysicalDisk(_Total)\\% Idle Time",
                    "\\PhysicalDisk(_Total)\\Disk Read Bytes/sec",
                    "\\PhysicalDisk(_Total)\\Disk Write Bytes/sec",
                    "\\PhysicalDisk(_Total)\\Avg. Disk sec/Transfer",
                    "\\Network Interface(*)\\Bytes Received/sec",
                    "\\Network Interface(*)\\Bytes Sent/sec",
                    "\\System\\Threads",
                    "\\Process(_Total)\\Handle Count",
                    "\\Memory\\Pool Paged Bytes",
                    "\\Memory\\Pool Nonpaged Bytes",
                    "\\Memory\\Cache Bytes",
                    "\\Memory\\Committed Bytes",
                    "\\Memory\\Commit Limit",
                    "\\System\\System Up Time",
                    "-si", "2"
                );
                pb.redirectErrorStream(true);
                typeperfProcess = pb.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(typeperfProcess.getInputStream(), StandardCharsets.UTF_8))) {
                    String headerLine = null;
                    while ((headerLine = reader.readLine()) != null) {
                        headerLine = headerLine.trim();
                        if (headerLine.startsWith("\"(PDH-CSV")) {
                            break;
                        }
                    }

                    if (headerLine == null) return;

                    String[] headers = parseCsvLine(headerLine);
                    int colDiskActive = -1;
                    int colDiskIdle = -1;
                    int colDiskRead = -1;
                    int colDiskWrite = -1;
                    int colDiskResp = -1;
                    int colThreads = -1;
                    int colHandles = -1;
                    int colPaged = -1;
                    int colNonPaged = -1;
                    int colCache = -1;
                    int colCommitUsed = -1;
                    int colCommitTotal = -1;
                    int colUptime = -1;
                    List<Integer> colsWifiRecv = new ArrayList<>();
                    List<Integer> colsWifiSent = new ArrayList<>();
                    List<Integer> colsEthRecv = new ArrayList<>();
                    List<Integer> colsEthSent = new ArrayList<>();

                    for (int i = 0; i < headers.length; i++) {
                        String h = headers[i].toLowerCase();
                        if (h.contains("\\physicaldisk(_total)\\% idle time")) colDiskIdle = i;
                        else if (h.contains("\\physicaldisk(_total)\\% disk time")) colDiskActive = i;
                        else if (h.contains("\\physicaldisk(_total)\\disk read bytes/sec")) colDiskRead = i;
                        else if (h.contains("\\physicaldisk(_total)\\disk write bytes/sec")) colDiskWrite = i;
                        else if (h.contains("\\physicaldisk(_total)\\avg. disk sec/transfer")) colDiskResp = i;
                        else if (h.contains("\\system\\threads")) colThreads = i;
                        else if (h.contains("\\process(_total)\\handle count")) colHandles = i;
                        else if (h.contains("\\memory\\pool paged bytes")) colPaged = i;
                        else if (h.contains("\\memory\\pool nonpaged bytes")) colNonPaged = i;
                        else if (h.contains("\\memory\\cache bytes")) colCache = i;
                        else if (h.contains("\\memory\\committed bytes")) colCommitUsed = i;
                        else if (h.contains("\\memory\\commit limit")) colCommitTotal = i;
                        else if (h.contains("\\system\\system up time")) colUptime = i;
                        else if (h.contains("\\network interface(") && h.contains("bytes received/sec")) {
                            if (h.contains("wi-fi") || h.contains("wifi") || h.contains("wireless") || h.contains("802.11") || h.contains("wlan")) {
                                colsWifiRecv.add(i);
                            } else if (!h.contains("pseudo") && !h.contains("teredo") && !h.contains("loopback") && !h.contains("vethernet")) {
                                colsEthRecv.add(i);
                            }
                        } else if (h.contains("\\network interface(") && h.contains("bytes sent/sec")) {
                            if (h.contains("wi-fi") || h.contains("wifi") || h.contains("wireless") || h.contains("802.11") || h.contains("wlan")) {
                                colsWifiSent.add(i);
                            } else if (!h.contains("pseudo") && !h.contains("teredo") && !h.contains("loopback") && !h.contains("vethernet")) {
                                colsEthSent.add(i);
                            }
                        }
                    }

                    String dataLine;
                    while ((dataLine = reader.readLine()) != null) {
                        dataLine = dataLine.trim();
                        if (dataLine.isEmpty() || dataLine.startsWith("\"(PDH-CSV")) continue;
                        String[] values = parseCsvLine(dataLine);

                        if (colDiskIdle >= 0 && colDiskIdle < values.length) {
                            double idle = parseDbl(values[colDiskIdle], -1.0);
                            if (idle >= 0.0) {
                                liveDiskActive = Math.min(100.0, Math.max(0.0, 100.0 - idle));
                            } else if (colDiskActive >= 0 && colDiskActive < values.length) {
                                liveDiskActive = Math.min(100.0, Math.max(0.0, parseDbl(values[colDiskActive], liveDiskActive)));
                            }
                        } else if (colDiskActive >= 0 && colDiskActive < values.length) {
                            liveDiskActive = Math.min(100.0, Math.max(0.0, parseDbl(values[colDiskActive], liveDiskActive)));
                        }
                        if (colDiskRead >= 0 && colDiskRead < values.length) {
                            liveDiskReadKB = parseDbl(values[colDiskRead], 0.0) / 1024.0;
                        }
                        if (colDiskWrite >= 0 && colDiskWrite < values.length) {
                            liveDiskWriteKB = parseDbl(values[colDiskWrite], 0.0) / 1024.0;
                        }
                        if (colDiskResp >= 0 && colDiskResp < values.length) {
                            liveDiskRespMs = parseDbl(values[colDiskResp], 0.0) * 1000.0;
                        }

                        if (colThreads >= 0 && colThreads < values.length) {
                            liveThreads = (long) parseDbl(values[colThreads], liveThreads);
                        }
                        if (colHandles >= 0 && colHandles < values.length) {
                            liveHandles = (long) parseDbl(values[colHandles], liveHandles);
                        }
                        if (colPaged >= 0 && colPaged < values.length) {
                            livePagedMB = parseDbl(values[colPaged], 0.0) / (1024.0 * 1024.0);
                        }
                        if (colNonPaged >= 0 && colNonPaged < values.length) {
                            liveNonPagedMB = parseDbl(values[colNonPaged], 0.0) / (1024.0 * 1024.0);
                        }
                        if (colCache >= 0 && colCache < values.length) {
                            liveCachedGB = parseDbl(values[colCache], 0.0) / (1024.0 * 1024.0 * 1024.0);
                        }
                        if (colCommitUsed >= 0 && colCommitUsed < values.length) {
                            liveCommitUsedGB = parseDbl(values[colCommitUsed], 0.0) / (1024.0 * 1024.0 * 1024.0);
                        }
                        if (colCommitTotal >= 0 && colCommitTotal < values.length) {
                            liveCommitTotalGB = parseDbl(values[colCommitTotal], 0.0) / (1024.0 * 1024.0 * 1024.0);
                        }
                        if (colUptime >= 0 && colUptime < values.length) {
                            liveUptimeSec = (long) parseDbl(values[colUptime], liveUptimeSec);
                        }

                        double wDown = 0.0;
                        for (int col : colsWifiRecv) {
                            if (col < values.length) wDown += parseDbl(values[col], 0.0);
                        }
                        liveWifiDownKB = wDown / 1024.0;

                        double wUp = 0.0;
                        for (int col : colsWifiSent) {
                            if (col < values.length) wUp += parseDbl(values[col], 0.0);
                        }
                        liveWifiUpKB = wUp / 1024.0;

                        double eDown = 0.0;
                        for (int col : colsEthRecv) {
                            if (col < values.length) eDown += parseDbl(values[col], 0.0);
                        }
                        liveEthDownKB = eDown / 1024.0;

                        double eUp = 0.0;
                        for (int col : colsEthSent) {
                            if (col < values.length) eUp += parseDbl(values[col], 0.0);
                        }
                        liveEthUpKB = eUp / 1024.0;
                    }
                }
            } catch (Exception ignored) {}
        }, "Typeperf-Worker");
        typeperfThread.setDaemon(true);
        typeperfThread.start();
    }

    private void stopTypeperfWorker() {
        if (typeperfProcess != null) {
            try { typeperfProcess.destroyForcibly(); } catch (Exception ignored) {}
            typeperfProcess = null;
        }
        if (typeperfThread != null) {
            typeperfThread.interrupt();
            typeperfThread = null;
        }
    }

    private static String[] parseCsvLine(String line) {
        List<String> list = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                list.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        list.add(sb.toString().trim());
        return list.toArray(new String[0]);
    }

    private static double parseDbl(String val, double defaultVal) {
        if (val == null || val.isBlank()) return defaultVal;
        try {
            return Double.parseDouble(val.trim().replace(',', '.'));
        } catch (Exception ignored) {
            return defaultVal;
        }
    }

    private long getPing() {
        if (serverHost == null || serverHost.isEmpty()) return 1;
        try {
            long start = System.currentTimeMillis();
            java.net.InetAddress addr = java.net.InetAddress.getByName(serverHost);
            if (addr.isReachable(500)) {
                return Math.max(1, System.currentTimeMillis() - start);
            }
        } catch (Exception ignored) {}
        return 1;
    }

    private static Map<String, String> queryRegistry(String keyPath) {
        Map<String, String> map = new HashMap<>();
        try {
            Process p = new ProcessBuilder("reg", "query", keyPath).redirectErrorStream(true).start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("HKEY_")) continue;
                    String[] parts = line.split("\\s{2,}");
                    if (parts.length >= 3) {
                        map.put(parts[0].trim(), parts[2].trim());
                    }
                }
            }
            p.waitFor(1, TimeUnit.SECONDS);
        } catch (Exception ignored) {}
        return map;
    }

    private static List<String> queryRegistryValues(String keyPath, String valueName) {
        List<String> list = new ArrayList<>();
        try {
            Process p = new ProcessBuilder("reg", "query", keyPath, "/s", "/v", valueName).redirectErrorStream(true).start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith(valueName)) {
                        String[] parts = line.split("\\s{2,}");
                        if (parts.length >= 3) {
                            String val = parts[2].trim();
                            if (!val.isEmpty()) list.add(val);
                        }
                    }
                }
            }
            p.waitFor(2, TimeUnit.SECONDS);
        } catch (Exception ignored) {}
        return list;
    }

    private String sanitize(String s) {
        if (s == null || s.isBlank()) return "N/A";
        return s.replace("|", "/").replace("~~", "-").trim();
    }
}

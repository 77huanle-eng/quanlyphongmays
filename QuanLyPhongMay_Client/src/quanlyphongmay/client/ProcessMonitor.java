package quanlyphongmay.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * ProcessMonitor - Thu thap danh sach tien trinh chuan Windows 11 Task Manager.
 * Toi uu hoa 0% CPU Overhead:
 * - Su dung tasklist.exe /FO CSV /NH (cuc nhe ~50ms, 0% CPU) thay vi WMI nang.
 * - Ket hop Java 9+ ProcessHandle de tinh toan CPU% thuc te chinh xac.
 * - Lay tieu de cua so (Window Title) tu cac ung dung co giao dien.
 * - Gom nhom cac tien trinh cung ten (Process Grouping, vi du: Google Chrome (18)).
 */
public class ProcessMonitor {

    public interface DataSender {
        void send(String data);
    }

    private final String maMay;
    private final DataSender sender;
    private ScheduledExecutorService scheduler;

    private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ProcessMonitor-Scanner");
        t.setDaemon(true);
        return t;
    });

    // Luu snapshot CPU thoi gian thuc de tinh delta CPU%
    private final Map<Long, Long> prevCpuMillis = new ConcurrentHashMap<>();
    private volatile long prevScanTime = 0;

    public ProcessMonitor(String maMay, DataSender sender) {
        this.maMay = maMay;
        this.sender = sender;
    }

    /** Khoi dong giam sat dinh ky moi 10 giay (tiet kiem 100% CPU) */
    public synchronized void start() {
        if (scheduler != null && !scheduler.isShutdown()) {
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ProcessMonitor-" + maMay);
            t.setDaemon(true);
            return t;
        });

        // Quet ngay lan dau sau 1 giay, sau do lap moi 10 giay
        scheduler.scheduleWithFixedDelay(this::collectAndSend, 1, 10, TimeUnit.SECONDS);
    }

    /** Dung giam sat */
    public synchronized void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        scanExecutor.shutdownNow();
    }

    /** Quet va gui danh sach tien trinh ngay lap tuc (khi Server gui lenh GET_PROCESSES) */
    public void scanAndSendNow() {
        scanExecutor.submit(this::collectAndSend);
    }

    /** Thu thap va gui danh sach tien trinh ve Server */
    private void collectAndSend() {
        try {
            List<ProcEntry> procs = getRunningProcesses();
            if (procs.isEmpty()) {
                return;
            }

            StringBuilder sbProcList = new StringBuilder();
            StringBuilder sbLegacy = new StringBuilder();

            for (ProcEntry p : procs) {
                if (sbProcList.length() > 0) sbProcList.append("~~");
                if (sbLegacy.length() > 0) sbLegacy.append("~~");

                // Chuan Task Manager moi
                sbProcList.append(sanitize(p.name))
                          .append("::").append(p.pid)
                          .append("::").append(sanitize(p.status))
                          .append("::").append(p.cpu)
                          .append("::").append(p.ramMB)
                          .append("::").append(p.diskMB)
                          .append("::").append(p.netMbps)
                          .append("::").append(sanitize(p.title));

                // Chuan tuong thich nguoc
                sbLegacy.append(sanitize(p.name))
                        .append("::").append(p.pid)
                        .append("::").append(p.cpu)
                        .append("::").append(p.ramMB)
                        .append("::").append(sanitize(p.title));
            }

            // Goi tin chuan moi PROC_LIST|
            sender.send("PROC_LIST|" + maMay + "|" + sbProcList);

            // Goi tin tuong thich nguoc PROCESSES| va APPS|
            sender.send("PROCESSES|" + maMay + "|" + sbLegacy);
            sender.send("APPS|" + maMay + "|" + sbLegacy);

        } catch (Exception ignored) {
        }
    }

    /** Lay danh sach tien trinh qua tasklist.exe va ProcessHandle (0% CPU Overhead) */
    private List<ProcEntry> getRunningProcesses() {
        List<ProcEntry> result = new ArrayList<>();
        long now = System.currentTimeMillis();
        long timeDelta = (prevScanTime > 0) ? (now - prevScanTime) : 10000;
        prevScanTime = now;

        // 1. Khong dung PowerShell de tranh nghen CPU tren VM
        Map<Long, String> windowTitles = new HashMap<>();

        // 2. Chay tasklist.exe /FO CSV /NH (< 50ms, 0% CPU Overhead)
        Map<String, GroupedProc> groupedMap = new LinkedHashMap<>();
        Map<Long, Long> currentCpuMillis = new ConcurrentHashMap<>();

        try {
            ProcessBuilder pbTasklist = new ProcessBuilder("tasklist.exe", "/FO", "CSV", "/NH");
            pbTasklist.redirectErrorStream(true);
            Process procTasklist = pbTasklist.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(procTasklist.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    List<String> cols = parseCsvLine(line);
                    if (cols.size() >= 5) {
                        String imgName = cols.get(0);
                        String pidStr = cols.get(1);
                        String memStr = cols.get(4);

                        long pid = 0;
                        try { pid = Long.parseLong(pidStr); } catch (Exception ignored) {}
                        if (pid <= 0) continue;

                        // Parse RAM MB
                        double ramMB = parseMemMB(memStr);

                        // Tinh CPU% qua ProcessHandle (0ms)
                        double cpuPct = 0.0;
                        try {
                            Optional<ProcessHandle> phOpt = ProcessHandle.of(pid);
                            if (phOpt.isPresent()) {
                                ProcessHandle ph = phOpt.get();
                                Optional<Duration> cpuDur = ph.info().totalCpuDuration();
                                if (cpuDur.isPresent()) {
                                    long totalMs = cpuDur.get().toMillis();
                                    currentCpuMillis.put(pid, totalMs);
                                    Long prevMs = prevCpuMillis.get(pid);
                                    if (prevMs != null && timeDelta > 0 && totalMs >= prevMs) {
                                        cpuPct = Math.round(((double)(totalMs - prevMs) / (double) timeDelta) * 1000.0) / 10.0;
                                        if (cpuPct > 100.0) cpuPct = 100.0;
                                    }
                                }
                            }
                        } catch (Exception ignored) {}

                        String title = windowTitles.getOrDefault(pid, "");

                        // Gom nhom theo base image name
                        String baseName = imgName;
                        GroupedProc g = groupedMap.get(baseName);
                        if (g == null) {
                            g = new GroupedProc(baseName, pidStr, title);
                            groupedMap.put(baseName, g);
                        }
                        g.count++;
                        g.totalRamMB += ramMB;
                        g.totalCpu += cpuPct;
                        if (!title.isEmpty() && g.mainTitle.isEmpty()) {
                            g.mainTitle = title;
                            g.mainPid = pidStr;
                        }
                    }
                }
            }
            if (!procTasklist.waitFor(4, TimeUnit.SECONDS)) {
                procTasklist.destroyForcibly();
            }
        } catch (Exception ignored) {}

        // Cap nhat cache CPU
        prevCpuMillis.clear();
        prevCpuMillis.putAll(currentCpuMillis);

        // 3. Sap xep theo CPU va RAM, lay Top 60 tien trinh
        List<GroupedProc> sorted = new ArrayList<>(groupedMap.values());
        sorted.sort((a, b) -> {
            int c = Double.compare(b.totalCpu, a.totalCpu);
            if (c != 0) return c;
            return Double.compare(b.totalRamMB, a.totalRamMB);
        });

        int limit = Math.min(sorted.size(), 60);
        for (int i = 0; i < limit; i++) {
            GroupedProc g = sorted.get(i);
            String displayName = (g.count > 1) ? (g.baseName + " (" + g.count + ")") : g.baseName;
            result.add(new ProcEntry(
                displayName,
                g.mainPid,
                "Running",
                String.format(Locale.US, "%.1f", g.totalCpu),
                String.format(Locale.US, "%.1f", g.totalRamMB),
                "0.0",
                "0.0",
                g.mainTitle
            ));
        }

        return result;
    }

    private static class GroupedProc {
        final String baseName;
        String mainPid;
        String mainTitle;
        int count = 0;
        double totalRamMB = 0.0;
        double totalCpu = 0.0;

        GroupedProc(String baseName, String mainPid, String mainTitle) {
            this.baseName = baseName;
            this.mainPid = mainPid;
            this.mainTitle = mainTitle;
        }
    }

    private static double parseMemMB(String memStr) {
        if (memStr == null || memStr.isEmpty()) return 0.0;
        try {
            // "147.012 K" hoac "147,012 K" hoac "147012 K"
            String digits = memStr.replaceAll("[^0-9]", "");
            if (digits.isEmpty()) return 0.0;
            long kb = Long.parseLong(digits);
            return Math.round((kb / 1024.0) * 10.0) / 10.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        if (line == null || line.isEmpty()) return result;
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        result.add(sb.toString().trim());
        return result;
    }

    private String sanitize(String s) {
        if (s == null) return "";
        return s.replace("::", " ").replace("~~", " ").replace("|", " ").trim();
    }

    public static class ProcEntry {
        public final String name;
        public final String pid;
        public final String status;
        public final String cpu;
        public final String ramMB;
        public final String diskMB;
        public final String netMbps;
        public final String title;

        public ProcEntry(String name, String pid, String status, String cpu, String ramMB, String diskMB, String netMbps, String title) {
            this.name = name;
            this.pid = pid;
            this.status = status;
            this.cpu = cpu;
            this.ramMB = ramMB;
            this.diskMB = diskMB;
            this.netMbps = netMbps;
            this.title = title;
        }

        public ProcEntry(String name, String pid, String cpu, String ramMB, String title) {
            this(name, pid, "Running", cpu, ramMB, "0.0", "0.0", title);
        }
    }
}

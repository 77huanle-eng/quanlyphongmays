package quanlyphongmay.client;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * SystemCleaner - Module mô phỏng logic đóng băng và khôi phục hệ thống (Deep Freeze / Image Sync logic).
 * Tự động dọn dẹp các tệp tin rác, bộ nhớ tạm, cache trình duyệt và thùng rác trên ổ đĩa hệ thống C:
 * để đưa máy trạm về trạng thái sạch sẽ.
 */
public class SystemCleaner {

    public static class CleanReport {
        public final int filesDeleted;
        public final double freedMB;
        public final String status;

        public CleanReport(int filesDeleted, double freedMB, String status) {
            this.filesDeleted = filesDeleted;
            this.freedMB = freedMB;
            this.status = status;
        }

        @Override
        public String toString() {
            return String.format("Đã xóa %d tệp tin rác, giải phóng %.2f MB dung lượng ổ đĩa.", filesDeleted, freedMB);
        }
    }

    public static void cleanAsync(boolean fullClean, Consumer<CleanReport> callback) {
        Thread thread = new Thread(() -> {
            CleanReport report = clean(fullClean);
            callback.accept(report);
        }, "SystemCleaner-Thread");
        thread.setDaemon(true);
        thread.start();
    }

    public static CleanReport clean(boolean fullClean) {
        AtomicInteger deletedCount = new AtomicInteger(0);
        AtomicLong freedBytes = new AtomicLong(0);

        try {
            // 1. Dọn dẹp thư mục Temp của người dùng hiện tại (%TEMP%)
            String userTemp = System.getProperty("java.io.tmpdir");
            if (userTemp != null) {
                deleteDirectoryContents(new File(userTemp), deletedCount, freedBytes);
            }

            // 2. Dọn dẹp thư mục Windows Temp (C:\Windows\Temp)
            File winTemp = new File("C:\\Windows\\Temp");
            if (winTemp.exists() && winTemp.isDirectory()) {
                deleteDirectoryContents(winTemp, deletedCount, freedBytes);
            }

            // 3. Dọn dẹp thư mục Windows Prefetch (C:\Windows\Prefetch) nếu là fullClean
            if (fullClean) {
                File prefetch = new File("C:\\Windows\\Prefetch");
                if (prefetch.exists() && prefetch.isDirectory()) {
                    deleteDirectoryContents(prefetch, deletedCount, freedBytes);
                }
            }

            // 4. Dọn dẹp Cache trình duyệt Chrome và Edge
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null) {
                // Chrome Cache
                File chromeCache = new File(localAppData, "Google\\Chrome\\User Data\\Default\\Cache");
                if (chromeCache.exists()) {
                    deleteDirectoryContents(chromeCache, deletedCount, freedBytes);
                }
                // Edge Cache
                File edgeCache = new File(localAppData, "Microsoft\\Edge\\User Data\\Default\\Cache");
                if (edgeCache.exists()) {
                    deleteDirectoryContents(edgeCache, deletedCount, freedBytes);
                }
            }

            // 5. Dọn dẹp thùng rác Windows (Recycle Bin)
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                try {
                    new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                            "Clear-RecycleBin -Force -ErrorAction SilentlyContinue")
                            .start().waitFor();
                } catch (Exception ignored) {}
            }

            double freedMB = freedBytes.get() / (1024.0 * 1024.0);
            return new CleanReport(deletedCount.get(), freedMB, "SUCCESS");

        } catch (Exception e) {
            double freedMB = freedBytes.get() / (1024.0 * 1024.0);
            return new CleanReport(deletedCount.get(), freedMB, "ERROR: " + e.getMessage());
        }
    }

    private static void deleteDirectoryContents(File dir, AtomicInteger count, AtomicLong bytes) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File f : files) {
            try {
                if (f.isDirectory()) {
                    deleteDirectoryContents(f, count, bytes);
                    f.delete(); // Xóa thư mục rỗng
                } else {
                    long len = f.length();
                    if (f.delete()) {
                        count.incrementAndGet();
                        bytes.addAndGet(len);
                    }
                }
            } catch (Exception ignored) {
                // Bỏ qua các file đang bị hệ thống khóa
            }
        }
    }
}


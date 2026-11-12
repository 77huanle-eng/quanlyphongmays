package quanlyphongmay.server;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * IncidentManager - Quản lý tập trung các sự cố và vi phạm phòng máy.
 */
public class IncidentManager {

    public static class IncidentItem {
        public final String timestamp;
        public final String maMay;
        public final String type;        // BANNED_APP, BANNED_WEB, STORAGE_CRITICAL, NETWORK_DROP
        public final String severity;    // CRITICAL, WARNING, INFO
        public final String title;
        public final String description;

        public IncidentItem(String timestamp, String maMay, String type, String severity, String title, String description) {
            this.timestamp = timestamp;
            this.maMay = maMay;
            this.type = type;
            this.severity = severity;
            this.title = title;
            this.description = description;
        }

        public String getTypeLabel() {
            return switch (type) {
                case "BANNED_APP" -> "[Game] Ứng dụng cấm";
                case "BANNED_WEB" -> "[LAN] Web vi phạm";
                case "STORAGE_CRITICAL" -> "[Disk] Ổ đĩa cạn kiệt";
                case "NETWORK_DROP" -> "Rớt mạng / Trễ cao";
                default -> type;
            };
        }
    }

    private static final List<IncidentItem> incidents = new CopyOnWriteArrayList<>();
    private static final List<Consumer<IncidentItem>> listeners = new CopyOnWriteArrayList<>();
    private static final DateTimeFormatter DFM = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void addListener(Consumer<IncidentItem> listener) {
        listeners.add(listener);
    }

    public static void removeListener(Consumer<IncidentItem> listener) {
        listeners.remove(listener);
    }

    public static void logIncident(String maMay, String type, String severity, String title, String description) {
        String now = LocalDateTime.now().format(DFM);
        IncidentItem item = new IncidentItem(now, maMay, type, severity, title, description);
        incidents.add(0, item); // Thêm vào đầu danh sách (mới nhất lên trên)

        // Cũng tự động ghi nhận vào ActivityLogForm
        ActivityLogForm.themNhatKy(maMay, "CẢNH BÁO TỪ SERVER [" + item.getTypeLabel() + "]", title + " (" + description + ")");

        // Thông báo cho các UI listeners
        for (Consumer<IncidentItem> listener : listeners) {
            try {
                listener.accept(item);
            } catch (Exception ignored) {}
        }
    }

    public static List<IncidentItem> getAllIncidents() {
        return Collections.unmodifiableList(new ArrayList<>(incidents));
    }

    public static int getCount() {
        return incidents.size();
    }

    public static void clear() {
        incidents.clear();
    }
}


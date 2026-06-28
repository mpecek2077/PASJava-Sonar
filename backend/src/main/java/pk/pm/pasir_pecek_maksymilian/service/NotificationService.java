package pk.pm.pasir_pecek_maksymilian.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import pk.pm.pasir_pecek_maksymilian.model.Group;
import pk.pm.pasir_pecek_maksymilian.model.User;

import java.util.HashMap;
import java.util.Map;

@Service
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void sendExpenseNotification(User targetUser, User creator, Group group, String title, double amount, double userShare) {
        // Przygotowujemy wiadomość zgodnie z kontraktem wymaganym przez Reacta
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "GROUP_EXPENSE_ADDED");
        payload.put("groupId", group.getId());
        payload.put("groupName", group.getName());
        payload.put("title", title);
        payload.put("amount", amount);
        payload.put("userShare", userShare);
        payload.put("createdByEmail", creator.getEmail());

        // Generujemy polski komunikat, który wyświetli się w oknie powiadomienia
        String message = String.format("%s dodał wydatek \"%s\" w grupie %s. Twoja część: %.2f zł.",
                creator.getEmail(), title, group.getName(), userShare);
        payload.put("message", message);

        // Wysyłamy wiadomość na prywatny kanał konkretnego użytkownika
        messagingTemplate.convertAndSendToUser(
                targetUser.getEmail(),
                "/queue/notifications",
                payload
        );
    }
}
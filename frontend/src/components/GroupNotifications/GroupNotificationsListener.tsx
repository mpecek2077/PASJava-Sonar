import { useEffect, useRef } from "react";
import { toast } from "react-toastify";
import { useAuth } from "../../context/AuthContext";

const GroupNotificationsListener = ({ onRefresh }: { onRefresh: () => void }) => {
  const { isAuthenticated } = useAuth();
  // Zapisujemy funkcję w ref, żeby nie restartować połączenia przy zmianie funkcji
  const onRefreshRef = useRef(onRefresh);

  useEffect(() => {
    onRefreshRef.current = onRefresh;
  }, [onRefresh]);

  useEffect(() => {
    if (!isAuthenticated) return;
    const token = localStorage.getItem("accessToken");
    if (!token) return;

    const socket = new WebSocket(`ws://localhost:8080/ws/group-notifications?token=${encodeURIComponent(token)}`);

    socket.onopen = () => {
      socket.send("CONNECT\naccept-version:1.1,1.0\nhost:localhost\n\n\0");
    };

    socket.onmessage = (event) => {
      const text = event.data;
      if (text.startsWith("CONNECTED")) {
        socket.send("SUBSCRIBE\nid:sub-0\ndestination:/user/queue/notifications\n\n\0");
      } else if (text.startsWith("MESSAGE")) {
        const bodyStartIndex = text.indexOf("\n\n") + 2;
        const jsonBody = text.substring(bodyStartIndex).replace('\0', '');
        const notification = JSON.parse(jsonBody);

        toast.info(notification.message);
        onRefreshRef.current(); // Wywołujemy najnowszą wersję funkcji
      }
    };

    return () => socket.close();
  }, [isAuthenticated]);

  return null;
};

export default GroupNotificationsListener;
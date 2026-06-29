package su.kukecdk.api;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ApiRateLimiter {
    private final Map<String, Deque<Long>> hits = new HashMap<>();

    public synchronized boolean allow(String key, int requestsPerMinute) {
        long now = System.currentTimeMillis();
        long cutoff = now - 60000L;
        Deque<Long> deque = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!deque.isEmpty() && deque.peekFirst() < cutoff) {
            deque.removeFirst();
        }
        if (deque.size() >= requestsPerMinute) {
            cleanup(cutoff);
            return false;
        }
        deque.addLast(now);
        cleanup(cutoff);
        return true;
    }

    private void cleanup(long cutoff) {
        Iterator<Map.Entry<String, Deque<Long>>> it = hits.entrySet().iterator();
        while (it.hasNext()) {
            Deque<Long> deque = it.next().getValue();
            while (!deque.isEmpty() && deque.peekFirst() < cutoff) deque.removeFirst();
            if (deque.isEmpty()) it.remove();
        }
    }
}

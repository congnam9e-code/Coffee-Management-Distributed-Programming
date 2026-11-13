package com.cafe.server.service;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.cafe.common.network.Response;

/**
 * Phát thông báo đồng bộ real-time qua TCP Socket tới các Client đang đăng ký SUBSCRIBE.
 */
public final class SyncBroadcaster {

	public static final String TOPIC_MENU_CHANGED = "MENU_CHANGED";
	public static final String TOPIC_ORDER_CHANGED = "ORDER_CHANGED";
	public static final String TOPIC_EMPLOYEE_CHANGED = "EMPLOYEE_CHANGED";
	public static final String TOPIC_PING = "PING";

	public static final class Subscriber {
		private final Socket socket;
		private final ObjectOutputStream out;
		private final String sessionToken;
		private final Object writeLock = new Object();

		private Subscriber(Socket socket, ObjectOutputStream out, String sessionToken) {
			this.socket = socket;
			this.out = out;
			this.sessionToken = sessionToken;
		}

		private void send(Response packet) throws IOException {
			synchronized (writeLock) {
				out.writeObject(packet);
				out.reset();
				out.flush();
			}
		}

		private void closeQuietly() {
			try {
				socket.close();
			} catch (IOException ignored) {
			}
		}
	}

	private static final CopyOnWriteArrayList<Subscriber> SUBSCRIBERS = new CopyOnWriteArrayList<>();
	private static final Object SCHEDULER_LOCK = new Object();
	private static ScheduledExecutorService scheduler;

	private SyncBroadcaster() {
	}

	private static ScheduledExecutorService ensureScheduler() {
		synchronized (SCHEDULER_LOCK) {
			if (scheduler == null || scheduler.isShutdown()) {
				scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
					Thread t = new Thread(r, "CafeSyncBroadcaster");
					t.setDaemon(true);
					return t;
				});
				scheduler.scheduleAtFixedRate(() -> {
					if (!SUBSCRIBERS.isEmpty()) {
						deliverTopic(TOPIC_PING);
					}
				}, 25, 25, TimeUnit.SECONDS);
			}
			return scheduler;
		}
	}

	public static Subscriber register(Socket socket, ObjectOutputStream out, String sessionToken) throws IOException {
		ensureScheduler();
		Subscriber sub = new Subscriber(socket, out, sessionToken);
		sub.send(new Response(true, "SUBSCRIBED", null));
		SUBSCRIBERS.add(sub);
		return sub;
	}

	public static void unregister(Subscriber sub) {
		if (sub != null) {
			SUBSCRIBERS.remove(sub);
		}
	}

	public static void unregisterTokens(Collection<String> tokens) {
		if (tokens == null || tokens.isEmpty()) {
			return;
		}
		for (Subscriber sub : SUBSCRIBERS) {
			if (tokens.contains(sub.sessionToken)) {
				SUBSCRIBERS.remove(sub);
				sub.closeQuietly();
			}
		}
	}

	public static void broadcast(String topic) {
		if (topic == null || SUBSCRIBERS.isEmpty()) {
			return;
		}
		try {
			ensureScheduler().execute(() -> deliverTopic(topic));
		} catch (Exception ignored) {
		}
	}

	private static void deliverTopic(String topic) {
		Response event = new Response(true, topic, null);
		for (Subscriber sub : SUBSCRIBERS) {
			if (sub.socket.isClosed()) {
				SUBSCRIBERS.remove(sub);
				continue;
			}
			try {
				sub.send(event);
			} catch (IOException e) {
				SUBSCRIBERS.remove(sub);
				sub.closeQuietly();
			}
		}
	}

	public static int subscriberCount() {
		return SUBSCRIBERS.size();
	}

	public static void shutdown() {
		synchronized (SCHEDULER_LOCK) {
			if (scheduler != null) {
				scheduler.shutdownNow();
				scheduler = null;
			}
		}
		for (Subscriber sub : SUBSCRIBERS) {
			sub.closeQuietly();
		}
		SUBSCRIBERS.clear();
	}
}

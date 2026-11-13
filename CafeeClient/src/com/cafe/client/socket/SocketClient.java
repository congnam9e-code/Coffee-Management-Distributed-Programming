package com.cafe.client.socket;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.function.Consumer;

import com.cafe.common.network.ActionType;
import com.cafe.common.network.Request;
import com.cafe.common.network.Response;

/**
 * Quản lý kết nối Socket TCP tới Server: tự động khôi phục phiên khi rớt mạng và lắng nghe đồng bộ real-time.
 */
public class SocketClient {

	private static final int CONNECT_TIMEOUT_MS = 3000;
	private static final int READ_TIMEOUT_MS = 20000;
	private static final int SYNC_READ_TIMEOUT_MS = 65000;
	private static final int SYNC_RECONNECT_DELAY_MS = 1500;

	private static SocketClient instance;

	private Socket socket;
	private ObjectOutputStream out;
	private ObjectInputStream in;

	private String lastHost;
	private int lastPort;
	private volatile String sessionToken;

	private final Object syncLock = new Object();
	private volatile Consumer<String> syncListener;
	private Thread syncThread;
	private volatile Socket syncSocket;
	private volatile boolean syncRunning;

	private SocketClient() {
	}

	public static synchronized SocketClient getInstance() {
		if (instance == null) {
			instance = new SocketClient();
		}
		return instance;
	}

	public synchronized boolean connect(String host, int port) {
		this.lastHost = host;
		this.lastPort = port;
		return connectInternal(host, port);
	}

	private boolean connectInternal(String host, int port) {
		closeRpcStreamsQuietly();
		if (host == null || host.isBlank() || port <= 0) {
			return false;
		}
		try {
			Socket newSocket = new Socket();
			newSocket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
			newSocket.setTcpNoDelay(true);
			newSocket.setKeepAlive(true);
			newSocket.setSoTimeout(READ_TIMEOUT_MS);

			ObjectOutputStream newOut = new ObjectOutputStream(
					new BufferedOutputStream(newSocket.getOutputStream(), 8192));
			newOut.flush();
			ObjectInputStream newIn = new ObjectInputStream(
					new BufferedInputStream(newSocket.getInputStream(), 8192));

			this.socket = newSocket;
			this.out = newOut;
			this.in = newIn;
			return true;
		} catch (IOException e) {
			closeRpcStreamsQuietly();
			return false;
		}
	}

	public synchronized boolean isConnected() {
		return socket != null && socket.isConnected() && !socket.isClosed() && out != null && in != null;
	}

	public synchronized String getSessionToken() {
		return sessionToken;
	}

	public void setSyncListener(Consumer<String> listener) {
		synchronized (syncLock) {
			this.syncListener = listener;
			if (listener != null && sessionToken != null && lastHost != null && lastPort > 0) {
				startSyncThreadLocked();
			} else if (listener == null) {
				stopSyncThreadLocked();
			}
		}
	}

	public synchronized Response sendRequest(Request request) {
		if (request == null || request.getAction() == null) {
			return new Response(false, "Yêu cầu không hợp lệ!", null);
		}

		ActionType action = request.getAction();
		if (action != ActionType.LOGIN
				&& (request.getSessionToken() == null || request.getSessionToken().isBlank())
				&& this.sessionToken != null) {
			request.setSessionToken(this.sessionToken);
		}

		if (!isConnected()) {
			if (action != ActionType.LOGIN && this.sessionToken != null && this.lastHost != null && this.lastPort > 0) {
				if (!connectInternal(this.lastHost, this.lastPort)) {
					if (action == ActionType.LOGOUT) {
						clearSessionAndSync();
						return new Response(true, "Đã đăng xuất.", null);
					}
					return new Response(false, "Mất kết nối tới Server! Đang chờ kết nối lại...", null);
				}
			} else {
				if (action == ActionType.LOGOUT) {
					clearSessionAndSync();
					return new Response(true, "Đã đăng xuất.", null);
				}
				return new Response(false, "Mất kết nối tới Server! Vui lòng kiểm tra lại Server.", null);
			}
		}

		for (int attempt = 0; attempt < 2; attempt++) {
			try {
				out.writeObject(request);
				out.reset();
				out.flush();

				Response response = (Response) in.readObject();
				handlePostResponse(action, response);
				return response;
			} catch (SocketTimeoutException e) {
				// Đóng socket kẹt để lần gọi kế tự nối lại, giữ sessionToken tránh ép đăng nhập lại
				closeRpcStreamsQuietly();
				if (action == ActionType.LOGOUT) {
					clearSessionAndSync();
					return new Response(true, "Đã đăng xuất.", null);
				}
				return new Response(false,
						"Server phản hồi quá lâu (timeout " + (READ_TIMEOUT_MS / 1000) + "s). Thao tác chưa được xác nhận!",
						null);
			} catch (IOException e) {
				closeRpcStreamsQuietly();
				boolean canRetry = attempt == 0
						&& action != ActionType.LOGIN
						&& this.sessionToken != null
						&& this.lastHost != null
						&& this.lastPort > 0
						&& connectInternal(this.lastHost, this.lastPort);
				if (!canRetry) {
					if (action == ActionType.LOGOUT) {
						clearSessionAndSync();
						return new Response(true, "Đã đăng xuất.", null);
					}
					return new Response(false, "Lỗi truyền tải mạng (Socket): " + e.getMessage(), null);
				}
			} catch (ClassNotFoundException e) {
				return new Response(false, "Dữ liệu phản hồi từ Server không tương thích!", null);
			}
		}

		return new Response(false, "Không thể gửi yêu cầu tới Server!", null);
	}

	private void handlePostResponse(ActionType action, Response response) {
		if (action == ActionType.LOGIN) {
			if (response != null && response.isSuccess()) {
				this.sessionToken = response.getSessionToken();
				synchronized (syncLock) {
					if (this.syncListener != null && this.sessionToken != null) {
						startSyncThreadLocked();
					}
				}
			} else {
				clearSessionAndSync();
			}
			return;
		}

		if (action == ActionType.LOGOUT) {
			clearSessionAndSync();
			return;
		}

		if (response != null && !response.isSuccess() && response.getMessage() != null
				&& response.getMessage().contains("Phiên đăng nhập không hợp lệ")) {
			clearSessionAndSync();
		}
	}

	private void startSyncThreadLocked() {
		stopSyncThreadLocked();
		final String tokenSnapshot = this.sessionToken;
		final String hostSnapshot = this.lastHost;
		final int portSnapshot = this.lastPort;
		if (tokenSnapshot == null || hostSnapshot == null || portSnapshot <= 0) {
			return;
		}
		this.syncRunning = true;
		Thread worker = new Thread(() -> runSyncLoop(hostSnapshot, portSnapshot, tokenSnapshot), "CafeSyncListener");
		worker.setDaemon(true);
		this.syncThread = worker;
		worker.start();
	}

	private void stopSyncThreadLocked() {
		this.syncRunning = false;
		if (this.syncSocket != null) {
			try {
				this.syncSocket.close();
			} catch (IOException ignored) {
			}
			this.syncSocket = null;
		}
		if (this.syncThread != null) {
			this.syncThread.interrupt();
			this.syncThread = null;
		}
	}

	private void runSyncLoop(String host, int port, String token) {
		boolean disconnectedOnce = false;
		while (syncRunning && token.equals(this.sessionToken)) {
			Socket s = null;
			try {
				s = new Socket();
				s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
				s.setTcpNoDelay(true);
				s.setKeepAlive(true);
				s.setSoTimeout(SYNC_READ_TIMEOUT_MS);
				this.syncSocket = s;

				ObjectOutputStream sOut = new ObjectOutputStream(new BufferedOutputStream(s.getOutputStream(), 4096));
				sOut.flush();
				ObjectInputStream sIn = new ObjectInputStream(new BufferedInputStream(s.getInputStream(), 4096));

				Request subReq = new Request(ActionType.SUBSCRIBE, null);
				subReq.setSessionToken(token);
				sOut.writeObject(subReq);
				sOut.flush();

				while (syncRunning && token.equals(this.sessionToken)) {
					Object obj = sIn.readObject();
					if (!(obj instanceof Response packet)) {
						break;
					}
					if (!packet.isSuccess()) {
						// Phiên đã bị thu hồi hoặc hết hạn trên Server -> ngừng tự kết nối lại kênh sync
						syncRunning = false;
						break;
					}
					String topic = packet.getMessage();
					if ("SUBSCRIBED".equals(topic)) {
						if (disconnectedOnce) {
							disconnectedOnce = false;
							notifySyncListener("ALL_REFRESH");
						}
						continue;
					}
					if ("PING".equals(topic)) {
						continue;
					}
					notifySyncListener(topic);
				}
			} catch (Exception e) {
				disconnectedOnce = true;
			} finally {
				if (this.syncSocket == s) {
					this.syncSocket = null;
				}
				if (s != null) {
					try {
						s.close();
					} catch (IOException ignored) {
					}
				}
			}

			if (!syncRunning || !token.equals(this.sessionToken)) {
				break;
			}
			try {
				Thread.sleep(SYNC_RECONNECT_DELAY_MS);
			} catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	private void notifySyncListener(String topic) {
		Consumer<String> listener = this.syncListener;
		if (listener != null && topic != null && !topic.isBlank()) {
			listener.accept(topic);
		}
	}

	private void clearSessionAndSync() {
		this.sessionToken = null;
		synchronized (syncLock) {
			stopSyncThreadLocked();
		}
	}

	private void closeRpcStreamsQuietly() {
		try {
			if (in != null) {
				in.close();
			}
		} catch (IOException ignored) {
		}
		try {
			if (out != null) {
				out.close();
			}
		} catch (IOException ignored) {
		}
		try {
			if (socket != null) {
				socket.close();
			}
		} catch (IOException ignored) {
		}
		in = null;
		out = null;
		socket = null;
	}

	public synchronized void close() {
		clearSessionAndSync();
		closeRpcStreamsQuietly();
	}
}

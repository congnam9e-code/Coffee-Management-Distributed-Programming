package com.cafe.client.socket;

import com.cafe.common.network.Config;
import com.cafe.common.network.Request;
import com.cafe.common.network.Response;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class SocketClient {
	private static SocketClient instance;
	private Socket socket;
	private ObjectOutputStream out;
	private ObjectInputStream in;

	private SocketClient() {
		connect(Config.SERVER_IP, Config.SERVER_PORT);
	}

	public synchronized boolean connect(String host, int port) {
		close();
		try {
			Config.SERVER_IP = host;
			Config.SERVER_PORT = port;
			socket = new Socket();
			socket.connect(new InetSocketAddress(host, port), 3000); // 3 giây timeout
			out = new ObjectOutputStream(socket.getOutputStream());
			out.flush();
			in = new ObjectInputStream(socket.getInputStream());
			return true;
		} catch (Exception e) {
			close();
			return false;
		}
	}

	public synchronized boolean isConnected() {
		return socket != null && socket.isConnected() && !socket.isClosed();
	}

	public static synchronized SocketClient getInstance() {
		if (instance == null) {
			instance = new SocketClient();
		}
		return instance;
	}

	public synchronized Response sendRequest(Request request) {
		try {
			if (!isConnected()) {
				boolean reconnected = connect(Config.SERVER_IP, Config.SERVER_PORT);
				if (!reconnected) {
					return null;
				}
			}
			out.writeObject(request);
			out.flush();
			return (Response) in.readObject();
		} catch (Exception e) {
			e.printStackTrace();
			close();
			return null;
		}
	}

	public synchronized void close() {
		try {
			if (in != null) in.close();
			if (out != null) out.close();
			if (socket != null && !socket.isClosed()) socket.close();
		} catch (Exception ignored) {
		} finally {
			in = null;
			out = null;
			socket = null;
		}
	}
}

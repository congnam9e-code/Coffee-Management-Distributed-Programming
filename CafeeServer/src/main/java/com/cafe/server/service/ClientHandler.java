package com.cafe.server.service;

import com.cafe.common.entity.Employee;
import com.cafe.common.entity.Product;
import com.cafe.common.network.ActionType;
import com.cafe.common.network.ChangePasswordDto;
import com.cafe.common.network.OrderDto;
import com.cafe.common.network.Request;
import com.cafe.common.network.Response;
import com.cafe.server.dao.DaoResult;
import com.cafe.server.dao.EmployeeDao;
import com.cafe.server.dao.OrderDao;
import com.cafe.server.dao.ProductDao;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.EnumSet;
import java.util.Set;

public class ClientHandler implements Runnable {
	private static final Set<ActionType> MANAGER_ONLY_ACTIONS = EnumSet.of(
			ActionType.ADD_PRODUCT,
			ActionType.UPDATE_PRODUCT,
			ActionType.DELETE_PRODUCT,
			ActionType.GET_EMPLOYEES,
			ActionType.ADD_EMPLOYEE,
			ActionType.UPDATE_EMPLOYEE,
			ActionType.DELETE_EMPLOYEE,
			ActionType.GET_ALL_BILLS);

	private final Socket socket;
	private final EmployeeDao employeeDao;
	private final ProductDao productDao;
	private final OrderDao orderDao;
	private Employee currentUser;
	private volatile String currentSessionToken;
	private static final long REQUEST_DEADLINE_MS = 16_000L;
	private static final Set<ActionType> MUTATING_ACTIONS = EnumSet.of(
			ActionType.ADD_PRODUCT,
			ActionType.UPDATE_PRODUCT,
			ActionType.DELETE_PRODUCT,
			ActionType.ADD_TO_BILL,
			ActionType.CHECKOUT_BILL,
			ActionType.ADD_EMPLOYEE,
			ActionType.UPDATE_EMPLOYEE,
			ActionType.DELETE_EMPLOYEE,
			ActionType.CHANGE_PASSWORD);

	public ClientHandler(Socket socket) {
		this.socket = socket;
		this.employeeDao = new EmployeeDao();
		this.productDao = new ProductDao();
		this.orderDao = new OrderDao();
	}

	@Override
	public void run() {
		ObjectOutputStream out = null;
		ObjectInputStream in = null;
		try {
			socket.setTcpNoDelay(true);
			socket.setKeepAlive(true);
			out = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream(), 8192));
			out.flush();
			in = new ObjectInputStream(new BufferedInputStream(socket.getInputStream(), 8192));

			while (true) {
				Request request = (Request) in.readObject();
				if (request == null) {
					break;
				}
				if (request.getAction() == ActionType.SUBSCRIBE) {
					handleSubscribe(request, out, in);
					break;
				}
				Response response = handleRequest(request);
				out.writeObject(response);
				out.reset();
				out.flush();
			}
		} catch (EOFException | SocketException e) {
			System.out.println("Client disconnected: " + socket.getInetAddress());
		} catch (Exception e) {
			System.err.println("Error handling client " + socket.getInetAddress() + ": " + e.getMessage());
			e.printStackTrace();
		} finally {
			closeQuietly(in);
			closeQuietly(out);
			closeQuietly(socket);
		}
	}

	private void handleSubscribe(Request request, ObjectOutputStream out, ObjectInputStream in) {
		String token = request.getSessionToken();
		Employee user = SessionManager.getUser(token);
		if (user == null) {
			try {
				out.writeObject(new Response(false, "Phiên đăng nhập không hợp lệ để đăng ký đồng bộ!", null));
				out.reset();
				out.flush();
			} catch (Exception ignored) {
			}
			return;
		}

		SyncBroadcaster.Subscriber subscriber = null;
		try {
			socket.setSoTimeout(0);
			subscriber = SyncBroadcaster.register(socket, out, token);
			while (true) {
				Object extra = in.readObject();
				if (extra == null) {
					break;
				}
			}
		} catch (Exception ignored) {
			// Kết thúc tự nhiên khi Client ngắt kết nối hoặc đăng xuất
		} finally {
			SyncBroadcaster.unregister(subscriber);
		}
	}

	Response handleRequest(Request request) {
		if (request == null || request.getAction() == null) {
			return new Response(false, "Yêu cầu không hợp lệ!", null);
		}

		ActionType action = request.getAction();
		if (action == ActionType.LOGIN) {
			Response loginRes = handleLogin(request.getPayload());
			if (loginRes.isSuccess() && loginRes.getData() instanceof Employee emp) {
				String token = SessionManager.createSession(emp);
				this.currentSessionToken = token;
				loginRes.setSessionToken(token);
			} else {
				this.currentSessionToken = null;
			}
			return loginRes;
		}
		if (action == ActionType.LOGOUT) {
			String tokenToClear = isNotBlank(request.getSessionToken()) ? request.getSessionToken() : this.currentSessionToken;
			if (tokenToClear != null) {
				SessionManager.invalidate(tokenToClear);
				SyncBroadcaster.unregisterTokens(java.util.Collections.singleton(tokenToClear));
			}
			this.currentUser = null;
			this.currentSessionToken = null;
			return new Response(true, "Đã đăng xuất.", null);
		}

		String effectiveToken;
		Employee activeUser;
		if (isNotBlank(request.getSessionToken())) {
			effectiveToken = request.getSessionToken();
			activeUser = SessionManager.getUser(effectiveToken);
			if (activeUser == null) {
				this.currentUser = null;
				this.currentSessionToken = null;
				return new Response(false, "Phiên đăng nhập không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại!", null);
			}
			this.currentUser = activeUser;
			this.currentSessionToken = effectiveToken;
		} else if (isNotBlank(this.currentSessionToken)) {
			effectiveToken = this.currentSessionToken;
			activeUser = SessionManager.getUser(effectiveToken);
			if (activeUser == null) {
				this.currentUser = null;
				this.currentSessionToken = null;
				return new Response(false, "Phiên đăng nhập không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại!", null);
			}
			this.currentUser = activeUser;
		} else {
			effectiveToken = null;
			activeUser = this.currentUser;
			if (activeUser == null) {
				return new Response(false, "Phiên đăng nhập không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại!", null);
			}
		}

		if (MANAGER_ONLY_ACTIONS.contains(action) && !isManager(activeUser)) {
			return new Response(false, "Bạn không có quyền Quản lý để thực hiện chức năng này!", null);
		}

		com.cafe.server.dao.RequestContext.setDeadlineMs(REQUEST_DEADLINE_MS);
		try {
			if (MUTATING_ACTIONS.contains(action)) {
				return SessionManager.executeIdempotent(effectiveToken, request.getRequestId(),
						() -> dispatchAuthenticatedAction(action, request, activeUser));
			}
			return dispatchAuthenticatedAction(action, request, activeUser);
		} catch (ClassCastException | NullPointerException ex) {
			return new Response(false, "Dữ liệu yêu cầu không đúng định dạng!", null);
		} finally {
			com.cafe.server.dao.RequestContext.clear();
		}
	}

	private Response dispatchAuthenticatedAction(ActionType action, Request request, Employee activeUser) {
		switch (action) {
		case GET_CATEGORIES:
			return new Response(true, "OK", productDao.getAllCategories());
		case GET_PRODUCTS:
			return new Response(true, "OK", productDao.getAllProducts());
		case ADD_PRODUCT: {
			Response res = fromDaoResult(productDao.addProduct((Product) request.getPayload()));
			if (res.isSuccess()) {
				SyncBroadcaster.broadcast(SyncBroadcaster.TOPIC_MENU_CHANGED);
			}
			return res;
		}
		case UPDATE_PRODUCT: {
			Response res = fromDaoResult(productDao.updateProduct((Product) request.getPayload()));
			if (res.isSuccess()) {
				SyncBroadcaster.broadcast(SyncBroadcaster.TOPIC_MENU_CHANGED);
			}
			return res;
		}
		case DELETE_PRODUCT: {
			Response res = fromDaoResult(productDao.deleteProduct((Integer) request.getPayload()));
			if (res.isSuccess()) {
				SyncBroadcaster.broadcast(SyncBroadcaster.TOPIC_MENU_CHANGED);
			}
			return res;
		}
		case GET_TABLES:
			return new Response(true, "OK", orderDao.getAllTables());
		case GET_ACTIVE_BILL:
			return new Response(true, "OK", orderDao.getActiveBill((Integer) request.getPayload()));
		case ADD_TO_BILL: {
			OrderDto order = (OrderDto) request.getPayload();
			if (order == null) {
				return new Response(false, "Dữ liệu đặt món không hợp lệ!", null);
			}
			// Luôn ghi nhận nhân viên theo phiên đã đăng nhập trên socket để tránh giả mạo employeeId
			Response res = fromDaoResult(orderDao.addToBill(
					order.getTableId(), activeUser.getId(), order.getProductId(), order.getQuantity()));
			if (res.isSuccess()) {
				SyncBroadcaster.broadcast(SyncBroadcaster.TOPIC_ORDER_CHANGED);
			}
			return res;
		}
		case CHECKOUT_BILL: {
			Response res = fromDaoResult(orderDao.checkoutBill((Integer) request.getPayload()));
			if (res.isSuccess()) {
				SyncBroadcaster.broadcast(SyncBroadcaster.TOPIC_ORDER_CHANGED);
			}
			return res;
		}
		case GET_EMPLOYEES:
			return new Response(true, "OK", employeeDao.getAllEmployees());
		case ADD_EMPLOYEE: {
			Response res = fromDaoResult(employeeDao.addEmployee((Employee) request.getPayload()));
			if (res.isSuccess()) {
				SyncBroadcaster.broadcast(SyncBroadcaster.TOPIC_EMPLOYEE_CHANGED);
			}
			return res;
		}
		case UPDATE_EMPLOYEE: {
			Employee empPayload = (Employee) request.getPayload();
			Response res = fromDaoResult(employeeDao.updateEmployee(empPayload));
			if (res.isSuccess()) {
				if (empPayload != null) {
					SessionManager.refreshUser(empPayload);
				}
				SyncBroadcaster.broadcast(SyncBroadcaster.TOPIC_EMPLOYEE_CHANGED);
			}
			return res;
		}
		case DELETE_EMPLOYEE: {
			Integer targetId = (Integer) request.getPayload();
			Response res = fromDaoResult(employeeDao.deleteEmployee(targetId, activeUser.getId()));
			if (res.isSuccess()) {
				SyncBroadcaster.unregisterTokens(SessionManager.invalidateUser(targetId));
				SyncBroadcaster.broadcast(SyncBroadcaster.TOPIC_EMPLOYEE_CHANGED);
			}
			return res;
		}
		case GET_ALL_BILLS:
			return new Response(true, "OK", orderDao.getAllBills());
		case CHANGE_PASSWORD: {
			ChangePasswordDto dto = (ChangePasswordDto) request.getPayload();
			if (dto == null) {
				return new Response(false, "Dữ liệu đổi mật khẩu không hợp lệ!", null);
			}
			return fromDaoResult(employeeDao.changePassword(
					activeUser.getId(), dto.getOldPassword(), dto.getNewPassword()));
		}
		default:
			return new Response(false, "Yêu cầu không được hỗ trợ!", null);
		}
	}

	private boolean isNotBlank(String value) {
		return value != null && !value.isBlank();
	}

	private Response handleLogin(Object payload) {
		if (!(payload instanceof Employee creds)) {
			return new Response(false, "Thông tin đăng nhập không hợp lệ!", null);
		}
		Employee user = employeeDao.login(creds.getUsername(), creds.getPassword());
		if (user != null) {
			this.currentUser = user;
			return new Response(true, "Đăng nhập thành công!", user);
		}
		this.currentUser = null;
		return new Response(false, "Tên tài khoản hoặc mật khẩu không chính xác!", null);
	}

	private boolean isManager(Employee user) {
		return user != null && "MANAGER".equalsIgnoreCase(user.getRole());
	}

	private Response fromDaoResult(DaoResult<?> result) {
		return new Response(result.isSuccess(), result.getMessage(), result.getData());
	}

	private void closeQuietly(AutoCloseable closeable) {
		if (closeable != null) {
			try {
				closeable.close();
			} catch (Exception ignored) {
			}
		}
	}
}

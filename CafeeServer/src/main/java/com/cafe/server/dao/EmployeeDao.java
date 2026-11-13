package com.cafe.server.dao;

import com.cafe.common.entity.Employee;
import com.cafe.server.util.PasswordUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.List;

public class EmployeeDao {

	public static Employee sanitize(Employee src) {
		if (src == null) {
			return null;
		}
		Employee copy = new Employee();
		copy.setId(src.getId());
		copy.setUsername(src.getUsername());
		copy.setPassword(null);
		copy.setFullName(src.getFullName());
		copy.setRole(src.getRole());
		copy.setActive(src.isActive());
		return copy;
	}

	public Employee login(String username, String password) {
		if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
			return null;
		}
		EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
		try {
			TypedQuery<Employee> query = em.createQuery(
					"SELECT e FROM Employee e WHERE e.username = :user AND e.isActive = true",
					Employee.class);
			query.setParameter("user", username.trim());
			List<Employee> result = query.getResultList();
			if (result.isEmpty()) {
				return null;
			}
			Employee found = result.get(0);
			if (!PasswordUtil.verifyPassword(password, found.getPassword())) {
				return null;
			}
			// Tự động nâng cấp mật khẩu plain-text cũ trong DB sang PBKDF2
			if (PasswordUtil.needsRehash(found.getPassword())) {
				em.getTransaction().begin();
				found.setPassword(PasswordUtil.hashPassword(password));
				em.getTransaction().commit();
			}
			return sanitize(found);
		} catch (Exception e) {
			if (em.getTransaction().isActive()) {
				em.getTransaction().rollback();
			}
			e.printStackTrace();
			return null;
		} finally {
			em.close();
		}
	}

	public List<Employee> getAllEmployees() {
		EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
		try {
			List<Employee> list = em
					.createQuery("SELECT e FROM Employee e WHERE e.isActive = true ORDER BY e.id ASC", Employee.class)
					.getResultList();
			List<Employee> safeList = new ArrayList<>(list.size());
			for (Employee emp : list) {
				safeList.add(sanitize(emp));
			}
			return safeList;
		} finally {
			em.close();
		}
	}

	public DaoResult<Void> addEmployee(Employee emp) {
		if (emp == null) {
			return DaoResult.fail("Dữ liệu nhân viên không hợp lệ!");
		}
		String username = emp.getUsername() != null ? emp.getUsername().trim() : "";
		String password = emp.getPassword() != null ? emp.getPassword().trim() : "";
		String fullName = emp.getFullName() != null ? emp.getFullName().trim() : "";
		String role = normalizeRole(emp.getRole());

		if (username.isEmpty() || username.contains(" ")) {
			return DaoResult.fail("Tên tài khoản không được để trống hoặc chứa khoảng trắng!");
		}
		if (password.isEmpty()) {
			return DaoResult.fail("Mật khẩu không được để trống!");
		}
		if (fullName.isEmpty()) {
			return DaoResult.fail("Họ và tên không được để trống!");
		}
		if (role == null) {
			return DaoResult.fail("Vai trò nhân viên không hợp lệ (chỉ chấp nhận MANAGER hoặc STAFF)!");
		}

		EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
		try {
			em.getTransaction().begin();

			Long count = em.createQuery("SELECT COUNT(e) FROM Employee e WHERE e.username = :user", Long.class)
					.setParameter("user", username)
					.getSingleResult();
			if (count > 0) {
				em.getTransaction().rollback();
				return DaoResult.fail("Tên tài khoản \"" + username + "\" đã tồn tại!");
			}

			Employee entity = new Employee();
			entity.setUsername(username);
			entity.setPassword(PasswordUtil.hashPassword(password));
			entity.setFullName(fullName);
			entity.setRole(role);
			entity.setActive(true);
			em.persist(entity);
			if (RequestContext.rollbackIfExpired(em)) {
				return DaoResult.fail("Yêu cầu quá thời gian xử lý trên Server, thao tác thêm nhân viên đã được hủy.");
			}
			em.getTransaction().commit();
			return DaoResult.ok("Thêm nhân viên thành công!");
		} catch (Exception e) {
			if (em.getTransaction().isActive()) {
				em.getTransaction().rollback();
			}
			e.printStackTrace();
			return DaoResult.fail("Lỗi hệ thống khi thêm nhân viên: " + e.getMessage());
		} finally {
			em.close();
		}
	}

	public DaoResult<Void> changePassword(int empId, String oldPassword, String newPassword) {
		if (oldPassword == null || oldPassword.isEmpty()) {
			return DaoResult.fail("Vui lòng nhập mật khẩu hiện tại!");
		}
		String trimmedNew = newPassword != null ? newPassword.trim() : "";
		if (trimmedNew.isEmpty()) {
			return DaoResult.fail("Mật khẩu mới không được để trống!");
		}

		EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
		try {
			em.getTransaction().begin();
			Employee e = em.find(Employee.class, empId);
			if (e == null || !e.isActive()) {
				em.getTransaction().rollback();
				return DaoResult.fail("Tài khoản không tồn tại hoặc đã bị khóa!");
			}
			if (!PasswordUtil.verifyPassword(oldPassword, e.getPassword())) {
				em.getTransaction().rollback();
				return DaoResult.fail("Mật khẩu hiện tại không chính xác!");
			}
			e.setPassword(PasswordUtil.hashPassword(trimmedNew));
			if (RequestContext.rollbackIfExpired(em)) {
				return DaoResult.fail("Yêu cầu quá thời gian xử lý trên Server, thao tác đổi mật khẩu đã được hủy.");
			}
			em.getTransaction().commit();
			return DaoResult.ok("Đổi mật khẩu thành công!");
		} catch (Exception ex) {
			if (em.getTransaction().isActive()) {
				em.getTransaction().rollback();
			}
			ex.printStackTrace();
			return DaoResult.fail("Lỗi hệ thống khi đổi mật khẩu!");
		} finally {
			em.close();
		}
	}

	public DaoResult<Void> updateEmployee(Employee emp) {
		if (emp == null || emp.getId() <= 0) {
			return DaoResult.fail("Dữ liệu cập nhật nhân viên không hợp lệ!");
		}
		String username = emp.getUsername() != null ? emp.getUsername().trim() : "";
		String fullName = emp.getFullName() != null ? emp.getFullName().trim() : "";
		String role = normalizeRole(emp.getRole());

		if (username.isEmpty() || username.contains(" ")) {
			return DaoResult.fail("Tên tài khoản không được để trống hoặc chứa khoảng trắng!");
		}
		if (fullName.isEmpty()) {
			return DaoResult.fail("Họ và tên không được để trống!");
		}
		if (role == null) {
			return DaoResult.fail("Vai trò nhân viên không hợp lệ!");
		}

		EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
		try {
			em.getTransaction().begin();
			Employee existing = em.find(Employee.class, emp.getId());
			if (existing == null || !existing.isActive()) {
				em.getTransaction().rollback();
				return DaoResult.fail("Không tìm thấy nhân viên cần cập nhật!");
			}

			Long dupCount = em.createQuery(
					"SELECT COUNT(e) FROM Employee e WHERE e.username = :user AND e.id <> :id", Long.class)
					.setParameter("user", username)
					.setParameter("id", emp.getId())
					.getSingleResult();
			if (dupCount > 0) {
				em.getTransaction().rollback();
				return DaoResult.fail("Tên tài khoản \"" + username + "\" đã được sử dụng!");
			}

			existing.setUsername(username);
			existing.setFullName(fullName);
			existing.setRole(role);
			if (emp.getPassword() != null && !emp.getPassword().trim().isEmpty()) {
				existing.setPassword(PasswordUtil.hashPassword(emp.getPassword().trim()));
			}
			if (RequestContext.rollbackIfExpired(em)) {
				return DaoResult.fail("Yêu cầu quá thời gian xử lý trên Server, thao tác cập nhật nhân viên đã được hủy.");
			}
			em.getTransaction().commit();
			return DaoResult.ok("Cập nhật thông tin nhân viên thành công!");
		} catch (Exception e) {
			if (em.getTransaction().isActive()) {
				em.getTransaction().rollback();
			}
			e.printStackTrace();
			return DaoResult.fail("Lỗi hệ thống khi cập nhật nhân viên: " + e.getMessage());
		} finally {
			em.close();
		}
	}

	public DaoResult<Void> deleteEmployee(int id, int actingUserId) {
		if (id == actingUserId) {
			return DaoResult.fail("Không thể tự xóa tài khoản đang đăng nhập!");
		}
		EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
		try {
			em.getTransaction().begin();
			Employee emp = em.find(Employee.class, id);
			if (emp == null || !emp.isActive()) {
				em.getTransaction().rollback();
				return DaoResult.fail("Nhân viên không tồn tại hoặc đã bị xóa!");
			}
			emp.setActive(false);
			if (RequestContext.rollbackIfExpired(em)) {
				return DaoResult.fail("Yêu cầu quá thời gian xử lý trên Server, thao tác xóa nhân viên đã được hủy.");
			}
			em.getTransaction().commit();
			return DaoResult.ok("Đã xóa tài khoản nhân viên thành công!");
		} catch (Exception e) {
			if (em.getTransaction().isActive()) {
				em.getTransaction().rollback();
			}
			e.printStackTrace();
			return DaoResult.fail("Lỗi hệ thống khi xóa nhân viên: " + e.getMessage());
		} finally {
			em.close();
		}
	}

	private String normalizeRole(String role) {
		if (role == null) {
			return null;
		}
		String upper = role.trim().toUpperCase();
		if ("MANAGER".equals(upper) || "STAFF".equals(upper)) {
			return upper;
		}
		return null;
	}
}

package com.cafe.server.dao;

import com.cafe.common.entity.Employee;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;

public class EmployeeDao {

	public EmployeeDao() {
		// Constructor rỗng, không giữ EntityManager global nữa
	}

	public Employee login(String username, String password) {
		EntityManager localEm = JPAUtil.getEntityManagerFactory().createEntityManager();
		try {
			TypedQuery<Employee> query = localEm.createQuery(
					"SELECT e FROM Employee e WHERE e.username = :user AND e.password = :pass AND e.isActive = true",
					Employee.class);
			query.setParameter("user", username);
			query.setParameter("pass", password);
			List<Employee> result = query.getResultList();
			return result.isEmpty() ? null : result.get(0);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		} finally {
			localEm.close();
		}
	}

	public List<Employee> getAllEmployees() {
		EntityManager localEm = JPAUtil.getEntityManagerFactory().createEntityManager();
		try {
			return localEm.createQuery("SELECT e FROM Employee e WHERE e.isActive = true", Employee.class)
					.getResultList();
		} finally {
			localEm.close();
		}
	}

	public boolean addEmployee(Employee emp) {
		EntityManager localEm = JPAUtil.getEntityManagerFactory().createEntityManager();
		try {
			localEm.getTransaction().begin();
			localEm.persist(emp);
			localEm.getTransaction().commit();
			return true;
		} catch (Exception e) {
			if (localEm.getTransaction().isActive())
				localEm.getTransaction().rollback();
			e.printStackTrace();
			return false;
		} finally {
			localEm.close();
		}
	}

	public boolean changePassword(int empId, String newPassword) {
		EntityManager localEm = JPAUtil.getEntityManagerFactory().createEntityManager();
		try {
			localEm.getTransaction().begin();
			Employee e = localEm.find(Employee.class, empId);
			if (e != null) {
				e.setPassword(newPassword);
				localEm.merge(e);
			}
			localEm.getTransaction().commit();
			return true;
		} catch (Exception ex) {
			if (localEm.getTransaction().isActive())
				localEm.getTransaction().rollback();
			ex.printStackTrace();
			return false;
		} finally {
			localEm.close();
		}
	}

	public boolean updateEmployee(Employee emp) {
		EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
		try {
			em.getTransaction().begin();
			Employee existing = em.find(Employee.class, emp.getId());
			if (existing != null) {
				existing.setFullName(emp.getFullName());
				existing.setRole(emp.getRole());
				// Cập nhật pass nếu có nhập
				if (emp.getPassword() != null && !emp.getPassword().isEmpty()) {
					existing.setPassword(emp.getPassword());
				}
				em.merge(existing);
			}
			em.getTransaction().commit();
			return true;
		} catch (Exception e) {
			if (em.getTransaction().isActive())
				em.getTransaction().rollback();
			e.printStackTrace();
			return false;
		} finally {
			em.close();
		}
	}

	public boolean deleteEmployee(int id) {
		EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
		try {
			em.getTransaction().begin();
			Employee p = em.find(Employee.class, id);
			if (p != null) {
				p.setActive(false);
				em.merge(p);
			}
			em.getTransaction().commit();
			return true;
		} catch (Exception e) {
			if (em.getTransaction().isActive())
				em.getTransaction().rollback();
			e.printStackTrace();
			return false;
		} finally {
			em.close();
		}
	}
}

package com.cafe.server.dao;

import com.cafe.common.entity.Category;
import com.cafe.common.entity.Product;
import jakarta.persistence.EntityManager;
import java.util.List;

public class ProductDao {

	public List<Category> getAllCategories() {
		EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
		try {
			return em.createQuery("SELECT c FROM Category c ORDER BY c.id ASC", Category.class).getResultList();
		} finally {
			em.close();
		}
	}

	public List<Product> getAllProducts() {
		EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
		try {
			return em.createQuery(
					"SELECT p FROM Product p LEFT JOIN FETCH p.category WHERE p.isActive = true ORDER BY p.id ASC",
					Product.class).getResultList();
		} finally {
			em.close();
		}
	}

	public DaoResult<Void> addProduct(Product p) {
		if (p == null) {
			return DaoResult.fail("Dữ liệu món không hợp lệ!");
		}
		String name = p.getName() != null ? p.getName().trim() : "";
		if (name.isEmpty()) {
			return DaoResult.fail("Tên món không được để trống!");
		}
		if (Float.isNaN(p.getPrice()) || Float.isInfinite(p.getPrice()) || p.getPrice() < 0) {
			return DaoResult.fail("Đơn giá món không hợp lệ!");
		}
		if (p.getCategory() == null || p.getCategory().getId() <= 0) {
			return DaoResult.fail("Vui lòng chọn danh mục hợp lệ!");
		}

		EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
		try {
			em.getTransaction().begin();
			Category cat = em.find(Category.class, p.getCategory().getId());
			if (cat == null) {
				em.getTransaction().rollback();
				return DaoResult.fail("Danh mục không tồn tại!");
			}

			Product entity = new Product();
			entity.setName(name);
			entity.setPrice(p.getPrice());
			entity.setCategory(cat);
			entity.setActive(true);
			em.persist(entity);
			if (RequestContext.rollbackIfExpired(em)) {
				return DaoResult.fail("Yêu cầu quá thời gian xử lý trên Server, thao tác thêm món đã được hủy.");
			}
			em.getTransaction().commit();
			return DaoResult.ok("Thêm món mới thành công!");
		} catch (Exception e) {
			if (em.getTransaction().isActive()) {
				em.getTransaction().rollback();
			}
			e.printStackTrace();
			return DaoResult.fail("Lỗi hệ thống khi thêm món: " + e.getMessage());
		} finally {
			em.close();
		}
	}

	public DaoResult<Void> updateProduct(Product p) {
		if (p == null || p.getId() <= 0) {
			return DaoResult.fail("Dữ liệu món cần cập nhật không hợp lệ!");
		}
		String name = p.getName() != null ? p.getName().trim() : "";
		if (name.isEmpty()) {
			return DaoResult.fail("Tên món không được để trống!");
		}
		if (Float.isNaN(p.getPrice()) || Float.isInfinite(p.getPrice()) || p.getPrice() < 0) {
			return DaoResult.fail("Đơn giá bán không được âm hoặc không hợp lệ!");
		}
		if (p.getCategory() == null || p.getCategory().getId() <= 0) {
			return DaoResult.fail("Vui lòng chọn danh mục hợp lệ!");
		}

		EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
		try {
			em.getTransaction().begin();
			Product existing = em.find(Product.class, p.getId());
			if (existing == null || !existing.isActive()) {
				em.getTransaction().rollback();
				return DaoResult.fail("Không tìm thấy món cần cập nhật (có thể đã bị xóa)!");
			}
			Category cat = em.find(Category.class, p.getCategory().getId());
			if (cat == null) {
				em.getTransaction().rollback();
				return DaoResult.fail("Danh mục không tồn tại!");
			}

			existing.setName(name);
			existing.setPrice(p.getPrice());
			existing.setCategory(cat);
			if (RequestContext.rollbackIfExpired(em)) {
				return DaoResult.fail("Yêu cầu quá thời gian xử lý trên Server, thao tác cập nhật món đã được hủy.");
			}
			em.getTransaction().commit();
			return DaoResult.ok("Cập nhật thông tin món thành công!");
		} catch (Exception e) {
			if (em.getTransaction().isActive()) {
				em.getTransaction().rollback();
			}
			e.printStackTrace();
			return DaoResult.fail("Lỗi hệ thống khi cập nhật món: " + e.getMessage());
		} finally {
			em.close();
		}
	}

	public DaoResult<Void> deleteProduct(int id) {
		EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
		try {
			em.getTransaction().begin();
			Product p = em.find(Product.class, id);
			if (p == null || !p.isActive()) {
				em.getTransaction().rollback();
				return DaoResult.fail("Món không tồn tại hoặc đã bị xóa trước đó!");
			}
			p.setActive(false);
			if (RequestContext.rollbackIfExpired(em)) {
				return DaoResult.fail("Yêu cầu quá thời gian xử lý trên Server, thao tác xóa món đã được hủy.");
			}
			em.getTransaction().commit();
			return DaoResult.ok("Đã xóa món thành công!");
		} catch (Exception e) {
			if (em.getTransaction().isActive()) {
				em.getTransaction().rollback();
			}
			e.printStackTrace();
			return DaoResult.fail("Lỗi hệ thống khi xóa món: " + e.getMessage());
		} finally {
			em.close();
		}
	}
}

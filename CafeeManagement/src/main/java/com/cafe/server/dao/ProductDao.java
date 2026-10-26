package com.cafe.server.dao;

import com.cafe.common.entity.Category;
import com.cafe.common.entity.Product;
import jakarta.persistence.EntityManager;
import java.util.List;

public class ProductDao {
    
    public List<Category> getAllCategories() {
        EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
        try {
            return em.createQuery("SELECT c FROM Category c", Category.class).getResultList();
        } finally {
            em.close();
        }
    }

    public List<Product> getAllProducts() {
        EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
        try {
            return em.createQuery("SELECT p FROM Product p WHERE p.isActive = true", Product.class).getResultList();
        } finally {
            em.close();
        }
    }

    public boolean addProduct(Product p) {
        EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
        try {
            em.getTransaction().begin();
            if (p.getCategory() != null) {
                p.setCategory(em.merge(p.getCategory()));
            }
            em.persist(p);
            em.getTransaction().commit();
            return true;
        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            e.printStackTrace();
            return false;
        } finally {
            em.close();
        }
    }

    public boolean updateProduct(Product p) {
        EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
        try {
            em.getTransaction().begin();
            if (p.getCategory() != null) {
                p.setCategory(em.merge(p.getCategory()));
            }
            em.merge(p);
            em.getTransaction().commit();
            return true;
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            e.printStackTrace();
            return false;
        } finally {
            em.close();
        }
    }

    public String deleteProduct(int id) {
        EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
        try {
            em.getTransaction().begin();
            Product p = em.find(Product.class, id);
            if (p != null) {
                p.setActive(false);
                em.merge(p);
            }
            em.getTransaction().commit();
            return "SUCCESS";
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            e.printStackTrace();
            return "Lỗi hệ thống: " + e.getMessage();
        } finally {
            em.close();
        }
    }
}

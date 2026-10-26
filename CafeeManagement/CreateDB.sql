-- ==========================================================
-- SCRIPT KHỞI TẠO CƠ SỞ DỮ LIỆU CafeDB
-- Đồ Án: Lập Trình Phân Tán (FIT - IUH)
-- ==========================================================

IF NOT EXISTS (SELECT * FROM sys.databases WHERE name = 'CafeDB')
BEGIN
    CREATE DATABASE CafeDB;
END
GO

USE CafeDB;
GO

-- Lưu ý:
-- Nhờ cấu hình <property name="hibernate.hbm2ddl.auto" value="update"/>
-- trong persistence.xml, khi bạn chạy ServerMain.java:
-- 1. Hibernate sẽ TỰ ĐỘNG TẠO ĐẦY ĐỦ CÁC BẢNG (Employee, Category, CoffeeTable, Product, Bill, BillDetail).
-- 2. Hệ thống sẽ TỰ ĐỘNG KHỞI TẠO SẴN DỮ LIỆU MẪU:
--    - 2 Tài khoản đăng nhập (mật khẩu mặc định: 123456):
--         * admin (Vai trò MANAGER - Quản lý)
--         * staff (Vai trò STAFF - Nhân viên bán hàng)
--    - 12 Bàn cafe (Bàn 01 -> Bàn 12) với trạng thái AVAILABLE
--    - Các danh mục (Cà Phê, Trà & Trà Sữa, Bánh Ngọt) và các món mẫu để gọi món.
--
-- Do đó, bạn CHỈ CẦN tạo CSDL rỗng CafeDB và bấm chạy ServerMain.java là ứng dụng sẵn sàng hoạt động ngay!


package vn.rikkei.cinemabox.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class FresherSqlTest {

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanupReviews() {
        jdbc.execute("DROP TABLE IF EXISTS reviews");
    }

    @Test
    void f1TaoBangReviewsVaChanDuLieuSai() throws Exception {
        String sql = Files.readString(Path.of("sql/fresher/f1_reviews.sql"));
        jdbc.execute(sql);

        jdbc.update("INSERT INTO reviews (customer_id, movie_id, rating, content) VALUES (1, 1, 5, 'Rất hay')");
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM reviews WHERE customer_id = 1 AND movie_id = 1");
        assertNotNull(row.get("created_at"));
        assertEquals(5, ((Number) row.get("rating")).intValue());

        // Chặn trùng (customer_id, movie_id)
        assertThrows(DataAccessException.class, () ->
                jdbc.update("INSERT INTO reviews (customer_id, movie_id, rating) VALUES (1, 1, 4)"));

        // Chặn rating ngoài 1..5
        assertThrows(DataAccessException.class, () ->
                jdbc.update("INSERT INTO reviews (customer_id, movie_id, rating) VALUES (2, 1, 0)"));
        assertThrows(DataAccessException.class, () ->
                jdbc.update("INSERT INTO reviews (customer_id, movie_id, rating) VALUES (2, 1, 6)"));

        // Chặn khoá ngoại không tồn tại
        assertThrows(DataAccessException.class, () ->
                jdbc.update("INSERT INTO reviews (customer_id, movie_id, rating) VALUES (9999, 1, 3)"));
        assertThrows(DataAccessException.class, () ->
                jdbc.update("INSERT INTO reviews (customer_id, movie_id, rating) VALUES (2, 9999, 3)"));

        // Chặn NULL ở cột bắt buộc và content vượt 500 ký tự
        assertThrows(DataAccessException.class, () ->
                jdbc.update("INSERT INTO reviews (customer_id, movie_id, rating) VALUES (2, 1, NULL)"));
        assertThrows(DataAccessException.class, () ->
                jdbc.update("INSERT INTO reviews (customer_id, movie_id, rating, content) VALUES (2, 1, 5, ?)",
                        "a".repeat(501)));
    }

    @Test
    void f2DoanhThuTheoPhimTinhDu10Phim() throws Exception {
        String sql = Files.readString(Path.of("sql/fresher/f2_doanh_thu_theo_phim.sql"));
        List<Map<String, Object>> rows = jdbc.queryForList(sql);

        assertEquals(10, rows.size());
        Map<String, Object> top = rows.get(0);
        assertEquals("Biệt Đội Sấm Sét", top.get("title"));
        assertEquals(9L, ((Number) top.get("so_ve")).longValue());
        assertEquals(1020000L, ((Number) top.get("doanh_thu")).longValue());

        Map<String, Object> last = rows.get(rows.size() - 1);
        assertEquals("Vũ Điệu Rồng Con", last.get("title"));
        assertEquals(0L, ((Number) last.get("so_ve")).longValue());
        assertEquals(0L, ((Number) last.get("doanh_thu")).longValue());
    }

    @Test
    void f3KhachChiTieuCaoLocTu500k() throws Exception {
        String sql = Files.readString(Path.of("sql/fresher/f3_khach_chi_tieu_cao.sql"));
        List<Map<String, Object>> rows = jdbc.queryForList(sql);

        assertEquals(4, rows.size());
        assertEquals("binh.tran@example.com", rows.get(0).get("email"));
        assertEquals(3L, ((Number) rows.get(0).get("so_lan_dat")).longValue());
        assertEquals(800000L, ((Number) rows.get(0).get("tong_chi_tieu")).longValue());
    }
}

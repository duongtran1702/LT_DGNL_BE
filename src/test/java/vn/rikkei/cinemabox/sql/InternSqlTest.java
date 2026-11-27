package vn.rikkei.cinemabox.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class InternSqlTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void i1PhimHanhDongChayDung() throws Exception {
        String sql = Files.readString(Path.of("sql/intern/i1_phim_hanh_dong.sql"));
        List<Map<String, Object>> rows = jdbc.queryForList(sql);
        assertEquals(4, rows.size());
        assertEquals("Cuộc Đua Cuối Cùng", rows.get(0).get("title"));
    }

    @Test
    void i2SuatChieu0512ChayDung() throws Exception {
        String sql = Files.readString(Path.of("sql/intern/i2_suat_chieu_05_12.sql"));
        List<Map<String, Object>> rows = jdbc.queryForList(sql);
        assertFalse(rows.isEmpty());
    }

    @Test
    void i3VeCuaKhachChayDung() throws Exception {
        String sql = Files.readString(Path.of("sql/intern/i3_ve_cua_khach.sql"));
        List<Map<String, Object>> rows = jdbc.queryForList(sql);
        assertEquals(3, rows.size());
    }
}

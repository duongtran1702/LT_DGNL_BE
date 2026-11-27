package vn.rikkei.cinemabox.showtime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ShowtimeControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void getByIdTraVeSuatChieuDungDinhDang() throws Exception {
        mvc.perform(get("/api/showtimes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.movieTitle").value("Biệt Đội Sấm Sét"))
                .andExpect(jsonPath("$.roomName").value("Phòng IMAX"))
                .andExpect(jsonPath("$.startTime").value("2026-12-04T19:00:00"))
                .andExpect(jsonPath("$.price").value(120000))
                .andExpect(jsonPath("$.availableSeats").value(35));
    }

    @Test
    void getByIdKhongTonTaiTraVe404() throws Exception {
        mvc.perform(get("/api/showtimes/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/showtimes/9999"));
    }
}

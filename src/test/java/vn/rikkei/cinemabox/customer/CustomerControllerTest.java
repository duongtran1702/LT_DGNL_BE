package vn.rikkei.cinemabox.customer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CustomerControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void taoKhachHangHopLeTraVe201VaMacDinhStandard() throws Exception {
        String email = "thu.tran.test@example.com";
        String body = """
                {
                  "fullName": "Trần Thị Thu",
                  "email": "%s",
                  "phone": "0912345678"
                }
                """.formatted(email);

        try {
            mvc.perform(post("/api/customers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.fullName").value("Trần Thị Thu"))
                    .andExpect(jsonPath("$.email").value(email))
                    .andExpect(jsonPath("$.phone").value("0912345678"))
                    .andExpect(jsonPath("$.memberLevel").value("STANDARD"))
                    .andExpect(jsonPath("$.createdAt").isString());
        } finally {
            customerRepository.findAll().stream()
                    .filter(c -> email.equals(c.getEmail()))
                    .forEach(customerRepository::delete);
        }
    }

    @Test
    void taoKhachHangSaiDuLieuTraVe400ValidationFailed() throws Exception {
        String body = """
                {
                  "fullName": "   ",
                  "email": "khong-hop-le",
                  "phone": "12345",
                  "memberLevel": "VIP"
                }
                """;

        mvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.length()").value(4));
    }

    @Test
    void taoKhachHangTrungEmailTraVe409Conflict() throws Exception {
        String body = """
                {
                  "fullName": "Trùng Email",
                  "email": "an.nguyen@example.com"
                }
                """;

        mvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void bodyKhongPhaiJsonHopLeTraVe400() throws Exception {
        mvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void xemKhachHangTheoIdVaPhanTrang() throws Exception {
        mvc.perform(get("/api/customers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("an.nguyen@example.com"));

        mvc.perform(get("/api/customers/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));

        mvc.perform(get("/api/customers?page=0&size=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.content[0].id").value(1));

        mvc.perform(get("/api/customers?page=99&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(8));

        mvc.perform(get("/api/customers?page=-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));

        mvc.perform(get("/api/customers?size=51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));

        mvc.perform(get("/api/customers?page=abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }
}

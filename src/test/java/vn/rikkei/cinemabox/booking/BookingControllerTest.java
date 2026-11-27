package vn.rikkei.cinemabox.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.rikkei.cinemabox.movie.Movie;
import vn.rikkei.cinemabox.movie.MovieRepository;
import vn.rikkei.cinemabox.showtime.RoomRepository;
import vn.rikkei.cinemabox.showtime.Showtime;
import vn.rikkei.cinemabox.showtime.ShowtimeRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BookingControllerTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ShowtimeRepository showtimeRepository;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<Long> createdShowtimeIds = new ArrayList<>();
    private final List<Long> createdMovieIds = new ArrayList<>();

    @AfterEach
    void cleanupTestData() {
        for (Long showtimeId : createdShowtimeIds) {
            jdbc.update("DELETE FROM bookings WHERE showtime_id = ?", showtimeId);
            jdbc.update("DELETE FROM showtimes WHERE id = ?", showtimeId);
        }
        for (Long movieId : createdMovieIds) {
            jdbc.update("DELETE FROM movies WHERE id = ?", movieId);
        }
        createdShowtimeIds.clear();
        createdMovieIds.clear();
    }

    private Showtime createTestShowtime(long price, int availableSeats) {
        Movie m = new Movie();
        m.setTitle("Test Movie " + System.nanoTime());
        m.setGenre("ACTION");
        m.setDurationMin(100);
        m.setAgeRating("P");
        m.setReleaseDate(LocalDate.of(2026, 12, 1));
        m = movieRepository.save(m);
        createdMovieIds.add(m.getId());

        Showtime s = new Showtime();
        s.setMovie(m);
        s.setRoom(roomRepository.findById(1L).orElseThrow());
        s.setStartTime(LocalDateTime.of(2026, 12, 10, 19, 0));
        s.setPrice(price);
        s.setAvailableSeats(availableSeats);
        s = showtimeRepository.save(s);
        createdShowtimeIds.add(s.getId());
        return s;
    }

    private ResponseEntity<Map> postBooking(Long customerId, Long showtimeId, Integer quantity) {
        Map<String, Object> body = Map.of(
                "customerId", customerId,
                "showtimeId", showtimeId,
                "quantity", quantity
        );
        return rest.postForEntity("/api/bookings", body, Map.class);
    }

    @Test
    void datVeThanhCongTinhGiamGiaGoldVaTruGhe() {
        // price = 123_455, qty = 1, GOLD giảm 10% làm tròn xuống: 123455 * 9 / 10 = 111109
        Showtime showtime = createTestShowtime(123_455L, 10);

        ResponseEntity<Map> resp = postBooking(1L, showtime.getId(), 1);
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        Map<?, ?> body = resp.getBody();
        assertNotNull(body);
        assertEquals(111_109, ((Number) body.get("totalAmount")).intValue());
        assertEquals("CONFIRMED", body.get("status"));
        assertNotNull(body.get("paymentRef"));

        Showtime updated = showtimeRepository.findById(showtime.getId()).orElseThrow();
        assertEquals(9, updated.getAvailableSeats());
    }

    @Test
    void thanhToanBiTuChoiHoanTacDuLieu() {
        // price = 200_000, qty = 6 -> 1_200_000 > 1_000_000 -> FakePaymentGateway từ chối
        Showtime showtime = createTestShowtime(200_000L, 10);
        long confirmedBefore = bookingRepository.findAll().stream()
                .filter(b -> b.getShowtime().getId().equals(showtime.getId()) && "CONFIRMED".equals(b.getStatus()))
                .count();

        ResponseEntity<Map> resp = postBooking(2L, showtime.getId(), 6);
        assertEquals(HttpStatus.PAYMENT_REQUIRED, resp.getStatusCode());
        assertEquals("PAYMENT_FAILED", resp.getBody().get("error"));

        Showtime after = showtimeRepository.findById(showtime.getId()).orElseThrow();
        assertEquals(10, after.getAvailableSeats());

        long confirmedAfter = bookingRepository.findAll().stream()
                .filter(b -> b.getShowtime().getId().equals(showtime.getId()) && "CONFIRMED".equals(b.getStatus()))
                .count();
        assertEquals(confirmedBefore, confirmedAfter);
    }

    @Test
    void kiemTraLoiValidation404VaThieuGhe409() {
        Showtime showtime = createTestShowtime(80_000L, 2);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> invalid = rest.postForEntity(
                "/api/bookings",
                new HttpEntity<>("{\"customerId\":2,\"showtimeId\":" + showtime.getId() + ",\"quantity\":11}", headers),
                Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, invalid.getStatusCode());
        assertEquals("VALIDATION_FAILED", invalid.getBody().get("error"));

        ResponseEntity<Map> notFound = postBooking(9999L, showtime.getId(), 1);
        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatusCode());
        assertEquals("NOT_FOUND", notFound.getBody().get("error"));

        ResponseEntity<Map> notEnough = postBooking(2L, showtime.getId(), 3);
        assertEquals(HttpStatus.CONFLICT, notEnough.getStatusCode());
        assertEquals("NOT_ENOUGH_SEATS", notEnough.getBody().get("error"));
        assertEquals(2, showtimeRepository.findById(showtime.getId()).orElseThrow().getAvailableSeats());
    }

    @Test
    void huyVeConfirmedTraLaiGheVaChanHuyLan2() {
        Showtime showtime = createTestShowtime(80_000L, 10);
        ResponseEntity<Map> created = postBooking(2L, showtime.getId(), 3);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        long bookingId = ((Number) created.getBody().get("id")).longValue();
        assertEquals(7, showtimeRepository.findById(showtime.getId()).orElseThrow().getAvailableSeats());

        ResponseEntity<Map> cancelled = rest.postForEntity("/api/bookings/" + bookingId + "/cancel", null, Map.class);
        assertEquals(HttpStatus.OK, cancelled.getStatusCode());
        assertEquals("CANCELLED", cancelled.getBody().get("status"));
        assertEquals(10, showtimeRepository.findById(showtime.getId()).orElseThrow().getAvailableSeats());

        ResponseEntity<Map> cancelAgain = rest.postForEntity("/api/bookings/" + bookingId + "/cancel", null, Map.class);
        assertEquals(HttpStatus.CONFLICT, cancelAgain.getStatusCode());
        assertEquals("INVALID_STATE", cancelAgain.getBody().get("error"));

        ResponseEntity<Map> missing = rest.postForEntity("/api/bookings/999999/cancel", null, Map.class);
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
        assertEquals("NOT_FOUND", missing.getBody().get("error"));
    }

    @Test
    void rangBuocDbChanAvailableSeatsAm() {
        Showtime showtime = createTestShowtime(80_000L, 1);
        assertThrows(DataAccessException.class, () ->
                jdbc.update("UPDATE showtimes SET available_seats = -1 WHERE id = ?", showtime.getId()));
    }

    @Test
    void nhieuLuongDatVeDongThoiLanThanhToanLoiKhongBanVuotGhe() throws Exception {
        int initialSeats = 10;
        Showtime showtime = createTestShowtime(200_000L, initialSeats);

        int validRequests = 8;      // mỗi request 3 vé (600_000đ -> hợp lệ)
        int failedPayRequests = 3;  // mỗi request 6 vé (1_200_000đ -> vượt hạn mức 1_000_000đ)
        int totalThreads = validRequests + failedPayRequests;

        ExecutorService pool = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch ready = new CountDownLatch(totalThreads);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<ResponseEntity<Map>>> tasks = new ArrayList<>();

        for (int i = 0; i < validRequests; i++) {
            tasks.add(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return postBooking(2L, showtime.getId(), 3);
            });
        }
        for (int i = 0; i < failedPayRequests; i++) {
            tasks.add(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return postBooking(2L, showtime.getId(), 6);
            });
        }

        List<Future<ResponseEntity<Map>>> futures = new ArrayList<>();
        for (Callable<ResponseEntity<Map>> task : tasks) {
            futures.add(pool.submit(task));
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();

        int createdCount = 0;
        for (Future<ResponseEntity<Map>> f : futures) {
            ResponseEntity<Map> res = f.get(30, TimeUnit.SECONDS);
            HttpStatus status = HttpStatus.valueOf(res.getStatusCode().value());
            if (status == HttpStatus.CREATED) {
                createdCount++;
            } else {
                assertTrue(status == HttpStatus.CONFLICT || status == HttpStatus.PAYMENT_REQUIRED,
                        "Trạng thái không mong đợi: " + status);
            }
        }
        pool.shutdown();

        Showtime finalShowtime = showtimeRepository.findById(showtime.getId()).orElseThrow();
        List<Booking> confirmed = bookingRepository.findAll().stream()
                .filter(b -> b.getShowtime().getId().equals(showtime.getId()) && "CONFIRMED".equals(b.getStatus()))
                .toList();

        int soldSeats = confirmed.stream().mapToInt(Booking::getQuantity).sum();
        assertEquals(3, createdCount);
        assertEquals(9, soldSeats);
        assertEquals(1, finalShowtime.getAvailableSeats());
        assertEquals(initialSeats, finalShowtime.getAvailableSeats() + soldSeats);
        confirmed.forEach(b -> assertNotNull(b.getPaymentRef()));
    }

    @Test
    void nhieuLuongDatVeDongThoiHaiSuatChieu() throws Exception {
        Showtime s1 = createTestShowtime(100_000L, 5);
        Showtime s2 = createTestShowtime(100_000L, 4);

        int threads = 8; // 4 vào s1 (mỗi lần 2 vé -> tối đa 2 thành công), 4 vào s2 (mỗi lần 2 vé -> tối đa 2 thành công)
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ResponseEntity<Map>>> futures = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return postBooking(2L, s1.getId(), 2);
            }));
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return postBooking(2L, s2.getId(), 2);
            }));
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();

        for (Future<ResponseEntity<Map>> f : futures) {
            HttpStatus status = HttpStatus.valueOf(f.get(30, TimeUnit.SECONDS).getStatusCode().value());
            assertTrue(status == HttpStatus.CREATED || status == HttpStatus.CONFLICT);
        }
        pool.shutdown();

        Showtime f1 = showtimeRepository.findById(s1.getId()).orElseThrow();
        Showtime f2 = showtimeRepository.findById(s2.getId()).orElseThrow();
        int sold1 = bookingRepository.findAll().stream()
                .filter(b -> b.getShowtime().getId().equals(s1.getId()) && "CONFIRMED".equals(b.getStatus()))
                .mapToInt(Booking::getQuantity).sum();
        int sold2 = bookingRepository.findAll().stream()
                .filter(b -> b.getShowtime().getId().equals(s2.getId()) && "CONFIRMED".equals(b.getStatus()))
                .mapToInt(Booking::getQuantity).sum();

        assertEquals(4, sold1);
        assertEquals(1, f1.getAvailableSeats());
        assertEquals(4, sold2);
        assertEquals(0, f2.getAvailableSeats());
    }
}

# Bài làm CinemaBox – Database + Backend

Họ tên:
Mã sinh viên:
Lớp:

## Pha 1 (không dùng AI)

Tự đánh dấu phần đã xong trước mốc 1 (chỉ để GV tham khảo, không thay cho kết quả chấm):

- [x] I1  - [x] I2  - [x] I3  - [x] Endpoint GET /api/showtimes/{id}
- [x] F1  - [x] F2  - [x] F3  - [x] API khách hàng (tạo, xem, danh sách, validate, lỗi)

Ghi chú (nếu có):

## Pha 2 (được dùng AI)

### Cách bạn chống bán vượt số ghế khi nhiều người đặt cùng lúc

- Dùng khoá bi quan (`SELECT ... FOR UPDATE`, `@Lock(LockModeType.PESSIMISTIC_WRITE)`) ở `ShowtimeRepository.findByIdForUpdate` để khoá dòng suất chiếu trong suốt giao dịch đặt vé, kết hợp ràng buộc `CHECK (available_seats >= 0)` trong `schema.sql`.
- Khi nhiều request cùng đặt vào một suất chiếu, các giao dịch sẽ xử lý lần lượt theo khoá dòng: đọc số ghế mới nhất, kiểm tra đủ ghế, gọi cổng thanh toán rồi mới trừ ghế.
- Không chọn cách trừ ghế tạm trước khi thanh toán vì nếu một request bị từ chối thanh toán (vượt 1.000.000 đ) đang tạm giữ ghế trong 80 ms, request hợp lệ đến cùng lúc sẽ bị báo nhầm `409 NOT_ENOUGH_SEATS`.
- Đã kiểm chứng bằng test chạy 11 luồng đồng thời trong `BookingControllerTest.nhieuLuongDatVeDongThoiLanThanhToanLoiKhongBanVuotGhe`.

### Cách bạn hoàn tác khi thanh toán lỗi

- `BookingService.create` chạy trong `@Transactional` và chỉ cập nhật `available_seats` cũng như lưu `Booking` (`CONFIRMED`) sau khi `paymentGateway.charge(...)` thành công.
- Khi cổng thanh toán ném `PaymentFailedException`, Spring tự rollback toàn bộ transaction: `available_seats` giữ nguyên, không có bản ghi `CONFIRMED` nào được lưu, và `GlobalExceptionHandler` trả về `402 PAYMENT_FAILED`.

### Test bạn đã viết (tên lớp, tên test, mỗi test kiểm tra điều gì)

- `InternSqlTest`:
  - `i1PhimHanhDongChayDung`, `i2SuatChieu0512ChayDung`, `i3VeCuaKhachChayDung`: kiểm tra 3 câu SQL tầng Intern chạy đúng trên H2.
- `ShowtimeControllerTest`:
  - `getByIdTraVeSuatChieuDungDinhDang`: `GET /api/showtimes/1` trả về 200 cùng đúng 6 trường và định dạng `startTime`.
  - `getByIdKhongTonTaiTraVe404`: `GET /api/showtimes/9999` trả về 404 `NOT_FOUND` theo khung `ApiError`.
- `FresherSqlTest`:
  - `f1TaoBangReviewsVaChanDuLieuSai`: chạy `f1_reviews.sql` và thử chèn dữ liệu đúng/sai để kiểm tra FK, `rating` trong khoảng 1..5, chặn đánh giá trùng `(customer_id, movie_id)` và tự điền `created_at`.
  - `f2DoanhThuTheoPhimTinhDu10Phim`: kiểm tra `f2_doanh_thu_theo_phim.sql` trả đủ 10 phim, phim chưa bán được vé hiện 0 (không bị `NULL`).
  - `f3KhachChiTieuCaoLocTu500k`: kiểm tra `f3_khach_chi_tieu_cao.sql` lọc đúng khách có tổng chi vé `CONFIRMED >= 500000`.
- `CustomerControllerTest`:
  - `taoKhachHangHopLeTraVe201VaMacDinhStandard`: `POST /api/customers` trả 201 và tự gán `STANDARD` khi bỏ trống `memberLevel`.
  - `taoKhachHangSaiDuLieuTraVe400ValidationFailed`: gửi sai/thiếu trường, trả 400 `VALIDATION_FAILED` kèm `details` đúng tên trường.
  - `taoKhachHangTrungEmailTraVe409Conflict`: trùng email trả 409 `CONFLICT`.
  - `bodyKhongPhaiJsonHopLeTraVe400`: gửi JSON sai cú pháp trả 400 `BAD_REQUEST`, không trả 500.
  - `xemKhachHangTheoIdVaPhanTrang`: kiểm tra `GET /api/customers/{id}` (200/404) và phân trang `GET /api/customers` (trang hợp lệ, trang vượt quá, tham số sai trả 400).
- `BookingControllerTest`:
  - `datVeThanhCongTinhGiamGiaGoldVaTruGhe`: đặt vé cho khách `GOLD`, kiểm tra giảm 10% làm tròn xuống và trừ đúng `available_seats`.
  - `thanhToanBiTuChoiHoanTacDuLieu`: đặt tổng tiền > 1.000.000 đ, kiểm tra trả 402 `PAYMENT_FAILED`, số ghế giữ nguyên và không có vé `CONFIRMED`.
  - `kiemTraLoiValidation404VaThieuGhe409`: kiểm tra `quantity = 11` (400), khách không tồn tại (404), đặt quá số ghế còn lại (409 `NOT_ENOUGH_SEATS`).
  - `huyVeConfirmedTraLaiGheVaChanHuyLan2`: `POST /api/bookings/{id}/cancel` trả lại ghế, huỷ lại lần 2 trả 409 `INVALID_STATE`.
  - `rangBuocDbChanAvailableSeatsAm`: thử `UPDATE available_seats = -1` để kiểm tra ràng buộc `CHECK` trong `schema.sql` chặn thành công.
  - `nhieuLuongDatVeDongThoiLanThanhToanLoiKhongBanVuotGhe`: chạy 11 luồng đặt vé cùng lúc (8 luồng hợp lệ, 3 luồng vượt hạn mức thanh toán), kiểm tra không bán vượt ghế và `available_seats + tổng vé bán = ghế ban đầu`.
  - `nhieuLuongDatVeDongThoiHaiSuatChieu`: chạy đồng thời 8 luồng vào 2 suất chiếu khác nhau, kiểm tra không suất nào bán vượt ghế.

### Nhật ký dùng AI

| # | Bạn hỏi AI điều gì | AI gợi ý gì | Bạn giữ / sửa / bỏ, vì sao |
|---|---|---|---|
| 1 | Cách xử lý khi nhiều người đặt cùng lúc trong lúc `PaymentGateway.charge` tốn ~80 ms và có thể lỗi thanh toán | Đưa ra 2 hướng: trừ ghế tạm rồi hoàn lại nếu lỗi, hoặc dùng `SELECT ... FOR UPDATE` (`PESSIMISTIC_WRITE`) giữ khoá tới khi thanh toán xong | Chọn `PESSIMISTIC_WRITE` và trừ ghế sau `charge()`, bỏ hướng trừ ghế tạm để tránh từ chối nhầm `409 NOT_ENOUGH_SEATS` với request hợp lệ đến cùng lúc. Đã kiểm chứng qua test 11 luồng `BookingControllerTest.nhieuLuongDatVeDongThoiLanThanhToanLoiKhongBanVuotGhe` |
| 2 | Cách gom các lỗi 400/402/409 về cùng format `ApiError` | Tạo `ApiException` chung và bắt thêm trong `GlobalExceptionHandler` cùng các lỗi validate, JSON sai, sai kiểu tham số, `PaymentFailedException` | Giữ cấu trúc chung này để xử lý gọn, tách riêng `VALIDATION_FAILED` cho lỗi validate body và `BAD_REQUEST` cho JSON hỏng hoặc tham số query sai |
| 3 | Cách chặn số ghế âm ngay từ tầng cơ sở dữ liệu | Thêm `CONSTRAINT chk_showtimes_available_seats CHECK (available_seats >= 0)` vào bảng `showtimes` trong `schema.sql` | Giữ nguyên để làm lớp bảo vệ cuối ở DB theo đúng giới hạn mục 3.4; đã kiểm chứng bằng test `rangBuocDbChanAvailableSeatsAm` |

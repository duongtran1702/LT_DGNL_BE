package vn.rikkei.cinemabox.customer;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.rikkei.cinemabox.common.ApiException;
import vn.rikkei.cinemabox.common.PageResponse;
import vn.rikkei.cinemabox.common.ResourceNotFoundException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;

    @Transactional
    public CustomerResponse create(CreateCustomerRequest request) {
        String email = request.email().trim();
        if (customerRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Email đã tồn tại trong hệ thống: " + email);
        }

        Customer customer = new Customer();
        customer.setFullName(request.fullName().trim());
        customer.setEmail(email);
        customer.setPhone(request.phone());
        customer.setMemberLevel(request.memberLevel() != null ? request.memberLevel() : "STANDARD");

        return CustomerResponse.from(customerRepository.save(customer));
    }

    public CustomerResponse getById(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng id=" + id));
        return CustomerResponse.from(customer);
    }

    public PageResponse<CustomerResponse> list(int page, int size) {
        if (page < 0) {
            throw ApiException.badRequest("page phải lớn hơn hoặc bằng 0");
        }
        if (size < 1 || size > 50) {
            throw ApiException.badRequest("size phải trong khoảng từ 1 đến 50");
        }
        return PageResponse.from(
                customerRepository.findAll(PageRequest.of(page, size, Sort.by("id").ascending()))
                        .map(CustomerResponse::from));
    }
}

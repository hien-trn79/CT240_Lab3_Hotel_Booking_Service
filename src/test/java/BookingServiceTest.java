
import exception.PaymentFailedException;
import exception.RoomNotAvailableException;
import model.BookingReceipt;
import model.Room;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import repository.RoomRepository;
import service.BookingService;
import service.PaymentGateway;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookingService Unit Test Suite")
class BookingServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @InjectMocks
    private BookingService bookingService;

    private Room availableRoom;
    private Room unavailableRoom;

    @BeforeEach
    void setUp() {
        // Khởi tạo dữ liệu mẫu trước mỗi bài test
        availableRoom = new Room("R101", 100.0, true);
        unavailableRoom = new Room("R102", 150.0, false);
    }

    // ==========================================
    // 1. LUỒNG THÀNH CÔNG (HAPPY PATH)
    // ==========================================
    @Test
    @DisplayName("Đặt phòng thành công - Phòng trống và thanh toán thành công")
    void testBookRoom_Success() {
        // 1. ARRANGE
        String roomId = "R101";
        int nights = 3;
        String creditCard = "1234-5678-9012-3456";
        double expectedTotalAmount = 300.0; // 100.0 * 3

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(availableRoom));
        when(paymentGateway.charge(creditCard, expectedTotalAmount)).thenReturn(true);

        // 2. ACT
        BookingReceipt receipt = bookingService.bookRoom(roomId, nights, creditCard);

        // 3. ASSERT
        assertNotNull(receipt);
        assertEquals(roomId, receipt.getRoomId());
        assertEquals(expectedTotalAmount, receipt.getTotalAmount());
        assertNotNull(receipt.getBookingId());

        // Kiểm tra trạng thái phòng đã chuyển sang false
        assertFalse(availableRoom.isAvailable());

        // VERIFICATION: Kiểm tra các phương thức save() và charge() được gọi đúng 1 lần
        verify(paymentGateway, times(1)).charge(creditCard, expectedTotalAmount);
        verify(roomRepository, times(1)).save(availableRoom);
    }

    // ==========================================
    // 2. KIỂM THỬ NGOẠI LỆ NGHIỆP VỤ (BUSINESS EXCEPTIONS)
    // ==========================================
    @Test
    @DisplayName("Ném RoomNotAvailableException khi không tìm thấy phòng (findById trả về Optional.empty)")
    void testBookRoom_RoomNotFound_ThrowsException() {
        // 1. ARRANGE
        String roomId = "R999";
        when(roomRepository.findById(roomId)).thenReturn(Optional.empty());

        // 2. ACT & 3. ASSERT
        RoomNotAvailableException exception = assertThrows(
            RoomNotAvailableException.class,
            () -> bookingService.bookRoom(roomId, 2, "1234-5678-9012-3456")
        );

        assertEquals("Khong tim thay phong: " + roomId, exception.getMessage());

        // VERIFICATION: Không gọi thanh toán hoặc lưu CSDL khi không thấy phòng
        verify(paymentGateway, never()).charge(anyString(), anyDouble());
        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("Ném RoomNotAvailableException khi phòng đã được đặt trước đó (isAvailable == false)")
    void testBookRoom_RoomAlreadyBooked_ThrowsException() {
        // 1. ARRANGE
        String roomId = "R102";
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(unavailableRoom));

        // 2. ACT & 3. ASSERT
        RoomNotAvailableException exception = assertThrows(
            RoomNotAvailableException.class,
            () -> bookingService.bookRoom(roomId, 2, "1234-5678-9012-3456")
        );

        assertEquals("Phong da duoc dat truoc do: " + roomId, exception.getMessage());

        // VERIFICATION
        verify(paymentGateway, never()).charge(anyString(), anyDouble());
        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("Ném PaymentFailedException khi thanh toán qua PaymentGateway thất bại (charge trả về false)")
    void testBookRoom_PaymentFailed_ThrowsException() {
        // 1. ARRANGE
        String roomId = "R101";
        int nights = 2;
        String creditCard = "1234-5678-9012-3456";

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(availableRoom));
        when(paymentGateway.charge(creditCard, 200.0)).thenReturn(false);

        // 2. ACT & 3. ASSERT
        PaymentFailedException exception = assertThrows(
            PaymentFailedException.class,
            () -> bookingService.bookRoom(roomId, nights, creditCard)
        );

        assertEquals("Thanh toan qua the credit card that bai!", exception.getMessage());

        // VERIFICATION: charge() được gọi 1 lần, nhưng save() KHÔNG được gọi và isAvailable vẫn bằng true
        verify(paymentGateway, times(1)).charge(creditCard, 200.0);
        verify(roomRepository, never()).save(any());
        assertTrue(availableRoom.isAvailable());
    }

    // ==========================================
    // 3. KIỂM THỬ THAM SỐ BIÊN & ĐIỀU KIỆN ĐẦU VÀO (BOUNDARY & EDGE CASES)
    // ==========================================
    @ParameterizedTest
    @ValueSource(ints = {0, -1, -5})
    @DisplayName("Ném IllegalArgumentException khi nights <= 0")
    void testBookRoom_InvalidNights_ThrowsException(int invalidNights) {
        // ACT & ASSERT
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> bookingService.bookRoom("R101", invalidNights, "1234-5678-9012-3456")
        );

        assertEquals("Tham so dat phong khong hop le!", exception.getMessage());

        // VERIFICATION: Kiểm tra không tương tác với bất kỳ mock dependency nào
        verifyNoInteractions(roomRepository);
        verifyNoInteractions(paymentGateway);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("Ném IllegalArgumentException khi creditCard là null, rỗng hoặc chỉ chứa khoảng trắng")
    void testBookRoom_NullOrEmptyCreditCard_ThrowsException(String invalidCard) {
        // ACT & ASSERT
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> bookingService.bookRoom("R101", 2, invalidCard)
        );

        assertEquals("Tham so dat phong khong hop le!", exception.getMessage());

        // VERIFICATION
        verifyNoInteractions(roomRepository);
        verifyNoInteractions(paymentGateway);
    }

    @Test
    @DisplayName("Ném IllegalArgumentException khi roomId là null")
    void testBookRoom_NullRoomId_ThrowsException() {
        // ACT & ASSERT
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> bookingService.bookRoom(null, 2, "1234-5678-9012-3456")
        );

        assertEquals("Tham so dat phong khong hop le!", exception.getMessage());

        // VERIFICATION
        verifyNoInteractions(roomRepository);
        verifyNoInteractions(paymentGateway);
    }
}
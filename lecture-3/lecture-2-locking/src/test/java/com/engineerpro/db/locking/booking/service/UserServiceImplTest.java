package com.engineerpro.db.locking.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import com.engineerpro.db.locking.booking.model.Booking;
import com.engineerpro.db.locking.booking.model.Room;
import com.engineerpro.db.locking.booking.repository.BookingRepository;
import com.engineerpro.db.locking.booking.repository.RoomRepository;

// https://www.baeldung.com/mockito-annotations
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

  @Mock
  private BookingRepository bookingRepository;

  @Mock
  private RoomRepository roomRepository;

  UserService userService;

  @BeforeEach
  public void init() {
    MockitoAnnotations.openMocks(this);
    userService = new UserServiceImpl(bookingRepository, roomRepository);
  }

  @Test
  void testOptismisticBooking_whenUpdatedRoomRow() {
    // given
    int userId = 1;
    int roomId = 2;
    int version = 0;
    Room mockAvailableRoom = Room.builder().id(roomId).available(true).room("test").version(version).build();
    Booking mockCreatedBooking = Booking.builder().id(100).userId(userId).roomId(roomId).build();
    when(roomRepository.findOneByIdAndAvailable(roomId, true)).thenReturn(mockAvailableRoom);
    when(bookingRepository.save(any())).thenReturn(mockCreatedBooking);
    when(roomRepository.updateRoomAsUnavailable(roomId, version)).thenReturn(1);
    // when
    Booking booking = userService.optimisticBookRoom(userId, roomId);
    // then
    assertEquals(roomId, booking.getRoomId());
    assertEquals(userId, booking.getUserId());
  }

  @Test
  void testOptismisticBooking_whenUpdatedNoRoomRow() {
    // given
    int userId = 1;
    int roomId = 2;
    int version = 0;
    Room mockAvailableRoom = Room.builder().id(roomId).available(true).room("test").version(version).build();
    Booking mockCreatedBooking = Booking.builder().id(100).userId(userId).roomId(roomId).build();
    when(roomRepository.findOneByIdAndAvailable(roomId, true)).thenReturn(mockAvailableRoom);
    when(bookingRepository.save(any())).thenReturn(mockCreatedBooking);
    when(roomRepository.updateRoomAsUnavailable(roomId, version)).thenReturn(0);
    // when
    assertThrows(RuntimeException.class, () -> userService.optimisticBookRoom(userId, roomId));
  }

  @Test
  void testOptimisticBooking_with100ConcurrentUsers_onlyOneSucceeds() throws InterruptedException, Exception {
    int roomId = 1;
    int version = 0;
    int threadCount = 1000;

    Room mockRoom = Room.builder()
        .id(roomId)
        .available(true)
        .version(version)
        .room("test")
        .build();

    // Tất cả thread đều thấy phòng còn trống (mock)
    when(roomRepository.findOneByIdAndAvailable(eq(roomId), eq(true)))
        .thenReturn(mockRoom);

    when(bookingRepository.save(any()))
        .thenAnswer(invocation -> {
          Booking b = invocation.getArgument(0);
          b.setId(new Random().nextInt(1000));
          return b;
        });

    // Chỉ 1 thread update thành công
    AtomicInteger updateCallCount = new AtomicInteger(0);
    when(roomRepository.updateRoomAsUnavailable(eq(roomId), eq(version)))
        .thenAnswer(invocation -> updateCallCount.getAndIncrement() == 0 ? 1 : 0);

    // Tạo 100 thread đồng thời
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch readyLatch = new CountDownLatch(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);

    List<Future<Boolean>> futures = new ArrayList<>();

    for (int i = 0; i < threadCount; i++) {
      final int userId = i + 1;
      futures.add(executor.submit(() -> {
        readyLatch.countDown(); // báo thread đã sẵn sàng
        startLatch.await(); // chờ tất cả thread ready
        try {
          userService.optimisticBookRoom(userId, roomId);
          return true; // đặt thành công
        } catch (Exception e) {
          return false; // đặt thất bại
        }
      }));
    }

    // Đợi tất cả thread sẵn sàng rồi cùng start
    readyLatch.await();
    startLatch.countDown();

    int success = 0;
    int failure = 0;
    for (Future<Boolean> f : futures) {
      if (f.get()) {
        success++;
      } else {
        failure++;
      }
    }

    executor.shutdown();
    assertEquals(1, success, "Only one user should succeed");
    assertEquals(threadCount - 1, failure, "All others should fail");
    System.out.println(">>>>> Booking success count = " + success);
    System.out.println(">>>>> Booking failure count = " + failure);

  }

  @Test
  void testPessimisticBooking_whenSelectedAvaiableRoomRow() {
    // given
    int userId = 1;
    int roomId = 2;
    Room mockAvailableRoom = Room.builder().id(roomId).available(true).room("test").build();
    Booking mockCreatedBooking = Booking.builder().id(100).userId(userId).roomId(roomId).build();
    when(roomRepository.findByIdAndAvailable(roomId, true)).thenReturn(mockAvailableRoom);
    when(bookingRepository.save(any())).thenReturn(mockCreatedBooking);
    when(roomRepository.updateRoomAsUnavailableWhenPessimisticLocked(roomId)).thenReturn(1);
    // when
    Booking booking = userService.perssimisticBookRom(userId, roomId);
    // then
    assertEquals(roomId, booking.getRoomId());
    assertEquals(userId, booking.getUserId());
  }

  @Test
  void testPessimisticBooking_whenCannotSelectedAvailableRoom() {
    // given
    int userId = 1;
    int roomId = 2;
    when(roomRepository.findByIdAndAvailable(roomId, true)).thenReturn(null);
    // when
    assertThrows(RuntimeException.class, () -> userService.perssimisticBookRom(userId, roomId));
    // then
    verifyNoInteractions(bookingRepository);
    verify(roomRepository, times(1)).findByIdAndAvailable(roomId, true);
    verifyNoMoreInteractions(roomRepository);
  }

}

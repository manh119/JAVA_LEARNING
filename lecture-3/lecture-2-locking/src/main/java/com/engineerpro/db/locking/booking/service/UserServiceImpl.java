package com.engineerpro.db.locking.booking.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.engineerpro.db.locking.booking.model.Booking;
import com.engineerpro.db.locking.booking.model.Room;
import com.engineerpro.db.locking.booking.repository.BookingRepository;
import com.engineerpro.db.locking.booking.repository.RoomRepository;

@Service
public class UserServiceImpl implements UserService {

  @Autowired
  BookingRepository bookingRepository;

  @Autowired
  RoomRepository roomRepository;

  public UserServiceImpl(BookingRepository bookingRepository, RoomRepository roomRepository) {
    this.bookingRepository = bookingRepository;
    this.roomRepository = roomRepository;
  }

  // Implement the methods from UserService interface

  @Transactional
  @Override
  public Booking optimisticBookRoom(int userId, int roomId) {
    // Implementation for optimistic locking booking
    Room room = roomRepository.findOneByIdAndAvailable(roomId, true);
    if (room == null) {
      throw new IllegalStateException("Room is not available for booking");
    }
    Booking booking = Booking.builder()
        .userId(userId)
        .roomId(roomId)
        .build();
    booking = bookingRepository.save(booking);
    int updatedRows = roomRepository.updateRoomAsUnavailable(roomId, room.getVersion());
    if (updatedRows == 0) {
      throw new IllegalStateException(
          "Failed to update room as unavailable, it might have been booked by another user");
    }
    return booking;
  }

  @Transactional
  @Override
  public Booking perssimisticBookRom(int userId, int roomId) {
    // Implementation for pessimistic locking booking
    // 1. check if room is available
    // 2. if available, create booking
    // 3. update room as unavailable
    // 4. return booking
    Room room = roomRepository.findByIdAndAvailable(roomId, true);
    if (room == null) {
      throw new IllegalStateException("Room is not available for booking");
    }

    Booking booking = Booking.builder()
        .userId(userId)
        .roomId(roomId)
        .build();
    booking = bookingRepository.save(booking);
    roomRepository.updateRoomAsUnavailableWhenPessimisticLocked(roomId);

    return booking;
  }
}
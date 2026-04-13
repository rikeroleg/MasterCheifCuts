package com.masterchefcuts.repositories;

import com.masterchefcuts.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    /** All messages exchanged between two participants, oldest first. */
    @Query("SELECT m FROM Message m WHERE " +
           "(m.sender.id = :a AND m.receiver.id = :b) OR " +
           "(m.sender.id = :b AND m.receiver.id = :a) " +
           "ORDER BY m.sentAt ASC")
    List<Message> findConversation(@Param("a") String a, @Param("b") String b);

    /**
     * For each distinct conversation partner of the given participant,
     * return the most recent message (used to build the thread list).
     */
    @Query("SELECT m FROM Message m WHERE m.id IN (" +
           "  SELECT MAX(m2.id) FROM Message m2 " +
           "  WHERE m2.sender.id = :userId OR m2.receiver.id = :userId " +
           "  GROUP BY CASE WHEN m2.sender.id = :userId THEN m2.receiver.id ELSE m2.sender.id END" +
           ") ORDER BY m.sentAt DESC")
    List<Message> findLatestPerThread(@Param("userId") String userId);

    /** Unread messages sent TO the given participant FROM a specific sender. */
    List<Message> findByReceiverIdAndSenderIdAndReadFalse(String receiverId, String senderId);

       long countByReceiverIdAndSenderIdAndReadFalse(String receiverId, String senderId);

       Optional<Message> findByIdAndReceiverId(Long id, String receiverId);
}

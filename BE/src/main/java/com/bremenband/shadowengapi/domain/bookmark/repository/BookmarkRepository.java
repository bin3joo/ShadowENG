package com.bremenband.shadowengapi.domain.bookmark.repository;

import com.bremenband.shadowengapi.domain.bookmark.entity.Bookmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    @Query("SELECT b FROM Bookmark b JOIN FETCH b.studySession s JOIN FETCH s.video WHERE b.user.id = :userId ORDER BY b.createdAt DESC")
    List<Bookmark> findByUser_IdOrderByCreatedAtDesc(@Param("userId") Long userId);

    boolean existsByUser_IdAndStudySession_Id(Long userId, Long sessionId);

    void deleteByUser_IdAndStudySession_Id(Long userId, Long sessionId);
}

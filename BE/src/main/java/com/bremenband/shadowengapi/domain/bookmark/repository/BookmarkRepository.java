package com.bremenband.shadowengapi.domain.bookmark.repository;

import com.bremenband.shadowengapi.domain.bookmark.entity.Bookmark;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    @Query("SELECT b FROM Bookmark b JOIN FETCH b.sentence s JOIN FETCH s.studySession WHERE b.user.id = :userId ORDER BY b.createdAt DESC")
    List<Bookmark> findByUser_IdOrderByCreatedAtDesc(@Param("userId") Long userId);

    Optional<Bookmark> findByUser_IdAndSentence_Id(Long userId, Long sentenceId);

    void deleteByUser_IdAndSentence_Id(Long userId, Long sentenceId);
}

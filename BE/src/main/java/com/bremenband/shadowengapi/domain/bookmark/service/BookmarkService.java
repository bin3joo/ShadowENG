package com.bremenband.shadowengapi.domain.bookmark.service;

import com.bremenband.shadowengapi.domain.bookmark.dto.res.BookmarkListResponse;
import com.bremenband.shadowengapi.domain.bookmark.dto.res.BookmarkUpdateResponse;
import com.bremenband.shadowengapi.domain.bookmark.entity.Bookmark;
import com.bremenband.shadowengapi.domain.bookmark.repository.BookmarkRepository;
import com.bremenband.shadowengapi.domain.study.entity.StudySession;
import com.bremenband.shadowengapi.domain.study.repository.StudySessionRepository;
import com.bremenband.shadowengapi.domain.user.entity.User;
import com.bremenband.shadowengapi.domain.user.repository.UserRepository;
import com.bremenband.shadowengapi.global.exception.CustomException;
import com.bremenband.shadowengapi.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final StudySessionRepository studySessionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public BookmarkListResponse getBookmarks(Long userId) {
        List<Bookmark> bookmarks = bookmarkRepository.findByUser_IdOrderByCreatedAtDesc(userId);

        List<BookmarkListResponse.BookmarkItem> items = bookmarks.stream()
                .map(BookmarkListResponse.BookmarkItem::from)
                .toList();

        return new BookmarkListResponse(items);
    }

    @Transactional
    public BookmarkUpdateResponse updateBookmark(Long userId, Long sessionId, Boolean isBookmarked) {
        StudySession session = studySessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));

        if (isBookmarked) {
            if (!bookmarkRepository.existsByUser_IdAndStudySession_Id(userId, sessionId)) {
                User user = userRepository.getReferenceById(userId);
                bookmarkRepository.save(Bookmark.builder().user(user).studySession(session).build());
            }
        } else {
            bookmarkRepository.deleteByUser_IdAndStudySession_Id(userId, sessionId);
        }

        return new BookmarkUpdateResponse(sessionId, isBookmarked);
    }
}

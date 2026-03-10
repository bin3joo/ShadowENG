package com.bremenband.shadowengapi.domain.bookmark.service;

import com.bremenband.shadowengapi.domain.bookmark.dto.res.BookmarkListResponse;
import com.bremenband.shadowengapi.domain.bookmark.dto.res.BookmarkUpdateResponse;
import com.bremenband.shadowengapi.domain.bookmark.entity.Bookmark;
import com.bremenband.shadowengapi.domain.bookmark.repository.BookmarkRepository;
import com.bremenband.shadowengapi.domain.study.entity.Sentence;
import com.bremenband.shadowengapi.domain.study.repository.SentenceRepository;
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
    private final SentenceRepository sentenceRepository;
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
    public BookmarkUpdateResponse updateBookmark(Long userId, Long sentenceId, Boolean isBookmarked) {
        Sentence sentence = sentenceRepository.findById(sentenceId)
                .orElseThrow(() -> new CustomException(ErrorCode.SENTENCE_NOT_FOUND));

        if (isBookmarked) {
            boolean alreadyExists = bookmarkRepository.findByUser_IdAndSentence_Id(userId, sentenceId).isPresent();
            if (!alreadyExists) {
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
                bookmarkRepository.save(Bookmark.builder().user(user).sentence(sentence).build());
            }
        } else {
            bookmarkRepository.deleteByUser_IdAndSentence_Id(userId, sentenceId);
        }

        return new BookmarkUpdateResponse(sentenceId, sentence.getContent(), isBookmarked);
    }
}

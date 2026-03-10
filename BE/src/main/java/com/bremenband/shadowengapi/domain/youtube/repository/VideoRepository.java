package com.bremenband.shadowengapi.domain.youtube.repository;

import com.bremenband.shadowengapi.domain.youtube.entity.Video;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoRepository extends JpaRepository<Video, String> {
}

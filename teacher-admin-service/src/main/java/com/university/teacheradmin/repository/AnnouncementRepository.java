package com.university.teacheradmin.repository;

import com.university.teacheradmin.model.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    List<Announcement> findByCourseId(Long courseId);
}

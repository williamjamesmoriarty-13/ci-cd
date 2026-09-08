package com.university.teacheradmin.repository;

import com.university.teacheradmin.model.Grade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GradeRepository extends JpaRepository<Grade, Long> {
    List<Grade> findByStudentId(Long studentId);

    Optional<Grade> findByStudentIdAndCourseId(Long studentId, Long courseId);
}

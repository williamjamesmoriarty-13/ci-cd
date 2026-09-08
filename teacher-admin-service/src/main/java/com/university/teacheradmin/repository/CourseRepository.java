package com.university.teacheradmin.repository;

import com.university.teacheradmin.model.Course;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, Long> {
}

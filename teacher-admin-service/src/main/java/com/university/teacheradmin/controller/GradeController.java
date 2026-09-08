package com.university.teacheradmin.controller;

import com.university.teacheradmin.dto.GradeCreateRequest;
import com.university.teacheradmin.exception.ResourceNotFoundException;
import com.university.teacheradmin.model.Grade;
import com.university.teacheradmin.repository.CourseRepository;
import com.university.teacheradmin.repository.GradeRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/grades")
public class GradeController {

    private final GradeRepository gradeRepository;
    private final CourseRepository courseRepository;

    public GradeController(GradeRepository gradeRepository, CourseRepository courseRepository) {
        this.gradeRepository = gradeRepository;
        this.courseRepository = courseRepository;
    }

    @PostMapping
    public ResponseEntity<Grade> submitGrade(@Valid @RequestBody GradeCreateRequest request) {
        if (!courseRepository.existsById(request.getCourseId())) {
            throw new ResourceNotFoundException("Cours introuvable");
        }
        Grade grade = gradeRepository.findByStudentIdAndCourseId(request.getStudentId(), request.getCourseId())
                .orElse(new Grade(request.getStudentId(), request.getCourseId(), request.getValue()));
        grade.setValue(request.getValue());
        gradeRepository.save(grade);
        return ResponseEntity.status(HttpStatus.CREATED).body(grade);
    }

    @GetMapping
    public List<Grade> listGrades(@RequestParam(required = false) Long studentId) {
        if (studentId != null) {
            return gradeRepository.findByStudentId(studentId);
        }
        return gradeRepository.findAll();
    }
}

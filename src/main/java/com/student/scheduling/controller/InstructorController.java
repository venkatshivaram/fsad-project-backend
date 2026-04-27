package com.student.scheduling.controller;

import com.student.scheduling.entity.Course;
import com.student.scheduling.entity.User;
import com.student.scheduling.repository.CourseRepository;
import com.student.scheduling.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/instructor")
@Tag(name = "Instructor", description = "Instructor/admin endpoints for course rosters")
@SecurityRequirement(name = "bearerAuth")
public class InstructorController {

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/{username}/courses")
    @Operation(summary = "Get instructor courses", description = "Returns courses assigned to the given instructor username.")
    public ResponseEntity<?> getInstructorCourses(@PathVariable String username) {
        List<Course> courses = courseRepository.findByInstructor(username);
        return ResponseEntity.ok(courses);
    }

    @GetMapping("/course/{courseId}/students")
    @Operation(summary = "Get course roster", description = "Returns enrolled students for a course.")
    public ResponseEntity<?> getStudentsForCourse(@PathVariable Long courseId) {
        Optional<Course> courseOpt = courseRepository.findById(courseId);
        if (courseOpt.isPresent()) {
            Course course = courseOpt.get();
            Set<User> students = course.getEnrolledStudents();
            List<Map<String, Object>> roster = students.stream().map(s -> {
                Map<String, Object> info = new HashMap<>();
                info.put("id", s.getId());
                info.put("username", s.getUsername());
                info.put("email", s.getEmail());
                return info;
            }).collect(Collectors.toList());
            return ResponseEntity.ok(roster);
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/course/{courseId}/students")
    @Operation(summary = "Add student to course", description = "Adds a student to the course roster by username.")
    public ResponseEntity<?> addStudentToCourse(@PathVariable Long courseId, @RequestBody Map<String, String> payload) {
        String studentUsername = payload.get("studentUsername");
        if (studentUsername == null || studentUsername.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Student username is required"));
        }

        Optional<Course> courseOpt = courseRepository.findById(courseId);
        Optional<User> studentOpt = userRepository.findByUsername(studentUsername);

        if (courseOpt.isPresent() && studentOpt.isPresent()) {
            Course course = courseOpt.get();
            User student = studentOpt.get();

            if (!"student".equalsIgnoreCase(student.getRole())) {
                return ResponseEntity.badRequest().body(Map.of("message", "User is not a student"));
            }

            if (student.getRegisteredCourses().contains(course)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Student is already enrolled"));
            }

            if (course.getEnrolledCount() >= course.getCapacity()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Course is at capacity"));
            }

            student.getRegisteredCourses().add(course);
            userRepository.save(student);

            return ResponseEntity.ok(Map.of("message", "Student added successfully"));
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/course/{courseId}/students/{studentId}")
    @Operation(summary = "Remove student from course", description = "Removes a student from the selected course roster.")
    public ResponseEntity<?> removeStudentFromCourse(@PathVariable Long courseId, @PathVariable Long studentId) {
        Optional<Course> courseOpt = courseRepository.findById(courseId);
        Optional<User> studentOpt = userRepository.findById(studentId);

        if (courseOpt.isPresent() && studentOpt.isPresent()) {
            Course course = courseOpt.get();
            User student = studentOpt.get();

            if (student.getRegisteredCourses().contains(course)) {
                student.getRegisteredCourses().remove(course);
                userRepository.save(student);
                return ResponseEntity.ok(Map.of("message", "Student removed successfully"));
            }
            return ResponseEntity.badRequest().body(Map.of("message", "Student is not enrolled in this course"));
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/students/{studentId}")
    @Operation(summary = "Delete student account", description = "Deletes a student account from the system.")
    public ResponseEntity<?> deleteStudentAccount(@PathVariable Long studentId) {
        Optional<User> studentOpt = userRepository.findById(studentId);

        if (studentOpt.isPresent()) {
            User student = studentOpt.get();
            if ("student".equalsIgnoreCase(student.getRole())) {
                userRepository.delete(student);
                return ResponseEntity.ok(Map.of("message", "Student account completely deleted"));
            }
            return ResponseEntity.badRequest().body(Map.of("message", "Can only delete student accounts"));
        }
        return ResponseEntity.notFound().build();
    }
}
